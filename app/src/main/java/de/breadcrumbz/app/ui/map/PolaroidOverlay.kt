package de.breadcrumbz.app.ui.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.LruCache
import android.view.MotionEvent
import de.breadcrumbz.app.domain.model.Photo
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay

/**
 * Foto-Polaroids auf der Karte. Das Bild wird nie verzogen: Der Rahmen passt
 * sich dem Seitenverhältnis an. Alle Ränder sind gleich breit, nur der untere
 * Rand ist für die Beschriftung dicker. Die Nadelspitze zeigt auf die Mitte
 * des oberen weißen Rahmens. Die Größe wächst und schrumpft mit dem Zoom,
 * damit man auch beim Rauszoomen alle Einträge überblicken kann.
 */
class PolaroidOverlay(
    private val photos: List<Photo>,
    private val filePath: (String) -> String,
    private val onPhotoClick: (List<String>) -> Unit,
    private val frontPhotoId: String? = null,
) : Overlay() {

    private var ignoreTapsUntilRelease = false

    private data class Group(
        val ids: List<String>,
        val representative: Photo,
        val latitude: Double,
        val longitude: Double,
    )

    private val cache = LruCache<String, Bitmap>(24)
    private val hitAreas = mutableListOf<Pair<RectF, List<String>>>()

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBF4E4")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3B2B20")
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D99B52")
    }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3B2B20")
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("cursive", Typeface.NORMAL)
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        hitAreas.clear()

        // Zoom 14 entspricht der bisherigen Größe. Beim Rauszoomen werden die
        // Polaroids kleiner, beim Hineinzoomen etwas größer – oben und unten
        // begrenzt, damit sie nie unlesbar oder überdimensional werden.
        val zoomFactor = (mapView.zoomLevelDouble / 14.0).coerceIn(0.4, 1.4).toFloat()
        val scale = 5f * zoomFactor
        val imageWidth = 88f * scale
        val padding = 5f * scale
        val captionHeight = 12f * scale
        val shadowOffset = 3f * scale
        val needleLength = 18f * zoomFactor
        borderPaint.strokeWidth = 2f * scale
        pinPaint.strokeWidth = 3f * scale
        badgeTextPaint.textSize = 34f * zoomFactor
        captionPaint.textSize = 28f * zoomFactor

        val projection = mapView.projection
        groups().forEach { group ->
            val bitmap = bitmapFor(group.representative.fileName) ?: return@forEach
            val ratio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1)
            val naturalHeight = imageWidth / ratio.coerceAtLeast(0.01f)
            // Rahmen passt sich an: Breite wird proportional mitgezogen, nie verzerrt.
            val imageHeight = naturalHeight.coerceIn(60f * scale, 120f * scale)
            val fittedWidth = imageWidth * (imageHeight / naturalHeight)
            val cardWidth = fittedWidth + 2f * padding
            val cardHeight = padding + imageHeight + captionHeight

            val point = projection.toPixels(
                GeoPoint(group.latitude, group.longitude),
                null,
            )
            val tilt = ((group.representative.id.hashCode() ushr 4) % 29).toFloat() - 14f

            // Oberer weißer Rand: die Spitze der Nadel landet in seiner Mitte.
            val top = needleLength - padding / 2f
            val rect = RectF(-cardWidth / 2f, top, cardWidth / 2f, top + cardHeight)
            val pinX = point.x.toFloat()
            val pinY = point.y.toFloat()

            // Hintere Karten des Stapels.
            val layers = minOf(3, group.ids.size)
            for (layer in layers - 1 downTo 1) {
                canvas.save()
                canvas.translate(pinX, pinY)
                canvas.rotate(tilt + layer * 5f)
                val dx = layer * 14f * zoomFactor
                val dy = -layer * 12f * zoomFactor
                canvas.drawRect(
                    rect.left + dx + shadowOffset,
                    rect.top + dy + shadowOffset,
                    rect.right + dx + shadowOffset,
                    rect.bottom + dy + shadowOffset,
                    shadowPaint,
                )
                canvas.drawRect(
                    rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy, framePaint,
                )
                canvas.drawRect(
                    rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy, borderPaint,
                )
                canvas.restore()
            }

            // Vorderste Karte mit unverzerrtem Bild und Titel.
            canvas.save()
            canvas.translate(pinX, pinY)
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
            val title = group.representative.caption.take(12)
            if (title.isNotBlank()) {
                canvas.drawText(title, 0f, rect.bottom - 14f * zoomFactor, captionPaint)
            }
            canvas.restore()

            // Nadel von der Koordinate bis zur Mitte des oberen weißen Rahmens.
            canvas.drawLine(pinX, pinY, pinX, pinY + needleLength, pinPaint)
            canvas.drawCircle(pinX, pinY, 9f * zoomFactor, pinPaint)
            if (group.ids.size > 1) {
                val badgeRadius = 34f * zoomFactor
                val badgeX = pinX + cardWidth / 2f - badgeRadius * 0.35f
                val badgeY = pinY + needleLength + 28f * zoomFactor
                canvas.drawCircle(badgeX, badgeY, badgeRadius, badgePaint)
                val label = "×${group.ids.size}"
                val textY = badgeY - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2
                canvas.drawText(label, badgeX, textY, badgeTextPaint)
            }

            val hitPad = 24f * zoomFactor
            hitAreas.add(
                RectF(
                    pinX - cardWidth / 2f - hitPad,
                    pinY - hitPad,
                    pinX + cardWidth / 2f + hitPad,
                    pinY + top + cardHeight + hitPad,
                ) to group.ids,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> ignoreTapsUntilRelease = true
            MotionEvent.ACTION_UP -> {
                val blocked = ignoreTapsUntilRelease
                ignoreTapsUntilRelease = false
                if (blocked) return false
                val hit = hitAreas.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit != null) {
                    onPhotoClick(hit.second)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> ignoreTapsUntilRelease = false
        }
        return false
    }

    private fun groups(): List<Group> = photos
        .groupBy { it.stopId ?: "solo-${it.id}" }
        .values
        .map { items ->
            val sorted = items.sortedBy { it.capturedAt ?: Long.MAX_VALUE }
            // Das gerade in der Mitte der Timeline liegende Bild kommt nach vorn.
            val front = frontPhotoId?.let { id -> sorted.firstOrNull { it.id == id } }
            val ordered = if (front != null) {
                sorted.filter { it.id != front.id } + front
            } else {
                sorted
            }
            Group(
                ids = ordered.map { it.id },
                representative = ordered.last(),
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
