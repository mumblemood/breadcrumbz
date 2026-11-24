package de.breadcrumbz.app.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import android.view.MotionEvent
import de.breadcrumbz.app.domain.model.Photo
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

/**
 * Eigene Kartenebene für große Polaroid-Foto-Pins. Fotos desselben Stopps
 * werden als Stapel mit Count-Badge gezeichnet. Die Bilder werden verkleinert
 * gecacht und direkt auf dem Canvas gezeichnet, damit sie beim Zoomen exakt
 * an ihrem GPS-Punkt kleben.
 */
class PolaroidOverlay(
    private val photos: List<Photo>,
    private val filePath: (String) -> String,
    private val onPhotoClick: (List<String>) -> Unit,
) : Overlay() {

    private data class Group(
        val ids: List<String>,
        val representative: Photo,
        val latitude: Double,
        val longitude: Double,
    )

    private val cache = LruCache<String, Bitmap>(24)
    private val hitAreas = mutableListOf<Pair<RectF, List<String>>>()

    private val scale = 5f
    private val cardWidth = 72f * scale
    private val cardHeight = 86f * scale
    private val padding = 5f * scale
    private val captionHeight = 12f * scale
    private val shadowOffset = 3f * scale

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBF4E4")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3B2B20")
        strokeWidth = 2f * scale
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D99B52")
        strokeWidth = 3f * scale
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D99B52")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 34f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        hitAreas.clear()

        val projection = mapView.projection
        groups().forEach { group ->
            val bitmap = bitmapFor(group.representative.fileName) ?: return@forEach
            val point = projection.toPixels(
                GeoPoint(group.latitude, group.longitude),
                null,
            )
            val tilt = when (abs(group.representative.id.hashCode()) % 3) {
                0 -> -10f
                1 -> 8f
                else -> -4f
            }

            val left = -cardWidth / 2
            val top = -cardHeight + 12f * scale
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)

            // Hintere Karten des Stapels (maximal drei sichtbar).
            val layers = minOf(3, group.ids.size)
            for (layer in layers - 1 downTo 1) {
                canvas.save()
                canvas.translate(point.x.toFloat(), point.y.toFloat())
                canvas.rotate(tilt + layer * 4f)
                val dx = layer * 14f
                val dy = -layer * 10f
                canvas.drawRect(
                    rect.left + dx + shadowOffset,
                    rect.top + dy + shadowOffset,
                    rect.right + dx + shadowOffset,
                    rect.bottom + dy + shadowOffset,
                    shadowPaint,
                )
                canvas.drawRect(
                    rect.left + dx,
                    rect.top + dy,
                    rect.right + dx,
                    rect.bottom + dy,
                    framePaint,
                )
                canvas.drawRect(
                    rect.left + dx,
                    rect.top + dy,
                    rect.right + dx,
                    rect.bottom + dy,
                    borderPaint,
                )
                canvas.restore()
            }

            // Vorderste Karte mit Bild.
            canvas.save()
            canvas.translate(point.x.toFloat(), point.y.toFloat())
            canvas.rotate(tilt)
            canvas.drawRect(
                rect.left + shadowOffset,
                rect.top + shadowOffset,
                rect.right + shadowOffset,
                rect.bottom + shadowOffset,
                shadowPaint,
            )
            canvas.drawRect(rect, framePaint)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(
                    rect.left + padding,
                    rect.top + padding,
                    rect.right - padding,
                    rect.bottom - captionHeight,
                ),
                null,
            )
            canvas.drawRect(rect, borderPaint)
            canvas.restore()

            // Nadel und Stapel-Anzahl.
            val pinX = point.x.toFloat()
            val pinY = point.y.toFloat()
            canvas.drawLine(pinX, pinY, pinX, pinY - 24f, pinPaint)
            canvas.drawCircle(pinX, pinY, 12f, pinPaint)
            if (group.ids.size > 1) {
                val badgeX = pinX + cardWidth / 2 - 12f
                val badgeY = pinY - cardHeight + 12f * scale + 24f
                canvas.drawCircle(badgeX, badgeY, 34f, badgePaint)
                val label = "×${group.ids.size}"
                val textY = badgeY - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
                canvas.drawText(label, badgeX, textY, badgeTextPaint)
            }

            hitAreas.add(
                RectF(
                    pinX - cardWidth / 2 - 20f,
                    pinY - cardHeight - 20f,
                    pinX + cardWidth / 2 + 40f,
                    pinY + 24f,
                ) to group.ids,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val hit = hitAreas.firstOrNull { it.first.contains(event.x, event.y) }
            if (hit != null) {
                onPhotoClick(hit.second)
                return true
            }
        }
        return super.onTouchEvent(event, mapView)
    }

    /** Fotos mit gemeinsamem Stopp werden zu einem Stapel zusammengefasst. */
    private fun groups(): List<Group> = photos
        .groupBy { it.stopId ?: "solo-${it.id}" }
        .values
        .map { items ->
            val sorted = items.sortedBy { it.capturedAt ?: Long.MAX_VALUE }
            Group(
                ids = sorted.map { it.id },
                representative = sorted.first(),
                latitude = sorted.mapNotNull { it.latitude }.average(),
                longitude = sorted.mapNotNull { it.longitude }.average(),
            )
        }
        .sortedBy { it.representative.capturedAt ?: Long.MAX_VALUE }

    private fun bitmapFor(fileName: String): Bitmap? {
        cache.get(fileName)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = 2 }
        val bitmap = BitmapFactory.decodeFile(filePath(fileName), options) ?: return null
        cache.put(fileName, bitmap)
        return bitmap
    }
}
