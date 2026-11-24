package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import de.breadcrumbz.app.domain.model.Stop
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

/**
 * Lässt einen Pin erst nach zwei Sekunden gedrückt halten loswerden. Danach
 * kann er per Drag & Drop verschoben werden. Vorher gilt ein Tippen als Auswahl.
 */
class PinInteractionOverlay(
    private val markers: Map<String, Marker>,
    private val stops: List<Stop>,
    private val onTap: (String) -> Unit,
    private val onMove: (String, Double, Double) -> Unit,
) : Overlay() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingStopId: String? = null
    private var draggingStopId: String? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88D99B52.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private var mapViewRef: MapView? = null

    private val unlock = Runnable {
        val id = pendingStopId
        if (id != null) {
            draggingStopId = id
            pendingStopId = null
            mapViewRef?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val id = draggingStopId ?: return
        val marker = markers[id] ?: return
        val point = mapView.projection.toPixels(marker.position, null)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 34f, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                mapViewRef = mapView
                val projection = mapView.projection
                val hit = stops.firstOrNull { stop ->
                    val point = projection.toPixels(
                        GeoPoint(stop.latitude, stop.longitude),
                        null,
                    )
                    abs(point.x - event.x) < 48f && abs(point.y - event.y) < 64f
                }
                if (hit != null) {
                    pendingStopId = hit.id
                    handler.postDelayed(unlock, 2_000L)
                    return true
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val id = draggingStopId
                if (id != null) {
                    val marker = markers[id]
                    val geo = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                    if (marker != null && geo != null) {
                        marker.position = GeoPoint(geo.latitude, geo.longitude)
                        mapView.invalidate()
                    }
                    return true
                }
                // Wischen vor Ablauf der zwei Sekunden bricht die Auswahl ab.
                if (pendingStopId != null &&
                    (abs(event.x - lastDownX) > 12f || abs(event.y - lastDownY) > 12f)
                ) {
                    handler.removeCallbacks(unlock)
                    pendingStopId = null
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(unlock)
                val dragging = draggingStopId
                if (dragging != null) {
                    val marker = markers[dragging]
                    if (marker != null && event.action == MotionEvent.ACTION_UP) {
                        onMove(
                            dragging,
                            marker.position.latitude,
                            marker.position.longitude,
                        )
                    }
                    draggingStopId = null
                    mapView.invalidate()
                    return true
                }
                val pending = pendingStopId
                if (pending != null && event.action == MotionEvent.ACTION_UP) {
                    mapView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap(pending)
                    pendingStopId = null
                    return true
                }
                pendingStopId = null
            }
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            lastDownX = event.x
            lastDownY = event.y
        }
        return super.onTouchEvent(event, mapView)
    }

    private var lastDownX = 0f
    private var lastDownY = 0f
}
