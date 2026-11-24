package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Nur für Meilenstein 1: Erzeugt Testdaten und zeigt an, ob die
 * Datenbank und die Beziehungen zwischen Reisen, Stationen und Einträgen
 * funktionieren. Wird später durch die echte Reise-Ansicht ersetzt.
 */
class DatabaseTestViewModel(
    private val repository: BreadcrumbzRepository,
) : ViewModel() {

    val trips = repository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stops = trips.map { it.firstOrNull()?.id }
        .flatMapLatest { tripId ->
            if (tripId == null) flowOf<List<Stop>>(emptyList())
            else repository.observeStops(tripId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val entries = stops.map { it.firstOrNull()?.id }
        .flatMapLatest { stopId ->
            if (stopId == null) flowOf<List<Entry>>(emptyList())
            else repository.observeEntries(stopId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createSample() {
        viewModelScope.launch {
            val trip = repository.createTrip(
                title = "Probe-Reise",
                description = "Testdaten aus Meilenstein 1",
            )
            val stop = repository.addStop(
                tripId = trip.id,
                title = "Erster Wegpunkt",
                latitude = 48.1374,
                longitude = 11.5755,
                visitedAt = System.currentTimeMillis(),
                source = StopSource.MANUAL,
            )
            repository.addEntry(
                stopId = stop.id,
                title = "Notiz",
                body = "Datenbank und Beziehungen funktionieren.",
            )
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            trips.value.forEach { repository.deleteTrip(it.id) }
        }
    }
}
