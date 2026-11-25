package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import android.os.SystemClock
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Ein kleines Flugzeug fliegt auf jedem Flug-Abschnitt der Linie mit:
 * vom Start zum Ziel, dann beginnt es von vorn. Die Nase zeigt immer in
 * Flugrichtung.
 */
class PlaneOverlay(
    private val paths: List<List<GeoPoint>>,
    private val icon: Drawable?,
) : Overlay() {

    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBF4E4")
    }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || paths.isEmpty() || icon == null) return
        val density = mapView.resources.displayMetrics.density
        val projection = mapView.projection
        // Eine Runde dauert sechs Sekunden.
        val fraction = (SystemClock.uptimeMillis() % 6_000L) / 6_000f
        val iconSize = (24f * density).toInt()
        ringPaint.strokeWidth = 1.5f * density

        paths.forEach { path ->
            if (path.size < 2) return@forEach
            val pixels = path.map { projection.toPixels(it, null) }
            val lengths = FloatArray(pixels.size - 1) { index ->
                hypot(
                    (pixels[index + 1].x - pixels[index].x).toFloat(),
                    (pixels[index + 1].y - pixels[index].y).toFloat(),
                )
            }
            val total = lengths.sum()
            if (total < 1f) return@forEach
            var remaining = total * fraction
            var segment = 0
            while (segment < lengths.size - 1 && remaining > lengths[segment]) {
                remaining -= lengths[segment]
                segment++
            }
            val start = pixels[segment]
            val end = pixels[segment + 1]
            val t = if (lengths[segment] > 0f) (remaining / lengths[segment]).coerceIn(0f, 1f) else 0f
            val x = start.x + (end.x - start.x) * t
            val y = start.y + (end.y - start.y) * t
            val angle = Math.toDegrees(
                atan2((end.y - start.y).toDouble(), (end.x - start.x).toDouble()),
            ).toFloat() + 90f

            canvas.drawCircle(x, y, iconSize * 0.62f, haloPaint)
            canvas.drawCircle(x, y, iconSize * 0.62f, ringPaint)
            canvas.save()
            canvas.rotate(angle, x, y)
            icon.setBounds(
                (x - iconSize / 2f).toInt(), (y - iconSize / 2f).toInt(),
                (x + iconSize / 2f).toInt(), (y + iconSize / 2f).toInt(),
            )
            icon.draw(canvas)
            canvas.restore()
        }
        // Nächstes Bild der Animation.
        mapView.postInvalidateDelayed(40L)
    }
}
