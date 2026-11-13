# QO-100 Lokalspektrum für DL2GHM

Der Raspberry-Pi-Dienst misst das DATV-Band von 10.490,5 bis 10.499,5 MHz mit einem RTL-SDR-Stick. Er zeigt die Live-Kurve im Browser und stellt sie im Heimnetz für Longmynd Mobil und kompatible WebSocket-Clients bereit.

## Installation oder Update

Das ZIP auf dem Pi in einen Ordner entpacken. Einen laufenden RTL-Live-Test vorher mit **Strg+C** beenden, damit der Dienst den USB-Stick öffnen kann. Im entpackten Ordner ausführen.

```bash
sudo apt install rtl-sdr python3-aiohttp python3-numpy
sudo sh install-service.sh
```

Der Installer aktualisiert und startet `qo100-lokalspektrum.service`. Der Dienst startet automatisch mit dem Pi. Ein bereits konfigurierter Port wird übernommen. Standardmäßig läuft die Browseransicht unter `http://PI-IP:8096/`. Nach dem Update die Seite neu laden.

Longmynd Mobil verwendet weiterhin dieselbe lokale Adresse. Bei der erstmaligen Auswahl das Spektrum zwei Sekunden gedrückt halten, **Lokaler QO-100-Spektrumserver** wählen und `http://PI-IP:8096` eintragen. Der Dienst liefert die aktuelle Kurve und geschätzte Symbolraten. Die App verwendet ihre eigene Darstellung und Glättung.

## Einstellungen und Diagnose

In `/etc/default/qo100-lokalspektrum` lassen sich Port, Geräteindex und gewünschter Tuner-Gain einstellen. Standardwerte sind `QO100_PORT=8096`, `QO100_RTL_DEVICE=0` und `QO100_RTL_GAIN=20`. Der Stick nutzt die nächstliegende feste Verstärkungsstufe. Änderungen werden nach einem Dienstneustart wirksam.

```bash
sudo systemctl restart qo100-lokalspektrum
systemctl status qo100-lokalspektrum --no-pager
journalctl -u qo100-lokalspektrum -n 30 --no-pager
```

Die Darstellung in Browser und App vergrößert die Auslenkung um bis zu 40 Prozent. Sehr starke Ausschläge werden im oberen Teil der Skala sanft zusammengedrückt, damit der sichtbare Bereich stabil bleibt. Die dB-Markierungen im linearen Bereich der Browserkurve und die Symbolratenschätzung beziehen sich auf die gemessenen Pegel.

Der Browser meldet die tatsächliche Zeit eines Vollscans. Unter `/api/status` stehen außerdem Quelle, Gain und Verbindungsstatus. Wenn der Dienst den Stick nicht öffnen kann, prüfen, ob ein anderer SDR-Prozess ihn noch verwendet und ob der Stick am Pi erkannt wird.

## Messung und Schnittstellen

Der Stick bleibt geöffnet und misst 16 überlappende Teilbänder. Der Dienst kombiniert die jeweils jüngsten Messwerte, unterdrückt die Teilbandmitten und reduziert den Einfluss der Ränder. Die Kurve verwendet das beim Test ermittelte Pegelprofil, eine Interpolation an der bekannten Störstelle bei 10.498,802 MHz und eine leichte Glättung.

- `/api/spectrum` liefert 922 Pegelwerte und geschätzte Symbolraten für Longmynd Mobil.
- `ws://PI-IP:8096/wb/fft` liefert 922 Little-Endian-UInt16-Werte pro Frame. Es unterstützt unter anderem das Subprotokoll `fft_m0dtslivetune`. Für OpenTuner ist eine konfigurierbare Spektrum-URL in der jeweiligen Version erforderlich.
