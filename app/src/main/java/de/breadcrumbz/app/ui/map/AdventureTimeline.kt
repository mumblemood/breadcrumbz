package de.breadcrumbz.app.ui.map

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.shape.GenericShape
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
import androidx.compose.ui.graphics.PathEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.breadcrumbz.app.R
import de.breadcrumbz.app.ui.theme.HandFont
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

// Schrift wie überall in der App: Titel in Handschrift (Caveat), Text in Nunito.
// Die Linie schwingt deutlich, aber ruhig: 12 dp hoch und runter.
private const val WAVE_DP = 12f
private const val WAVE_FREQUENCY = 0.7f

private val TimelineTitle = TextStyle(
    fontFamily = HandFont,
    fontWeight = FontWeight.Bold,
    fontSize = 20.sp,
    lineHeight = 22.sp,
)
private val TimelineTitleSmall = TimelineTitle.copy(fontSize = 17.sp, lineHeight = 19.sp)
private val TimelinePhotoCaption = TimelineTitle.copy(fontSize = 14.sp, lineHeight = 15.sp)

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

/** Klickbereich für einen Reiseabschnitt auf der gezeichneten Zeitleiste. */
private data class TimelineSegmentHit(
    val fromStopId: String,
    val toStopId: String,
    val left: Float,
    val right: Float,
) {
    val center: Float get() = (left + right) / 2f
}

private val ink = Color(0xFF3B2B20)
private val paper = Color(0xFFFBF4E4)
private val signWood = Color(0xFFD9B57E)
private val signPost = Color(0xFF8A6440)
/** Wegweiser-Brett: rechts spitz wie ein Pfeil. */
private val SignShape = GenericShape { size, _ ->
    val tip = size.height * 0.45f
    moveTo(0f, 0f)
    lineTo(size.width - tip, 0f)
    lineTo(size.width, size.height / 2f)
    lineTo(size.width - tip, size.height)
    lineTo(0f, size.height)
    close()
}
private val accent = Color(0xFFB8782F)
private val teal = Color(0xFF2E6B6B)
/** Genau das Papier aus dem tbe-Detailfenster. */
private val notebookPaper = Color(0xFFF6EFE1)
// zl A: Lesezeichen in Pergament statt Mint – passt zur alten Karte.
private val dayMarker = Color(0xFFEFE4CF)

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

