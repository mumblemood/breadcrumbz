# Breadcrumbz

Eine lokale, quelloffene Android-App, um Reisen, Urlaube und Ausflüge mit Fotos,
Kartenroute und Tagebuch schön zu dokumentieren.

**Aktueller Stand: Version 0.15.0 – Ort direkt aus Fotos, Linien und Rahmen vereinheitlicht, Zeitleiste zuverlässig synchron.**

Die **Reisenliste** (Startseite) zeigt alle Reisen chronologisch, die neueste oben.
Das **App-Logo** steht oben links. Das **„+"** sitzt mittig oben **innerhalb der Liste**
und legt sofort eine neue Reise an und öffnet direkt die Reisekarte – ohne Dialog.
Jede Reisekarte zeigt nur **Titel und Zeitraum**; der Zeitraum wird aus Fotos,
Pins und Einträgen abgeleitet, manuell gesetzte Werte behalten Vorrang. Ist ein
Titelbild festgelegt, liegt es leicht sichtbar im Hintergrund der Karte. Das
**Dreistrich-Menü** oben rechts enthält Einstellungen sowie
**Backup erstellen** und **Backup einspielen**. Ein Tippen auf eine Reise öffnet
sie, langes Drücken öffnet die Reisebearbeitung mit genau einem Feld: dem Titel.
Der Zeitraum wird nicht mehr angefasst, sondern bleibt automatisch.

Der Reisetitel steht in der Kartenkopfleiste. Solange er automatisch ist, steht
dort kursiv **„neue Reise"**; ein Tippen öffnet ein direktes Textfeld (kein
Pop-up). Ein Tippen woandershin (auf die Karte) oder die Eingabetaste beendet die
Bearbeitung und speichert den Titel. Sobald der erste Ort, Pin oder Eintrag
bekannt ist, übernimmt die App ihn automatisch als Titel – weiterhin kursiv, bis
du den Titel selbst tippst. Danach bleibt er fest. Das Dreistrich-Menü in der Karte öffnet die
**Reise-Einstellungen** mit Linienstil, Kartenstil, Standard-Verkehrsmittel und
**Titelbild wählen** (alle Fotos der Reise als Vorschau). Diese Einstellungen
ändern nur die Reise selbst – Einträge, Pins und Fotos bleiben erhalten. Die
Zähler und der alte Knopf **„Linie:auto"** sind entfernt.

**Linie:** Karte und Zeitleiste zeigen denselben Stil: weißer Unterzug und eine
schwarze gestrichelte Linie darüber. Ein Tippen auf die Linie öffnet keine
Sprechblase, sondern die **Auswahl des Verkehrsmittels** für diesen Abschnitt.
Mit Auto oder Campingbus wird die Route nur ungefähr über Straßen geführt – ein
Zwischenpunkt pro Kilometer genügt. Flugzeug und Kajak werden als Bogen
gezeichnet.

**Pins:** Zwei Sekunden Halten auf freier Karte erzeugt einen Pin. Ein Tippen
wählt ihn aus; der ausgewählte Pin wird **1,5× größer**. Nur ein Element ist
gleichzeitig ausgewählt, ein Tippen ins Leere hebt die Auswahl auf. Die Nadel
zeigt mit der Spitze auf die Mitte des oberen weißen Polaroid-Rands. Fotos werden
nie verzogen: Der Rahmen passt sich dem Seitenverhältnis an, alle Ränder sind
gleich breit und nur der untere Rand ist für die Beschriftung dicker. Die
Polaroids werden mit dem Zoom größer und kleiner. Pinch-Zoom funktioniert
überall; währenddessen wird nichts ausgewählt. Statt eines Rings wird der
ausgewählte Pin nur vorübergehend **1,5× größer** dargestellt. Der kleinste Zoom ist Stufe 1: Solange es noch keine
Fotos und Pins gibt, bleibt die **aktuelle Position in der Mitte** und die Karte
wird nur so weit rausgezoomt, dass sie den Bildschirm in der Höhe füllt. Danach
passt sie die Karte mit sicherem Rand an alle Punkte an.
Foto-Stapel entstehen nach dem **Abstand der Originalorte**: nahe Fotos stapeln
sich, entfernte liegen einzeln. Klappst du einen Eintrag in der Zeitleiste auf,
wandern seine Fotos vorübergehend einzeln an ihre Originalorte und bekommen
jeweils eigene Pins.

