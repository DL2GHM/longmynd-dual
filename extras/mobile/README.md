# Longmynd Mobil – erste lokale Android-Beta

Zwei Longmynd-Kanaele auf Android-Handys und Tablets: Video, ausgewaehlter
Audiokanal, Frequenz/Symbolrate, Bake-Taste, MER und geschaetzte Empfangsreserve.
Im Querformat nebeneinander, im Hochformat untereinander. Im Auswahlmenue
kann ein einzelner Kanal den Videobereich fuellen. Der andere Decoder wird
dann freigegeben, was insbesondere aelteren Geraeten hilft.

## Installation

1. Den alten Android-Longmynd-Client schliessen, damit er keine Stream-Ziele
   mehr auf das Tablet umstellt.
2. Die `.run`-Datei auf den Pi kopieren und als `pi` ausfuehren:

       bash longmynd-mobile-0.1.0-beta.2.run

3. Falls der alte Client die Ziele schon verstellt hat, einmal den Empfaenger
   neu starten, um die konfigurierten Multicast-Ziele wiederherzustellen:

       sudo systemctl restart longmynd-dual

4. Die APK auf dem Android-Geraet installieren. Mindestversion Android 8.0
   (API 26). Fuer die Installation aus dem Downloadordner muss die jeweilige
   Dateiverwaltung/der Browser APK-Installationen erlauben.
5. Mit dem Heim-WLAN verbinden, App oeffnen, Adresse
   `http://raspberrypi.local:8090` eintragen und **Verbinden** waehlen.

Der Dienst `longmynd-mobile.service` wird zusaetzlich installiert. Vorhandene
OT-/Longmynd-/APRS-Dienste und ihre Konfiguration werden nicht ersetzt.
Pfade: `/opt/longmynd-dual/mobile/current`, `/etc/systemd/system/longmynd-mobile.service`.
Die bestehende `/etc/longmynd-dual.json` wird nur gelesen.

Status: `systemctl status longmynd-mobile --no-pager -l`
Log: `journalctl -u longmynd-mobile -n 30 --no-pager`
Abschalten: `sudo systemctl disable --now longmynd-mobile`

## Verhalten

Es sind dieselben beiden Hardware-Empfaenger wie am PC. Eine Frequenzaenderung
wirkt daher auch auf die entsprechende PC-Anzeige. Mehrere Zuschauer koennen
gleichzeitig denselben Kanal sehen. Die Android-App aendert keine UDP-Ziele.
Je Kanal hoechstens acht HTTP-Zuschauer. Langsame Verbindungen werden getrennt,
statt Daten unbemerkt im TS zu ueberspringen; der Player versucht erneut zu laden.
Reserve: dieselben Referenzschwellen wie OT D00.12, keine Messung der echten
Decodiergrenze. Ton kommt vom per Taste ausgewaehlten Kanal.

Die Frequenzeingabe ist die QO-100-Sendefrequenz in MHz, z.B. 10491.538.
Der LNB-Offset betraegt in dieser ersten Version fest 9750000 kHz.

## Lokal und spaeter VPN

Der Dienst stellt HTTP/TCP statt Multicast am Mobilgeraet bereit. Damit ist
die Verbindung fuer einen spaeteren gerouteten VPN-Zugang vorbereitet. Er
hat in dieser Version keine eigene Anmeldung und ist fuer das private Heimnetz
oder das eigene VPN gedacht. Es werden keine Routerfreigaben eingerichtet.
VPN und mobiler Zugriff sind in dieser Beta noch nicht praktisch getestet.

## Grenzen und Pruefung

Die APK benutzt libVLC 3.6.5 mit bevorzugter Hardware-Decodierung. Welche H.264-/H.265-
Aufloesungen und wie viele gleichzeitige Decoder funktionieren, haengt vom
Geraet ab. Bei Problemen mit zwei Bildern einen einzelnen Kanal waehlen.
Stream-Wiederanlauf, Kanaltrennung, Fan-out, Datenidentitaet und API-Validierung
werden lokal automatisiert getestet. Ein Test auf dem echten Tablet/Handy
steht bei Auslieferung noch aus. Kein Spektrum/Chat und kein Hintergrundempfang
in dieser ersten Version. Beim Verlassen der App werden die Decoder gestoppt.

## Quellen und Build

App: Java 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Android SDK 35,
libVLC 3.6.5. Im `app`-Verzeichnis `gradle :mobile:assembleRelease`.
Die erste APK ist mit einem Entwicklungszertifikat signiert.
Der Pi-Installer enthaelt die App-Quellen, Pi-Quellen und Tests.

Eigene Quellen GPL-3.0-or-later, siehe LICENSE. `bridge.py` uebernimmt die
getestete Paketbildung und OT-kompatiblen Schwellwerte der vorhandenen Bruecke.
libVLC: VideoLAN, LGPL-2.1-or-later; Quellen und Lizenz:
https://code.videolan.org/videolan/vlc-android
https://code.videolan.org/videolan/vlc


APK-Build dieser Auslieferung: direkt mit Android SDK Build Tools 35.0.0
(aapt2, D8, zipalign, apksigner) und Java 17; der Java-Teil wurde kompiliert
und die APK-Signaturen v2/v3 sowie Manifest/MinSdk/ABIs wurden geprueft.
Der alternative Gradle-Build ist als Projekt enthalten, wurde hier aber
nicht bis zum Ende als Freigabepruefung verwendet.

Reproduzierbarer SDK-Build (eigener Entwicklungsschluessel wird bei Bedarf erzeugt):

    python3 build_apk.py --sdk SDK_ORDNER --jdk JDK_ORDNER \
      --vlc-aar libvlc-all-3.6.5.aar --keystore dev.keystore --output Longmynd-Mobil.apk

