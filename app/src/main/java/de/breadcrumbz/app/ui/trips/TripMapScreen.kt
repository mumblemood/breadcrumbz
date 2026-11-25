package de.breadcrumbz.app.ui.trips

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.data.photo.PhotoSourceReader
import de.breadcrumbz.app.data.route.RoutingService
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.OverlayMode
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.domain.util.chronologicalStops
import de.breadcrumbz.app.domain.util.haversineMeters
import de.breadcrumbz.app.domain.util.segmentKey
import de.breadcrumbz.app.domain.util.tripTimeRange
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.AdventureTimeline
import de.breadcrumbz.app.ui.map.PinInteractionOverlay
import de.breadcrumbz.app.ui.map.PolaroidOverlay
import de.breadcrumbz.app.ui.map.RouteEditOverlay
import de.breadcrumbz.app.ui.map.RouteStyling
import de.breadcrumbz.app.ui.theme.Ink
import de.breadcrumbz.app.ui.theme.PaperAlertDialog
import de.breadcrumbz.app.ui.theme.PaperCard
import de.breadcrumbz.app.ui.theme.PaperIconButton
import de.breadcrumbz.app.ui.theme.SealRed
import de.breadcrumbz.app.ui.theme.SealRedDark
import de.breadcrumbz.app.ui.theme.Sepia
import de.breadcrumbz.app.ui.theme.TransportIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log2
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

/**
 * Färbt die OSM-Kacheln wie eine alte Papierkarte: Sepia-Mischung, Weiß wird
 * zum Papierton #F4E9D2, Schwarz bleibt Schwarz. Die Kacheln selbst bleiben
 * unverändert, es ist nur ein Farbfilter beim Zeichnen.
 */
private fun paperTileFilter() = ColorMatrixColorFilter(
    ColorMatrix(
        floatArrayOf(
            0.534f, 0.339f, 0.083f, 0f, 0f,
            0.158f, 0.680f, 0.076f, 0f, 0f,
            0.128f, 0.250f, 0.445f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Ein gemeinsames Ziel für beide Richtungen der Karten-/Zeitleisten-Synchronisation. */
private data class SyncTarget(
    val key: String?,
    val origin: String,
    val seq: Long,
)

/** Fortlaufende Reiseposition für Karte, Zeitleiste und Schieberegler. */
private data class ScrollSync(
    val fraction: Float,
    val origin: String,
    val seq: Long,
)

/** Ein Abschnitt gehört immer zu genau einem Start-Ziel-Paar. */
private data class RouteSegment(
    val key: String,
    val fromStopId: String,
    val arrivalStopId: String,
    val points: List<GeoPoint>,
)

/**
 * Abstand eines Tipps zur gezeichneten Linie – nicht nur zu ihren Punkten.
 * Bei dicht beieinander liegenden Abschnitten landet das Pop-up sonst am
 * falschen Abschnitt und zeigt die An-/Abreise-Häkchen nicht an.
 */
private fun distanceToPolylineMeters(point: GeoPoint, points: List<GeoPoint>): Double {
    if (points.isEmpty()) return Double.MAX_VALUE
    if (points.size == 1) {
        return haversineMeters(
            point.latitude, point.longitude,
            points[0].latitude, points[0].longitude,
        )
    }
    var best = Double.MAX_VALUE
    for (index in 0 until points.size - 1) {
        val from = points[index]
        val to = points[index + 1]
        val midLatRad = Math.toRadians(
            (from.latitude + to.latitude + point.latitude) / 3.0,
        )
        val latMeters = 111_320.0
        val lonMeters = latMeters * cos(midLatRad).coerceAtLeast(0.01)
        val ax = from.longitude * lonMeters
        val ay = from.latitude * latMeters
        val bx = to.longitude * lonMeters
        val by = to.latitude * latMeters
        val px = point.longitude * lonMeters
        val py = point.latitude * latMeters
        val dx = bx - ax
        val dy = by - ay
        val lengthSquared = dx * dx + dy * dy
        val part = if (lengthSquared > 0.0) {
            (((px - ax) * dx + (py - ay) * dy) / lengthSquared).coerceIn(0.0, 1.0)
        } else {
            0.0
        }
        val closestX = ax + dx * part
        val closestY = ay + dy * part
        best = minOf(best, hypot(px - closestX, py - closestY))
    }
    return best
}

/** Zwei exklusive Stufen: Abschnittsdetails und die Verkehrsmittel-Auswahl. */
private sealed interface SegmentDialog {
    val segment: RouteSegment
    data class Detail(override val segment: RouteSegment) : SegmentDialog
    data class Modes(override val segment: RouteSegment) : SegmentDialog
}

/** Schieberegler zwischen Karte und Zeitleiste: zeigt die Reiseposition. */
@Composable
private fun RouteScrollbar(
    fraction: Float,
    onFraction: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    var width by remember { mutableStateOf(0f) }
    val thumbPx = with(density) { 22.dp.toPx() }
    val padPx = with(density) { 8.dp.toPx() }
    fun fractionAt(x: Float): Float {
        val usable = width - thumbPx - padPx * 2f
        return if (usable > 0f) ((x - padPx) / usable).coerceIn(0f, 1f) else 0f
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(20.dp)
            .onGloballyPositioned { width = it.size.width.toFloat() }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    onFraction(fractionAt(change.position.x))
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> onFraction(fractionAt(offset.x)) }
            },
    ) {
        // Gestrichelte Spur wie ein Weg auf der Karte.
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 8.dp)
                .fillMaxWidth()
                .height(2.dp),
        ) {
            drawLine(
                color = Sepia,
                start = androidx.compose.ui.geometry.Offset(0f, size.height / 2f),
                end = androidx.compose.ui.geometry.Offset(size.width, size.height / 2f),
                strokeWidth = size.height,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(8.dp.toPx(), 5.dp.toPx()),
                ),
            )
        }
        val usable = (width - thumbPx - padPx * 2f).coerceAtLeast(1f)
        // Griff: Papierknopf mit Tintenrand und einem Tintenpunkt.
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = with(density) { (padPx + fraction * usable).toDp() })
                .size(22.dp)
                .background(PaperCard, CircleShape)
                .border(2.dp, Ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(Ink, CircleShape),
            )
        }
    }
}

@Composable
private fun rememberThumbnail(viewModel: TripMapViewModel, fileName: String): ImageBitmap? =
    remember(fileName) {
        PhotoImporter.decode(viewModel.photoPath(fileName), sampleSize = 8)?.asImageBitmap()
    }

