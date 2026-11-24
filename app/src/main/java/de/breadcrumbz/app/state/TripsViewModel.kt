package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripsViewModel(
    private val repository: BreadcrumbzRepository,
) : ViewModel() {

    val trips = repository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun saveTrip(id: String?, title: String, description: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            if (id == null) {
                repository.createTrip(title.trim(), description.trim())
            } else {
                val trip = repository.observeTrips().first().firstOrNull { it.id == id }
                if (trip != null) {
                    repository.updateTrip(
                        trip.copy(title = title.trim(), description = description.trim()),
                    )
                }
            }
        }
    }

    fun deleteTrip(id: String) {
        viewModelScope.launch {
            repository.deleteTrip(id)
        }
    }
}
