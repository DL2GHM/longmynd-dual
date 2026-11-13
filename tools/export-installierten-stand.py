#!/usr/bin/env python3
"""Read-only Longmynd Pi export. GPL-3.0-or-later; no service changes or network calls."""
import argparse
import datetime
import hashlib
import io
import json
import os
from pathlib import Path
import stat
import sqlite3
import subprocess
import tarfile
import tempfile

ROLES={
 'core':('/opt/longmynd-dual/current','source/SHA256SUMS'),
 'ot_bridge':('/opt/longmynd-dual/ot-dual/current','SHA256SUMS'),
 'mobile':('/opt/longmynd-dual/mobile/current','SHA256SUMS'),
}
SERVICES=('longmynd-dual','longmynd-ot-dual','longmynd-mobile','qo100-lokalspektrum','longmynd-web','caddy')

def digest_file(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(1024*1024),b''):h.update(block)
    return h.hexdigest()

def inside(path,parent):
    try:path.resolve().relative_to(parent.resolve());return True
    except ValueError:return False

def collect(root):
    selected={};releases={}
    def add(path,name):
        if path.is_symlink() or not path.is_file():return
        if not inside(path,root):raise ValueError('Pfad liegt ausserhalb des Dateisystems: '+str(path))
        selected[name]=path
    def tree(base,name):
        if not base.exists():return
        resolved=base.resolve()
        if not inside(resolved,root):raise ValueError('Release-Link zeigt ausserhalb des Dateisystems.')
        for path in sorted(resolved.rglob('*')):
            rel=path.relative_to(resolved)
            if any(p in ('.git','__pycache__') for p in rel.parts):continue
            if path.is_symlink() or not path.is_file():continue
            if path.suffix in ('.pyc','.keystore','.pcap','.log') or path.name=='.env':continue
            add(path,name+'/'+rel.as_posix())
    for role,(absolute,manifest) in ROLES.items():
        base=root/absolute.lstrip('/')
        if base.exists():
            releases[role]={'resolved_path':'/'+base.resolve().relative_to(root.resolve()).as_posix()}
            tree(base,'files'+absolute)
    if 'core' not in releases:raise ValueError('Kein installiertes /opt/longmynd-dual/current gefunden.')
    for absolute in ('/etc/longmynd-dual.json','/etc/udev/rules.d/70-longmynd-dual.rules',
                     '/usr/local/libexec/longmynd-dual/launcher.py','/usr/local/libexec/longmynd-dual/settings.py',
                     '/etc/default/qo100-lokalspektrum','/etc/caddy/Caddyfile',
                     '/home/pi/longmynd-browser-dual-test.py',
                     '/opt/qo100-lokalspektrum/qo100_spectrum.py',
                     '/opt/qo100-lokalspektrum/rtl_spectrum.py',
                     '/opt/qo100-lokalspektrum/index.html',
                     '/etc/os-release'):
        p=root/absolute.lstrip('/')
        # os-release is commonly a safe symlink into the same filesystem.
        if p.name=='os-release' and p.is_symlink() and inside(p,root):p=p.resolve()
        add(p,'files'+absolute)
    for service in SERVICES:
        unit='/etc/systemd/system/'+service+'.service'
        p=root/unit.lstrip('/')
        if p.is_symlink() and inside(p,root):p=p.resolve()
        add(p,'files'+unit)
        dropins=root/(unit+'.d').lstrip('/')
        if dropins.is_dir():
            for p in sorted(dropins.glob('*.conf')):add(p,'files'+unit+'.d/'+p.name)
    return selected,releases

def compare(root,reference):
    result={}
    for role,(absolute,relative) in ROLES.items():
        base=root/absolute.lstrip('/');manifest=base/relative
        expected=reference.get(role,{})
        row={'expected_version':expected.get('version'),'installed':base.exists()}
        if manifest.is_file():
            row['manifest_sha256']=digest_file(manifest)
            row['reference_manifest_matches']=row['manifest_sha256']==expected.get('source_manifest_sha256')
            errors=[];count=0
            for line in manifest.read_text().splitlines():
                if not line.strip():continue
                sha,name=line.split('  ',1);candidate=manifest.parent/name
                if not inside(candidate,manifest.parent):raise ValueError('Unsicherer Quellmanifest-Pfad: '+name)
                count+=1
                if not candidate.is_file() or digest_file(candidate)!=sha:errors.append(name)
            row['checked_source_files']=count
            row['different_or_missing_files']=errors
            row['confirmed_package_sources']=row['reference_manifest_matches'] and count>0 and not errors
        else:row['confirmed_package_sources']=False
        result[role]=row
    return result

def copy_user_database(source, destination):
    with sqlite3.connect(source.as_uri()+'?mode=ro', uri=True) as current:
        with sqlite3.connect(destination) as snapshot:
            current.backup(snapshot)


