package de.breadcrumbz.app.ui.map

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
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
    /** 0 = Tag, 1 = Station, 2 = Eintrag, 3 = Foto. Ein Ereignis = ein Pin. */
    val kind: Int,
    /** Eigene Breite des Elements – kleine Pins brauchen weniger Platz. */
    val slot: Float = 0f,
)

private val ink = Color(0xFF3B2B20)
private val paper = Color(0xFFFBF4E4)
private val accent = Color(0xFFB8782F)
private val teal = Color(0xFF2E6B6B)
private val fieldGray = Color(0xFFE8E8E8)
private val dayMarker = Color(0xFFCDE3DF)

private fun dayKey(time: Long): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date(time))

/** Rahmen hinter den Kindern: ein Rechteck pro Tag bzw. pro Eintrag. */
@Composable
private fun TimelineFrame(
    rect: Rect,
    fill: Color,
    highlight: Boolean = false,
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset(
                x = with(density) { rect.left.toDp() },
                y = with(density) { rect.top.toDp() },
            )
            .size(
                width = with(density) { rect.width.toDp() },
                height = with(density) { rect.height.toDp() },
            )
            .clip(RoundedCornerShape(14.dp))
            .background(fill)
            .border(
                if (highlight) 3.dp else 1.dp,
                if (highlight) accent else ink.copy(alpha = 0.45f),
                RoundedCornerShape(14.dp),
            ),
    )
}

/**
 * Zeitstrom: schwarze gestrichelte Linie mit weißem Rand. Pins und Elemente
 * hängen auf der Linie. Wischen scrollt; erst langes Halten startet das Ziehen.
 */
