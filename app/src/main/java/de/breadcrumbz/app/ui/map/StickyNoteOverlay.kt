package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Stop
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/** Kleiner Zettel für Tagebucheinträge ohne Bild, an den Stopp geheftet. */
class StickyNoteOverlay(
    private val entries: List<Entry>,
    private val stops: List<Stop>,
    private val onEntryClick: (String) -> Unit,
) : Overlay() {

    private val hitAreas = mutableListOf<Pair<RectF, String>>()

    private val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F6E27A")
    }
    private val foldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D8BE58")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6B5533")
        strokeWidth = 3f
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#8A6E3C")
        strokeWidth = 2f
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        hitAreas.clear()
        val byId = stops.associateBy { it.id }
        val projection = mapView.projection

        entries.forEach { entry ->
            val stop = byId[entry.stopId] ?: return@forEach
            val point = projection.toPixels(GeoPoint(stop.latitude, stop.longitude), null)
            val size = 62f
            val left = point.x - size / 2 + 34f
            val top = point.y - size - 26f
            val rect = RectF(left, top, left + size, top + size)

            canvas.save()
            canvas.translate(left, top)
            canvas.rotate(-6f, size / 2, size / 2)
            canvas.drawRect(0f, 0f, size, size, notePaint)
            val fold = Path().apply {
                moveTo(size - 16f, 0f)
                lineTo(size, 16f)
                lineTo(size - 16f, 16f)
                close()
            }
            canvas.drawPath(fold, foldPaint)
            canvas.drawRect(0f, 0f, size, size, borderPaint)
            canvas.drawLine(10f, 26f, size - 12f, 26f, linePaint)
            canvas.drawLine(10f, 38f, size - 20f, 38f, linePaint)
            canvas.drawLine(10f, 50f, size - 26f, 50f, linePaint)
            canvas.restore()

            canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), 5f, pinPaint)
            hitAreas.add(rect to entry.id)
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = hitAreas.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                onEntryClick(hit.second)
                return true
            }
        }
        return super.onTouchEvent(event, mapView)
    }
}
