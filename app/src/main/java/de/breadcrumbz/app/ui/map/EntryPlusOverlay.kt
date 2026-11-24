package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.hypot

/**
 * Kleines Plus über einem ausgewählten Pin, der noch keinen Eintrag hat.
 * Es wird direkt auf der Karte gezeichnet und auch dort angetappt.
 */
class EntryPlusOverlay(
    private val position: GeoPoint,
    private val onClick: () -> Unit,
) : Overlay() {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBF4E4")
    }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.parseColor("#3B2B20")
    }
    private val plus = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        textAlign = Paint.Align.CENTER
        textSize = 36f
        typeface = Typeface.DEFAULT_BOLD
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val point = mapView.projection.toPixels(position, null)
        val x = point.x.toFloat()
        val y = point.y.toFloat() - OFFSET
        canvas.drawCircle(x, y, RADIUS, fill)
        canvas.drawCircle(x, y, RADIUS, outline)
        canvas.drawText("+", x, y + 12f, plus)
    }

    private fun isHit(event: MotionEvent, mapView: MapView): Boolean {
        val point = mapView.projection.toPixels(position, null)
        val centerX = point.x.toFloat()
        val centerY = point.y.toFloat() - OFFSET
        return hypot(event.x - centerX, event.y - centerY) <= HIT
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && isHit(event, mapView)) {
            return true
        }
        if (event.actionMasked == MotionEvent.ACTION_UP && isHit(event, mapView)) {
            onClick()
            return true
        }
        return super.onTouchEvent(event, mapView)
    }

    private companion object {
        const val OFFSET = 72f
        const val RADIUS = 24f
        const val HIT = 30f
    }
}
