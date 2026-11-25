package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import de.breadcrumbz.app.domain.util.chronologicalStops
import de.breadcrumbz.app.domain.util.haversineMeters
import de.breadcrumbz.app.domain.util.tripTimeRange
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Eine Reise plus die Werte, die die App aus ihren Inhalten ableitet. */
data class TripOverview(
    val trip: Trip,
    /** Frühester Zeitpunkt aus Fotos, Pins und Einträgen. */
    val startDate: Long?,
    /** Spätester Zeitpunkt aus Fotos, Pins und Einträgen. */
    val endDate: Long?,
    /** Erster bekannter Ortsname der Reise. */
    val place: String?,
    val coverPhoto: Photo?,
    /** Pins der Reise in Reihenfolge (ohne An-/Abreise) – für die Kartenskizze. */
    val routePoints: List<Pair<Double, Double>> = emptyList(),
    /** Gleiche Luftlinien-Summe wie auf dem Kartenbildschirm. */
    val distanceKm: Double = 0.0,
    /** Alle Pins in Reisereihenfolge (mit An-/Abreise) – für die echte Mini-Karte. */
    val routeStops: List<Stop> = emptyList(),
)

class TripsViewModel(
    private val repository: BreadcrumbzRepository,
    private val photoStore: InternalPhotoStore,
) : ViewModel() {

    /**
     * Zeitraum und Ort kommen aus den Inhalten: Foto-Aufnahmezeit, Pin-Besuch,
     * Eintragszeit und dem ersten Ortsnamen. Manuell gesetzte Werte behalten
     * Vorrang, damit nichts verloren geht.
     */
    val overviews = combine(
        repository.observeTrips(),
        repository.observeAllStops(),
        repository.observeAllEntries(),
        repository.observeAllPhotos(),
    ) { trips: List<Trip>, stops: List<Stop>, entries: List<Entry>, photos: List<Photo> ->
        trips.map { trip ->
            val tripStops = stops.filter { it.tripId == trip.id }
                .sortedBy { it.visitedAt ?: it.createdAt }
            val tripEntries = entries.filter { it.tripId == trip.id }
            val tripPhotos = photos.filter { it.tripId == trip.id }

            // An-/Abreise-Pins und ihre Inhalte zählen nicht zur Gesamtreise.
            val range = tripTimeRange(tripStops, tripEntries, tripPhotos)
            val chronological = chronologicalStops(tripStops, tripEntries, tripPhotos)
            val ordered = chronological.filterNot { it.isArrivalDeparture }
            val meters = (1 until ordered.size).sumOf { index ->
                haversineMeters(
                    ordered[index - 1].latitude, ordered[index - 1].longitude,
                    ordered[index].latitude, ordered[index].longitude,
                )
            }

            TripOverview(
                trip = trip,
                startDate = trip.startDate ?: range.first,
                endDate = trip.endDate ?: range.second,
                // An-/Abreise gehören nicht zur Gesamtansicht der Reise.
                place = tripStops.firstOrNull {
                    !it.isArrivalDeparture && it.title.isNotBlank()
                }?.title,
                coverPhoto = tripPhotos.firstOrNull { it.id == trip.coverPhotoId },
                routePoints = ordered.map { it.latitude to it.longitude },
                distanceKm = meters / 1_000.0,
                routeStops = chronological,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun photoPath(fileName: String): String = photoStore.fileFor(fileName).absolutePath

    /** Verkehrsmittel je Abschnitt einer Reise – für die Linie in der Vorschau. */
    fun segmentModes(tripId: String) = repository.observeSegmentModes(tripId)

    /** Neue Reise direkt anlegen und sofort die Karte öffnen – ohne Dialog. */
    fun createTrip(onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val trip = repository.createTrip(
                title = "neue Reise",
                titleIsAuto = true,
            )
            onCreated(trip.id)
        }
    }

    fun saveTrip(
        id: String?,
        title: String,
        description: String,
        startDate: Long?,
        endDate: Long?,
        transportMode: TransportMode,
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (id == null) {
                repository.createTrip(
                    title = title.trim(),
                    description = description.trim(),
                    startDate = startDate,
                    endDate = endDate,
                    defaultTransportMode = transportMode,
                )
            } else {
                val trip = repository.observeTrips().first().firstOrNull { it.id == id }
                if (trip != null) {
                    repository.updateTrip(
                        trip.copy(
                            title = title.trim(),
                            description = description.trim(),
                            startDate = startDate,
                            endDate = endDate,
                            defaultTransportMode = transportMode,
                            titleIsAuto = title.trim() == trip.title && trip.titleIsAuto,
                        ),
                    )
                }
            }
        }
    }

    fun setCoverPhoto(tripId: String, photoId: String) {
        viewModelScope.launch {
            repository.setCoverPhoto(tripId, photoId)
        }
    }

    fun deleteTrip(id: String) {
        viewModelScope.launch {
            repository.deleteTrip(id)
        }
    }
}
