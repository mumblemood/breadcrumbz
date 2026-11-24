package de.breadcrumbz.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.pointer.pointerInput
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
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sin

private data class TimelinePoint(
    val key: String,
    val label: String,
    val detail: String,
    val x: Float,
    val km: Double,
    val dayKey: String?,
    val item: TimelineItem?,
    val photoId: String?,
    /** 0 = Tag, 1 = Station, 2 = Eintrag, 3 = Foto, 4 = Foto im Eintrag. */
    val kind: Int,
)

private fun dayKey(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(time))

private fun dayLabel(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(time))

/**
 * Dauerhaft sichtbarer Zeitstrom: geschwungene gestrichelte Linie, Pin-Nadeln,
 * Tagesmarken, Kilometer-Marker und aufklappbare Einträge mit mehreren Bildern.
 */
@Composable
fun AdventureTimeline(
    items: List<TimelineItem>,
    stops: List<Stop>,
    photosByEntry: Map<String, List<String>>,
    expandedDay: String?,
    expandedEntryIds: Set<String>,
    onDayToggle: (String) -> Unit,
    onEntryToggle: (String) -> Unit,
    onItemClick: (TimelineItem) -> Unit,
    onPhotoClick: (String) -> Unit,
    photoBitmap: (String) -> ImageBitmap?,
    photoTimes: Map<String, Long> = emptyMap(),
    mapZoom: Float = 14f,
    externalKey: String? = null,
    onScrollFraction: (Float) -> Unit = {},
    onDropElement: (String, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stepPx = with(density) {
        (168f * (mapZoom / 14f)).coerceIn(100f, 300f).dp.toPx()
    }
    val scrollState = rememberScrollState()
    val startPx = with(density) { 48.dp.toPx() }
    val baseY = with(density) { 112.dp.toPx() }

    val orderedStops = remember(stops) { stops.sortedBy { it.sortIndex } }
    val stopKm = remember(orderedStops) {
        val result = mutableMapOf<String, Double>()
        var total = 0.0
        orderedStops.forEachIndexed { index, stop ->
            if (index > 0) {
                val previous = orderedStops[index - 1]
                total += haversineMeters(
                    previous.latitude, previous.longitude, stop.latitude, stop.longitude,
                ) / 1_000.0
            }
            result[stop.id] = total
        }
        result
    }
    val totalKm = stopKm.values.maxOrNull() ?: 0.0

    val points = remember(items, expandedDay, expandedEntryIds, photosByEntry, photoTimes, stopKm, stops) {
        val result = mutableListOf<TimelinePoint>()
        val kmOf = { item: TimelineItem -> item.stopId?.let { stopKm[it] } ?: 0.0 }

        items.groupBy { dayKey(it.time) }.toSortedMap().forEach { (key, dayItems) ->
            val first = dayItems.first()
            result.add(
                TimelinePoint(
                    key = "day-$key",
                    label = dayLabel(first.time),
                    detail = "${dayItems.size} Elemente",
                    x = 0f,
                    km = kmOf(first),
                    dayKey = key,
                    item = null,
                    photoId = null,
                    kind = 0,
                ),
            )
            if (expandedDay == key) {
                dayItems.forEach { item ->
                    val photoIds = item.entryId?.let { photosByEntry[it] }.orEmpty()
                    val photoText = if (photoIds.isEmpty()) "" else " · ${photoIds.size} Fotos"
                    result.add(
                        TimelinePoint(
                            key = item.id,
                            label = item.title.ifBlank {
                                if (item.kind == TimelineKind.STOP) "Pin" else
                                    if (item.kind == TimelineKind.PHOTO) "Foto" else "Eintrag"
                            },
                            detail = when (item.kind) {
                                TimelineKind.STOP -> "Station"
                                TimelineKind.ENTRY ->
                                    if (item.hasLocation) "Tagebuch$photoText" else "Ohne Ort$photoText"
                                TimelineKind.PHOTO -> "Foto"
                            },
                            x = 0f,
                            km = kmOf(item),
                            dayKey = key,
                            item = item,
                            photoId = null,
                            kind = when (item.kind) {
                                TimelineKind.STOP -> 1
                                TimelineKind.ENTRY -> 2
                                TimelineKind.PHOTO -> 3
                            },
                        ),
                    )
                    if (item.entryId != null && expandedEntryIds.contains(item.entryId)) {
                        val photoChildren = photoIds.map { photoId ->
                            (photoTimes[photoId] ?: 0L) to TimelinePoint(
                                key = "entryphoto-$photoId",
                                label = "Foto",
                                detail = "",
                                x = 0f,
                                km = kmOf(item),
                                dayKey = key,
                                item = null,
                                photoId = photoId,
                                kind = 4,
                            )
                        }
                        val pinChildren = stops
                            .filter { stop -> stop.entryId == item.entryId }
                            .map { stop ->
                                (stop.visitedAt ?: stop.createdAt) to TimelinePoint(
                                    key = "entrypin-${stop.id}",
                                    label = stop.title.ifBlank { "Pin" },
                                    detail = "Pin im Eintrag",
                                    x = 0f,
                                    km = stopKm[stop.id] ?: kmOf(item),
                                    dayKey = dayKey(stop.visitedAt ?: stop.createdAt),
                                    item = TimelineItem(
                                        id = "stop-${stop.id}",
                                        time = stop.visitedAt ?: stop.createdAt,
                                        kind = TimelineKind.STOP,
                                        title = stop.title.ifBlank { "Pin" },
                                        subtitle = "Pin im Eintrag",
                                        stopId = stop.id,
                                        entryId = item.entryId,
                                        photoId = null,
                                        hasLocation = true,
                                    ),
                                    photoId = null,
                                    kind = 5,
                                )
                            }
                        (photoChildren + pinChildren)
                            .sortedBy { it.first }
                            .forEach { result.add(it.second) }
                    }
                }
            }
        }
        result.forEachIndexed { index, point ->
            result[index] = point.copy(x = startPx + index * stepPx)
        }
        result
    }

    val contentWidth = with(density) { (startPx + points.size * stepPx + 72.dp.toPx()).toDp() }

    LaunchedEffect(scrollState.value, contentWidth) {
        val widthPx = with(density) { contentWidth.toPx() }
        if (widthPx > 0f) onScrollFraction(scrollState.value / widthPx)
    }
    LaunchedEffect(externalKey) {
        val index = points.indexOfFirst { it.key == externalKey }
        if (index >= 0) {
            scrollState.animateScrollTo((index * stepPx).roundToInt())
        }
    }

    val kmTargets = remember(totalKm) {
        buildList {
            var value = 100.0
            while (value <= totalKm) {
                add(value)
                value += 100.0
            }
            if (totalKm > 0.0) add(totalKm)
        }.distinctBy { it.toInt() }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        Box(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.width(contentWidth).fillMaxHeight(),
            ) {
                if (points.isEmpty()) return@Canvas

                val yAt = { index: Int ->
                    baseY + (sin(index * 0.85f) * with(density) { 9.dp.toPx() }).toFloat()
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 13f), 0f)

                // Hintergrundfeld: zeigt sichtbar, welche Elemente im Eintrag stecken.
                points.forEachIndexed { index, point ->
                    val children = (point.item?.photoCount ?: 0) +
                        (point.item?.pinCount ?: 0)
                    if (point.kind == 2 && children > 0 &&
                        expandedEntryIds.contains(point.item?.entryId)
                    ) {
                        drawRoundRect(
                            color = Color(0xFFF6E27A).copy(alpha = 0.38f),
                            topLeft = Offset(point.x - 76f, yAt(index) - 10f),
                            size = androidx.compose.ui.geometry.Size(
                                170f,
                                58f + children * 34f,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14f, 14f),
                        )
                    }
                }

                val path = Path()
                path.moveTo(points.first().x, yAt(0))
                for (index in 1 until points.size) {
                    val middle = (points[index - 1].x + points[index].x) / 2
                    path.quadraticBezierTo(middle, yAt(index - 1), points[index].x, yAt(index))
                }
                drawPath(
                    path,
                    Color(0xFF6B4A2A),
                    style = Stroke(width = 4f, pathEffect = dash),
                )

                val markerTop = with(density) { 52.dp.toPx() }
                val markerBottom = with(density) { 196.dp.toPx() }
                kmTargets.forEach { target ->
                    val index = points.indexOfFirst { it.km >= target }
                        .let { if (it < 0) points.lastIndex else it }
                    val x = points[index].x
                    drawLine(
                        color = Color(0xFFB8782F),
                        start = Offset(x, markerTop),
                        end = Offset(x, markerBottom),
                        strokeWidth = 3f,
                    )
                }

                // Pin-Nadeln: kräftige Linie und Dreieck unter jedem Punkt.
                points.forEachIndexed { index, point ->
                    val color = when (point.kind) {
                        0 -> Color(0xFF2E6B6B)
                        1 -> Color(0xFF3B2B20)
                        2 -> Color(0xFFB8782F)
                        else -> Color(0xFF8A5A3B)
                    }
                    val y = yAt(index)
                    val needleLength = if (point.kind == 2) 36f else 26f
                    drawLine(
                        color = color,
                        start = Offset(point.x, y),
                        end = Offset(point.x, y + needleLength),
                        strokeWidth = 4f,
                    )
                    val triangle = Path().apply {
                        moveTo(point.x - 7f, y + needleLength)
                        lineTo(point.x + 7f, y + needleLength)
                        lineTo(point.x, y + needleLength + 11f)
                        close()
                    }
                    drawPath(triangle, color)
                    drawCircle(color, radius = 11f, center = Offset(point.x, y))
                    drawCircle(Color(0xFFFBF4E4), radius = 5f, center = Offset(point.x, y))
                }
            }

            points.forEachIndexed { index, point ->
                val item = point.item
                val wobble = (sin(index * 0.85f) * 9f).toInt()
                val x = with(density) { (point.x - 78.dp.toPx()).toDp() }
                val y = with(density) { (baseY + wobble + 42.dp.toPx()).toDp() }
                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(156.dp)
                        .pointerInput(point.key) {
                            var dragX = 0f
                            detectDragGestures(onDragEnd = {
                                val source = when {
                                    point.kind == 3 || point.kind == 4 ->
                                        point.photoId?.let { "photo-$it" }
                                    point.kind == 1 || point.kind == 5 ->
                                        point.item?.stopId?.let { "stop-$it" }
                                    else -> null
                                }
                                if (source != null) {
                                    val finalX = point.x + dragX
                                    val target = points.firstOrNull { candidate ->
                                        candidate.kind == 2 &&
                                            abs(candidate.x - finalX) < 170f
                                    }?.item?.entryId
                                    onDropElement(source, target)
                                }
                            }) { _, amount -> dragX += amount.x }
                        }
                        .clickable {
                            when {
                                point.kind == 0 && point.dayKey != null ->
                                    onDayToggle(point.dayKey)
                                point.kind == 2 && item?.entryId != null ->
                                    onEntryToggle(item.entryId)
                                point.kind == 3 && point.photoId != null ->
                                    onPhotoClick(point.photoId)
                                point.kind == 4 && point.photoId != null ->
                                    onPhotoClick(point.photoId)
                                point.item != null -> onItemClick(point.item)
                            }
                        },
                    contentAlignment = Alignment.TopCenter,
                ) {
                    when (point.kind) {
                        0 -> Text(
                            point.label,
                            color = Color(0xFFFBF4E4),
                            style = MaterialTheme.typography.labelMedium
                                .copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2E6B6B))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                        3, 4 -> {
                            val bitmap = point.photoId?.let(photoBitmap)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = point.label,
                                        modifier = Modifier
                                            .width(if (point.kind == 4) 64.dp else 76.dp)
                                            .height(if (point.kind == 4) 44.dp else 58.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFFBF4E4)),
                                    )
                                } else {
                                    Box(
                                        Modifier
                                            .width(if (point.kind == 4) 64.dp else 76.dp)
                                            .height(if (point.kind == 4) 44.dp else 58.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color(0xFFDCCDB4)),
                                    )
                                }
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    point.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (point.kind == 2 &&
                                    ((item?.photoCount ?: 0) + (item?.pinCount ?: 0)) > 0
                                ) {
                                    Text(
                                        if (item?.entryId?.let { expandedEntryIds.contains(it) } == true) {
                                            "▴"
                                        } else {
                                            "▾"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                            Text(
                                text = point.label,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                        }
                    }
                }
            }

            // Gesamtdistanz am rechten Ende der Zeitleiste.
            Text(
                text = "Gesamt: ${String.format(Locale.GERMANY, "%.1f", totalKm)} km",
                color = Color(0xFF3B2B20),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = contentWidth - 156.dp, y = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFFBF4E4))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )

            kmTargets.forEach { target ->
                val index = points.indexOfFirst { it.km >= target }
                    .let { if (it < 0) points.lastIndex else it }
                val x = with(density) { (points[index].x - 34.dp.toPx()).toDp() }
                val label = if (target == totalKm) {
                    "${String.format(Locale.GERMANY, "%.1f", totalKm)} km"
                } else {
                    "${target.toInt()} km"
                }
                Text(
                    text = label,
                    color = Color(0xFF3B2B20),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(x = x, y = 22.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFBF4E4))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
