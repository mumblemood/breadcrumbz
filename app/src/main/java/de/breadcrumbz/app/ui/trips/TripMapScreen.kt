package de.breadcrumbz.app.ui.trips

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.PhotoSourceReader
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.PolaroidOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun standardTiles() = XYTileSource(
    "OSM-Standard",
    0,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.tile.openstreetmap.org/",
        "https://b.tile.openstreetmap.org/",
        "https://c.tile.openstreetmap.org/",
    ),
    "© OpenStreetMap-Mitwirkende",
)

@Composable
private fun rememberThumbnail(viewModel: TripMapViewModel, fileName: String): ImageBitmap? =
    remember(fileName) {
        val options = BitmapFactory.Options().apply { inSampleSize = 8 }
        BitmapFactory.decodeFile(viewModel.photoPath(fileName), options)?.asImageBitmap()
    }

private fun formatDate(time: Long?, withYear: Boolean = false): String {
    if (time == null) return "Ohne Datum"
    val pattern = if (withYear) "dd.MM.yyyy HH:mm" else "dd.MM. HH:mm"
    return SimpleDateFormat(pattern, Locale.GERMANY).format(Date(time))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    viewModel: TripMapViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val activeStopId by viewModel.selectedStopId.collectAsStateWithLifecycle()
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val pendingPhotoId by viewModel.pendingPhotoId.collectAsStateWithLifecycle()
    val pendingEntryId by viewModel.pendingEntryId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var didCenter by remember { mutableStateOf(false) }
    var showPhotos by remember { mutableStateOf(false) }
    var showTimeline by remember { mutableStateOf(false) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    var stackPhotoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDeleteStopId by remember { mutableStateOf<String?>(null) }
    var showEntryEditor by remember { mutableStateOf(false) }
    var editingEntryId by remember { mutableStateOf<String?>(null) }
    var entryTitle by remember { mutableStateOf("") }
    var entryBody by remember { mutableStateOf("") }

    BackHandler {
        when {
            stackPhotoIds.isNotEmpty() -> stackPhotoIds = emptyList()
            selectedPhotoId != null -> selectedPhotoId = null
            showTimeline -> showTimeline = false
            pendingPhotoId != null || pendingEntryId != null -> viewModel.cancelPlacement()
            activeStopId != null -> viewModel.selectStop(null)
            else -> onBack()
        }
    }

    fun openEntryEditor(entry: Entry?) {
        editingEntryId = entry?.id
        entryTitle = entry?.title.orEmpty()
        entryBody = entry?.body.orEmpty()
        showEntryEditor = true
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val imported = uris.mapNotNull { uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    if (!mimeType.startsWith("image/")) return@mapNotNull null
                    val bytes = PhotoSourceReader.read(context, uri) ?: return@mapNotNull null
                    ImportedPhoto(bytes, mimeType)
                }
                viewModel.importPhotos(imported)
            }
        }
    }

    val requestImportPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        importLauncher.launch(arrayOf("image/*"))
    }

    fun startPhotoImport() {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
        }
        if (permissions.isEmpty()) {
            importLauncher.launch(arrayOf("image/*"))
        } else {
            requestImportPermissions.launch(permissions.toTypedArray())
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(standardTiles())
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(51.0, 10.0))
        }
    }

    val tapOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean = false

            override fun longPressHelper(point: GeoPoint?): Boolean {
                if (point != null) {
                    viewModel.onMapLongPress(point.latitude, point.longitude)
                }
                return true
            }
        })
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(trip?.title ?: "Reise") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Zurück") }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    if (stops.isNotEmpty() && !didCenter) {
                        view.controller.setCenter(
                            GeoPoint(stops.first().latitude, stops.first().longitude),
                        )
                        view.controller.setZoom(16.0)
                        didCenter = true
                    }

                    view.overlays.clear()
                    view.overlays.add(tapOverlay)

                    val placedPhotos = photos.filter {
                        it.latitude != null && it.longitude != null
                    }
                    view.overlays.add(
                        PolaroidOverlay(
                            photos = placedPhotos,
                            filePath = { name -> viewModel.photoPath(name) },
                            onPhotoClick = { ids ->
                                if (ids.size == 1) {
                                    selectedPhotoId = ids.first()
                                } else {
                                    stackPhotoIds = ids
                                }
                            },
                        ),
                    )

                    if (stops.size > 1) {
                        val route = Polyline(view)
                        route.setPoints(stops.map { GeoPoint(it.latitude, it.longitude) })
                        route.color = 0xFF3B2B20.toInt()
                        route.width = 6f
                        view.overlays.add(route)
                    }

                    stops.forEach { stop ->
                        val marker = Marker(view)
                        marker.position = GeoPoint(stop.latitude, stop.longitude)
                        marker.title = stop.title
                        marker.snippet = "Ziehen verschiebt den Punkt"
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                        val icon = ContextCompat.getDrawable(context, R.drawable.ic_stop_marker)?.mutate()
                        icon?.let {
                            it.setBounds(
                                -it.intrinsicWidth / 2,
                                -it.intrinsicHeight,
                                it.intrinsicWidth / 2,
                                0,
                            )
                            marker.icon = it
                        }

                        marker.isDraggable = true
                        val stopId = stop.id
                        marker.setOnMarkerClickListener { _, _ ->
                            selectedPhotoId = null
                            showTimeline = false
                            showPhotos = false
                            viewModel.selectStop(stopId)
                            true
                        }
                        marker.setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                            override fun onMarkerDrag(marker: Marker?) = Unit

                            override fun onMarkerDragStart(marker: Marker?) = Unit

                            override fun onMarkerDragEnd(marker: Marker?) {
                                if (marker != null) {
                                    viewModel.moveStop(
                                        stopId,
                                        marker.position.latitude,
                                        marker.position.longitude,
                                    )
                                }
                            }
                        })
                        view.overlays.add(marker)
                    }
                    view.invalidate()
                },
            )

            Button(
                onClick = {
                    val points = buildList {
                        stops.forEach { add(GeoPoint(it.latitude, it.longitude)) }
                        photos.forEach { photo ->
                            val latitude = photo.latitude
                            val longitude = photo.longitude
                            if (latitude != null && longitude != null) {
                                add(GeoPoint(latitude, longitude))
                            }
                        }
                    }
                    if (points.isNotEmpty()) {
                        mapView.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 100)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            ) {
                Text("Ganze Reise")
            }

            if (pendingPhotoId != null || pendingEntryId != null) {
                Card(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            if (pendingEntryId != null) {
                                "Eintrag: jetzt lange auf die Karte drücken"
                            } else {
                                "Foto: jetzt lange auf die Karte drücken"
                            },
                        )
                        TextButton(onClick = { viewModel.cancelPlacement() }) {
                            Text("Abbrechen")
                        }
                    }
                }
            }

            val activeStop = stops.firstOrNull { it.id == activeStopId }
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .heightIn(max = if (showTimeline) 480.dp else 400.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (activeStop != null) {
                        Text(activeStop.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = formatDate(activeStop.visitedAt, withYear = true),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                        ) {
                            if (entries.isEmpty()) {
                                Text(
                                    "Noch keine Einträge für diesen Stopp.",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            entries.forEach { entry ->
                                EntryRow(
                                    entry = entry,
                                    onEdit = { openEntryEditor(entry) },
                                    onDelete = { viewModel.deleteEntry(entry.id) },
                                )
                            }
                        }
                        Row {
                            TextButton(onClick = { openEntryEditor(null) }) {
                                Text("+ Eintrag")
                            }
                            TextButton(onClick = { confirmDeleteStopId = activeStop.id }) {
                                Text("Pin löschen")
                            }
                            TextButton(onClick = { viewModel.selectStop(null) }) {
                                Text("Zur Karte")
                            }
                        }
                    } else {
                        Text(trip?.title ?: "Reise", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${stops.size} Wegpunkte · ${photos.size} Fotos · " +
                                String.format(Locale.GERMANY, "%.1f", distanceKm) + " km (Luftlinie)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Marker antippen = Tagebuch. Lange drücken = Punkt setzen.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row {
                            TextButton(onClick = { showTimeline = !showTimeline }) {
                                Text(if (showTimeline) "Timeline aus" else "Timeline")
                            }
                            TextButton(onClick = { startPhotoImport() }) {
                                Text("Fotos")
                            }
                            TextButton(onClick = { showPhotos = !showPhotos }) {
                                Text(if (showPhotos) "Bilder aus" else "Bilder")
                            }
                        }

                        if (showTimeline) {
                            Spacer(Modifier.height(8.dp))
                            Text("Zeitstrom", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(4.dp))
                            if (timeline.isEmpty()) {
                                Text("Noch keine Einträge in dieser Reise.")
                            } else {
                                LazyRow(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(118.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(timeline, key = { it.id }) { item ->
                                        TimelineCard(
                                            item = item,
                                            onClick = {
                                                when (item.kind) {
                                                    TimelineKind.PHOTO -> {
                                                        selectedPhotoId = item.photoId
                                                    }
                                                    TimelineKind.STOP -> {
                                                        val stop = stops.firstOrNull {
                                                            it.id == item.stopId
                                                        }
                                                        if (stop != null) {
                                                            focusStop(mapView, stop.latitude, stop.longitude)
                                                            viewModel.selectStop(stop.id)
                                                            showTimeline = false
                                                        }
                                                    }
                                                    TimelineKind.ENTRY -> {
                                                        val entryId = item.entryId
                                                        if (item.hasLocation) {
                                                            val stop = stops.firstOrNull {
                                                                it.id == item.stopId
                                                            }
                                                            if (stop != null) {
                                                                focusStop(
                                                                    mapView,
                                                                    stop.latitude,
                                                                    stop.longitude,
                                                                )
                                                                viewModel.selectStop(stop.id)
                                                                showTimeline = false
                                                            }
                                                        } else if (entryId != null) {
                                                            viewModel.beginEntryPlacement(entryId)
                                                        }
                                                    }
                                                }
                                            },
                                            onPlaceLocation = if (!item.hasLocation && item.entryId != null) {
                                                { item.entryId?.let(viewModel::beginEntryPlacement) }
                                            } else {
                                                null
                                            },
                                        )
                                    }
                                }
                            }
                        }

                        if (showPhotos) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                val withoutLocation = photos.filter {
                                    it.latitude == null || it.longitude == null
                                }
                                val withLocation = photos.filter {
                                    it.latitude != null && it.longitude != null
                                }

                                if (withoutLocation.isNotEmpty()) {
                                    Text(
                                        "Ohne Ort – antippen, dann lange auf die Karte drücken",
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                    withoutLocation.forEach { photo ->
                                        PhotoRow(
                                            photo = photo,
                                            thumbnail = rememberThumbnail(viewModel, photo.fileName),
                                            onClick = { viewModel.beginPlacement(photo.id) },
                                            onDelete = { viewModel.deletePhoto(photo.id) },
                                        )
                                    }
                                }

                                if (withLocation.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Text("Auf der Karte", style = MaterialTheme.typography.labelLarge)
                                    withLocation.forEach { photo ->
                                        PhotoRow(
                                            photo = photo,
                                            thumbnail = rememberThumbnail(viewModel, photo.fileName),
                                            onClick = { selectedPhotoId = photo.id },
                                            onDelete = { viewModel.deletePhoto(photo.id) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    val selectedPhoto = photos.firstOrNull { it.id == selectedPhotoId }
    if (selectedPhoto != null) {
        AlertDialog(
            onDismissRequest = { selectedPhotoId = null },
            title = { Text("Foto") },
            text = {
                Column {
                    val thumbnail = rememberThumbnail(viewModel, selectedPhoto.fileName)
                    if (thumbnail != null) {
                        Image(
                            bitmap = thumbnail,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(formatDate(selectedPhoto.capturedAt, withYear = true))
                    if (selectedPhoto.caption.isNotBlank()) {
                        Text(selectedPhoto.caption)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedPhotoId = null }) { Text("Schließen") }
            },
        )
    }

    if (stackPhotoIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { stackPhotoIds = emptyList() },
            title = { Text("${stackPhotoIds.size} Fotos an diesem Ort") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    stackPhotoIds.forEach { id ->
                        val photo = photos.firstOrNull { it.id == id } ?: return@forEach
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedPhotoId = id
                                    stackPhotoIds = emptyList()
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val thumbnail = rememberThumbnail(viewModel, photo.fileName)
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Spacer(Modifier.size(10.dp))
                            Text(formatDate(photo.capturedAt))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { stackPhotoIds = emptyList() }) { Text("Schließen") }
            },
        )
    }

    if (confirmDeleteStopId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteStopId = null },
            title = { Text("Pin löschen?") },
            text = {
                Text(
                    "Der Pin wird entfernt. Tagebucheinträge bleiben ohne Ort in der " +
                        "Timeline erhalten und können dort neu verortet werden. " +
                        "Fotos kommen zurück in „Ohne Ort“.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteStopId?.let { viewModel.deleteStop(it) }
                        viewModel.selectStop(null)
                        confirmDeleteStopId = null
                    },
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteStopId = null }) { Text("Abbrechen") }
            },
        )
    }

    if (showEntryEditor && activeStopId != null) {
        AlertDialog(
            onDismissRequest = { showEntryEditor = false },
            title = { Text(if (editingEntryId == null) "Neuer Eintrag" else "Eintrag bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(
                        value = entryTitle,
                        onValueChange = { entryTitle = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = entryBody,
                        onValueChange = { entryBody = it },
                        label = { Text("Text") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeStopId?.let { stopId ->
                            viewModel.saveEntry(stopId, editingEntryId, entryTitle, entryBody)
                        }
                        showEntryEditor = false
                    },
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEntryEditor = false }) { Text("Abbrechen") }
            },
        )
    }
}

private fun focusStop(mapView: MapView, latitude: Double, longitude: Double) {
    mapView.controller.setCenter(GeoPoint(latitude, longitude))
    mapView.controller.setZoom(16.0)
}

@Composable
private fun PhotoRow(
    photo: Photo,
    thumbnail: ImageBitmap?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumbnail != null) {
            Image(
                bitmap = thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
        }
        Spacer(Modifier.size(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatDate(photo.capturedAt),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = photo.caption.ifBlank {
                    if (photo.latitude == null) {
                        "GPS fehlt – antippen zum Setzen"
                    } else {
                        "Hängt am Pin"
                    }
                },
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onClick) { Text("Öffnen") }
        TextButton(onClick = onDelete) { Text("Löschen") }
    }
}

@Composable
private fun EntryRow(
    entry: Entry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = entry.title.ifBlank { "Ohne Titel" },
            style = MaterialTheme.typography.bodyMedium,
        )
        if (entry.body.isNotBlank()) {
            Text(text = entry.body, style = MaterialTheme.typography.bodySmall)
        }
        Row {
            TextButton(onClick = onEdit) { Text("Bearbeiten") }
            TextButton(onClick = onDelete) { Text("Löschen") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineCard(
    item: TimelineItem,
    onClick: () -> Unit,
    onPlaceLocation: (() -> Unit)?,
) {
    val label = when (item.kind) {
        TimelineKind.STOP -> "Station"
        TimelineKind.ENTRY -> "Tagebuch"
        TimelineKind.PHOTO -> "Foto"
    }
    Card(
        modifier = Modifier
            .width(190.dp)
            .height(118.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
        onClick = onClick,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                "$label · ${formatDate(item.time)}",
                style = MaterialTheme.typography.labelSmall,
            )
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            if (item.kind == TimelineKind.ENTRY && item.subtitle.isNotBlank()) {
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                )
            }
            if (!item.hasLocation && onPlaceLocation != null) {
                TextButton(
                    onClick = onPlaceLocation,
                    modifier = Modifier.height(30.dp),
                ) {
                    Text("Ort setzen")
                }
            }
        }
    }
}
