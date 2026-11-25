# Breadcrumbz

Breadcrumbz ist eine quelloffene Android-App zum Dokumentieren von Reisen,
Urlauben und Ausflügen. Fotos, Karte, Route, Pins und Tagebucheinträge kommen
in einer Reise zusammen.

Alle Daten bleiben **auf deinem Gerät**. Es gibt kein Konto, keine Anmeldung
und keinen Cloud-Zwang. Die App nutzt OpenStreetMap und ist unter der MIT-Lizenz
veröffentlicht.

## Was du damit machen kannst

- **Reisen anlegen:** Die Startseite zeigt deine Reisen in zeitlicher Reihenfolge.
  Mit dem „+" oben in der Liste legst du sofort eine neue Reise an und landest
  direkt auf ihrer Karte. Der Reisetitel lässt sich jederzeit direkt antippen
  und umschreiben.
- **Karte erkunden:** Die Karte ist heran- und herauszoombar. Beim Öffnen einer
  Reise werden alle Pins und Fotos mit etwas Rand sichtbar gemacht. Ein langes
  Drücken (etwa zwei Sekunden) auf freie Karte setzt einen Pin. Ortsnamen werden
  automatisch als „Stadt, Staat" aus den Koordinaten ermittelt; ohne Verbindung
  wird ein Wegpunkt angezeigt.
- **Fotos einbinden:** Fotos werden aus der Galerie oder der Systemauswahl
  importiert. Datum und Ort liest die App aus dem Originalbild und übernimmt sie
  in die Reise. Auf der Karte erscheinen die Bilder als Polaroids. Fotos, die
  nahe beieinander liegen, werden zu einem Stapel zusammengefasst; ein Tippen
  löst den Stapel auf, danach kannst du jedes Bild einzeln öffnen. Bilder lassen
  sich zwischen Einträgen verschieben; der bisherige Eintrag bleibt erhalten.
  Langes Halten auf der Karte löscht ein Foto nach kurzer Rückfrage und entfernt
  es auch aus seinem Eintrag.
- **Route festhalten:** Karte und Zeitleiste zeigen dieselbe gestrichelte Linie.
  Ein Tippen auf die Linie lässt dich das Verkehrsmittel für einen Abschnitt
  wählen. Für Auto und Campingbus wird die Strecke offline handgezeichnet.
  Die Pins werden dabei chronologisch nach dem Datum ihrer Fotos abgefahren.
  Für Auto und Campingbus holt Breadcrumbz die Straßenführung online (nur
  Koordinaten, kein Konto). In den Einstellungen lässt sich das ausschalten.
  Punkte auf der Linie kannst du ziehen: langes Halten und Ziehen setzt einen
  eigenen Zwischenpunkt für die Routenführung.
  Darunter liegt eine Anzeige der Luftlinie in Kilometern.
- **Tagebuch schreiben:** Zu einem Pin oder direkt über das „+" in der Karte
  legst du Einträge mit Titel und Text an. Mehrere Einträge pro Tag sind möglich.
  Datum und Ort kommen auf Wunsch aus dem ersten Foto; was du selbst einträgst,
  bleibt erhalten.
- **Zeitleiste nutzen:** Der Bereich unter der Karte ordnet alles nach Tagen und
  Uhrzeiten. Die Tage sind nummeriert, das Datum steht am Rand. Unten liegt eine
  Scrollbar zum Springen und Ziehen. Mit längerem Halten kannst du Fotos und
  Pins in einen Eintrag ziehen oder Einträge auf der Karte neu verorten.
  Tippen auf ein Foto richtet Karte und Zeitleiste danach aus und markiert es
  mit einem leichten Leuchten.
- **Details ansehen:** Ein Tippen auf ein Foto, einen Pin oder einen Eintrag
  öffnet die Detailansicht. Dort kannst du Titel und Text bearbeiten, Bilder
  einzeln ansehen oder das Original im Android-Bildviewer öffnen. Geschlossen
  wird mit einem Tippen daneben oder der Back-Taste. Die Eintragsansicht ist
  wie ein aufgeschlagenes Buch aufgebaut: ein schmaler Streifen der linken Seite,
  der Falz und die vollständige rechte Seite mit Text und Bildern.
- **Sichern und wiederherstellen:** Über die Systemauswahl kannst du ein
  Backup als ZIP-Datei exportieren oder einspielen. Vor einem Import wird die
  bestehende Datenbank als Snapshot gesichert.

## So kommst du los

1. Öffne Breadcrumbz und tippe auf das „+" in der Reisenliste.
2. Gib der Reise einen Namen und importiere erste Fotos.
3. Setze bei Bedarf Pins auf der Karte und schreibe Tagebucheinträge.
4. Nutze die Zeitleiste, um Ereignisse einzuordnen und zu verschieben.
5. Erstelle von Zeit zu Zeit ein Backup über das Menü in der Reisenliste.

## Grundsätze

- 100 % lokal auf dem Gerät – ohne Konto und ohne Cloud
- OpenStreetMap-Karten über osmdroid mit lokalem Karten-Cache
- Quelloffen unter der MIT-Lizenz (siehe `LICENSE`)
- Klare Trennung von Daten, Regeln und Oberfläche, damit sich die App später
  erweitern lässt

Das geplante Karten- und Overlay-Design ist in `docs/map-design.md` beschrieben.

## Technik

- Kotlin und Jetpack Compose (Material 3)
- Android Gradle Plugin 8.6.1, Gradle 8.7, Kotlin 2.0.20
- Mindest-Android-Version: 8.0 (API 26)
- Room-Datenbank mit versionierten Schemata unter `app/schemas/`
- Fotos werden als Kopie im privaten App-Speicher (`filesDir/photos/`) abgelegt,
  damit sie auch nach dem Löschen in der Galerie erhalten bleiben

## APK herunterladen und installieren

Der Build läuft in GitHub Actions. Nach einem Push auf `main` entsteht automatisch
eine Testversion unter **Releases → neueste Testversion**. Der Dateiname wird aus
der `versionName` in `app/build.gradle.kts` gebildet, zum Beispiel
`Breadcrumbz_v0.18.apk`.

Nach dem Download liegt die APK zusätzlich im Ordner **mumblemood** im internen
Speicher. Dort kannst du sie direkt antippen und installieren. Dafür muss einmalig
„Apps aus unbekannten Quellen installieren" erlaubt sein.

Die Datei `app/debug.keystore` liegt absichtlich im Repository: Sie ist der
gemeinsame Test-Schlüssel, damit jede neue APK über eine bestehende Installation
installiert werden kann. Für eine spätere echte Veröffentlichung wäre ein
separater, geheimer Release-Schlüssel nötig.

Zum manuellen Bauen auf einem Computer:

```sh
./gradlew assembleDebug
sh scripts/save-apk.sh
```

## Lizenz

MIT – siehe `LICENSE`.
