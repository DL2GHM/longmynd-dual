#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
python3 tests/test_installer.py
cc=${CC:-gcc}
extra_include=()
extra_link=()
if [[ -n "${LONGMYND_DEPS_ROOT:-}" ]]; then
    extra_include=(-I"$LONGMYND_DEPS_ROOT/usr/include")
    extra_link=(-L"$LONGMYND_DEPS_ROOT/usr/lib/$("$cc" -dumpmachine)")
fi
flags=(-O1 -g -fsanitize=address,undefined -fno-omit-frame-pointer -Wall -Wextra -pthread -D_GNU_SOURCE -I. -Iweb/libwebsockets/build/include)
"$cc" "${flags[@]}" "${extra_include[@]}" \
    main.c dual.c nim.c ftdi.c stv0910.c stv0910_utils.c stvvglna.c stvvglna_utils.c \
    stv6120.c stv6120_utils.c ftdi_usb.c fifo.c udp.c beep.c ts.c libts.c web/web.c tests/mock_hardware.c \
    -Lweb/libwebsockets/build/lib "${extra_link[@]}" -Wl,--wrap=ftdi_usb_ts_read \
    -lusb-1.0 -lm -lasound -ljson-c -Wl,-Bstatic -lwebsockets -Wl,-Bdynamic -o tests/longmynd-mock
# Submodule library uses process-global allocations; receiver memory/UB checks stay enabled.
ASAN_OPTIONS=detect_leaks=0 python3 tests/integration.py
"$cc" "${flags[@]}" "${extra_include[@]}" -ffunction-sections -fdata-sections \
    tests/registers.c dual_hardware.c stv0910.c stv0910_utils.c stv6120.c stv6120_utils.c \
    -Wl,--gc-sections -lm -o tests/register-test
ASAN_OPTIONS=detect_leaks=0 tests/register-test
