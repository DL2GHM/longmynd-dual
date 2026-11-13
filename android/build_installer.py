#!/usr/bin/env python3
"""Create a self-contained Longmynd Mobile Pi installer with all own sources."""
import gzip
import hashlib
import io
from pathlib import Path
import tarfile

ROOT=Path(__file__).resolve().parent
STUB='''#!/usr/bin/env bash
set -euo pipefail
line=$(awk '/^__MOBILE_ARCHIVE_BELOW__$/ {print NR+1;exit}' "$0")
if [[ -z "$line" ]]; then echo "Archivmarkierung fehlt." >&2;exit 1;fi
if [[ "${1:-}" == "--extract-only" ]]; then
    [[ $# -eq 2 ]] || { echo "--extract-only NEUES_VERZEICHNIS" >&2;exit 1; }
    mkdir -- "$2"
    tail -n +"$line" -- "$0" | tar -xzf - -C "$2"
    exit 0
fi
work=$(mktemp -d "${TMPDIR:-/tmp}/longmynd-mobile.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
tail -n +"$line" -- "$0" | tar -xzf - -C "$work"
python3 "$work/longmynd-mobile/install_mobile.py" "$@"
exit 0
__MOBILE_ARCHIVE_BELOW__
'''

def build(output):
    files={name:(ROOT/name).read_bytes() for name in ('relay.py','bridge.py','install_mobile.py','test_relay.py','test_install_mobile.py','README.md','LICENSE','build_installer.py','build_apk.py','spectrum.py','test_spectrum.py','fixtures/batc-fft.bin')}
    for p in (ROOT/'app').rglob('*'):
        if not p.is_file() or any(x in ('.gradle','build') for x in p.relative_to(ROOT).parts) or p.name=='local.properties':continue
        files[p.relative_to(ROOT).as_posix()]=p.read_bytes()
    files['SHA256SUMS']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(files.items())).encode()
    buffer=io.BytesIO()
    with gzip.GzipFile(fileobj=buffer,mode='wb',mtime=0) as gz:
        with tarfile.open(fileobj=gz,mode='w') as tar:
            for name,data in sorted(files.items()):
                info=tarfile.TarInfo('longmynd-mobile/'+name);info.size=len(data);info.mode=0o644;tar.addfile(info,io.BytesIO(data))
    output=Path(output);output.parent.mkdir(parents=True,exist_ok=True)
    output.write_bytes(STUB.encode()+buffer.getvalue());output.chmod(0o755)
    print(output,len(output.read_bytes()),hashlib.sha256(output.read_bytes()).hexdigest())

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('output');a=p.parse_args();build(a.output)
