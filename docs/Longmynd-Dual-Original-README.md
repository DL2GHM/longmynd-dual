# Longmynd Dual

Zwei unabhaengig abstimmbare DVB-S/S2-Empfaenger auf einem MiniTiouner Pro mit
Serit STV0910/STV6120 und zwei FT2232H-USB-Modulen. Fuer Linux / Raspberry Pi.

**Version 0.1.0-beta.1, 01.11.2026.** Community-Betaversion, kein offizielles
Release des urspruenglichen Longmynd-Projekts.

## Erprobter Stand

Mit DL2GHM auf Raspberry Pi 4 Model B (2 GB), Debian 13 Trixie ARM64 und
MiniTiouner Pro von DJ7TH getestet:

- Zwei unterschiedliche Stationen mit Bild und Ton in zwei OpenTuner-Instanzen.
- Unabhaengige Frequenz- und Symbolratensteuerung ueber WebSockets.
- QO-100-Bake gleichzeitig auf beiden Kanaelen mit jeweils rund 2,4 Mbit/s.
- Letzte Diagnose ueber acht Sekunden: beide Kanaele TEI=0, CC-Spruenge=0.

Ein Langzeit- und Kaltstarttest des Installationspakets steht noch aus.
Python 3.9 oder neuer wird fuer Installer/Launcher benoetigt.
Andere Boards und Betriebssysteme sind bislang nicht am Geraet bestaetigt.

## Hardware

Benoetigt werden ein passender Dual-NIM und zwei korrekt konfigurierte,
getrennt per USB angeschlossene FT2232H-Module. MASTER ist das bereits
vorhandene Modul fuer gemeinsame I2C-Steuerung und einen Transportstrom.
AUX liefert den zweiten Transportstrom ueber Interface B. AUX-Interface A
wird nicht als I2C- oder Reset-Ausgang initialisiert.

Nicht nur auf die Produkttexte TS1/TS2 verlassen: sie koennen unterschiedlich
benannt sein. Die Module werden anhand ihrer eindeutigen USB-Seriennummern
zugeordnet. Bei der getesteten DJ7TH-Platine heisst MASTER im EEPROM TS2 und
AUX TS1. EEPROM-Konfiguration und Steckrichtung muessen zur Platine passen.

Offizielle Hardware-Unterlagen: https://download.dj7th.de/
Das Installationspaket programmiert keine EEPROMs.

## Installation mit einer Datei

`longmynd-dual-0.1.0-beta.1.run` auf den Pi kopieren. Einen noch manuell
laufenden Longmynd-Test vorher mit Strg+C beenden. Als normaler Benutzer:

```bash
bash longmynd-dual-0.1.0-beta.1.run --list-usb
bash longmynd-dual-0.1.0-beta.1.run --user pi --master MASTER_SERIAL --aux AUX_SERIAL --start
```

`MASTER_SERIAL` und `AUX_SERIAL` durch die eigenen Seriennummern ersetzen.
Die Datei entpackt die enthaltenen Quellen, installiert die Build-Abhaengigkeiten
ueber APT und kompiliert auf dem Ziel-Pi. Internet wird nur fuer APT benoetigt;
alle Longmynd- und libwebsockets-Quellen liegen bei. `sudo` wird fuer die
Systeminstallation verwendet. Es gibt kein Upgrade des gesamten Betriebssystems.

Vorherige Ansicht ohne Aenderungen:

```bash
bash longmynd-dual-0.1.0-beta.1.run --user pi --master MASTER_SERIAL --aux AUX_SERIAL --start --dry-run
```

Weitere Optionen: `--skip-deps` (Pakete bereits installiert), `--jobs 2`
(Compiler-Parallelitaet), `--help`. Ohne `--start` erfolgt keine erstmalige
Aktivierung des neuen Dienstes; ein bereits laufender Dual-Dienst wird nach
seiner Aktualisierung wieder gestartet.

Der Installer verwendet `/opt/longmynd-dual/releases/` und einen `current`-
Symlink. Die Originalinstallation, z.B. `/home/pi/longmynd`, bleibt erhalten.
Bei `--start` wird der alte `longmynd.service` deaktiviert und angehalten;
`longmynd-dual.service` wird aktiviert und gestartet. APRS wird nicht angefasst.
Die USB-Regeln betreffen nur die beiden angegebenen Seriennummern.

Status / Logs / Neustart:

```bash
systemctl status longmynd-dual --no-pager -l
sudo journalctl -u longmynd-dual -n 60 --no-pager
sudo systemctl restart longmynd-dual
```

Konfiguration: `/etc/longmynd-dual.json` (vorhandene Werte werden bei erneuter
Installation uebernommen). Nach Bearbeitung den Dual-Dienst neu starten.
Startfrequenzen sind ZF in kHz; Symbolraten stehen in kS/s. Beide Kanaele
verwenden anfangs den oberen RF-Eingang. `rf_bottom: true` waehlt den unteren.

