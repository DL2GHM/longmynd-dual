# Eine OpenTuner-Instanz fuer beide Longmynd-Kanaele

**Optionale Beta-Erweiterung 0.1.0-beta.4.** Voraussetzung: das bereits
installierte und getestete Longmynd Dual mit `/etc/longmynd-dual.json` und
`longmynd-dual.service`. Der vorhandene Empfangscode wird nicht ersetzt und
seine Multicast-Ziele werden nicht veraendert.

OpenTuner hat im normalen Longmynd-Client nur einen Empfaenger. Im Modus
**WinterHill Variant / PicoTuner Ethernet (G4EWJ)** unterstuetzt es bereits
zwei Empfaenger in einer Instanz. Die Erweiterung uebersetzt diese UDP-Steuerung
in zwei Longmynd-WebSocket-Verbindungen und leitet die TS-Inhalte beider Multicast-Stroeme
an die zwei UDP-Videoports von OpenTuner weiter. Es wird keine PicoTuner-Hardware
benoetigt. OpenTuner bleibt unveraendert.

## Installation auf dem Pi

Die `.run`-Datei auf den Pi kopieren und als Benutzer pi starten:

```bash
bash longmynd-ot-dual-0.1.0-beta.4.run
```

Das Paket installiert `python3-websockets` aus Debian ueber APT und legt einen
separaten Dienst `longmynd-ot-dual.service` an. Es gibt keine Neukompilation
und keinen Neustart des laufenden Longmynd-Empfaengers. Installation erfordert
sudo; die laufende Bridge verwendet denselben normalen Benutzer wie Longmynd.
Die Abhaengigkeit ist in Debian 13 Trixie als Paket vorhanden.

Optionen: `--base-port 9900`, `--client PC_IP` (optional nur diesen PC akzeptieren),
`--multicast-interface PI_LAN_IP` (bei Bedarf die LAN-IP des Pi fuer Multicast),
`--skip-deps`, `--no-start`, `--dry-run`.

## Einstellungen in OpenTuner

Nur die beiden bisherigen **Netzwerk-OT-Instanzen fuer diesen Pi** schliessen.
Die anderen OT-Instanzen fuer direkt angeschlossene USB-Empfaenger koennen
weiterlaufen. Eine der bisherigen Netzwerk-Instanzen verwenden:

1. Als Quelle **WinterHill Variant** auswaehlen.
2. In dessen Settings als Default Interface **PicoTuner Ethernet (G4EWJ)** waehlen.
3. Im Bereich **WinterHill (PicoTuner Ethernet)** die IP des Pi eintragen.
4. **Udp Base Port: 9900**.
5. Default Freq Offset fuer Tuner 1 und 2: **9750000** kHz bei einem
   9750-MHz-LNB fuer QO-100; bei anderen LNBs passend anpassen.
6. Save und mit dieser Quelle verbinden.

Die WebSocket-Einstellungen des WinterHill-Dialogs werden in diesem Modus
nicht verwendet. Das Programm sollte genau zwei Videoempfaenger anzeigen.
Beide lassen sich ueber die gewohnten OT-Bedienelemente separat abstimmen,
stummschalten, aufzeichnen usw.

Bei Basisport 9900:

| Zweck | Port |
|---|---|
| Steuerung Kanal 1 / Kanal 2 am Pi | UDP 9921 / 9922 |
| Status an den PC | UDP 9901 |
| Video an den PC, Kanal 1 / Kanal 2 | UDP 9941 / 9942 |

Die PC-Adresse wird aus den gueltigen Abstimmkommandos erkannt. OpenTuner
sendet beim Verbinden je ein Startkommando fuer beide Kanaele. Eingehender
UDP-Verkehr muss fuer diese opentuner.exe im privaten Windows-Netz erlaubt sein.
Die bisherige Websteuerung auf 8080/8081 bleibt erreichbar.

## Pruefen / Zurueck

```bash
systemctl status longmynd-ot-dual --no-pager -l
sudo journalctl -u longmynd-ot-dual -n 40 --no-pager
```

Im Log sollten je Kanal Monitor-/Control-Verbindungen und nach OT-Verbindung
auch die erkannte PC-IP und Abstimmkommandos erscheinen. Dann unterschiedliche
Stationen waehlen und unabhaengiges Umschalten pruefen. Erst danach den
Neustarttest durchfuehren.

Rueckkehr zu den bisherigen zwei Longmynd-OT-Instanzen:

```bash
sudo systemctl disable --now longmynd-ot-dual
```

Danach die WinterHill-Verbindung in OT schliessen und wieder die Quelle
Longmynd mit den bisherigen Ports 8080/8081 und 4003/4005 verwenden.

## Grenzen / Tests

- Pro Bridge ist eine gemeinsame OT-Instanz auf einem PC vorgesehen.
- Nur die UDP-PicoTuner-Schnittstelle wird nachgebildet, keine automatische
  Hardware-Erkennung, keine WinterHill-WebSockets und keine vier Empfaenger.
- Keine LNB-Spannungssteuerung: entsprechende OT-Kommandos werden ignoriert,
  die bereits funktionierende externe LNB-Versorgung bleibt bestehen.
- Die Link-Margin wird ab beta.4 wie im nativen OT-Longmynd-Modus aus MER
  minus ModCod-Referenzschwelle berechnet. Sie ist eine geschaetzte Reserve;
  es wird keine echte Empfangsgrenze gemessen.
- Der aktuelle Adapter liest die konfigurierten Multicast-Ziele beim Start.
  Nach Aenderungen der Core-Konfiguration den Zusatzdienst neu starten.
- Lokal getestet: getrennte Abstimmung und Statuskonvertierung, unveraenderte
  UDP-Nutzdaten pro Kanal und Lock-verloren-Status bei Backend-Ausfall.
