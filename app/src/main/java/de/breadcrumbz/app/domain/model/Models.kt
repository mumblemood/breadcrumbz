package de.breadcrumbz.app.domain.model

/** Legt fest, ob die Karte normal oder als Kontrast-Poster gezeigt wird. */
enum class OverlayMode { STANDARD, POSTER }

/** Woher ein Wegpunkt stammt: aus einem Foto oder manuell gesetzt. */
enum class StopSource { PHOTO, MANUAL }

/** Welche Distanzmethode hinter einem Kilometerwert steckt. */
enum class DistanceSource { STRAIGHT, ROAD }

data class Trip(
    val id: String,
    val title: String,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val coverPhotoId: String?,
    val overlayMode: OverlayMode,
    /** Standard-Verkehrsmittel für neue Abschnitte. */
    val defaultTransportMode: TransportMode,
    /** Linie automatisch oder direkt als Spline. */
    val routeMode: RouteMode,
    /** Kursiv „neue Reise“: Titel darf automatisch zum ersten Ort werden. */
    val titleIsAuto: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    /** Von jemand anderem empfangen: nur ansehen, Fotos nicht exportierbar. */
    val isReceived: Boolean = false,
    /** Name des Absenders (kann leer sein). */
    val receivedFrom: String? = null,
    val receivedAt: Long? = null,
)

data class Stop(
    val id: String,
    val tripId: String,
    /** Pins können zu einem Tagebucheintrag gehören; alleinstehende haben null. */
    val entryId: String?,
    /** Anreise oder Abreise: zählt nicht bei Datum und Kilometern der Gesamtreise. */
    val isArrivalDeparture: Boolean = false,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val sortIndex: Int,
    val visitedAt: Long?,
    val source: StopSource,
    val distanceFromPreviousMeters: Double?,
    val distanceSource: DistanceSource?,
    val distanceUpdatedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Ein Reiseabschnitt geht von einem Pin zum nächsten. Nur Abschnitte haben ein
 * Verkehrsmittel – niemals einzelne Pins.
 */
data class SegmentMode(
    /** Eindeutiger Schlüssel aus Start- und Ziel-Pin. */
    val id: String,
    val tripId: String,
    val fromStopId: String,
    val toStopId: String,
    val mode: TransportMode,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Ein Tagebucheintrag gehört zu einer Reise. Der Stopp ist optional:
 * Wird ein Pin gelöscht, bleibt der Eintrag ohne Ort in der Timeline erhalten.
 */
data class Entry(
    val id: String,
    val tripId: String,
    val stopId: String?,
    /** Editierbare Zeit des Eintrags. null = wird aus dem ersten Foto übernommen. */
    val entryAt: Long?,
    val title: String,
    val body: String,
    val sortIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /** true = Zeit kam aus dem ersten Foto und wurde noch nicht angefasst. */
    val timeFromPhoto: Boolean = false,
    /** true = Ort kam aus dem ersten Foto und wurde noch nicht angefasst. */
    val placeFromPhoto: Boolean = false,
)

data class Photo(
    val id: String,
    val tripId: String,
    val stopId: String?,
    /** Fotos eines Tagebucheintrags; normale Importe bleiben ohne Eintrag. */
    val entryId: String?,
    /** Dateiname relativ zum Foto-Ordner der App. */
    val fileName: String,
    val mimeType: String,
    val capturedAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val caption: String,
    val sortIndex: Int,
    val createdAt: Long,
)
