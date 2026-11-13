#!/usr/bin/env python3
"""systemd launcher; resolves USB serials in the receiver, never USB device numbers."""
import json
import os
from pathlib import Path
from settings import receiver_args, VERSION

def main():
    config = json.loads(Path('/etc/longmynd-dual.json').read_text())
    args = receiver_args(config)
    release = Path('/opt/longmynd-dual/current').resolve(strict=True)
    os.chdir(release / 'share')
    print(f'Longmynd Dual {VERSION}; MASTER={config["master_serial"]}; AUX={config["aux_serial"]}', flush=True)
    binary = str(release / 'bin/longmynd-dual')
    os.execv('/usr/bin/stdbuf', ['stdbuf', '-oL', '-eL', binary, *args])

if __name__ == '__main__':
    main()
