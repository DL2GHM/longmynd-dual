# Longmynd Mobil 0.1.0-beta.39

Android-Client fuer das Longmynd-Mobil-Relay. Android 8 oder neuer, ARM 32/64 Bit.
Im Verbindungsfeld die Adresse des eigenen Raspberry Pi mit Port 8090 eintragen.
Der Hostname raspberrypi.local dient als Voreinstellung.
Build und Versionsverlauf stehen weiter unten.

## Android 0.1.0-beta.39 – K2-Ton beim Schreiben im Chat

Die Chatansicht zeigt weiterhin K1. Wenn K2 vor dem Schreiben aktiv war, läuft seine Tonwiedergabe mit der eingestellten Lautstärke weiter. Die App blendet beim Tippen die K2-Videofläche aus und verbindet sie nach dem Schreiben wieder mit dem laufenden Player.

## Android 0.1.0-beta.38 – Chatansicht beim Antippen der Eingabezeile

Beim Antippen des Chatfelds wechselt die App direkt zur Chatansicht mit K1 ohne Spektrum. Das Schließen der Tastatur oder der Wechsel des Fokus stellt die vorherige Ansicht wieder her.

## Android 0.1.0-beta.37 – K1 beim Schreiben im Chat

Bei geöffneter Chat-Tastatur zeigt die App K1 und blendet das Spektrum aus. Der Chat nutzt die übrige Höhe. Nach dem Schließen der Tastatur gilt wieder die zuvor gewählte Ansicht.

## Android 0.1.0-beta.36 – Mehr Chatplatz beim Schreiben

Bei geöffneter Chat-Tastatur bleibt der ausgewählte Empfangskanal sichtbar. Seine Videofläche folgt dem Bildformat. Der Chatverlauf erhält die übrige Höhe über der Eingabezeile.

## Android 0.1.0-beta.35 – RTL-Kurve vom Pi

Longmynd Mobil übernimmt die bereits korrigierten Pegel vom lokalen RTL-SDR-Dienst. Die App zeichnet den Rauschboden und die Signale mit derselben relativen dB-Skala wie der Browser. Frequenzzoom, Antippen, Symbolraten und Tuner-Markierungen bleiben verfügbar. Der bestehende RTL-SDR-Pi-Dienst auf Port 8096 liefert die benötigten Daten.

## Android 0.1.0-beta.34 – Wiederherstellung von Beta 31

Diese APK enthält den Programmstand von Beta 31. Der neue Versionscode ermöglicht die Installation als Update über Beta 33 mit derselben Signatur.

## Android 0.1.0-beta.31 – Ruhigere lokale Spektrumkurve

Die Android-App mittelt die lokale FFT-Kurve mit einer Zeitkonstante von 3,5 Sekunden. Die Pegelachse hat einen festen unteren Bezug, und die obere Grenze folgt anhaltenden Pegeländerungen weich. Die Mittelung erfolgt über die Zeit für jeden FFT-Bin einzeln. Erkennung und Abstimmung verwenden weiterhin die empfangenen Werte. Der lokale Spektrumserver aus Beta 29 kann weiterverwendet werden.

## Android 0.1.0-beta.30 – Ruhigere lokale Spektrumkurve

Die lokale Spektrumquelle nutzt dieselbe Zeitkonstante von zwei Sekunden und denselben frequenzabhängigen Rauschbodenausgleich wie die Browseransicht. Signal- und Rufzeichenpositionen folgen der gezeichneten Kurve. Die Erkennung, Abstimmung, BATC-Ansicht und die Quellenwahl per Zwei-Sekunden-Druck verwenden weiterhin die bestehenden Abläufe. Der Spektrumserver aus Beta 29 kann weiterverwendet werden.

## Android 0.1.0-beta.29 – Spektrumquelle wählen

Das Spektrum zwei Sekunden gedrückt halten. Die Auswahl bietet BATC / AMSAT-UK und den lokalen QO‑100-Spektrumserver. Beim ersten Wechsel auf Lokal schlägt die App die IP der Longmynd-Verbindung mit Port 8096 vor. Die gewählte Quelle und die lokale Adresse werden gespeichert. Ein kürzeres Halten öffnet beim Loslassen die manuelle Symbolratenwahl. Ein kurzer Tipp, Doppeltipp, Dreifachtipp und Zwei-Finger-Zoom bleiben verfügbar.

