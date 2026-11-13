#!/usr/bin/env python3
"""Build self-contained source and .run releases from tracked files. GPL-3.0-or-later."""
import argparse
import gzip
import hashlib
import io
from pathlib import Path
import subprocess
import tarfile

ROOT = Path(__file__).resolve().parent.parent
VERSION = (ROOT/'VERSION').read_text().strip()

STUB = '''#!/usr/bin/env bash
# Longmynd Dual VERSION — GPL-3.0-or-later, complete sources embedded.
set -euo pipefail
payload_line=$(awk '/^__LONGMYND_DUAL_ARCHIVE_BELOW__$/ {print NR+1; exit}' "$0")
if [[ -z "$payload_line" ]]; then echo "Archivmarkierung fehlt." >&2; exit 1; fi
if [[ "${1:-}" == "--extract-only" ]]; then
    if [[ $# -ne 2 ]]; then echo "Aufruf: bash $0 --extract-only NEUES_VERZEICHNIS" >&2; exit 1; fi
    mkdir -- "$2"
    tail -n +"$payload_line" -- "$0" | tar -xzf - -C "$2"
    echo "Quellen entpackt: $2/longmynd-dual"
    exit 0
fi
if ! command -v python3 >/dev/null; then echo "Python 3 wird benoetigt." >&2; exit 1; fi
build_dir=$(mktemp -d "${TMPDIR:-/tmp}/longmynd-dual.XXXXXXXX")
trap 'rm -rf -- "$build_dir"' EXIT
tail -n +"$payload_line" -- "$0" | tar -xzf - -C "$build_dir"
python3 "$build_dir/longmynd-dual/install.py" "$@"
exit 0
__LONGMYND_DUAL_ARCHIVE_BELOW__
'''

def paths():
    if (ROOT/'.git').exists():
        raw = subprocess.check_output(['git','ls-files','--recurse-submodules','-z'], cwd=ROOT)
        result = raw.decode().split('\0')
    else:
        result = [l.split('  ',1)[1] for l in (ROOT/'SHA256SUMS').read_text().splitlines()]
    return sorted(set(x for x in result if x and x not in ('.gitmodules','SHA256SUMS')))

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, default=ROOT.parent/'deliverables')
    args = p.parse_args(); args.output.mkdir(parents=True,exist_ok=True)
    files = paths()
    manifest = []
    for name in files:
        source = ROOT/name
        if not source.is_file() or not source.resolve().is_relative_to(ROOT):
            raise SystemExit('Missing/escaping source file: '+name)
        if any(t in name for t in ('__pycache__','before-reference-','ts-before.c','.pcap')):
            raise SystemExit('Unexpected private/test artifact: '+name)
        manifest.append(hashlib.sha256(source.read_bytes()).hexdigest()+'  '+name+'\n')
    archive = args.output/f'longmynd-dual-{VERSION}-source.tar.gz'
    with archive.open('wb') as out:
        with gzip.GzipFile(filename='',mode='wb',fileobj=out,mtime=0) as gz:
            with tarfile.open(mode='w',fileobj=gz) as tar:
                for name in files:
                    source = ROOT/name
                    info = tar.gettarinfo(str(source), arcname='longmynd-dual/'+name)
                    info.mtime=0;info.uid=info.gid=0;info.uname=info.gname='root'
                    with source.open('rb') as data: tar.addfile(info, data if info.isfile() else None)
                data = ''.join(manifest).encode()
                info=tarfile.TarInfo('longmynd-dual/SHA256SUMS');info.size=len(data);info.mode=0o644
                tar.addfile(info,io.BytesIO(data))
    installer = args.output/f'longmynd-dual-{VERSION}.run'
    installer.write_bytes(STUB.replace('VERSION',VERSION).encode()+archive.read_bytes())
    installer.chmod(0o755)
    for f in (installer,archive):
        print(hashlib.sha256(f.read_bytes()).hexdigest(), f, f.stat().st_size)

if __name__ == '__main__': main()
