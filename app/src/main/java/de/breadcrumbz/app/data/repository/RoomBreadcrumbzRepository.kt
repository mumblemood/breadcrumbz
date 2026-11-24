package de.breadcrumbz.app.data.repository

import de.breadcrumbz.app.data.db.EntryDao
import de.breadcrumbz.app.data.db.PhotoDao
import de.breadcrumbz.app.data.db.StopDao
import de.breadcrumbz.app.data.db.TripDao
import de.breadcrumbz.app.data.model.toDomain
import de.breadcrumbz.app.data.model.toEntity
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.OverlayMode
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomBreadcrumbzRepository(
    private val tripDao: TripDao,
    private val stopDao: StopDao,
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
) : BreadcrumbzRepository {

    override fun observeTrips(): Flow<List<Trip>> =
        tripDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun createTrip(
        title: String,
        description: String,
        startDate: Long?,
        endDate: Long?,
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
            createdAt = now,
            updatedAt = now,
        )
        tripDao.upsert(trip.toEntity())
        return trip
    }

    override suspend fun updateTrip(trip: Trip) {
        tripDao.upsert(trip.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteTrip(id: String) = tripDao.deleteById(id)

    override fun observeStops(tripId: String): Flow<List<Stop>> =
        stopDao.observeForTrip(tripId).map { rows -> rows.map { it.toDomain() } }

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
        stopDao.upsert(stop.toEntity())
        return stop
    }

    override suspend fun updateStop(stop: Stop) {
        stopDao.upsert(stop.copy(updatedAt = System.currentTimeMillis()).toEntity())
    }

    override suspend fun deleteStop(id: String) = stopDao.deleteById(id)

    override fun observeEntries(stopId: String): Flow<List<Entry>> =
        entryDao.observeForStop(stopId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun addEntry(stopId: String, title: String, body: String): Entry {
        val now = System.currentTimeMillis()
        val entry = Entry(
            id = UUID.randomUUID().toString(),
            stopId = stopId,
            title = title.trim(),
            body = body.trim(),
            sortIndex = entryDao.nextSortIndex(stopId),
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

    override suspend fun insertPhoto(photo: Photo) = photoDao.upsert(photo.toEntity())

    override suspend fun deletePhoto(id: String) = photoDao.deleteById(id)
}
