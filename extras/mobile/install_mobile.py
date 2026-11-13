#!/usr/bin/env python3
"""Install the optional Longmynd local/VPN mobile relay. GPL-3.0-or-later."""
import argparse
import datetime
import hashlib
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
PREFIX=Path('/opt/longmynd-dual/mobile')
UNIT=Path('/etc/systemd/system/longmynd-mobile.service')
VERSION='0.1.0-beta.2'

def run(args,admin=False,check=True,capture=False):
    cmd=list(map(str,args))
    if admin and os.geteuid()!=0:cmd.insert(0,'sudo')
    return subprocess.run(cmd,text=True,check=check,capture_output=capture)

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--port',type=int,default=8090)
    p.add_argument('--skip-deps',action='store_true');p.add_argument('--dry-run',action='store_true')
    a=p.parse_args()
    try:
        if not 1024<=a.port<=65535:raise ValueError('Invalid port')
        config=json.loads(Path('/etc/longmynd-dual.json').read_text());user=config['user']
        if not re.fullmatch(r'[a-z_][a-z0-9_-]*',user) or user=='root':raise ValueError('Invalid user')
        pwd.getpwnam(user)
        if len(config['channels'])!=2:raise ValueError('Two channels required')
        if a.port in [ch['web_port'] for ch in config['channels']]:raise ValueError('Port collides with receiver')
        files=[]
        for line in (ROOT/'SHA256SUMS').read_text().splitlines():
            digest,name=line.split('  ',1);path=ROOT/name
            if not path.resolve().is_relative_to(ROOT) or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:
                raise ValueError('Source checksum mismatch: '+name)
            files.append(name)
        if a.dry_run:
            print('Longmynd Mobile',VERSION,'port',a.port,'user',user,'files',len(files));return 0
        if run(['systemctl','show','longmynd-dual','-p','LoadState','--value'],capture=True).stdout.strip()!='loaded':
            raise ValueError('Longmynd Dual is not installed')
        if not a.skip_deps:
            run(['apt-get','update'],admin=True)
            run(['apt-get','install','-y','python3-aiohttp','python3-websockets'],admin=True)
        run(['/usr/bin/python3','-c','import aiohttp;from websockets.legacy.client import connect'])
        stamp=datetime.datetime.now().strftime('%Y%m%d-%H%M%S-%f')
        release=PREFIX/'releases'/(VERSION+'-'+stamp);backup=PREFIX/'backups'/stamp
        old_link=os.readlink(PREFIX/'current') if (PREFIX/'current').is_symlink() else None
        if (PREFIX/'current').exists() and not old_link:raise ValueError('current is not a symlink')
        active=run(['systemctl','is-active','--quiet',UNIT.name],check=False).returncode==0
        enabled=run(['systemctl','is-enabled','--quiet',UNIT.name],check=False).returncode==0
        with tempfile.TemporaryDirectory(prefix='longmynd-mobile-') as td:
            tmp=Path(td)
            unit=f'''[Unit]
Description=Longmynd Mobile local/VPN stream relay
Wants=network-online.target longmynd-dual.service
After=network-online.target longmynd-dual.service

[Service]
Type=simple
User={user}
Group=longmynd-dual
ExecStart=/usr/bin/python3 /opt/longmynd-dual/mobile/current/relay.py --port {a.port}
Restart=on-failure
RestartSec=5
TimeoutStopSec=10

[Install]
WantedBy=multi-user.target
'''
            (tmp/'unit').write_text(unit)
            run(['install','-d','-m','0755',release,backup],admin=True)
            for name in [*files,'SHA256SUMS']:
                run(['install','-D','-m','0644',ROOT/name,release/name],admin=True)
            if UNIT.exists():run(['cp','-a',UNIT,backup/UNIT.name],admin=True)
            if active:run(['systemctl','stop',UNIT.name],admin=True)
            try:
                run(['install','-m','0644',tmp/'unit',UNIT],admin=True)
                run(['ln','-sfn',release,PREFIX/'current.next'],admin=True)
                run(['mv','-Tf',PREFIX/'current.next',PREFIX/'current'],admin=True)
                run(['systemctl','daemon-reload'],admin=True)
                run(['systemctl','enable','--now',UNIT.name],admin=True)
                time.sleep(2);run(['systemctl','is-active','--quiet',UNIT.name])
            except BaseException:
                run(['systemctl','disable','--now',UNIT.name],admin=True,check=False)
                if old_link:run(['ln','-sfn',old_link,PREFIX/'current'],admin=True)
                if (backup/UNIT.name).exists():run(['cp','-a',backup/UNIT.name,UNIT],admin=True)
                else:run(['rm','-f',UNIT],admin=True)
                run(['systemctl','daemon-reload'],admin=True)
                if enabled:run(['systemctl','enable',UNIT.name],admin=True,check=False)
                if active:run(['systemctl','start',UNIT.name],admin=True,check=False)
                raise
        print('Longmynd Mobile installiert. App-Adresse: http://PI-IP:'+str(a.port))
        print('Status: systemctl status longmynd-mobile --no-pager -l')
        return 0
    except (OSError,ValueError,KeyError,subprocess.CalledProcessError) as e:
        print('Installation abgebrochen:',e,file=sys.stderr);return 1

if __name__=='__main__':raise SystemExit(main())