Die benoetigte AAR ist auf Maven Central verfuegbar:
https://repo.maven.apache.org/maven2/org/videolan/android/libvlc-all/3.6.5/libvlc-all-3.6.5.aar
Das Skript prueft ihren SHA256-Wert vor dem Einbinden.

Pruefstand: sechs Pi/API-Tests bestanden, einschliesslich zweier paralleler
Streams bei Baken-Datenrate ueber UDP-Eingang und HTTP-Ausgang.
Die APK ist kompiliert und signaturgeprueft; Wiedergabe/Bedienung auf echter
Android-Hardware und VPN-Zugriff sind noch nicht praktisch getestet.


Android-App beta.2:
- Jede VLCVideoLayout liegt in einem eigenen FrameLayout. VLC ueberschreibt
  bei onAttachedToWindow die eigenen LayoutParams mit MATCH_PARENT; dadurch
  darf das VLC-Layout nicht selbst die LinearLayout-Gewichtung tragen.
- Feste Panel-Hierarchie, Sichtbarkeit und Groesse werden angepasst, statt
  Videoflaechen bei Statuswechseln zu entfernen und erneut anzuhaengen.
- TextureView je Kanal statt separater SurfaceView-Overlays.
- Nur sichtbare Kanaele halten Player und Decoder; beim Verbergen/Verlassen
  werden sie vollstaendig freigegeben. Statusabfragen starten nicht staendig
  neue Player. Fehler-/Pufferanzeige getrennt vom Empfaengerstatus.
- Umschaltbarer Hardware-/Softwaredecoder fuer weitere Geraetetests.

Der Pi-Dienst beta.1 bleibt fuer diese App-Korrektur kompatibel. Die neue APK
verwendet denselben Paketenamen und dieselbe Signatur, mit versionCode 2.
Kompilation/Signatur sind geprueft; die Wiedergabe muss erneut auf dem echten
Tablet getestet werden. Erster Befund: beide direkten Longmynd-Stroeme sauber,
HTTP Kanal 1 in PC-VLC sauber; Android beta.1 zeigte nur Bake auf Kanal 2.

- Automatisches Wiederverbinden beim erneuten Oeffnen nach gespeicherter
  Serveradresse. Nach App-Pause werden die sichtbaren Player neu aufgebaut.
- Bei erneuter Demodulator-Verriegelung, Dienstwechsel oder groesserer
  Frequenz-/Symbolratenaenderung wird der betroffene Player neu aufgebaut.
  Dadurch erfordert Rueckschalten auf die Bake keinen manuellen Connect.
- Die urspruengliche Ursache der fehlenden Bilder ist noch nicht praktisch
  bestaetigt. Beta.2 behebt einen nachgewiesenen Layoutfehler und macht den
  Playerzustand sichtbar; Softwaredecoder ist als gezielter Vergleich waehlbar.


## Android beta.3 / Pi-Dienst beta.2: antippbares QO-100-Spektrum

Quelle ist das oeffentliche BATC-/AMSAT-UK-Breitbandspektrum, nicht eine
FFT-Messung des eigenen MiniTiouners. Der Pi braucht dafuer Internetzugang.
Er haelt nur eine gemeinsame WebSocket-Verbindung zur festen BATC-Quelle,
wenn die App die Spektrumsanzeige abfragt. Bei Inaktivitaet wird sie geschlossen.
Protokoll: wss://eshail.batc.org.uk/wb/fft, fft_m0dtslivetune, 922 uint16-LE
Bins im geprueften Rahmen; die Bandabbildung ist 10490,5 bis 10499,5 MHz.
Referenz: M0DTS QO-100-WB-Live-Tune und OpenTuner D00.12 socket.cs/signal.cs.
https://github.com/m0dts/QO-100-WB-Live-Tune
https://github.com/DH1RK/open_tuner/tree/2cc4a5430ac13b0dd5f690f92abe4c1ebd42dd60/ExtraFeatures/BATCSpectrum

Bedienung:
- Spektrum per Kontrollkaestchen anzeigen oder ausblenden.
- Unter "Tippen schaltet" den Zielkanal waehlen.
- Erkanntes Signal antippen: Zentrum und geschaetzte Symbolrate uebernehmen.
- Lange tippen (oder auf einen nicht erkannten Bereich tippen): Symbolrate
  selbst waehlen. Kleine oder schwache Signale werden eventuell nicht erkannt.
- Mit zwei Fingern zoomen, im gezoomten Bereich seitlich schieben.
- Doppeltipp zeigt wieder das ganze Band.
- Frequenzmarker/Rufzeichen stammen aus den lokalen Longmynd-Statusdaten.
- Die bisherige direkte Frequenzeingabe befindet sich unter "Manuell" am Kanal.

Die Schaetzung benutzt dieselben breiten Symbolraten-Grenzen wie die
Referenzprogramme; sehr schmale Signale und atypischer Roll-off koennen
abweichen. Fuer den ersten echten BATC-Rahmen erkennt sie die Bake mit
1500 kS/s bei 10491,525 MHz. Der Rahmen ist als Testfixture enthalten.
Ein End-to-End-Umschalttest mit der echten Empfangshardware steht noch aus.

Die native App bleibt mit dem vorigen Pi-Dienst fuer Video/Status kompatibel,
aber fuer das neue Spektrum ist das Update des Pi-Dienstes erforderlich.
Der Dienstwechsel unterbricht die Android-HTTP-Streams kurz. Er startet weder
Longmynd Dual noch APRS oder die PC-OT-Bruecke neu.