@Composable
private fun rememberFullImage(viewModel: TripMapViewModel, fileName: String): ImageBitmap? {
    // Im Hintergrund und verkleinert laden – schützt vor Abstürzen bei großen Fotos.
    val image by produceState<ImageBitmap?>(initialValue = null, fileName) {
        value = withContext(Dispatchers.IO) {
            PhotoImporter.decode(viewModel.photoPath(fileName), maxSide = 2048)
                ?.asImageBitmap()
        }
    }
    return image
}

private fun formatDate(time: Long?, withYear: Boolean = false): String {
    if (time == null) return "–"
    val pattern = if (withYear) "dd.MM.yyyy HH:mm" else "dd.MM.yyyy"
    return SimpleDateFormat(pattern, Locale.GERMANY).format(Date(time))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    viewModel: TripMapViewModel,
    routingService: RoutingService,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val timeline by viewModel.timeline.collectAsStateWithLifecycle()
    val waypoints by viewModel.waypoints.collectAsStateWithLifecycle()
    val storedSegmentModes by viewModel.segmentModes.collectAsStateWithLifecycle()
    val onlineRouting by viewModel.onlineRouting.collectAsStateWithLifecycle()
    val activeStopId by viewModel.selectedStopId.collectAsStateWithLifecycle()
    val pendingPhotoId by viewModel.pendingPhotoId.collectAsStateWithLifecycle()
    val pendingEntryId by viewModel.pendingEntryId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var didCenter by remember { mutableStateOf(false) }
    var mapZoom by remember { mutableStateOf(14f) }
    /** Rohwert der Karte; wird erst nach kurzer Ruhe weitergegeben. */
    var rawMapCenter by remember { mutableStateOf<GeoPoint?>(null) }
    var mapCenter by remember { mutableStateOf<GeoPoint?>(null) }
    /** Kurz nach einer programmgesteuerten Bewegung kein Echo zurückschicken. */
    var syncLockUntil by remember { mutableStateOf(0L) }
    var syncSeq by remember { mutableStateOf(0L) }
    var syncTarget by remember { mutableStateOf<SyncTarget?>(null) }
    var scrollSync by remember { mutableStateOf(ScrollSync(0f, "start", 0L)) }
    var lastTimelineMoveAt by remember { mutableStateOf(0L) }
    var fullscreenKey by remember { mutableStateOf<String?>(null) }
    var centerKey by remember { mutableStateOf<String?>(null) }
    val markers = remember { mutableMapOf<String, Marker>() }
    var stackPhotoIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var coverPickerOpen by remember { mutableStateOf(false) }
    var segmentDialog by remember { mutableStateOf<SegmentDialog?>(null) }
    var defaultModePickerOpen by remember { mutableStateOf(false) }
    var photoTargetEntryId by remember { mutableStateOf<String?>(null) }
    var zoomOutToCurrent by remember { mutableStateOf(false) }
    var didRequestEmptyLocation by remember { mutableStateOf(false) }
    var mapBounds by remember { mutableStateOf(Rect.Zero) }
    /** Foto, das auf Karte und Zeitleiste sanft leuchtet. */
    var highlightedPhotoId by remember { mutableStateOf<String?>(null) }
    /** Springt die Zeitleiste gezielt zu einem Foto, statt nach Kartennähe zu raten. */
    var focusKey by remember { mutableStateOf<String?>(null) }
    var lastFocusAt by remember { mutableStateOf(0L) }
    var photoToDelete by remember { mutableStateOf<String?>(null) }
    var waypointToDelete by remember { mutableStateOf<String?>(null) }

    var editingTitle by remember { mutableStateOf(false) }
    var titleDraft by remember { mutableStateOf("") }
    var tripMenuExpanded by remember { mutableStateOf(false) }
    var appliedOverlayMode by remember { mutableStateOf<OverlayMode?>(null) }
    val keyboardController = LocalSoftwareKeyboardController.current

    val thumbnails = photos.map { it.id to rememberThumbnail(viewModel, it.fileName) }.toMap()
    // Stabile Lambda: sonst wird die Zeitleiste bei jeder Recomposition neu vermessen.
    val bitmapForPhoto = remember(thumbnails) { { id: String -> thumbnails[id] } }
    val photosByEntry = remember(photos) {
        photos.filter { it.entryId != null }
            .sortedBy { it.capturedAt ?: it.createdAt }
            .groupBy { it.entryId.orEmpty() }
            .mapValues { entry -> entry.value.map { it.id } }
    }
    val photoTimes = remember(photos) {
        photos.associate { it.id to (it.capturedAt ?: it.createdAt) }
    }

    /** Kanonische Position eines Timeline-Eintrags – auch ohne eigenen Pin. */
    fun geoFor(item: TimelineItem): GeoPoint? {
        val stop = item.stopId?.let { id -> stops.firstOrNull { it.id == id } }
        if (stop != null) return GeoPoint(stop.latitude, stop.longitude)
        val entryPhotoIds = item.entryId?.let { photosByEntry[it] }.orEmpty()
        val photoId = item.photoId ?: entryPhotoIds.firstOrNull()
        val photo = photoId?.let { id -> photos.firstOrNull { it.id == id } }
        if (photo?.latitude != null && photo.longitude != null) {
            return GeoPoint(photo.latitude, photo.longitude)
        }
        return null
    }

    // Alle Orte der Zeitleiste in Reihenfolge. Daraus lässt sich jede Position
    // zwischen zwei Einträgen berechnen – die Synchronisation läuft fließend.
    val geoPath = remember(timeline, stops, photosByEntry, photos) {
        timeline.mapNotNull { geoFor(it) }
    }

    fun pointAtFraction(fraction: Float): GeoPoint? {
        if (geoPath.isEmpty()) return null
        if (geoPath.size == 1) return geoPath.first()
        val scaled = fraction.coerceIn(0f, 1f) * (geoPath.size - 1).toFloat()
        val index = scaled.toInt().coerceAtMost(geoPath.size - 2)
        val part = scaled - index
        val from = geoPath[index]
        val to = geoPath[index + 1]
        return GeoPoint(
            from.latitude + (to.latitude - from.latitude) * part,
            from.longitude + (to.longitude - from.longitude) * part,
        )
    }

    fun fractionForGeo(point: GeoPoint): Float? {
        if (geoPath.isEmpty()) return null
        if (geoPath.size == 1) return 0f
        var bestDistance = Double.MAX_VALUE
        var bestFraction = 0.0
        for (index in 0 until geoPath.size - 1) {
            val from = geoPath[index]
            val to = geoPath[index + 1]
            val dx = to.longitude - from.longitude
            val dy = to.latitude - from.latitude
            val lengthSquared = dx * dx + dy * dy
            val part = if (lengthSquared > 0.0) {
                (((point.longitude - from.longitude) * dx +
                    (point.latitude - from.latitude) * dy) / lengthSquared)
                    .coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            val px = from.longitude + dx * part
            val py = from.latitude + dy * part
            val distance = (point.longitude - px) * (point.longitude - px) +
                (point.latitude - py) * (point.latitude - py)
            if (distance < bestDistance) {
                bestDistance = distance
                bestFraction = (index + part) / (geoPath.size - 1).toDouble()
            }
        }
        return bestFraction.toFloat().coerceIn(0f, 1f)
    }

    fun photoKey(id: String): String =
        if (photos.firstOrNull { it.id == id }?.entryId != null) {
            "entryphoto-$id"
        } else {
            "photo-$id"
        }

    val frontPhotoId = remember(centerKey, highlightedPhotoId) {
        val key = highlightedPhotoId?.let { photoKey(it) } ?: centerKey
        when {
            key?.startsWith("photo-") == true -> key.removePrefix("photo-")
            key?.startsWith("entryphoto-") == true -> key.removePrefix("entryphoto-")
            else -> null
        }
    }

    LaunchedEffect(trip?.id, trip?.title) {
        if (!editingTitle) titleDraft = trip?.title.orEmpty()
    }

    BackHandler {
        val dialog = segmentDialog
        when {
            fullscreenKey != null -> fullscreenKey = null
            photoTargetEntryId != null -> photoTargetEntryId = null
            coverPickerOpen -> coverPickerOpen = false
            defaultModePickerOpen -> defaultModePickerOpen = false
            dialog is SegmentDialog.Modes ->
                segmentDialog = SegmentDialog.Detail(dialog.segment)
            dialog is SegmentDialog.Detail -> segmentDialog = null
            stackPhotoIds.isNotEmpty() -> stackPhotoIds = emptyList()
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
                val imported = mutableListOf<ImportedPhoto>()
                uris.forEach { uri ->
                    try {
                        val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                        if (!mimeType.startsWith("image/")) return@forEach
                        val source = PhotoSourceReader.readWithMetadata(context, uri)
                            ?: return@forEach
                        imported.add(
                            ImportedPhoto(
                                bytes = source.bytes,
                                mimeType = mimeType,
                                latitude = source.latitude,
                                longitude = source.longitude,
                                capturedAt = source.capturedAt,
                            ),
                        )
                    } catch (_: Throwable) {
                        // Dieses eine Foto überspringen – die App bleibt stabil.
                    }
                }
                if (imported.isNotEmpty()) {
                    try {
                        // Erst fertig speichern, dann das Elementfenster öffnen.
                        viewModel.addPhotosToEntry(targetId, imported)
                        withContext(Dispatchers.Main) {
                            photoTargetEntryId = null
                            fullscreenKey = "entry-$targetId"
                        }
                    } catch (_: Throwable) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                "Fotos konnten nicht gespeichert werden.",
                                Toast.LENGTH_SHORT,
                            ).show()
                            photoTargetEntryId = null
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            "Kein lesbares Bild gefunden.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        photoTargetEntryId = null
                    }
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
            fullscreenKey = "entry-${entry.id}"
        }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(standardTiles())
            setMultiTouchControls(true)
            // Nicht weiter raus als ein Kartenbildschirm in der Höhe.
            minZoomLevel = 1.0
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
                val now = System.currentTimeMillis()
                // Ein bewusster Fokus bleibt kurz erhalten; danach führt die Karte.
                if (focusKey != null && now - lastFocusAt > 600L) focusKey = null
                if (now >= syncLockUntil) {
                    rawMapCenter = GeoPoint(
                        mapView.mapCenter.latitude,
                        mapView.mapCenter.longitude,
                    )
                }
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                val now = System.currentTimeMillis()
                if (focusKey != null && now - lastFocusAt > 600L) focusKey = null
                mapZoom = mapView.zoomLevelDouble.toFloat()
                if (now >= syncLockUntil) {
                    rawMapCenter = GeoPoint(
                        mapView.mapCenter.latitude,
                        mapView.mapCenter.longitude,
                    )
                }
                return false
            }
        }
    }

    /** Foto in beiden Ansichten markieren, Karte und Zeitleiste darauf ausrichten. */
    fun focusPhoto(photoId: String, moveMap: Boolean) {
        val photo = photos.firstOrNull { it.id == photoId } ?: return
        val key = photoKey(photoId)
        highlightedPhotoId = photoId
        centerKey = key
        focusKey = key
        lastFocusAt = System.currentTimeMillis()
        syncSeq += 1
        syncTarget = SyncTarget(key, "focus", syncSeq)
        if (moveMap) syncLockUntil = System.currentTimeMillis() + 700L
        if (moveMap) {
            val entryStopId = photo.entryId
                ?.let { id -> allEntries.firstOrNull { it.id == id }?.stopId }
            val stop = entryStopId?.let { id -> stops.firstOrNull { it.id == id } }
            val point = if (photo.latitude != null && photo.longitude != null) {
                GeoPoint(photo.latitude, photo.longitude)
            } else {
                stop?.let { GeoPoint(it.latitude, it.longitude) }
            }
            if (point != null) {
                // Programmbewegung darf nicht als Nutzer-Wisch zurückkommen.
                rawMapCenter = null
                mapCenter = point
                fractionForGeo(point)?.let { fraction ->
                    scrollSync = ScrollSync(fraction, "focus", syncSeq)
                }
                mapView.controller.animateTo(
                    point,
                    mapView.zoomLevelDouble,
                    600L,
                )
            }
        }
        stackPhotoIds = emptyList()
        fullscreenKey = "photo-$photoId"
    }

    val placedPhotos = remember(photos) {
        photos.filter { it.latitude != null && it.longitude != null }
    }
    val polaroidOverlay = remember(
        placedPhotos, viewModel, frontPhotoId, stackPhotoIds, highlightedPhotoId,
    ) {
        PolaroidOverlay(
            photos = placedPhotos,
            filePath = { name -> viewModel.photoPath(name) },
            onPhotoClick = { ids ->
                if (ids.size == 1) {
                    focusPhoto(ids.first(), moveMap = false)
                } else {
                    stackPhotoIds = ids
                    highlightedPhotoId = ids.first()
                }
            },
            frontPhotoId = frontPhotoId,
            expandedPhotoIds = stackPhotoIds.toSet(),
            onPhotoLongClick = { id -> photoToDelete = id },
            highlightPhotoId = highlightedPhotoId,
        )
    }

    // Die Linie fährt die Pins in der Reihenfolge ihrer Zeiten ab. Ändert sich
    // Datum oder Uhrzeit, wandert der Pin und damit der ganze Abschnitt sofort.
    val orderedRouteStops = remember(stops, photos, allEntries) {
        chronologicalStops(stops, allEntries, photos)
    }

    // Zieht eine Zeitänderung die Reihenfolge um, wandern Anreise und Abreise
    // mit an die neuen Endpunkte. Die Rollen werden eindeutig aus dem alten
    // ersten/letzten Pin gelesen und gemeinsam geschrieben – ohne Race und ohne
    // Sonderfall, der bei zwei Pins beide Häkchen auf denselben Pin setzte.
    var previousRouteIds by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(orderedRouteStops) {
        val ids = orderedRouteStops.map { it.id }
        val oldIds = previousRouteIds
        previousRouteIds = ids
        if (oldIds.isEmpty() || oldIds == ids) return@LaunchedEffect
        // Nur bei einer reinen Umsortierung (gleiche Pins, neue Reihenfolge)
        // wandern die Häkchen mit. Kommt ein Pin dazu oder fällt einer weg –
        // z. B. ein neuer Pin ohne Zeit, der deshalb ans Ende rutscht –, bleiben
        // An- und Abreise dort, wo sie gesetzt wurden.
        if (oldIds.toSet() != ids.toSet()) return@LaunchedEffect
        val oldFirst = oldIds.firstOrNull()
        val oldLast = oldIds.lastOrNull()
        val newFirst = ids.firstOrNull()
        val newLast = ids.lastOrNull()
        fun flagged(id: String?) = id != null &&
            stops.firstOrNull { it.id == id }?.isArrivalDeparture == true
        val hadAnreise = flagged(oldFirst)
        val hadAbreise = flagged(oldLast) && oldLast != oldFirst
        val nextAnreise = if (hadAnreise) newFirst else null
        var nextAbreise = if (hadAbreise) newLast else null
        // Bei einem Pin gibt es nur eine Rolle. Zwei Rollen auf demselben Pin
        // wären bei der nächsten Reihenfolgeänderung nicht mehr zuzuordnen.
        if (nextAnreise != null && nextAnreise == nextAbreise) nextAbreise = null
        viewModel.setTripBoundaries(nextAnreise, nextAbreise)
    }

    // Jeder Abschnitt mit allen selbst gesetzten Zwischenpunkten in Fahrtrichtung.
    // Der Schlüssel enthält Start und Ziel – bei einer neuen Reihenfolge wird
    // deshalb eine völlig neue Route berechnet.
    val routeSegments = remember(orderedRouteStops, waypoints) {
        buildList<RouteSegment> {
            for (index in 1 until orderedRouteStops.size) {
                val from = orderedRouteStops[index - 1]
                val to = orderedRouteStops[index]
                val extra = waypoints.filter { it.afterStopId == to.id }
                    .sortedBy { it.sortIndex }
                add(
                    RouteSegment(
                        key = segmentKey(from.id, to.id),
                        fromStopId = from.id,
                        arrivalStopId = to.id,
                        points = buildList {
                            add(GeoPoint(from.latitude, from.longitude))
                            extra.forEach { add(GeoPoint(it.latitude, it.longitude)) }
                            add(GeoPoint(to.latitude, to.longitude))
                        },
                    ),
                )
            }
        }
    }

    // Wechselt die Reihenfolge, während das Pop-up offen ist, gehört der alte
    // Abschnitt nicht mehr zur Reise. Das Pop-up wird geschlossen statt mit
    // veralteten Pinen weiterzuarbeiten.
    LaunchedEffect(routeSegments) {
        val open = segmentDialog
        if (open != null && routeSegments.none { it.key == open.segment.key }) {
            segmentDialog = null
        }
    }

    // Modus pro Abschnitt – gespeichert unter Start>Ziel und nicht am Pin.
    // Der Key ist eine Zeichenkette, damit ein Moduswechsel sofort sichtbar wird.
    val segmentModes = remember(routeSegments, storedSegmentModes, trip?.defaultTransportMode) {
        val stored = storedSegmentModes.associate { it.id to it.mode }
        routeSegments.associate { segment ->
            segment.key to (
                stored[segment.key]
                    ?: trip?.defaultTransportMode
                    ?: TransportMode.CAR
                )
        }
    }

    // Dieselben Zwischenpunkte ein mal als GeoPoint – für Linie und Griff-Overlay.
    val waypointPointsByStop = remember(waypoints) {
        waypoints.groupBy({ it.afterStopId.orEmpty() }) { waypoint ->
            GeoPoint(waypoint.latitude, waypoint.longitude)
        }
    }

    var onlinePoints by remember {
        mutableStateOf<Map<String, List<GeoPoint>>>(emptyMap())
    }
    LaunchedEffect(
        routeSegments, segmentModes, onlineRouting, trip?.routeMode,
    ) {
        if (!onlineRouting || trip?.routeMode == RouteMode.DIRECT) {
            onlinePoints = emptyMap()
            return@LaunchedEffect
        }
        // Alte Straßenpunkte sofort entfernen, statt sie bis zum neuen
        // Routing-Ergebnis weiter zu zeigen.
        onlinePoints = emptyMap()
        val fresh = mutableMapOf<String, List<GeoPoint>>()
        routeSegments.forEach { segment ->
            val mode = segmentModes[segment.key] ?: return@forEach
            if (mode == TransportMode.CAR || mode == TransportMode.CAMPER) {
                routingService.route(segment.points)?.let { fresh[segment.key] = it }
            }
        }
        onlinePoints = fresh
    }

    // Nur Straßenpunkte für Abschnitte verwenden, die auch gerade ein Auto
    // oder Wohnmobil nutzen – doppelter Schutz gegen veraltete Antworten.
    val effectiveOnlinePoints = remember(onlinePoints, segmentModes, routeSegments) {
        val allowed = routeSegments.map { it.key }.toSet()
        onlinePoints.filterKeys { key ->
            val mode = if (key in allowed) segmentModes[key] else null
            mode == TransportMode.CAR || mode == TransportMode.CAMPER
        }
    }

    // Zum Antippen zählt die gezeichnete Linie, nicht die gerade Verbindung.
    val routeEditSegments = remember(routeSegments, effectiveOnlinePoints) {
        routeSegments.map { segment ->
            segment.key to (effectiveOnlinePoints[segment.key] ?: segment.points)
        }
    }

    // Nur neu bauen, wenn sich Pins oder Verkehrsmittel ändern – nicht bei
    // jeder Kartenbewegung. So bleiben die Striche der Reiselinie stabil.
    val routeLines = remember(
        orderedRouteStops, segmentModes, trip?.defaultTransportMode, trip?.routeMode,
        effectiveOnlinePoints, waypointPointsByStop, routeEditSegments,
    ) {
        RouteStyling.build(
            mapView = mapView,
            stops = orderedRouteStops,
            defaultMode = trip?.defaultTransportMode ?: TransportMode.CAR,
            routeMode = trip?.routeMode ?: RouteMode.AUTO,
            segmentModes = segmentModes,
            overridePoints = effectiveOnlinePoints,
            waypoints = waypointPointsByStop,
            // Tippen auf die Linie öffnet die Verkehrsmittelwahl,
            // keine Sprechblase (InfoWindow ist in RouteStyling null).
            onLineClick = { clicked ->
                // Die Berührung gehört zum nächsten gezeichneten Abschnitt –
                // nicht einfach zum nächsten Pin. Der erste Pin hat selbst
                // keinen Abschnitt und darf hier nicht ausgewählt werden.
                val nearestKey = routeEditSegments.minByOrNull { (_, points) ->
                    distanceToPolylineMeters(clicked, points)
                }?.first
                val nearest = routeSegments.firstOrNull { it.key == nearestKey }
                segmentDialog = nearest?.let { SegmentDialog.Detail(it) }
            },
        )
    }

    // Griffe auf der Linie: ziehen verschiebt, langes Halten setzt einen neuen Punkt.
    val routeEditOverlay = remember(mapView, waypoints, routeEditSegments) {
        RouteEditOverlay(
            mapView = mapView,
            waypoints = waypoints,
            segments = routeEditSegments,
            onMove = { id, latitude, longitude ->
                waypoints.firstOrNull { it.id == id }?.let { point ->
                    viewModel.moveWaypoint(point, latitude, longitude)
                }
            },
            onCreate = { afterStopId, latitude, longitude ->
                viewModel.addWaypoint(afterStopId, latitude, longitude)
            },
            onTapWaypoint = { waypointToDelete = it },
        )
    }

    val pinOverlay = remember(stops, activeStopId, polaroidOverlay, routeEditOverlay) {
        PinInteractionOverlay(
            markers = markers,
            stops = stops,
            selectedStopId = activeStopId,
            allowCreate = true,
            onTap = { id ->
                viewModel.selectStop(id)
                fullscreenKey = "stop-$id"
            },
            onEmptyTap = { viewModel.selectStop(null) },
            onCreateStop = { latitude, longitude ->
                viewModel.onMapLongPress(latitude, longitude)
            },
            onMove = { id, latitude, longitude, moveLinkedContent ->
                viewModel.moveStop(
                    id,
                    latitude,
                    longitude,
                    moveLinkedContent = moveLinkedContent,
                )
            },
            // Halten auf Foto oder Linie erzeugt weder Pin noch Foto-Löschdialog.
            ignoreLongPress = { x, y ->
                polaroidOverlay.isPhotoAt(x, y) || routeEditOverlay.isRouteAt(x, y)
            },
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.any { it }) {
            locationOverlay.enableMyLocation()
            val zoom = if (zoomOutToCurrent) fillZoom(mapView) else 15.0
            zoomOutToCurrent = false
            focusCurrentPosition(mapView, locationOverlay, zoom)
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

    // Leere Reise: aktuelle Position in die Mitte, nicht die ganze Welt.
    LaunchedEffect(stops.isEmpty(), photos.isEmpty()) {
        if (stops.isEmpty() && photos.isEmpty() && !didRequestEmptyLocation) {
            didRequestEmptyLocation = true
            zoomOutToCurrent = true
            showMyLocation()
        }
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
                item.photoId?.let { focusPhoto(it, moveMap = true) }
            }
            TimelineKind.STOP -> {
                val stop = stops.firstOrNull { it.id == item.stopId }
                if (stop != null) {
                    syncSeq += 1
                    syncTarget = SyncTarget(item.id, "focus", syncSeq)
                    syncLockUntil = System.currentTimeMillis() + 700L
                    val point = GeoPoint(stop.latitude, stop.longitude)
                    rawMapCenter = null
                    mapCenter = point
                    fractionForGeo(point)?.let { fraction ->
                        scrollSync = ScrollSync(fraction, "focus", syncSeq)
                    }
                    focusStop(mapView, stop.latitude, stop.longitude)
                    fullscreenKey = "stop-${stop.id}"
                    viewModel.selectStop(stop.id)
                }
            }
            TimelineKind.ENTRY -> {
                if (item.entryId != null) {
                    syncSeq += 1
                    syncTarget = SyncTarget(item.id, "focus", syncSeq)
                    val point = geoFor(item)
                    if (point != null) {
                        syncLockUntil = System.currentTimeMillis() + 700L
                        rawMapCenter = null
                        mapCenter = point
                        fractionForGeo(point)?.let { fraction ->
                            scrollSync = ScrollSync(fraction, "focus", syncSeq)
                        }
                        mapView.controller.animateTo(
                            point,
                            mapView.zoomLevelDouble,
                            350L,
                        )
                    }
                    fullscreenKey = "entry-${item.entryId}"
                    if (!item.hasLocation) {
                        // Ort setzen bleibt im Vollbild möglich.
                        viewModel.cancelPlacement()
                    }
                }
            }
        }
    }

    /** Die Karte folgt einer Position, ohne das Echo als Nutzerwisch zu melden. */
    fun moveMapTo(point: GeoPoint, duration: Long = 140L) {
        syncLockUntil = System.currentTimeMillis() + duration + 200L
        rawMapCenter = null
        mapCenter = point
        mapView.controller.animateTo(point, mapView.zoomLevelDouble, duration)
    }

    // Die Karte meldet viele Scroll-Ereignisse. Die Position wird kurz
    // geglättet und fortlaufend an Zeitleiste und Schieberegler gegeben.
    LaunchedEffect(rawMapCenter) {
        val center = rawMapCenter ?: return@LaunchedEffect
        delay(90L)
        if (System.currentTimeMillis() < syncLockUntil) return@LaunchedEffect
        mapCenter = center
        val fraction = fractionForGeo(center) ?: return@LaunchedEffect
        syncTarget = null
        focusKey = null
        if (scrollSync.origin != "map" || abs(fraction - scrollSync.fraction) > 0.004f) {
            syncSeq += 1
            scrollSync = ScrollSync(fraction, "map", syncSeq)
        }
    }

    // An-/Abreise bleiben im Reisedatum außen vor, sind aber weiter sichtbar.
    val effectiveRange = remember(stops, allEntries, photos) {
        tripTimeRange(stops, allEntries, photos)
    }
    val dateRange = listOfNotNull(
        trip?.startDate?.let { formatDate(it) } ?: effectiveRange.first?.let { formatDate(it) },
        trip?.endDate?.let { formatDate(it) } ?: effectiveRange.second?.let { formatDate(it) },
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
                            placeholder = { Text("Reisetitel") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { commitTitle() }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        val titleText = if (dateRange.isBlank()) {
                            trip?.title ?: "Reise"
                        } else {
                            "${trip?.title ?: "Reise"} · $dateRange"
                        }
                        Text(
                            text = titleText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
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
                                            "Linie: automatisch"
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
                                            "Karte: Altes Papier"
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
                            DropdownMenuItem(
                                text = { Text("Titelbild wählen") },
                                onClick = {
                                    tripMenuExpanded = false
                                    coverPickerOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    val current = trip?.defaultTransportMode ?: TransportMode.CAR
                                    Text(
                                        "Standardverkehrsmittel: ${current.label}",
                                    )
                                },
                                onClick = {
                                    tripMenuExpanded = false
                                    defaultModePickerOpen = true
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
            // Karte: drei Fünftel, Zeitleiste: zwei Fünftel des Bildschirms.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f)
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
                            // Standard = alte Papierkarte (Sepia-Filter auf den
                            // OSM-Kacheln). Kontrast bleibt farbig und gut lesbar.
                            view.overlayManager.tilesOverlay.setColorFilter(
                                if (mode == OverlayMode.POSTER) null else paperTileFilter(),
                            )
                            appliedOverlayMode = mode
                        }

                        if (!didCenter && view.height > 0 &&
                            (stops.isNotEmpty() || photos.isNotEmpty())
                        ) {
                            zoomToRelevant(view, stops, photos)
                            didCenter = true
                        }

                        view.overlays.clear()
                        view.overlays.add(locationOverlay)

                        // Dieselben Polyline-Objekte bleiben erhalten – beim
                        // Verschieben der Karte werden die Striche nicht neu
                        // aufgebaut und springen dadurch nicht.
                        // Die Route liegt unter den Bildern: Fotos sind immer
                        // sichtbar und bekommen den Tap zuerst.
                        routeLines.forEach { view.overlays.add(it) }

                        // Polaroids bleiben auch in der Gesamtansicht sichtbar.
                        // Sie werden nur kleiner, niemals durch Clustering ersetzt.
                        view.overlays.add(polaroidOverlay)

                        markers.clear()
                        stops.forEach { stop ->
                            val marker = Marker(view)
                            marker.position = GeoPoint(stop.latitude, stop.longitude)
                            marker.title = stop.title
                            marker.snippet = ""
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
                                // osmdroid setzt den Anker selbst (unten mittig).
                                // Doppelte Offsets würden den Pin verschieben.
                                it.setBounds(0, 0, width, height)
                                marker.icon = it
                            }
                            marker.isDraggable = false
                            markers[stop.id] = marker
                            view.overlays.add(marker)
                        }
                        view.overlays.add(pinOverlay)
                        view.overlays.add(routeEditOverlay)
                        view.invalidate()
                    },
                )

                // Während der Titelbearbeitung beendet ein Tap auf die Karte
                // die Bearbeitung und speichert den eingegebenen Titel.
                if (editingTitle) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable { commitTitle() },
                    )
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PaperIconButton(
                        onClick = { showMyLocation() },
                        icon = R.drawable.ic_location,
                        contentDescription = "Standort anzeigen",
                    )
                    IconButton(
                        onClick = {
                            // An-/Abreise-Pins und ihre Inhalte bleiben auf der
                            // Karte sichtbar, bestimmen aber nicht den Ausschnitt
                            // der Gesamtansicht.
                            val outerStopIds = stops.filter { it.isArrivalDeparture }
                                .map { it.id }.toSet()
                            val outerEntryIds = buildSet {
                                stops.filter { it.isArrivalDeparture }
                                    .mapNotNull { it.entryId }.forEach(::add)
                                allEntries.filter { it.stopId in outerStopIds }
                                    .map { it.id }.forEach(::add)
                            }
                            val points = buildList {
                                stops.filterNot { it.isArrivalDeparture }.forEach { stop ->
                                    add(GeoPoint(stop.latitude, stop.longitude))
                                }
                                photos.filter {
                                    it.stopId !in outerStopIds && it.entryId !in outerEntryIds
                                }.forEach { photo ->
                                    val latitude = photo.latitude
                                    val longitude = photo.longitude
                                    if (latitude != null && longitude != null) {
                                        add(GeoPoint(latitude, longitude))
                                    }
                                }
                            }
                            if (points.isEmpty()) {
                                // Ohne Einträge nur so weit raus, dass die Karte
                                // die Höhe füllt – Mitte ist die aktuelle Position.
                                zoomOutToCurrent = true
                                showMyLocation()
                            } else {
                                // Rand um die äußeren Fotos, damit nichts abgeschnitten wird.
                                val box = BoundingBox.fromGeoPoints(points)
                                val latMargin = maxOf((box.latNorth - box.latSouth) * 0.15, 0.02)
                                val lonMargin = maxOf((box.lonEast - box.lonWest) * 0.15, 0.02)
                                val padded = BoundingBox(
                                    (box.latNorth + latMargin).coerceAtMost(85.0),
                                    (box.lonEast + lonMargin).coerceAtMost(180.0),
                                    (box.latSouth - latMargin).coerceAtLeast(-85.0),
                                    (box.lonWest - lonMargin).coerceAtLeast(-180.0),
                                )
                                mapView.zoomToBoundingBox(padded, true, 140)
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(PaperCard, CircleShape)
                            .border(2.dp, Ink, CircleShape),
                    ) {
                        // Kompassrose statt Vollbild-Symbol.
                        Icon(
                            painter = painterResource(R.drawable.ic_compass),
                            contentDescription = "Ganze Reise vollständig anzeigen",
                            tint = Color.Unspecified,
                            modifier = Modifier.size(28.dp),
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

                // Neuer Eintrag immer unten rechts, unabhängig vom gewählten Pin.
                FloatingActionButton(
                    onClick = { openNewEntry() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    // Rotes Siegel mit dunklerem Prägerand.
                    shape = CircleShape,
                    containerColor = SealRed,
                    contentColor = PaperCard,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .border(3.dp, SealRedDark, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "Neuer Eintrag",
                        )
                    }
                }
            }

            // Sichtbarer Schieberegler genau zwischen Karte und Zeitleiste.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                RouteScrollbar(
                    fraction = scrollSync.fraction,
                    onFraction = { fraction ->
                        syncSeq += 1
                        scrollSync = ScrollSync(fraction, "bar", syncSeq)
                        syncTarget = null
                        focusKey = null
                        pointAtFraction(fraction)?.let { moveMapTo(it) }
                    },
                )
            }

            // Zeitleiste: untere zwei Fünftel, von links nach rechts.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(2f)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                AdventureTimeline(
                    items = timeline,
                    stops = orderedRouteStops,
                    photosByEntry = photosByEntry,
                    entryBodies = allEntries.associate { it.id to it.body },
                    onItemClick = { item -> handleTimelineClick(item) },
                    onPhotoClick = { id -> focusPhoto(id, moveMap = true) },
                    onSegmentClick = { fromId, toId ->
                        routeSegments.firstOrNull { it.key == segmentKey(fromId, toId) }
                            ?.let { segmentDialog = SegmentDialog.Detail(it) }
                    },
                    photoBitmap = bitmapForPhoto,
                    photoTimes = photoTimes,
                    mapZoom = mapZoom,
                    externalKey = syncTarget?.key ?: focusKey,
                    externalSeq = syncTarget?.seq ?: 0L,
                    externalOrigin = syncTarget?.origin,
                    externalFraction = scrollSync.fraction,
                    externalFractionSeq = scrollSync.seq,
                    externalFractionOrigin = scrollSync.origin,
                    onPinClick = { stopId ->
                        viewModel.selectStop(stopId)
                        fullscreenKey = "stop-$stopId"
                    },
                    highlightPhotoId = highlightedPhotoId,
                    // Fortlaufende Scrollposition: Karte und Regler folgen dem
                    // Finger während des Wischens, nicht erst beim Loslassen.
                    onScrollFraction = { fraction, fromUser ->
                        if (!fromUser) return@AdventureTimeline
                        val now = System.currentTimeMillis()
                        if (now - lastTimelineMoveAt < 70L) return@AdventureTimeline
                        lastTimelineMoveAt = now
                        syncSeq += 1
                        scrollSync = ScrollSync(fraction, "timeline", syncSeq)
                        syncTarget = null
                        focusKey = null
                        pointAtFraction(fraction)?.let { moveMapTo(it) }
                    },
                    // Das mittlere Element dient nur noch der Markierung.
                    onUserCenterKey = { key -> centerKey = key },
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

    // Standardverkehrsmittel: genau ein Menüeintrag öffnet dieses Pop-up.
    if (defaultModePickerOpen) {
        PaperAlertDialog(
            onDismissRequest = { defaultModePickerOpen = false },
            title = { Text("Standardverkehrsmittel") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    TransportMode.entries.forEach { mode ->
                        TextButton(onClick = {
                            viewModel.setDefaultTransportMode(mode)
                            defaultModePickerOpen = false
                        }) {
                            TransportIcon(mode)
                            Spacer(Modifier.width(12.dp))
                            Text(mode.label)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { defaultModePickerOpen = false }) { Text("Abbrechen") }
            },
        )
    }

    // Verkehrsmittel und An-/Abreise für einen Abschnitt – nach Tap auf die Linie.
    fun segmentTitle(segment: RouteSegment): String {
        val from = stops.firstOrNull { it.id == segment.fromStopId }
        val to = stops.firstOrNull { it.id == segment.arrivalStopId }
        fun label(stop: Stop?) = stop?.title?.takeIf { it.isNotBlank() } ?: "Pin"
        return "Abschnitt: ${label(from)} → ${label(to)}"
    }

    /** Setzt genau die gewählte Endrolle und räumt die andere Seite mit auf. */
    fun applyBoundary(isAnreise: Boolean, value: Boolean) {
        val firstId = orderedRouteStops.firstOrNull()?.id
        val lastId = orderedRouteStops.lastOrNull()?.id
        val currentAnreise = firstId?.takeIf { id ->
            stops.firstOrNull { it.id == id }?.isArrivalDeparture == true
        }
        val currentAbreise = lastId?.takeIf { id ->
            id != firstId && stops.firstOrNull { it.id == id }?.isArrivalDeparture == true
        }
        val nextAnreise = if (isAnreise) firstId?.takeIf { value } else currentAnreise
        var nextAbreise = if (isAnreise) currentAbreise else lastId?.takeIf { value }
        if (nextAnreise != null && nextAnreise == nextAbreise) nextAbreise = null
        viewModel.setTripBoundaries(nextAnreise, nextAbreise)
    }

    when (val dialog = segmentDialog) {
        is SegmentDialog.Detail -> {
            // Immer den aktuellen Abschnitt benutzen, auch wenn sich die
            // Reihenfolge während des offenen Pop-ups geändert hat.
            val segment = routeSegments.firstOrNull { it.key == dialog.segment.key }
                ?: dialog.segment
            val effectiveMode = segmentModes[segment.key]
                ?: trip?.defaultTransportMode
                ?: TransportMode.CAR
            val firstStop = orderedRouteStops.firstOrNull()
            val lastStop = orderedRouteStops.lastOrNull()
            val segmentIndex = routeSegments.indexOfFirst { it.key == segment.key }
            val isCurrentSegment = segmentIndex >= 0 &&
                orderedRouteStops.getOrNull(segmentIndex)?.id == segment.fromStopId &&
                orderedRouteStops.getOrNull(segmentIndex + 1)?.id == segment.arrivalStopId
            // Der Index der Strecke entscheidet – nicht nur ein einzelner Pin.
            // Bei zwei Pins ist derselbe Abschnitt erster und letzter und bekommt
            // deshalb zwei Häkchen in einem Pop-up.
            val endpointRows = buildList<Triple<Boolean, String, String>> {
                if (isCurrentSegment && segmentIndex == 0) {
                    firstStop?.let {
                        add(Triple(true, it.id, "Anreise: erster Pin zählt nicht mit"))
                    }
                }
                if (isCurrentSegment && segmentIndex == routeSegments.lastIndex) {
                    lastStop?.takeIf { it.id != firstStop?.id }?.let {
                        add(Triple(false, it.id, "Abreise: letzter Pin zählt nicht mit"))
                    }
                }
            }
            PaperAlertDialog(
                onDismissRequest = { segmentDialog = null },
                title = { Text(segmentTitle(segment)) },
                text = {
                    Column {
                        Text(
                            "Verkehrsmittel in diesem Abschnitt",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        // Genau ein Symbol; ein Tippen öffnet die Auswahl.
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .size(34.dp)
                                .clip(RoundedCornerShape(17.dp))
                                .background(Color(0xFFE7DAC2))
                                .clickable {
                                    segmentDialog = SegmentDialog.Modes(segment)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            TransportIcon(effectiveMode, size = 24.dp)
                        }
                        endpointRows.forEach { (isAnreise, endpointId, label) ->
                            val checked = stops.firstOrNull { it.id == endpointId }
                                ?.isArrivalDeparture == true
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { applyBoundary(isAnreise, it) },
                                )
                                Text(label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { segmentDialog = null }) { Text("Fertig") }
                },
            )
        }
        is SegmentDialog.Modes -> {
            val segment = routeSegments.firstOrNull { it.key == dialog.segment.key }
                ?: dialog.segment
            PaperAlertDialog(
                onDismissRequest = {
                    segmentDialog = SegmentDialog.Detail(segment)
                },
                title = { Text(segmentTitle(segment)) },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState())) {
                        TransportMode.entries.forEach { mode ->
                            TextButton(onClick = {
                                viewModel.setSegmentMode(
                                    segment.fromStopId, segment.arrivalStopId, mode,
                                )
                                // Direktmodus ignoriert Verkehrsmittel – einmal umstellen.
                                if (trip?.routeMode == RouteMode.DIRECT) {
                                    viewModel.setRouteMode(RouteMode.AUTO)
                                }
                                segmentDialog = SegmentDialog.Detail(segment)
                            }) {
                                TransportIcon(mode)
                                Spacer(Modifier.width(12.dp))
                                Text(mode.label)
                            }
                        }
                        TextButton(onClick = {
                            viewModel.clearSegmentMode(segment.fromStopId, segment.arrivalStopId)
                            if (trip?.routeMode == RouteMode.DIRECT) {
                                viewModel.setRouteMode(RouteMode.AUTO)
                            }
                            segmentDialog = SegmentDialog.Detail(segment)
                        }) {
                            Text("Standard der Reise")
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        segmentDialog = SegmentDialog.Detail(segment)
                    }) { Text("Zurück") }
                },
            )
        }
        null -> Unit
    }

    if (coverPickerOpen) {
        PaperAlertDialog(
            onDismissRequest = { coverPickerOpen = false },
            title = { Text("Titelbild wählen") },
            text = {
                Column {
                    Text(
                        "Ein Foto antippen. Es erscheint danach leicht sichtbar im " +
                            "Hintergrund der Reisekarte auf der Startseite.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (photos.isEmpty()) {
                        Text("Diese Reise hat noch keine Fotos.")
                    } else {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            photos.forEach { photo ->
                                val bitmap = thumbnails[photo.id]
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (photo.id == trip?.coverPhotoId) {
                                                Color(0xFFB8782F)
                                            } else {
                                                Color(0xFFE7DAC2)
                                            },
                                        )
                                        .clickable {
                                            viewModel.setCoverPhoto(photo.id)
                                            coverPickerOpen = false
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription =
                                                photo.caption.ifBlank { "Foto" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(68.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { coverPickerOpen = false }) { Text("Schließen") }
            },
            dismissButton = {
                if (trip?.coverPhotoId != null) {
                    TextButton(onClick = {
                        viewModel.clearCoverPhoto()
                        coverPickerOpen = false
                    }) { Text("Titelbild entfernen") }
                }
            },
        )
    }

    waypointToDelete?.let { id ->
        PaperAlertDialog(
            onDismissRequest = { waypointToDelete = null },
            title = { Text("Routenpunkt") },
            text = { Text("Diesen selbst gesetzten Punkt von der Linie entfernen?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteWaypoint(id)
                    waypointToDelete = null
                }) { Text("Entfernen") }
            },
            dismissButton = {
                TextButton(onClick = { waypointToDelete = null }) { Text("Abbrechen") }
            },
        )
    }

    photoToDelete?.let { id ->
        PaperAlertDialog(
            onDismissRequest = { photoToDelete = null },
            title = { Text("Foto löschen?") },
            text = {
                Text(
                    "Das Foto wird von der Karte und aus seinem Eintrag entfernt. " +
                        "Der Eintrag selbst bleibt erhalten.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deletePhoto(id)
                    if (highlightedPhotoId == id) highlightedPhotoId = null
                    if (focusKey == photoKey(id)) focusKey = null
                    if (fullscreenKey == "photo-$id") fullscreenKey = null
                    stackPhotoIds = stackPhotoIds.filterNot { it == id }
                    photoToDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = {
                TextButton(onClick = { photoToDelete = null }) { Text("Abbrechen") }
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
            onAddEntry = { stopId -> openNewEntry(stopId) },
            onAdoptPhotoLocation = { id -> viewModel.adoptPhotoLocation(id) },
        )
    }
}

private fun focusStop(mapView: MapView, latitude: Double, longitude: Double) {
    // Kein Zoomsprung: die Karte behält ihren Maßstab und bewegt sich nur weich.
    mapView.controller.animateTo(
        GeoPoint(latitude, longitude),
        mapView.zoomLevelDouble,
        450L,
    )
}

/** Kleinster Zoom, bei dem die Weltkartenhöhe den Kartenbildschirm füllt. */
private fun fillZoom(mapView: MapView): Double {
    val heightPx = mapView.height.takeIf { it > 0 }?.toDouble() ?: 512.0
    return maxOf(1.0, log2(heightPx / 256.0))
}

/** Beim Öffnen der Reise alle Pins und platzierten Fotos mit Rand einpassen. */
private fun zoomToRelevant(view: MapView, stops: List<Stop>, photos: List<Photo>) {
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
    if (points.isEmpty()) return
    val box = BoundingBox.fromGeoPoints(points)
    val latMargin = maxOf((box.latNorth - box.latSouth) * 0.15, 0.02)
    val lonMargin = maxOf((box.lonEast - box.lonWest) * 0.15, 0.02)
    val padded = BoundingBox(
        (box.latNorth + latMargin).coerceAtMost(85.0),
        (box.lonEast + lonMargin).coerceAtMost(180.0),
        (box.latSouth - latMargin).coerceAtLeast(-85.0),
        (box.lonWest - lonMargin).coerceAtLeast(-180.0),
    )
    view.zoomToBoundingBox(padded, true, 140)
}

/** Aktuelle Position weich in die Mitte bringen. */
private fun focusCurrentPosition(
    mapView: MapView,
    locationOverlay: MyLocationNewOverlay,
    zoom: Double,
) {
    val fix = locationOverlay.lastFix
    if (fix != null) {
        mapView.controller.animateTo(
            GeoPoint(fix.latitude, fix.longitude), zoom, 600L,
        )
    } else {
        locationOverlay.runOnFirstFix {
            mapView.post {
                locationOverlay.myLocation?.let { point ->
                    mapView.controller.animateTo(point, zoom, 600L)
                }
            }
        }
    }
}
