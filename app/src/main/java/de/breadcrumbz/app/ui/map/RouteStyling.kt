package de.breadcrumbz.app.ui.map

import android.graphics.DashPathEffect
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Zeichnet die Route abschnittsweise passend zum Verkehrsmittel. Der Modus
 * DIREKT überschreibt alle Stile durch einen glatten Spline.
 */
object RouteStyling {

    fun build(
        mapView: MapView,
        stops: List<Stop>,
        defaultMode: TransportMode,
        routeMode: RouteMode,
    ): List<Polyline> {
        val ordered = stops.sortedBy { it.sortIndex }
        if (ordered.size < 2) return emptyList()

        if (routeMode == RouteMode.DIRECT) {
            return listOf(
                line(
                    mapView,
                    smooth(ordered.map { GeoPoint(it.latitude, it.longitude) }),
                    color = 0xFF6B4A2A.toInt(),
                    width = 6f,
                    dash = floatArrayOf(18f, 12f),
                ),
            )
        }

        return buildList {
            for (index in 1 until ordered.size) {
                val from = ordered[index - 1]
                val to = ordered[index]
                val mode = to.transportMode ?: defaultMode
                val style = styleOf(mode)
                val points = when (mode) {
                    TransportMode.PLANE -> arc(
                        GeoPoint(from.latitude, from.longitude),
                        GeoPoint(to.latitude, to.longitude),
                        wave = false,
                    )
                    TransportMode.KAYAK -> arc(
                        GeoPoint(from.latitude, from.longitude),
                        GeoPoint(to.latitude, to.longitude),
                        wave = true,
                    )
                    else -> listOf(
                        GeoPoint(from.latitude, from.longitude),
                        GeoPoint(to.latitude, to.longitude),
                    )
                }
                add(
                    line(
                        mapView,
                        points,
                        color = style.color,
                        width = style.width,
                        dash = style.dash,
                    ),
                )
            }
        }
    }

    private data class Style(val color: Int, val width: Float, val dash: FloatArray)

    private fun styleOf(mode: TransportMode) = when (mode) {
        TransportMode.WALK -> Style(0xFF4E7A3A.toInt(), 5f, floatArrayOf(10f, 10f))
        TransportMode.BIKE -> Style(0xFF2E6B6B.toInt(), 5f, floatArrayOf(18f, 10f))
        TransportMode.KAYAK -> Style(0xFF2E6FA8.toInt(), 6f, floatArrayOf(8f, 12f))
        TransportMode.CAR -> Style(0xFF3B2B20.toInt(), 7f, floatArrayOf(20f, 12f))
        TransportMode.TRAIN -> Style(0xFF9B3B2F.toInt(), 6f, floatArrayOf(26f, 8f))
        TransportMode.PLANE -> Style(0xFF6E5A8C.toInt(), 5f, floatArrayOf(16f, 14f))
        TransportMode.CAMPER -> Style(0xFFB8782F.toInt(), 7f, floatArrayOf(22f, 10f))
    }

    private fun line(
        mapView: MapView,
        points: List<GeoPoint>,
        color: Int,
        width: Float,
        dash: FloatArray,
    ): Polyline = Polyline(mapView).apply {
        setPoints(points)
        this.color = color
        this.width = width
        paint.pathEffect = DashPathEffect(dash, 0f)
    }

    /** Catmull-Rom geglättete Punkte für den Modus „direkt". */
    private fun smooth(source: List<GeoPoint>): List<GeoPoint> {
        if (source.size < 3) return source
        val result = mutableListOf<GeoPoint>()
        for (index in 0 until source.size - 1) {
            val p0 = source[(index - 1).coerceAtLeast(0)]
            val p1 = source[index]
            val p2 = source[index + 1]
            val p3 = source[(index + 2).coerceAtMost(source.lastIndex)]
            for (step in 0 until 12) {
                val t = step / 12f
                val t2 = t * t
                val t3 = t2 * t
                result.add(
                    GeoPoint(
                        0.5 * (
                            2 * p1.latitude +
                                (-p0.latitude + p2.latitude) * t +
                                (2 * p0.latitude - 5 * p1.latitude + 4 * p2.latitude - p3.latitude) * t2 +
                                (-p0.latitude + 3 * p1.latitude - 3 * p2.latitude + p3.latitude) * t3
                            ),
                        0.5 * (
                            2 * p1.longitude +
                                (-p0.longitude + p2.longitude) * t +
                                (2 * p0.longitude - 5 * p1.longitude + 4 * p2.longitude - p3.longitude) * t2 +
                                (-p0.longitude + 3 * p1.longitude - 3 * p2.longitude + p3.longitude) * t3
                            ),
                    ),
                )
            }
        }
        result.add(source.last())
        return result
    }

    /** Flugbogen oder leicht gewellte Wasserlinie zwischen zwei Punkten. */
    private fun arc(from: GeoPoint, to: GeoPoint, wave: Boolean): List<GeoPoint> {
        val dLat = to.latitude - from.latitude
        val dLon = to.longitude - from.longitude
        val length = hypot(dLat, dLon).coerceAtLeast(0.00001)
        val lift = length * 0.18
        val mid = GeoPoint(
            (from.latitude + to.latitude) / 2 - dLon / length * lift,
            (from.longitude + to.longitude) / 2 + dLat / length * lift,
        )
        return (0..24).map { step ->
            val t = step / 24f
            val a = (1 - t) * (1 - t)
            val b = 2 * (1 - t) * t
            val c = t * t
            val ripple = if (wave) sin(t * 6 * kotlin.math.PI) * length * 0.03 else 0.0
            GeoPoint(
                a * from.latitude + b * mid.latitude + c * to.latitude + ripple * cos(t * kotlin.math.PI),
                a * from.longitude + b * mid.longitude + c * to.longitude,
            )
        }
    }
}
