package de.breadcrumbz.app.ui.trips

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.state.TripMapViewModel
import org.osmdroid.events.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
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
    val distanceKm by viewModel.distanceKm.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val mapView = remember {
        MapView(context).apply {
            setTileSource(standardTiles())
            setMultiTouchControls(true)
            controller.setZoom(14.0)
            controller.setCenter(GeoPoint(51.0, 10.0))
        }
    }

    var didCenter by remember { mutableStateOf(false) }
    var selectedStopId by remember { mutableStateOf<String?>(null) }

    val tapOverlay = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint?): Boolean = false

            override fun longPressHelper(point: GeoPoint?): Boolean {
                if (point != null) {
                    viewModel.addStop(point.latitude, point.longitude)
                }
                return true
            }
        })
    }

    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDestroy()
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
                            selectedStopId = stopId
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

            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(trip?.title ?: "Reise", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${stops.size} Wegpunkte · " +
                            String.format(Locale.GERMANY, "%.1f", distanceKm) + " km (Luftlinie)",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Lange drücken = neuer Punkt. Punkt ziehen = Route ändern.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (selectedStopId != null) {
                        TextButton(onClick = {
                            viewModel.deleteStop(selectedStopId!!)
                            selectedStopId = null
                        }) {
                            Text("Ausgewählten Punkt löschen")
                        }
                    }
                }
            }
        }
    }
}
