package de.breadcrumbz.app.ui.trips

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.ui.map.RouteStyling
import de.breadcrumbz.app.ui.map.TrackOverlay
import de.breadcrumbz.app.ui.map.TrackPath
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Echte Karte für die Reise-Vorschau auf der Hauptseite: alte Papierkarte
 * mit derselben Linie wie im Kartenbildschirm und kleinen roten Pins.
 * Sie lässt sich nicht bewegen; Tippen öffnet die Reise (das übernimmt die Karte darüber).
 */
@Composable
fun TripMiniMap(
    stops: List<Stop>,
    segmentModes: Map<String, TransportMode>,
    defaultMode: TransportMode,
    routeMode: RouteMode,
    /** Links liegt das Titelbild: dann rückt die Route nach rechts. */
    leaveRoomLeft: Boolean,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                setTileSource(standardTiles())
                overlayManager.tilesOverlay.setColorFilter(paperTileFilter())
                setMultiTouchControls(false)
                isClickable = false
                isFocusable = false
                zoomController.setVisibility(
                    org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
                )
                // Gesten gehen an die Karte darüber (öffnen/bearbeiten).
                setOnTouchListener { _, _ -> true }
                minZoomLevel = 1.0
                maxZoomLevel = 16.0
                controller.setZoom(4.0)
                controller.setCenter(GeoPoint(51.0, 10.0))
            }
        },
        onRelease = { it.onDetach() },
        update = { view ->
            val key = listOf(stops, segmentModes, defaultMode, routeMode, leaveRoomLeft)
            if (view.tag == key) return@AndroidView
            view.tag = key
            view.overlays.clear()
            val tracks = mutableListOf<TrackPath>()
            view.overlays.addAll(
                RouteStyling.build(
                    mapView = view,
                    stops = stops,
                    defaultMode = defaultMode,
                    routeMode = routeMode,
                    segmentModes = segmentModes,
                    trackPaths = tracks,
                ),
            )
            view.overlays.add(TrackOverlay(tracks))
            view.overlays.add(StopDots(stops))
            fit(view, stops, leaveRoomLeft)
            view.invalidate()
        },
    )
}

private fun fit(view: MapView, allStops: List<Stop>, leaveRoomLeft: Boolean) {
    if (allStops.isEmpty()) return
    // An- und Abreise zählen nicht zum Ausschnitt; ihre Linien laufen
    // einfach aus dem Bild heraus.
    val stops = allStops.filterNot { it.isArrivalDeparture }.ifEmpty { allStops }
    val apply = {
        val points = stops.map { GeoPoint(it.latitude, it.longitude) }
        if (points.size == 1 || points.all { it.distanceToAsDouble(points.first()) < 200.0 }) {
            view.controller.setZoom(12.0)
            view.controller.setCenter(points.first())
        } else {
            val box = BoundingBox.fromGeoPoints(points)
            val width = (box.lonEast - box.lonWest).coerceAtLeast(0.002)
            val height = (box.latNorth - box.latSouth).coerceAtLeast(0.002)
            // Rand rundherum, links zusätzlich Platz für das Polaroid.
            val west = box.lonWest - width * (if (leaveRoomLeft) 1.0 else 0.18)
            val padded = BoundingBox(
                (box.latNorth + height * 0.22).coerceAtMost(85.0),
                (box.lonEast + width * 0.18).coerceAtMost(180.0),
                (box.latSouth - height * 0.22).coerceAtLeast(-85.0),
                west.coerceAtLeast(-180.0),
            )
            view.zoomToBoundingBox(padded, false, 0)
        }
    }
    if (view.width > 0 && view.height > 0) apply() else view.addOnFirstLayoutListener { _, _, _, _, _ -> apply() }
}

/** Kleine rote Siegelpunkte an den Pins, An-/Abreise blasser. */
private class StopDots(private val stops: List<Stop>) : Overlay() {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#9E3B2E") }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val density = mapView.resources.displayMetrics.density
        ring.strokeWidth = 1.5f * density
        stops.forEach { stop ->
            val point = mapView.projection.toPixels(GeoPoint(stop.latitude, stop.longitude), null)
            val radius = (if (stop.isArrivalDeparture) 3.5f else 5f) * density
            val alpha = if (stop.isArrivalDeparture) 140 else 255
            fill.alpha = alpha
            ring.alpha = alpha
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, fill)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, ring)
        }
    }
}
