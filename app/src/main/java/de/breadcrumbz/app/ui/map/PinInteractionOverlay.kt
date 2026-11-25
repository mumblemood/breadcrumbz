package de.breadcrumbz.app.ui.map

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
 * Pins entstehen durch zwei Sekunden Halten auf freier Fläche. Ein Tippen wählt
 * einen Pin aus; ein Tippen ins Leere hebt die Auswahl auf. Nur der ausgewählte
 * Pin lässt sich nach zwei Sekunden Halten ziehen. Während eines Pinch-Zooms
 * passiert gar nichts.
 */
class PinInteractionOverlay(
    private val markers: Map<String, Marker>,
    private val stops: List<Stop>,
    private val selectedStopId: String?,
    private val allowCreate: Boolean,
    private val onTap: (String) -> Unit,
    private val onEmptyTap: () -> Unit,
    private val onCreateStop: (Double, Double) -> Unit,
    /** Letzter Wert: sollen Einträge und Bilder mitwandern? */
    private val onMove: (String, Double, Double, Boolean) -> Unit,
    /** Foto-Polaroids sollen kein Halten-auf-freier-Fläche auslösen. */
    private val ignoreLongPress: (Float, Float) -> Boolean = { _, _ -> false },
    /** Pin liegt an dieser Stelle unter einem Foto – dann gehört der Tap dem Foto. */
    private val isPinHidden: (String, Float, Float) -> Boolean = { _, _, _ -> false },
) : Overlay() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingStopId: String? = null
    private var candidateStopId: String? = null
    private var draggingStopId: String? = null
    private var dragOriginal: GeoPoint? = null
    private var pendingCreate = false
    private var createdStop = false
    private var downGeo: GeoPoint? = null
    private var multiTouch = false
    private var ignoreTapsUntilRelease = false
    private var lastDownX = 0f
    private var lastDownY = 0f
    private var gestureMoved = false
    private var mapViewRef: MapView? = null

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

    private val create = Runnable {
        val geo = downGeo
        if (pendingCreate && allowCreate && geo != null && !multiTouch &&
            !ignoreTapsUntilRelease
        ) {
            pendingCreate = false
            createdStop = true
            mapViewRef?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onCreateStop(geo.latitude, geo.longitude)
            mapViewRef?.invalidate()
        }
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
                pendingStopId = null
                createdStop = false
                gestureMoved = false
                multiTouch = false
                ignoreTapsUntilRelease = false
                if (event.pointerCount > 1) return false

                downGeo = mapView.projection.fromPixels(
                    event.x.toInt(),
                    event.y.toInt(),
                )?.let { GeoPoint(it.latitude, it.longitude) }

                val hit = hitStop(event, mapView)
                if (hit != null) {
                    candidateStopId = hit.id
                    // Nur der bereits ausgewählte Pin darf durch Halten losgelöst werden.
                    if (hit.id == selectedStopId &&
                        !ignoreLongPress(event.x, event.y)
                    ) {
                        pendingStopId = hit.id
                        handler.postDelayed(unlock, 2_000L)
                    }
                } else if (allowCreate && !ignoreLongPress(event.x, event.y)) {
                    pendingCreate = true
                    handler.postDelayed(create, 2_000L)
                }
                // Wichtig: DOWN nie verbrauchen. Sonst bekommt der
                // MultiTouchController von osmdroid kein Pinch-Zoom.
                return false
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                ignoreTapsUntilRelease = true
                multiTouch = true
                handler.removeCallbacks(create)
                pendingCreate = false
                cancel(restoreMarker = true)
                candidateStopId = null
                // Zweiter Finger: ebenfalls durchreichen für Pinch-Zoom.
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (multiTouch || event.pointerCount > 1) {
                    cancel(restoreMarker = true)
                    handler.removeCallbacks(create)
                    pendingCreate = false
                    return false
                }
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
                val moved = abs(event.x - lastDownX) > 12f ||
                    abs(event.y - lastDownY) > 12f
                if (moved) gestureMoved = true
                if (pendingStopId != null && moved) cancel(restoreMarker = false)
                if (pendingCreate && moved) {
                    handler.removeCallbacks(create)
                    pendingCreate = false
                }
                return false
            }

            MotionEvent.ACTION_POINTER_UP -> {
                multiTouch = event.pointerCount > 2
                return false
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(unlock)
                handler.removeCallbacks(create)
                if (ignoreTapsUntilRelease || multiTouch) {
                    ignoreTapsUntilRelease = false
                    multiTouch = false
                    cancel(restoreMarker = true)
                    candidateStopId = null
                    pendingCreate = false
                    return false
                }
                val dragging = draggingStopId
                if (dragging != null) {
                    val marker = markers[dragging]
                    if (marker != null) {
                        // Pin ohne tbe: nur der Pin zieht um, die Bilder bleiben.
                        val moveLinkedContent =
                            stops.firstOrNull { it.id == dragging }?.entryId != null
                        onMove(
                            dragging,
                            marker.position.latitude,
                            marker.position.longitude,
                            moveLinkedContent,
                        )
                    }
                    draggingStopId = null
                    dragOriginal = null
                    mapView.invalidate()
                    return true
                }
                if (createdStop) {
                    createdStop = false
                    return true
                }
                val tapped = pendingStopId ?: candidateStopId
                if (tapped != null) {
                    mapView.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onTap(tapped)
                    pendingStopId = null
                    candidateStopId = null
                    return true
                }
                pendingCreate = false
                candidateStopId = null
                if (!gestureMoved) onEmptyTap()
                return false
            }

            MotionEvent.ACTION_CANCEL -> {
                ignoreTapsUntilRelease = false
                multiTouch = false
                handler.removeCallbacks(create)
                pendingCreate = false
                cancel(restoreMarker = true)
                candidateStopId = null
                return false
            }
        }
        return false
    }

    private fun hitStop(event: MotionEvent, mapView: MapView): Stop? {
        val projection = mapView.projection
        val density = mapView.resources.displayMetrics.density
        return stops.firstOrNull { stop ->
            val point = projection.toPixels(
                GeoPoint(stop.latitude, stop.longitude),
                null,
            )
            // Tippen auf den ganzen Pin, nicht nur auf die Spitze.
            val scale = if (stop.id == selectedStopId) 1.5f else 1f
            val half = 14f * density * scale
            val height = 28f * density * scale
            abs(point.x - event.x) < half + 8f * density &&
                event.y >= point.y - height - 8f * density &&
                event.y <= point.y + 8f * density &&
                !isPinHidden(stop.id, event.x, event.y)
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
