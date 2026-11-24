package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.place.PlaceNameResolver
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository

class BreadcrumbzViewModelFactory(
    private val repository: BreadcrumbzRepository,
    private val photoStore: InternalPhotoStore,
    private val placeNameResolver: PlaceNameResolver,
    private val tripId: String? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        when {
            modelClass.isAssignableFrom(TripsViewModel::class.java) ->
                return TripsViewModel(repository) as T

            modelClass.isAssignableFrom(TripMapViewModel::class.java) && tripId != null ->
                return TripMapViewModel(repository, photoStore, placeNameResolver, tripId) as T
        }
        throw IllegalArgumentException("Unbekanntes ViewModel: $modelClass")
    }
}
