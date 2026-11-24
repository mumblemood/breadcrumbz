package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.roundToInt

data class ClusterElement(val latitude: Double, val longitude: Double, val key: String)

/**
 * Bei starkem Zoom-out legen sich nahe Pins und Bilder zusammen. Ein Kreis
 * zeigt die Anzahl; ein Tipp zoomt auf die Gruppe.
 */
class ClusterOverlay(
    private val elements: List<ClusterElement>,
    private val onClusterClick: (BoundingBox) -> Unit,
) : Overlay() {

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D99B52")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3B2B20")
        strokeWidth = 4f
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 40f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val groups = mutableListOf<Pair<RectF, List<ClusterElement>>>()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        groups.clear()

        val cell = 110
        val buckets = elements.groupBy { element ->
            val point = mapView.projection.toPixels(
                GeoPoint(element.latitude, element.longitude),
                null,
            )
            "${Math.round(point.x / cell.toDouble())}:${Math.round(point.y / cell.toDouble())}"
        }

        buckets.values.filter { it.size > 1 }.forEach { group ->
            val latitude = group.map { it.latitude }.average()
            val longitude = group.map { it.longitude }.average()
            val point = mapView.projection.toPixels(GeoPoint(latitude, longitude), null)
            val radius = (28f + group.size * 2f).coerceAtMost(58f)
            val rect = RectF(
                point.x - radius,
                point.y - radius,
                point.x + radius,
                point.y + radius,
            )
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, circlePaint)
            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, borderPaint)
            val label = group.size.toString()
            val baseline = point.y - (textPaint.descent() + textPaint.ascent()) / 2
            canvas.drawText(label, point.x.toFloat(), baseline, textPaint)
            groups.add(rect to group)
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = groups.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                val points = hit.second.map { GeoPoint(it.latitude, it.longitude) }
                onClusterClick(BoundingBox.fromGeoPoints(points))
                return true
            }
        }
        return super.onTouchEvent(event, mapView)
    }
}