- Der erste Test mit der echten Windows-OT-Oberflaeche und dem Pi steht noch aus.

## Protokollreferenz / Lizenz

GPL-3.0-or-later; vollstaendiger Quellcode, Tests und Lizenz liegen im Paket.
Protokoll anhand des OpenTuner-Quellcodes geprueft:
https://github.com/DH1RK/open_tuner/tree/1b3cd075e74de616c5caae79a477688f2a1e8822/MediaSources/Winterhill
insbesondere `WinterhillUDP.cs`, `WinterhillSource.cs`, `WinterhillProperties.cs`.
Die Erweiterung ist ein Community-Projekt und kein offizielles OpenTuner-,
PicoTuner- oder WinterHill-Release.

Tests: `python3 test_bridge.py -v` mit Python >=3.11 und websockets 15.0.1.

## Aenderung in beta.2

Der UDP-Status meldet die von OT angeforderte nominelle Symbolrate pro Kanal
(z.B. 333 oder 1000 kS/s). Die gemessene Demodulatorrate bleibt unveraendert
in Longmynd und dessen Diagnose verfuegbar. Hintergrund: OT D00.12 uebernimmt
die Statusrate in seinen Spektrumcache und vergleicht beim naechsten
Spektrumupdate auf exakte Gleichheit. Gemessene 332 statt 333 oder 999 statt
1000 fuehrten dadurch zum Verwerfen der beschrifteten Signale.

Update: neue .run-Datei wie bei der Erstinstallation starten. Danach OT neu
verbinden und beide Kanaele einmal auf die gewuenschten Stationen schalten,
damit die Bruecke die jeweiligen Sollraten aus den Steuerbefehlen uebernimmt.
10 Tests bestanden, darunter zwei unabhaengige Sollraten trotz abweichender
Messwerte sowie unveraenderte TS-Weiterleitung bei paralleler Baken-Datenrate.
Der Test mit der echten OT-Spektrumsanzeige steht noch aus.

## Aenderung in beta.3 (Paketbildung, Praxistest ausstehend)

Die Bruecke setzt aus den 510-Byte-Eingangsdatagrammen vollstaendige
188-Byte-TS-Pakete zusammen und sendet Gruppen von maximal sieben Paketen
(1316 Bytes). Unvollstaendige Gruppen werden alle 20 ms abgegeben, damit auch
schmale Signale ohne langes Ansammeln ausgegeben werden. Kanalpuffer werden
beim Umschalten verworfen. Unsynchronisierte Anfangsbytes werden bis zur
TS-Synchronisation uebersprungen. Video- und Audioinhalte werden nicht decodiert
oder veraendert. Der UDP-Sendepuffer wird auf 1 MiB angefordert.

Dies ist ein gezielter Kompatibilitaetstest, kein bereits bestaetigter Fix fuer
das zeitweise Stocken in OT. Der direkte Multicast-Empfang blieb sauber; VLC
auf dem Brueckenport lief zuletzt 3,5 Minuten sauber, OT stockte kurz danach.
13 automatisierte Tests bestehen: beliebige Datagrammgrenzen, bytegleiche
TS-Reihenfolge, Resynchronisation, kurze Streams, Umschalten, beide Kanaele
bei Baken-Datenrate sowie Steuerung und Installation. Zusaetzlich wurden
beide Kanaele aus dem vorhandenen Netzwerk-Mitschnitt verlustfrei in
vollstaendige TS-Datagramme umgepackt (ca. 61 Prozent weniger Datagramme bei
vollen Gruppen). Der installierte Longmynd-Empfaenger bleibt unveraendert.

Zum Zurueckgehen die vorherige beta.2-.run-Datei erneut installieren.

## Aenderung in beta.4 (Empfangsreserve / D-Anzeige)

Statusfeld $30 enthaelt jetzt die Empfangsreserve in dB:

    D = MER in dB - Referenzschwelle fuer Modulation und FEC

Die Schwellen entsprechen den beiden Tabellen im nativen Longmynd-Modus
von OpenTuner D00.12, Commit 2cc4a5430ac13b0dd5f690f92abe4c1ebd42dd60,
MediaSources/Longmynd/LongmyndSource.cs. Fuer DVB-S und DVB-S2
werden getrennte Tabellen verwendet. Beispiel: DVB-S2 ModCod 8, MER 9,8 dB,
Schwelle 4,7 dB -> Reserve 5,1 dB. Die Anzeige ist eine Referenzschaetzung,
keine zusaetzliche Hardwaremessung. Wie bei OT wird nicht gesondert nach
kurzen Frames/Piloten korrigiert. Negative Reserven bleiben erhalten.
Bei fehlendem Lock, veraltetem Status oder unbekanntem ModCod bleibt das
Feld leer (OT stellt fehlende Werte weiterhin als D0 dar).

Die 35 verwendeten Schwellen wurden gegen den OT-Quellcode verglichen.
15 Tests bestehen, darunter beide Kanaele mit verschiedenen Reserven,
negative Werte sowie fehlender/veralteter Status und die bisherigen
Datenstrom- und Installationstests.

Praxisergebnis zum vorigen Stocken: Die Paketbildung in beta.3 allein behob
es nicht. In der tatsaechlichen OT-DLL wurden zwei 4000-Byte-Limits im
WinterHillSource.ReadTSGeneric gefunden, obwohl der TS-Worker erst ab 4096
Bytes sofort weiterliest. Nach gezielter Korrektur auf 4096 bestaetigte der
Anwender beide Kanaele ohne Kloetzchen. Diese DLL-Korrektur ist ein separates
OT-Update und wird durch dieses Brueckenpaket nicht installiert oder ersetzt.

Installation wie oben, danach OT neu verbinden und beide Kanaele abstimmen.
