#!/usr/bin/env python3
"""Install optional OpenTuner two-channel UDP bridge; existing receiver untouched.
GPL-3.0-or-later. Run as pi; sudo is used only for system changes.
"""
import argparse
import datetime
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import pwd
import re
import subprocess
import sys
import tempfile
import time

ROOT=Path(__file__).resolve().parent
PREFIX=Path('/opt/longmynd-dual/ot-dual')
UNIT=Path('/etc/systemd/system/longmynd-ot-dual.service')
CONFIG=Path('/etc/longmynd-dual.json')
VERSION='0.1.0-beta.4'

def run(args,admin=False,check=True,capture=False):
    cmd=list(map(str,args))
    if admin and os.geteuid()!=0:cmd.insert(0,'sudo')
    return subprocess.run(cmd,text=True,check=check,capture_output=capture)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base-port',type=int,default=9900)
    p.add_argument('--client',help='optional PC IPv4 address allowed to control the bridge')
    p.add_argument('--multicast-interface',default='0.0.0.0')
    p.add_argument('--skip-deps',action='store_true')
    p.add_argument('--dry-run',action='store_true')
    p.add_argument('--no-start',action='store_true')
    args=p.parse_args()
    try:
        if not 1024<=args.base_port<=65493:raise ValueError('Ungueltiger UDP-Basisport.')
        interface=str(ipaddress.IPv4Address(args.multicast_interface))
        if args.client:args.client=str(ipaddress.IPv4Address(args.client))
        config=json.loads(CONFIG.read_text())
        user=config['user']
        if not re.fullmatch(r'[a-z_][a-z0-9_-]*',user) or user=='root':raise ValueError('Ungueltiger Dienstbenutzer.')
        pwd.getpwnam(user)
        if len(config.get('channels',[]))!=2:raise ValueError('Zwei vorhandene Longmynd-Kanaele erforderlich.')
        for ch in config['channels']:
            if not ipaddress.IPv4Address(ch['ts_ip']).is_multicast:raise ValueError('Multicast-TS-Ausgaben erforderlich.')
            reserved={args.base_port+d for d in (1,21,22,41,42)}
            if ch['ts_port'] in reserved or ch.get('status_port') in reserved:raise ValueError('UDP-Ports kollidieren.')
        files=[]
        for line in (ROOT/'SHA256SUMS').read_text().splitlines():
            digest,name=line.split('  ',1);path=ROOT/name
            if not path.resolve().is_relative_to(ROOT) or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
                raise ValueError('Quell-Pruefsumme stimmt nicht: '+name)
            files.append(name)
        if args.dry_run:
            print('OpenTuner Dual Bridge',VERSION)
            print('Vorhandener Longmynd und seine Konfiguration bleiben unveraendert.')
            print('Benutzer:',user,'; UDP-Basis:',args.base_port,'; Multicast-Interface:',interface)
            print('Zusatzdienst:',UNIT.name,'; Aktivierung:',not args.no_start)
            return 0
        loaded=run(['systemctl','show','longmynd-dual.service','-p','LoadState','--value'],capture=True).stdout.strip()
        if loaded!='loaded':raise ValueError('Zuerst Longmynd Dual installieren.')
        if not args.skip_deps:
            run(['apt-get','update'],admin=True)
            run(['apt-get','install','-y','python3-websockets'],admin=True)
        run(['/usr/bin/python3','-c','from websockets.legacy.client import connect'])
        stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        release=PREFIX/'releases'/(VERSION+'-'+stamp)
        old_link=os.readlink(PREFIX/'current') if (PREFIX/'current').is_symlink() else None
        if (PREFIX/'current').exists() and old_link is None:raise ValueError('Bridge current ist kein Symlink.')
        old_active=run(['systemctl','is-active','--quiet',UNIT.name],check=False).returncode==0
        old_enabled=run(['systemctl','is-enabled','--quiet',UNIT.name],check=False).returncode==0
        with tempfile.TemporaryDirectory(prefix='ot-dual-install-') as td:
            tmp=Path(td);backup=PREFIX/'backups'/stamp
            run(['install','-d','-m','0755',release,backup],admin=True)
            for name in [*files,'SHA256SUMS']:
                run(['install','-m','0644',ROOT/name,release/name],admin=True)
            if UNIT.exists():run(['cp','-a',UNIT,backup/UNIT.name],admin=True)
            command='/usr/bin/python3 /opt/longmynd-dual/ot-dual/current/bridge.py --base-port '+str(args.base_port)+' --multicast-interface '+interface
            if args.client:command+=' --client '+args.client
            unit=f'''[Unit]
Description=OpenTuner two-channel UDP bridge for Longmynd Dual
Wants=network-online.target longmynd-dual.service
After=network-online.target longmynd-dual.service

[Service]
Type=simple
User={user}
Group=longmynd-dual
ExecStart={command}
Restart=on-failure
RestartSec=5
TimeoutStopSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
'''
            (tmp/'unit').write_text(unit)
            if old_active:run(['systemctl','stop',UNIT.name],admin=True)
            run(['install','-m','0644',tmp/'unit',UNIT],admin=True)
            run(['ln','-sfn',release,PREFIX/'current.next'],admin=True)
            run(['mv','-Tf',PREFIX/'current.next',PREFIX/'current'],admin=True)
            run(['systemctl','daemon-reload'],admin=True)
            if not args.no_start:
                try:
                    run(['systemctl','enable','--now',UNIT.name],admin=True)
                    time.sleep(2)
                    run(['systemctl','is-active','--quiet',UNIT.name])
                except BaseException:
                    run(['systemctl','disable','--now',UNIT.name],admin=True,check=False)
                    if old_link:
                        run(['ln','-sfn',old_link,PREFIX/'current'],admin=True)
                        if (backup/UNIT.name).exists():run(['cp','-a',backup/UNIT.name,UNIT],admin=True)
                        run(['systemctl','daemon-reload'],admin=True)
                        if old_enabled:run(['systemctl','enable',UNIT.name],admin=True,check=False)
                        if old_active:run(['systemctl','start',UNIT.name],admin=True,check=False)
                    raise
        print('\nZusatzdienst installiert:',UNIT.name)
        print('Longmynd-Empfangscode und /etc/longmynd-dual.json unveraendert.')
        print('OT: WinterHill Variant -> PicoTuner Ethernet (G4EWJ), Pi-IP, UDP-Basis',args.base_port)
        print('Status: systemctl status longmynd-ot-dual --no-pager -l')
        return 0
    except (OSError,ValueError,KeyError,subprocess.CalledProcessError) as e:
        print('Installation abgebrochen:',e,file=sys.stderr);return 1

if __name__=='__main__':raise SystemExit(main())
