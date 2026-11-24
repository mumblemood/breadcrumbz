package de.breadcrumbz.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TimelineItem
import de.breadcrumbz.app.domain.model.TimelineKind
import de.breadcrumbz.app.domain.util.haversineMeters
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.sin

private data class TimelinePoint(
    val key: String,
    val label: String,
    val detail: String,
    val x: Float,
    val km: Double,
    val dayKey: String?,
    val item: TimelineItem?,
    /** 0 = Tag, 1 = Station, 2 = Eintrag, 3 = Foto. */
    val kind: Int,
)

private fun dayKey(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(time))

private fun dayLabel(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(time))

/**
 * Horizontaler Zeitstrom über die volle Fensterbreite: leicht geschwungene,
 * gestrichelte Linie mit Pins, Tagesmarken und lesbaren Kilometer-Markern.
 */
@Composable
fun AdventureTimeline(
    items: List<TimelineItem>,
    stops: List<Stop>,
    expandedDay: String?,
    onDayToggle: (String) -> Unit,
    onItemClick: (TimelineItem) -> Unit,
    photoBitmap: (String) -> ImageBitmap?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stepPx = with(density) { 168.dp.toPx() }
    val startPx = with(density) { 48.dp.toPx() }
    val baseY = with(density) { 112.dp.toPx() }
    val canvasHeight = 260.dp

    val stopKm = remember(stops) {
        val ordered = stops.sortedBy { it.sortIndex }
        val result = mutableMapOf<String, Double>()
        var total = 0.0
        ordered.forEachIndexed { index, stop ->
            if (index > 0) {
                val previous = ordered[index - 1]
                total += haversineMeters(
                    previous.latitude, previous.longitude, stop.latitude, stop.longitude,
                ) / 1_000.0
            }
            result[stop.id] = total
        }
        result
    }

    val points = remember(items, expandedDay, stopKm) {
        val result = mutableListOf<TimelinePoint>()
        items.groupBy { dayKey(it.time) }.toSortedMap().forEach { (key, dayItems) ->
            val first = dayItems.first()
            result.add(
                TimelinePoint(
                    key = "day-$key",
                    label = dayLabel(first.time),
                    detail = "${dayItems.size} Einträge",
                    x = 0f,
                    km = first.stopId?.let { stopKm[it] } ?: 0.0,
                    dayKey = key,
                    item = null,
                    kind = 0,
                ),
            )
            if (expandedDay == key) {
                dayItems.forEach { item ->
                    result.add(
                        TimelinePoint(
                            key = item.id,
                            label = item.title.ifBlank { "Foto" },
                            detail = when (item.kind) {
                                TimelineKind.STOP -> "Station"
                                TimelineKind.ENTRY ->
                                    if (item.hasLocation) "Tagebuch" else "Ohne Ort"
                                TimelineKind.PHOTO -> "Foto"
                            },
                            x = 0f,
                            km = item.stopId?.let { stopKm[it] } ?: 0.0,
                            dayKey = key,
                            item = item,
                            kind = when (item.kind) {
                                TimelineKind.STOP -> 1
                                TimelineKind.ENTRY -> 2
                                TimelineKind.PHOTO -> 3
                            },
                        ),
                    )
                }
            }
        }
        result.forEachIndexed { index, point ->
            result[index] = point.copy(x = startPx + index * stepPx)
        }
        result
    }

    val contentWidth = with(density) { (startPx + points.size * stepPx + 64.dp.toPx()).toDp() }
    val maxKm = points.maxOfOrNull { it.km } ?: 0.0
    val kmTargets = remember(maxKm) {
        buildList {
            var value = 100.0
            while (value <= maxKm) {
                add(value)
                value += 100.0
            }
            if (maxKm >= 100.0) add(maxKm)
        }.distinctBy { it.toInt() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .horizontalScroll(rememberScrollState()),
    ) {
        Box(modifier = Modifier.width(contentWidth).height(canvasHeight)) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.width(contentWidth).height(canvasHeight),
            ) {
                if (points.isEmpty()) return@Canvas

                val yAt = { index: Int ->
                    baseY + (sin(index * 0.85f) * with(density) { 9.dp.toPx() }).toFloat()
                }
                val lineColor = Color(0xFF3B2B20).copy(alpha = 0.7f)
                val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 12f), 0f)

                val path = Path()
                path.moveTo(points.first().x, yAt(0))
                for (index in 1 until points.size) {
                    val middle = (points[index - 1].x + points[index].x) / 2
                    path.quadraticBezierTo(middle, yAt(index - 1), points[index].x, yAt(index))
                }
                drawPath(path, lineColor, style = Stroke(width = 3f, pathEffect = dash))

                val markerTop = with(density) { 44.dp.toPx() }
                val markerBottom = with(density) { 200.dp.toPx() }
                kmTargets.forEach { target ->
                    val index = points.indexOfFirst { it.km >= target }
                        .let { if (it < 0) points.lastIndex else it }
                    val x = points[index].x
                    drawLine(
                        color = Color(0xFFB8782F),
                        start = Offset(x, markerTop),
                        end = Offset(x, markerBottom),
                        strokeWidth = 2f,
                    )
                }

                points.forEachIndexed { index, point ->
                    val color = when (point.kind) {
                        0 -> Color(0xFF2E6B6B)
                        1 -> Color(0xFF3B2B20)
                        2 -> Color(0xFFB8782F)
                        else -> Color(0xFF8A5A3B)
                    }
                    drawLine(
                        color = color,
                        start = Offset(point.x, yAt(index)),
                        end = Offset(point.x, yAt(index) + with(density) { 26.dp.toPx() }),
                        strokeWidth = 3f,
                    )
                    drawCircle(color, radius = 9f, center = Offset(point.x, yAt(index)))
                    drawCircle(
                        Color(0xFFFBF4E4),
                        radius = 4f,
                        center = Offset(point.x, yAt(index)),
                    )
                }
            }

            // Karten an den Pins. Ein Tipp öffnet den Tag oder das Element.
            points.forEachIndexed { index, point ->
                val wobble = (sin(index * 0.85f) * 9f).toInt()
                val x = with(density) { (point.x - 78.dp.toPx()).toDp() }
                val y = with(density) {
                    (baseY + wobble + 30.dp.toPx()).toDp()
                }
                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(156.dp)
                        .clickable {
                            if (point.kind == 0 && point.dayKey != null) {
                                onDayToggle(point.dayKey)
                            } else if (point.item != null) {
                                onItemClick(point.item)
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (point.kind) {
                        0 -> Text(
                            text = point.label,
                            color = Color(0xFFFBF4E4),
                            style = MaterialTheme.typography.labelMedium
                                .copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2E6B6B))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        3 -> {
                            val photoId = point.item?.photoId
                            val bitmap = photoId?.let(photoBitmap)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = point.label,
                                        modifier = Modifier
                                            .width(76.dp)
                                            .height(58.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFBF4E4)),
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .width(76.dp)
                                            .height(58.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFDCCDB4)),
                                    )
                                }
                                Text(
                                    text = point.label.take(12),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                        else -> Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (point.kind == 2) Color(0xFFF6E27A) else Color(0xFFFBF4E4),
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Text(point.detail, style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }

            // Kilometer-Beschriftung liegt über den Bildern und bleibt so lesbar.
            kmTargets.forEach { target ->
                val index = points.indexOfFirst { it.km >= target }
                    .let { if (it < 0) points.lastIndex else it }
                val x = with(density) { (points[index].x - 34.dp.toPx()).toDp() }
                val label = if (target == maxKm && maxKm >= 100.0) {
                    "${String.format(Locale.GERMANY, "%.1f", maxKm)} km"
                } else {
                    "${target.toInt()} km"
                }
                Text(
                    text = label,
                    color = Color(0xFF3B2B20),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(x = x, y = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFBF4E4))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
