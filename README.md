# Breadcrumbz

Eine lokale, quelloffene Android-App, um Reisen, Urlaube und Ausflüge mit Fotos,
Kartenroute und Tagebuch schön zu dokumentieren.

**Aktueller Stand: Meilenstein 6 – dauerhafte Timeline und Einträge mit Bildern.**
Die Karte nimmt die oberen zwei Drittel ein, die **Timeline** ist ein festes
unteres Drittel über die gesamte Fensterbreite. Sie zeigt eine geschwungene,
gestrichelte Linie mit Pin-Nadeln. Tagesmarken und Einträge mit mehreren Bildern
lassen sich aufklappen; die Distanz ist immer als Beschriftung sichtbar und
alle 100 km senkrecht markiert. Der **„+"**-Knopf öffnet einen Dialog für Titel,
Tagebuch, Datum, Uhrzeit und mehrere neue Bilder aus der Galerie. Der Ort wird
automatisch aus GPS und Aufnahmezeit der Bilder bzw. vorhandenen Stationen
bestimmt. Text-Einträge erscheinen als gelbe Zettel auf der Karte, Fotos als
große Polaroids unter dem Marker. Mit **„Standort"** wird die eigene Position
angezeigt und die Karte dorthin zentriert. Foto-Titel sind handschriftlich
(maximal 12 Zeichen), ein Foto kann als Titelbild gesetzt werden. Das
Standard-Verkehrsmittel wird beim Anlegen oder Bearbeiten einer Reise gewählt;
die Linien-Stile pro Abschnitt folgen in einem späteren Schritt.

**Aus Meilenstein 5:** Beim Setzen eines Punkts wird der Ortsname automatisch
über Nominatim (OpenStreetMap) ermittelt und lokal gecacht; ohne Internet wird
„Wegpunkt n" verwendet. Fotos werden jetzt auch über mehrere Importe hinweg in
einen vorhandenen Foto-Stopp einsortiert (Gruppierungs-Bug behoben). Die Foto-Pins
sind deutlich größer und zeigen bei mehreren Bildern ein Stapel-Symbol mit Anzahl.
Pins können gelöscht werden: Einträge bleiben ohne Ort in der Timeline erhalten
und lassen sich dort mit „Ort setzen" neu verorten; Fotos wandern in „Ohne Ort".
Die Taste **„Ganze Reise"** zoomt auf alle Stationen und Fotos.

Meilenstein 4 hatte den Foto-Import mit EXIF, Gruppierung und Polaroid-Pins
gebracht. Fotos werden als Kopie im App-Speicher abgelegt; Aufnahmezeit und GPS
kommen aus den EXIF-Daten. Seit Android 10 blendet das System GPS-Daten in
Galerie-Fotos aus; die App fragt beim Import die Erlaubnis für Media-Standorte
an und liest das unveränderte Original.

Meilenstein 3 hatte Karte, Wegpunkte und Live-Kilometer gebracht, Meilenstein 2
die Reisen-Verwaltung, Meilenstein 1 das Datenfundament, Meilenstein 0 den
Build-Weg bis zur APK.

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
3. Die APK liegt unter **Releases → neueste Testversion** als Download bereit.
4. Auf dem Handy den Download öffnen und installieren. Dafür muss einmalig
   „Apps aus unbekannten Quellen installieren" erlaubt sein.

Zum manuellen Bauen auf einem Computer:

```sh
./gradlew assembleDebug
```

Die Datei entsteht dann unter `app/build/outputs/apk/debug/app-debug.apk`.

## Lizenz

MIT – siehe `LICENSE`.
