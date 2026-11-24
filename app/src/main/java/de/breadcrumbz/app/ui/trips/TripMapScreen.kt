package de.breadcrumbz.app.ui.trips

import android.Manifest
import android.graphics.BitmapFactory
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.PolaroidOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripMapScreen(
    viewModel: TripMapViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)

    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val pendingPhotoId by viewModel.pendingPhotoId.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var didCenter by remember { mutableStateOf(false) }
    var showPhotos by remember { mutableStateOf(false) }
    var selectedPhotoId by remember { mutableStateOf<String?>(null) }

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
                            onPhotoClick = { id -> selectedPhotoId = id },
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

            if (pendingPhotoId != null) {
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
                        Text("Jetzt lange auf die Karte drücken")
                        TextButton(onClick = { viewModel.cancelPlacement() }) {
                            Text("Abbrechen")
                        }
                    }
                }
            }

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(trip?.title ?: "Reise", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stops.size} Wegpunkte · ${photos.size} Fotos · " +
                            String.format(Locale.GERMANY, "%.1f", distanceKm) + " km (Luftlinie)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Lange drücken = Punkt setzen. Foto-Pins antippen = Bild ansehen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        TextButton(onClick = { startPhotoImport() }) {
                            Text("Fotos importieren")
                        }
                        TextButton(onClick = { showPhotos = !showPhotos }) {
                            Text(if (showPhotos) "Fotos ausblenden" else "Fotos anzeigen")
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
                    Text(
                        selectedPhoto.capturedAt?.let {
                            SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date(it))
                        } ?: "Kein Aufnahmedatum",
                    )
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
                text = photo.capturedAt?.let {
                    SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(it))
                } ?: "Ohne Datum",
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
