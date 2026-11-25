package de.breadcrumbz.app.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.state.TripOverview
import de.breadcrumbz.app.ui.theme.BodyFont
import de.breadcrumbz.app.ui.theme.HandFont
import de.breadcrumbz.app.ui.theme.Ink
import de.breadcrumbz.app.ui.theme.Paper
import de.breadcrumbz.app.ui.theme.PaperCard
import de.breadcrumbz.app.ui.theme.ParchmentEdge
import de.breadcrumbz.app.ui.theme.PolaroidWhite
import de.breadcrumbz.app.ui.theme.RiverBlue
import de.breadcrumbz.app.ui.theme.SealRed
import de.breadcrumbz.app.ui.theme.Sepia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

private fun shortDate(value: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(value))

/**
 * Reiseliste: jede Reise als Postkarte aus der alten Karte.
 * Links das Titelbild als Polaroid, rechts die Skizze der Route mit allen
 * Pins, unten das Etikett mit Titel, Zeitraum, Ort und Gesamt-km.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripMapCard(
    overview: TripOverview,
    coverPath: String?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val coverBitmap = remember(coverPath) {
        coverPath?.let { PhotoImporter.decode(it, sampleSize = 4) }?.asImageBitmap()
    }
    val points = overview.routePoints
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, shape)
            .clip(shape)
            .background(PaperCard)
            .border(2.dp, Ink, shape)
            .combinedClickable(onClick = onOpen, onLongClick = onEdit),
    ) {
        // Oberer Teil: Kartenausschnitt.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .background(Paper),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawMapBackground()
                val left = if (coverBitmap != null) size.width * 0.44f else 28.dp.toPx()
                drawRouteSketch(
                    points = points,
                    left = left,
                    right = size.width - 28.dp.toPx(),
                    top = 22.dp.toPx(),
                    bottom = size.height - 22.dp.toPx(),
                )
            }

            // Kleine Kompassrose oben rechts.
            Icon(
                painter = painterResource(R.drawable.ic_compass),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp)
                    .alpha(0.75f),
            )

            if (coverBitmap != null) {
                // Titelbild als Polaroid – ohne Klebeband.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 16.dp)
                        .rotate(-4f)
                        .shadow(5.dp)
                        .background(PolaroidWhite)
                        .border(1.dp, ParchmentEdge)
                        .padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 16.dp),
                ) {
                    Image(
                        bitmap = coverBitmap,
                        contentDescription = "Titelbild",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(width = 118.dp, height = 92.dp),
                    )
                }
            }
        }

        // Trennlinie wie eine Faltkante.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(Ink),
        )

        // Etikett.
        val range = listOfNotNull(
            overview.startDate?.let { shortDate(it) },
            overview.endDate?.let { shortDate(it) },
        ).distinct().joinToString(" – ")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overview.trip.title.ifBlank { "Neue Reise" },
                    color = if (overview.trip.title.isBlank()) Sepia else Ink,
                    fontFamily = HandFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    lineHeight = 30.sp,
                    fontStyle = if (overview.trip.titleIsAuto) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOfNotNull(
                    range.ifBlank { null },
                    overview.place?.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        color = Sepia,
                        fontFamily = BodyFont,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (overview.distanceKm >= 0.1) {
                Spacer(Modifier.width(10.dp))
                // Kilometer als runder Stempel.
                Column(
                    modifier = Modifier
                        .size(58.dp)
                        .rotate(-8f)
                        .border(2.dp, SealRed, RoundedCornerShape(50))
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "${overview.distanceKm.toInt().coerceAtLeast(1)}",
                        color = SealRed,
                        fontFamily = BodyFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                    )
                    Text(
                        text = "km",
                        color = SealRed,
                        fontFamily = BodyFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        lineHeight = 12.sp,
                    )
                }
            }
        }
    }
}

/** Leise Höhenlinien, ein Fluss und feine Gitterlinien wie auf einer alten Karte. */
private fun DrawScope.drawMapBackground() {
    val grid = Sepia.copy(alpha = 0.10f)
    val step = 28.dp.toPx()
    var x = step
    while (x < size.width) {
        drawLine(grid, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        x += step
    }
    var y = step
    while (y < size.height) {
        drawLine(grid, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += step
    }

    val contour = Sepia.copy(alpha = 0.18f)
    for (i in 0..3) {
        val base = size.height * (0.22f + i * 0.17f)
        val wave = Path().apply {
            moveTo(0f, base)
            cubicTo(
                size.width * 0.3f, base - 16.dp.toPx(),
                size.width * 0.62f, base + 16.dp.toPx(),
                size.width, base - 6.dp.toPx(),
            )
        }
        drawPath(wave, contour, style = Stroke(width = 1.dp.toPx()))
    }

    val river = Path().apply {
        moveTo(size.width * 0.55f, size.height)
        cubicTo(
            size.width * 0.62f, size.height * 0.7f,
            size.width * 0.78f, size.height * 0.75f,
            size.width * 0.86f, size.height * 0.4f,
        )
        quadraticBezierTo(size.width * 0.9f, size.height * 0.2f, size.width, size.height * 0.12f)
    }
    drawPath(
        river,
        RiverBlue.copy(alpha = 0.35f),
        style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
}

/** Skizze der Route aus den Pins – mit Längengrad-Korrektur, damit nichts verzerrt. */
private fun DrawScope.drawRouteSketch(
    points: List<Pair<Double, Double>>,
    left: Float,
    right: Float,
    top: Float,
    bottom: Float,
) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        val center = Offset((left + right) / 2f, (top + bottom) / 2f)
        drawCircle(SealRed, radius = 7.dp.toPx(), center = center)
        drawCircle(Ink, radius = 7.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
        return
    }
    val midLat = points.map { it.first }.average()
    val lonScale = cos(Math.toRadians(midLat)).coerceAtLeast(0.2)
    val xs = points.map { it.second * lonScale }
    val ys = points.map { it.first }
    val spanX = (xs.max() - xs.min()).coerceAtLeast(1e-6)
    val spanY = (ys.max() - ys.min()).coerceAtLeast(1e-6)
    val scale = minOf((right - left) / spanX, (bottom - top) / spanY)
    val offsetX = left + ((right - left) - spanX * scale) / 2.0
    val offsetY = top + ((bottom - top) - spanY * scale) / 2.0
    val projected = points.indices.map { index ->
        Offset(
            (offsetX + (xs[index] - xs.min()) * scale).toFloat(),
            (offsetY + (ys.max() - ys[index]) * scale).toFloat(),
        )
    }
    val route = Path().apply {
        moveTo(projected.first().x, projected.first().y)
        for (index in 1 until projected.size) {
            val previous = projected[index - 1]
            val current = projected[index]
            // Leicht geschwungen wie von Hand gezogen.
            val mid = Offset((previous.x + current.x) / 2f, (previous.y + current.y) / 2f)
            val bend = if (index % 2 == 0) 6.dp.toPx() else -6.dp.toPx()
            quadraticBezierTo(mid.x, mid.y + bend, current.x, current.y)
        }
    }
    // Wie überall: weißer Unterzug, schwarze gestrichelte Linie.
    drawPath(
        route,
        Color.White,
        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    drawPath(
        route,
        Color(0xFF111111),
        style = Stroke(
            width = 2.6.dp.toPx(),
            join = StrokeJoin.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 6.dp.toPx())),
        ),
    )
    // Zwischenhalte als kleine Tintenpunkte, Start und Ziel als rote Siegel.
    projected.drop(1).dropLast(1).forEach { stop ->
        drawCircle(PaperCard, radius = 3.5.dp.toPx(), center = stop)
        drawCircle(Ink, radius = 3.5.dp.toPx(), center = stop, style = Stroke(1.5.dp.toPx()))
    }
    listOf(projected.first(), projected.last()).forEach { end ->
        drawCircle(SealRed, radius = 6.dp.toPx(), center = end)
        drawCircle(Ink, radius = 6.dp.toPx(), center = end, style = Stroke(1.5.dp.toPx()))
    }
}