@Composable
fun AdventureTimeline(
    items: List<TimelineItem>,
    stops: List<Stop>,
    photosByEntry: Map<String, List<String>>,
    onItemClick: (TimelineItem) -> Unit,
    onPhotoClick: (String) -> Unit,
    photoBitmap: (String) -> ImageBitmap?,
    photoTimes: Map<String, Long> = emptyMap(),
    entryBodies: Map<String, String> = emptyMap(),
    mapZoom: Float = 14f,
    externalKey: String? = null,
    onScrollFraction: (Float) -> Unit = {},
    onDropElement: (String, String?) -> Unit = { _, _ -> },
    onDropEntry: (String, Float, Float) -> Unit = { _, _, _ -> },
    onCenterKeyChange: (String?) -> Unit = {},
    /** Nur vom Nutzer gestartetes Wischen soll die Karte mitziehen. */
    onUserCenterKey: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scrollScope = rememberCoroutineScope()
    // Grundmaß in dp. Jedes Element bekommt eine eigene Breite: Pins und Fotos
    // sind schmal, Einträge breit. So passt deutlich mehr in den sichtbaren
    // Bereich, ohne dass Karten übereinander liegen.
    val baseStepPx = with(density) {
        (118f * (mapZoom / 14f)).coerceIn(112f, 162f).dp.toPx()
    }
    val scrollState = rememberScrollState()
    var timelineBounds by remember { mutableStateOf(Rect.Zero) }
    // Beim Ziehen wird der Eintrag hervorgehoben, auf den man zusteuert.
    var dragTargetEntryId by remember { mutableStateOf<String?>(null) }
    // Verhindert, dass eine programmgesteuerte Scrollbewegung die Karte zurückzieht.
    var programmaticScroll by remember { mutableStateOf(false) }
    val startPx = with(density) { 34.dp.toPx() }
    val baseY = with(density) { 74.dp.toPx() }

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

    // Die Tage werden fortlaufend nummeriert – oben links im Tagesrahmen.
    val dayLabels = remember(items) {
        items.map { dayKey(it.time) }.distinct().sorted()
            .mapIndexed { index, key -> key to "Tag ${index + 1}" }
            .toMap()
    }

    val points = remember(
        items, dayLabels, photosByEntry, photoTimes, stopKm, stops, baseStepPx, startPx,
    ) {
        val result = mutableListOf<TimelinePoint>()
        val kmOf = { item: TimelineItem -> item.stopId?.let { stopKm[it] } ?: 0.0 }

        items.groupBy { dayKey(it.time) }.toSortedMap().forEach { (key, dayItems) ->
            val first = dayItems.first()
            result.add(
                TimelinePoint(
                    key = "day-$key",
                    label = dayLabels[key].orEmpty(),
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
            dayItems.forEach { item ->
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
            }
        }
        var cursor = startPx
        result.forEachIndexed { index, point ->
            val slot = when (point.kind) {
                2 -> baseStepPx * 2.1f   // Eintrag mit Text und Fotos
                0 -> baseStepPx * 0.62f  // nur Tagesmarke
                else -> baseStepPx * 0.95f
            }
            result[index] = point.copy(x = cursor + slot / 2f, slot = slot)
            cursor += slot
        }
        result
    }

    val contentWidthPx = startPx + points.sumOf { it.slot.toDouble() }.toFloat() +
        with(density) { 56.dp.toPx() }
    val contentWidth = with(density) { contentWidthPx.toDp() }
    val viewportPx = if (timelineBounds.width > 0f) {
        timelineBounds.width
    } else {
        with(density) { 320.dp.toPx() }
    }

    val scrollFraction = if (scrollState.maxValue > 0) {
        (scrollState.value.toFloat() / scrollState.maxValue.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    LaunchedEffect(scrollFraction) { onScrollFraction(scrollFraction) }
    LaunchedEffect(externalKey, viewportPx, points) {
        if (externalKey == null) return@LaunchedEffect
        val index = points.indexOfFirst { it.key == externalKey }
        if (index >= 0) {
            val maxScroll = (contentWidthPx - viewportPx).coerceAtLeast(0f).roundToInt()
            val target = (points[index].x - viewportPx / 2f)
                .roundToInt().coerceAtLeast(0).coerceAtMost(maxScroll)
            if (abs(scrollState.value - target) > 24) {
                // Die Karte hat das gewünschte Element vorgegeben: nicht retour melden.
                programmaticScroll = true
                scrollState.animateScrollTo(target)
                programmaticScroll = false
            }
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

    Column(modifier = modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
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
            LaunchedEffect(centerKey) {
                if (!programmaticScroll) onUserCenterKey(centerKey)
            }

            val frameHalf = with(density) { 26.dp.toPx() }
            val entryFrameHalf = with(density) { 120.dp.toPx() }
            val dayPad = with(density) { 12.dp.toPx() }
            val entryPad = with(density) { 6.dp.toPx() }
            val dayTopPx = with(density) { 46.dp.toPx() }
            val entryTopPx = with(density) { 58.dp.toPx() }
            val dayBottomPx = with(density) { (maxHeight - 8.dp).toPx() }
            val entryBottomPx = with(density) { (maxHeight - 14.dp).toPx() }

            // Rahmen haben feste Höhen: Auf-/Zuklappen ändert nur ihre Breite.
            // Die Grenzen liegen genau in der Mitte zwischen zwei Tagen, damit sich
            // Tagesrahmen niemals überdecken – auch bei dicht beieinander liegenden
            // Einträgen nicht.
            val dayFrames = remember(points, frameHalf, dayPad, dayTopPx, dayBottomPx) {
                val dayKeys = points.filter { it.kind == 0 }
                    .mapNotNull { it.dayKey }
                    .distinct()
                val gap = with(density) { 8.dp.toPx() }
                dayKeys.mapIndexed { index, key ->
                    val xs = points.filter { it.dayKey == key }.map { it.x }
                    if (xs.isEmpty()) return@mapIndexed key to Rect.Zero
                    val left = if (index == 0) {
                        xs.minOf { it } - frameHalf - dayPad
                    } else {
                        val previous = points.filter { it.dayKey == dayKeys[index - 1] }
                            .maxOfOrNull { it.x } ?: xs.minOf { it }
                        (previous + xs.minOf { it }) / 2f + gap / 2f
                    }
                    val right = if (index == dayKeys.lastIndex) {
                        xs.maxOf { it } + frameHalf + dayPad
                    } else {
                        val next = points.filter { it.dayKey == dayKeys[index + 1] }
                            .minOfOrNull { it.x } ?: xs.maxOf { it }
                        (xs.maxOf { it } + next) / 2f - gap / 2f
                    }
                    // Nie über die Tagesscheide hinaus – das verhindert Überschneidungen.
                    val safeLeft = minOf(left, right - 8f)
                    val safeRight = maxOf(right, safeLeft + 8f)
                    key to Rect(safeLeft, dayTopPx, safeRight, dayBottomPx)
                }.filter { it.second != Rect.Zero }.toMap()
            }
            // Eintragsrahmen bleiben immer innerhalb ihres Tagesrahmens.
            val entryFrames = remember(
                points, dayFrames, entryFrameHalf, entryPad, entryTopPx, entryBottomPx,
            ) {
                points.filter { it.kind == 2 && it.item?.entryId != null }.mapNotNull { entry ->
                    val id = entry.item?.entryId ?: return@mapNotNull null
                    val xs = points.filter { it.entryId == id }.map { it.x }
                    if (xs.isEmpty()) return@mapNotNull null
                    val dayRect = entry.dayKey?.let { dayFrames[it] }
                    val rawLeft = xs.minOf { it } - entryFrameHalf - entryPad
                    val rawRight = xs.maxOf { it } + entryFrameHalf + entryPad
                    val left = if (dayRect != null) maxOf(rawLeft, dayRect.left) else rawLeft
                    val right = if (dayRect != null) minOf(rawRight, dayRect.right) else rawRight
                    if (right <= left) return@mapNotNull null
                    id to Rect(left, entryTopPx, right, entryBottomPx)
                }.toMap()
            }

            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight(),
            ) {
                // Tag-Rahmen außen, Eintragsrahmen darüber – beide liegen hinter
                // der Linie und hinter den Elementen.
                dayFrames.forEach { (_, rect) ->
                    TimelineFrame(rect = rect, fill = dayMarker)
                }
                entryFrames.forEach { (id, rect) ->
                    TimelineFrame(
                        rect = rect,
                        fill = fieldGray,
                        highlight = id == dragTargetEntryId,
                    )
                }
                // "Tag 1", "Tag 2" … oben links im jeweiligen Tagesrahmen.
                dayFrames.forEach { (key, rect) ->
                    Text(
                        text = dayLabels[key].orEmpty(),
                        color = paper,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .offset(
                                x = with(density) { (rect.left + 6f).toDp() },
                                y = with(density) { (rect.top + 3f).toDp() },
                            )
                            .clip(RoundedCornerShape(6.dp))
                            .background(teal)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                ) {
                    if (points.isEmpty()) return@Canvas

                    val yAt = { index: Int ->
                        baseY + (sin(index * 0.85f) * with(density) { 6.dp.toPx() }).toFloat()
                    }
                    // Identische Werte wie auf der Karte (RouteStyling).
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(RouteStyling.DASH, RouteStyling.GAP), 0f,
                    )

                    val path = Path()
                    path.moveTo(points.first().x, yAt(0))
                    for (index in 1 until points.size) {
                        val middle = (points[index - 1].x + points[index].x) / 2
                        path.quadraticBezierTo(middle, yAt(index - 1), points[index].x, yAt(index))
                    }
                    // Weißer Unterzug, darüber schwarze Striche – wie auf der Karte.
                    drawPath(
                        path, Color(RouteStyling.UNDER_COLOR),
                        style = Stroke(width = RouteStyling.UNDER_WIDTH),
                    )
                    drawPath(
                        path, Color(RouteStyling.LINE_COLOR),
                        style = Stroke(width = RouteStyling.LINE_WIDTH, pathEffect = dash),
                    )

                    val centerIndex = points.indexOfFirst { it.key == centerKey }
                    if (centerIndex >= 0) {
                        val center = Offset(points[centerIndex].x, yAt(centerIndex))
                        drawCircle(Color(0xFFF6E27A).copy(alpha = 0.55f), radius = 27f, center = center)
                        drawCircle(accent, radius = 27f, center = center, style = Stroke(width = 3f))
                    }

                    // Kilometerleiste in einem schmalen Streifen oben; die Pins
                    // beginnen erst darunter.
                    val markerTop = with(density) { 30.dp.toPx() }
                    val markerBottom = with(density) { 44.dp.toPx() }
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
                    val lineY = with(density) { (baseY + sin(index * 0.85f) * 6f).toDp() }
                    val cardY = lineY + 24.dp
                    val highlighted = point.key == centerKey
                    val dragOffset = remember(point.key) { mutableStateOf(Offset.Zero) }
                    val cardWidth = when (point.kind) {
                        2 -> 236.dp
                        1 -> 108.dp
                        else -> 100.dp
                    }
                    val halfCardPx = with(density) { cardWidth.toPx() / 2f }

                    // Datum direkt über der Nadel des Eintrags.
                    if (point.kind == 2) {
                        Text(
                            text = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
                                .format(Date(item?.time ?: System.currentTimeMillis())),
                            color = ink,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(
                                x = with(density) { (point.x - 44.dp.toPx()).toDp() },
                                y = lineY - 50.dp,
                            ),
                        )
                    }

                    // Genau ein Pin je Ereignis: Eintrag oder einzelner Pin.
                    // Fotos bekommen keinen eigenen Pin mehr.
                    if (point.kind == 1 || point.kind == 2) {
                        Icon(
                            painter = painterResource(R.drawable.ic_stop_marker),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier
                                .offset(
                                    x = with(density) { (point.x - 17.dp.toPx()).toDp() },
                                    y = lineY - 32.dp,
                                )
                                .size(34.dp)
                                .scale(if (highlighted) 1.15f else 1f),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .offset(
                                x = with(density) {
                                    (point.x - halfCardPx).toDp() + dragOffset.value.x.toDp()
                                },
                                y = cardY + with(density) { dragOffset.value.y.toDp() },
                            )
                            .width(cardWidth)
                            .scale(if (highlighted) 1.08f else 1f)
                            .pointerInput(point.key) {
                                var dragX = 0f
                                var dragY = 0f
                                var grabX = 0f
                                var grabY = 0f
                                var grabSet = false
                                var edgeJob: kotlinx.coroutines.Job? = null
                                // Ein Foto darf nur in einen Eintrag desselben Tages.
                                val photoDay = if (point.kind == 3) {
                                    point.item?.time?.let { dayKey(it) }
                                } else {
                                    null
                                }
                                fun isAllowedTarget(candidate: TimelinePoint, x: Float): Boolean {
                                    if (candidate.kind != 2 || abs(candidate.x - x) >= 170f) {
                                        return false
                                    }
                                    if (photoDay == null) return true
                                    return candidate.item?.time?.let { dayKey(it) } == photoDay
                                }
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
                                        // Am Bildschirmrand scrollt die Zeitleiste mit.
                                        val rootX = timelineBounds.left +
                                            point.x - halfCardPx - scrollState.value +
                                            change.position.x
                                        val edge = with(density) { 56.dp.toPx() }
                                        val edgeDelta = when {
                                            rootX < timelineBounds.left + edge -> -24f
                                            rootX > timelineBounds.right - edge -> 24f
                                            else -> 0f
                                        }
                                        if (edgeDelta != 0f) {
                                            edgeJob?.cancel()
                                            edgeJob = scrollScope.launch {
                                                scrollState.scrollTo(
                                                    scrollState.value + edgeDelta.roundToInt(),
                                                )
                                            }
                                        }
                                        // Nur gültige Ziele leuchten auf.
                                        dragTargetEntryId = points.firstOrNull { candidate ->
                                            isAllowedTarget(candidate, point.x + dragX)
                                        }?.item?.entryId
                                    },
                                    onDragEnd = {
                                        edgeJob?.cancel()
                                        dragOffset.value = Offset.Zero
                                        dragTargetEntryId = null
                                        val source = when {
                                            point.kind == 3 ->
                                                point.photoId?.let { "photo-$it" }
                                            point.kind == 1 ->
                                                point.item?.stopId?.let { "stop-$it" }
                                            else -> null
                                        }
                                        if (point.kind == 2 && item?.entryId != null) {
                                            val cardLeft = timelineBounds.left +
                                                point.x - halfCardPx -
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
                                                isAllowedTarget(candidate, finalX)
                                            }?.item?.entryId
                                            onDropElement(source, target)
                                        }
                                    },
                                    onDragCancel = {
                                        edgeJob?.cancel()
                                        dragOffset.value = Offset.Zero
                                        dragTargetEntryId = null
                                    },
                                )
                            }
                            // Kein Auf-/Zuklappen mehr: Foto öffnet Detail, Pin öffnet Element.
                            .then(
                                when (point.kind) {
                                    3 -> Modifier.clickable {
                                        point.photoId?.let(onPhotoClick)
                                    }
                                    1 -> Modifier.clickable {
                                        point.item?.let(onItemClick)
                                    }
                                    else -> Modifier
                                },
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        when (point.kind) {
                            // Der Tag steht als "Tag 1", "Tag 2" … oben links im Rahmen.
                            0 -> {}

                            3, 4 -> {
                                val bitmap = point.photoId?.let(photoBitmap)
                                val ratio = if (bitmap != null && bitmap.height > 0) {
                                    (bitmap.width.toFloat() / bitmap.height.toFloat())
                                        .coerceIn(0.7f, 1.6f)
                                } else {
                                    4f / 3f
                                }
                                val photoWidth = if (point.kind == 4) 124.dp else 116.dp
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

                            else -> {
                                // Immer offen: Titel, Text und Fotos rechts daneben.
                                val entryId = item?.entryId
                                val entryPhotos = entryId?.let { photosByEntry[it] }.orEmpty()
                                val body = entryId?.let { entryBodies[it] }.orEmpty()
                                val ordered = if (centerPhotoId != null &&
                                    entryPhotos.contains(centerPhotoId)
                                ) {
                                    listOf(centerPhotoId) + entryPhotos.filter { it != centerPhotoId }
                                } else {
                                    entryPhotos
                                }
                                Row(
                                    modifier = Modifier
                                        .width(236.dp)
                                        .background(fieldGray, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                ) {
                                    // Text links, Fotos direkt daneben – das spart Höhe.
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = point.label,
                                            color = ink,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 2,
                                            modifier = Modifier.clickable { item?.let(onItemClick) },
                                        )
                                        if (body.isNotBlank()) {
                                            Spacer(Modifier.height(3.dp))
                                            Text(
                                                text = body,
                                                color = ink,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                            )
                                        }
                                    }
                                    if (ordered.isNotEmpty()) {
                                        Spacer(Modifier.width(6.dp))
                                        Row(
                                            modifier = Modifier
                                                .width(84.dp)
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            ordered.forEach { photoId ->
                                                val bitmap = photoBitmap(photoId)
                                                Box(
                                                    modifier = Modifier
                                                        .size(56.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(paper)
                                                        .border(1.dp, Color(0xFFDCCDB4))
                                                        .clickable { onPhotoClick(photoId) },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    if (bitmap != null) {
                                                        Image(
                                                            bitmap = bitmap,
                                                            contentDescription = "Foto öffnen",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize(),
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                        .offset(x = 8.dp, y = 0.dp)
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
                            .offset(x = x, y = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(paper)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }

        TimelineScrollbar(scrollState = scrollState)
    }
}

/**
 * Schmale, bedienbare Leiste unten: Der Regler zeigt die absolute Position in
 * der gesamten Zeitleiste. Tippen oder Ziehen springt direkt dorthin.
 */
@Composable
private fun TimelineScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val scrollScope = rememberCoroutineScope()
    var trackWidthPx by remember { mutableStateOf(0f) }
    val maxScroll = scrollState.maxValue
    val thumbFraction = if (maxScroll <= 0) {
        1f
    } else {
        val viewport = scrollState.viewportSize.coerceAtLeast(1)
        (viewport.toFloat() / (viewport + maxScroll)).coerceIn(0.08f, 1f)
    }

    fun scrollToScreenX(x: Float, centerThumb: Boolean) {
        if (trackWidthPx <= 0f || maxScroll <= 0) return
        val raw = (x / trackWidthPx).coerceIn(0f, 1f)
        val fraction = if (centerThumb && thumbFraction < 1f) {
            ((raw - thumbFraction / 2f) / (1f - thumbFraction)).coerceIn(0f, 1f)
        } else {
            raw
        }
        scrollScope.launch { scrollState.scrollTo((fraction * maxScroll).roundToInt()) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .background(paper)
            .onGloballyPositioned { trackWidthPx = it.size.width.toFloat() }
            .pointerInput(maxScroll) {
                detectTapGestures { offset -> scrollToScreenX(offset.x, centerThumb = false) }
            }
            .pointerInput(maxScroll) {
                detectDragGestures(
                    onDragStart = { scrollToScreenX(it.x, centerThumb = true) },
                    onDrag = { change, _ ->
                        change.consume()
                        scrollToScreenX(change.position.x, centerThumb = true)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ink.copy(alpha = 0.16f)),
        )
        val thumbWidth = (trackWidthPx * thumbFraction).roundToInt()
            .coerceAtLeast(with(density) { 28.dp.toPx().roundToInt() })
        Box(
            modifier = Modifier
                .padding(horizontal = 2.dp)
                .offset(
                    x = with(density) {
                        ((trackWidthPx - thumbWidth) * scrollFraction(scrollState)).toDp()
                    },
                )
                .width(with(density) { thumbWidth.toDp() })
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(teal),
        )
    }
}

private fun scrollFraction(scrollState: ScrollState): Float =
    if (scrollState.maxValue <= 0) 0f else {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
