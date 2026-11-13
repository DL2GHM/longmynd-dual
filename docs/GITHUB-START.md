# Installation aus dem GitHub-Quellcode

Auf Raspberry Pi OS oder Debian mit installiertem Git das Repository samt
fest referenziertem libwebsockets-Quellcode klonen.

```sh
git clone --recurse-submodules https://github.com/DL2GHM/longmynd-dual.git
cd longmynd-dual
python3 packaging/build-release.py --output ../longmynd-installers
bash ../longmynd-installers/longmynd-dual-0.1.0-beta.1.run --list-usb
```

Den erzeugten Dual-Installer mit den beiden USB-Seriennummern nach
[Installationsanleitung](INSTALLATION-DE.md) ausführen. Danach kann die
OpenTuner-Bridge direkt aus ihren Quellen installiert werden.

```sh
python3 extras/ot-dual/install_bridge.py
python3 extras/mobile/install_mobile.py
```

Die Bridge wird nur für OpenTuner benötigt. Das Mobil-Relay auf Port 8090
versorgt Longmynd Mobil und den Webclient. Das
[RTL-Lokalspektrum](../extras/lokalspektrum/README.md) ist ein eigener Dienst.
