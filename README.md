# Longmynd Dual für MiniTiouner Pro

Zwei unabhängig abstimmbare DATV-Kanäle auf einem Raspberry Pi, mit einer
optionalen Bridge für eine zweikanalige OpenTuner-Instanz und einem HTTP-Relay
für Longmynd Mobil. Community-Erweiterung auf Basis von Longmynd.

## Erprobter Raspberry-Stand

| Komponente | Version | Dienst |
| --- | --- | --- |
| Longmynd Dual | 0.1.0-beta.1 | longmynd-dual |
| OpenTuner UDP-Bridge | 0.1.0-beta.4 | longmynd-ot-dual |
| Optionales Mobil-Relay | 0.1.0-beta.2 | longmynd-mobile |

Erprobt bei DL2GHM auf Raspberry Pi 4, Debian 13 / Raspberry Pi OS 64 Bit,
DJ7TH/F6DZP MiniTiouner Pro, Serit FTS-4334L-V und zwei FT2232H-Modulen.
Beide Kanäle wurden mit separater Abstimmung und Bakenempfang bestätigt.
Das ist ein Beta-Communityprojekt; andere Platinen und Betriebssysteme sind
damit nicht automatisch verifiziert.

## Einstieg

Die [deutsche Installationsanleitung](docs/INSTALLATION-DE.md) erklärt
USB-Seriennummern, Dienste, Ports und OpenTuner-Einstellungen.
Die fertigen `.run`-Installer und das komplette Weitergabepaket gehören in
den Bereich **Releases** dieses Repositorys. Jeder Installer enthält seine
vollständigen Quellen und Lizenzhinweise.

Der Empfänger führt die gemeinsame Tunersteuerung und beide TS-Leser in
einem Prozess. Die Bridge übersetzt OpenTuners PicoTuner-Ethernet-UDP-
Protokoll in die getrennten Longmynd-WebSocket-Verbindungen 8080 und 8081.
Sie reicht beide Multicast-Streams an OT weiter, ohne Video zu transcodieren.
Eine gemeinsame OT-Instanz nutzt UDP-Basisport 9900. Ein PicoTuner ist dafür
nicht erforderlich.

**OpenTuner-Pufferfix:** Bei Martins konkreter OT-D00.12-DLL war zusätzlich
eine Korrektur der 4000-Byte-Grenze auf 4096 nötig. Hintergrund und die genaue
Versionsabgrenzung stehen in der Installationsanleitung und im Weitergabepaket.
Nicht eine fremde OT-Version ungeprüft mit dieser DLL überschreiben.

## Verzeichnisse

- Hauptverzeichnis: Longmynd-Dual-Empfänger und Installer.
- `extras/ot-dual`: zweikanalige OpenTuner-Bridge, Quellen und Tests.
- `extras/mobile`: bestätigter Pi-HTTP-/Spektrum-Relay-Stand.
- `tools`: Diagnose und lesender Export einer installierten Pi-Version.
- `docs`: Installation, Paketstand und ursprüngliche Dokumentation.

Die im historischen Mobil-Installer enthaltenen Android-Quellen sind dessen
Auslieferungsstand. Dieses Repository beschreibt den Raspberry-Stand; spätere
Android-Oberflächenversionen haben eine eigene Versionsnummer.

## Quellen und Lizenz

Longmynd-Upstream: https://github.com/philcrump/longmynd

Eigene Erweiterungen: GPL-3.0-or-later, siehe [LICENSE](LICENSE).
Herkunfts- und Lizenzhinweise von Upstream, libwebsockets und weiteren
Fremdkomponenten bleiben erhalten. Die libwebsockets-Quellen sind für dieses
Weitergabepaket vollständig enthalten.

Die [Originalbeschreibung](docs/Longmynd-Dual-Original-README.md) dokumentiert
weitere Build- und Testdetails. Historische Hinweise auf damals noch offene
Praxistests sind zusammen mit dem später bestätigten [Paketstand](docs/PAKETSTAND.json)
zu lesen. Ein echter Iststandexport vom Pi ist zusätzlich möglich; das
Repository enthält keine individuellen Zugangs- oder VPN-Schlüssel.
