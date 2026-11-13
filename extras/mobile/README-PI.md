# Longmynd Mobil-Relay auf dem Pi

Das Relay bedient Longmynd Mobil und den Webclient. Die Version im Dienst meldet Beta 2. Dieses Paket enthält bereits die später ergänzte Android-Anwesenheitserkennung, die der Web-Automatik beim Abstimmen Vorrangregeln ermöglicht. Der Relay-Code wurde mit dem archivierten Beta-2-Installer plus Automatik-Patch abgeglichen.

Longmynd Dual muss eingerichtet sein. Das Installationsprogramm übernimmt den Benutzer und die beiden Empfangskanäle aus `/etc/longmynd-dual.json`.

```sh
python3 install_mobile.py --dry-run
python3 install_mobile.py
systemctl is-active longmynd-mobile
```

Der Installer installiert die Python-Abhängigkeiten bei Bedarf über APT, erstellt ein neues Release-Verzeichnis und richtet `longmynd-mobile.service` ein. Auf dem ursprünglichen Aufbau läuft der Dienst auf Port 8090. Bei einem Update wird die vorherige Dienstdatei gesichert. Wenn bereits eine individuell angepasste Unit installiert ist, vorher den Iststand exportieren und die Dienstoptionen vergleichen.

Prüfung am Pi

```sh
curl -fsS http://127.0.0.1:8090/api/status
```

Die Antwort enthält zwei Kanäle und das Feld `native_active`. Die Rohstreams liegen unter `/live/0.ts` und `/live/1.ts`. Der Relay ist für das Heimnetz oder eigenes VPN vorgesehen.
