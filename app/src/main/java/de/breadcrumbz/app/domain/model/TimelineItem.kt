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
)
