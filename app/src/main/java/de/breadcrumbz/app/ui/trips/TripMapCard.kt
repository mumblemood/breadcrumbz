package de.breadcrumbz.app.ui.trips

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.state.TripOverview
import de.breadcrumbz.app.ui.theme.BodyFont
import de.breadcrumbz.app.ui.theme.PaperCard
import de.breadcrumbz.app.ui.theme.HandFont
import de.breadcrumbz.app.ui.theme.Ink
import de.breadcrumbz.app.ui.theme.Paper
import de.breadcrumbz.app.ui.theme.PolaroidWhite
import de.breadcrumbz.app.ui.theme.SealRed
import de.breadcrumbz.app.ui.theme.Sepia
import de.breadcrumbz.app.ui.theme.Tape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos

private val DateChip = androidx.compose.ui.graphics.Color(0xFFE8B86A)

private fun shortDate(value: Long): String =
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(value))

/**
 * Reiseliste: jede Reise als kleiner, gefalteter Kartenausschnitt.
 * Die Linie ist eine Skizze aus den Pins (gleiche Reihenfolge wie die
 * Route), darunter das Etikett mit Datum, Titel und Gesamt-km.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TripMapCard(
    overview: TripOverview,
    coverPath: String?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val coverBitmap = remember(coverPath) {
        coverPath?.let { PhotoImporter.decode(it, sampleSize = 4) }?.asImageBitmap()
    }
    val points = overview.routePoints
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(214.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Paper)
            .border(2.dp, Ink, shape)
            .combinedClickable(onClick = onOpen, onLongClick = onEdit),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Faltkanten der Karte.
            val crease = Sepia.copy(alpha = 0.14f)
            val creaseWidth = 1.dp.toPx()
            drawLine(crease, Offset(size.width / 3f, 0f), Offset(size.width / 3f, size.height), creaseWidth)
            drawLine(crease, Offset(size.width * 2f / 3f, 0f), Offset(size.width * 2f / 3f, size.height), creaseWidth)
            drawLine(crease, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), creaseWidth)

            // Ein paar leise Höhenlinien als Schmuck.
            val contour = Sepia.copy(alpha = 0.16f)
            for (i in 0..2) {
                val y = size.height * (0.18f + i * 0.09f)
                val wave = Path().apply {
                    moveTo(0f, y)
                    cubicTo(
                        size.width * 0.3f, y - 18.dp.toPx(),
                        size.width * 0.6f, y + 18.dp.toPx(),
                        size.width, y - 6.dp.toPx(),
                    )
                }
                drawPath(wave, contour, style = Stroke(width = 1.dp.toPx()))
            }

            if (points.size >= 2) {
                // Skizze in den Bereich über dem Etikett einpassen,
                // mit Längengrad-Korrektur, damit nichts verzerrt.
                val left = 24.dp.toPx()
                val right = size.width - 24.dp.toPx()
                val top = 22.dp.toPx()
                val bottom = size.height - 96.dp.toPx()
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
                    projected.drop(1).forEach { lineTo(it.x, it.y) }
                }
                // Wie überall: weißer Unterzug, schwarze gestrichelte Linie.
                drawPath(
                    route,
                    androidx.compose.ui.graphics.Color.White,
                    style = Stroke(
                        width = 6.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                drawPath(
                    route,
                    androidx.compose.ui.graphics.Color(0xFF111111),
                    style = Stroke(
                        width = 2.6.dp.toPx(),
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(9.dp.toPx(), 6.dp.toPx()),
                        ),
                    ),
                )
                listOf(projected.first(), projected.last()).forEach { end ->
                    drawCircle(SealRed, radius = 6.dp.toPx(), center = end)
                    drawCircle(Ink, radius = 6.dp.toPx(), center = end, style = Stroke(1.5.dp.toPx()))
                }
            } else if (points.size == 1) {
                val center = Offset(size.width / 2f, (size.height - 96.dp.toPx()) / 2f + 22.dp.toPx())
                drawCircle(SealRed, radius = 7.dp.toPx(), center = center)
                drawCircle(Ink, radius = 7.dp.toPx(), center = center, style = Stroke(1.5.dp.toPx()))
            }
        }

        if (coverBitmap != null) {
            Box(
                modifier = Modifier
                    .offset(x = 18.dp, y = 16.dp)
                    .rotate(-6f)
                    .shadow(4.dp)
                    .background(PolaroidWhite)
                    .padding(start = 5.dp, end = 5.dp, top = 5.dp, bottom = 14.dp),
            ) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = "Titelbild",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 86.dp, height = 68.dp),
                )
                // Polaroid mit Klebeband.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-9).dp)
                        .size(width = 36.dp, height = 13.dp)
                        .background(Tape),
                )
            }
        }

        // Etikett unten wie im Design-Beispiel.
        val range = listOfNotNull(
            overview.startDate?.let { shortDate(it) },
            overview.endDate?.let { shortDate(it) },
        ).distinct().joinToString(" – ")
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(12.dp)
                .fillMaxWidth()
                .background(PaperCard, RoundedCornerShape(14.dp))
                .border(2.dp, Ink, RoundedCornerShape(14.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (range.isNotBlank()) {
                    Text(
                        text = range,
                        color = Ink,
                        fontFamily = HandFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .background(DateChip, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp),
                    )
                }
                Text(
                    text = overview.trip.title.ifBlank { "Neue Reise" },
                    color = if (overview.trip.title.isBlank()) Sepia else Ink,
                    fontFamily = HandFont,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp,
                    lineHeight = 28.sp,
                    fontStyle = if (overview.trip.titleIsAuto) FontStyle.Italic else FontStyle.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (overview.distanceKm >= 0.1) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Gesamt", color = Sepia, fontFamily = BodyFont, fontSize = 12.sp)
                    Text(
                        text = "${overview.distanceKm.toInt().coerceAtLeast(1)} km",
                        color = Ink,
                        fontFamily = BodyFont,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
}
