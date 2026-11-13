# Longmynd Dual auf dem Raspberry Pi – für Rudy

Stand: 03.11.2026 · zuletzt von DL2GHM bestätigte Installationspakete.

## Was die Komponenten machen

**Longmynd Dual** betreibt beide Demodulatoren des Serit-Tuners in einem Prozess.
Eine gemeinsame Hardwareansteuerung verhindert, dass zwei getrennte Longmynd-
Prozesse sich beim Initialisieren und Abstimmen gegenseitig stören. Zwei
FT2232H-USB-Module liefern die getrennten Transportströme. Frequenz, Symbolrate,
Lock, MER und Dienstinformationen werden pro Kanal getrennt geführt.

Die **OT-Bridge** übersetzt das vorhandene zweikanalige UDP-Protokoll von
OpenTuner (PicoTuner-Ethernet-Modus) in die beiden Longmynd-WebSocket-
Steuerverbindungen. Sie liest die Multicast-Streams und reicht vollständige
TS-Pakete an OpenTuner weiter. Dabei wird kein Video transcodiert und kein
PicoTuner benötigt. Die Multicast-Ziele des Empfängers bleiben unverändert.

Das **optionale Mobil-Relay** bietet beide Streams, Status, Abstimmung und das
öffentliche BATC-Spektrum über HTTP für Longmynd Mobil an. PC und Mobilgeräte
benutzen dieselben zwei Hardwarekanäle: Wer abstimmt, verändert den jeweiligen
Kanal für alle Zuschauer. Die Android-APK ist nicht Teil dieses Raspberry-Pakets.

| Komponente | Dienst | Installationspfad |
| --- | --- | --- |
| Longmynd Dual Beta 1 | `longmynd-dual` | `/opt/longmynd-dual/current` |
| OT-Bridge Beta 4 | `longmynd-ot-dual` | `/opt/longmynd-dual/ot-dual/current` |
| Mobil-Relay Beta 2, optional | `longmynd-mobile` | `/opt/longmynd-dual/mobile/current` |

## Voraussetzungen

Erprobt: Raspberry Pi 4 mit 2 GB, Raspberry Pi OS / Debian 13 Trixie, 64 Bit
(aarch64), DJ7TH/F6DZP MiniTiouner Pro mit Serit FTS-4334L-V
(STV0910/STV6120) und zwei korrekt angeschlossenen/programmierten FT2232H-
USB-Modulen. MASTER ist das Modul für die gemeinsame I²C-/Hardwaresteuerung,
AUX das zusätzliche TS-Modul. Die Zuordnung muss zur tatsächlichen Platine
passen; zwei beliebige vollständige USB-Tuner sind damit nicht gleichzusetzen.
Die Installer programmieren keine FTDI-EEPROMs.

Ein normaler Benutzer mit sudo-Rechten und Internet für APT ist nötig.
Der Empfänger wird auf Rudys Pi aus den mitgelieferten Quellen kompiliert.
Andere Hardware-/OS-Kombinationen sind nicht durch Martins Betrieb bestätigt.
Die LNB-Spannung muss hardwareseitig bereitstehen; unsere Dual-Version und
Bridge aktivieren sie nicht per Software.

## Installation

ZIP in einen Ordner auf dem Pi entpacken, Terminal dort öffnen. Als normaler
Benutzer arbeiten, nicht eine dauerhafte Root-Shell verwenden.

1. USB-Seriennummern anzeigen, ohne etwas zu installieren:

   ```bash
   cd 01-Raspberry
   bash longmynd-dual-0.1.0-beta.1.run --list-usb
   ```

2. Mit **Rudys eigenen** Seriennummern zunächst den Plan prüfen. Die Namen
   `MASTER_SERIAL` und `AUX_SERIAL` sind durch die ausgelesenen Werte zu ersetzen:

   ```bash
   bash longmynd-dual-0.1.0-beta.1.run --user "$(id -un)" \
     --master MASTER_SERIAL --aux AUX_SERIAL --start --dry-run
   ```

3. Dann denselben Befehl ohne `--dry-run` ausführen:

   ```bash
   bash longmynd-dual-0.1.0-beta.1.run --user "$(id -un)" \
     --master MASTER_SERIAL --aux AUX_SERIAL --start
   ```

   Ein manuell laufender Longmynd muss vorher beendet werden. Der Installer
   löst den alten `longmynd.service` ab und richtet `longmynd-dual` mit USB-
   Rechten und Autostart ein. Konfiguration: `/etc/longmynd-dual.json`.
   Vorherige Konfiguration/Unit werden gesichert. Andere Funkdienste werden
   nicht umkonfiguriert. Standardmäßig starten beide Kanäle mit 741538 kHz IF
   und 1500 kS/s; beide sind zunächst auf die Bake eingestellt.

4. Nach erfolgreichem Dual-Empfang die Bridge installieren:

   ```bash
   bash longmynd-ot-dual-0.1.0-beta.4.run
   ```

5. Nur bei gewünschtem Android-/HTTP-Zugriff zusätzlich:

   ```bash
   bash longmynd-mobile-0.1.0-beta.2.run
   ```

## OpenTuner am PC

Quelle **WinterHill Variant**, darin **PicoTuner Ethernet (G4EWJ)** auswählen.
Pi-IP eintragen, **UDP Base Port 9900**. LNB-Offset pro Kanal bei einem
9750-MHz-LNB: **9750000 kHz**. Für andere LNBs entsprechend anpassen.
Nur eine zweikanalige Netzwerk-OT-Instanz an dieser Bridge verwenden.
Andere, direkt per USB angeschlossene OpenTuner-Instanzen können weiterlaufen.

