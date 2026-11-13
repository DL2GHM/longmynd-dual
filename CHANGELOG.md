# Changelog

## 0.1.0-beta.1 — 2026-11-01

- Ein Prozess mit zwei unabhaengigen Demodulator-/Tuner-Konfigurationen.
- Gemeinsamer I2C-Besitzer; pro Kanal eigener USB-Leser, MPEG-Parser und UDP/WS-Ausgang.
- Auswahl von MASTER/AUX ueber USB-Seriennummer, unabhaengig von Bus-/Geraetenummern.
- Vollstaendige Dual-Registerinitialisierung nach OpenTuner; im Hardwaretest
  wurden damit die zuvor gespiegelten Datenstroeme getrennt.
- Kein regelmaessiges Warten des USB-Lesers auf die Status-Sperre. Damit
  laufen im Hardwaretest beide Bake-Streams ohne CC-Spruenge.
- Integer-JSON-Felder fuer OpenTuner, getrennte Parserzustaende und sichere JSON-Lebensdauer.
- Korrektur der P1-Symbolraten-Bytefolge und des STV6120-CTRL17-Schattens.
- Web-Kontext-Aufbau serialisiert; libwebsockets mit SMP-Unterstuetzung gebaut.
- Installationsdatei mit komplettem Quellcode, Pruefsummen und eigenem systemd-Dienst.

Die Version ist ein Community-Beta-Stand. Langzeitbetrieb und Paket-Kaltstart
muessen auf der Zielhardware noch erprobt werden.
