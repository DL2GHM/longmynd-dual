#!/bin/sh
set -eu

service=qo100-lokalspektrum.service
config=/etc/default/qo100-lokalspektrum
target=/opt/qo100-lokalspektrum
source_dir=$(CDPATH='' cd -- "$(dirname -- "$0")" && pwd)

if [ "$#" -ne 0 ] || [ "$(id -u)" -ne 0 ]; then
    printf '%s\n' 'Aufruf: sudo sh install-service.sh' >&2
    exit 1
fi

if ! /usr/bin/python3 -c 'import aiohttp, ctypes, numpy; ctypes.CDLL("librtlsdr.so.0")' 2>/dev/null; then
    printf '%s\n' 'Bitte zuerst sudo apt install rtl-sdr python3-aiohttp python3-numpy ausführen.' >&2
    exit 1
fi

if ! getent group plugdev >/dev/null; then
    printf '%s\n' 'Die von den RTL-SDR-USB-Regeln verwendete Gruppe plugdev fehlt.' >&2
    exit 1
fi

# Preserve the shared port and RTL settings from an existing installation.
config_tmp=$(mktemp /etc/default/.qo100-lokalspektrum.XXXXXX)
trap 'rm -f "$config_tmp"' EXIT
if [ -f "$config" ]; then
    awk '/^[[:blank:]]*QO100_(PORT|RTL_DEVICE|RTL_GAIN)=/' "$config" > "$config_tmp"
fi
grep -Eq '^[[:blank:]]*QO100_PORT=' "$config_tmp" || printf 'QO100_PORT=8096\n' >> "$config_tmp"
grep -Eq '^[[:blank:]]*QO100_RTL_DEVICE=' "$config_tmp" || printf 'QO100_RTL_DEVICE=0\n' >> "$config_tmp"
grep -Eq '^[[:blank:]]*QO100_RTL_GAIN=' "$config_tmp" || printf 'QO100_RTL_GAIN=20\n' >> "$config_tmp"
chmod 0644 "$config_tmp"

install -d -m 0755 "$target"
install -m 0644 "$source_dir/qo100_spectrum.py" "$source_dir/rtl_spectrum.py" "$source_dir/index.html" "$target/"
install -m 0644 "$source_dir/$service" "/etc/systemd/system/$service"
mv "$config_tmp" "$config"

systemctl daemon-reload
systemctl enable "$service"
systemctl restart "$service"
systemctl --no-pager --full status "$service"
