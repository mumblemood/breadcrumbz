package de.breadcrumbz.app.ui.map

import android.graphics.DashPathEffect
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.util.haversineMeters
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polyline
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Die Linie auf der Karte sieht genauso aus wie in der Zeitleiste:
 * weißer Untergrund, schwarze gestrichelte Linie darüber. Das Verkehrsmittel
 * bestimmt nur, wie die Punkte geführt werden – nicht die Farbe.
 *
 * Ein Tippen auf die Linie öffnet die Verkehrsmittel-Auswahl des Abschnitts.
 */
object RouteStyling {

    // Dieselben Werte benutzt auch die Zeitleiste – Karte und Timeline sehen
    // dadurch identisch aus: weißer Unterzug, schwarze gestrichelte Linie.
    val LINE_COLOR = 0xFF3B2B20.toInt()
    val UNDER_COLOR = 0xFFFFFFFF.toInt()
    const val LINE_WIDTH = 14f
    const val UNDER_WIDTH = 26f
    // Längere Pause zwischen den Strichen, damit die Lücken deutlich sichtbar sind.
    const val DASH = 26f
    const val GAP = 22f

    fun build(
        mapView: MapView,
        stops: List<Stop>,
        defaultMode: TransportMode,
        routeMode: RouteMode,
        /** Fertige Straßenpunkte pro Abschnitt (Ankunfts-Pin als Schlüssel). */
        overridePoints: Map<String, List<GeoPoint>> = emptyMap(),
        /** Selbst gesetzte Punkte pro Abschnitt – auch offline sichtbar. */
        waypoints: Map<String, List<GeoPoint>> = emptyMap(),
        onLineClick: (GeoPoint) -> Unit = {},
    ): List<Polyline> {
        // Die Reihenfolge kommt chronologisch aus der Reise (Foto-Zeitstempel).
        // Der Aufruf sortiert die Stops vor; hier darf nichts mehr umsortiert werden.
        val ordered = stops
        if (ordered.size < 2) return emptyList()

        if (routeMode == RouteMode.DIRECT) {
            val via = mutableListOf<GeoPoint>()
            ordered.forEachIndexed { index, stop ->
                if (index > 0) waypoints[stop.id].orEmpty().forEach { via.add(it) }
                via.add(GeoPoint(stop.latitude, stop.longitude))
            }
            val points = smooth(via)
            return listOf(
                underLine(mapView, points),
                dashedLine(mapView, points) { _, _, point ->
                    onLineClick(point ?: points.last())
                    true
                },
            )
        }

        return buildList {
            for (index in 1 until ordered.size) {
                val from = ordered[index - 1]
                val to = ordered[index]
                val mode = to.transportMode ?: defaultMode
                val online = overridePoints[to.id]
                val raw = online ?: routeVia(
                    from = GeoPoint(from.latitude, from.longitude),
                    to = GeoPoint(to.latitude, to.longitude),
                    via = waypoints[to.id].orEmpty(),
                    mode = mode,
                )
                // Online-Punkte sind sehr genau: nur Wendepunkte behalten,
                // leicht nachzeichnen und als weichen Spline verbinden.
                val points = if (online != null &&
                    (mode == TransportMode.CAR || mode == TransportMode.CAMPER)
                ) {
                    sketchLine(raw)
                } else {
                    raw
                }
                add(underLine(mapView, points))
                add(dashedLine(mapView, points) { _, _, point ->
                    onLineClick(point ?: GeoPoint(to.latitude, to.longitude))
                    true
                })
            }
        }
    }

    private fun underLine(mapView: MapView, points: List<GeoPoint>): Polyline =
        line(mapView, points, UNDER_COLOR, UNDER_WIDTH, null).apply {
            infoWindow = null
        }

    private fun dashedLine(
        mapView: MapView,
        points: List<GeoPoint>,
        onClick: Polyline.OnClickListener,
    ): Polyline = line(
        mapView, points, LINE_COLOR, LINE_WIDTH,
        DashPathEffect(floatArrayOf(DASH, GAP), 0f),
    ).apply {
        // Keine Sprechblase: ein Tippen öffnet die Verkehrsmittel-Auswahl.
        infoWindow = null
        setOnClickListener(onClick)
    }

    private fun line(
        mapView: MapView,
        points: List<GeoPoint>,
        color: Int,
        width: Float,
        dash: DashPathEffect?,
    ): Polyline = Polyline(mapView).apply {
        setPoints(points)
        this.color = color
        this.width = width
        paint.pathEffect = dash
    }

    /** Führt die Linie über alle gesetzten Zwischenpunkte des Abschnitts. */
    private fun routeVia(
        from: GeoPoint,
        to: GeoPoint,
        via: List<GeoPoint>,
        mode: TransportMode,
    ): List<GeoPoint> {
        val nodes = buildList {
            add(from)
            addAll(via)
            add(to)
        }
        return buildList {
            for (index in 1 until nodes.size) {
                val start = nodes[index - 1]
                val end = nodes[index]
                val segment = when (mode) {
                    TransportMode.PLANE -> arc(start, end, wave = false)
                    TransportMode.KAYAK -> arc(start, end, wave = true)
                    TransportMode.CAR, TransportMode.CAMPER -> handDrawnRoad(start, end)
                    else -> listOf(start, end)
                }
                if (index == 1) addAll(segment) else addAll(segment.drop(1))
            }
        }
    }