Für Lokal den aktualisierten QO‑100-Spektrumserver mit `/api/spectrum` verwenden. Für BATC genügt die bestehende Longmynd-Installation. Der lokale Server liefert geschätzte Symbolraten. Die Medienstreams und Abstimmkommandos gehen weiterhin an den in der App eingestellten Longmynd-Pi.

## Android 0.1.0-beta.28 – Freien Platz auch im Querformat für den Chat nutzen

Die dynamische Höhenaufteilung gilt nun auch im Querformat. Bei zwei Videos nebeneinander richtet sich die Höhe des Videobereichs nach der breiteren verfügbaren Spaltenfläche und den tatsächlichen Bildformaten. Die bisher ungenutzte Höhe ober- und unterhalb der Videos geht an den Chatverlauf; dieser ist nicht mehr auf zwei Zeilen begrenzt. Bei wenig Platz bleiben mindestens die bisherigen zwei Chatzeilen verfügbar. Ausblenden von Komponenten und Wechsel auf einen Empfänger werden berücksichtigt. Hochformat, Vollbild und die kompakte Ansicht beim Schreiben behalten ihr Verhalten.

Die Schriftanpassung auf ganze Zeilen aus Beta 27 bleibt erhalten, ebenso Videorundungen, Farben, Rahmen und Tippgesten. Gleiche Signatur, kein Pi-Update.

Geprüft: Geometriemodell für Tablet-Querformat mit zwei Videos, gemischten Seitenverhältnissen, einer Videoansicht und knapper Höhe; APK-Build, Signatur und Alignment. Native Darstellung bitte auf dem Tablet prüfen.

## Android 0.1.0-beta.27 – Ganze Zeilen im Chatfenster

Der Chat passt die Schrift behutsam an seine verfügbare Höhe an (höchstens ±10 % gegenüber der bisherigen Größe). Zeilenabstand und unterer Innenrand bilden ein ganzzahliges Zeilenraster. Die zusätzliche Leerfläche am Ende der letzten Textzeile verhindert abgeschnittene erste Zeilen beim automatischen Folgen am unteren Ende. Nach manuellem Scrollen rastet der Verlauf nach Ende der Bewegung auf einer ganzen Zeile ein; Hochscrollen beendet weiterhin die Folgefunktion, Zurückscrollen ans Ende aktiviert sie wieder. Die Berechnung reagiert auf Drehen, Tastatur und Komponenten-Sichtbarkeit; die gewünschte Grundfläche wird unabhängig von der angepassten Schriftgröße berechnet.

Gleiche Signatur, kein Pi-Update. Geprüft: Zeilenrastermodell, APK-Build, Signatur und Alignment. Native Textdarstellung, Umbrüche und Scrollverhalten müssen auf dem Gerät geprüft werden.

## Android 0.1.0-beta.26 – Dunklere Trenner und kompaktes Spektrum

