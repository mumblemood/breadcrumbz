package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository

class BreadcrumbzViewModelFactory(
    private val repository: BreadcrumbzRepository,
    private val tripId: String? = null,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        when {
            modelClass.isAssignableFrom(TripsViewModel::class.java) ->
                return TripsViewModel(repository) as T

            modelClass.isAssignableFrom(TripMapViewModel::class.java) && tripId != null ->
                return TripMapViewModel(repository, tripId) as T
        }
        throw IllegalArgumentException("Unbekanntes ViewModel: $modelClass")
    }
}
