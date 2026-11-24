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
    val createdAt: Long,
    val updatedAt: Long,
)

data class Stop(
    val id: String,
    val tripId: String,
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
 * Ein Tagebucheintrag gehört zu einer Reise. Der Stopp ist optional:
 * Wird ein Pin gelöscht, bleibt der Eintrag ohne Ort in der Timeline erhalten.
 */
data class Entry(
    val id: String,
    val tripId: String,
    val stopId: String?,
    val title: String,
    val body: String,
    val sortIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
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