Die abgerundeten Trennlinien sind dunkler (#16221a). Das Spektrum nutzt oben und unten nur noch 6 dp Rand; die Frequenzzahlen unterhalb des Diagramms entfallen. Das feine Frequenzraster, die Rufzeichen und die Kanalmarkierungen bleiben erhalten. Spektrumhöhe im Hochformat 96 statt 130 dp, im Querformat 64 statt 85 dp. Die eigentliche Diagrammfläche bleibt dabei mindestens so hoch wie bisher. Im Hochformat erhält der Chat den frei gewordenen Platz.

Gleiche Signatur, kein Pi-Update. Geprüft: APK-Build, Signatur und Alignment. Darstellung bitte auf dem Gerät prüfen.

## Android 0.1.0-beta.25 – Videoecken mit deckender Eckmaske

Der Gerätescreenshot zeigte Videopixel außerhalb der gerundeten Kontur trotz aktivierter Outline-Begrenzung. Ein eigener VLCVideoLayout zeichnet jetzt nach der Videotextur vier deckende Eck-Aussparungen in der App-Hintergrundfarbe und darüber die exakt passende runde Kontur. Diese Darstellung ist nicht von der Outline-Begrenzung der Videotextur abhängig. Der Container bleibt auf die tatsächliche Bildfläche begrenzt; im Vollbild sind Maske und Kontur aus.

Gleiche Signatur, kein Pi-Update. Geprüft: APK-Build, Signatur, Alignment und bisherige Bildgeometrie. Die tatsächliche Video-Compositor-Darstellung muss erneut auf dem Android-Gerät geprüft werden.

## Android 0.1.0-beta.24 – Runde Videoecken direkt am Bild

Rundung und Kontur gehören jetzt zum VLC-Bildcontainer statt zur gesamten Kanalfläche. Der Container wird auf die tatsächliche, anhand Seitenverhältnis/SAR/Orientierung berechnete Bildfläche begrenzt. Damit schneiden die Rundungen das Video selbst ab und der Rahmen folgt dem sichtbaren Bild, auch bei freien Flächen links/rechts oder oben/unten. Außerhalb des Bildes liegt die App-Hintergrundfarbe. Die Videoeinblendungen bleiben an derselben berechneten Bildfläche ausgerichtet. Im Vollbild entfallen Rundung und Rahmen wie bisher.

Gleiche Signatur, kein Pi-Update. Geprüft: Geometriemodell einschließlich 4:3-Video in breitem Fenster, APK-Build, Signatur und Alignment. Clipping und Videodarstellung müssen auf dem Gerät geprüft werden.

## Android 0.1.0-beta.23 – Gedämpfte olivgrüne Rahmen

Der gemeinsame Außenrahmen um Chatverlauf und Schreibzeile entfällt wieder. Die beiden Bereiche behalten ihre separaten runden Rahmen. Alle Rahmen einschließlich Zahnrad und Menü verwenden jetzt ein dunkleres, gelblicheres Grün (#788b46). Trennlinien, Rundungen und Abstände bleiben erhalten. Gleiche Signatur wie Beta 22 und kein Pi-Update.

Geprüft: APK-Build, Signatur und Alignment. Farbwirkung bitte auf dem Gerät vergleichen.

## Android 0.1.0-beta.22 – Äußerer Chatrahmen und Randlinien

Der gesamte Chatbereich erhält zusätzlich einen äußeren hellgrünen Rahmen mit runden Ecken. Alle Bereichskonturen sind nun 1 dp fein, damit sie auch auf hochauflösenden Displays sichtbar bleiben. Ganz oben und unten stehen abgerundete Trennlinien in der bisherigen dunkelgrünen Farbe, mit halber Höhe der inneren 7-dp-Trenner. Im Vollbild verschwinden diese Linien. Die Hochformatberechnung berücksichtigt die neuen Abstände.

Fehlender Relay-Zugang wird im Video auch bei ausgeschalteter Player-Diagnose angezeigt; die Verbindungseinstellungen zeigen Adresse und konkrete Fehlermeldung. Kein bestätigter Netzwerkfehler im App-Code gefunden; Erreichbarkeit von Pi/Port 8090 muss separat geprüft werden. Serveradresse und Verbindungsprotokoll wurden nicht geändert.

Gleiche Signatur, kein Pi-Update. Geprüft: Build, Signatur, Alignment und bestehende Layouttests. Kein Gerätetest möglich.

## Android 0.1.0-beta.21 – Hellgrüne Rahmen zum Vergleich

Die Rahmen um Videos, Spektrum, Chatverlauf und Eingabe sowie um Zahnrad und Menü verwenden jetzt ein freundliches Hellgrün (#9de6b1). Rundungen, feine Strichstärken und dunkelgrüne Trennbereiche bleiben erhalten. Gleiche Signatur wie Beta 20, kein Pi-Update.

Geprüft: APK-Build, Signatur und Alignment. Farbwirkung bitte auf dem Gerät vergleichen.

## Android 0.1.0-beta.20 – Abgerundete grüne Trennlinien

Die Trennbereiche haben wieder das dunkle Grün aus Beta 17/18 (#223428). Ihre Ecken sind jetzt ebenfalls abgerundet (4 dp Radius bei 7 dp Breite, sowohl waagerecht als auch senkrecht). Der feste abgerundete Rahmen des Chatverlaufs aus Beta 19 bleibt erhalten. Gleiche Signatur und kein Pi-Update.

Geprüft: APK-Build, Signatur und Alignment. Optik bitte auf dem Gerät prüfen.

## Android 0.1.0-beta.19 – Hintergrundfarbene Abstände und fester Chatrahmen

Alle 7-dp-Trennbereiche haben jetzt exakt dieselbe Farbe wie der App-Hintergrund. Der Chatverlauf sitzt in einem eigenen, feststehenden Rahmen mit abgerundeten Ecken. Dieser Rahmen gehört zur äußeren Hülle statt zum scrollenden Inhalt und bleibt dadurch beim Lesen älterer Nachrichten sichtbar. Chattextfarben und automatisches Folgen am Ende bleiben erhalten. Gleiche Signatur, kein Pi-Update.

Geprüft: APK-Build, Signatur und Alignment. Die native Darstellung bitte auf dem Gerät prüfen.

## Android 0.1.0-beta.18 – Abgerundete Bereiche und feine Konturen

Videos, Spektrum, Chatverlauf und Eingabebereich haben 10 dp gerundete Ecken mit einer feinen dunkelgrünen Kontur (1 Pixel). Die Kontur wird als Vordergrund gezeichnet und bleibt damit auch über den Videos sichtbar. Inhalte werden an den Rundungen beschnitten; kleine Innen- und Außenabstände schaffen Platz für die Umrandung. Videogeometrie und Einblendungen berücksichtigen die verkleinerte Innenfläche. Im Vollbild entfallen Rahmen und Abstände. Hochformat-Chatfläche und Tastaturansicht berücksichtigen die zusätzlichen Abstände.

Gleiche Signatur wie Beta 17, kein Pi-Update. Geprüft: Layoutmodell mit Innenabständen, Build, Signatur und Alignment. Native Darstellung bitte auf dem Gerät prüfen.

## Android 0.1.0-beta.17 – Dezente Trennbereiche

7 dp breite, dunkle grüne Trennbereiche zwischen den Videos, unter dem Videobereich, zwischen Spektrum und Chat sowie zwischen Chatverlauf und Eingabe. Im Querformat trennt ein senkrechter Streifen die beiden Videos. Ausgeblendete Komponenten hinterlassen keine überflüssigen Trennbereiche; Vollbild zeigt keine Linien. Die Hochformatberechnung berücksichtigt alle Trennbereiche, damit die Videos ihr Seitenverhältnis behalten und die freie Fläche weiterhin dem Chat gehört. Gleiche Signatur wie Beta 16, kein Pi-Update.

Geprüft: Layoutberechnung mit/ohne Videotrennung, APK-Build, Signatur und Alignment. Darstellung bitte auf dem Gerät prüfen.

## Android 0.1.0-beta.16 – Tippgesten für Automatik und Stationswechsel

* Doppeltipp ins Spektrum schaltet die Automatik wieder ein und zeigt weiterhin das Gesamtband.
* Dreifachtipp schaltet die Automatik ein und stimmt bis zu zwei andere verfügbare Stationen ab. Die beiden aktuell eingestellten Signale und die Bake sind keine Wechselkandidaten. Ist nur eine Alternative verfügbar, wechselt nur ein Empfänger (bevorzugt der mit der Bake); ohne Alternative bleibt die Auswahl bestehen.
* Einzel- und Doppeltipp warten auf das Ende des Mehrfachtipp-Zeitfensters. Ein Dreifachtipp löst weder einen zusätzlichen Einzel- noch Doppeltipp aus. Langes Drücken, Ziehen und Zweifinger-Zoom lösen keinen Stationswechsel aus.
* Moduswechsel behalten frische Spektrum-/Empfangsbeobachtungen und Codec-Sperren bei. Beim Umschalten überholte Aufträge werden verworfen.
* Gleiche Signatur wie Beta 15, kein Pi-Update.

Geprüft: neue Tests für Tippfolgen, Abstand/Bewegung/Abbruch/langes Drücken sowie alternative Senderpaare, eine/keine Alternative und veraltete Statusdaten; bestehende Automatikregeln; APK-Build, Signatur und Alignment. Kein echter Gerätetest der Touchgesten möglich.

## Android 0.1.0-beta.15 – Modusanzeige vor dem Kanal

A/M steht vor K1 bzw. K2, mit derselben Schriftgröße und Schriftstärke wie die übrigen Empfangsdaten. Die Anordnung und Skalierung folgen weiterhin dem Videobild. Übriges Verhalten wie Beta 14; gleiche Signatur, kein Pi-Update.

## Android 0.1.0-beta.14 – Mehr Chatfläche im Hochformat

Im Hochformat erhalten die sichtbaren Videos die Höhe, die ihr tatsächliches Seitenverhältnis benötigt. Die übrige Fläche unter dem Spektrum gehört vollständig dem Chatverlauf; die Eingabe bleibt ganz unten. Bei zwei verschiedenen Bildformaten werden die Videohöhen entsprechend verteilt. Auf kleinen Displays bleiben mindestens die bisherigen Chatzeilen erhalten, die Videos passen dann verkleinert ins Fenster. Ausblenden von Spektrum oder eines Empfängers vergrößert den Chat weiter. Querformat, Vollbild und der kompakte Chat mit einem sichtbaren Video bei geöffneter Tastatur behalten ihr bisheriges Verhalten.

Gleiche Signatur, kein Pi-Update erforderlich. Geprüft: APK-Build/Signatur, Layoutberechnung einschließlich Zweikanal-/Einkanalansicht, knapper Höhe und gemischter Seitenverhältnisse. Darstellung bitte auf Handy/Tablet prüfen; kein echter Gerätetest verfügbar.

## Android 0.1.0-beta.13 – Automatik und Zahnradmenü

* Die App startet im Automatikmodus. Das Zahnrad schaltet auf manuelle Steuerung um; eine manuelle Signalauswahl beendet die Automatik ebenfalls. Zum Fortsetzen „Automatik“ wieder einschalten.
* Automatik läuft nur bei geöffneter App im Vordergrund über die bestehende Pi-Relay-API. Keine neue Scanwache, kein zusätzlicher Dienst, kein Pi-Update nötig. Der bestehende Webscanner pausiert weiterhin wegen der nativen Android-Verbindung.
* Zwei verschiedene Stationen haben Vorrang vor der Bake. Eine Station plus Bake bei nur einem Sender; Bake auf beiden Empfängern ohne andere Station. Laufende Stationen werden gehalten, es gibt keinen zeitgesteuerten Wechsel. Bei Verlust zehn Sekunden warten, bei ausbleibendem Lock andere Kandidaten probieren und nach 30 Sekunden erneut versuchen. Empfangs- und Spektrumstatus müssen frisch sein.
* Erkennt libVLC in den Spurinformationen H.266/VVC, nimmt die Automatik diese Frequenz vorübergehend aus der Auswahl. HEVC bleibt zugelassen. Keine Rufzeichen-Ausnahmeliste.
* Ein einziges durchscheinendes Zahnrad oben rechts im sichtbaren Video öffnet das kompakte Menü. Mit Maus auch per Hover, auf dem Touchscreen per Tipp. Die Zahnradposition ist im Menü integriert; außerhalb tippen oder das Zahnrad erneut antippen schließt es. Verbindung/Chat bleiben auf einer separaten Konfigurationsseite erreichbar.
* Empfangsdaten oben links mit größerem **A** / **M** am Ende. Position und Skalierung aller Video-Einblendungen einschließlich Menü und dezenter Lautstärkeanzeige folgen dem tatsächlichen Videobild (inklusive Letterboxing und Seitenverhältniswechsel). Kopfzeile und dauerhafte Lautstärkebadges entfallen.
* Spektrummarker, Striche und Rufzeichen liegen in der Mitte zwischen den ermittelten Signalfüßen. Die Abstimmfrequenz wird dadurch nicht geändert.
* Kein automatischer Fokus auf die Chatzeile beim Start. Beim Schreiben bleibt der zuletzt berührte bzw. ausgewählte Empfänger sichtbar, mit einer Chat-Verlaufszeile über der Eingabe. Nach Schließen der Tastatur wird die vorherige Ansicht wiederhergestellt. Android 11+ berücksichtigt die IME-Inset-Höhe ausdrücklich; ältere Geräte verwenden adjustResize.
* Im Hochformat weiterhin beide Videos untereinander, im Querformat nebeneinander. Vollbild bleibt ohne Zusatzkomponenten; Videos passen vollständig ins Fenster.
* Gleiche Entwicklersignatur wie Beta 12; als Update installierbar, Einstellungen bleiben erhalten.

Prüfung: erfolgreicher APK-Build, Signaturvergleich mit Beta 12, APK-Signatur und Alignment; Java-Tests für Automatikregeln, Signalmitten, Bildgeometrie, Ansichtsprioritäten und bestehendes Chat-Protokoll. Kein Android-Gerät/Emulator verfügbar: Touchdarstellung, Tastatur und Live-Empfang müssen auf Handy/Tablet geprüft werden.

## Android 0.1.0-beta.12 – Chatdarstellung und Platzanpassung

* Am unteren Ende bleibt der Chat bei neuen Nachrichten unten. Die Folgefunktion wird erst durch Hochscrollen verlassen und durch Zurückscrollen an das Ende wieder aktiviert. Text- und Größenänderungen werden dabei nicht als Benutzerscrollen gewertet.
* Im Querformat zwei sichtbare Textzeilen, im Hochformat drei, berechnet aus der tatsächlichen Schriftzeilenhöhe.
* Neuer „Chat“-Button in der Kopfzeile blendet Verlauf und Eingabe aus bzw. ein. Den freigegebenen Platz erhält der Videobereich. Die Auswahl wird gespeichert; die Chat-Verbindung bleibt unabhängig davon bestehen.
* Im Video-Vollbild ist der Chat immer ausgeblendet. Beim Zurückkehren gilt die vorherige Chat-Auswahl.
* Rufzeichen/Nicknames gelb, Nachrichtentext grün, Uhrzeiten dezent grau. Text bleibt einfacher Text; keine HTML-Ausführung.
* Gleiche Entwicklersignatur wie Beta 11. Kein Pi-Update erforderlich.

Prüfung: Android-Build, APK-Signatur und Alignment sowie vorhandene Chat-/Spektrummodelltests. Die Scroll- und Layoutdarstellung muss zusätzlich auf einem echten Android-Gerät geprüft werden.

## Android 0.1.0-beta.11 – Durchscheinende Empfangsdaten und QO-100-Chat

* Empfangsdaten liegen mit sehr dezentem, durchscheinendem Hintergrund direkt über dem zugehörigen Video. Der Daten-Button schaltet sie weiterhin ein und aus.
* Unter dem Spektrum stehen ungefähr drei Chatzeilen ohne zusätzliche Überschrift. Der Verlauf lässt sich scrollen; beim Lesen älterer Nachrichten wird nicht ungefragt nach unten gesprungen.
* Ganz unten: Eingabezeile und Senden-Button. Texte werden als Text dargestellt, nicht als HTML ausgeführt. Eingaben bleiben bei einem Sendefehler erhalten und werden nicht automatisch erneut gesendet.
* Einstellungen: Rufzeichen (2–20 Zeichen), „Chat beim App-Start automatisch anmelden“, „Jetzt anmelden“ bzw. „Abmelden“ und Verbindungsstatus. Rufzeichen und Startoption bleiben gespeichert. Die automatische Anmeldung ist anfangs aus.
* Ohne Anmeldung ist Mitlesen möglich. Die Anmeldung ist die vom BATC-Chat unterstützte Rufzeichen-/Nickname-Registrierung, keine Passwortanmeldung. Der Server-Nickname-Verlauf bestätigt die Registrierung vor Freigabe des Sendens.
* Der Chat verwendet direkt HTTPS zum BATC-Dienst und benötigt Internet auf dem Android-Gerät. Er ist unabhängig von der Pi-Verbindung. Kein Pi-Update nötig.
* Beim Verlassen der App wird die Chat-Verbindung beendet; beim Zurückkehren wird sie wieder aufgebaut. Eine ausdrücklich gewählte Anmeldung bleibt während derselben App-Sitzung erhalten.
* Doppeltipp-Vollbild blendet weiterhin Menü, Empfangsdaten, Spektrum und Chat aus; Rückkehr stellt die Ansicht wieder her. Das Spektrum ist kompakter, um Platz für Video und Chat zu schaffen.
* Beim Schreiben mit Bildschirmtastatur werden Video und Spektrum vorübergehend ausgeblendet, damit Chat und Eingabe Platz haben.
* Dieselbe Entwicklersignatur wie Beta 10; vorhandene Einstellungen bleiben beim Update erhalten.

### Chat-Protokoll und Prüfung

Eigene native Java-Implementierung von Engine.IO 4 / Socket.IO über HTTPS-Long-Polling. Keine Kopie des BATC-Clients und keine zusätzliche Bibliothek. Protokollreferenzen:
https://socket.io/docs/v4/engine-io-protocol/
https://socket.io/docs/v4/socket-io-protocol/
BATC-Chat: https://eshail.batc.org.uk/wb/chat/
Client-Protokollquelle: https://eshail.batc.org.uk/lib/chat.js?v=5
Raumkonfiguration: https://eshail.batc.org.uk/wb/chat/index.js?v=0

Lokaler Protokolltest mit Java 17 und org.json (nur Testabhängigkeit):

    java com.sun.tools.javac.Main --release 17 -cp json.jar -d test-classes \
      app/mobile/src/main/java/de/dl2ghm/longmyndmobil/ChatClient.java tests/ChatClientTest.java
    java -cp test-classes:json.jar de.dl2ghm.longmyndmobil.ChatClientTest

Geprüft: anonymes Lesen, Verlauf, Nickname-Bestätigung, Unicode/JSON beim Senden an einen lokalen Testserver, Ping/Pong, Wiederverbindung, Abmelden, Hintergrundzustand und bisherige Spektrummodelltests. Zusätzlich echter BATC-Verlauf ausschließlich lesend. Keine Testnachrichten im öffentlichen Chat. Kein Test auf einem echten Android-Gerät durchgeführt; Video, Tastatur und Touchbedienung bitte dort prüfen.

## Android 0.1.0-beta.10 – QO-100-App-Symbol

* Neues grünes Fernsehsymbol mit Spektrum und kleinem QO-100-Satellitenemblem.
* Funktionen und Video-Vollbild aus beta.9 bleiben erhalten.
* Mit derselben Entwicklersignatur wie beta.9 signiert; als Update installierbar.
* Kein Update auf dem Raspberry Pi erforderlich.

## Android 0.1.0-beta.9 – Video-Vollbild per Doppeltipp

* Doppeltipp auf eines der Videos öffnet genau diesen Kanal bildschirmfüllend, ohne Menüzeile, Spektrum, Statuszeile oder Kanalbadge.
* Erneuter Doppeltipp oder Android-Zurück stellt die vorherige Ein-/Zweikanalansicht samt Sichtbarkeit von Spektrum, Status und Diagnose wieder her.
* Das Video wird im Vollbild ohne Verzerrung flächenfüllend skaliert; bei abweichendem Seitenverhältnis werden Ränder beschnitten. Außerhalb des Vollbilds gilt wieder die bisherige vollständige Darstellung.
* Vollbild ändert keine gespeicherten Ansichtseinstellungen. Drehen und Stream-Neustarts behalten den temporären Vollbildmodus bei.
* Lautstärkegesten bleiben nutzbar. Im Vollbild läuft nur der angezeigte Player, beim Zurückkehren wird der zweite bei Bedarf wieder gestartet.
* Kein Update auf dem Pi erforderlich.

## Android 0.1.0-beta.8 – Geradlinige Signalflanken und Fangbereich

* Die unteren Farbgrenzen folgen der geraden Verlängerung der 30–70%-Signalflanken. Ausläufer im Rasen verbreitern die Färbung nicht mehr.
* Die Farbfläche wird zusätzlich mit diesen Flankengeraden beschnitten, bis zum unteren Rand der Darstellung. Nachbarsignale bleiben getrennt.
* Kurzes Antippen fängt den gesamten Signalberg einschließlich Flanken und einen kleinen zusätzlichen Rand (10 dp, höchstens 180 kHz). Bei mehreren Kandidaten gewinnt der nächstgelegene Signalrand.
* Langes Drücken öffnet weiterhin die manuelle Symbolratenauswahl. Weit vom Signal entfernte Tipps bleiben manuell.
* Rufzeichenspeicher, nummerierte Kreise, Video und Steuerung bleiben erhalten; kein Pi-Update nötig.

## Android 0.1.0-beta.7 – Signalflanken und Rufzeichenspeicher

* Die Färbung des ausgewählten Signals reicht jetzt bis zum Rauschboden einschließlich der Flanken; Nachbarsignale werden abgegrenzt.
* Obere und untere Hälfte behalten die jeweilige Kanalfarbe und die nummerierten Kreise.
* Erkannte Rufzeichen bleiben am zugehörigen Signalberg stehen, auch wenn ein Kanal weitergeschaltet wird oder kurz den Lock verliert.
* Erst wenn das Signal im laufenden Spektrum mindestens zwei Sekunden durchgehend fehlt, wird seine Zuordnung gelöscht. Kurze Aussetzer führen nicht zum Flackern.
* Bei Ausfall des Spektrumfeeds wird keine Signalabschaltung angenommen. Die Zuordnungen gelten für die laufende App-Sitzung.
* Kein Update auf dem Pi notwendig. Video und Steuerung bleiben unverändert.

## Android 0.1.0-beta.6 – Rufzeichen im Spektrum

Die Rufzeichenbeschriftung zeigt nur noch das Rufzeichen ohne K1/K2 davor. Die nummerierten Kreise und übrigen Kanalmarkierungen bleiben erhalten.

## Android 0.1.0-beta.5 – Spektrummarkierungen

* Nicht ausgewählte Signale erscheinen dezent grau.
* Das ausgewählte Signal wird für Kanal 1 in der oberen Hälfte grün, für Kanal 2 in der unteren Hälfte orange eingefärbt.
* Ein ausgefüllter Kreis mit 1 oder 2 sitzt auf der eingestellten Mittenfrequenz innerhalb der entsprechenden Signalfläche, sofern dort genügend Platz ist.
* Erreicht das Signal die entsprechende Hälfte nicht oder ist es für einen lesbaren Kreis zu klein, erscheint ein kurzer Strich auf der Mittenfrequenz.
* Rufzeichen erscheinen weiterhin nur bei Lock. Beide Kanäle können dasselbe Signal in getrennten Hälften markieren.
* Tippen, Zoomen, Lautstärkemischung und Videoverhalten bleiben erhalten. Kein Update auf dem Pi erforderlich.

# Longmynd Mobil – erste lokale Android-Beta

Zwei Longmynd-Kanaele auf Android-Handys und Tablets: Video, gemischter
Ton mit getrennten Pegeln, Frequenz/Symbolrate, Bake-Taste, MER und geschaetzte Empfangsreserve.
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
Decodiergrenze. Die sichtbaren Kanaele werden mit unabhaengigen Pegeln gemischt.

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


## Android beta.4: Video-Oberflaeche und Gesten

App-Update; kompatibel zum installierten Pi-Dienst beta.2. Kein Pi-Update.
- Spektrum obere Haelfte -> Kanal 1, untere Haelfte -> Kanal 2. Gilt fuer
  Antippen und langes Halten. Pinch/Schieben/Doppeltipp bleiben erhalten.
- Gefuellte Spektrumskurve. Empfangene Rufzeichen am Marker der jeweiligen
  Kanalhaelfte, sofern der lokale Status Lock und einen Dienstnamen meldet.
- Kompakte Werkzeugleiste mit Daten, FFT, Ton, Videoansicht und Zahnrad.
- Zahnrad oeffnet eine eigene Konfigurationsseite mit Adresse, Verbindung,
  Decoderwahl, Anzeigeoptionen, Diagnose und manueller Abstimmung.
- Videos fuellen die verbleibende Flaeche ohne zusaetzliche Bedienzeilen.
  Seitenverhaeltnis bleibt erhalten; keine Bildverzerrung/automatische Beschneidung.
- Empfangsdaten als einzeiliges Overlay pro Video. Taste "Daten" schaltet es
  ein/aus; die Wahl wird gespeichert. Diagnosetext ist separat konfigurierbar.
- Tippen auf Video zeigt seinen Pegel. Doppeltipp schaltet zwischen Einzel-
  und Doppelansicht. Die Taste "1+2"/"1"/"2" bietet dieselbe Ansichtsauswahl.
- Video kurz gedrueckt halten, dann vertikal ziehen: hoch = lauter,
  runter = leiser, pro Kanal 0 bis 100 Prozent. Prozentanzeige waehrend der
  Geste; der andere Kanal behaelt seinen Pegel. Werte bleiben
  gespeichert. Die Android-Systemlautstaerke wird nicht veraendert.
- FFT-Taste blendet das Spektrum fuer eine noch groessere Videoflaeche aus.
- Immersive Anzeige; Android-Systemleisten per Wischgeste am Rand erreichbar.

Pruefung: Java-Kompilation gegen Android SDK 35/libVLC und APK-Signatur/
Paket-/Versionsdaten geprueft. Kein Emulator- oder Geraete-UI-Test hier.
Die veraenderten Touchgesten und das Layout muessen auf dem Tablet geprueft
werden. Video-/HTTP-/Steuerungsdienst wurde fuer diese UI-Aenderung nicht geaendert.


Ergaenzung zur beta.4 nach Nutzerpraezisierung:
Beide sichtbaren Kanaele werden gleichzeitig gehoert/gemischt. Jeder Player
hat einen unabhaengigen Pegel von 0 bis 100 Prozent, initial je 50 Prozent,
sofern noch kein Pegel gespeichert war. Halten/Ziehen veraendert ausschliesslich
den betroffenen Kanal und schaltet den anderen nicht stumm. Tippen zeigt den
Pegel, ohne die Mischung zu aendern. Taste "Mix"/"Stumm" schaltet die gesamte
Mischung stumm bzw. wieder ein. In der Einzelansicht bleibt wie bisher nur
der sichtbare Player aktiv; die Pegel beider Kanaele bleiben gespeichert.
