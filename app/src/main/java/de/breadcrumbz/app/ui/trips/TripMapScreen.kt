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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.PhotoSourceReader
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.OverlayMode
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.domain.util.haversineMeters
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.AdventureTimeline
import de.breadcrumbz.app.ui.map.ClusterElement
import de.breadcrumbz.app.ui.map.ClusterOverlay
import de.breadcrumbz.app.ui.map.EntryPlusOverlay
import de.breadcrumbz.app.ui.map.PinInteractionOverlay
import de.breadcrumbz.app.ui.map.PolaroidOverlay
import de.breadcrumbz.app.ui.map.RouteStyling
import de.breadcrumbz.app.ui.map.StickyNoteOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.text.SimpleDateFormat
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

private fun contrastTiles() = XYTileSource(
    "OSM-Kontrast",
    0,
    19,
    256,
    ".png",
    arrayOf(
        "https://a.tile.openstreetmap.fr/hot/",
        "https://b.tile.openstreetmap.fr/hot/",
        "https://c.tile.openstreetmap.fr/hot/",
    ),
    "© OpenStreetMap-Mitwirkende, HOT",
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
    var photoTargetEntryId by remember { mutableStateOf<String?>(null) }
    var mapBounds by remember { mutableStateOf(Rect.Zero) }

    var editingTitle by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var tripMenuExpanded by remember { mutableStateOf(false) }
    var appliedOverlayMode by remember { mutableStateOf<OverlayMode?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

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

    LaunchedEffect(trip?.id, trip?.title) {
        if (!editingTitle) titleDraft = trip?.title.orEmpty()
    }

    BackHandler {
        when {
            fullscreenKey != null -> fullscreenKey = null
            photoTargetEntryId != null -> photoTargetEntryId = null
            stackPhotoIds.isNotEmpty() -> stackPhotoIds = emptyList()
            selectedPhotoId != null -> selectedPhotoId = null
            pendingPhotoId != null || pendingEntryId != null -> viewModel.cancelPlacement()
            activeStopId != null -> viewModel.selectStop(null)
            else -> onBack()
        }
    }

    val dialogImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
    ) { uris ->
        val targetId = photoTargetEntryId
        if (uris.isNotEmpty() && targetId != null) {
            scope.launch(Dispatchers.IO) {
                val imported = uris.mapNotNull { uri ->
                    val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                    if (!mimeType.startsWith("image/")) return@mapNotNull null
                    val bytes = PhotoSourceReader.read(context, uri) ?: return@mapNotNull null
                    ImportedPhoto(bytes, mimeType)
                }
                viewModel.addPhotosToEntry(targetId, imported)
                withContext(Dispatchers.Main) {
                    photoTargetEntryId = null
                    fullscreenKey = "entry-$targetId"
                }
            }
        } else {
            photoTargetEntryId = null
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

    fun openNewEntry(stopId: String? = null) {
        scope.launch {
            val entry = viewModel.createEntry(stopId)
            expandedDay = dayKeyOf(entry.entryAt ?: entry.createdAt)
            fullscreenKey = "entry-${entry.id}"
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
            allowCreate = true,
            onTap = { id ->
                selectedPhotoId = null
                viewModel.selectStop(id)
            },
            onEmptyTap = { viewModel.selectStop(null) },
            onCreateStop = { latitude, longitude ->
                viewModel.onMapLongPress(latitude, longitude)
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

    DisposableEffect(mapView) {
        mapView.onResume()
        mapView.addMapListener(mapListener)
        onDispose {
            mapView.removeMapListener(mapListener)
            mapView.onPause()
            mapView.onDetach()
        }
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

    fun commitTitle() {
        if (titleDraft.isNotBlank()) {
            viewModel.setTripTitle(titleDraft)
        } else {
            titleDraft = trip?.title.orEmpty()
        }
        editingTitle = false
        keyboardController?.hide()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    if (editingTitle) {
                        OutlinedTextField(
                            value = titleDraft,
                            onValueChange = { titleDraft = it },
                            label = { Text("Reisetitel") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitTitle() }),
                            modifier = Modifier.fillMaxWidth().height(56.dp),
                        )
                    } else {
                        val titleText = if (dateRange.isBlank()) {
                            trip?.title ?: "Reise"
                        } else {
                            "${trip?.title ?: "Reise"} · $dateRange"
                        }
                        Text(
                            text = titleText,
                            fontStyle = if (trip?.titleIsAuto == true) {
                                FontStyle.Italic
                            } else {
                                FontStyle.Normal
                            },
                            modifier = Modifier.clickable {
                                titleDraft = trip?.title.orEmpty()
                                editingTitle = true
                            },
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    Box {
                        IconButton(onClick = { tripMenuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = "Reise-Einstellungen",
                            )
                        }
                        DropdownMenu(
                            expanded = tripMenuExpanded,
                            onDismissRequest = { tripMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (trip?.routeMode == RouteMode.DIRECT) {
                                            "Linie: direkt"
                                        } else {
                                            "Linie: Auto"
                                        },
                                    )
                                },
                                onClick = {
                                    tripMenuExpanded = false
                                    viewModel.setRouteMode(
                                        if (trip?.routeMode == RouteMode.DIRECT) {
                                            RouteMode.AUTO
                                        } else {
                                            RouteMode.DIRECT
                                        },
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (trip?.overlayMode == OverlayMode.POSTER) {
                                            "Karte: Kontrast"
                                        } else {
                                            "Karte: Standard"
                                        },
                                    )
                                },
                                onClick = {
                                    tripMenuExpanded = false
                                    viewModel.setOverlayMode(
                                        if (trip?.overlayMode == OverlayMode.POSTER) {
                                            OverlayMode.STANDARD
                                        } else {
                                            OverlayMode.POSTER
                                        },
                                    )
                                },
                            )
                        }
                    }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .onGloballyPositioned { mapBounds = it.boundsInRoot() },
            ) {
                AndroidView(
                    factory = { mapView },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        val mode = trip?.overlayMode ?: OverlayMode.STANDARD
                        if (appliedOverlayMode != mode) {
                            view.setTileSource(
                                if (mode == OverlayMode.POSTER) contrastTiles() else standardTiles(),
                            )
                            appliedOverlayMode = mode
                        }

                        if (stops.isNotEmpty() && !didCenter) {
                            view.controller.setCenter(
                                GeoPoint(stops.first().latitude, stops.first().longitude),
                            )
                            view.controller.setZoom(16.0)
                            didCenter = true
                        }

                        view.overlays.clear()
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
                                // Ausgewählter Pin: 1,5× größer, Spitze bleibt auf dem Ort.
                                val factor = if (stop.id == activeStopId) 1.5f else 1f
                                val width = (it.intrinsicWidth * factor).roundToInt()
                                val height = (it.intrinsicHeight * factor).roundToInt()
                                it.setBounds(-width / 2, -height, width / 2, 0)
                                marker.icon = it
                            }
                            marker.isDraggable = false
                            markers[stop.id] = marker
                            if (!clustered) view.overlays.add(marker)
                        }
                        if (!clustered) view.overlays.add(pinOverlay)

                        val selectedStop = stops.firstOrNull { it.id == activeStopId }
                        if (selectedStop != null &&
                            allEntries.none { it.stopId == selectedStop.id }
                        ) {
                            view.overlays.add(
                                EntryPlusOverlay(
                                    GeoPoint(selectedStop.latitude, selectedStop.longitude),
                                ) { openNewEntry(selectedStop.id) },
                            )
                        }
                        view.invalidate()
                    },
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Button(onClick = { openNewEntry() }) {
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
                        val activeStop = stops.firstOrNull { it.id == activeStopId }
                        if (activeStop == null) {
                            Text(
                                "Pin antippen zum Auswählen",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (activeStop != null) {
                            Text(
                                activeStop.title,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row {
                                TextButton(onClick = { openNewEntry(activeStop.id) }) {
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
                    onDropEntry = { entryId, screenX, screenY ->
                        if (screenY >= mapBounds.top && screenY <= mapBounds.bottom &&
                            screenX >= mapBounds.left && screenX <= mapBounds.right
                        ) {
                            val pixelX = screenX - mapBounds.left
                            val pixelY = screenY - mapBounds.top
                            val targetStopId = markers.entries.firstOrNull { (_, marker) ->
                                val pixels = mapView.projection.toPixels(marker.position, null)
                                kotlin.math.hypot(
                                    pixels.x.toDouble() - pixelX,
                                    pixels.y.toDouble() - pixelY,
                                ) < 40.0
                            }?.key
                            if (targetStopId != null) {
                                viewModel.assignEntryToStop(entryId, targetStopId)
                            } else {
                                val geo = mapView.projection.fromPixels(
                                    pixelX.roundToInt(),
                                    pixelY.roundToInt(),
                                )
                                viewModel.placeEntryAt(entryId, geo.latitude, geo.longitude)
                            }
                        }
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
            onAddPhotos = { entryId ->
                fullscreenKey = null
                photoTargetEntryId = entryId
                startGalleryPick()
            },
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