## OpenTuner / VLC

Pro Kanal eine eigene OpenTuner-Instanz. Fuer beide:
Control Interface `Websocket (M0DNY)`, IP-Adresse des Pi, Test mode **aus**.
Bei QO-100 mit 9750-MHz-LNB-LO: Frequency Offset `9750000` kHz.

| Einstellung | Kanal 1 | Kanal 2 |
|---|---|---|
| Web / WebSocket | PI_IP:8080 | PI_IP:8081 |
| TS-Adresse | 230.0.0.12 | 230.0.0.13 |
| TS-Port | 4003 | 4005 |
| UDP-Status | 230.0.0.12:4002 | 230.0.0.13:4004 |
| Start-ZF | 741538 kHz | 741538 kHz |
| Start-Symbolrate | 1500 kS/s | 1500 kS/s |

VLC-Netzwerkstreams: `udp://@230.0.0.12:4003` bzw.
`udp://@230.0.0.13:4005`. Das lokale Netz muss Multicast transportieren koennen.
Die Websteuerung hat keine Anmeldung; fuer den Betrieb im eigenen LAN gedacht.

Diagnose direkt auf dem Pi:

```bash
python3 /opt/longmynd-dual/current/source/tools/longmynd-diagnose.py
```

Die Diagnose erwartet die Standard-Webports 8080 und 8081. TEI und
CC-Spruenge sollten bei stabilem Empfang null bleiben. Einzelne beim Beginn
der Messung verworfene Sync-Bytes koennen vom Einstieg mitten in ein Paket
kommen und beweisen fuer sich keinen fortlaufenden Fehler.

## Zurueck zum bisherigen Ein-Kanal-Dienst

Wenn vorher `longmynd.service` eingerichtet war:

```bash
sudo systemctl disable --now longmynd-dual
sudo systemctl enable --now longmynd
```

Jede Installation sichert Konfiguration und vorhandene Dual-Unit unter
`/var/lib/longmynd-dual/before-*`. Vorherige Dual-Releases werden aufbewahrt.
Bei fehlgeschlagenem Start versucht der Installer den zuvor aktiven Dienst
wiederherzustellen. Ein vorhandener manueller Empfaenger wird nicht beendet.

## Quellcode / selber bauen

Das Quellarchiv enthaelt dieselben Quellen wie die Installationsdatei.
Aus der `.run`-Datei lassen sie sich ohne Installation gewinnen:

```bash
bash longmynd-dual-0.1.0-beta.1.run --extract-only ./longmynd-dual-source
cd longmynd-dual-source/longmynd-dual
sudo apt-get install build-essential cmake pkg-config git python3 libusb-1.0-0-dev libasound2-dev libjson-c-dev
make -j2
```

Das Zielverzeichnis fuer `--extract-only` darf noch nicht existieren.
Die vorhandene Ein-Kanal-Aufrufsyntax bleibt verfuegbar. Im Dual-Modus:

```bash
./longmynd --dual MASTER_SERIAL AUX_SERIAL \
  -r -1 -W 8080 -i 230.0.0.12 4003 -I 230.0.0.12 4002 741538 1500 --next \
  -r -1 -W 8081 -i 230.0.0.13 4005 -I 230.0.0.13 4004 741538 1500
```

Pro Kanal eine Frequenz/Symbolrate und UDP-Ausgabe; keine Frequenzlisten,
FIFO-Ausgaben oder MER-Tonausgabe im Dual-Modus. LNB-Spannungssteuerung ist
im Dual-Modus deaktiviert; externe/manuelle LNB-Versorgung verwenden.

Entwicklertests: `python3 -m pip install websockets` in einer passenden
Entwicklungsumgebung und anschliessend `bash tests/run.sh`.
Der eigentliche Receiver und der Installer benoetigen kein Python-Paket von pip.

## Herkunft / Lizenz

Basis: https://github.com/philcrump/longmynd, Commit `441ae5a`.
Copyright 2019 Heather Lomond; urspruengliche Anleitung: `UPSTREAM-README.md`.
Receiver und Erweiterungen: GPL-3.0-or-later, siehe `LICENSE`.

Die Dual-Registerfolge wurde aus Tom Van den Bons OpenTuner uebernommen,
das seinerseits diesen Treibercode aus Longmynd portiert hat:
https://github.com/tomvdb/open_tuner . Herkunft und Pruefsumme des verwendeten
Quellstands stehen in `stv0910_dual_regs_init.h`.

libwebsockets: Commit `58af7b4409c60da4cef8ee36b13fdb980574968c`, Quellen
unter `web/libwebsockets` einschliesslich ihrer eigenen Lizenzdateien.
Der Paket-Build aktiviert Thread-Unterstuetzung und deaktiviert die hier
nicht benoetigte Netlink-Ueberwachung fuer die zwei getrennten Web-Kontexte.

Siehe `CHANGELOG.md`, `TEST-RESULTS.md` und `GITHUB-PUBLISH.md`.
