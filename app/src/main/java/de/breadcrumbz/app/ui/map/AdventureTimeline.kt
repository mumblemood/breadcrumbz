package de.breadcrumbz.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
    /** Verknüpfter Eintrag – für die Markierung aller zugehörigen Elemente. */
    val entryId: String?,
    /** 0 = Tag, 1 = Station, 2 = Eintrag, 3 = Foto, 4 = Foto im Eintrag, 5 = Pin im Eintrag. */
    val kind: Int,
)

private val ink = Color(0xFF3B2B20)
private val paper = Color(0xFFFBF4E4)
private val accent = Color(0xFFB8782F)
private val teal = Color(0xFF2E6B6B)
private val fieldGray = Color(0xFFE8E8E8)
private val dayMarker = Color(0xFFCDE3DF)

private fun dayKey(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(time))

private fun dayLabel(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(time))

/**
 * Zeitstrom: schwarze gestrichelte Linie mit weißem Rand. Pins und Elemente
 * hängen auf der Linie. Wischen scrollt; erst langes Halten startet das Ziehen.
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
    onDropEntry: (String, Float, Float) -> Unit = { _, _, _ -> },
    onCenterKeyChange: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val stepPx = with(density) {
        (168f * (mapZoom / 14f)).coerceIn(100f, 300f).dp.toPx()
    }
    val scrollState = rememberScrollState()
    var timelineBounds by remember { mutableStateOf(Rect.Zero) }
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
                    detail = "",
                    x = 0f,
                    km = kmOf(first),
                    dayKey = key,
                    item = null,
                    photoId = null,
                    entryId = null,
                    kind = 0,
                ),
            )
            if (expandedDay == key) {
                dayItems.forEach { item ->
                    val photoIds = item.entryId?.let { photosByEntry[it] }.orEmpty()
                    result.add(
                        TimelinePoint(
                            key = item.id,
                            label = item.title.ifBlank {
                                if (item.kind == TimelineKind.STOP) "Pin" else
                                    if (item.kind == TimelineKind.PHOTO) "Foto" else "Eintrag"
                            },
                            detail = when (item.kind) {
                                TimelineKind.STOP -> "Station"
                                TimelineKind.ENTRY -> ""
                                TimelineKind.PHOTO -> "Foto"
                            },
                            x = 0f,
                            km = kmOf(item),
                            dayKey = key,
                            item = item,
                            photoId = null,
                            entryId = item.entryId,
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
                                entryId = item.entryId,
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
                                    entryId = item.entryId,
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
            .horizontalScroll(scrollState)
            .onGloballyPositioned { timelineBounds = it.boundsInRoot() },
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
                val dash = PathEffect.dashPathEffect(floatArrayOf(30f, 18f), 0f)

                // Tage bekommen eine größere Markierung in eigener Farbe. Sie
                // umschließt den Titel und alle Elemente dieses Tages.
                val openDay = expandedDay
                if (openDay != null) {
                    val indices = points.indices.filter { points[it].dayKey == openDay }
                    if (indices.isNotEmpty()) {
                        val minX = indices.minOf { points[it].x }
                        val maxX = indices.maxOf { points[it].x }
                        val minY = indices.minOf { yAt(it) }
                        val maxY = indices.maxOf { yAt(it) }
                        drawRoundRect(
                            color = dayMarker,
                            topLeft = Offset(minX - 100f, minY - 52f),
                            size = androidx.compose.ui.geometry.Size(
                                maxX - minX + 200f,
                                maxY - minY + 172f,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22f, 22f),
                        )
                    }
                }

                // Einträge bekommen ein hellgraues Feld, das den Eintrag und
                // alle zugehörigen Fotos und Pins umschließt.
                expandedEntryIds.forEach { entryId ->
                    val indices = points.indices.filter { points[it].entryId == entryId }
                    if (indices.isNotEmpty()) {
                        val minX = indices.minOf { points[it].x }
                        val maxX = indices.maxOf { points[it].x }
                        val minY = indices.minOf { yAt(it) }
                        val maxY = indices.maxOf { yAt(it) }
                        drawRoundRect(
                            color = fieldGray,
                            topLeft = Offset(minX - 86f, minY - 40f),
                            size = androidx.compose.ui.geometry.Size(
                                maxX - minX + 172f,
                                maxY - minY + 150f,
                            ),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f),
                        )
                    }
                }

                val path = Path()
                path.moveTo(points.first().x, yAt(0))
                for (index in 1 until points.size) {
                    val middle = (points[index - 1].x + points[index].x) / 2
                    path.quadraticBezierTo(middle, yAt(index - 1), points[index].x, yAt(index))
                }
                // Dünner weißer Rand, darüber dicke schwarze Striche.
                drawPath(path, Color.White, style = Stroke(width = 8f))
                drawPath(path, ink, style = Stroke(width = 20f, pathEffect = dash))

                val centerIndex = points.indexOfFirst { it.key == centerKey }
                if (centerIndex >= 0) {
                    val center = Offset(points[centerIndex].x, yAt(centerIndex))
                    drawCircle(Color(0xFFF6E27A).copy(alpha = 0.55f), radius = 27f, center = center)
                    drawCircle(accent, radius = 27f, center = center, style = Stroke(width = 3f))
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
            }

            points.forEachIndexed { index, point ->
                val item = point.item
                val lineY = with(density) { (baseY + sin(index * 0.85f) * 9f).toDp() }
                val cardY = lineY + 30.dp
                val highlighted = point.key == centerKey
                val expanded = item?.entryId?.let { expandedEntryIds.contains(it) } == true
                val children = (item?.photoCount ?: 0) + (item?.pinCount ?: 0)
                val dragOffset = remember(point.key) { mutableStateOf(Offset.Zero) }

                // Pin in Kartengröße mit farbigem Rand: die Spitze sitzt auf der Linie.
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { (point.x - 17.dp.toPx()).toDp() },
                            y = lineY - 31.dp,
                        )
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(paper)
                        .border(2.dp, accent, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_stop_marker),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) {
                                (point.x - 84.dp.toPx()).toDp() + dragOffset.value.x.toDp()
                            },
                            y = cardY + with(density) { dragOffset.value.y.toDp() },
                        )
                        .width(168.dp)
                        .scale(if (highlighted) 1.08f else 1f)
                        .pointerInput(point.key) {
                            var dragX = 0f
                            var dragY = 0f
                            var grabX = 0f
                            var grabY = 0f
                            var grabSet = false
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dragX = 0f
                                    dragY = 0f
                                    grabSet = false
                                    dragOffset.value = Offset.Zero
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    if (!grabSet) {
                                        grabX = change.position.x
                                        grabY = change.position.y
                                        grabSet = true
                                    }
                                    dragX += amount.x
                                    dragY += amount.y
                                    dragOffset.value = Offset(dragX, dragY)
                                },
                                onDragEnd = {
                                    dragOffset.value = Offset.Zero
                                    val source = when {
                                        point.kind == 3 || point.kind == 4 ->
                                            point.photoId?.let { "photo-$it" }
                                        point.kind == 1 || point.kind == 5 ->
                                            point.item?.stopId?.let { "stop-$it" }
                                        else -> null
                                    }
                                    if (point.kind == 2 && item?.entryId != null) {
                                        val cardLeft = timelineBounds.left +
                                            point.x - with(density) { 84.dp.toPx() } -
                                            scrollState.value
                                        val cardTop = timelineBounds.top +
                                            with(density) { cardY.toPx() }
                                        onDropEntry(
                                            item.entryId!!,
                                            cardLeft + grabX + dragX,
                                            cardTop + grabY + dragY,
                                        )
                                    }
                                    if (source != null) {
                                        val finalX = point.x + dragX
                                        val target = points.firstOrNull { candidate ->
                                            candidate.kind == 2 &&
                                                abs(candidate.x - finalX) < 170f
                                        }?.item?.entryId
                                        onDropElement(source, target)
                                    }
                                },
                                onDragCancel = { dragOffset.value = Offset.Zero },
                            )
                        }
                        .clickable {
                            when {
                                point.kind == 0 && point.dayKey != null ->
                                    onDayToggle(point.dayKey)
                                point.kind == 2 && item?.entryId != null ->
                                    onItemClick(item)
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
                            val ratio = if (bitmap != null && bitmap.height > 0) {
                                (bitmap.width.toFloat() / bitmap.height.toFloat())
                                    .coerceIn(0.7f, 1.6f)
                            } else {
                                4f / 3f
                            }
                            val photoWidth = if (point.kind == 4) 92.dp else 116.dp
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(photoWidth)
                                    .rotate(if (point.photoId == centerPhotoId) -4f else -8f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(paper)
                                    .border(
                                        if (highlighted) 2.dp else 1.dp,
                                        if (highlighted) accent else Color(0xFFDCCDB4),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(ratio)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(0xFFE7DAC2)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap,
                                            contentDescription = point.label.ifBlank { "Foto" },
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                Text(
                                    text = point.label,
                                    color = ink,
                                    fontSize = 9.sp,
                                    maxLines = 1,
                                )
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlacePinIcon(
                                    tint = ink,
                                    size = with(density) {
                                        MaterialTheme.typography.bodySmall.fontSize.toDp()
                                    },
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = point.label,
                                    color = ink,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2,
                                )
                            }
                            Text(
                                text = point.detail,
                                color = Color(0xFF6B4A2A),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }

                        else -> Column(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (expanded) fieldGray else paper)
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
                                        modifier = Modifier.clickable {
                                            item?.entryId?.let(onEntryToggle)
                                        },
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (point.kind == 2 && item?.hasLocation == true) {
                                    PlacePinIcon(
                                        tint = ink,
                                        size = with(density) {
                                            MaterialTheme.typography.bodyMedium.fontSize.toDp()
                                        },
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(
                                    text = point.label,
                                    color = ink,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 2,
                                )
                            }
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
                                                    contentScale = ContentScale.Fit,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
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

            if (points.isNotEmpty()) kmTargets.forEach { target ->
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
