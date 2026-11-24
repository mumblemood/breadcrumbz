package de.breadcrumbz.app.ui.trips

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.PhotoSourceReader
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.domain.util.haversineMeters
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.AdventureTimeline
import de.breadcrumbz.app.ui.map.ClusterElement
import de.breadcrumbz.app.ui.map.ClusterOverlay
import de.breadcrumbz.app.ui.map.PinInteractionOverlay
import de.breadcrumbz.app.ui.map.PolaroidOverlay
import de.breadcrumbz.app.ui.map.RouteStyling
import de.breadcrumbz.app.ui.map.StickyNoteOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

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

@Composable
private fun rememberFullImage(viewModel: TripMapViewModel, fileName: String): ImageBitmap? =
    remember(fileName) {
        BitmapFactory.decodeFile(viewModel.photoPath(fileName))?.asImageBitmap()
    }

private fun formatDate(time: Long?, withYear: Boolean = false): String {
    if (time == null) return "–"
    val pattern = if (withYear) "dd.MM.yyyy HH:mm" else "dd.MM.yyyy"
    return SimpleDateFormat(pattern, Locale.GERMANY).format(Date(time))
}

private fun formatTime(time: Long): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(time))

private fun dayKeyOf(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(time))

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
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val activeStopId by viewModel.selectedStopId.collectAsStateWithLifecycle()
    val pendingPhotoId by viewModel.pendingPhotoId.collectAsStateWithLifecycle()
    val pendingEntryId by viewModel.pendingEntryId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var didCenter by remember { mutableStateOf(false) }
    var mapZoom by remember { mutableStateOf(14f) }
    var mapCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var lastTimelineSync by remember { mutableStateOf(0L) }
    var fullscreenKey by remember { mutableStateOf<String?>(null) }
    var centerKey by remember { mutableStateOf<String?>(null) }
    val markers = remember { mutableMapOf<String, Marker>() }
    var expandedDay by remember { mutableStateOf<String?>(null) }
    var didAutoExpandDay by remember { mutableStateOf(false) }
    var expandedEntryIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }
    var stackPhotoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDeleteStopId by remember { mutableStateOf<String?>(null) }
    var showEntryEditor by remember { mutableStateOf(false) }
    var editingEntryId by remember { mutableStateOf<String?>(null) }
    var editorStopId by remember { mutableStateOf<String?>(null) }
    var entryTitle by remember { mutableStateOf("") }
    var entryBody by remember { mutableStateOf("") }

    var showNewEntryDialog by rememberSaveable { mutableStateOf(false) }
    var newEntryTitle by rememberSaveable { mutableStateOf("") }
    var newEntryBody by rememberSaveable { mutableStateOf("") }
    var newEntryTime by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var newEntryImages by remember { mutableStateOf<List<ImportedPhoto>>(emptyList()) }

    val thumbnails = photos.map { it.id to rememberThumbnail(viewModel, it.fileName) }.toMap()
    val photosByEntry = remember(photos) {
        photos.filter { it.entryId != null }
            .sortedBy { it.capturedAt ?: it.createdAt }
            .groupBy { it.entryId.orEmpty() }
            .mapValues { entry -> entry.value.map { it.id } }
    }
    val photoTimes = remember(photos) {
        photos.associate { it.id to (it.capturedAt ?: it.createdAt) }
    }
    val frontPhotoId = remember(centerKey) {
        val key = centerKey
        when {
            key?.startsWith("photo-") == true -> key.removePrefix("photo-")
            key?.startsWith("entryphoto-") == true -> key.removePrefix("entryphoto-")
            else -> null
        }
    }

    LaunchedEffect(timeline) {
        if (!didAutoExpandDay && timeline.isNotEmpty()) {
            expandedDay = dayKeyOf(timeline.first().time)
            didAutoExpandDay = true
        }
    }

    BackHandler {
        when {
            fullscreenKey != null -> fullscreenKey = null
            showEntryEditor -> showEntryEditor = false
            showNewEntryDialog -> showNewEntryDialog = false
            stackPhotoIds.isNotEmpty() -> stackPhotoIds = emptyList()
            selectedPhotoId != null -> selectedPhotoId = null
            pendingPhotoId != null || pendingEntryId != null -> viewModel.cancelPlacement()
            activeStopId != null -> viewModel.selectStop(null)
            else -> onBack()
        }
    }

    fun openEntryEditor(entry: Entry?) {
        editingEntryId = entry?.id
        editorStopId = entry?.stopId ?: activeStopId
        entryTitle = entry?.title.orEmpty()
        entryBody = entry?.body.orEmpty()
        showEntryEditor = true
    }

    val dialogImageLauncher = rememberLauncherForActivityResult(
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
                newEntryImages = newEntryImages + imported
            }
        }
    }

    val requestGalleryPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        dialogImageLauncher.launch(arrayOf("image/*"))
    }

    fun startGalleryPick() {
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
            dialogImageLauncher.launch(arrayOf("image/*"))
        } else {
            requestGalleryPermissions.launch(permissions.toTypedArray())
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

    val locationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView)
    }

    val mapListener = remember {
        object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                if (System.currentTimeMillis() - lastTimelineSync > 700L) {
                    lastTimelineSync = System.currentTimeMillis()
                    mapCenter = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                lastTimelineSync = System.currentTimeMillis()
                mapZoom = mapView.zoomLevelDouble.toFloat()
                mapCenter = GeoPoint(mapView.mapCenter.latitude, mapView.mapCenter.longitude)
                return false
            }
        }
    }

    val pinOverlay = remember(stops, activeStopId) {
        PinInteractionOverlay(
            markers = markers,
            stops = stops,
            selectedStopId = activeStopId,
            onTap = { id ->
                selectedPhotoId = null
                fullscreenKey = "stop-$id"
                viewModel.selectStop(id)
            },
            onMove = { id, latitude, longitude ->
                viewModel.moveStop(id, latitude, longitude)
            },
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            locationOverlay.enableMyLocation()
            val fix = locationOverlay.lastFix
            if (fix != null) {
                mapView.controller.setCenter(GeoPoint(fix.latitude, fix.longitude))
                mapView.controller.setZoom(16.0)
            } else {
                locationOverlay.runOnFirstFix {
                    mapView.post {
                        val first = locationOverlay.myLocation
                        if (first != null) {
                            mapView.controller.setCenter(first)
                            mapView.controller.setZoom(16.0)
                        }
                    }
                }
            }
        }
    }

    fun showMyLocation() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
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
        mapView.addMapListener(mapListener)
        onDispose {
            mapView.removeMapListener(mapListener)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    fun pickDate() {
        val calendar = Calendar.getInstance().apply { timeInMillis = newEntryTime }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                newEntryTime = Calendar.getInstance().apply {
                    timeInMillis = newEntryTime
                    set(year, month, day)
                }.timeInMillis
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun pickTime() {
        val calendar = Calendar.getInstance().apply { timeInMillis = newEntryTime }
        TimePickerDialog(
            context,
            { _, hour, minute ->
                newEntryTime = Calendar.getInstance().apply {
                    timeInMillis = newEntryTime
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }.timeInMillis
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true,
        ).show()
    }

    fun handleTimelineClick(item: TimelineItem) {
        when (item.kind) {
            TimelineKind.PHOTO -> {
                item.photoId?.let { fullscreenKey = "photo-$it" }
            }
            TimelineKind.STOP -> {
                val stop = stops.firstOrNull { it.id == item.stopId }
                if (stop != null) {
                    focusStop(mapView, stop.latitude, stop.longitude)
                    fullscreenKey = "stop-${stop.id}"
                    viewModel.selectStop(stop.id)
                }
            }
            TimelineKind.ENTRY -> {
                if (item.entryId != null) {
                    fullscreenKey = "entry-${item.entryId}"
                    if (!item.hasLocation) {
                        // Ort setzen bleibt im Vollbild möglich.
                        viewModel.cancelPlacement()
                    }
                }
            }
        }
    }

    fun geoFor(item: TimelineItem): GeoPoint? {
        val stop = stops.firstOrNull { it.id == item.stopId }
        if (stop != null) return GeoPoint(stop.latitude, stop.longitude)
        val photo = photos.firstOrNull { it.id == item.photoId }
        if (photo?.latitude != null && photo.longitude != null) {
            return GeoPoint(photo.latitude, photo.longitude)
        }
        return null
    }

    val nearestIndex = remember(mapCenter, timeline, stops, photos) {
        val center = mapCenter
        if (center == null || timeline.isEmpty()) {
            null
        } else {
            timeline.indices.minByOrNull { index ->
                val point = geoFor(timeline[index])
                if (point != null) {
                    haversineMeters(
                        center.latitude, center.longitude, point.latitude, point.longitude,
                    )
                } else {
                    Double.MAX_VALUE
                }
            }
        }
    }

    val dateRange = listOfNotNull(
        trip?.startDate?.let { formatDate(it) },
        trip?.endDate?.let { formatDate(it) },
    ).joinToString(" - ")

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (dateRange.isBlank()) {
                            trip?.title ?: "Reise"
                        } else {
                            "${trip?.title ?: "Reise"} · $dateRange"
                        },
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Karte: zwei Drittel des Bildschirms.
            Box(modifier = Modifier.fillMaxWidth().weight(2f)) {
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
                        view.overlays.add(locationOverlay)

                        val placedPhotos = photos.filter {
                            it.latitude != null && it.longitude != null
                        }
                        val clusterElements = buildList {
                            stops.forEach { stop ->
                                add(ClusterElement(stop.latitude, stop.longitude, "stop-${stop.id}"))
                            }
                            placedPhotos.forEach { photo ->
                                add(
                                    ClusterElement(
                                        photo.latitude!!,
                                        photo.longitude!!,
                                        "photo-${photo.id}",
                                    ),
                                )
                            }
                        }
                        val clustered = view.zoomLevelDouble <= 13.0 &&
                            clusterElements.isNotEmpty()

                        if (clustered) {
                            view.overlays.add(
                                ClusterOverlay(clusterElements) { box ->
                                    view.zoomToBoundingBox(box, true, 80)
                                },
                            )
                        } else {
                            view.overlays.add(
                                PolaroidOverlay(
                                    photos = placedPhotos,
                                    filePath = { name -> viewModel.photoPath(name) },
                                    onPhotoClick = { ids ->
                                        if (ids.size == 1) {
                                            fullscreenKey = "photo-${ids.first()}"
                                        } else {
                                            stackPhotoIds = ids
                                        }
                                    },
                                    frontPhotoId = frontPhotoId,
                                ),
                            )

                            val stickyEntries = allEntries.filter { entry ->
                                entry.stopId != null &&
                                    (entry.title.isNotBlank() || entry.body.isNotBlank())
                            }
                            view.overlays.add(
                                StickyNoteOverlay(
                                    entries = stickyEntries,
                                    stops = stops,
                                    onEntryClick = { id -> fullscreenKey = "entry-$id" },
                                ),
                            )
                        }

                        RouteStyling.build(
                            mapView = view,
                            stops = stops,
                            defaultMode = trip?.defaultTransportMode
                                ?: de.breadcrumbz.app.domain.model.TransportMode.CAR,
                            routeMode = trip?.routeMode ?: RouteMode.AUTO,
                        ).forEach { view.overlays.add(it) }

                        markers.clear()
                        stops.forEach { stop ->
                            val marker = Marker(view)
                            marker.position = GeoPoint(stop.latitude, stop.longitude)
                            marker.title = stop.title
                            marker.snippet = "2 Sekunden halten, dann ziehen"
                            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                            val icon = ContextCompat.getDrawable(
                                context,
                                R.drawable.ic_stop_marker,
                            )?.mutate()
                            icon?.let {
                                it.setBounds(
                                    -it.intrinsicWidth / 2,
                                    -it.intrinsicHeight,
                                    it.intrinsicWidth / 2,
                                    0,
                                )
                                marker.icon = it
                            }
                            marker.isDraggable = false
                            markers[stop.id] = marker
                            if (!clustered) view.overlays.add(marker)
                        }
                        if (!clustered) view.overlays.add(pinOverlay)
                        view.invalidate()
                    },
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = {
                        newEntryTitle = ""
                        newEntryBody = ""
                        newEntryTime = System.currentTimeMillis()
                        newEntryImages = emptyList()
                        showNewEntryDialog = true
                    }) {
                        Text("+")
                    }
                    IconButton(
                        onClick = { showMyLocation() },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFBF4E4)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_location),
                            contentDescription = "Standort anzeigen",
                            tint = Color(0xFF3B2B20),
                        )
                    }
                    Button(onClick = {
                        val next = if (trip?.routeMode == RouteMode.DIRECT) {
                            RouteMode.AUTO
                        } else {
                            RouteMode.DIRECT
                        }
                        viewModel.setRouteMode(next)
                    }) {
                        Text(if (trip?.routeMode == RouteMode.DIRECT) "Linie: direkt" else "Linie: Auto")
                    }
                    IconButton(
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
                                mapView.zoomToBoundingBox(
                                    BoundingBox.fromGeoPoints(points),
                                    true,
                                    100,
                                )
                            }
                        },
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFFBF4E4)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_fullscreen),
                            contentDescription = "Ganze Reise vollständig anzeigen",
                            tint = Color(0xFF3B2B20),
                        )
                    }
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

                Card(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                        Text(
                            "${stops.size} Wegpunkte · ${photos.size} Fotos",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val activeStop = stops.firstOrNull { it.id == activeStopId }
                        if (activeStop != null) {
                            Text(
                                activeStop.title,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                TextButton(onClick = {
                                    editorStopId = activeStop.id
                                    editingEntryId = null
                                    entryTitle = ""
                                    entryBody = ""
                                    showEntryEditor = true
                                }) {
                                    Text("+ Eintrag")
                                }
                                TextButton(onClick = { confirmDeleteStopId = activeStop.id }) {
                                    Text("Pin löschen")
                                }
                            }
                        }
                    }
                }
            }

            // Timeline: festes unteres Drittel, von links nach rechts.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                AdventureTimeline(
                    items = timeline,
                    stops = stops,
                    photosByEntry = photosByEntry,
                    expandedDay = expandedDay,
                    expandedEntryIds = expandedEntryIds,
                    onDayToggle = { day ->
                        expandedDay = if (expandedDay == day) null else day
                    },
                    onEntryToggle = { id ->
                        expandedEntryIds = if (expandedEntryIds.contains(id)) {
                            expandedEntryIds - id
                        } else {
                            expandedEntryIds + id
                        }
                    },
                    onItemClick = { item -> handleTimelineClick(item) },
                    onPhotoClick = { id -> fullscreenKey = "photo-$id" },
                    photoBitmap = { id -> thumbnails[id] },
                    photoTimes = photoTimes,
                    mapZoom = mapZoom,
                    externalKey = timeline.getOrNull(nearestIndex ?: -1)?.id,
                    onScrollFraction = { fraction ->
                        if (timeline.isNotEmpty() &&
                            System.currentTimeMillis() - lastTimelineSync > 700L
                        ) {
                            val index = (fraction * timeline.lastIndex)
                                .roundToInt()
                                .coerceIn(0, timeline.lastIndex)
                            val point = geoFor(timeline[index])
                            if (point != null) {
                                lastTimelineSync = System.currentTimeMillis()
                                mapView.controller.setCenter(point)
                            }
                        }
                    },
                    onDropElement = { source, entryId ->
                        viewModel.assignElementToEntry(source, entryId)
                    },
                    onCenterKeyChange = { key -> centerKey = key },
                    modifier = Modifier.fillMaxHeight(),
                )
            }
        }
    }

    val selectedPhoto = photos.firstOrNull { it.id == selectedPhotoId }
    if (selectedPhoto != null) {
        var captionDraft by remember(selectedPhoto.id) {
            mutableStateOf(selectedPhoto.caption.take(12))
        }
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
                                .height(160.dp)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(formatDate(selectedPhoto.capturedAt, withYear = true))
                    OutlinedTextField(
                        value = captionDraft,
                        onValueChange = { captionDraft = it.take(12) },
                        label = { Text("Titel (max. 12 Zeichen)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(onClick = { viewModel.setCoverPhoto(selectedPhoto.id) }) {
                            Text("Titelbild")
                        }
                        if (selectedPhoto.latitude != null &&
                            selectedPhoto.longitude != null &&
                            selectedPhoto.entryId != null
                        ) {
                            TextButton(onClick = {
                                viewModel.adoptPhotoLocation(selectedPhoto.id)
                                selectedPhotoId = null
                            }) {
                                Text("Ort in Eintrag übernehmen")
                            }
                        }
                        if (selectedPhoto.latitude == null || selectedPhoto.longitude == null) {
                            TextButton(onClick = {
                                viewModel.beginPlacement(selectedPhoto.id)
                                selectedPhotoId = null
                            }) {
                                Text("Ort setzen")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updatePhotoCaption(selectedPhoto.id, captionDraft)
                    selectedPhotoId = null
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedPhotoId = null }) { Text("Schließen") }
            },
        )
    }

    if (stackPhotoIds.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { stackPhotoIds = emptyList() },
            title = { Text("${stackPhotoIds.size} Fotos an diesem Ort") },
            text = {
                Column {
                    stackPhotoIds.forEach { id ->
                        val photo = photos.firstOrNull { it.id == id } ?: return@forEach
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val thumbnail = thumbnails[id]
                            if (thumbnail != null) {
                                Image(
                                    bitmap = thumbnail,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(formatDate(photo.capturedAt))
                                if (photo.caption.isNotBlank()) {
                                    Text(photo.caption, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = {
                                fullscreenKey = "photo-$id"
                                stackPhotoIds = emptyList()
                            }) {
                                Text("Öffnen")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { stackPhotoIds = emptyList() }) { Text("Schließen") }
            },
        )
    }

    if (showNewEntryDialog) {
        AlertDialog(
            onDismissRequest = { showNewEntryDialog = false },
            title = { Text("Neuer Eintrag") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newEntryTitle,
                        onValueChange = { newEntryTitle = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newEntryBody,
                        onValueChange = { newEntryBody = it },
                        label = { Text("Tagebuch") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { pickDate() }) {
                            Text(formatDate(newEntryTime))
                        }
                        Button(onClick = { pickTime() }) {
                            Text(formatTime(newEntryTime))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Der Ort wird automatisch aus GPS und Zeit der Bilder " +
                            "bzw. vorhandenen Stationen bestimmt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = { startGalleryPick() }) {
                        Text("Bilder hinzufügen (${newEntryImages.size})")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosenTime = newEntryTime
                    val chosenImages = newEntryImages
                    viewModel.createEntryFromDialog(
                        title = newEntryTitle,
                        body = newEntryBody,
                        time = chosenTime,
                        images = chosenImages,
                        fallbackLatitude = mapView.mapCenter.latitude,
                        fallbackLongitude = mapView.mapCenter.longitude,
                    )
                    expandedDay = dayKeyOf(chosenTime)
                    showNewEntryDialog = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewEntryDialog = false }) { Text("Abbrechen") }
            },
        )
    }

    if (confirmDeleteStopId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteStopId = null },
            title = { Text("Pin löschen?") },
            text = {
                Text(
                    "Der Pin wird entfernt. Einträge bleiben ohne Ort in der Timeline " +
                        "erhalten und können dort neu verortet werden. Fotos kommen " +
                        "zurück in „Ohne Ort“.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteStopId?.let { viewModel.deleteStop(it) }
                    viewModel.selectStop(null)
                    confirmDeleteStopId = null
                }) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteStopId = null }) { Text("Abbrechen") }
            },
        )
    }

    if (showEntryEditor && editorStopId != null) {
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
                TextButton(onClick = {
                    editorStopId?.let { stopId ->
                        viewModel.saveEntry(stopId, editingEntryId, entryTitle, entryBody)
                    }
                    showEntryEditor = false
                }) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEntryEditor = false }) { Text("Abbrechen") }
            },
        )
    }

    val activeFullscreenKey = fullscreenKey
    if (activeFullscreenKey != null) {
        FullscreenElement(
            elementKey = activeFullscreenKey,
            viewModel = viewModel,
            stops = stops,
            photos = photos,
            entries = allEntries,
            photoBitmap = { id -> thumbnails[id] },
            onClose = { fullscreenKey = null },
            onOpenElement = { key -> fullscreenKey = key },
            onEditEntry = { entry -> openEntryEditor(entry) },
            onEditPhoto = { id ->
                fullscreenKey = null
                selectedPhotoId = id
            },
            onAdoptPhotoLocation = { id -> viewModel.adoptPhotoLocation(id) },
        )
    }
}

private fun focusStop(mapView: MapView, latitude: Double, longitude: Double) {
    mapView.controller.setCenter(GeoPoint(latitude, longitude))
    mapView.controller.setZoom(16.0)
}
