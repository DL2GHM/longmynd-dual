#!/usr/bin/env python3
"""Build self-contained optional OT bridge installer. GPL-3.0-or-later."""
import argparse
import gzip
import hashlib
import io
from pathlib import Path
import tarfile

ROOT=Path(__file__).resolve().parent
VERSION='0.1.0-beta.4'
STUB='''#!/usr/bin/env bash
# Longmynd OpenTuner Dual Bridge — complete GPL sources included.
set -euo pipefail
line=$(awk '/^__OT_DUAL_ARCHIVE_BELOW__$/ {print NR+1;exit}' "$0")
if [[ -z "$line" ]]; then echo "Archivmarkierung fehlt." >&2;exit 1;fi
if [[ "${1:-}" == "--extract-only" ]]; then
    [[ $# -eq 2 ]] || { echo "--extract-only NEUES_VERZEICHNIS" >&2;exit 1; }
    mkdir -- "$2"
    tail -n +"$line" -- "$0" | tar -xzf - -C "$2"
    echo "Quellen: $2/longmynd-ot-dual"
    exit 0
fi
command -v python3 >/dev/null || { echo "Python 3 fehlt." >&2;exit 1; }
work=$(mktemp -d "${TMPDIR:-/tmp}/longmynd-ot-dual.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
tail -n +"$line" -- "$0" | tar -xzf - -C "$work"
python3 "$work/longmynd-ot-dual/install_bridge.py" "$@"
exit 0
__OT_DUAL_ARCHIVE_BELOW__
'''

def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
    files={n:(ROOT/n).read_bytes() for n in ['bridge.py','install_bridge.py','test_bridge.py','test_install_bridge.py','README.md','build.py']}
    license=ROOT/'LICENSE'
    if not license.exists():license=ROOT.parent.parent/'LICENSE'
    files['LICENSE']=license.read_bytes()
    files['SHA256SUMS']=''.join(hashlib.sha256(data).hexdigest()+'  '+name+'\n' for name,data in sorted(files.items())).encode()
    payload=io.BytesIO()
    with gzip.GzipFile(fileobj=payload,mode='wb',mtime=0) as gz:
        with tarfile.open(fileobj=gz,mode='w') as tar:
            for name,data in sorted(files.items()):
                info=tarfile.TarInfo('longmynd-ot-dual/'+name);info.size=len(data);info.mode=0o644;tar.addfile(info,io.BytesIO(data))
    out=a.output/f'longmynd-ot-dual-{VERSION}.run'
    out.write_bytes(STUB.encode()+payload.getvalue());out.chmod(0o755)
    print(out,out.stat().st_size,hashlib.sha256(out.read_bytes()).hexdigest())

if __name__=='__main__':main()
