package de.breadcrumbz.app.state

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.data.place.PlaceNameResolver
import de.breadcrumbz.app.data.settings.SettingsStore
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.OverlayMode
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.RouteWaypoint
import de.breadcrumbz.app.domain.model.SegmentMode
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import de.breadcrumbz.app.domain.util.chronologicalStops
import de.breadcrumbz.app.domain.util.clusterPhotos
import de.breadcrumbz.app.domain.util.haversineMeters
import de.breadcrumbz.app.domain.util.photoMatchesStop
import de.breadcrumbz.app.domain.util.fittingTimeForNewStop
import de.breadcrumbz.app.domain.util.segmentKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import java.util.concurrent.ConcurrentHashMap

private const val TEXT_SAVE_DELAY_MS = 400L

/** Zustand für die Kartenansicht einer Reise. */
class TripMapViewModel(
    private val repository: BreadcrumbzRepository,
    private val photoStore: InternalPhotoStore,
    private val placeNameResolver: PlaceNameResolver,
    private val settingsStore: SettingsStore,
    private val tripId: String,
) : ViewModel() {

    /** Eigener Bereich fürs Speichern von Text – läuft auch nach dem Schließen weiter. */
    private val saveScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val textWriteLock = Mutex()
    private val textSaveJobs = mutableMapOf<String, Job>()
    private val pendingEntryTexts = ConcurrentHashMap<String, Pair<String, String>>()
    private val pendingCaptions = ConcurrentHashMap<String, String>()

    val trip = repository.observeTrips()
        .map { trips -> trips.firstOrNull { it.id == tripId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val stops = repository.observeStops(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photos = repository.observePhotos(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Selbst gesetzte Punkte auf der Reiselinie. */
    val waypoints = repository.observeWaypoints(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Verkehrsmittel pro Abschnitt – getrennt von den Pins gespeichert. */
    val segmentModes: StateFlow<List<SegmentMode>> = repository.observeSegmentModes(tripId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Online-Routing ist Standard, lässt sich aber in den Einstellungen ausstellen. */
    val onlineRouting: StateFlow<Boolean> = settingsStore.onlineRouting

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
        // Alles, was an An-/Abreise-Pins hängt, bleibt auf der Karte sichtbar,
        // gehört aber nicht zur eigentlichen Reise in der Zeitleiste.
        val outerStopIds = allStops.filter { it.isArrivalDeparture }
            .map { it.id }.toSet()
        val outerEntryIds = buildSet {
            allStops.filter { it.isArrivalDeparture }
                .mapNotNull { it.entryId }.forEach(::add)
            allEntries.filter { it.stopId in outerStopIds }
                .map { it.id }.forEach(::add)
        }
        // Die Reihenfolge kommt aus den Foto-Daten: Einträge mit automatisch
        // übernommener Zeit richten sich nach dem frühesten Bild des Eintrags.
        // Manuell gesetzte Zeiten bleiben davon unberührt.
        fun entrySortTime(entry: Entry): Long {
            val fromPhoto = allPhotos.filter { it.entryId == entry.id }
                .mapNotNull { it.capturedAt }.minOrNull()
            return if (entry.timeFromPhoto || entry.entryAt == null) {
                fromPhoto ?: entry.entryAt ?: entry.createdAt
            } else {
                entry.entryAt ?: entry.createdAt
            }
        }
        buildList {
            // Pins in Einträgen erscheinen nur im Eintrag, nicht noch einmal allein.
            allStops.filter { it.entryId == null && it.id !in outerStopIds }.forEach { stop ->
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
            allEntries.filterNot { it.id in outerEntryIds }.forEach { entry ->
                add(
                    TimelineItem(
                        id = "entry-${entry.id}",
                        time = entrySortTime(entry),
                        kind = TimelineKind.ENTRY,
                        // Ein leerer Titel bleibt leer – er ist ein optionales Feld.
                        title = entry.title,
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
            allPhotos.filter { photo ->
                photo.entryId == null && photo.stopId !in outerStopIds
            }.forEach { photo ->
                add(
                    TimelineItem(
                        id = "photo-${photo.id}",
                        time = photo.capturedAt ?: photo.createdAt,
                        kind = TimelineKind.PHOTO,
                        // Ein leerer Foto-Titel wird nirgends als "Foto" ersetzt.
                        title = photo.caption,
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

    /** Live-Luftlinie in km in der gleichen Reihenfolge wie die gezeichnete Route. */
    val distanceKm = combine(stops, allEntries, photos) { list, entries, photoList ->
        // Erst in Zeitreihenfolge bringen, dann die markierten Außenpins
        // entfernen. So zählt auch ein markierter Mittelpin nicht mit, ohne
        // die Strecke davor und danach künstlich zu unterbrechen.
        val ordered = chronologicalStops(list, entries, photoList)
            .filterNot { it.isArrivalDeparture }
        val meters = (1 until ordered.size).sumOf { index ->
            val previous = ordered[index - 1]
            val current = ordered[index]
            haversineMeters(
                previous.latitude, previous.longitude,
                current.latitude, current.longitude,
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

    /** Solange der Titel automatisch ist, übernimmt er den ersten bekannten Ort. */
    private suspend fun autoNameTripFrom(place: String?) {
        if (place.isNullOrBlank()) return
        val current = repository.observeTrips().first().firstOrNull { it.id == tripId }
            ?: return
        if (current.titleIsAuto) {
            repository.updateTrip(current.copy(title = place.trim()))
        }
    }

    /** Titel direkt in der Kopfleiste ändern – danach bleibt er fest. */
    fun setTripTitle(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.observeTrips().first().firstOrNull { it.id == tripId }
                ?: return@launch
            repository.updateTrip(current.copy(title = title.trim(), titleIsAuto = false))
        }
    }

    /**
     * Neuer Eintrag: ohne Ort über „+", oder direkt an einem ausgewählten Pin.
     * Die Zeit bleibt leer, damit das erste Foto sie übernehmen kann. Hat der
     * Pin eine Zeit, wird sie vom Pin übernommen.
     */
    suspend fun createEntry(stopId: String? = null): Entry = withContext(Dispatchers.IO) {
        val stop = stopId?.let { id ->
            repository.observeStops(tripId).first().firstOrNull { it.id == id }
        }
        val created = repository.addEntry(tripId, stopId, "", "")
        // Rückverlinkung: der Pin gilt jetzt als tbe-Pin und steht nicht mehr
        // zusätzlich als alleinstehender Pin in der Zeitleiste.
        if (stop != null && stop.entryId == null) {
            repository.updateStop(stop.copy(entryId = created.id))
        }
        val stopTime = stop?.visitedAt
        if (stopTime != null) {
            val timed = created.copy(entryAt = stopTime, timeFromPhoto = false)
            repository.updateEntry(timed)
            timed
        } else {
            created
        }
    }

    /**
     * Langes Drücken auf ein loses Foto in der zl: neuer tbe mit genau diesem
     * Foto. Hat das Foto einen Pin, bekommt der tbe diesen Ort; die Zeit
     * kommt aus dem Foto, damit der tbe an derselben Stelle der zl steht.
     */
    suspend fun createEntryFromPhoto(photoId: String): Entry? = withContext(Dispatchers.IO) {
        val photo = repository.observePhotos(tripId).first()
            .firstOrNull { it.id == photoId } ?: return@withContext null
        if (photo.entryId != null) return@withContext null
        var entry = createEntry(photo.stopId)
        val photoTime = photo.capturedAt
        if (photoTime != null) {
            entry = entry.copy(entryAt = photoTime, timeFromPhoto = true)
        }
        if (photo.stopId != null) entry = entry.copy(placeFromPhoto = true)
        repository.updateEntry(entry)
        repository.updatePhoto(photo.copy(entryId = entry.id))
        entry
    }

    /** Über „+“ angelegter Eintrag, der nur mit Inhalt bestehen bleibt. */
    @Volatile
    private var draftEntryId: String? = null

    val currentDraftEntryId: String? get() = draftEntryId

    /**
     * Neuer Eintrag über „+“: Er wird sofort angelegt, damit Fotos und Ort
     * wie gewohnt funktionieren. Bleibt er leer, wird er beim Schließen
     * wieder entfernt (siehe [finishDraftEntry]).
     */
    suspend fun createDraftEntry(): Entry {
        finishDraftEntry()
        val created = createEntry(null)
        draftEntryId = created.id
        return created
    }

    /** Der „+“-Eintrag ist geschlossen: leer → löschen, sonst behalten. */
    fun finishDraftEntry() {
        val id = draftEntryId ?: return
        draftEntryId = null
        saveScope.launch { discardEntryIfEmpty(id) }
    }

    private suspend fun discardEntryIfEmpty(entryId: String) {
        // Erst den zuletzt getippten Text schreiben, dann prüfen.
        flushEntryText(entryId)
        val entry = repository.observeEntriesForTrip(tripId).first()
            .firstOrNull { it.id == entryId } ?: return
        val hasPhotos = repository.observePhotos(tripId).first().any { it.entryId == entryId }
        val hasPins = repository.observeStops(tripId).first().any { it.entryId == entryId }
        val isEmpty = entry.title.isBlank() && entry.body.isBlank() &&
            entry.stopId == null && entry.entryAt == null && !hasPhotos && !hasPins
        if (isEmpty) repository.deleteEntry(entryId)
    }

    /** Direkte Bearbeitung im geöffneten Eintrag – kein Dialog mehr. */
    fun updateEntryText(entryId: String, title: String, body: String) {
        // Nicht mehr pro Tastendruck parallel speichern: der jeweils neueste
        // Text wird gemerkt und kurz nach der letzten Eingabe geschrieben.
        // So kann ein älterer Speichervorgang nie den neueren überholen.
        pendingEntryTexts[entryId] = title to body
        scheduleTextSave("entry-$entryId") { flushEntryText(entryId) }
    }

    /** Wartende Texte sofort speichern, z. B. beim Schließen des Detailfensters. */
    fun flushPendingTexts() {
        saveScope.launch {
            pendingEntryTexts.keys.toList().forEach { flushEntryText(it) }
            pendingCaptions.keys.toList().forEach { flushCaption(it) }
        }
    }

    private fun scheduleTextSave(key: String, save: suspend () -> Unit) {
        synchronized(textSaveJobs) {
            textSaveJobs[key]?.cancel()
            textSaveJobs[key] = saveScope.launch {
                delay(TEXT_SAVE_DELAY_MS)
                // Einmal begonnen, wird das Speichern nicht mehr abgebrochen.
                withContext(NonCancellable) { save() }
            }
        }
    }

    private suspend fun flushEntryText(entryId: String): Unit = textWriteLock.withLock {
        val (title, body) = pendingEntryTexts.remove(entryId) ?: return@withLock
        val entry = repository.observeEntriesForTrip(tripId)
            .first().firstOrNull { it.id == entryId } ?: return@withLock
        if (entry.title != title || entry.body != body) {
            repository.updateEntry(entry.copy(title = title, body = body))
        }
        Unit
    }

    private suspend fun flushCaption(photoId: String): Unit = textWriteLock.withLock {
        val caption = pendingCaptions.remove(photoId) ?: return@withLock
        val photo = repository.observePhotos(tripId).first()
            .firstOrNull { it.id == photoId } ?: return@withLock
        if (photo.caption != caption) repository.updatePhoto(photo.copy(caption = caption))
        Unit
    }

    override fun onCleared() {
        // Die Ansicht wird geschlossen: noch nicht gespeicherten Text sofort
        // schreiben. saveScope läuft unabhängig vom viewModelScope weiter.
        flushPendingTexts()
        // Reise verlassen, während der neue „+“-Eintrag noch leer offen war.
        finishDraftEntry()
        super.onCleared()
    }

    fun setEntryTime(entryId: String, time: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            // Erst den getippten Text sichern, sonst würde die Zeitänderung
            // den alten Text zurückschreiben.
            flushEntryText(entryId)
            val entry = repository.observeEntriesForTrip(tripId)
                .first().firstOrNull { it.id == entryId } ?: return@launch
            repository.updateEntry(entry.copy(entryAt = time, timeFromPhoto = false))
            // Der verlinkte Pin übernimmt dieselbe Zeit. Damit wandert der
            // Abschnitt in der Route, sobald der Zeitpunkt geändert wird.
            repository.observeStops(tripId).first()
                .filter { stop -> stop.entryId == entryId || stop.id == entry.stopId }
                .forEach { stop ->
                    if (stop.visitedAt != time) {
                        repository.updateStop(stop.copy(visitedAt = time))
                    }
                }
        }
    }

    /** Lesbarer Ort „Stadt, Staat" für Foto-Details – lokal gecacht. */
    suspend fun placeName(latitude: Double, longitude: Double): String? =
        placeNameResolver.resolve(latitude, longitude)

    /** Bearbeitbare Zeit eines alleinstehenden Pins; null löscht sie wieder. */
    fun setStopVisitedAt(stopId: String, time: Long?) {
        viewModelScope.launch(Dispatchers.IO) {
            val stop = repository.observeStops(tripId).first()
                .firstOrNull { it.id == stopId } ?: return@launch
            repository.updateStop(stop.copy(visitedAt = time))
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

    /**
     * Fotos werden einem geöffneten Eintrag hinzugefügt. Zeit und Ort kommen
     * aus EXIF, aber nur in noch leere Felder: vorhandene Werte bleiben stehen.
     */
    suspend fun addPhotosToEntry(entryId: String, images: List<ImportedPhoto>) =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext
            flushEntryText(entryId)
            val entry = repository.observeEntriesForTrip(tripId).first()
                .firstOrNull { it.id == entryId } ?: return@withContext
            val now = System.currentTimeMillis()
            val startIndex = repository.observePhotos(tripId).first().size
            val created = mutableListOf<Photo>()

            images.forEachIndexed { index, item ->
                // Ein einzelnes schlechtes Foto darf den ganzen Import nicht umbringen.
                val metadata = try {
                    PhotoImporter.read(item.bytes)
                } catch (_: Throwable) {
                    null
                }
                // MediaStore-Werte greifen, wenn die Datei selbst keine GPS-Daten hat.
                val capturedAt = item.capturedAt ?: metadata?.capturedAt
                val latitude = item.latitude ?: metadata?.latitude
                val longitude = item.longitude ?: metadata?.longitude
                val fileName = try {
                    photoStore.save(item.bytes, item.mimeType)
                } catch (_: Throwable) {
                    return@forEachIndexed
                }
                val photo = Photo(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    stopId = null,
                    entryId = entryId,
                    fileName = fileName,
                    mimeType = item.mimeType,
                    capturedAt = capturedAt,
                    latitude = latitude,
                    longitude = longitude,
                    caption = "",
                    sortIndex = startIndex + index,
                    createdAt = now + index,
                )
                try {
                    repository.insertPhoto(photo)
                    created.add(photo)
                } catch (_: Throwable) {
                    // Dieses Bild überspringen, den Rest trotzdem übernehmen.
                }
            }
            if (created.isEmpty()) return@withContext

            // Zeit und Ort dürfen aus verschiedenen Bildern kommen: hat das
            // erste nur eine Zeit und das nächste GPS, übernehmen wir beides.
            val timeSource = created.firstOrNull { it.capturedAt != null }?.capturedAt
            val placeSource = created.firstOrNull {
                it.latitude != null && it.longitude != null
            }

            var updated = entry
            if (timeSource != null &&
                (updated.entryAt == null || updated.timeFromPhoto)
            ) {
                updated = updated.copy(entryAt = timeSource, timeFromPhoto = true)
            }

            val photoLatitude = placeSource?.latitude
            val photoLongitude = placeSource?.longitude
            var targetStopId = updated.stopId
            var targetStop: Stop? = null
            if ((targetStopId == null || updated.placeFromPhoto) &&
                photoLatitude != null && photoLongitude != null
            ) {
                val photoTime = placeSource?.capturedAt ?: timeSource
                targetStop = repository.observeStops(tripId).first()
                    .firstOrNull { stop ->
                        photoMatchesStop(
                            photoLatitude, photoLongitude, photoTime,
                            stop.latitude, stop.longitude, stop.visitedAt,
                        )
                    }
                if (targetStop == null) {
                    val place = runCatching {
                        placeNameResolver.resolve(photoLatitude, photoLongitude)
                    }.getOrNull()
                    val name = place ?: run {
                        val stamp = photoTime?.let {
                            SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(it))
                        } ?: "Foto"
                        "Foto · $stamp"
                    }
                    targetStop = repository.addStop(
                        tripId = tripId,
                        title = name,
                        latitude = photoLatitude,
                        longitude = photoLongitude,
                        visitedAt = photoTime,
                        source = StopSource.PHOTO,
                    )
                    autoNameTripFrom(place)
                }
                targetStopId = targetStop.id
                updated = updated.copy(stopId = targetStopId, placeFromPhoto = true)
            } else if (targetStopId != null) {
                targetStop = repository.observeStops(tripId).first()
                    .firstOrNull { it.id == targetStopId }
            }

            repository.updateEntry(updated)
            if (targetStopId != null) {
                targetStop?.copy(entryId = entryId)?.let { repository.updateStop(it) }
            }
            created.forEach { photo ->
                repository.updatePhoto(
                    photo.copy(
                        stopId = targetStopId ?: photo.stopId,
                        latitude = photo.latitude ?: targetStop?.latitude,
                        longitude = photo.longitude ?: targetStop?.longitude,
                    ),
                )
            }
        }

    /**
     * Geteilte Fotos (z. B. aus Google Fotos) kommen ohne Tagebucheintrag in
     * die Reise: Jedes steht einzeln in der zl und lässt sich später per
     * Drag & Drop einem tbe zuordnen. Fotos mit Ort landen wie gewohnt an
     * einem passenden Foto-Pin oder bekommen einen neuen.
     */
    suspend fun addLoosePhotos(images: List<ImportedPhoto>): Int =
        withContext(Dispatchers.IO) {
            if (images.isEmpty()) return@withContext 0
            val now = System.currentTimeMillis()
            val startIndex = repository.observePhotos(tripId).first().size
            var added = 0
            images.forEachIndexed { index, item ->
                // Ein einzelnes schlechtes Foto darf den ganzen Import nicht umbringen.
                val metadata = try {
                    PhotoImporter.read(item.bytes)
                } catch (_: Throwable) {
                    null
                }
                val fileName = try {
                    photoStore.save(item.bytes, item.mimeType)
                } catch (_: Throwable) {
                    return@forEachIndexed
                }
                val photo = Photo(
                    id = UUID.randomUUID().toString(),
                    tripId = tripId,
                    stopId = null,
                    entryId = null,
                    fileName = fileName,
                    mimeType = item.mimeType,
                    capturedAt = item.capturedAt ?: metadata?.capturedAt,
                    latitude = item.latitude ?: metadata?.latitude,
                    longitude = item.longitude ?: metadata?.longitude,
                    caption = "",
                    sortIndex = startIndex + index,
                    createdAt = now + index,
                )
                try {
                    repository.insertPhoto(photo)
                    added++
                } catch (_: Throwable) {
                    // Dieses Bild überspringen, den Rest trotzdem übernehmen.
                }
            }
            // Fotos mit Ort an Foto-Pins hängen (bestehende oder neue).
            if (added > 0) runCatching { clusterUnassigned() }
            added
        }

    /** Fotos ohne offenen Eintrag landen in einem neuen Eintrag. */
    fun importPhotos(items: List<ImportedPhoto>) {
        if (items.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val entry = createEntry()
            addPhotosToEntry(entry.id, items)
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
            autoNameTripFrom(targetStop?.title)
            if (targetStop != null && entryId != null) {
                repository.updateStop(targetStop.copy(entryId = entryId))
            }

            when {
                elementKey.startsWith("photo-") -> {
                    val id = elementKey.removePrefix("photo-")
                    val photo = repository.observePhotos(tripId).first()
                        .firstOrNull { it.id == id } ?: return@launch
                    // Ein Bild kann in jeden anderen Eintrag wandern. Der
                    // bisherige Eintrag bleibt dabei unverändert bestehen.
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
                        entry?.copy(stopId = stop.id, placeFromPhoto = false)
                            ?.let { repository.updateEntry(it) }
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
            val cover = repository.observeTrips().first()
                .firstOrNull { it.id == tripId }?.coverPhotoId
            if (cover == photoId) repository.clearCoverPhoto(tripId)
            // Die Zuordnung zum Eintrag verschwindet mit dem Foto; der Eintrag
            // selbst bleibt unverändert erhalten.
            repository.deletePhoto(photoId)
        }
    }

    /** Foto-Titel, leer bedeutet „optionales Feld noch nicht ausgefüllt“. */
    fun updatePhotoCaption(photoId: String, caption: String) {
        pendingCaptions[photoId] = caption
        scheduleTextSave("photo-$photoId") { flushCaption(photoId) }
    }

    fun setCoverPhoto(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.setCoverPhoto(tripId, photoId)
        }
    }

    fun clearCoverPhoto() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearCoverPhoto(tripId)
        }
    }

    /**
     * Der Ort eines Fotos wird in dessen Eintrag übernommen: Der Eintrag
     * bekommt genau diesen Pin, und alle Bilder des Eintrags wandern dorthin.
     */
    fun adoptPhotoLocation(photoId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val photo = repository.observePhotos(tripId).first()
                .firstOrNull { it.id == photoId } ?: return@launch
            val latitude = photo.latitude ?: return@launch
            val longitude = photo.longitude ?: return@launch
            val entry = repository.observeEntriesForTrip(tripId).first()
                .firstOrNull { it.id == photo.entryId } ?: return@launch

            val stop = repository.observeStops(tripId).first().firstOrNull { candidate ->
                photoMatchesStop(
                    photo.latitude, photo.longitude, photo.capturedAt,
                    candidate.latitude, candidate.longitude, candidate.visitedAt,
                )
            } ?: repository.addStop(
                tripId = tripId,
                title = placeNameResolver.resolve(latitude, longitude) ?: run {
                    val stamp = photo.capturedAt?.let {
                        SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(it))
                    } ?: "Foto"
                    "Foto · $stamp"
                },
                latitude = latitude,
                longitude = longitude,
                visitedAt = photo.capturedAt,
                source = StopSource.PHOTO,
            )

            autoNameTripFrom(stop.title)
            repository.updateStop(stop.copy(entryId = entry.id))
            repository.updateEntry(
                entry.copy(
                    stopId = stop.id,
                    entryAt = entry.entryAt ?: photo.capturedAt,
                    placeFromPhoto = true,
                    timeFromPhoto = entry.entryAt == null && photo.capturedAt != null,
                ),
            )
            repository.observePhotos(tripId).first()
                .filter { it.entryId == entry.id }
                .forEach { child ->
                    repository.updatePhoto(
                        child.copy(
                            stopId = stop.id,
                            latitude = latitude,
                            longitude = longitude,
                        ),
                    )
                }
        }
    }

    /** Kartenstil aus dem Reise-Menü. */
    fun setOverlayMode(mode: OverlayMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.observeTrips().first().firstOrNull { it.id == tripId }
                ?: return@launch
            repository.updateTrip(current.copy(overlayMode = mode))
        }
    }

    /** Standard-Verkehrsmittel der Reise aus dem Reise-Menü. */
    fun setDefaultTransportMode(mode: TransportMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val current = repository.observeTrips().first().firstOrNull { it.id == tripId }
                ?: return@launch
            repository.updateTrip(current.copy(defaultTransportMode = mode))
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

    /** Verkehrsmittel nur für den Abschnitt zwischen zwei Pins ändern. */
    fun setSegmentMode(fromStopId: String, toStopId: String, mode: TransportMode) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            repository.upsertSegmentMode(
                SegmentMode(
                    id = segmentKey(fromStopId, toStopId),
                    tripId = tripId,
                    fromStopId = fromStopId,
                    toStopId = toStopId,
                    mode = mode,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
    }

    /**
     * Anreise und Abreise der Reise gemeinsam setzen. Es gibt höchstens zwei
     * markierte Pins. Alle anderen Häkchen werden entfernt, damit ein alter
     * Endpin nicht irgendwo in der Mitte aus Datum und Kilometern fällt.
     * Die Änderungen laufen nacheinander in einer Coroutine – ein gleichzeitiges
     * Setzen und Löschen desselben Pins kann so nicht mehr gewinnen.
     */
    fun setTripBoundaries(anreiseStopId: String?, abreiseStopId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            // Schnelles Umschalten: jede Änderung wartet, bis die vorige fertig
            // geschrieben ist. Sonst könnte ein älterer Stand gewinnen.
            boundaryLock.withLock { applyTripBoundaries(anreiseStopId, abreiseStopId) }
        }
    }

    private val boundaryLock = Mutex()

    private suspend fun applyTripBoundaries(anreiseStopId: String?, abreiseStopId: String?) {
        run {
            val marked = setOfNotNull(anreiseStopId, abreiseStopId)
            val changes = repository.observeStops(tripId).first()
                .filter { it.isArrivalDeparture != (it.id in marked) }
                .map { it.copy(isArrivalDeparture = it.id in marked) }
            changes.filterNot { it.isArrivalDeparture }.forEach { repository.updateStop(it) }
            changes.filter { it.isArrivalDeparture }.forEach { repository.updateStop(it) }
        }
    }

    /** Wieder den Standard der Reise für diesen Abschnitt benutzen. */
    fun clearSegmentMode(fromStopId: String, toStopId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSegmentMode(segmentKey(fromStopId, toStopId))
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
            // Der neue Pin bekommt gleich eine passende Zeit: so sortiert er
            // sich an der richtigen Stelle der Route ein.
            val visitedAt = fittingTimeForNewStop(
                latitude = latitude,
                longitude = longitude,
                stops = repository.observeStops(tripId).first(),
                entries = repository.observeEntriesForTrip(tripId).first(),
                photos = repository.observePhotos(tripId).first(),
            )
            repository.addStop(
                tripId = tripId,
                title = name ?: "Wegpunkt $number",
                latitude = latitude,
                longitude = longitude,
                visitedAt = visitedAt,
                source = StopSource.MANUAL,
            )
            autoNameTripFrom(name)
        }
    }

    /**
     * Beim Ziehen eines Pins ziehen Eintrag und Bilder mit. Bei Pins ohne tbe
     * bleiben die Bilder ortsfest – nur der Pin wandert.
     */
    fun moveStop(
        stopId: String,
        latitude: Double,
        longitude: Double,
        moveLinkedContent: Boolean = true,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val stopsNow = repository.observeStops(tripId).first()
            val stop = stopsNow.firstOrNull { it.id == stopId } ?: return@launch
            repository.updateStop(stop.copy(latitude = latitude, longitude = longitude))
            if (!moveLinkedContent) return@launch

            val entriesNow = repository.observeEntriesForTrip(tripId).first()
            val entryIds = entriesNow
                .filter { it.stopId == stopId || stop.entryId == it.id }
                .map { it.id }
                .toSet()
            if (stop.entryId != null) {
                entriesNow.firstOrNull { it.id == stop.entryId }
                    ?.takeIf { it.stopId != stopId }
                    ?.let { repository.updateEntry(it.copy(stopId = stopId)) }
            }
            repository.observePhotos(tripId).first()
                .filter { it.stopId == stopId || it.entryId in entryIds }
                .forEach { photo ->
                    repository.updatePhoto(
                        photo.copy(
                            stopId = stopId,
                            latitude = latitude,
                            longitude = longitude,
                        ),
                    )
                }
        }
    }

    /** Standalone-Pin: nur der Pin zieht um, Fotos behalten ihre GPS-Daten. */
    fun moveStopOnly(stopId: String, latitude: Double, longitude: Double) =
        moveStop(stopId, latitude, longitude, moveLinkedContent = false)

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

    /** Gibt einem Eintrag ohne Ort einen neuen Pin – auch per Drag & Drop. */
    fun placeEntryAt(entryId: String, latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = repository.observeEntriesForTrip(tripId)
                .first().firstOrNull { it.id == entryId }
                ?: return@launch
            val number = stops.value.size + 1
            val name = placeNameResolver.resolve(latitude, longitude)
            val stop = repository.addStop(tripId, name ?: "Wegpunkt $number", latitude, longitude)
            autoNameTripFrom(name)
            repository.updateEntry(entry.copy(stopId = stop.id, placeFromPhoto = false))
            repository.updateStop(stop.copy(entryId = entryId))
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

    /** Eintrag aus der Timeline auf einen vorhandenen Pin ablegen. */
    fun assignEntryToStop(entryId: String, stopId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val entry = repository.observeEntriesForTrip(tripId)
                .first().firstOrNull { it.id == entryId } ?: return@launch
            val stop = repository.observeStops(tripId).first()
                .firstOrNull { it.id == stopId } ?: return@launch
            autoNameTripFrom(stop.title)
            repository.updateEntry(entry.copy(stopId = stop.id, placeFromPhoto = false))
            repository.updateStop(stop.copy(entryId = entryId))
            repository.observePhotos(tripId).first()
                .filter { it.entryId == entryId }
                .forEach { photo ->
                    repository.updatePhoto(
                        photo.copy(
                            stopId = stop.id,
                            latitude = stop.latitude,
                            longitude = stop.longitude,
                        ),
                    )
                }
        }
    }

    /** alter Name für den „Ort setzen"-Knopf aus dem Vollbild. */
    private fun placeEntry(entryId: String, latitude: Double, longitude: Double) {
        placeEntryAt(entryId, latitude, longitude)
    }

    /**
     * Fotos mit Ort werden gruppiert. Wichtig: neue Fotos werden auch in einen
     * schon vorhandenen Foto-Stopp einsortiert – sonst entstand pro Import ein
     * eigener Stopp. Das war der Gruppierungs-Bug.
     */
    private suspend fun clusterUnassigned() {
        val all = repository.observePhotos(tripId).first()
        val entries = repository.observeEntriesForTrip(tripId).first()
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
                autoNameTripFrom(place)

                val stop = repository.addStop(
                    tripId = tripId,
                    title = title,
                    latitude = latitude,
                    longitude = longitude,
                    visitedAt = capturedAt,
                    source = StopSource.PHOTO,
                )
                cluster.forEach { photo -> repository.updatePhoto(photo.copy(stopId = stop.id)) }
                cluster.mapNotNull { it.entryId }.toSet().forEach { id ->
                    entries.firstOrNull { it.id == id && it.stopId == null }
                        ?.let { repository.updateEntry(it.copy(stopId = stop.id)) }
                }
            }
        }
    }

    fun addWaypoint(
        afterStopId: String?,
        latitude: Double,
        longitude: Double,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.addWaypoint(tripId, afterStopId, latitude, longitude)
        }
    }

    fun moveWaypoint(waypoint: RouteWaypoint, latitude: Double, longitude: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateWaypoint(
                waypoint.copy(
                    latitude = latitude,
                    longitude = longitude,
                ),
            )
        }
    }

    fun deleteWaypoint(id: String) {
        viewModelScope.launch(Dispatchers.IO) { repository.deleteWaypoint(id) }
    }

}
