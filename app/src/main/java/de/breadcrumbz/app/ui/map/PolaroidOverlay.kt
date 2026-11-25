package de.breadcrumbz.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.MotionEvent
import android.view.ViewConfiguration
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.util.haversineMeters
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs
import kotlin.math.pow

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
    /** Fotos eines aufgeklappten Eintrags liegen einzeln an ihrem Originalort. */
    private val expandedPhotoIds: Set<String> = emptySet(),
    /** Langes Halten auf einem Polaroid löscht dieses Foto (mit Rückfrage). */
    private val onPhotoLongClick: (String) -> Unit = {},
    /** Markiertes Foto: leichtes Leuchten hinter dem Polaroid. */
    private val highlightPhotoId: String? = null,
) : Overlay() {

    private var ignoreTapsUntilRelease = false
    private val handler = Handler(Looper.getMainLooper())
    private var longPress: Runnable? = null
    private var downX = 0f
    private var downY = 0f
    /** Beim Karten-Verschieben darf ein Loslassen über einem Bild nichts öffnen. */
    private var tapMoved = false

    private data class Group(
        val ids: List<String>,
        val representative: Photo,
        val latitude: Double,
        val longitude: Double,
        /** Platzhalter, wenn ein Stapel vorübergehend einzeln gezeigt wird. */
        val fanIndex: Int = 0,
    )

    private val cache = LruCache<String, Bitmap>(24)
    private val hitAreas = mutableListOf<Triple<RectF, List<String>, String>>()

    /** Damit langes Halten auf einem Polaroid keinen neuen Pin erzeugt. */
    fun isPhotoAt(x: Float, y: Float): Boolean =
        hitAreas.any { it.first.contains(x, y) }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFE08A")
    }
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

        // Zoom 14 entspricht der Normalgröße. Die Bilder wachsen nur etwa
        // 30 % mit, nicht 1:1 mit der Karte – dadurch bleiben sie gut lesbar
        // und verdecken auch bei starkem Hineinzoomen nicht die Umgebung.
        val zoomFactor = (1.0 + (mapView.zoomLevelDouble - 14.0) * 0.075)
            .coerceIn(0.8, 1.3).toFloat()
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
        groups(mapView.zoomLevelDouble).forEach { group ->
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
            val originX = point.x.toFloat()
            val originY = point.y.toFloat()
            // Aufgelöste Stapel liegen leicht versetzt nebeneinander, damit man
            // jedes einzelne Polaroid treffen kann. Die Nadel zeigt trotzdem
            // weiter auf den ursprünglichen Ort.
            val fanStepX = cardWidth * 0.58f
            val fanStepY = cardHeight * 0.92f
            val fanSlot = group.fanIndex - 1
            val pinX = if (fanSlot >= 0) {
                originX + (((fanSlot % 4) - 1.5f) * fanStepX)
            } else {
                originX
            }
            val pinY = if (fanSlot >= 0) {
                originY + ((fanSlot / 4) * fanStepY)
            } else {
                originY
            }

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

            // Markiertes Foto leuchtet sanft, ohne das Polaroid zu verdecken.
            if (group.ids.contains(highlightPhotoId)) {
                val glow = 12f * zoomFactor
                canvas.drawRect(
                    rect.left - glow, rect.top - glow,
                    rect.right + glow, rect.bottom + glow,
                    glowPaint,
                )
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

            // Nadel vom Originalort bis zur Mitte des oberen weißen Rahmens.
            canvas.drawLine(originX, originY, pinX, pinY + top, pinPaint)
            canvas.drawCircle(originX, originY, 9f * zoomFactor, pinPaint)
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
                Triple(
                    RectF(
                        pinX - cardWidth / 2f - hitPad,
                        pinY - hitPad,
                        pinX + cardWidth / 2f + hitPad,
                        pinY + top + cardHeight + hitPad,
                    ),
                    group.ids,
                    group.representative.id,
                ),
            )
        }
    }

    private fun cancelLongPress() {
        longPress?.let { handler.removeCallbacks(it) }
        longPress = null
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.pointerCount == 1) {
                    downX = event.x
                    downY = event.y
                    tapMoved = false
                    val hit = hitAreas.firstOrNull { it.first.contains(event.x, event.y) }
                    if (hit != null) {
                        val photoId = hit.third
                        longPress = Runnable {
                            longPress = null
                            onPhotoLongClick(photoId)
                        }.also {
                            // Übliches Android-Haltechangesignal, nicht zwei Sekunden.
                            handler.postDelayed(
                                it,
                                ViewConfiguration.getLongPressTimeout().toLong(),
                            )
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(event.x - downX) > 12f || abs(event.y - downY) > 12f) {
                    tapMoved = true
                    cancelLongPress()
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                ignoreTapsUntilRelease = true
                cancelLongPress()
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                val blocked = ignoreTapsUntilRelease || tapMoved ||
                    abs(event.x - downX) > 12f || abs(event.y - downY) > 12f
                ignoreTapsUntilRelease = false
                tapMoved = false
                if (blocked) return false
                val hit = hitAreas.firstOrNull { it.first.contains(event.x, event.y) }
                if (hit != null) {
                    onPhotoClick(hit.second)
                    return true
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                ignoreTapsUntilRelease = false
                tapMoved = false
                cancelLongPress()
            }
        }
        return false
    }

    /**
     * Stapel entstehen nach der Entfernung der Originalorte, nicht nach dem Pin.
     * Beim Rauszoomen wächst der Abstand, bis zu dem Fotos zusammenwandern.
     */
    private fun groups(zoom: Double): List<Group> {
        val thresholdMeters = 25.0 * 2.0.pow((14.0 - zoom).coerceIn(0.0, 7.0))
        val placed = photos.filter { it.latitude != null && it.longitude != null }
            .sortedBy { it.capturedAt ?: Long.MAX_VALUE }
        val buckets = mutableListOf<MutableList<Photo>>()

        placed.forEach { photo ->
            // Aufgelöster Stapel: jedes Bild bekommt einen eigenen Platz.
            if (photo.id in expandedPhotoIds) {
                buckets.add(mutableListOf(photo))
                return@forEach
            }
            val neighbour = buckets.firstOrNull { bucket ->
                bucket.none { it.id in expandedPhotoIds } &&
                    bucket.any { other ->
                        haversineMeters(
                            photo.latitude!!, photo.longitude!!,
                            other.latitude!!, other.longitude!!,
                        ) <= thresholdMeters
                    }
            }
            if (neighbour != null) neighbour.add(photo) else buckets.add(mutableListOf(photo))
        }

        return buckets.map { items ->
            val sorted = items.sortedBy { it.capturedAt ?: Long.MAX_VALUE }
            // Das gerade in der Mitte der Timeline liegende Bild kommt nach vorn.
            val front = frontPhotoId?.let { id -> sorted.firstOrNull { it.id == id } }
            val ordered = if (front != null) {
                sorted.filter { it.id != front.id } + front
            } else {
                sorted
            }
            val fanIndex = if (items.size == 1 && items.first().id in expandedPhotoIds) {
                expandedPhotoIds.toList().indexOf(items.first().id) + 1
            } else {
                0
            }
            Group(
                ids = ordered.map { it.id },
                representative = ordered.last(),
                latitude = sorted.mapNotNull { it.latitude }.average(),
                longitude = sorted.mapNotNull { it.longitude }.average(),
                fanIndex = fanIndex,
            )
        }.sortedBy { it.representative.capturedAt ?: Long.MAX_VALUE }
    }

    private fun bitmapFor(fileName: String): Bitmap? {
        cache.get(fileName)?.let { return it }
        val bitmap = PhotoImporter.decode(filePath(fileName), maxSide = 1024) ?: return null
        cache.put(fileName, bitmap)
        return bitmap
    }
}
