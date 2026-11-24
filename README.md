# Breadcrumbz

Eine lokale, quelloffene Android-App, um Reisen, Urlaube und Ausflüge mit Fotos,
Kartenroute und Tagebuch schön zu dokumentieren.

**Aktueller Stand: Meilenstein 1 – Datenfundament.**
Die App kann Reisen, Stationen, Tagebucheinträge und Foto-Verknüpfungen lokal
in einer Room-Datenbank speichern. Ein Testbildschirm erzeugt Beispieldaten,
damit die Beziehungen überprüft werden können. Die fertigen Reise-Screens
kommen in Meilenstein 2.

Meilenstein 0 hatte den Build-Weg vom Quellcode bis zur installierbaren APK
auf dem Handy geprüft.

## Grundsätze

- 100 % lokal auf dem Gerät, kein Konto und kein Cloud-Zwang in der App
- OpenStreetMap-Karten über osmdroid, mit lokalem Karten-Cache
- Quelloffen unter der MIT-Lizenz (siehe `LICENSE`)
- Saubere Trennung von Daten, Regeln und Oberfläche, damit später Chromecast
  ergänzt werden kann, ohne die App umzubauen

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
