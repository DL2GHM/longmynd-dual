# Validation — 0.1.0-beta.1

## Hardware, 2026-11-01

Reported by DL2GHM on Raspberry Pi 4 Model B Rev 1.5 / 2 GB,
Debian 13 Trixie aarch64, MiniTiouner Pro DJ7TH / FTS-4334L-V,
two FT2232H interfaces at USB high speed (480 Mbit/s).

- Two independent stations received with picture and sound in separate
  OpenTuner instances; frequency and symbol rate independently controlled.
- Both receivers on the QO-100 A71A beacon at the same time.
- Final eight-second measurement: 2407.2 and 2376.6 kbit/s;
  both TEI=0, continuity-counter jumps=0. Initial resynchronization discarded
  128 and 72 bytes respectively, compatible with joining mid-packet.
- Before the USB-flow correction the beacon had 111 CC jumps and 12968
  resynchronization bytes in an eight-second local UDP measurement.
- A USB capture confirmed errors before UDP output (114 CC jumps on the
  beacon, zero on the other station). The receiver was found waiting on a
  status mutex during slow I2C reporting. Publishing TS destination metadata
  only on configuration changes removed the recurring reader stall.

The installer and its new multi-context WebSocket library build still need
an install/reboot/long-duration run on the physical Pi. Do not describe this
beta as broadly hardware-qualified.

## Automated validation, Linux x86-64

- Build with the bundled libwebsockets revision, LWS_MAX_SMP=2,
  LWS_WITH_NETLINK=OFF.
- Five integration tests against real TS parsing, UDP and WebSocket code,
  with simulated USB/NIM hardware: all pass with ASan/UBSan enabled.
- Slow-report regression: a simulated 200ms hardware read reproduces a
  recurring >200ms reader pause with the previous TS code; the corrected
  reader passes the maximum-gap check after startup.
- Register-level test: channel retuning leaves peer PLL/demod registers
  untouched; P1 symbol-rate byte order; correct dual/single register profile.
- Five additional repeated control/monitor connect/disconnect test runs pass.
- Four installer tests pass: explicit serials / distinct endpoints,
  damaged or escaping source manifest rejection, previous service restored
  after activation timeout; unmanaged receiver processes are not taken over. Installer tests mock privileged operations and
  do not install anything on the build host.
- Leak detection is disabled for the external library; no claim of complete
  leak or race freedom. Hardware tests do not substitute for these tests,
  and simulated tests do not prove reception on other boards.

## Packaged-source verification

- Self-extracting archive export and read-only installer plan verified.
- Fresh build from extracted sources completed successfully with two-way
  parallel compilation and a fresh libwebsockets configuration.
- Embedded SHA256 manifest validates the packaged source files.
