# Karten- und Overlay-Design: „Paper-Adventure"

Ziel: Die Reiseansicht wirkt wie eine warme Abenteuerkarte, ein moderner
Kontrast-Stadtplan und eine leicht geneigte Polaroid-Pinnwand – bei voller
Lokalität der Daten und ohne externe Stil-Server.

## Farbwelt

| Zweck | Farbe |
| --- | --- |
| Pergament / Papier | `#F6EEDB`, `#EFE2C6` |
| Wasser (lesbar bleiben) | `#4A7C9B` |
| Routenlinie / Kontur | `#3B2B20` |
| Krümel / Wegpunkte | `#D99B52` mit dunkler Kontur |
| App-Petrol / dunkle Akzente | `#0E3540` |
| Bernstein-Akzent | `#F5A93B` |

## Technische Umsetzung im gewählten Stack

### Kartenbasis

osmdroid `6.1.20` (Apache-2.0) wird über `AndroidView` in eine Compose-Fläche
eingebettet. Darüber liegen Compose-Elemente für Info-Karte, Kompass und
Dialoge. Die Kartenkacheln (Tiles) werden beim Ansehen lokal gecacht; ein
Schalter kann die Internetverbindung für Karten deaktivieren
(`setUseDataConnection(false)`). Es gibt bewusst **keinen Massen-Download**
von Karten – nur tatsächlich angesehene Ausschnitte werden gespeichert. Das
hält sich an die Nutzungsregeln von OpenStreetMap-Tileservern.

**Hinweis:** osmdroid wird seit November 2024 nicht mehr weiterentwickelt
(archiviert, Version 6.1.20). Die Bibliothek ist stabil und erfüllt alle
hier benötigten Funktionen; die Entscheidung darüber wird ausdrücklich
festgehalten (siehe offene Punkte).

### Pergament-Farbfilter

osmdroid unterstützt seit Version 5.1 einen `ColorFilter` direkt auf der
Kartenebene (`TilesOverlay.setColorFilter(...)`). Damit wird eine
`ColorMatrixColorFilter` auf die gezeichneten Kacheln angewendet:

1. Farben teilweise entsättigen,
2. leicht in Richtung Creme/Sepia verschieben,
3. Helligkeit anheben,
4. Blauanteil teilweise erhalten, damit Wasser als Petrol lesbar bleibt.

Vorteil: Die Original-Kacheln bleiben im Cache, der Filter ist sofort
umschaltbar und es entsteht keinerlei zusätzlicher Speicher- oder
Rechenaufwand pro Kachel. Eine dezente Vignette wird als separater,
statischer Gradient darüber gezeichnet. Papierkörnung nur sehr dezent und
optional, damit die Karte ruhig bleibt.

### Geschwungene Breadcrumbz-Route

Ein eigener osmdroid-`Overlay` zeichnet die Route:

- Geo-Koordinaten werden über `Projection.toPixels(...)` in Bildschirmpunkte
  übersetzt.
- Die Punkte werden über quadratische/kubische Kurven verbunden, damit die
  Linie geschwungen wirkt statt eckig.
- Zwei Pinsel-Durchgänge: dunkle Kontur `#3B2B20` und darüber eine
  cremefarbene Innenlinie.
- `DashPathEffect` erzeugt die gestrichelte Nebenlinie, `CornerPathEffect`
  rundet die Übergänge, `strokeCap = ROUND` rundet die Enden.
- Kleine organische Krümel `#D99B52` liegen auf der Linie bzw. an
  Zwischenpunkten.
- Die Kilometer werden bei jeder Änderung direkt aus den Koordinaten
  neu berechnet (Luftlinie/Haversine). Straßen-km über OSRM laufen
  asynchron und überschreiben den Wert nur bei Erfolg.

### Polaroid-Foto-Pins

Ebenfalls ein eigener `Overlay`, damit die Pins beim Zoomen und Verschieben
exakt an der Karte kleben:

- `canvas.save()` / `rotate()` / `drawBitmap()` / `restore()` erzeugen die
  leicht geneigte Polaroid-Karte.
- Rahmen in Creme, weicher Schlagschatten, kleine Nadel plus feiner
  Verbindungslinie zum exakten Kartenpunkt.
- Das Foto-Bitmap wird über Coil geladen, verkleinert und gecacht – nie in
  Originalauflösung gezeichnet.
- Ein Hit-Test auf dem Overlay öffnet Foto und zugehörigen Tagebucheintrag.

### Kompass und Dekoration

Die Kompassrose liegt als Vektor-Icon fest in einer Ecke des Bildschirms
(Compose-Ebene) – das ist am billigsten und bleibt beim Kartenverschieben
stabil. Tannen, Gipfel und Wegweiser sind ein **modulares, optionales**
Overlay, das erst nach den Kernfunktionen kommt und pro Reise einzeln
abgeschaltet werden kann. Keine Bildgenerierung zur Laufzeit.

### Info-Karte

Abgerundete Compose-`Card` unten: cremefarbener Hintergrund, dunkle Kontur,
Reisetitel, Gesamtkilometer und die Aktionen „Anpassen" und „Zeigen".

## Performance-Regeln

- Paints, ColorFilter und Pfad-Effekte einmal anlegen, nicht pro Frame.
- Route nur bei Daten-, Zoom- oder Positionsänderung neu berechnen.
- Foto-Bitmaps in fester, kleiner Größe cachen.
- Kacheln nicht nachbearbeiten oder neu speichern – nur der Filter wird
  gesetzt.
- Vignette als statischer Gradient, Deko nur auf Wunsch.

## Angepasster Meilenstein-Plan

| Nr. | Inhalt |
| --- | --- |
| M2 | Reisen anlegen, bearbeiten, löschen und als Liste zeigen |
| M3 | Funktionale OSM-Karte, Marker, Drag & Drop, Live-Luftlinien-km |
| M4 | Foto-Import mit EXIF, Chronologie, Polaroid-Pins, GPS-Lücken manuell |
| M5 | Tagebucheinträge, Timeline, Karte ↔ Timeline verknüpft |
| M6 | Paper-Adventure-Overlay: 6.1 Pergament-Filter + Vignette, 6.2 geschwungene Doppel Route + Krümel, 6.3 Polaroid-Politur, 6.4 Kompass + optionale Vektor-Deko, 6.5 Info-Karte |
| M7 | ZIP-Backup/Import mit Snapshot und Fehlertoleranz |
| M8 | Präsentationsmodus |

Die Funktionen kommen vor der Politur: erst Karte und Route korrekt, dann
das Aussehen. So bleibt jeder Schritt testbar.

## Entscheidungen (24.11.2026)

1. **Kartenbibliothek:** Noch keine endgültige Wahl. Zuerst wird M2 (Reisen
   verwalten) ohne Karte gebaut. Die Kartenschicht wird so gekapselt, dass
   osmdroid oder eine gepflegte Alternative später ausgetauscht werden kann.
2. **Pergament-Filter:** `ColorMatrixColorFilter` auf der Kartenebene – sofort
   umschaltbar, kein zusätzlicher Speicher.
3. **Polaroid-Pins:** Eigener Overlay direkt auf der Karte – exakte Haftung
   bei Zoom und Verschieben, gut performant.
