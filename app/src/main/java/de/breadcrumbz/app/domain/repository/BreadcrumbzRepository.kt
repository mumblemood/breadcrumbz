package de.breadcrumbz.app.domain.repository

import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.Trip
import kotlinx.coroutines.flow.Flow

/**
 * Alle Datenoperationen der App laufen über dieses Interface.
 * Die Oberfläche kennt deshalb keine Datenbank – später kann hier z. B.
 * eine Cast-Steuerung dieselben Methoden benutzen.
 */
interface BreadcrumbzRepository {
    fun observeTrips(): Flow<List<Trip>>

    suspend fun createTrip(
        title: String,
        description: String = "",
        startDate: Long? = null,
        endDate: Long? = null,
        defaultTransportMode: TransportMode = TransportMode.CAR,
        routeMode: RouteMode = RouteMode.AUTO,
        titleIsAuto: Boolean = false,
    ): Trip

    suspend fun updateTrip(trip: Trip)
    suspend fun deleteTrip(id: String)

    fun observeStops(tripId: String): Flow<List<Stop>>
    /** Alle Pins aller Reisen – für automatisch abgeleitete Reise-Infos. */
    fun observeAllStops(): Flow<List<Stop>>

    suspend fun addStop(
        tripId: String,
        title: String,
        latitude: Double,
        longitude: Double,
        visitedAt: Long? = null,
        source: StopSource = StopSource.MANUAL,
    ): Stop

    suspend fun updateStop(stop: Stop)
    suspend fun deleteStop(id: String)

    fun observeEntries(stopId: String): Flow<List<Entry>>
    fun observeEntriesForTrip(tripId: String): Flow<List<Entry>>
    /** Alle Einträge aller Reisen – für automatisch abgeleitete Reise-Infos. */
    fun observeAllEntries(): Flow<List<Entry>>

    suspend fun addEntry(tripId: String, stopId: String?, title: String, body: String): Entry
    suspend fun updateEntry(entry: Entry)
    suspend fun deleteEntry(id: String)

    fun observePhotos(tripId: String): Flow<List<Photo>>
    fun observeAllPhotos(): Flow<List<Photo>>
    suspend fun setCoverPhoto(tripId: String, photoId: String)
    suspend fun insertPhoto(photo: Photo)
    suspend fun updatePhoto(photo: Photo)
    suspend fun deletePhoto(id: String)
}
