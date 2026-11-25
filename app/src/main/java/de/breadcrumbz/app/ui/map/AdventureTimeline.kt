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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
    /** Breite des Elements, aus seinem echten Inhalt berechnet. */
    val slot: Float = 0f,
    /** Für Einträge: Breite des Textblocks. */
    val textWidthPx: Float = 0f,
    /** Für Einträge/Fotos: Breiten der Bilder in der Reihenfolge. */
    val photoWidthsPx: List<Float> = emptyList(),
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
    onClick: (() -> Unit)? = null,
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
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
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
    /** Das Pin-Symbol öffnet die Pin-Detailansicht – auch bei Einträgen mit Ort. */
    onPinClick: (String) -> Unit = {},
    photoBitmap: (String) -> ImageBitmap?,
    photoTimes: Map<String, Long> = emptyMap(),
    entryBodies: Map<String, String> = emptyMap(),
    mapZoom: Float = 14f,
    externalKey: String? = null,
    /** Foto, das von Karte oder Zeitleiste markiert wurde (sanftes Leuchten). */
    highlightPhotoId: String? = null,
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
    val zoomFactor = (mapZoom / 14f).coerceIn(0.85f, 1.25f)
    val scrollState = rememberScrollState()
    var timelineBounds by remember { mutableStateOf(Rect.Zero) }
    val availableHeightPx = if (timelineBounds.height > 0f) {
        timelineBounds.height
    } else {
        with(density) { 320.dp.toPx() }
    }
    // Karten beginnen bei cardY = baseY + sin(6) + 24 = maximal 104 dp.
    // Der Eintragsrahmen schließt direkt daran an; die Bilder füllen ihn bis
    // auf den kleinen Abstand nach unten und die Kartenpolsterung komplett aus.
    val cardTopMaxPx = with(density) { 104.dp.toPx() }
    val entryTopPx = with(density) { 88.dp.toPx() }
    val photoHeightPx = (availableHeightPx - cardTopMaxPx -
        with(density) { 22.dp.toPx() })
        .coerceAtLeast(with(density) { 76.dp.toPx() })
    val photoCaptionHeightPx = with(density) { 20.dp.toPx() }

    /** Textbreite aus dem echten Inhalt – kein fester Platz für leere Felder. */
    fun textWidthPx(text: String, maxChars: Int, minDp: Float, maxDp: Float): Float {
        if (text.isBlank()) return 0f
        return with(density) {
            (text.take(maxChars).length * 6.2f).coerceIn(minDp, maxDp).dp.toPx()
        }
    }

    /** Ein Bild nimmt fast die ganze Eintragshöhe ein – zugeschnitten auf 4:3. */
    fun photoWidthPx(photoId: String, heightPx: Float = photoHeightPx): Float {
        val bitmap = photoBitmap(photoId)
        val ratio = if (bitmap != null && bitmap.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else {
            4f / 3f
        }
        return (heightPx * ratio.coerceIn(0.75f, 1.5f) * zoomFactor)
            .coerceIn(with(density) { 64.dp.toPx() }, with(density) { 190.dp.toPx() })
    }
    // Beim Ziehen wird der Eintrag hervorgehoben, auf den man zusteuert.
    var dragTargetEntryId by remember { mutableStateOf<String?>(null) }
    // Ein gerade gezogenes Eintragsfoto darf nicht zusätzlich die Eintragskarte ziehen.
    var photoDragActive by remember { mutableStateOf(false) }
    // Schon beim Antippen blockieren, damit die Karte keinen Drag startet.
    var photoPressed by remember { mutableStateOf(false) }
    // Verhindert, dass eine programmgesteuerte Scrollbewegung die Karte zurückzieht.
    var lastProgrammaticScrollAt by remember { mutableStateOf(0L) }
    val startPx = with(density) { 34.dp.toPx() }
    val baseY = with(density) { 74.dp.toPx() }

    val orderedStops = remember(stops) { stops.sortedBy { it.sortIndex } }
    val stopKm = remember(orderedStops) {
        val result = mutableMapOf<String, Double>()
        var total = 0.0
        // An-/Abreise-Pins liegen außerhalb der Gesamtstrecke: weder die
        // Anfahrt zu ihnen noch die Abfahrt von ihnen wird gezählt.
        var previousKept: Stop? = null
        orderedStops.forEach { stop ->
            if (!stop.isArrivalDeparture) {
                previousKept?.let { previous ->
                    total += haversineMeters(
                        previous.latitude, previous.longitude,
                        stop.latitude, stop.longitude,
                    ) / 1_000.0
                }
                previousKept = stop
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
        items, dayLabels, photosByEntry, entryBodies, photoBitmap, stopKm,
        photoHeightPx, photoCaptionHeightPx, zoomFactor, startPx,
    ) {
        val result = mutableListOf<TimelinePoint>()
        val kmOf = { item: TimelineItem -> item.stopId?.let { stopKm[it] } ?: 0.0 }
        val gapPx = with(density) { 8.dp.toPx() }
        val padPx = with(density) { 16.dp.toPx() }
        val iconPx = with(density) { 22.dp.toPx() }
        val daySlotPx = with(density) { 54.dp.toPx() }
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

        items.groupBy { dayKey(it.time) }.toSortedMap().forEach { (key, dayItems) ->
            val first = dayItems.first()
            result.add(
                TimelinePoint(
                    key = "day-$key",
                    label = dayLabels[key].orEmpty(),
                    detail = dateFormat.format(Date(first.time)),
                    x = 0f,
                    km = kmOf(first),
                    dayKey = key,
                    item = null,
                    photoId = null,
                    entryId = null,
                    kind = 0,
                    slot = daySlotPx,
                ),
            )
            dayItems.forEach { item ->
                val kind = when (item.kind) {
                    TimelineKind.STOP -> 1
                    TimelineKind.ENTRY -> 2
                    TimelineKind.PHOTO -> 3
                }
                // Ein leerer Titel wird nirgends als Ersatztext angezeigt.
                val label = item.title
                val photoIds = when (item.kind) {
                    TimelineKind.ENTRY -> photosByEntry[item.entryId].orEmpty()
                    TimelineKind.PHOTO -> listOfNotNull(item.photoId)
                    else -> emptyList()
                }
                val imageHeight = if (item.kind == TimelineKind.PHOTO) {
                    photoHeightPx - photoCaptionHeightPx
                } else {
                    photoHeightPx
                }
                val photoWidths = photoIds.map { photoWidthPx(it, imageHeight) }
                val body = if (item.kind == TimelineKind.ENTRY) {
                    entryBodies[item.entryId].orEmpty()
                } else {
                    ""
                }
                val textWidth = when (item.kind) {
                    TimelineKind.STOP -> textWidthPx(label, 20, 44f, 132f)
                    TimelineKind.ENTRY -> maxOf(
                        textWidthPx(label, 24, 56f, 150f),
                        textWidthPx(body, 70, 56f, 150f),
                    )
                    TimelineKind.PHOTO -> textWidthPx(label, 20, 44f, 120f)
                    else -> 0f
                }
                val stripWidth = if (photoWidths.isEmpty()) {
                    0f
                } else {
                    photoWidths.sum() + gapPx * (photoWidths.size - 1)
                }
                val slot = when (item.kind) {
                    TimelineKind.STOP -> iconPx +
                        (if (textWidth > 0f) gapPx + textWidth else 0f) + padPx
                    TimelineKind.ENTRY -> textWidth +
                        (if (stripWidth > 0f) gapPx + stripWidth else 0f) + padPx
                    TimelineKind.PHOTO -> (photoWidths.firstOrNull()
                        ?: with(density) { 88.dp.toPx() }) +
                        (if (textWidth > 0f) gapPx + textWidth else 0f) + padPx
                    else -> daySlotPx
                }.coerceAtLeast(with(density) { 44.dp.toPx() })
                result.add(
                    TimelinePoint(
                        key = item.id,
                        label = label,
                        detail = "",
                        x = 0f,
                        km = kmOf(item),
                        dayKey = key,
                        item = item,
                        photoId = item.photoId,
                        entryId = item.entryId,
                        kind = kind,
                        slot = slot,
                        textWidthPx = textWidth,
                        photoWidthsPx = photoWidths,
                    ),
                )
            }
        }
        var cursor = startPx
        result.forEachIndexed { index, point ->
            result[index] = point.copy(x = cursor + point.slot / 2f)
            cursor += point.slot + gapPx
        }
        result
    }

    val contentWidthPx = startPx + points.sumOf { it.slot.toDouble() }.toFloat() +
        (points.size - 1).coerceAtLeast(0) * with(density) { 8.dp.toPx() } +
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
        // Ein Foto im Eintrag hat keinen eigenen Punkt: wir scrollen zu seinem
        // Eintrag und markieren das einzelne Bild dort.
        val targetKey = if (externalKey.startsWith("entryphoto-")) {
            val photoId = externalKey.removePrefix("entryphoto-")
            photosByEntry.entries.firstOrNull { photoId in it.value }?.key
                ?.let { "entry-$it" }
                ?: externalKey
        } else {
            externalKey
        }
        val index = points.indexOfFirst { it.key == targetKey }
        if (index >= 0) {
            val maxScroll = (contentWidthPx - viewportPx).coerceAtLeast(0f).roundToInt()
            val target = (points[index].x - viewportPx / 2f)
                .roundToInt().coerceAtLeast(0).coerceAtMost(maxScroll)
            if (abs(scrollState.value - target) > 24) {
                // Die Karte hat das gewünschte Element vorgegeben: nicht retour melden.
                lastProgrammaticScrollAt = System.currentTimeMillis()
                scrollState.animateScrollTo(target)
                lastProgrammaticScrollAt = System.currentTimeMillis()
            }
        }
    }

    /** Kilometer-Marke liegt genau zwischen den beiden Punkten, die sie umschließen. */
    fun milestoneX(target: Double): Float {
        if (points.isEmpty()) return 0f
        if (points.first().km >= target) return points.first().x
        for (index in 1 until points.size) {
            val previous = points[index - 1]
            val current = points[index]
            if (current.km >= target) {
                val span = current.km - previous.km
                val fraction = if (span > 0.0) {
                    ((target - previous.km) / span).coerceIn(0.0, 1.0).toFloat()
                } else {
                    1f
                }
                return previous.x + (current.x - previous.x) * fraction
            }
        }
        return points.last().x
    }

    val kmTargets = remember(totalKm) {
        buildList {
            var value = 100.0
            while (value <= totalKm) {
                add(value)
                value += 100.0
            }
            if (totalKm > 0.0) add(totalKm)
        }.distinct()
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
                if (System.currentTimeMillis() - lastProgrammaticScrollAt > 600L) {
                    onUserCenterKey(centerKey)
                }
            }

            val dayPad = with(density) { 12.dp.toPx() }
            val dayGap = with(density) { 14.dp.toPx() }
            // Kleiner als der Abstand zwischen zwei Punkten (8 dp), sonst
            // berühren sich die Rahmen benachbarter Einträge.
            // Größer als die Innen-Padding der Karten (8 dp), damit kein Text
            // über den Rahmen ragt. Die Trennung der Rahmen erfolgt weiter unten.
            val entryPad = with(density) { 12.dp.toPx() }
            val dayTopPx = with(density) { 46.dp.toPx() }
            val dayBottomPx = with(density) { (maxHeight - 8.dp).toPx() }
            val entryBottomPx = with(density) { (maxHeight - 14.dp).toPx() }

            // Die Klammer-Systematik [Tag (Eintrag Bilder) (Eintrag Bilder)]:
            // Jeder Rahmen wird aus den echten Breiten seiner Kinder gebaut.
            val dayFrames = remember(points, dayPad, dayGap, dayTopPx, dayBottomPx) {
                val dayKeys = points.filter { it.kind == 0 }
                    .mapNotNull { it.dayKey }
                    .distinct()
                dayKeys.mapIndexed { index, key ->
                    val children = points.filter { it.dayKey == key }
                    if (children.isEmpty()) return@mapIndexed key to Rect.Zero
                    // Alle Karten zählen: eine breite Karte in der Mitte darf
                    // nicht über den Tagesrahmen hinausragen.
                    val firstEdge = children.minOf { it.x - it.slot / 2f } - dayPad
                    val lastEdge = children.maxOf { it.x + it.slot / 2f } + dayPad
                    val left = if (index == 0) {
                        firstEdge
                    } else {
                        val prevChildren = points.filter { it.dayKey == dayKeys[index - 1] }
                        val prevEdge = prevChildren.maxOf { it.x + it.slot / 2f } + dayPad
                        (prevEdge + firstEdge) / 2f + dayGap / 2f
                    }
                    val right = if (index == dayKeys.lastIndex) {
                        lastEdge
                    } else {
                        val nextChildren = points.filter { it.dayKey == dayKeys[index + 1] }
                        val nextEdge = nextChildren.minOf { it.x - it.slot / 2f } - dayPad
                        (lastEdge + nextEdge) / 2f - dayGap / 2f
                    }
                    val safeLeft = minOf(left, right - 8f)
                    val safeRight = maxOf(right, safeLeft + 8f)
                    key to Rect(safeLeft, dayTopPx, safeRight, dayBottomPx)
                }.filter { it.second != Rect.Zero }.toMap()
            }
            // Eintragsrahmen bleiben innerhalb ihres Tagesrahmens und
            // überschneiden sich garantiert nicht: pro Tag werden sie in
            // Leserichtung sortiert und mit Mindestabstand getrennt.
            val entryFrames = remember(
                points, dayFrames, entryPad, entryTopPx, entryBottomPx,
            ) {
                val raw = points.filter { it.kind == 2 && it.item?.entryId != null }
                    .mapNotNull { entry ->
                        val id = entry.item?.entryId ?: return@mapNotNull null
                        val children = points.filter { it.entryId == id }
                        if (children.isEmpty()) return@mapNotNull null
                        val dayRect = entry.dayKey?.let { dayFrames[it] }
                        // Die breiteste Karte liegt nicht zwangsläufig am Rand:
                        // deshalb alle Karten des Eintrags berücksichtigen.
                        val rawLeft = children.minOf { it.x - it.slot / 2f } - entryPad
                        val rawRight = children.maxOf { it.x + it.slot / 2f } + entryPad
                        val left = if (dayRect != null) maxOf(rawLeft, dayRect.left) else rawLeft
                        val right = if (dayRect != null) minOf(rawRight, dayRect.right) else rawRight
                        if (right <= left + 4f) return@mapNotNull null
                        Triple(entry.dayKey, id, Rect(left, entryTopPx, right, entryBottomPx))
                    }
                val minGap = with(density) { 6.dp.toPx() }
                val separated = linkedMapOf<String, Rect>()
                raw.groupBy({ it.first }, { it.second to it.third })
                    .forEach { (dayKey, items) ->
                        val dayRect = dayKey?.let { dayFrames[it] }
                        var lastRight = Float.NEGATIVE_INFINITY
                        items.sortedBy { it.second.left }.forEach { (id, rect) ->
                            var left = if (lastRight == Float.NEGATIVE_INFINITY) {
                                rect.left
                            } else {
                                maxOf(rect.left, lastRight + minGap)
                            }
                            var right = rect.right
                            if (dayRect != null) {
                                left = left.coerceAtMost(dayRect.right - 8f)
                                    .coerceAtLeast(dayRect.left)
                                right = minOf(right, dayRect.right)
                            }
                            if (right > left + 4f) {
                                separated[id] = Rect(left, rect.top, right, rect.bottom)
                                lastRight = right
                            }
                        }
                    }
                separated
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
                    val entryPoint = points.firstOrNull { it.entryId == id && it.item != null }
                    TimelineFrame(
                        rect = rect,
                        fill = fieldGray,
                        highlight = id == dragTargetEntryId,
                        onClick = { entryPoint?.item?.let(onItemClick) },
                    )
                }
                // "Tag 1", "Tag 2" … als reiner Text, ohne Etikett-Hintergrund.
                dayFrames.forEach { (key, rect) ->
                    val dayPoint = points.firstOrNull { it.kind == 0 && it.dayKey == key }
                    Text(
                        text = dayLabels[key].orEmpty(),
                        color = teal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(
                            x = with(density) { (rect.left + 18f).toDp() },
                            y = with(density) { (rect.top + 6f).toDp() },
                        ),
                    )
                    // Datum um 90° gegen den Uhrzeigersinn am linken Rand.
                    // Der Abstand zum Rahmen verhindert, dass die Schrift am
                    // Rand klebt; von rechts lesbar und gut erkennbar.
                    if (dayPoint?.detail?.isNotBlank() == true) {
                        Text(
                            text = dayPoint.detail,
                            color = ink.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .offset(
                                    x = with(density) { (rect.left + 22f).toDp() },
                                    y = with(density) {
                                        (rect.top + rect.height / 2f + 30f).toDp()
                                    },
                                )
                                .graphicsLayer(
                                    rotationZ = -90f,
                                    transformOrigin = TransformOrigin(0f, 0.5f),
                                ),
                        )
                    }
                }

                androidx.compose.foundation.Canvas(
                    modifier = Modifier.width(contentWidth).fillMaxHeight(),
                ) {
                    if (points.isEmpty()) return@Canvas

                    val yAt = { index: Int ->
                        baseY + (sin(index * 0.85f) * with(density) { 6.dp.toPx() }).toFloat()
                    }
                    // Identische Werte wie auf der Karte (RouteStyling).
                    val path = Path()
                    path.moveTo(points.first().x, yAt(0))
                    for (index in 1 until points.size) {
                        val middle = (points[index - 1].x + points[index].x) / 2
                        path.quadraticBezierTo(middle, yAt(index - 1), points[index].x, yAt(index))
                    }
                    // Weißer Unterzug, darüber die durchgezogene Linie – wie auf der Karte.
                    drawPath(
                        path, Color(RouteStyling.UNDER_COLOR),
                        style = Stroke(width = RouteStyling.UNDER_WIDTH),
                    )
                    drawPath(
                        path, Color(RouteStyling.LINE_COLOR),
                        style = Stroke(width = RouteStyling.LINE_WIDTH),
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
                        val x = milestoneX(target)
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
                    val cardWidth = with(density) { point.slot.toDp() }
                    val halfCardPx = point.slot / 2f

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
                                .clickable {
                                    point.item?.stopId?.let(onPinClick)
                                }
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
                            // Nur minimal vergrößern, sonst schiebt die Karte
                            // in den Rahmen des Nachbarn.
                            .scale(if (highlighted) 1.03f else 1f)
                            .pointerInput(point.key) {
                                var dragX = 0f
                                var dragY = 0f
                                var grabX = 0f
                                var grabY = 0f
                                var grabSet = false
                                var edgeJob: kotlinx.coroutines.Job? = null
                                var blockedByPhoto = false
                                var startScroll = 0
                                // Bilder dürfen in Einträge jeden Tages wandern;
                                // der alte Eintrag bleibt dabei erhalten.
                                fun isAllowedTarget(
                                    candidate: TimelinePoint,
                                    x: Float,
                                ): Boolean = candidate.kind == 2 &&
                                    abs(candidate.x - x) < candidate.slot / 2f + 56f

                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        if (photoDragActive || photoPressed) {
                                            blockedByPhoto = true
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragX = 0f
                                        dragY = 0f
                                        grabSet = false
                                        startScroll = scrollState.value
                                        dragOffset.value = Offset.Zero
                                    },
                                    onDrag = { change, amount ->
                                        if (blockedByPhoto) {
                                            return@detectDragGesturesAfterLongPress
                                        }
                                        change.consume()
                                        if (!grabSet) {
                                            grabX = change.position.x
                                            grabY = change.position.y
                                            grabSet = true
                                        }
                                        dragX += amount.x
                                        dragY += amount.y
                                        // Scrollen während des Ziehens mitnehmen,
                                        // sonst bleibt die Karte unter dem Finger zurück.
                                        val scrollDelta = scrollState.value - startScroll
                                        dragOffset.value = Offset(
                                            dragX + scrollDelta, dragY,
                                        )
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
                                        // Nur gültige Ziele leuchten auf. Das
                                        // Rand-Scrollen zählt zur Drag-Bewegung dazu.
                                        val finalX = point.x + dragX +
                                            (scrollState.value - startScroll)
                                        dragTargetEntryId = points.firstOrNull { candidate ->
                                            isAllowedTarget(candidate, finalX)
                                        }?.item?.entryId
                                    },
                                    onDragEnd = {
                                        edgeJob?.cancel()
                                        if (blockedByPhoto) {
                                            blockedByPhoto = false
                                            return@detectDragGesturesAfterLongPress
                                        }
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
                                                point.x - halfCardPx - startScroll
                                            val cardTop = timelineBounds.top +
                                                with(density) { cardY.toPx() }
                                            onDropEntry(
                                                item.entryId!!,
                                                cardLeft + grabX + dragX,
                                                cardTop + grabY + dragY,
                                            )
                                        }
                                        if (source != null) {
                                            val finalX = point.x + dragX +
                                                (scrollState.value - startScroll)
                                            val target = points.firstOrNull { candidate ->
                                                isAllowedTarget(candidate, finalX)
                                            }?.item?.entryId
                                            onDropElement(source, target)
                                        }
                                    },
                                    onDragCancel = {
                                        edgeJob?.cancel()
                                        blockedByPhoto = false
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
                                val imageWidth = point.photoWidthsPx.firstOrNull()
                                    ?.let { with(density) { it.toDp() } } ?: 100.dp
                                val imageHeight = with(density) {
                                    (photoHeightPx - photoCaptionHeightPx).toDp()
                                }
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .rotate(if (point.photoId == centerPhotoId) -4f else -8f)
                                        .shadow(
                                            if (point.photoId == highlightPhotoId) 8.dp else 0.dp,
                                            RoundedCornerShape(4.dp),
                                            ambientColor = Color(0xFFFFE08A),
                                            spotColor = Color(0xFFFFE08A),
                                        )
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(paper)
                                        .border(
                                            if (highlighted ||
                                                point.photoId == highlightPhotoId
                                            ) 2.dp else 1.dp,
                                            if (highlighted ||
                                                point.photoId == highlightPhotoId
                                            ) accent else Color(0xFFDCCDB4),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .padding(start = 4.dp, end = 4.dp, top = 4.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(imageWidth)
                                            .height(imageHeight)
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
                                    if (point.label.isNotBlank()) {
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            text = point.label,
                                            color = ink,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }

                            1, 5 -> Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(cardWidth)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(paper)
                                    .border(
                                        if (highlighted) 2.dp else 1.dp,
                                        if (highlighted) accent else Color(0xFFDCCDB4),
                                        RoundedCornerShape(8.dp),
                                    )
                                    .padding(horizontal = 6.dp, vertical = 5.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PlacePinIcon(
                                        tint = ink,
                                        size = with(density) {
                                            MaterialTheme.typography.bodySmall.fontSize.toDp()
                                        },
                                    )
                                    if (point.label.isNotBlank()) {
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = point.label,
                                            color = ink,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 2,
                                        )
                                    }
                                }
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
                                        .width(cardWidth)
                                        .background(fieldGray, RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                        .clickable { item?.let(onItemClick) },
                                ) {
                                    // Text links, Fotos rechts – Breite kommt aus dem Inhalt.
                                    if (point.textWidthPx > 0f) {
                                        Column(
                                            modifier = Modifier
                                                .width(with(density) { point.textWidthPx.toDp() }),
                                        ) {
                                            if (point.label.isNotBlank()) {
                                                Text(
                                                    text = point.label,
                                                    color = ink,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 2,
                                                )
                                            }
                                            if (body.isNotBlank()) {
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    text = body,
                                                    color = ink,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    maxLines = 3,
                                                )
                                            }
                                        }
                                    }
                                    if (ordered.isNotEmpty()) {
                                        Spacer(Modifier.width(8.dp))
                                        // Derselbe Abstand wie bei der Breitenberechnung,
                                        // sonst ragt der Streifen über den Rahmen hinaus.
                                        val stripWidth = point.photoWidthsPx.sum() +
                                            (point.photoWidthsPx.size - 1).coerceAtLeast(0) *
                                                with(density) { 8.dp.toPx() }
                                        Row(
                                            modifier = Modifier
                                                .width(with(density) { stripWidth.toDp() }),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            ordered.forEach { photoId ->
                                                val bitmap = photoBitmap(photoId)
                                                val ratio = if (bitmap != null && bitmap.height > 0) {
                                                    bitmap.width.toFloat() / bitmap.height.toFloat()
                                                } else {
                                                    4f / 3f
                                                }
                                                val widthPx = (photoHeightPx *
                                                    ratio.coerceIn(0.75f, 1.5f) * zoomFactor)
                                                    .coerceIn(
                                                        with(density) { 64.dp.toPx() },
                                                        with(density) { 190.dp.toPx() },
                                                    )
                                                // Markierte Bilder leuchten sanft.
                                                val photoMarked = photoId == highlightPhotoId
                                                var photoDragOffset by remember(photoId) {
                                                    mutableStateOf(Offset.Zero)
                                                }
                                                var photoRootX by remember(photoId) {
                                                    mutableStateOf(point.x)
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .width(with(density) { widthPx.toDp() })
                                                        .height(with(density) { photoHeightPx.toDp() })
                                                        .offset(
                                                            x = with(density) {
                                                                photoDragOffset.x.toDp()
                                                            },
                                                            y = with(density) {
                                                                photoDragOffset.y.toDp()
                                                            },
                                                        )
                                                        .onGloballyPositioned { coordinates ->
                                                            if (!photoPressed && !photoDragActive) {
                                                                photoRootX = coordinates
                                                                    .boundsInRoot().center.x
                                                            }
                                                        }
                                                        .pointerInput(photoId) {
                                                            detectTapGestures(onPress = {
                                                                photoPressed = true
                                                                tryAwaitRelease()
                                                                photoPressed = false
                                                            })
                                                        }
                                                        .pointerInput(photoId) {
                                                            var dragX = 0f
                                                            var dragY = 0f
                                                            var startRootX = photoRootX
                                                            var startScroll = scrollState.value
                                                            var edgeJob: kotlinx.coroutines.Job? = null
                                                            fun targetEntry(x: Float): String? =
                                                                entryFrames.entries.firstOrNull { entry ->
                                                                    entry.key != entryId &&
                                                                        x >= entry.value.left - 16f &&
                                                                        x <= entry.value.right + 16f
                                                                }?.key

                                                            detectDragGesturesAfterLongPress(
                                                                onDragStart = {
                                                                    haptic.performHapticFeedback(
                                                                        HapticFeedbackType.LongPress,
                                                                    )
                                                                    photoDragActive = true
                                                                    dragX = 0f
                                                                    dragY = 0f
                                                                    startRootX = photoRootX
                                                                    startScroll = scrollState.value
                                                                    photoDragOffset = Offset.Zero
                                                                    dragTargetEntryId = null
                                                                },
                                                                onDrag = { change, amount ->
                                                                    change.consume()
                                                                    dragX += amount.x
                                                                    dragY += amount.y
                                                                    val scrollDelta =
                                                                        scrollState.value - startScroll
                                                                    photoDragOffset =
                                                                        Offset(dragX + scrollDelta, dragY)
                                                                    val rootX = startRootX + dragX
                                                                    val edge = with(density) {
                                                                        56.dp.toPx()
                                                                    }
                                                                    val edgeDelta = when {
                                                                        rootX < timelineBounds.left +
                                                                            edge -> -24f
                                                                        rootX > timelineBounds.right -
                                                                            edge -> 24f
                                                                        else -> 0f
                                                                    }
                                                                    if (edgeDelta != 0f) {
                                                                        edgeJob?.cancel()
                                                                        edgeJob = scrollScope.launch {
                                                                            scrollState.scrollTo(
                                                                                scrollState.value +
                                                                                    edgeDelta.roundToInt(),
                                                                            )
                                                                        }
                                                                    }
                                                                    val contentX = rootX -
                                                                        timelineBounds.left +
                                                                        scrollState.value
                                                                    dragTargetEntryId =
                                                                        targetEntry(contentX)
                                                                },
                                                                onDragEnd = {
                                                                    edgeJob?.cancel()
                                                                    photoDragActive = false
                                                                    photoDragOffset = Offset.Zero
                                                                    val contentX = startRootX + dragX -
                                                                        timelineBounds.left +
                                                                        scrollState.value
                                                                    val target = targetEntry(contentX)
                                                                    dragTargetEntryId = null
                                                                    if (target != null) {
                                                                        onDropElement(
                                                                            "photo-$photoId",
                                                                            target,
                                                                        )
                                                                    }
                                                                },
                                                                onDragCancel = {
                                                                    edgeJob?.cancel()
                                                                    photoDragActive = false
                                                                    photoDragOffset = Offset.Zero
                                                                    dragTargetEntryId = null
                                                                },
                                                            )
                                                        }
                                                        .shadow(
                                                            if (photoMarked) 8.dp else 0.dp,
                                                            RoundedCornerShape(4.dp),
                                                            ambientColor = Color(0xFFFFE08A),
                                                            spotColor = Color(0xFFFFE08A),
                                                        )
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(paper)
                                                        .border(
                                                            if (photoMarked) 2.dp else 1.dp,
                                                            if (photoMarked) accent
                                                            else Color(0xFFDCCDB4),
                                                            RoundedCornerShape(4.dp),
                                                        )
                                                        .clickable { onPhotoClick(photoId) },
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
                                    }
                                }
                            }
                        }
                    }
                }

                if (points.isNotEmpty()) kmTargets.forEach { target ->
                    val x = with(density) { (milestoneX(target) - 34.dp.toPx()).toDp() }
                    val label = if (target == totalKm) {
                        "${String.format(Locale.GERMANY, "%.0f", totalKm)} km"
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
