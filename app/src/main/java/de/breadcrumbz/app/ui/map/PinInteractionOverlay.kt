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
 * Ein Pin wird zuerst durch Tippen ausgewählt. Erst wenn er ausgewählt ist,
 * lässt er sich durch zwei Sekunden Halten lösen und verschieben. Während
 * eines Pinch-Zooms wird gar nichts ausgewählt.
 */
class PinInteractionOverlay(
    private val markers: Map<String, Marker>,
    private val stops: List<Stop>,
    private val selectedStopId: String?,
    private val onTap: (String) -> Unit,
    private val onMove: (String, Double, Double) -> Unit,
) : Overlay() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingStopId: String? = null
    private var candidateStopId: String? = null
    private var draggingStopId: String? = null
    private var dragOriginal: GeoPoint? = null
    private var multiTouch = false
    private var ignoreTapsUntilRelease = false
    private var lastDownX = 0f
    private var lastDownY = 0f
    private var mapViewRef: MapView? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x88D99B52.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    private val unlock = Runnable {
        val id = pendingStopId
        if (id != null) {
            val marker = markers[id]
            dragOriginal = marker?.position?.let { GeoPoint(it.latitude, it.longitude) }
            draggingStopId = id
            pendingStopId = null
            mapViewRef?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            mapViewRef?.invalidate()
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
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            lastDownX = event.x
            lastDownY = event.y
        }

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                mapViewRef = mapView
                candidateStopId = null
                if (ignoreTapsUntilRelease || event.pointerCount > 1) return true
                val hit = hitStop(event, mapView) ?: return false
                candidateStopId = hit.id
                // Nur der bereits ausgewählte Pin darf durch Halten losgelöst werden.
                if (hit.id == selectedStopId) {
                    pendingStopId = hit.id
                    handler.postDelayed(unlock, 2_000L)
                }
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                // Pinch/Zweitfinger: Auswahl und laufendes Ziehen sofort abbrechen.
                ignoreTapsUntilRelease = true
                multiTouch = true
                cancel(restoreMarker = true)
                candidateStopId = null
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (multiTouch || event.pointerCount > 1) {
                    cancel(restoreMarker = true)
                    return true
                }
                val id = draggingStopId
                if (id != null) {
                    val marker = markers[id]
                    val geo = mapView.projection.fromPixels(
                        event.x.toInt(),
                        event.y.toInt(),
                    )
                    if (marker != null && geo != null) {
                        marker.position = GeoPoint(geo.latitude, geo.longitude)
                        mapView.invalidate()
                    }
                    return true
                }
                if (pendingStopId != null &&
                    (abs(event.x - lastDownX) > 12f || abs(event.y - lastDownY) > 12f)
                ) {
                    cancel(restoreMarker = false)
                }
            }

            MotionEvent.ACTION_POINTER_UP -> {
                multiTouch = event.pointerCount > 2
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (ignoreTapsUntilRelease || multiTouch) {
                    ignoreTapsUntilRelease = false
                    multiTouch = false
                    cancel(restoreMarker = true)
                    candidateStopId = null
                    return true
                }
                handler.removeCallbacks(unlock)
                val dragging = draggingStopId
                if (dragging != null) {
                    val marker = markers[dragging]
                    if (marker != null) {
                        onMove(
                            dragging,
                            marker.position.latitude,
                            marker.position.longitude,
                        )
                    }
                    draggingStopId = null
                    dragOriginal = null
                    mapView.invalidate()
                    return true
                }
                val tapped = pendingStopId ?: candidateStopId
                if (tapped != null) {
                    mapView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap(tapped)
                }
                pendingStopId = null
                candidateStopId = null
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                ignoreTapsUntilRelease = false
                multiTouch = false
                cancel(restoreMarker = true)
                candidateStopId = null
                return true
            }
        }
        return super.onTouchEvent(event, mapView)
    }

    private fun hitStop(event: MotionEvent, mapView: MapView): Stop? {
        val projection = mapView.projection
        return stops.firstOrNull { stop ->
            val point = projection.toPixels(
                GeoPoint(stop.latitude, stop.longitude),
                null,
            )
            abs(point.x - event.x) < 48f && abs(point.y - event.y) < 64f
        }
    }

    private fun cancel(restoreMarker: Boolean) {
        handler.removeCallbacks(unlock)
        pendingStopId = null
        val dragging = draggingStopId ?: return
        if (restoreMarker) {
            val original = dragOriginal
            if (original != null) {
                markers[dragging]?.position = GeoPoint(original.latitude, original.longitude)
            }
        }
        draggingStopId = null
        dragOriginal = null
        mapViewRef?.invalidate()
    }
}
