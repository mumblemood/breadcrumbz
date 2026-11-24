# Breadcrumbz

Eine lokale, quelloffene Android-App, um Reisen, Urlaube und Ausflüge mit Fotos,
Kartenroute und Tagebuch schön zu dokumentieren.

**Aktueller Stand: Meilenstein 0 – Build-Test.**
Die App zeigt gerade nur einen kleinen Begrüßungsbildschirm. Ziel dieses
Schritts ist es, den gesamten Weg vom Quellcode bis zur installierbaren APK
auf dem Handy zu prüfen, bevor die eigentlichen Funktionen kommen.

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
  data/     Datenhaltung, Foto-Auswertung, Karten- und Backup-Logik (folgt)
  domain/   Geschäftsregeln (folgt)
  state/    ViewModels und aktueller Bildschirm-Zustand (folgt)
  ui/       Oberfläche: theme, trips, map, timeline, entry, overlay, ...
```

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