    /**
     * Aus den vielen Online-Punkten nur die Stellen behalten, an denen die
     * Route wirklich abbiegt. Danach ein kleiner, gleichmäßiger Schwung – wie
     * mit dem Stift nachgezogen – und alles als Spline geglättet. Start und
     * Ziel bleiben exakt auf ihren Pins.
     */
    private fun sketchLine(source: List<GeoPoint>): List<GeoPoint> {
        if (source.size < 3) return source
        val kept = mutableListOf(source.first())
        for (index in 1 until source.lastIndex) {
            val point = source[index]
            val distance = haversineMeters(
                kept.last().latitude, kept.last().longitude,
                point.latitude, point.longitude,
            )
            val turn = turnDegrees(source[index - 1], point, source[index + 1])
            if (distance >= 120.0 || turn >= 22.0) kept.add(point)
        }
        kept.add(source.last())
        if (kept.size < 3) return smooth(kept)

        val seed = source.first().latitude * 13.0 + source.first().longitude * 7.0
        val wobbled = kept.mapIndexed { index, point ->
            if (index == 0 || index == kept.lastIndex) {
                point
            } else {
                val t = index.toDouble() / kept.lastIndex
                val envelope = Math.sin(t * Math.PI)
                val lateral = Math.sin(index * 1.7 + seed) * 26.0 * envelope
                val before = kept[index - 1]
                val after = kept[index + 1]
                val cosLat = Math.cos(Math.toRadians(point.latitude)).coerceAtLeast(0.01)
                val dx = (after.longitude - before.longitude) * cosLat * 111_320.0
                val dy = (after.latitude - before.latitude) * 111_320.0
                val length = Math.hypot(dx, dy).coerceAtLeast(1.0)
                val nx = -dy / length
                val ny = dx / length
                GeoPoint(
                    point.latitude + ny * lateral / 111_320.0,
                    point.longitude + nx * lateral / (111_320.0 * cosLat),
                )
            }
        }
        return smooth(wobbled)
    }

    /** Winkel zwischen zwei aufeinanderfolgenden Teilen der Route in Grad. */
    private fun turnDegrees(a: GeoPoint, b: GeoPoint, c: GeoPoint): Double {
        val cosLat = Math.cos(Math.toRadians(b.latitude)).coerceAtLeast(0.01)
        val ax = (b.longitude - a.longitude) * cosLat
        val ay = b.latitude - a.latitude
        val bx = (c.longitude - b.longitude) * cosLat
        val by = c.latitude - b.latitude
        return Math.abs(
            Math.toDegrees(
                Math.atan2(ax * by - ay * bx, ax * bx + ay * by),
            ),
        )
    }

    /**
     * Ungefähre Straßenführung, ganz offline: als würde man die Route mit einem
     * Stift auf der Karte nachziehen. Ein Zwischenpunkt pro Kilometer reicht;
     * kleine, deterministische Seitenschwünge lassen sie wie eine Straße wirken.
     */
    private fun handDrawnRoad(from: GeoPoint, to: GeoPoint): List<GeoPoint> {
        val meters = haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        val steps = maxOf(1, ceil(meters / 1_000.0).toInt())
        if (steps == 1) return listOf(from, to)

        val meanLat = Math.toRadians((from.latitude + to.latitude) / 2.0)
        val cosLat = cos(meanLat).coerceAtLeast(0.01)
        val dLat = to.latitude - from.latitude
        val dLonScaled = (to.longitude - from.longitude) * cosLat
        val length = hypot(dLat, dLonScaled)
        if (length == 0.0) return listOf(from, to)
        val perpLat = -dLonScaled / length
        val perpLon = dLat / length
        // Aus den Koordinaten abgeleitet: gleiche Strecke sieht immer gleich aus.
        val phase = ((from.latitude * 1_000.0).toLong() xor
            (to.longitude * 1_000.0).toLong()).toDouble() / 1_000.0

        return (0..steps).map { index ->
            val t = index.toDouble() / steps
            val envelope = sin(t * Math.PI)
            val wobbleMeters = (
                sin(t * Math.PI * 4.0 + phase) * 0.7 +
                    sin(t * Math.PI * 9.0 + phase * 0.5) * 0.3
                ) * 90.0 * envelope
            GeoPoint(
                from.latitude + dLat * t + perpLat * wobbleMeters / 111_320.0,
                from.longitude + (to.longitude - from.longitude) * t +
                    perpLon * wobbleMeters / (111_320.0 * cosLat),
            )
        }
    }

    /** Catmull-Rom geglättete Punkte für den Modus „direkt". */
    private fun smooth(source: List<GeoPoint>): List<GeoPoint> {
        if (source.size < 3) return source
        val result = mutableListOf<GeoPoint>()
        for (index in 0 until source.size - 1) {
            val p0 = source[(index - 1).coerceAtLeast(0)]
            val p1 = source[index]
            val p2 = source[index + 1]
            val p3 = source[index + 2.coerceAtMost(source.lastIndex)]
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

    /** Bogen für Flugzeug und Kajak; wc === true erzeugt eine Welle. */
    private fun arc(from: GeoPoint, to: GeoPoint, wave: Boolean): List<GeoPoint> {
        val steps = 24
        val midLat = (from.latitude + to.latitude) / 2.0
        val midLon = (from.longitude + to.longitude) / 2.0
        val distance = haversineMeters(from.latitude, from.longitude, to.latitude, to.longitude)
        val lift = minOf(distance / 4_000_000.0, 4.0)
        val bend = if (wave) 1.2 else 0.6
        val result = mutableListOf<GeoPoint>()
        for (index in 0..steps) {
            val t = index.toDouble() / steps
            val arch = sin(t * Math.PI)
            val side = sin(t * Math.PI * 2) * bend
            result.add(
                GeoPoint(
                    midLat + (from.latitude - to.latitude) * (0.5 - t) + arch * lift,
                    midLon + (from.longitude - to.longitude) * (0.5 - t) + side * lift,
                ),
            )
        }
        return result
    }
}
