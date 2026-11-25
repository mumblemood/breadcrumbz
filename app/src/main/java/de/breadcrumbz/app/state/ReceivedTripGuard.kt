package de.breadcrumbz.app.state

import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.RouteWaypoint
import de.breadcrumbz.app.domain.model.SegmentMode
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import java.util.UUID

/**
 * Schreibschutz für eine empfangene Reise („nur ansehen“).
 *
 * Die Kartenansicht einer Reise bekommt das Repository nur durch diesen
 * Filter. Ist die Reise empfangen, wird jede Änderung still ignoriert – egal
 * über welchen Knopf, welche Geste oder welche automatische Aufräumarbeit sie
 * kommt. Lesen funktioniert ganz normal. Eigene Reisen sind nicht betroffen.
 */
class ReceivedTripGuard(
    private val delegate: BreadcrumbzRepository,
    private val tripId: String,
) : BreadcrumbzRepository by delegate {

    /** Eine Reise wird nie nachträglich zur empfangenen: einmal prüfen reicht. */
    @Volatile
    private var locked: Boolean? = null

    private suspend fun isLocked(): Boolean =
        locked ?: delegate.isReceivedTrip(tripId).also { locked = it }

    override suspend fun updateTrip(trip: Trip) {
        if (trip.isReceived || isLocked()) return
        delegate.updateTrip(trip)
    }

    override suspend fun addStop(
        tripId: String,
        title: String,
        latitude: Double,
        longitude: Double,
        visitedAt: Long?,
        source: StopSource,
    ): Stop {
        if (isLocked()) {
            // Nicht gespeicherter Platzhalter – alle Folgeänderungen laufen ins Leere.
            val now = System.currentTimeMillis()
            return Stop(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                entryId = null,
                title = title,
                latitude = latitude,
                longitude = longitude,
                sortIndex = 0,
                visitedAt = visitedAt,
                source = source,
                distanceFromPreviousMeters = null,
                distanceSource = null,
                distanceUpdatedAt = null,
                createdAt = now,
                updatedAt = now,
            )
        }
        return delegate.addStop(tripId, title, latitude, longitude, visitedAt, source)
    }

    override suspend fun updateStop(stop: Stop) {
        if (isLocked()) return
        delegate.updateStop(stop)
    }

    override suspend fun deleteStop(id: String) {
        if (isLocked()) return
        delegate.deleteStop(id)
    }

    override suspend fun addWaypoint(
        tripId: String,
        afterStopId: String?,
        latitude: Double,
        longitude: Double,
    ): RouteWaypoint {
        if (isLocked()) {
            return RouteWaypoint(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                afterStopId = afterStopId,
                latitude = latitude,
                longitude = longitude,
                sortIndex = 0,
                createdAt = System.currentTimeMillis(),
            )
        }
        return delegate.addWaypoint(tripId, afterStopId, latitude, longitude)
    }

    override suspend fun updateWaypoint(waypoint: RouteWaypoint) {
        if (isLocked()) return
        delegate.updateWaypoint(waypoint)
    }

    override suspend fun deleteWaypoint(id: String) {
        if (isLocked()) return
        delegate.deleteWaypoint(id)
    }

    override suspend fun upsertSegmentMode(mode: SegmentMode) {
        if (isLocked()) return
        delegate.upsertSegmentMode(mode)
    }

    override suspend fun deleteSegmentMode(id: String) {
        if (isLocked()) return
        delegate.deleteSegmentMode(id)
    }

    override suspend fun addEntry(
        tripId: String,
        stopId: String?,
        title: String,
        body: String,
    ): Entry {
        if (isLocked()) {
            val now = System.currentTimeMillis()
            return Entry(
                id = UUID.randomUUID().toString(),
                tripId = tripId,
                stopId = stopId,
                entryAt = null,
                title = title,
                body = body,
                sortIndex = 0,
                createdAt = now,
                updatedAt = now,
            )
        }
        return delegate.addEntry(tripId, stopId, title, body)
    }

    override suspend fun updateEntry(entry: Entry) {
        if (isLocked()) return
        delegate.updateEntry(entry)
    }

    override suspend fun deleteEntry(id: String) {
        if (isLocked()) return
        delegate.deleteEntry(id)
    }

    override suspend fun setCoverPhoto(tripId: String, photoId: String) {
        if (isLocked()) return
        delegate.setCoverPhoto(tripId, photoId)
    }

    override suspend fun clearCoverPhoto(tripId: String) {
        if (isLocked()) return
        delegate.clearCoverPhoto(tripId)
    }

    override suspend fun insertPhoto(photo: Photo) {
        if (isLocked()) return
        delegate.insertPhoto(photo)
    }

    override suspend fun updatePhoto(photo: Photo) {
        if (isLocked()) return
        delegate.updatePhoto(photo)
    }

    override suspend fun deletePhoto(id: String) {
        if (isLocked()) return
        delegate.deletePhoto(id)
    }

    override suspend fun deleteTrip(id: String) {
        // Löschen bleibt immer möglich – auch eine empfangene Reise darf weg.
        delegate.deleteTrip(id)
    }
}
