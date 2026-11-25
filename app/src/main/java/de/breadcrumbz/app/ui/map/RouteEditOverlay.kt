package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import de.breadcrumbz.app.domain.model.RouteWaypoint
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Kleine Griffe auf der Reiselinie.
 *
 * - Ein Griff wird sofort gezogen und bleibt nach dem Loslassen gespeichert.
 * - Langes Halten auf der Linie nimmt einen beliebigen Punkt; ziehen und
 *   loslassen setzt dort einen neuen Zwischenpunkt für die Routenführung.
 */
class RouteEditOverlay(
    private val mapView: MapView,
    private val waypoints: List<RouteWaypoint>,
    /** Abschnitt (Ankunfts-Pin) mit seinen Linienpunkten. */
    private val segments: List<Pair<String, List<GeoPoint>>>,
    private val onMove: (String, Double, Double) -> Unit,
    private val onCreate: (String?, Double, Double) -> Unit,
    private val onTapWaypoint: (String) -> Unit,
) : Overlay() {

    private val whitePaint = Paint().apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val accentPaint = Paint().apply {
        color = 0xFFB8782F.toInt()
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    private val handler = Handler(Looper.getMainLooper())

    private var draggingId: String? = null
    private var creatingInSegment: String? = null
    private var dragGeo: GeoPoint? = null
    private var downX = 0f
    private var downY = 0f
    private var longPressPending = false
    private var tapCandidate: String? = null
    private var movedWhileDragging = false

    private fun screenPoint(point: GeoPoint): Pair<Float, Float> {
        val pixels = mapView.projection.toPixels(point, null)
        return pixels.x.toFloat() to pixels.y.toFloat()
    }

    private fun near(x: Float, y: Float, point: GeoPoint, radius: Float): Boolean {
        val (px, py) = screenPoint(point)
        return hypot(px - x, py - y) <= radius
    }

    /** Damit langes Halten auf der Linie keinen neuen Pin erzeugt. */
    fun isRouteAt(x: Float, y: Float): Boolean =
        segments.any { (_, points) -> points.any { near(x, y, it, 28f) } }

    private fun segmentAt(x: Float, y: Float): String? =
        segments.firstOrNull { (_, points) -> points.any { near(x, y, it, 28f) } }?.first

    private val startGrab = Runnable {
        if (!longPressPending) return@Runnable
        longPressPending = false
        dragGeo?.let { creatingInSegment = segmentAt(downX, downY) }
        tapCandidate = null
        mapView.invalidate()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        waypoints.forEach { waypoint ->
            val geo = if (waypoint.id == draggingId && dragGeo != null) {
                dragGeo
            } else {
                GeoPoint(waypoint.latitude, waypoint.longitude)
            } ?: return@forEach
            val (x, y) = screenPoint(geo)
            canvas.drawCircle(x, y, 11f, whitePaint)
            canvas.drawCircle(x, y, 7f, accentPaint)
        }
        // Während des Ziehens aus der Linie ist der neue Punkt schon sichtbar.
        if (creatingInSegment != null) {
            dragGeo?.let { geo ->
                val (x, y) = screenPoint(geo)
                canvas.drawCircle(x, y, 11f, whitePaint)
                canvas.drawCircle(x, y, 7f, accentPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = waypoints.firstOrNull { near(event.x, event.y, GeoPoint(it.latitude, it.longitude), 26f) }
                downX = event.x
                downY = event.y
                tapCandidate = hit?.id
                if (hit != null) {
                    draggingId = hit.id
                    dragGeo = GeoPoint(hit.latitude, hit.longitude)
                    movedWhileDragging = false
                    mapView.invalidate()
                    return true
                }
                val segment = segmentAt(event.x, event.y)
                if (segment != null) {
                    val touched = mapView.projection.fromPixels(
                        event.x.toInt(), event.y.toInt(),
                    )
                    dragGeo = GeoPoint(touched.latitude, touched.longitude)
                    longPressPending = true
                    handler.postDelayed(startGrab, 600L)
                }
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (draggingId != null || creatingInSegment != null) {
                    if (draggingId != null) movedWhileDragging = true
                    val moved = mapView.projection.fromPixels(
                        event.x.toInt(), event.y.toInt(),
                    )
                    dragGeo = GeoPoint(moved.latitude, moved.longitude)
                    tapCandidate = null
                    mapView.invalidate()
                    return true
                }
                if (longPressPending &&
                    hypot(event.x - downX, event.y - downY) > 12f
                ) {
                    longPressPending = false
                    handler.removeCallbacks(startGrab)
                    dragGeo = null
                }
                return false
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(startGrab)
                val geo = dragGeo
                val id = draggingId
                val created = creatingInSegment
                if (id != null) {
                    draggingId = null
                    dragGeo = null
                    tapCandidate = null
                    if (!movedWhileDragging) {
                        onTapWaypoint(id)
                    } else if (geo != null) {
                        onMove(id, geo.latitude, geo.longitude)
                    }
                    movedWhileDragging = false
                    mapView.invalidate()
                    return true
                }
                if (created != null) {
                    creatingInSegment = null
                    dragGeo = null
                    if (geo != null) onCreate(created, geo.latitude, geo.longitude)
                    mapView.invalidate()
                    return true
                }
                if (longPressPending && tapCandidate != null) {
                    onTapWaypoint(tapCandidate!!)
                }
                longPressPending = false
                tapCandidate = null
                dragGeo = null
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(startGrab)
                draggingId = null
                creatingInSegment = null
                longPressPending = false
                tapCandidate = null
                dragGeo = null
                mapView.invalidate()
                return false
            }
        }
        return false
    }
}
