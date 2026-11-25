package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.hypot

enum class TrackStyle { FOOTPRINTS, TIRES }

/** Ein Abschnitt, der als Spur statt als gestrichelte Linie gezeichnet wird. */
data class TrackPath(
    val points: List<GeoPoint>,
    val style: TrackStyle,
    /** An-/Abreise: blasser. */
    val subtle: Boolean,
)

/**
 * Zeichnet Spuren auf die Karte:
 * - zu Fuß: abwechselnd linke und rechte Fußabdrücke entlang der Strecke,
 * - Auto/Campingbus: zwei parallele Reifenspuren mit kurzen Profilstrichen.
 * Abstände sind in dp, damit die Spur bei jedem Zoom gleich dicht wirkt.
 */
class TrackOverlay(private val paths: List<TrackPath>) : Overlay() {

    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        style = Paint.Style.FILL
    }
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val tread = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val oval = RectF()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || paths.isEmpty()) return
        val density = mapView.resources.displayMetrics.density
        val projection = mapView.projection
        val screen = projection.intrinsicScreenRect
        val margin = 40f * density

        paths.forEach { path ->
            if (path.points.size < 2) return@forEach
            val xs = FloatArray(path.points.size)
            val ys = FloatArray(path.points.size)
            path.points.forEachIndexed { index, point ->
                val pixel = projection.toPixels(point, null)
                xs[index] = pixel.x.toFloat()
                ys[index] = pixel.y.toFloat()
            }
            val alpha = if (path.subtle) 120 else 230
            ink.alpha = alpha
            tread.alpha = alpha
            halo.alpha = if (path.subtle) 90 else 170

            val step = when (path.style) {
                TrackStyle.FOOTPRINTS -> 11f * density
                TrackStyle.TIRES -> 5f * density
            }
            var carry = step / 2f
            var count = 0
            for (index in 1 until xs.size) {
                val x0 = xs[index - 1]
                val y0 = ys[index - 1]
                val dx = xs[index] - x0
                val dy = ys[index] - y0
                val length = hypot(dx, dy)
                if (length < 0.01f) continue
                val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                var along = carry
                while (along <= length) {
                    val t = along / length
                    val x = x0 + dx * t
                    val y = y0 + dy * t
                    val visible = x > screen.left - margin && x < screen.right + margin &&
                        y > screen.top - margin && y < screen.bottom + margin
                    if (visible) {
                        when (path.style) {
                            TrackStyle.FOOTPRINTS -> footprint(canvas, x, y, angle, count % 2 == 0, density)
                            TrackStyle.TIRES -> tireMark(canvas, x, y, angle, count, density)
                        }
                    }
                    count++
                    along += step
                }
                carry = along - length
            }
        }
    }

    /** Ein kleiner Schuhabdruck: Sohle plus Ferse, seitlich versetzt. */
    private fun footprint(canvas: Canvas, x: Float, y: Float, angle: Float, left: Boolean, density: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        canvas.translate(0f, (if (left) -3.5f else 3.5f) * density)
        // Weißer Hauch darunter, damit die Spur auf jeder Karte lesbar bleibt.
        oval.set(-4.2f * density, -2.4f * density, 4.6f * density, 2.4f * density)
        canvas.drawOval(oval, halo)
        // Sohle (vorne) und Ferse (hinten).
        oval.set(0f, -1.7f * density, 3.6f * density, 1.7f * density)
        canvas.drawOval(oval, ink)
        oval.set(-3.4f * density, -1.3f * density, -0.6f * density, 1.3f * density)
        canvas.drawOval(oval, ink)
        canvas.restore()
    }

    /** Zwei parallele Reifenspuren; jeder Punkt trägt einen Profilstrich. */
    private fun tireMark(canvas: Canvas, x: Float, y: Float, angle: Float, count: Int, density: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(angle)
        val offset = 4.5f * density
        val half = 1.6f * density
        tread.strokeWidth = 1.7f * density
        // Leicht schräge Profilstriche, abwechselnd versetzt wie bei einem Reifen.
        val skew = if (count % 2 == 0) 0.9f * density else -0.9f * density
        for (side in listOf(-offset, offset)) {
            canvas.drawCircle(0f, side, half + 0.9f * density, halo)
        }
        for (side in listOf(-offset, offset)) {
            canvas.drawLine(-skew, side - half, skew, side + half, tread)
        }
        canvas.restore()
    }
}