**Timeline:** Sie lässt sich überall wischen; Ziehen eines Elements startet erst
nach längerem Halten. Die Linie sieht genauso aus wie auf der Karte. Ein Tippen
auf den Rahmen **oder auf die Eintragskarte** öffnet bzw. schließt Tag und Eintrag
– Pfeile gibt es nicht. Beim Ziehen leuchtet der Eintrag auf, dem du das Element
zuordnen willst. **Tagesrahmen überlappen sich nicht mehr**: ihre Grenzen liegen
genau in der Mitte zwischen zwei Tagen. Es gibt nur noch den Tagesrahmen und den
grauen Eintragsrahmen – der frühere beige Rahmen um die Eintragskarte ist weg.
Ihre Höhe bleibt beim Auf-/Zuklappen gleich, nur die Breite ändert sich. Das
Element in der **Bildschirmmitte bleibt dabei stehen**; automatische Bewegungen
laufen weich. Wischen in der Zeitleiste zentriert das Element auf der Karte und
zoomt nötigenfalls auf Stufe 15; bewegt man die Karte, folgt die Zeitleiste. Die
**Kilometerleiste** liegt oben und überlappt keine Pins. Die
Zeitleiste nutzt die unteren zwei Fünftel des Bildschirms; Fotos aufgeklappter
Einträge sind größer. Pins erscheinen als **Nadel** wie auf der Karte, ohne
zusätzlichen Rahmen; vor Ortsnamen steht ein einfarbiges Pin-Symbol in
Textgröße.

**Ortsnamen** werden automatisch als **„Stadt, Staat"** aus den Koordinaten
ermittelt. Vorhandene alte Namen werden beim ersten Start dieser Version einmalig
aufgefrischt und können in den Einstellungen erneut aktualisiert werden.

**Backup:** Alle Daten bleiben lokal. Ein Export schreibt eine ZIP-Datei mit
`reise_daten.json`, `bilder/` und `manifest.json` über die System-Auswahl. Vor
einem Import wird die bestehende Datenbank als Snapshot kopiert; der Import liest
toleranter, auch wenn einzelne Zeilen beschädigt sind.

**Einträge entstehen über das „+" unten rechts in der Karte oder im
Elementfenster eines Pins über „+ Eintrag hier“.** Es sind mehrere Einträge pro
Tag und mehrere Elemente pro Eintrag möglich. Elemente öffnen sich beim Antippen
und werden dort direkt bearbeitet – auch das Löschen gibt es nur dort. Fotos
übergeben **Datum, Uhrzeit und Ort** direkt an den Eintrag – der Ort steht also
sofort im Detailfenster, nicht nur als Hinweis. Felder, die du selbst gesetzt
hast, bleiben erhalten. In der Zeitleiste gibt es genau **einen Pin je Foto mit
Ort und einen Pin für den Eintrag selbst**, keine zusätzlichen Pins. Ein Eintrag aus der Zeitleiste kann nach langem
Halten auf einen Pin oder auf freie Karte gezogen werden – dort entsteht dann
ein neuer Pin. Beim Ziehen eines Pins verschieben sich Eintrag und Bilder mit.
Alte Einzelbilder wurden bereits in eigene Einträge überführt – nichts ging
verloren. Während des Pinch-Zooms wird nichts ausgewählt. Ein schlechtes Foto
überspringt der Import, statt die App abzustürzen.

Die Detailansichten öffnen sich als **Pop-up** über leicht abgedunkeltem
Hauptbereich; ein Klick daneben schließt es. Im Eintrag siehst du auch die
angehängten Fotos, aber **keine Liste der Pins** mehr. Foto-Orte werden aus dem
unveränderten Original ausgelesen und notfalls aus den GPS-Spalten des
MediaStore ergänzt – bei SAF-Kopien zusätzlich über den Dateinamen, auch wenn die
Galerie-Auswahl sonst nur eine GPS-lose Kopie liefert. Zeit und Ort stehen
direkt unterm Titel. Wurden sie aus dem ersten
Foto übernommen, stehen sie kursiv mit **„(aus erstem Foto)"**; ein Tippen
öffnet eine bequeme Auswahl und entfernt den Kursiv-Hinweis. Das Foto selbst hat
einen großen Polaroid-Rahmen mit dem Titel im unteren Rand (Comic-Schrift).
Ein Tippen auf den Titel öffnet ein direktes Textfeld – ohne Extra-Knopf. Der
Standardtext **„Titel"** ist kursiv, dein eigener Titel steht aufrecht.
Koordinaten werden nicht angezeigt; der **Ortsname** genügt. Die festen
Papier-/Tintafarben bleiben bei **hell und dunkel** gleich gut lesbar.

**Aus früheren Meilensteinen:** Foto-Import mit EXIF, Gruppierung, Polaroid-Pins,
Karte mit osmdroid und lokalem Karten-Cache, Live-Luftlinie in km, Einträge mit
Datum/Uhrzeit und Bildern, Vollbild-Details, Clustering und Routing pro
Verkehrsmittel. Ohne Internet wird beim Ortsnamen „Wegpunkt n" verwendet.
Fotos werden als Kopie im App-Speicher abgelegt; seit Android 10 fragt die App
beim Import die Erlaubnis für Media-Standorte an und liest das Original.

