#!/usr/bin/env python3
"""Longmynd Dual installer. Builds locally, installs a separate systemd service.
GPL-3.0-or-later. Run as the intended normal user; sudo is used for system changes.
"""
import argparse
import datetime
import grp
import hashlib
import json
import os
from pathlib import Path
import pwd
import shutil
import socket
import subprocess
import sys
import tempfile
import time

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / 'packaging'))
from settings import VERSION, default_config, validate

CONFIG = Path('/etc/longmynd-dual.json')
PREFIX = Path('/opt/longmynd-dual')
UNIT = '/etc/systemd/system/longmynd-dual.service'
GROUP = 'longmynd-dual'
DEPS = ['build-essential', 'cmake', 'pkg-config', 'git', 'python3',
        'libusb-1.0-0-dev', 'libasound2-dev', 'libjson-c-dev']

def run(args, *, admin=False, check=True, capture=False):
    cmd = list(map(str, args))
    if admin and os.geteuid() != 0: cmd.insert(0, 'sudo')
    return subprocess.run(cmd, check=check, text=True, capture_output=capture)

def state(name, prop):
    return run(['systemctl', 'show', name, '--property='+prop, '--value'],
               check=False, capture=True).stdout.strip()

def devices():
    result = []
    for d in sorted(Path('/sys/bus/usb/devices').glob('*')):
        try:
            if (d/'idVendor').read_text().strip() != '0403' or (d/'idProduct').read_text().strip() != '6010': continue
            result.append({'serial': (d/'serial').read_text().strip(),
                'product': (d/'product').read_text().strip(),
                'bus': int((d/'busnum').read_text()), 'device': int((d/'devnum').read_text())})
        except (OSError, ValueError): pass
    return result

def manifest_files(root):
    files = []
    for line in (root/'SHA256SUMS').read_text().splitlines():
        digest, name = line.split('  ', 1)
        p = root / name
        if Path(name).is_absolute() or not p.resolve().is_relative_to(root.resolve()):
            raise ValueError('Ungueltiger Pfad in SHA256SUMS: '+name)
        if hashlib.sha256(p.read_bytes()).hexdigest() != digest:
            raise ValueError('Pruefsumme stimmt nicht: '+name)
        files.append(name)
    if not files: raise ValueError('Leere Quelldateiliste.')
    return files

def write_system(stage, name, contents, destination, mode='0644'):
    path = stage / name
    path.write_text(contents)
    run(['install', '-m', mode, path, destination], admin=True)

def check_manual_receiver():
    managed = state('longmynd.service', 'MainPID')
    active = run(['pgrep', '-x', 'longmynd'], check=False, capture=True).stdout.split()
    other = [p for p in active if p != managed]
    if other:
        raise ValueError('Manueller Longmynd laeuft noch (PID '+', '.join(other)+'). Erst mit Strg+C beenden.')