def capture_status():
    result={}
    for service in SERVICES:
        try:
            r=subprocess.run(['systemctl','show',service+'.service',
                '--property=ActiveState,SubState,MainPID,ExecStart,User,Group,FragmentPath'],
                text=True,capture_output=True,timeout=5,check=False)
            result[service]=r.stdout.strip()
        except (OSError,subprocess.TimeoutExpired) as e:result[service]='Nicht lesbar: '+str(e)
    return result

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output',type=Path,help='Neues .tar.gz; vorhandene Datei wird nicht ersetzt')
    p.add_argument('--dry-run',action='store_true',help='Nur Dateiliste und Versionsabgleich anzeigen')
    p.add_argument('--root',type=Path,default=Path('/'),help=argparse.SUPPRESS)
    p.add_argument('--web-users-db',nargs='?',const='/home/pi/longmynd-users.sqlite3',metavar='PFAD',
                   help='Webkonten-Datenbank zusätzlich in den privaten Export aufnehmen')
    a=p.parse_args();root=a.root.resolve()
    selected,releases=collect(root)
    user_db=None
    if a.web_users_db:
        db=Path(a.web_users_db)
        if not db.is_absolute():p.error('--web-users-db benötigt einen absoluten Pfad.')
        user_db=(root/str(db).lstrip('/')).resolve()
        if not inside(user_db,root) or not user_db.is_file():p.error('Webkonten-Datenbank fehlt.')
        db_member='files/'+user_db.relative_to(root).as_posix()
    reference_path=Path(__file__).resolve().with_name('paket-referenz.json')
    reference=json.loads(reference_path.read_text()) if reference_path.is_file() else {}
    stamp=datetime.datetime.now().astimezone().strftime('%Y%m%d-%H%M%S')
    report={'created_at':datetime.datetime.now().astimezone().isoformat(),
        'purpose':'Direkter installierter Longmynd-Stand; kein allgemeines Systembackup oder Installer.',
        'releases':releases,'comparison':compare(root,reference),'files':sorted([*selected,db_member] if user_db else selected)}
    if root==Path('/'):report['service_status']=capture_status()
    if a.dry_run:
        print(json.dumps(report,ensure_ascii=False,indent=2));return 0
    output=(a.output or Path('longmynd-pi-iststand-'+stamp+'.tar.gz')).resolve()
    if output.exists():raise ValueError('Zieldatei existiert bereits: '+str(output))
    if not output.parent.is_dir():raise ValueError('Zielordner fehlt.')
    checks=[]
    # Build privately and publish without overwriting a concurrent destination.
    with tempfile.TemporaryDirectory(prefix='.longmynd-export-',dir=output.parent) as td:
        temp=Path(td)/'export.tar.gz'
        with tarfile.open(temp,'w:gz') as archive:
            def add_bytes(name,data,mode=0o644):
                info=tarfile.TarInfo('longmynd-pi-iststand/'+name)
                info.size=len(data);info.mode=mode;info.mtime=int(datetime.datetime.now().timestamp())
                archive.addfile(info,io.BytesIO(data))
                checks.append(hashlib.sha256(data).hexdigest()+'  '+name)
            for name,path in sorted(selected.items()):
                before=path.stat();data=path.read_bytes();after=path.stat()
                if (before.st_mtime_ns,before.st_size)!=(after.st_mtime_ns,after.st_size):
                    raise ValueError('Datei hat sich beim Lesen geaendert; bitte erneut exportieren: '+str(path))
                add_bytes(name,data,stat.S_IMODE(before.st_mode))
            if user_db:
                database_copy=Path(td)/'web-users.sqlite3'
                copy_user_database(user_db,database_copy)
                add_bytes(db_member,database_copy.read_bytes(),0o600)
            add_bytes('ISTSTAND.json',(json.dumps(report,ensure_ascii=False,indent=2)+'\n').encode())
            add_bytes('README.txt',('Direkter Longmynd-Export. Originaldateien unter files/.\n'
                'ISTSTAND.json enthaelt Versionsabgleich und Dienststatus.\n'
                'USB-Seriennummern und Netzadressen an fremde Hardware anpassen.\n'
                'Keine automatische Installation durch dieses Archiv.\n').encode())
            info=tarfile.TarInfo('longmynd-pi-iststand/SHA256SUMS.txt')
            data=('\n'.join(checks)+'\n').encode();info.size=len(data);info.mode=0o644
            archive.addfile(info,io.BytesIO(data))
        # O_EXCL plus streaming copy also works on filesystems without hard links.
        fd=os.open(output,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        try:
            with os.fdopen(fd,'wb') as target,temp.open('rb') as source:
                while True:
                    block=source.read(1024*1024)
                    if not block:break
                    target.write(block)
        except BaseException:
            output.unlink()
            raise
    print('Export erstellt:',output)
    print('Dateien:',len(report['files']))
    print('Dienste und Konfiguration wurden nicht veraendert.')
    for role,row in report['comparison'].items():
        print(role+': '+('Quellen entsprechen dem bestaetigten Paket' if row['confirmed_package_sources'] else 'Abweichend, nicht installiert oder nicht vollstaendig verifizierbar; siehe ISTSTAND.json'))
    return 0

if __name__=='__main__':
    try:raise SystemExit(main())
    except (OSError,ValueError,KeyError,sqlite3.Error) as e:raise SystemExit('Export abgebrochen: '+str(e))
