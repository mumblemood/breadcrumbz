package de.breadcrumbz.app.data.repository

import de.breadcrumbz.app.data.db.EntryDao
import de.breadcrumbz.app.data.db.PhotoDao
import de.breadcrumbz.app.data.db.SegmentModeDao
import de.breadcrumbz.app.data.db.WaypointDao
import de.breadcrumbz.app.data.db.StopDao
import de.breadcrumbz.app.data.db.TripDao
import de.breadcrumbz.app.data.model.toDomain
import de.breadcrumbz.app.data.model.toEntity
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.OverlayMode
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.RouteWaypoint
import de.breadcrumbz.app.domain.model.SegmentMode
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomBreadcrumbzRepository(
    private val tripDao: TripDao,
    private val stopDao: StopDao,
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
    private val waypointDao: WaypointDao,
    private val segmentModeDao: SegmentModeDao,
    private val photoStore: InternalPhotoStore,
) : BreadcrumbzRepository {

    override fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createTrip(
        title: String,
        description: String,
        startDate: Long?,
        endDate: Long?,
        defaultTransportMode: TransportMode,
        routeMode: RouteMode,
        titleIsAuto: Boolean,
    ): Trip {
        val now = System.currentTimeMillis()
        val trip = Trip(
            id = UUID.randomUUID().toString(),
            title = title.trim(),
            description = description.trim(),
            startDate = startDate,
            endDate = endDate,
            coverPhotoId = null,
            overlayMode = OverlayMode.STANDARD,
            defaultTransportMode = defaultTransportMode,
            routeMode = routeMode,
            titleIsAuto = titleIsAuto,
            createdAt = now,
            updatedAt = now,
        )
        tripDao.upsert(trip.toEntity())
        return trip
    }

    override suspend fun updateTrip(trip: Trip) {
        // Ein REPLACE würde die Reise löschen und neu einfügen – wegen der
        // CASCADE-Fremdschlüssel würden dabei alle Einträge, Pins und Fotos
        // verschwinden. @Update ändert nur die Reise selbst.
        tripDao.update(trip.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteTrip(id: String) {
        // Fotos einer empfangenen Reise gehören nur zu ihr: Dateien mit aufräumen.
        val receivedFiles = if (tripDao.isReceived(id) == true) {
            photoDao.fileNamesForTrip(id)
        } else {
            emptyList()
        }
        tripDao.deleteById(id)
        receivedFiles.forEach { photoStore.deleteReceived(it) }
    }

    override suspend fun isReceivedTrip(tripId: String): Boolean =
        tripDao.isReceived(tripId) == true

    override fun observeStops(tripId: String): Flow<List<Stop>> =
        stopDao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllStops(): Flow<List<Stop>> =
        stopDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun addStop(
        tripId: String,
        title: String,
        latitude: Double,
        longitude: Double,
        visitedAt: Long?,
        source: StopSource,
    ): Stop {
        val now = System.currentTimeMillis()
        val stop = Stop(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            entryId = null,
            title = title.trim(),
            latitude = latitude,
            longitude = longitude,
            sortIndex = stopDao.nextSortIndex(tripId),
            visitedAt = visitedAt,
            source = source,
            distanceFromPreviousMeters = null,
            distanceSource = null,
            distanceUpdatedAt = null,
            createdAt = now,
            updatedAt = now,
        )
        stopDao.insert(stop.toEntity())
        return stop
    }

    override suspend fun updateStop(stop: Stop) {
        stopDao.update(stop.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteStop(id: String) = stopDao.deleteById(id)

    override fun observeWaypoints(tripId: String): Flow<List<RouteWaypoint>> =
        waypointDao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addWaypoint(
        tripId: String,
        afterStopId: String?,
        latitude: Double,
        longitude: Double,
    ): RouteWaypoint {
        val waypoint = RouteWaypoint(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            afterStopId = afterStopId,
            latitude = latitude,
            longitude = longitude,
            sortIndex = waypointDao.nextSortIndex(tripId),
            createdAt = System.currentTimeMillis(),
        )
        waypointDao.upsert(waypoint.toEntity())
        return waypoint
    }

    override suspend fun updateWaypoint(waypoint: RouteWaypoint) =
        waypointDao.upsert(waypoint.toEntity())

    override suspend fun deleteWaypoint(id: String) = waypointDao.deleteById(id)

    override fun observeSegmentModes(tripId: String): Flow<List<SegmentMode>> =
        segmentModeDao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun upsertSegmentMode(mode: SegmentMode) {
        segmentModeDao.upsert(mode.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteSegmentMode(id: String) = segmentModeDao.deleteById(id)

    override fun observeEntries(stopId: String): Flow<List<Entry>> =
        entryDao.observeForStop(stopId).map { rows -> rows.map { it.toDomain() } }

    override fun observeEntriesForTrip(tripId: String): Flow<List<Entry>> =
        entryDao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllEntries(): Flow<List<Entry>> =
        entryDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun addEntry(
        tripId: String,
        stopId: String?,
        title: String,
        body: String,
    ): Entry {
        val now = System.currentTimeMillis()
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            tripId = tripId,
            stopId = stopId,
            entryAt = null,
            title = title.trim(),
            body = body.trim(),
            sortIndex = entryDao.nextSortIndex(tripId),
            createdAt = now,
            updatedAt = now,
        )
        entryDao.upsert(entry.toEntity())
        return entry
    }

    override suspend fun updateEntry(entry: Entry) {
        entryDao.upsert(entry.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteEntry(id: String) = entryDao.deleteById(id)

    override fun observePhotos(tripId: String): Flow<List<Photo>> =
        photoDao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

    override fun observeAllPhotos(): Flow<List<Photo>> =
        photoDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun setCoverPhoto(tripId: String, photoId: String) {
        val trip = tripDao.observeById(tripId).firstOrNull()?.toDomain() ?: return
        updateTrip(trip.copy(coverPhotoId = photoId))
    }

    override suspend fun clearCoverPhoto(tripId: String) {
        val trip = tripDao.observeById(tripId).firstOrNull()?.toDomain() ?: return
        updateTrip(trip.copy(coverPhotoId = null))
    }

    override suspend fun insertPhoto(photo: Photo) = photoDao.upsert(photo.toEntity())

    override suspend fun updatePhoto(photo: Photo) = photoDao.upsert(photo.toEntity())

    override suspend fun deletePhoto(id: String) {
        val photo = photoDao.findById(id)?.toDomain()
        photoDao.deleteById(id)
        if (photo != null) {
            photoStore.delete(photo.fileName)
        }
    }
}
