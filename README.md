# Longmynd Dual für MiniTiouner Pro

Zwei unabhängig abstimmbare DATV-Empfänger für QO-100 auf einem Raspberry Pi.
Der Aufbau wurde bei DL2GHM auf einem Pi 4 mit Debian 13 (64 Bit),
MiniTiouner Pro, Serit FTS-4334L-V und zwei FT2232H-Modulen erprobt.

| Komponente | Stand | Anschluss |
| --- | --- | --- |
| Longmynd Dual | 0.1.0-beta.1 | TCP 8080 und 8081 |
| [OpenTuner-Bridge](extras/ot-dual) | 0.1.0-beta.4 | UDP-Basisport 9900 |
| [Mobil-Relay](extras/mobile/README-PI.md) | Beta 2 mit Android-Anwesenheitserkennung | HTTP 8090 |
| [RTL-Lokalspektrum](extras/lokalspektrum) | Paketstand vom 11. November 2026 | HTTP 8096 |
| [Webclient](extras/webclient) | V53 | HTTP 8092 |

Der [Einstieg aus dem GitHub-Quellcode](docs/GITHUB-START.md) führt durch
Build und Installation. Die [Installationsanleitung](docs/INSTALLATION-DE.md)
erklärt USB-Zuordnung und Dienste im Detail. Longmynd Mobil verbindet sich
mit dem Relay über `http://PI-IP:8090`. Das RTL-Lokalspektrum lässt sich
in der App als eigene Spektrumquelle auswählen.

## Android

[Longmynd Mobil 0.1.0-beta.39](android) verbindet sich mit dem Mobil-Relay.
Im Verbindungsfeld die Adresse des eigenen Raspberry Pi mit Port 8090 eintragen.

## Herkunft und Lizenz

Longmynd Dual ist eine Community-Erweiterung des [Originalprojekts
Longmynd von Phil Crump und den Mitwirkenden](https://github.com/philcrump/longmynd).
Die [Originalbeschreibung](docs/Longmynd-Dual-Original-README.md) enthält
die ursprünglichen Build- und Testdetails. Die eigenen Longmynd-Dual-
Erweiterungen stehen unter [GPL-3.0-or-later](LICENSE). Die Lizenzhinweise
der Fremdkomponenten bleiben erhalten.