/** Tagesrahmen und Lesezeichen als eine Fläche mit nur einer Außenlinie. */
@Composable
private fun DayFrameWithTab(label: String, rect: Rect) {
    val density = LocalDensity.current
    val extraTopPx = with(density) { 20.dp.toPx() }
    val widthPx = rect.width
    val heightPx = rect.height + extraTopPx
    val radius = with(density) { 14.dp.toPx() }
    val tabWidth = with(density) { 78.dp.toPx() }
    val tabRadius = with(density) { 9.dp.toPx() }
    val edgeGap = with(density) { 4.dp.toPx() }
    val minTabLeft = radius + edgeGap
    val maxTabLeft = (widthPx - tabWidth - edgeGap).coerceAtLeast(minTabLeft)
    val tabLeft = with(density) { 18.dp.toPx() }.coerceIn(minTabLeft, maxTabLeft)
    val strokeWidth = with(density) { 1.dp.toPx() }
    val tabTextX = with(density) { (tabLeft + 8.dp.toPx()).toDp() }

    Box(
        modifier = Modifier
            .offset(
                x = with(density) { rect.left.toDp() },
                y = with(density) { (rect.top - extraTopPx).toDp() },
            )
            .size(
                width = with(density) { widthPx.toDp() },
                height = with(density) { heightPx.toDp() },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val top = extraTopPx
            val bottom = heightPx
            val right = widthPx
            val path = Path().apply {
                moveTo(radius, top)
                lineTo(tabLeft, top)
                lineTo(tabLeft, tabRadius)
                quadraticBezierTo(tabLeft, 0f, tabLeft + tabRadius, 0f)
                lineTo(tabLeft + tabWidth - tabRadius, 0f)
                quadraticBezierTo(tabLeft + tabWidth, 0f, tabLeft + tabWidth, tabRadius)
                lineTo(tabLeft + tabWidth, top)
                lineTo(right - radius, top)
                quadraticBezierTo(right, top, right, top + radius)
                lineTo(right, bottom - radius)
                quadraticBezierTo(right, bottom, right - radius, bottom)
                lineTo(radius, bottom)
                quadraticBezierTo(0f, bottom, 0f, bottom - radius)
                lineTo(0f, top + radius)
                quadraticBezierTo(0f, top, radius, top)
                close()
            }
            drawPath(path, dayMarker)
            drawPath(path, ink.copy(alpha = 0.45f), style = Stroke(width = strokeWidth))
        }
        Text(
            text = label,
            color = ink,
            fontFamily = HandFont,
            fontSize = 16.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.offset(x = tabTextX, y = 1.dp),
        )
    }
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
    /** Tippen auf die Linie öffnet denselben Abschnitts-Dialog wie auf der Karte. */
    onSegmentClick: (String, String) -> Unit = { _, _ -> },
    /** Das Pin-Symbol öffnet die Pin-Detailansicht – auch bei Einträgen mit Ort. */
    onPinClick: (String) -> Unit = {},
    photoBitmap: (String) -> ImageBitmap?,
    photoTimes: Map<String, Long> = emptyMap(),
    entryBodies: Map<String, String> = emptyMap(),
    mapZoom: Float = 14f,
    externalKey: String? = null,
    /** Neuer Wert erzwingt das Scrollen, auch wenn der Schlüssel gleich bleibt. */
    externalSeq: Long = 0L,
    /** "timeline" bedeutet: Ziel kam aus dem Wischen – nicht zurückscrollen. */
    externalOrigin: String? = null,
    /** Fortlaufende Position für Karte und Schieberegler (0..1). */
    externalFraction: Float? = null,
    externalFractionSeq: Long = 0L,
    /** "timeline" bedeutet: Die Zeitleiste führt gerade selbst. */
    externalFractionOrigin: String? = null,
    /** Foto, das von Karte oder Zeitleiste markiert wurde (sanftes Leuchten). */
    highlightPhotoId: String? = null,
    onScrollFraction: (Float, Boolean) -> Unit = { _, _ -> },
    onDropElement: (String, String?) -> Unit = { _, _ -> },
    onDropEntry: (String, Float, Float) -> Unit = { _, _, _ -> },
    onCenterKeyChange: (String?) -> Unit = {},
    /** Nur vom Nutzer gestartetes Wischen soll die Karte mitziehen. */
    onUserCenterKey: (String?) -> Unit = {},
    /** Beim Wischen: zwischen welchen beiden Elementen liegt die Mitte, und wie weit (0..1)? */
    onCenterBetween: (String, String, Float) -> Unit = { _, _, _ -> },
    /** Die zl ist auf einem Element eingerastet. */
    onSettleKey: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scrollScope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()
    val typography = MaterialTheme.typography
    val zoomFactor = (mapZoom / 14f).coerceIn(0.85f, 1.25f)
    val scrollState = rememberScrollState()
    var timelineBounds by remember { mutableStateOf(Rect.Zero) }
    val availableHeightPx = if (timelineBounds.height > 0f) {
        timelineBounds.height
    } else {
        with(density) { 320.dp.toPx() }
    }
    // Feste Ebenen von oben nach unten: Lasche (ab 4 dp), Tagesrahmen (ab 24 dp),
    // geschwungene Linie mit Pins und km-Steinen (38–62 dp), Eintragsrahmen
    // (ab 70 dp), Karten (ab 78 dp).
    // Alle Karten beginnen auf derselben Höhe – sie folgen nicht mehr der
    // Welle der Linie, sonst ragen sie unten aus ihrem Rahmen.
    val cardTopDp = 78.dp
    val entryTopPx = with(density) { 70.dp.toPx() }
    // Unter der Karte: 4 dp Kartenpolsterung, 8 dp bis zum Eintragsrahmen,
    // 10 dp bis zum Tagesrahmen, 10 dp bis zum unteren Rand.
    val photoHeightPx = (availableHeightPx - with(density) { (78 + 8 + 8 + 10 + 10).dp.toPx() })
        .coerceAtLeast(with(density) { 76.dp.toPx() })
    val photoCaptionHeightPx = with(density) { 20.dp.toPx() }

    /**
     * Gemessene Textbreite mit exakt dem Schriftbild, das auch gezeichnet wird.
     * Schätzwerte wie „6.2 dp pro Zeichen“ waren die Ursache für überlagerte
     * Textfelder und Rahmen.
     */
    fun measuredWidth(
        text: String,
        style: TextStyle,
        maxLines: Int,
        minDp: Float,
        maxDp: Float,
    ): Float {
        if (text.isBlank()) return 0f
        val maxPx = with(density) { maxDp.dp.toPx() }
        val measured = textMeasurer.measure(
            text = text,
            style = style,
            constraints = Constraints(maxWidth = maxPx.roundToInt()),
            maxLines = maxLines,
        ).size.width.toFloat()
        return measured.coerceIn(with(density) { minDp.dp.toPx() }, maxPx)
    }

    /** Ein Bild nimmt fast die ganze Eintragshöhe ein – zugeschnitten auf 4:3. */
    fun photoWidthPx(photoId: String, heightPx: Float = photoHeightPx): Float {
        val bitmap = photoBitmap(photoId)
        val ratio = if (bitmap != null && bitmap.height > 0) {
            bitmap.width.toFloat() / bitmap.height.toFloat()
        } else {
            4f / 3f
        }
        // Fast das echte Seitenverhältnis: Hochformat bis 9:16, Querformat bis 16:9.
        return (heightPx * ratio.coerceIn(0.56f, 1.78f) * zoomFactor)
            .coerceIn(with(density) { 56.dp.toPx() }, with(density) { 250.dp.toPx() })
    }
    // Beim Ziehen wird der Eintrag hervorgehoben, auf den man zusteuert.
    var dragTargetEntryId by remember { mutableStateOf<String?>(null) }
    // Ein gerade gezogenes Eintragsfoto darf nicht zusätzlich die Eintragskarte ziehen.
    var photoDragActive by remember { mutableStateOf(false) }
    // Schon beim Antippen blockieren, damit die Karte keinen Drag startet.
    var photoPressed by remember { mutableStateOf(false) }
    // Nur echte Fingerbewegungen dürfen die Karte mitziehen. Ein Scroll, den
    // die Karte ausgelöst hat, wird kurz als solcher markiert.
    var programmaticScrollUntil by remember { mutableStateOf(0L) }
    var lastUserCenterAt by remember { mutableStateOf(0L) }
    val startPx = with(density) { 34.dp.toPx() }
    val baseY = with(density) { 50.dp.toPx() }
    // Groß genug, dass die Rahmen der Nachbarn sich nicht berühren, selbst
    // wenn beide 10 dp Innenabstand zum Kartenrand haben.
    val itemGapPx = with(density) { 30.dp.toPx() }
    val entryPadPx = with(density) { 10.dp.toPx() }
    // Rotierte Polaroids (8°) und der Schatten ragen seitlich über ihre Karte.
    val photoVisualExtraPx = with(density) { 16.dp.toPx() } + photoHeightPx * 0.15f

    // Die Pins kommen bereits in Reisezeit-Reihenfolge vom Kartenbildschirm.
    val orderedStops = stops
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
        typography, textMeasurer, itemGapPx, photoVisualExtraPx,
    ) {
        val result = mutableListOf<TimelinePoint>()
        val kmOf = { item: TimelineItem -> item.stopId?.let { stopKm[it] } ?: 0.0 }
        val gapPx = itemGapPx
        val padPx = with(density) { 16.dp.toPx() }
        val iconPx = with(density) { 22.dp.toPx() }
        val daySlotPx = with(density) { 22.dp.toPx() }
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
                    // Ein Pin ohne Eintrag ist nur ein unbeschrifteter Pin auf der Linie.
                    TimelineKind.STOP -> 0f
                    TimelineKind.ENTRY -> maxOf(
                        measuredWidth(label, TimelineTitle, 2, 56f, 150f),
                        measuredWidth(body, typography.bodySmall, 3, 56f, 150f),
                    )
                    TimelineKind.PHOTO -> measuredWidth(
                        label,
                        TimelinePhotoCaption,
                        1, 44f, 120f,
                    )
                    else -> 0f
                }
                val stripWidth = if (photoWidths.isEmpty()) {
                    0f
                } else {
                    photoWidths.sum() + gapPx * (photoWidths.size - 1)
                }
                val rawSlot = when (item.kind) {
                    TimelineKind.STOP -> iconPx + padPx
                    TimelineKind.ENTRY -> textWidth +
                        (if (stripWidth > 0f) gapPx + stripWidth else 0f) + padPx
                    TimelineKind.PHOTO -> (photoWidths.firstOrNull()
                        ?: with(density) { 88.dp.toPx() }) +
                        (if (textWidth > 0f) gapPx + textWidth else 0f) + padPx
                    else -> daySlotPx
                }
                // 5 % Reserve für den 1.03-fachen Fokus-Scale; einzelne Fotos
                // brauchen zusätzlich Platz für Drehung und Schatten.
                val slot = (
                    rawSlot * 1.05f +
                        (if (item.kind == TimelineKind.PHOTO) photoVisualExtraPx else 0f)
                    ).coerceAtLeast(with(density) { 44.dp.toPx() })
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
        // Der erste Eintrag eines Tages rückt nah an das senkrechte Datum.
        val dayGapPx = with(density) { 6.dp.toPx() }
        result.forEachIndexed { index, point ->
            result[index] = point.copy(x = cursor + point.slot / 2f)
            cursor += point.slot + if (point.kind == 0) dayGapPx else gapPx
        }
        result
    }

    // Rechts bleibt Platz für das Schild mit der Gesamtstrecke.
    val contentWidthPx = (points.lastOrNull()?.let { it.x + it.slot / 2f } ?: startPx) +
        with(density) { (if (points.isNotEmpty()) 130.dp else 56.dp).toPx() }
    val contentWidth = with(density) { contentWidthPx.toDp() }

    // An-/Abreise-Pins haben keinen eigenen Punkt in der zl. Ihr Abschnitt
    // wird trotzdem gezeichnet: als Linie vom ersten sichtbaren Punkt nach
    // links bzw. vom letzten sichtbaren Punkt nach rechts.
    val hasAnreiseLine = stops.size > 1 && stops.first().isArrivalDeparture
    val hasAbreiseLine = stops.size > 1 && stops.last().isArrivalDeparture
    // Beide laufen bis an den Rand – als käme man von weit her bzw. führe weiter.
    val anreiseEndX = 0f
    val abreiseEndX = contentWidthPx

    // Die Abschnitte der Karte auf die gezeichnete Zeitleiste abbilden. Ein
    // markierter Endpin hat keinen eigenen Punkt mehr; dann grenzt der nächste
    // sichtbare Punkt den Abschnitt ab.
    val segmentHits = remember(stops, points, startPx, contentWidthPx, anreiseEndX, abreiseEndX) {
        fun xFor(stop: Stop): Float? = points.firstOrNull { point ->
            point.item?.stopId == stop.id ||
                (stop.entryId != null && point.entryId == stop.entryId) ||
                (point.item?.entryId != null && point.item?.entryId == stop.entryId)
        }?.x
        val xs = stops.map(::xFor)
        // Gleiche Enden wie die gezeichnete An-/Abreise-Linie.
        val leftFallback = anreiseEndX
        val rightFallback = abreiseEndX
        val minWidth = with(density) { 8.dp.toPx() }
        stops.zipWithNext().mapIndexed { index, (from, to) ->
            val fromX = xs[index] ?: xs.getOrNull(index - 1) ?: leftFallback
            val toX = xs[index + 1] ?: xs.getOrNull(index + 2) ?: rightFallback
            val left = minOf(fromX, toX)
            val right = maxOf(fromX, toX)
            if (right - left >= minWidth) {
                TimelineSegmentHit(from.id, to.id, left, right)
            } else {
                null
            }
        }.filterNotNull()
    }
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
    // Jede Scrollbewegung wird fortlaufend gemeldet. Nur echte Fingerwische
    // (nicht das Nachscrollen durch die Karte) dürfen die Karte mitziehen.
    LaunchedEffect(scrollFraction) {
        val fromUser = scrollState.isScrollInProgress &&
            System.currentTimeMillis() >= programmaticScrollUntil
        onScrollFraction(scrollFraction, fromUser)
        if (!fromUser) return@LaunchedEffect
        // Genaue Lage der Mitte zwischen zwei Elementen – damit die Karte
        // der Route folgt statt von Punkt zu Punkt zu springen.
        val items = points.filter { it.kind != 0 }
        if (items.isEmpty()) return@LaunchedEffect
        val center = scrollState.value + viewportPx / 2f
        val nextIndex = items.indexOfFirst { it.x >= center }
        when (nextIndex) {
            -1 -> onCenterBetween(items.last().key, items.last().key, 0f)
            0 -> onCenterBetween(items.first().key, items.first().key, 0f)
            else -> {
                val previous = items[nextIndex - 1]
                val next = items[nextIndex]
                val span = (next.x - previous.x).coerceAtLeast(1f)
                onCenterBetween(previous.key, next.key, ((center - previous.x) / span).coerceIn(0f, 1f))
            }
        }
    }
    LaunchedEffect(
        externalFraction, externalFractionSeq, externalFractionOrigin,
        contentWidthPx, viewportPx,
    ) {
        val fraction = externalFraction ?: return@LaunchedEffect
        if (externalFractionOrigin == "timeline" || externalFractionOrigin == "focus") {
            return@LaunchedEffect
        }
        val maxScroll = (contentWidthPx - viewportPx).coerceAtLeast(0f)
        val target = (fraction * maxScroll).roundToInt()
        if (externalFractionOrigin == "bar") {
            // Schieberegler: die zl hängt direkt am Finger, ohne Verzögerung.
            programmaticScrollUntil = System.currentTimeMillis() + 120L
            scrollState.scrollTo(target)
        } else if (abs(scrollState.value - target) > 4) {
            programmaticScrollUntil = System.currentTimeMillis() + 320L
            scrollState.animateScrollTo(target)
        }
    }
    LaunchedEffect(externalKey, externalSeq, externalOrigin, viewportPx) {
        if (externalKey == null || externalOrigin == "timeline") return@LaunchedEffect
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
                programmaticScrollUntil = System.currentTimeMillis() + 420L
                scrollState.animateScrollTo(target)
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

    /** Wie milestoneX, aber als Position zwischen den Punkten (für die Höhe auf der Welle). */
    fun milestoneIndex(target: Double): Float {
        if (points.isEmpty() || points.first().km >= target) return 0f
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
                return index - 1 + fraction
            }
        }
        return points.lastIndex.toFloat()
    }

    val kmTargets = remember(totalKm) {
        buildList {
            var value = 100.0
            while (value <= totalKm) {
                add(value)
                value += 100.0
            }
            // Die letzte Hunderter-Marke nicht direkt neben das Ziel-Schild legen.
            if (isNotEmpty() && totalKm - last() < 30.0) removeAt(lastIndex)
        }
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
            // Tagesköpfe haben keine Geo-Position und dürfen die Karte nicht ziehen.
            val centerPoint = points.filter { it.kind != 0 }
                .minByOrNull { abs(it.x - centerPixel) }
            val centerKey = centerPoint?.key
            val centerPhotoId = centerPoint?.photoId
            LaunchedEffect(centerKey) { onCenterKeyChange(centerKey) }
            // Beim Wischen: ein leichtes Ticken bei jedem neuen Element in der Mitte.
            LaunchedEffect(centerKey) {
                if (centerKey != null && scrollState.isScrollInProgress &&
                    System.currentTimeMillis() >= programmaticScrollUntil
                ) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
            // Nach dem Loslassen rastet das nächste Element weich in der Mitte
            // ein – mit kurzem Nachfedern und einem spürbaren Klick.
            val snapping = scrollState.isScrollInProgress
            var hadUserScroll by remember { mutableStateOf(false) }
            LaunchedEffect(snapping) {
                if (snapping) {
                    if (System.currentTimeMillis() >= programmaticScrollUntil) hadUserScroll = true
                    return@LaunchedEffect
                }
                if (!hadUserScroll) return@LaunchedEffect
                hadUserScroll = false
                if (photoDragActive || dragTargetEntryId != null) return@LaunchedEffect
                val nearest = points.filter { it.kind != 0 }
                    .minByOrNull { abs(it.x - (scrollState.value + viewportPx / 2f)) }
                    ?: return@LaunchedEffect
                val maxScroll = scrollState.maxValue
                val target = (nearest.x - viewportPx / 2f).roundToInt().coerceIn(0, maxScroll)
                if (abs(scrollState.value - target) >= 3) {
                    programmaticScrollUntil = System.currentTimeMillis() + 520L
                    scrollState.animateScrollTo(
                        target,
                        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow),
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                // Karte bekommt den eingerasteten Punkt als Nutzerauswahl.
                onUserCenterKey(nearest.key)
                onSettleKey(nearest.key)
            }
            // Während des Wischens folgt die Karte fortlaufend dem mittleren
            // Element – nicht erst beim Loslassen oder bei einer Auswahl.
            LaunchedEffect(centerKey) {
                val now = System.currentTimeMillis()
                if (now >= programmaticScrollUntil && now - lastUserCenterAt >= 90L) {
                    lastUserCenterAt = now
                    onUserCenterKey(centerKey)
                }
            }

            // Tages- und Eintragsrahmen haben rundum dieselbe Polsterung.
            val dayPad = with(density) { 10.dp.toPx() }
            val dayGap = with(density) { 14.dp.toPx() }
            // Kleiner als der Abstand zwischen zwei Punkten (8 dp), sonst
            // berühren sich die Rahmen benachbarter Einträge.
            // Größer als die Innen-Padding der Karten (8 dp), damit kein Text
            // über den Rahmen ragt. Die Trennung der Rahmen erfolgt weiter unten.
            val entryPad = entryPadPx
            // Schrifthöhe des vertikalen Datums. Die Hälfte davon korrigiert
            // nur die Drehung; der sichtbare Abstand bleibt 5 dp.
            val dayFontPx = with(density) { 10.sp.toPx() }
            val halfFontPx = dayFontPx / 2f
            val endGapPx = with(density) { 5.dp.toPx() }
            val dayTopPx = with(density) { 24.dp.toPx() }
            val dayBottomPx = with(density) { (maxHeight - 10.dp).toPx() }
            // Unten ebenfalls 10 dp zwischen Eintrags- und Tagesrahmen.
            val entryBottomPx = dayBottomPx - dayPad

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
            // Die Rahmen werden ausschließlich aus den echten Kartenbreiten
            // gebaut. Sie werden nie verschoben, ohne dass sich die Karten
            // mitbewegen – das war die Ursache für überlagerte Textfelder.
            val entryFrames = remember(
                points, dayFrames, entryPad, entryTopPx, entryBottomPx, endGapPx,
            ) {
                points.filter { it.kind == 2 && it.item?.entryId != null }
                    .mapNotNull { entry ->
                        val id = entry.item?.entryId ?: return@mapNotNull null
                        val children = points.filter { it.entryId == id }
                        if (children.isEmpty()) return@mapNotNull null
                        val dayRect = entry.dayKey?.let { dayFrames[it] }
                        val rawLeft = children.minOf { it.x - it.slot / 2f } - entryPad
                        val rawRight = children.maxOf { it.x + it.slot / 2f } + entryPad
                        val left = if (dayRect != null) maxOf(rawLeft, dayRect.left) else rawLeft
                        // Das letzte Ereignis endet 5 dp vor der Tageskante.
                        val right = if (dayRect != null) {
                            minOf(rawRight, dayRect.right - endGapPx)
                        } else {
                            rawRight
                        }
                        if (right <= left + 4f) return@mapNotNull null
                        id to Rect(left, entryTopPx, right, entryBottomPx)
                    }.toMap()
            }
            // Das letzte sichtbare Ereignis eines Tages wird um den Unterschied
            // zwischen Rahmenpolsterung und gewünschtem Endabstand nach rechts
            // gesetzt. Die Linie selbst bleibt an ihrer Zeitposition.
            val lastContentKeyByDay = remember(points) {
                points.filter { it.kind != 0 }
                    .groupBy { it.dayKey }
                    .mapValues { (_, dayPoints) ->
                        dayPoints.maxByOrNull { it.x + it.slot / 2f }?.key
                    }
            }
            // Karten bleiben genau in ihrem Rahmen (kein Versatz nach rechts).
            val endShiftPx = 0f

            Box(
                modifier = Modifier
                    .width(contentWidth)
                    .fillMaxHeight(),
            ) {
                // Tag-Rahmen und Lasche sind eine gezeichnete Fläche. Die
                // Eintragsrahmen liegen darüber, aber noch hinter der Linie.
                dayFrames.forEach { (key, rect) ->
                    DayFrameWithTab(label = dayLabels[key].orEmpty(), rect = rect)
                }
                entryFrames.forEach { (id, rect) ->
                    val entryPoint = points.firstOrNull { it.entryId == id && it.item != null }
                    TimelineFrame(
                        rect = rect,
                        fill = notebookPaper,
                        highlight = id == dragTargetEntryId,
                        onClick = { entryPoint?.item?.let(onItemClick) },
                    )
                }
                dayFrames.forEach { (key, rect) ->
                    val dayPoint = points.firstOrNull { it.kind == 0 && it.dayKey == key }
                    // Fester Abstand von 5 dp ab dem linken Tagesrand. Die
                    // halbe Schrifthöhe ist nur die Dreh-Korrektur, damit die
                    // sichtbare Kante bei 5 dp liegt.
                    val dateGapPx = with(density) { 5.dp.toPx() }
                    val dateX = rect.left + dateGapPx + halfFontPx
                    if (dayPoint?.detail?.isNotBlank() == true) {
                        Text(
                            text = dayPoint.detail,
                            color = ink.copy(alpha = 0.85f),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .offset(
                                    x = with(density) { dateX.toDp() },
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
                        baseY + (sin(index * WAVE_FREQUENCY) * with(density) { WAVE_DP.dp.toPx() })
                    }
                    // Identische Werte wie auf der Karte (RouteStyling).
                    val path = Path()
                    path.moveTo(points.first().x, yAt(0))
                    for (index in 1 until points.size) {
                        // Weiche S-Kurve: waagerecht aus dem einen Punkt
                        // heraus, waagerecht in den nächsten hinein.
                        val middle = (points[index - 1].x + points[index].x) / 2
                        path.cubicTo(
                            middle, yAt(index - 1),
                            middle, yAt(index),
                            points[index].x, yAt(index),
                        )
                    }
                    // An- und Abreise: eigene, dezente Stücke links und rechts.
                    val outerPaths = buildList {
                        if (hasAnreiseLine) {
                            val middle = (anreiseEndX + points.first().x) / 2
                            add(
                                Path().apply {
                                    moveTo(anreiseEndX, yAt(-1))
                                    cubicTo(middle, yAt(-1), middle, yAt(0), points.first().x, yAt(0))
                                },
                            )
                        }
                        if (hasAbreiseLine) {
                            val lastIndex = points.lastIndex
                            val middle = (points.last().x + abreiseEndX) / 2
                            add(
                                Path().apply {
                                    moveTo(points.last().x, yAt(lastIndex))
                                    cubicTo(
                                        middle, yAt(lastIndex),
                                        middle, yAt(lastIndex + 1),
                                        abreiseEndX, yAt(lastIndex + 1),
                                    )
                                },
                            )
                        }
                    }
                    val subtleDash = PathEffect.dashPathEffect(
                        floatArrayOf(
                            with(density) { (RouteStyling.DASH_DP / 2f).dp.toPx() },
                            with(density) { (RouteStyling.GAP_DP / 2f).dp.toPx() },
                        ),
                        0f,
                    )
                    outerPaths.forEach { outer ->
                        drawPath(
                            outer, Color(RouteStyling.SUBTLE_UNDER_COLOR),
                            style = Stroke(width = RouteStyling.SUBTLE_UNDER_WIDTH),
                        )
                        drawPath(
                            outer, Color(RouteStyling.SUBTLE_LINE_COLOR),
                            style = Stroke(
                                width = RouteStyling.SUBTLE_LINE_WIDTH,
                                pathEffect = subtleDash,
                            ),
                        )
                    }
                    // Weißer Unterzug, darüber die gestrichelte Spline-Linie –
                    // exakt wie auf der Karte. Die Striche sind etwa einen
                    // halben Pin lang.
                    drawPath(
                        path, Color(RouteStyling.UNDER_COLOR),
                        style = Stroke(width = RouteStyling.UNDER_WIDTH),
                    )
                    val dash = PathEffect.dashPathEffect(
                        floatArrayOf(
                            with(density) { RouteStyling.DASH_DP.dp.toPx() },
                            with(density) { RouteStyling.GAP_DP.dp.toPx() },
                        ),
                        0f,
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

                }

                // Kilometersteine sitzen direkt auf der Linie – wie Meilensteine
                // am Weg. Sie liegen unter Pins und Karten.
                if (points.isNotEmpty()) kmTargets.forEach { target ->
                    val position = milestoneIndex(target)
                    val lineY = with(density) {
                        (baseY + sin(position * WAVE_FREQUENCY) * WAVE_DP.dp.toPx()).toDp()
                    }
                    val x = with(density) { milestoneX(target).toDp() }
                    val label = "${target.toInt()} km"
                    Box(
                        modifier = Modifier
                            .offset(x = x - 40.dp, y = lineY - 10.dp)
                            .width(80.dp)
                            .height(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label,
                            color = ink,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier
                                .background(paper, RoundedCornerShape(50))
                                .border(1.5.dp, ink, RoundedCornerShape(50))
                                .padding(horizontal = 7.dp, vertical = 1.dp),
                        )
                    }
                }

                // Holzschild mit der Gesamtstrecke, rechts neben dem letzten Eintrag.
                val lastContentIndex = points.indexOfLast { it.kind != 0 }
                if (lastContentIndex >= 0 && totalKm > 0.0) {
                    val last = points[lastContentIndex]
                    val signX = with(density) { (last.x + last.slot / 2f).toDp() } + 18.dp
                    val lineY = with(density) {
                        (baseY + sin(lastContentIndex * WAVE_FREQUENCY) * WAVE_DP.dp.toPx()).toDp()
                    }
                    val boardTop = cardTopDp + 6.dp
                    // Pfosten: von der Linie bis unter das Schild.
                    Box(
                        modifier = Modifier
                            .offset(x = signX + 14.dp, y = lineY)
                            .width(5.dp)
                            .height(boardTop - lineY + 64.dp)
                            .background(signPost, RoundedCornerShape(2.dp))
                            .border(1.dp, ink, RoundedCornerShape(2.dp)),
                    )
                    Column(
                        modifier = Modifier
                            .offset(x = signX, y = boardTop)
                            .graphicsLayer { rotationZ = -4f }
                            .width(100.dp)
                            .background(signWood, SignShape)
                            .border(1.5.dp, ink, SignShape)
                            .padding(start = 8.dp, end = 18.dp, top = 3.dp, bottom = 4.dp),
                    ) {
                        Text(
                            text = "Gesamt",
                            color = ink.copy(alpha = 0.75f),
                            fontSize = 10.sp,
                            lineHeight = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            text = "${String.format(Locale.GERMANY, "%,.0f", totalKm)} km",
                            style = TimelineTitleSmall,
                            color = ink,
                            maxLines = 1,
                        )
                    }
                }

                // Die Linie selbst ist klickbar, bevor Pins und Karten darüber
                // liegen. Ein Tap nahe der Spline öffnet denselben Dialog wie
                // ein Tap auf die Linie der Karte. Die Klickfläche ist nur ein
                // schmaler Streifen um die Linie: Eine Fläche über die ganze
                // Höhe würde Taps auf die Eintragsrahmen (Papierfläche)
                // verschlucken, weil in Compose nur die oberste Fläche den Tap
                // bekommt.
                val lineBandDp = 18.dp
                Box(
                    modifier = Modifier
                        .offset(y = with(density) { baseY.toDp() } - lineBandDp)
                        .width(contentWidth)
                        .height(lineBandDp * 2)
                        .pointerInput(segmentHits) {
                            val lineBand = with(density) { lineBandDp.toPx() }
                            detectTapGestures { offset ->
                                val hit = segmentHits.firstOrNull { segment ->
                                    offset.x >= segment.left && offset.x <= segment.right
                                } ?: segmentHits.minByOrNull { segment ->
                                    abs(offset.x - segment.center)
                                } ?: return@detectTapGestures
                                val tolerance = maxOf(
                                    with(density) { 24.dp.toPx() },
                                    (hit.right - hit.left) / 2f,
                                )
                                if (abs(offset.x - hit.center) <= tolerance + lineBand) {
                                    onSegmentClick(hit.fromStopId, hit.toStopId)
                                }
                            }
                        },
                )

                points.forEachIndexed { index, point ->
                    val item = point.item
                    // Dieselbe Welle wie die gezeichnete Linie (6 dp, nicht 6 px),
                    // damit Pins und Karten genau auf der Linie sitzen.
                    val lineY = with(density) {
                        (baseY + sin(index * WAVE_FREQUENCY) * WAVE_DP.dp.toPx()).toDp()
                    }
                    val cardY = cardTopDp
                    val highlighted = point.key == centerKey
                    val dragOffset = remember(point.key) { mutableStateOf(Offset.Zero) }
                    val cardWidth = with(density) { point.slot.toDp() }
                    val halfCardPx = point.slot / 2f
                    val cardEndShiftPx = if (
                        point.kind != 0 && lastContentKeyByDay[point.dayKey] == point.key
                    ) endShiftPx else 0f

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
                                    (point.x - halfCardPx + cardEndShiftPx).toDp() +
                                        dragOffset.value.x.toDp()
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
                                            style = TimelinePhotoCaption,
                                            maxLines = 1,
                                        )
                                    }
                                }
                            }

                            // Pin ohne Eintrag: keine Karte, nur der Pin auf der Linie.
                            1 -> {}

                            5 -> Column(
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
                                            style = TimelineTitleSmall,
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
                                // Genau die gemessenen Breiten verwenden, die auch
                                // für Slot und Rahmen berechnet wurden.
                                val orderedWidths = ordered.map { id ->
                                    val index = entryPhotos.indexOf(id)
                                    point.photoWidthsPx.getOrNull(index)
                                        ?: with(density) { 96.dp.toPx() }
                                }
                                Row(
                                    modifier = Modifier
                                        .width(cardWidth)
                                        .background(notebookPaper, RoundedCornerShape(8.dp))
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
                                                    style = TimelineTitle,
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
                                            ordered.forEachIndexed { index, photoId ->
                                                val bitmap = photoBitmap(photoId)
                                                val widthPx = orderedWidths[index]
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

            }
        }
    }
}

private fun scrollFraction(scrollState: ScrollState): Float =
    if (scrollState.maxValue <= 0) 0f else {
        scrollState.value.toFloat() / scrollState.maxValue.toFloat()
    }
