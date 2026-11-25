package de.breadcrumbz.app.domain.model

/**
 * Ein selbst gesetzter Punkt auf der Reiselinie.
 *
 * Der Punkt liegt im Abschnitt, der beim Pin [afterStopId] endet. Dort wird er
 * in die Online-Route eingefügt. Er ist kein Pin und erscheint deshalb nicht
 * in der Zeitleiste.
 */
data class RouteWaypoint(
    val id: String,
    val tripId: String,
    val afterStopId: String?,
    val latitude: Double,
    val longitude: Double,
    val sortIndex: Int,
    val createdAt: Long,
)
