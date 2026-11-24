package de.breadcrumbz.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.breadcrumbz.app.R
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
    /** 0 = Tag, 1 = Station, 2 = Eintrag, 3 = Foto, 4 = Foto im Eintrag, 5 = Pin im Eintrag. */
    val kind: Int,
)

private val ink = Color(0xFF3B2B20)
private val paper = Color(0xFFFBF4E4)
private val accent = Color(0xFFB8782F)
private val teal = Color(0xFF2E6B6B)

private fun dayKey(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(time))

private fun dayLabel(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(time))

/**
 * Dauerhaft sichtbarer Zeitstrom: geschwungenene Linien, Pins, Tagesmarken,
 * große Polaroids und Einträge, die zum Aufklappen einladen.
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
    onCenterKeyChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val stepPx = with(density) {
        (168f * (mapZoom / 14f)).coerceIn(100f, 300f).dp.toPx()
    }
    val scrollState = rememberScrollState()
    val startPx = with(density) { 48.dp.toPx() }
    val baseY = with(density) { 58.dp.toPx() }

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

    val points = remember(
        items, expandedDay, expandedEntryIds, photosByEntry, photoTimes, stopKm, stops,
    ) {
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
                                    if (item.hasLocation) "Tagebuch$photoText" else "Eintrag$photoText"
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

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
    ) {
        val viewportPx = with(density) { maxWidth.toPx() }
        val centerPixel = scrollState.value + viewportPx / 2f
        val centerPoint = points.minByOrNull { abs(it.x - centerPixel) }
        val centerKey = centerPoint?.key
        val centerPhotoId = centerPoint?.photoId
        LaunchedEffect(centerKey) { onCenterKeyChange(centerKey) }

        Box(modifier = Modifier.width(contentWidth).fillMaxHeight()) {
            androidx.compose.foundation.Canvas(
                modifier = Modifier.width(contentWidth).fillMaxHeight(),
            ) {
                if (points.isEmpty()) return@Canvas

                val yAt = { index: Int ->
                    baseY + (sin(index * 0.85f) * with(density) { 9.dp.toPx() }).toFloat()
                }
                val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 13f), 0f)

                // Geldes Feld: zeigt sichtbar, welche Elemente im Eintrag stecken.
                points.forEachIndexed { index, point ->
                    val children = (point.item?.photoCount ?: 0) +
                        (point.item?.pinCount ?: 0)
                    if (point.kind == 2 && children > 0 &&
                        expandedEntryIds.contains(point.item?.entryId)
                    ) {
                        drawRoundRect(
                            color = Color(0xFFF6E27A).copy(alpha = 0.42f),
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
                drawPath(path, ink, style = Stroke(width = 4f, pathEffect = dash))

                // Half-markiertes Element in der Mitte der Zeitleiste.
                val centerIndex = points.indexOfFirst { it.key == centerKey }
                if (centerIndex >= 0) {
                    val center = Offset(points[centerIndex].x, yAt(centerIndex))
                    drawCircle(Color(0xFFF6E27A).copy(alpha = 0.55f), radius = 27f, center = center)
                    drawCircle(
                        accent,
                        radius = 27f,
                        center = center,
                        style = Stroke(width = 3f),
                    )
                }

                val markerTop = with(density) { 18.dp.toPx() }
                val markerBottom = with(density) { 138.dp.toPx() }
                kmTargets.forEach { target ->
                    val index = points.indexOfFirst { it.km >= target }
                        .let { if (it < 0) points.lastIndex else it }
                    val x = points[index].x
                    drawLine(
                        color = accent,
                        start = Offset(x, markerTop),
                        end = Offset(x, markerBottom),
                        strokeWidth = 3f,
                    )
                }

                // Pin-Nadeln unter jedem Element.
                points.forEachIndexed { index, point ->
                    val color = when (point.kind) {
                        0 -> teal
                        1, 5 -> ink
                        2 -> accent
                        else -> Color(0xFF8A5A3B)
                    }
                    val y = yAt(index)
                    val needleLength = if (point.kind == 2) 34f else 26f
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
                    drawCircle(paper, radius = 5f, center = Offset(point.x, y))
                }
            }

            points.forEachIndexed { index, point ->
                val item = point.item
                val wobble = (sin(index * 0.85f) * 9f).toInt()
                val x = with(density) { (point.x - 84.dp.toPx()).toDp() }
                val y = with(density) { (baseY + wobble + 46.dp.toPx()).toDp() }
                val highlighted = point.key == centerKey
                val expanded = item?.entryId?.let { expandedEntryIds.contains(it) } == true
                val children = (item?.photoCount ?: 0) + (item?.pinCount ?: 0)

                Box(
                    modifier = Modifier
                        .offset(x = x, y = y)
                        .width(168.dp)
                        .scale(if (highlighted) 1.08f else 1f)
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
                            color = paper,
                            style = MaterialTheme.typography.labelMedium
                                .copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(teal)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )

                        3, 4 -> {
                            val bitmap = point.photoId?.let(photoBitmap)
                            val photoWidth = if (point.kind == 4) 92.dp else 116.dp
                            val photoHeight = if (point.kind == 4) 70.dp else 90.dp
                            val tilt = if (point.photoId == centerPhotoId) -4f else -8f
                            Box(
                                modifier = Modifier
                                    .rotate(tilt)
                                    .width(photoWidth)
                                    .height(photoHeight)
                                    .border(
                                        if (highlighted) 2.dp else 1.dp,
                                        if (highlighted) accent else Color(0xFFDCCDB4),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(paper)
                                    .padding(5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFE7DAC2)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = point.label.ifBlank { "Foto" },
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                            }
                        }

                        1, 5 -> Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(paper)
                                .border(
                                    if (highlighted) 2.dp else 1.dp,
                                    if (highlighted) accent else Color(0xFFDCCDB4),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_stop_marker),
                                contentDescription = "Pin",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                text = point.label,
                                color = ink,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                            Text(
                                text = point.detail,
                                color = Color(0xFF6B4A2A),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        else -> Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (point.kind == 2) Color(0xFFF6E27A) else paper)
                                .border(
                                    if (highlighted) 2.dp else 1.dp,
                                    if (highlighted) accent else Color(0xFFDCCDB4),
                                    RoundedCornerShape(8.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    point.detail,
                                    color = Color(0xFF6B4A2A),
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (point.kind == 2 && children > 0) {
                                    Text(
                                        if (expanded) "▴" else "▾",
                                        color = ink,
                                        style = MaterialTheme.typography.labelLarge,
                                    )
                                }
                            }
                            Text(
                                text = point.label,
                                color = ink,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                            )
                            // Sichtbare Vorschau lädt zum Vergrößern des Eintrags ein.
                            if (point.kind == 2 && children > 0 && !expanded) {
                                val entryPhotos = item?.entryId
                                    ?.let { photosByEntry[it] }.orEmpty()
                                val ordered = if (centerPhotoId != null &&
                                    entryPhotos.contains(centerPhotoId)
                                ) {
                                    entryPhotos.filter { it != centerPhotoId } + centerPhotoId
                                } else {
                                    entryPhotos
                                }
                                Box(modifier = Modifier.height(38.dp).fillMaxWidth()) {
                                    ordered.takeLast(3).forEachIndexed { layer, photoId ->
                                        val bitmap = photoBitmap(photoId)
                                        Box(
                                            modifier = Modifier
                                                .offset(x = (layer * 24).dp)
                                                .size(38.dp)
                                                .rotate((-8f + layer * 5f))
                                                .clip(RoundedCornerShape(3.dp))
                                                .background(paper)
                                                .padding(3.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(Color(0xFFE7DAC2)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
                                Text(
                                    "▾ Eintrag öffnen · $children Elemente",
                                    color = ink,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            } else if (point.kind == 2) {
                                Spacer(modifier = Modifier.height(if (expanded) 12.dp else 6.dp))
                            }
                        }
                    }
                }
            }

            Text(
                text = "Gesamt: ${String.format(Locale.GERMANY, "%.1f", totalKm)} km",
                color = ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = contentWidth - 156.dp, y = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(paper)
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
                    color = ink,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .offset(x = x, y = 22.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(paper)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}
