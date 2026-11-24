package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Zustand für die Kartenansicht einer Reise. */
class TripMapViewModel(
    private val repository: BreadcrumbzRepository,
    private val tripId: String,
) : ViewModel() {

    val trip = repository.observeTrips()
        .map { trips -> trips.firstOrNull { it.id == tripId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stops = repository.observeStops(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Live-Luftlinie in km. Straßen-km über OSRM kommen in einem späteren Schritt. */
    val distanceKm = stops.map { list ->
        val meters = (1 until list.size).sumOf { index ->
            haversineMeters(
                list[index - 1].latitude,
                list[index - 1].longitude,
                list[index].latitude,
                list[index].longitude,
            )
        }
        meters / 1_000.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0.0)

    fun addStop(latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val number = stops.value.size + 1
            repository.addStop(tripId, "Wegpunkt $number", latitude, longitude)
        }
    }

    fun moveStop(stopId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch {
            val stop = repository.observeStops(tripId).first().firstOrNull { it.id == stopId }
                ?: return@launch
            repository.updateStop(stop.copy(latitude = latitude, longitude = longitude))
        }
    }

    fun deleteStop(stopId: String) {
        viewModelScope.launch {
            repository.deleteStop(stopId)
        }
    }
}

private fun haversineMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val earthRadius = 6_371_000.0
    val deltaLatitude = Math.toRadians(latitude2 - latitude1)
    val deltaLongitude = Math.toRadians(longitude2 - longitude1)
    val a = sin(deltaLatitude / 2) * sin(deltaLatitude / 2) +
        cos(Math.toRadians(latitude1)) * cos(Math.toRadians(latitude2)) *
        sin(deltaLongitude / 2) * sin(deltaLongitude / 2)
    return 2 * earthRadius * atan2(sqrt(a), sqrt(1 - a))
}
