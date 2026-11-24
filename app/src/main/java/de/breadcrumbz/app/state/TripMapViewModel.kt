package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import de.breadcrumbz.app.domain.util.clusterPhotos
import de.breadcrumbz.app.domain.util.haversineMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Zustand für die Kartenansicht einer Reise. */
class TripMapViewModel(
    private val repository: BreadcrumbzRepository,
    private val photoStore: InternalPhotoStore,
    private val tripId: String,
) : ViewModel() {

    val trip = repository.observeTrips()
        .map { trips -> trips.firstOrNull { it.id == tripId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stops = repository.observeStops(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos = repository.observePhotos(tripId)
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

    private val _pendingPhotoId = MutableStateFlow<String?>(null)
    val pendingPhotoId: StateFlow<String?> = _pendingPhotoId.asStateFlow()

    fun photoPath(fileName: String): String = photoStore.fileFor(fileName).absolutePath

    fun beginPlacement(photoId: String) {
        _pendingPhotoId.value = photoId
    }

    fun cancelPlacement() {
        _pendingPhotoId.value = null
    }

    /** Langes Drücken: entweder neuer Wegpunkt oder gewartetes Foto platzieren. */
    fun onMapLongPress(latitude: Double, longitude: Double) {
        val pending = _pendingPhotoId.value
        if (pending != null) {
            placePhoto(pending, latitude, longitude)
        } else {
            addStop(latitude, longitude)
        }
    }

    fun importPhotos(items: List<ImportedPhoto>) {
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val startIndex = repository.observePhotos(tripId).first().size
            val now = System.currentTimeMillis()

            items.forEachIndexed { index, item ->
                val metadata = PhotoImporter.read(item.bytes)
                val fileName = photoStore.save(item.bytes, item.mimeType)
                repository.insertPhoto(
                    de.breadcrumbz.app.domain.model.Photo(
                        id = UUID.randomUUID().toString(),
                        tripId = tripId,
                        stopId = null,
                        fileName = fileName,
                        mimeType = item.mimeType,
                        capturedAt = metadata.capturedAt,
                        latitude = metadata.latitude,
                        longitude = metadata.longitude,
                        caption = "",
                        sortIndex = startIndex + index,
                        createdAt = now + index,
                    ),
                )
            }
            clusterUnassigned()
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePhoto(photoId)
        }
    }

    private fun addStop(latitude: Double, longitude: Double) {
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

    private fun placePhoto(photoId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = repository.observePhotos(tripId).first().firstOrNull { it.id == photoId }
                ?: return@launch
            val oldStopId = photo.stopId
            repository.updatePhoto(
                photo.copy(latitude = latitude, longitude = longitude, stopId = null),
            )
            _pendingPhotoId.value = null

            if (oldStopId != null) {
                val stillUsed = repository.observePhotos(tripId).first().any { it.stopId == oldStopId }
                if (!stillUsed) {
                    repository.deleteStop(oldStopId)
                }
            }
            clusterUnassigned()
        }
    }

    /** Fotos mit Ort, aber ohne Stopp werden zu Gruppen zusammengefasst. */
    private suspend fun clusterUnassigned() {
        val all = repository.observePhotos(tripId).first()
        val loose = all.filter {
            it.stopId == null && it.latitude != null && it.longitude != null
        }

        withContext(Dispatchers.Default) {
            clusterPhotos(loose).forEach { cluster ->
                val latitude = cluster.mapNotNull { it.latitude }.average()
                val longitude = cluster.mapNotNull { it.longitude }.average()
                val capturedAt = cluster.first().capturedAt
                val formatted = capturedAt?.let {
                    SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(it))
                } ?: "Ohne Zeit"
                val title = if (cluster.size > 1) "Fotos · $formatted" else "Foto · $formatted"

                val stop = repository.addStop(
                    tripId = tripId,
                    title = title,
                    latitude = latitude,
                    longitude = longitude,
                    visitedAt = capturedAt,
                    source = StopSource.PHOTO,
                )
                cluster.forEach { photo -> repository.updatePhoto(photo.copy(stopId = stop.id)) }
            }
        }
    }
}