## Begriffe: Ansichten und Fenster

| Begriff | Was gemeint ist |
| --- | --- |
| **Reisenliste** | Die Startseite mit allen Reisen, neueste oben. |
| **Reisekarte** | Der Kartenbereich einer geöffneten Reise, mit Pins und Fotos. |
| **Zeitleiste** | Der untere Bereich mit Linie, Rahmen und Elementen. |
| **Kilometerleiste** | Die obere Zeile der Zeitleiste mit km-Marken und „Gesamt". |
| **Elementfenster** | Das Pop-up für ein Foto, einen Pin oder einen Eintrag. |
| **Foto-Stapel-Fenster** | Die Liste, wenn mehrere Fotos an einem Ort liegen. |
| **Reise-Einstellungen** | Das Dreistrich-Menü oben rechts in der Reisekarte. |
| **Titelbild-Auswahl** | Der Dialog aus den Reise-Einstellungen mit allen Fotos der Reise. |
| **Reisebearbeitung** | Der Dialog nach langem Drücken auf eine Reise in der Reisenliste – nur für den Titel. |
| **Verortung** | Einen Pin setzen/ziehen oder den Fotoort in einen Eintrag übernehmen. |

## Grundsätze

- 100 % lokal auf dem Gerät, kein Konto und kein Cloud-Zwang in der App
- OpenStreetMap-Karten über osmdroid, mit lokalem Karten-Cache
- Quelloffen unter der MIT-Lizenz (siehe `LICENSE`)
- Saubere Trennung von Daten, Regeln und Oberfläche, damit später Chromecast
  ergänzt werden kann, ohne die App umzubauen

Das geplante Karten- und Overlay-Design ist in `docs/map-design.md` beschrieben.

## Technik

- Kotlin + Jetpack Compose (Material 3)
- Android Gradle Plugin 8.6.1, Gradle 8.7, Kotlin 2.0.20
- Mindest-Android-Version: 8.0 (API 26)

## Projektaufbau

```text
app/src/main/java/de/breadcrumbz/app/
  data/     Datenbank, Tabellen, Foto-Ablage und Repository-Implementierung
  domain/   Datenmodelle und das Repository-Interface (ohne Android-Bezug)
  state/    ViewModels und aktueller Bildschirm-Zustand
  ui/       Oberfläche: theme, trips, map, timeline, entry, overlay, ...
app/schemas/  Versionierte Datenbank-Schemata für spätere Upgrades
```

Fotos werden als Kopie im privaten App-Speicher abgelegt
(`filesDir/photos/`), damit sie auch nach dem Löschen in der Galerie
erhalten bleiben und vollständig gesichert werden können.

Pro Station können mehrere Tagebucheinträge gespeichert werden.

## APK bauen

Der Build läuft in der Cloud (GitHub Actions), weil ein Android-Build direkt
auf einem ARM-Handy nicht verlässlich funktioniert.

Die Datei `app/debug.keystore` liegt absichtlich im Repository: Sie ist der
gemeinsame Test-Schlüssel, damit jede neue APK dieselbe Signatur hat und über
eine bestehende Installation installiert werden kann. Für eine echte
Veröffentlichung später wird ein separater, geheimer Release-Schlüssel nötig.

1. Änderungen in den Hauptzweig `main` hochladen (`git push`).
2. GitHub baut automatisch eine Test-APK.
3. Die Datei heißt jetzt immer **`Breadcrumbz_v0.x.apk`**. Die Zahl kommt aus
   der `versionName` in `app/build.gradle.kts`: aus `0.8.0` wird
   `Breadcrumbz_v0.8.apk`, aus `0.9.0` wird `Breadcrumbz_v0.9.apk`.
4. Die APK liegt unter **Releases → neueste Testversion** als Download bereit.
5. Zusätzlich wird sie nach dem Download auf dem Gerät unter
   **Interner Speicher → mumblemood** abgelegt. Dort kannst du sie direkt
   antippen und installieren. Dafür muss einmalig „Apps aus unbekannten
   Quellen installieren" erlaubt sein.

Zum manuellen Bauen auf einem Computer:

```sh
./gradlew assembleDebug
sh scripts/save-apk.sh
```

Die buildete Datei entsteht unter `app/build/outputs/apk/debug/app-debug.apk`.
Das Skript kopiert sie mit dem richtigen Versionsnamen nach
`/sdcard/mumblemood`. Ein anderer Zielordner kann als zweiter Argument
angegeben werden.

## Lizenz

MIT – siehe `LICENSE`.