def install(args, c, files):
    username = c['user']
    pwd.getpwnam(username)
    if args.start:
        available = [d['serial'] for d in devices()]
        for key in ('master_serial', 'aux_serial'):
            if available.count(c[key]) != 1:
                raise ValueError('USB-Modul nicht eindeutig angeschlossen: '+c[key])
        check_manual_receiver()
    # Finish compilation and source verification before replacing a live service.
    if not args.skip_deps:
        run(['apt-get', 'update'], admin=True)
        run(['apt-get', 'install', '-y', *DEPS], admin=True)
    for cmd in ('make', 'cmake', 'pkg-config', 'systemctl', 'udevadm'):
        if not shutil.which(cmd): raise ValueError('Fehlendes Programm: '+cmd)
    run(['make', '-C', ROOT, '-j', str(args.jobs), 'longmynd'])
    binary = ROOT / 'longmynd'
    if not binary.is_file(): raise ValueError('Kompilation hat kein Programm erzeugt.')
    unresolved = run(['ldd', binary], check=False, capture=True)
    if 'not found' in unresolved.stdout + unresolved.stderr:
        raise ValueError('Fehlende Laufzeitbibliothek:\n'+unresolved.stdout+unresolved.stderr)
    stamp = datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
    release = PREFIX/'releases'/(VERSION+'-'+stamp)
    previous = os.readlink(PREFIX/'current') if (PREFIX/'current').is_symlink() else None
    old_active = state('longmynd.service', 'ActiveState') == 'active'
    old_enabled = state('longmynd.service', 'UnitFileState') == 'enabled'
    old_present = state('longmynd.service', 'LoadState') == 'loaded'
    dual_active = state('longmynd-dual.service', 'ActiveState') == 'active'
    if (PREFIX/'current').exists() and previous is None:
        raise ValueError('/opt/longmynd-dual/current ist kein Symlink; nichts ersetzt.')
    with tempfile.TemporaryDirectory(prefix='longmynd-dual-install-') as temp:
        stage = Path(temp)
        tree = stage/'release'
        (tree/'bin').mkdir(parents=True)
        shutil.copy2(binary, tree/'bin/longmynd-dual')
        (tree/'bin/longmynd-dual').chmod(0o755)
        shutil.copytree(ROOT/'web/htdocs', tree/'share/web/htdocs')
        for name in [*files, 'SHA256SUMS']:
            target = tree/'source'/name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT/name, target)
        (tree/'VERSION').write_text(VERSION+'\n')
        run(['install', '-d', '-m', '0755', PREFIX/'releases',
             '/usr/local/libexec/longmynd-dual', '/var/lib/longmynd-dual'], admin=True)
        # Preserve previous config and unit for review/recovery.
        backup = Path('/var/lib/longmynd-dual')/('before-'+stamp)
        run(['install', '-d', '-m', '0755', backup], admin=True)
        for original in (CONFIG, Path(UNIT)):
            if original.is_file(): run(['cp', '-a', original, backup/original.name], admin=True)
        write_system(stage, 'state.json', json.dumps(dict(previous_release=previous,
            original_active=old_active, original_enabled=old_enabled, dual_active=dual_active), indent=2)+'\n', backup/'state.json')
        run(['cp', '-a', tree, release], admin=True)
        run(['chown', '-R', 'root:root', release], admin=True)
        run(['chmod', '-R', 'a+rX', release], admin=True)
        try: grp.getgrnam(GROUP)
        except KeyError: run(['groupadd', '--system', GROUP], admin=True)
        run(['usermod', '-a', '-G', GROUP, username], admin=True)
        rules = '# Only the two explicitly selected FT2232H modules.\n'
        for key in ('master_serial', 'aux_serial'):
            rules += 'SUBSYSTEM=="usb", ENV{DEVTYPE}=="usb_device", ATTR{idVendor}=="0403", ATTR{idProduct}=="6010", ATTR{serial}=="'+c[key]+'", GROUP="'+GROUP+'", MODE="0660"\n'
        write_system(stage, 'usb.rules', rules, '/etc/udev/rules.d/70-longmynd-dual.rules')
        run(['udevadm', 'control', '--reload-rules'], admin=True)
        run(['udevadm', 'trigger', '--subsystem-match=usb', '--action=change'], admin=True)
        run(['udevadm', 'settle'], admin=True)
        for name in ('launcher.py', 'settings.py'):
            run(['install', '-m', '0644', ROOT/'packaging'/name,
                '/usr/local/libexec/longmynd-dual/'+name], admin=True)
        write_system(stage, 'config.json', json.dumps(c, indent=2)+'\n', CONFIG)
        unit = f'''[Unit]
Description=Longmynd Dual DATV Receiver {VERSION}
Wants=network-online.target
After=network-online.target
Conflicts=longmynd.service

[Service]
Type=simple
User={username}
Group={GROUP}
WorkingDirectory=/opt/longmynd-dual/current/share
ExecStart=/usr/bin/python3 /usr/local/libexec/longmynd-dual/launcher.py
Restart=on-failure
RestartSec=5
TimeoutStopSec=20
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
'''
        if dual_active: run(['systemctl', 'stop', 'longmynd-dual.service'], admin=True)
        write_system(stage, 'service', unit, UNIT)
        run(['ln', '-sfn', release, PREFIX/'current.next'], admin=True)
        run(['mv', '-Tf', PREFIX/'current.next', PREFIX/'current'], admin=True)
        run(['systemctl', 'daemon-reload'], admin=True)
        if args.start:
            try:
                if old_present:
                    run(['systemctl', 'disable', '--now', 'longmynd.service'], admin=True, check=False)
                run(['systemctl', 'enable', '--now', 'longmynd-dual.service'], admin=True)
                deadline = time.monotonic()+30
                while time.monotonic() < deadline:
                    good = state('longmynd-dual.service', 'ActiveState') == 'active'
                    if good:
                        try:
                            for ch in c['channels']:
                                with socket.create_connection(('127.0.0.1', ch['web_port']), timeout=1): pass
                            break
                        except OSError: pass
                    time.sleep(.5)
                else: raise ValueError('Dual-Dienst wurde nicht innerhalb von 30 Sekunden erreichbar.')
            except Exception:
                run(['systemctl', 'disable', '--now', 'longmynd-dual.service'], admin=True, check=False)
                if previous:
                    run(['ln', '-sfn', previous, PREFIX/'current'], admin=True)
                    if (backup/CONFIG.name).exists(): run(['cp', '-a', backup/CONFIG.name, CONFIG], admin=True)
                    if (backup/Path(UNIT).name).exists(): run(['cp', '-a', backup/Path(UNIT).name, UNIT], admin=True)
                    run(['systemctl', 'daemon-reload'], admin=True)
                    if dual_active: run(['systemctl', 'start', 'longmynd-dual.service'], admin=True, check=False)
                if old_enabled: run(['systemctl', 'enable', 'longmynd.service'], admin=True, check=False)
                if old_active: run(['systemctl', 'start', 'longmynd.service'], admin=True, check=False)
                raise
        elif dual_active:
            run(['systemctl', 'start', 'longmynd-dual.service'], admin=True)
        print('\nInstalliert:', release)
        print('Konfiguration:', CONFIG)
        print('Sicherung:', backup)
        print('Status: systemctl status longmynd-dual --no-pager -l')
        if not args.start and not dual_active:
            print('Noch nicht gestartet. Zum Starten Installer erneut mit --start aufrufen.')
        print('Quellcode:', release/'source')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--version', action='version', version=VERSION)
    parser.add_argument('--master', help='Seriennummer des vorhandenen I2C/TS-Moduls')
    parser.add_argument('--aux', help='Seriennummer des zweiten TS-Moduls')
    parser.add_argument('--user', help='Dienst-Benutzer, z.B. pi')
    parser.add_argument('--start', action='store_true', help='Dual-Dienst aktivieren und bisherigen longmynd-Dienst abloesen')
    parser.add_argument('--skip-deps', action='store_true', help='APT-Abhaengigkeiten bereits vorhanden')
    parser.add_argument('--jobs', type=int, default=2)
    parser.add_argument('--list-usb', action='store_true', help='FT2232H-Module anzeigen, nichts installieren')
    parser.add_argument('--dry-run', action='store_true', help='Konfiguration und Installationsplan anzeigen, nichts aendern')
    args = parser.parse_args()
    if sys.version_info < (3, 9):
        parser.error('Python 3.9 oder neuer erforderlich.')
    try:
        if args.list_usb:
            print(json.dumps(devices(), indent=2)); return 0
        if not 1 <= args.jobs <= 16: raise ValueError('--jobs: 1..16')
        if CONFIG.exists(): c = json.loads(CONFIG.read_text())
        else:
            user = args.user or os.environ.get('SUDO_USER') or pwd.getpwuid(os.getuid()).pw_name
            c = default_config(user, args.master or '', args.aux or '')
        for arg, key in [('master','master_serial'), ('aux','aux_serial'), ('user','user')]:
            if getattr(args, arg) is not None: c[key] = getattr(args, arg)
        validate(c)
        files = manifest_files(ROOT)
        if args.dry_run:
            print('Longmynd Dual', VERSION, '\n'+json.dumps(c, indent=2))
            print('Plan: Abhaengigkeiten:', 'vorhanden' if args.skip_deps else ', '.join(DEPS))
            print('Kompilieren; installieren nach /opt/longmynd-dual/releases; USB-Rechte und eigenen Dienst einrichten.')
            print('Aktivieren und bisherigen longmynd-Dienst abloesen:', args.start)
            return 0
        install(args, c, files)
        return 0
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as e:
        print('Installation abgebrochen:', e, file=sys.stderr)
        return 1

if __name__ == '__main__':
    raise SystemExit(main())
