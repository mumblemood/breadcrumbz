package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.Drawable
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Ein kleines Flugzeug sitzt auf jedem Flug-Abschnitt der Linie: immer in der
 * Mitte des Stücks, das gerade auf dem Bildschirm zu sehen ist. Die Nase zeigt
 * in Flugrichtung. Es bewegt sich nur, wenn man die Karte verschiebt.
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
        val iconSize = (24f * density).toInt()
        ringPaint.strokeWidth = 1.5f * density

        val screen = projection.intrinsicScreenRect
        val margin = iconSize.toFloat()

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

            // Die Linie gleichmäßig abtasten und das längste sichtbare Stück suchen.
            val samples = 240
            val xs = FloatArray(samples + 1)
            val ys = FloatArray(samples + 1)
            var segment = 0
            var walked = 0f
            for (index in 0..samples) {
                val target = total * index / samples
                while (segment < lengths.size - 1 && walked + lengths[segment] < target) {
                    walked += lengths[segment]
                    segment++
                }
                val part = if (lengths[segment] > 0f) {
                    ((target - walked) / lengths[segment]).coerceIn(0f, 1f)
                } else {
                    0f
                }
                val start = pixels[segment]
                val end = pixels[segment + 1]
                xs[index] = start.x + (end.x - start.x) * part
                ys[index] = start.y + (end.y - start.y) * part
            }
            var bestStart = -1
            var bestLength = 0
            var runStart = -1
            for (index in 0..samples) {
                val visible = xs[index] >= screen.left + margin &&
                    xs[index] <= screen.right - margin &&
                    ys[index] >= screen.top + margin &&
                    ys[index] <= screen.bottom - margin
                if (visible) {
                    if (runStart < 0) runStart = index
                    val length = index - runStart + 1
                    if (length > bestLength) {
                        bestLength = length
                        bestStart = runStart
                    }
                } else {
                    runStart = -1
                }
            }
            if (bestStart < 0) return@forEach
            val middle = (bestStart + bestLength / 2).coerceIn(1, samples - 1)
            val x = xs[middle]
            val y = ys[middle]
            val angle = Math.toDegrees(
                atan2(
                    (ys[middle + 1] - ys[middle - 1]).toDouble(),
                    (xs[middle + 1] - xs[middle - 1]).toDouble(),
                ),
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
    }
}
