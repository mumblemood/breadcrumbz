package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository

class BreadcrumbzViewModelFactory(
    private val repository: BreadcrumbzRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TripsViewModel::class.java)) {
            return TripsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unbekanntes ViewModel: $modelClass")
    }
}
