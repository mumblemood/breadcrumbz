package de.breadcrumbz.app.domain.model

enum class TimelineKind { STOP, ENTRY, PHOTO }

/** Ein Element im gemeinsamen Zeitstrom einer Reise. */
data class TimelineItem(
    val id: String,
    val time: Long,
    val kind: TimelineKind,
    val title: String,
    val subtitle: String,
    val stopId: String?,
    val entryId: String?,
    val photoId: String?,
    val hasLocation: Boolean,
    /** Anzahl Bilder, die an diesem Eintrag hängen. */
    val photoCount: Int = 0,
    /** Anzahl Pins, die an diesem Eintrag hängen. */
    val pinCount: Int = 0,
)
