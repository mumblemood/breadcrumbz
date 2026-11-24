# Breadcrumbz

Eine lokale, quelloffene Android-App, um Reisen, Urlaube und Ausflüge mit Fotos,
Kartenroute und Tagebuch schön zu dokumentieren.

**Aktueller Stand: Version 0.10.0 – Bedienung, Pop-up-Details und Zeitleiste verfeinert.**

Die **Startseite** zeigt alle Reisen chronologisch, die neueste oben. Das **„+"**
ganz oben legt sofort eine neue Reise an und öffnet direkt die Karte – ohne
Dialog. Das **Dreistrich-Menü** oben rechts enthält Einstellungen sowie
**Backup erstellen** und **Backup einspielen**. Ein Tippen auf eine Reise öffnet
sie, langes Drücken öffnet die Bearbeitung.

Der Reisetitel steht in der Kartenkopfleiste. Solange er automatisch ist, steht
dort kursiv **„neue Reise"**; ein Tippen öffnet ein direktes Textfeld (kein
Pop-up). Sobald der erste Ort, Pin oder Eintrag bekannt ist, übernimmt die App
ihn automatisch als Titel – weiterhin kursiv, bis du den Titel selbst tippst.
Danach bleibt er fest. Das Dreistrich-Menü in der Karte enthält die
Reise-Einstellungen für Linienstil und Kartenstil. Die Zähler und der alte
Knopf **„Linie:auto"** sind entfernt.

**Pins:** Zwei Sekunden Halten auf freier Karte erzeugt einen Pin. Ein Tippen
wählt ihn aus; der ausgewählte Pin wird **1,5× größer**. Nur ein Element ist
gleichzeitig ausgewählt, ein Tippen ins Leere hebt die Auswahl auf. Die Nadel
zeigt mit der Spitze auf die Mitte des oberen weißen Polaroid-Rands. Fotos werden
nie verzogen: Der Rahmen passt sich dem Seitenverhältnis an, alle Ränder sind
gleich breit und nur der untere Rand ist für die Beschriftung dicker. Die
Polaroids werden mit dem Zoom größer und kleiner. Pinch-Zoom funktioniert
überall; währenddessen wird nichts ausgewählt. Als kleinster Zoom ist Stufe 4
eingestellt.

**Timeline:** Sie lässt sich überall wischen; Ziehen eines Elements startet erst
nach längerem Halten. Die schwarze gestrichelte Linie ist dick und hat nur einen
dünnen weißen Rand. Ein aufgeklappter Tag hat eine größere grüne Markierung, ein
aufgeklappter Eintrag eine hellgraue, die alle zugehörigen Elemente umschließt.
Pins haben einen farbigen Rand; vor Ortsnamen steht immer ein einfarbiges Pin-
Symbol in Textgröße.

**Ortsnamen** werden automatisch als **„Stadt, Staat"** aus den Koordinaten
ermittelt. Vorhandene alte Namen werden beim ersten Start dieser Version einmalig
aufgefrischt und können in den Einstellungen erneut aktualisiert werden.

**Backup:** Alle Daten bleiben lokal. Ein Export schreibt eine ZIP-Datei mit
`reise_daten.json`, `bilder/` und `manifest.json` über die System-Auswahl. Vor
einem Import wird die bestehende Datenbank als Snapshot kopiert; der Import liest
toleranter, auch wenn einzelne Zeilen beschädigt sind.

**Einträge entstehen über das „+" in der Karte oder über das Plus über einem
ausgewählten Pin ohne Eintrag.** Es sind mehrere Einträge pro Tag und mehrere
Elemente pro Eintrag möglich. Elemente öffnen sich beim Antippen und werden
dort direkt bearbeitet. Fotos schreiben Zeit und Ort nur in noch leere Felder
des Eintrags; vorhandene Werte bleiben erhalten. Ein Eintrag aus der Timeline
kann nach langem Halten auf einen Pin oder auf freie Karte gezogen werden – dort
entsteht dann ein neuer Pin. Beim Ziehen eines Pins verschieben sich Eintrag und
Bilder mit. Alte Einzelbilder wurden bereits in eigene Einträge überführt –
nichts ging verloren. Während des Pinch-Zooms wird nichts ausgewählt.

Die Detailansichten öffnen sich als **Pop-up** über leicht abgedunkeltem
Hauptbereich. Zeit und Ort stehen direkt unterm Titel. Wurden sie aus dem ersten
Foto übernommen, stehen sie kursiv mit **„(aus erstem Foto)"**; ein Tippen
öffnet eine bequeme Auswahl und entfernt den Kursiv-Hinweis. Das Foto selbst hat
einen großen Polaroid-Rahmen mit dem Titel im unteren Rand (Comic-Schrift);
Ort und Zeit stehen darunter klein. Die festen Papier-/Tintafarben bleiben bei
**hell und dunkel** gleich gut lesbar.

**Aus früheren Meilensteinen:** Foto-Import mit EXIF, Gruppierung, Polaroid-Pins,
Karte mit osmdroid und lokalem Karten-Cache, Live-Luftlinie in km, Einträge mit
Datum/Uhrzeit und Bildern, Vollbild-Details, Clustering und Routing pro
Verkehrsmittel. Ohne Internet wird beim Ortsnamen „Wegpunkt n" verwendet.
Fotos werden als Kopie im App-Speicher abgelegt; seit Android 10 fragt die App
beim Import die Erlaubnis für Media-Standorte an und liest das Original.

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
