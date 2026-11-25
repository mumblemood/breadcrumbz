package de.breadcrumbz.app.ui.map

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.atan2
import kotlin.math.hypot

/** FOOTPRINTS = zu Fuß, ROAD = Auto/Campingbus als Landstraße alter Karten. */
enum class TrackStyle { FOOTPRINTS, ROAD }

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
 * - Auto/Campingbus: eine Landstraße wie auf alten Karten – helles Band
 *   mit zwei feinen Tintenrändern.
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
    private val oval = RectF()
    private val roadEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val roadFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F6EFE1")
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val roadPath = Path()

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
            halo.alpha = if (path.subtle) 90 else 170
            if (path.style == TrackStyle.ROAD) {
                drawRoad(canvas, xs, ys, path.subtle, density)
                return@forEach
            }

            val step = when (path.style) {
                TrackStyle.FOOTPRINTS -> 11f * density
                TrackStyle.ROAD -> 5f * density
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
                            TrackStyle.ROAD -> Unit
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

    /** Landstraße: Tintenband, darin ein helles Band – ergibt zwei feine Ränder. */
    private fun drawRoad(canvas: Canvas, xs: FloatArray, ys: FloatArray, subtle: Boolean, density: Float) {
        roadPath.reset()
        roadPath.moveTo(xs[0], ys[0])
        for (index in 1 until xs.size) roadPath.lineTo(xs[index], ys[index])
        val scale = if (subtle) 0.6f else 1f
        halo.style = Paint.Style.STROKE
        halo.strokeWidth = 10f * density * scale
        halo.strokeCap = Paint.Cap.ROUND
        halo.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(roadPath, halo)
        halo.style = Paint.Style.FILL
        roadEdge.alpha = if (subtle) 120 else 235
        roadEdge.strokeWidth = 7f * density * scale
        canvas.drawPath(roadPath, roadEdge)
        roadFill.alpha = 255
        roadFill.strokeWidth = 4.4f * density * scale
        canvas.drawPath(roadPath, roadFill)
    }
}
