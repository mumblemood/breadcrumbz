package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TripsViewModel(
    private val repository: BreadcrumbzRepository,
    private val photoStore: InternalPhotoStore,
) : ViewModel() {

    val trips = repository.observeTrips()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photosByTrip = combine(trips, repository.observeAllPhotos()) { _, photos ->
        photos.groupBy { it.tripId }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun photoPath(fileName: String): String = photoStore.fileFor(fileName).absolutePath

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
