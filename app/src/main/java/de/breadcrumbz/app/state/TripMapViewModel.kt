package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.data.place.PlaceNameResolver
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import de.breadcrumbz.app.domain.util.PhotoGroupRule
import de.breadcrumbz.app.domain.util.clusterPhotos
import de.breadcrumbz.app.domain.util.haversineMeters
import de.breadcrumbz.app.domain.util.photoMatchesStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
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
    private val placeNameResolver: PlaceNameResolver,
    private val tripId: String,
) : ViewModel() {

    val trip = repository.observeTrips()
        .map { trips -> trips.firstOrNull { it.id == tripId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stops = repository.observeStops(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos = repository.observePhotos(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedStopId = MutableStateFlow<String?>(null)
    val selectedStopId: StateFlow<String?> = _selectedStopId.asStateFlow()

    /** Einträge des gerade ausgewählten Stopps. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val entries = _selectedStopId.flatMapLatest { stopId ->
        if (stopId != null) repository.observeEntries(stopId) else flowOf(emptyList())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Alle Einträge der Reise – für die Zettel auf der Karte. */
    val allEntries = repository.observeEntriesForTrip(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Stationen, Einträge und Fotos gemeinsam nach Zeit sortiert. */
    val timeline = combine(
        stops,
        repository.observeEntriesForTrip(tripId),
        photos,
    ) { allStops, allEntries, allPhotos ->
        buildList {
            // Pins in Einträgen erscheinen nur im Eintrag, nicht noch einmal allein.
            allStops.filter { it.entryId == null }.forEach { stop ->
                add(
                    TimelineItem(
                        id = "stop-${stop.id}",
                        time = stop.visitedAt ?: stop.createdAt,
                        kind = TimelineKind.STOP,
                        title = stop.title,
                        subtitle = "Station",
                        stopId = stop.id,
                        entryId = null,
                        photoId = null,
                        hasLocation = true,
                    ),
                )
            }
            allEntries.forEach { entry ->
                add(
                    TimelineItem(
                        id = "entry-${entry.id}",
                        time = entry.createdAt,
                        kind = TimelineKind.ENTRY,
                        title = entry.title.ifBlank { "Tagebucheintrag" },
                        subtitle = if (entry.stopId == null) "Ohne Ort" else entry.body,
                        stopId = entry.stopId,
                        entryId = entry.id,
                        photoId = null,
                        hasLocation = entry.stopId != null,
                        photoCount = allPhotos.count { it.entryId == entry.id },
                        pinCount = allStops.count { it.entryId == entry.id },
                    ),
                )
            }
            // Fotos mit Eintrag erscheinen aufgeklappt im Eintrag, nicht doppelt.
            allPhotos.filter { it.entryId == null }.forEach { photo ->
                add(
                    TimelineItem(
                        id = "photo-${photo.id}",
                        time = photo.capturedAt ?: photo.createdAt,
                        kind = TimelineKind.PHOTO,
                        title = photo.caption.ifBlank { "Foto" },
                        subtitle = "Foto",
                        stopId = photo.stopId,
                        entryId = null,
                        photoId = photo.id,
                        hasLocation = photo.latitude != null && photo.longitude != null,
                    ),
                )
            }
        }.sortedBy { it.time }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    private val _pendingEntryId = MutableStateFlow<String?>(null)
    val pendingEntryId: StateFlow<String?> = _pendingEntryId.asStateFlow()

    fun photoPath(fileName: String): String = photoStore.fileFor(fileName).absolutePath

    fun selectStop(stopId: String?) {
        _selectedStopId.value = stopId
    }

    fun saveEntry(stopId: String?, entryId: String?, title: String, body: String) {
        if (title.isBlank() && body.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (entryId != null) {
                val entry = repository.observeEntriesForTrip(tripId)
                    .first().firstOrNull { it.id == entryId }
                if (entry != null) {
                    repository.updateEntry(
                        entry.copy(title = title.trim(), body = body.trim()),
                    )
                }
            } else {
                repository.addEntry(tripId, stopId, title.trim(), body.trim())
            }
        }
    }

    fun deleteEntry(entryId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteEntry(entryId)
        }
    }

    fun beginPlacement(photoId: String) {
        _pendingEntryId.value = null
        _pendingPhotoId.value = photoId
    }

    fun beginEntryPlacement(entryId: String) {
        _pendingPhotoId.value = null
        _pendingEntryId.value = entryId
    }

    fun cancelPlacement() {
        _pendingPhotoId.value = null
        _pendingEntryId.value = null
    }

    /** Langes Drücken: Foto platzieren, Eintrag verorten oder neuen Punkt setzen. */
    fun onMapLongPress(latitude: Double, longitude: Double) {
        when {
            _pendingEntryId.value != null -> placeEntry(_pendingEntryId.value!!, latitude, longitude)
            _pendingPhotoId.value != null -> placePhoto(_pendingPhotoId.value!!, latitude, longitude)
            else -> addStop(latitude, longitude)
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
                    Photo(
                        id = UUID.randomUUID().toString(),
                        tripId = tripId,
                        stopId = null,
                        entryId = null,
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

    /**
     * Neuer Eintrag aus dem „+"-Dialog: Bilder werden direkt importiert und
     * gemeinsam mit dem Text gespeichert. Der Ort wird automatisch aus GPS und
     * Aufnahmezeit der Bilder bzw. aus der Zeit vorhandener Stationen gewählt.
     */
    fun createEntryFromDialog(
        title: String,
        body: String,
        time: Long,
        images: List<ImportedPhoto>,
        fallbackLatitude: Double? = null,
        fallbackLongitude: Double? = null,
    ) {
        if (title.isBlank() && body.isBlank() && images.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val allStops = repository.observeStops(tripId).first()
            val startIndex = repository.observePhotos(tripId).first().size
            val now = System.currentTimeMillis()
            val createdPhotos = mutableListOf<Photo>()

            images.forEachIndexed { index, item ->
                val metadata = PhotoImporter.read(item.bytes)
                val fileName = photoStore.save(item.bytes, item.mimeType)
                val photo = Photo(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    stopId = null,
                    entryId = null,
                    fileName = fileName,
                    mimeType = item.mimeType,
                    capturedAt = metadata.capturedAt ?: time,
                    latitude = metadata.latitude ?: fallbackLatitude,
                    longitude = metadata.longitude ?: fallbackLongitude,
                    caption = "",
                    sortIndex = startIndex + index,
                    createdAt = now + index,
                )
                repository.insertPhoto(photo)
                createdPhotos.add(photo)
            }

            var resolvedStopId = createdPhotos.firstOrNull {
                it.latitude != null && it.longitude != null
            }?.let { photo ->
                allStops.firstOrNull { stop ->
                    photoMatchesStop(
                        photo.latitude,
                        photo.longitude,
                        photo.capturedAt,
                        stop.latitude,
                        stop.longitude,
                        stop.visitedAt,
                    )
                }?.id
            } ?: allStops
                .filter { it.visitedAt != null }
                .minByOrNull { kotlin.math.abs((it.visitedAt ?: time) - time) }
                ?.takeIf { stop ->
                    kotlin.math.abs((stop.visitedAt ?: time) - time) <=
                        PhotoGroupRule.TIME_MS
                }?.id

            // Noch kein passender Pin, aber GPS in den Bildern: Foto-Stopp erzeugen.
            if (resolvedStopId == null && createdPhotos.any {
                    it.latitude != null && it.longitude != null
                }
            ) {
                clusterUnassigned()
                val ids = createdPhotos.map { it.id }.toSet()
                resolvedStopId = repository.observePhotos(tripId).first()
                    .firstOrNull { it.id in ids && it.stopId != null }?.stopId
            }

            // Ohne EXIF-GPS bekommt das Bild den aktuellen Kartenort als festen Ort.
            if (resolvedStopId == null &&
                fallbackLatitude != null && fallbackLongitude != null
            ) {
                val name = placeNameResolver.resolve(fallbackLatitude, fallbackLongitude)
                    ?: "Wegpunkt ${allStops.size + 1}"
                resolvedStopId = repository.addStop(
                    tripId = tripId,
                    title = name,
                    latitude = fallbackLatitude,
                    longitude = fallbackLongitude,
                    visitedAt = time,
                    source = StopSource.MANUAL,
                ).id
            }

            val entry = repository.addEntry(tripId, resolvedStopId, title.trim(), body.trim())
            repository.updateEntry(entry.copy(createdAt = time))
            val targetStop = resolvedStopId?.let { id ->
                repository.observeStops(tripId).first().firstOrNull { it.id == id }
            }
            createdPhotos.forEach { photo ->
                repository.updatePhoto(
                    photo.copy(
                        entryId = entry.id,
                        stopId = resolvedStopId ?: photo.stopId,
                        capturedAt = photo.capturedAt ?: time,
                        latitude = photo.latitude ?: targetStop?.latitude,
                        longitude = photo.longitude ?: targetStop?.longitude,
                    ),
                )
            }
        }
    }

    /**
     * Ordnet ein Element per Drag & Drop einem Eintrag zu oder löst es wieder.
     * Schlüssel: "photo-<id>" oder "stop-<id>". Hat der Eintrag einen Ort, zieht
     * ein zugeordnetes Bild dorthin.
     */
    fun assignElementToEntry(elementKey: String, entryId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val stopsNow = repository.observeStops(tripId).first()
            val entry = entryId?.let { id ->
                repository.observeEntriesForTrip(tripId).first().firstOrNull { it.id == id }
            }
            val targetStop = entry?.stopId?.let { id -> stopsNow.firstOrNull { it.id == id } }

            when {
                elementKey.startsWith("photo-") -> {
                    val id = elementKey.removePrefix("photo-")
                    val photo = repository.observePhotos(tripId).first()
                        .firstOrNull { it.id == id } ?: return@launch
                    repository.updatePhoto(
                        photo.copy(
                            entryId = entryId,
                            stopId = targetStop?.id ?: photo.stopId,
                            latitude = targetStop?.latitude ?: photo.latitude,
                            longitude = targetStop?.longitude ?: photo.longitude,
                        ),
                    )
                }
                elementKey.startsWith("stop-") -> {
                    val id = elementKey.removePrefix("stop-")
                    val stop = stopsNow.firstOrNull { it.id == id } ?: return@launch
                    repository.updateStop(stop.copy(entryId = entryId))
                    if (entryId != null && entry?.stopId == null) {
                        entry?.copy(stopId = stop.id)?.let { repository.updateEntry(it) }
                    }
                    if (entryId != null) {
                        repository.observePhotos(tripId).first()
                            .filter { it.stopId == stop.id }
                            .forEach { photo -> repository.updatePhoto(photo.copy(entryId = entryId)) }
                    }
                }
            }
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deletePhoto(photoId)
        }
    }

    /** Foto-Titel mit maximal zwölf Zeichen. */
    fun updatePhotoCaption(photoId: String, caption: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = repository.observePhotos(tripId).first().firstOrNull { it.id == photoId }
                ?: return@launch
            repository.updatePhoto(photo.copy(caption = caption.take(12)))
        }
    }

    fun setCoverPhoto(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setCoverPhoto(tripId, photoId)
        }
    }

    /** Umschalter zwischen automatischem Linien-Stil und glattem Direkt-Spline. */
    fun setRouteMode(routeMode: RouteMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.observeTrips().first().firstOrNull { it.id == tripId }
                ?: return@launch
            repository.updateTrip(current.copy(routeMode = routeMode))
        }
    }

    /** Verkehrsmittel für einen Abschnitt ändern (Pin = Ankunft an diesem Abschnitt). */
    fun setStopTransportMode(stopId: String, mode: TransportMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val stop = repository.observeStops(tripId).first().firstOrNull { it.id == stopId }
                ?: return@launch
            repository.updateStop(stop.copy(transportMode = mode))
        }
    }

    /**
     * Löscht einen Pin. Einträge und Fotos werden automatisch vom Pin getrennt
     * (Datenbank-Regel ON DELETE SET NULL) und bleiben erhalten.
     */
    fun deleteStop(stopId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteStop(stopId)
        }
    }

    private fun addStop(latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val number = stops.value.size + 1
            val name = placeNameResolver.resolve(latitude, longitude)
            repository.addStop(tripId, name ?: "Wegpunkt $number", latitude, longitude)
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

    /** Gibt einem Eintrag ohne Ort einen neuen Pin. */
    private fun placeEntry(entryId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = repository.observeEntriesForTrip(tripId)
                .first().firstOrNull { it.id == entryId }
                ?: return@launch
            val number = stops.value.size + 1
            val name = placeNameResolver.resolve(latitude, longitude) ?: "Wegpunkt $number"
            val stop = repository.addStop(tripId, name, latitude, longitude)
            repository.updateEntry(entry.copy(stopId = stop.id))
            repository.observePhotos(tripId).first()
                .filter { it.entryId == entryId }
                .forEach { photo ->
                    repository.updatePhoto(
                        photo.copy(
                            stopId = stop.id,
                            latitude = latitude,
                            longitude = longitude,
                        ),
                    )
                }
            _pendingEntryId.value = null
        }
    }

    /**
     * Fotos mit Ort werden gruppiert. Wichtig: neue Fotos werden auch in einen
     * schon vorhandenen Foto-Stopp einsortiert – sonst entstand pro Import ein
     * eigener Stopp. Das war der Gruppierungs-Bug.
     */
    private suspend fun clusterUnassigned() {
        val all = repository.observePhotos(tripId).first()
        val loose = all.filter {
            it.stopId == null && it.latitude != null && it.longitude != null
        }
        if (loose.isEmpty()) return

        val photoStops = repository.observeStops(tripId).first()
            .filter { it.source == StopSource.PHOTO }
        val remaining = mutableListOf<Photo>()

        withContext(Dispatchers.Default) {
            loose.forEach { photo ->
                val target = photoStops.firstOrNull { stop ->
                    photoMatchesStop(
                        photo.latitude,
                        photo.longitude,
                        photo.capturedAt,
                        stop.latitude,
                        stop.longitude,
                        stop.visitedAt,
                    )
                }
                if (target != null) {
                    repository.updatePhoto(photo.copy(stopId = target.id))
                } else {
                    remaining.add(photo)
                }
            }

            clusterPhotos(remaining).forEach { cluster ->
                val latitude = cluster.mapNotNull { it.latitude }.average()
                val longitude = cluster.mapNotNull { it.longitude }.average()
                val capturedAt = cluster.first().capturedAt
                val formatted = capturedAt?.let {
                    SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(it))
                } ?: "Ohne Zeit"
                val place = placeNameResolver.resolve(latitude, longitude)
                val title = if (place != null) "$place · Fotos" else "Fotos · $formatted"

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