| Verbindung | Kanal 1 | Kanal 2 |
| --- | --- | --- |
| Longmynd HTTP/WebSocket am Pi | TCP 8080 | TCP 8081 |
| Originaler TS-Multicast | 230.0.0.12:4003 | 230.0.0.13:4005 |
| Originaler Status-Multicast | 230.0.0.12:4002 | 230.0.0.13:4004 |
| OT-Abstimmkommando zum Pi | UDP 9921 | UDP 9922 |
| Video von der Bridge zum PC | UDP 9941 | UDP 9942 |

OT-Status beider Kanäle geht an UDP **9901** am PC. Die Bridge lernt dessen
IP aus gültigen Abstimmkommandos. Eingehendes UDP für die tatsächlich genutzte
`opentuner.exe` im privaten Windows-Netz erlauben. Der Longmynd-Modus mit
zwei separaten OT-Instanzen auf 8080/8081 bleibt alternativ möglich.

**Pufferfix beachten:** In Martins OT D00.12 begrenzte `ReadTSGeneric` die
Abholung auf 4000 Bytes, während der Worker erst bei 4096 Bytes sofort
weiterliest. Das verursachte bei der Bake wachsende Rückstände und Klötzchen,
obwohl der Pi saubere Streams lieferte. Die Korrektur auf 4096 wurde im Betrieb
bestätigt. Unter `02-OpenTuner-Pufferfix` liegen Details und Quellen. Die DLL
ist nur für die dort exakt benannte Ausgangsversion gedacht; bei einer anderen
Version den Quellfix prüfen und passend bauen, nicht blind ersetzen.

Die D-Anzeige ist eine Referenzschätzung aus MER minus ModCod-Schwelle;
sie ist keine zusätzliche Hardwaremessung.

## Prüfen und bedienen

```bash
systemctl is-active longmynd-dual longmynd-ot-dual
python3 /opt/longmynd-dual/current/source/tools/longmynd-diagnose.py
sudo journalctl -u longmynd-dual -u longmynd-ot-dual -n 40 --no-pager
```

Zwei unterschiedliche Stationen wählen und prüfen, dass sich nur der jeweils
gewünschte Kanal ändert. Danach beide auf die Bake stellen und einige Minuten
beobachten. TEI=0 und keine fortlaufenden CC-Sprünge sind bei sauberem Empfang
gewünscht. Wenige anfängliche Sync-Verlustbytes beim Einstieg in den TS sind
für sich allein noch kein fortlaufender Datenverlust.

```bash
sudo systemctl restart longmynd-dual
sudo systemctl restart longmynd-ot-dual
sudo systemctl stop longmynd-dual
sudo systemctl start longmynd-dual
```

Nach dem Stoppen nur der Bridge bleibt Longmynd selbst in Betrieb. Nach einer
Änderung der Core-Konfiguration auch Bridge und optionales Relay neu starten.
Nach erfolgreichem Test rebooten und den Autostart kontrollieren.

## Mobil-Relay und VPN, optional

App-Adresse: `http://PI-IP:8090`. Pro Kanal sind mehrere HTTP-Zuschauer möglich;
es bleiben dieselben zwei abstimmbaren Empfänger. Das Spektrum stammt vom
öffentlichen BATC-/AMSAT-UK-Empfänger, nicht von einer lokalen FFT des Tuners.
Der Relay-Dienst hat keine eigene Anmeldung und ist für Heimnetz oder eigenes
VPN vorgesehen. Auf Martins FRITZ!Box wurde WireGuard eingerichtet und der
Mobilfunkzugriff bei ausgeschaltetem WLAN erfolgreich getestet.

## Quellen und genauer Iststand

Jede `.run`-Datei enthält vollständige Quellen, Prüfsummen und Lizenzhinweise.
Ohne Installation entpacken, jeweils mit einem noch nicht existierenden Ziel:

```bash
bash 01-Raspberry/longmynd-dual-0.1.0-beta.1.run --extract-only core-quellen
bash 01-Raspberry/longmynd-ot-dual-0.1.0-beta.4.run --extract-only bridge-quellen
bash 01-Raspberry/longmynd-mobile-0.1.0-beta.2.run --extract-only mobil-quellen
```

Für einen direkten Export des laufend installierten Stands auf Martins Pi
im Paket-Hauptordner ausführen:

```bash
python3 03-Werkzeuge/export-installierten-stand.py
```

Das erzeugt ein zusätzliches `.tar.gz` mit den aktuell installierten Programm-
und Quelldateien, der Longmynd-Konfiguration, den zugehörigen Units/Drop-ins,
USB-Regeln, Prüfsummen und einem Versionsabgleich zu diesem Paket. Es startet
keinen Dienst neu und ändert keine Konfiguration. Der Export enthält lokale
USB-Seriennummern und gegebenenfalls IP-Adressen; Rudy muss sie anpassen.
SSH- und WireGuard-Schlüssel, WLAN-Zugangsdaten und Android-Signierschlüssel
gehören nicht zum Export. Es ist ein prüfbarer Standabzug, kein automatischer
Installer für eine andere Hardware. Die Installer oben sind der normale Weg.

Eigene Erweiterungen: GPL-3.0-or-later. Upstream und Fremdbibliotheken behalten
ihre jeweiligen Lizenzhinweise. Das gesamte Paket lässt sich weitergeben;
Zugänge oder Schlüssel von Martins Station sind nicht enthalten.
