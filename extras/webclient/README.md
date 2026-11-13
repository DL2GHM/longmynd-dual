# Longmynd-Webclient V53

Der Webclient zeigt beide DATV-Empfänger im Browser. Er verwendet das
Mobil-Relay auf Port 8090 und kann das RTL-Lokalspektrum auf Port 8096
anzeigen. Für die Browserstreams benötigt er FFmpeg.

Auf dem Pi zuerst [Longmynd Dual und das Relay](../../docs/INSTALLATION-DE.md)
einrichten. Danach als normaler Benutzer in diesem Ordner ausführen.

```sh
sudo apt install ffmpeg
python3 longmynd-browser-dual-test.py --help
python3 longmynd-browser-dual-test.py --init-admin dl2ghm
python3 longmynd-browser-dual-test.py --bind 127.0.0.1
```

Der Webclient ist dann auf dem Pi unter `http://127.0.0.1:8092/` erreichbar.
Für den Betrieb im LAN die gewünschte Bind-Adresse gezielt konfigurieren.
Das Beispiel aktiviert die normale Anmeldung. Die Datenbank
`longmynd-users.sqlite3` entsteht beim Einrichten des ersten Kontos neben
dem Skript.
