package de.breadcrumbz.app.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.MotionEvent
import android.view.ViewConfiguration
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.domain.model.Photo
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Overlay
import kotlin.math.abs

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
    /** Zoomstufe beim Auffächern: zoomt man deutlich heraus, schließt sich der Stapel wieder. */
    private val expandedAtZoom: Double = 0.0,
    /** Pins der Reise. Sie werden hier gezeichnet, gemeinsam mit ihren Fotos. */
    private val pins: () -> List<MapPin> = { emptyList() },
    /** Zu welchem Pin gehört ein Foto (direkt oder über seinen Eintrag)? */
    private val stopIdOf: (Photo) -> String? = { it.stopId },
    /** Schrift der Bildunterschrift (gleiche Schrift wie in der App). */
    private val captionTypeface: Typeface? = null,
) : Overlay() {

    /** Ein Pin auf der Karte: Ort und fertig skaliertes Symbol. */
    class MapPin(val stopId: String, val position: GeoPoint, val icon: Drawable?)

    /**
     * Pin und seine Fotos bilden eine Einheit. Einheiten weiter unten auf dem
     * Bildschirm liegen vorn – so liegt nie ein fremder Pin über einem Foto,
     * das vor ihm liegt.
     */
    private class PinUnit(val stopId: String?, val pin: MapPin?, val groups: MutableList<Group>)

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
        /** Pin, zu dem die meisten Fotos dieses Stapels gehören. */
        val stopId: String? = null,
        /** Mitte des aufgefächerten Stapels und Versatz dieser Karte darin (Pixel). */
        val fanCenterLat: Double = latitude,
        val fanCenterLon: Double = longitude,
        val fanDx: Float = 0f,
        val fanDy: Float = 0f,
    )

    private var dotRadius = 8f
    private val cache = LruCache<String, Bitmap>(24)
    private val hitAreas = mutableListOf<Triple<RectF, List<String>, String>>()
    /** Für jeden gezeichneten Pin: wie viele Foto-Flächen lagen zu dem Zeitpunkt schon darunter. */
    private val pinOrder = mutableMapOf<String, Int>()

    /** Damit langes Halten auf einem Polaroid keinen neuen Pin erzeugt. */
    fun isPhotoAt(x: Float, y: Float): Boolean =
        hitAreas.any { it.first.contains(x, y) }

    /** Liegt an dieser Stelle ein Foto über dem Pin? Dann gehört der Tap dem Foto. */
    fun isPinCoveredAt(stopId: String, x: Float, y: Float): Boolean {
        val drawnBefore = pinOrder[stopId] ?: return false
        return hitAreas.drop(drawnBefore).any { it.first.contains(x, y) }
    }

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
        typeface = captionTypeface ?: Typeface.DEFAULT
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        hitAreas.clear()

        // Zoom 14 entspricht der Normalgröße. Die Bilder wachsen nur etwa
        // 30 % mit, nicht 1:1 mit der Karte – dadurch bleiben sie gut lesbar
        // und verdecken auch bei starkem Hineinzoomen nicht die Umgebung.
        val zoomFactor = (1.0 + (mapView.zoomLevelDouble - 14.0) * 0.075)
            .coerceIn(0.8, 1.3).toFloat()
        // Kleiner als früher: die Karte bleibt aufgeräumt.
        val scale = 4f * zoomFactor
        val imageWidth = 88f * scale
        val padding = 5f * scale
        val captionHeight = 12f * scale
        val shadowOffset = 3f * scale
        val needleLength = 18f * zoomFactor
        borderPaint.strokeWidth = 2f * scale
        // Feine Nadel statt dicker Striche quer über die Karte.
        pinPaint.strokeWidth = 1.6f * mapView.resources.displayMetrics.density
        dotRadius = 3.5f * mapView.resources.displayMetrics.density
        badgeTextPaint.textSize = 34f * zoomFactor
        captionPaint.textSize = 36f * zoomFactor

        val projection = mapView.projection
        pinOrder.clear()

        // Fotos und Pins zu Einheiten zusammenfassen (Pin + seine Fotos).
        val pinList = pins()
        val units = linkedMapOf<String, PinUnit>()
        pinList.forEach { pin -> units[pin.stopId] = PinUnit(pin.stopId, pin, mutableListOf()) }
        groups(mapView).forEach { group ->
            val stopId = group.stopId
            val unit = stopId?.let { units[it] }
            if (unit != null) {
                unit.groups.add(group)
            } else {
                units["photo-" + group.representative.id] = PinUnit(null, null, mutableListOf(group))
            }
        }
        fun anchorY(unit: PinUnit): Float {
            unit.pin?.let { return projection.toPixels(it.position, null).y.toFloat() }
            return unit.groups.maxOf {
                projection.toPixels(GeoPoint(it.latitude, it.longitude), null).y.toFloat()
            }
        }
        units.values.sortedBy { anchorY(it) }.forEach { unit ->
            unit.groups.forEach { group ->
                drawGroup(
                    canvas, projection, group, zoomFactor, scale, imageWidth, padding,
                    captionHeight, shadowOffset, needleLength,
                )
            }
            unit.pin?.let { pin ->
                val icon = pin.icon ?: return@let
                val point = projection.toPixels(pin.position, null)
                val width = icon.bounds.width().takeIf { it > 0 } ?: icon.intrinsicWidth
                val height = icon.bounds.height().takeIf { it > 0 } ?: icon.intrinsicHeight
                // Spitze unten mittig auf dem Ort – wie vorher beim Marker.
                icon.setBounds(
                    point.x - width / 2, point.y - height,
                    point.x - width / 2 + width, point.y,
                )
                icon.draw(canvas)
                icon.setBounds(0, 0, width, height)
                pinOrder[pin.stopId] = hitAreas.size
            }
        }
    }

    private fun drawGroup(
        canvas: Canvas,
        projection: org.osmdroid.views.Projection,
        group: Group,
        zoomFactor: Float,
        scale: Float,
        imageWidth: Float,
        padding: Float,
        captionHeight: Float,
        shadowOffset: Float,
        needleLength: Float,
    ) {
        run {
            val bitmap = bitmapFor(group.representative.fileName) ?: return
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
            // Nur leicht schräg; aufgefächerte Bilder fast gerade.
            val tiltRange = if (group.fanIndex > 0) 5 else 11
            val tilt = ((group.representative.id.hashCode() ushr 4) % tiltRange).toFloat() -
                (tiltRange / 2).toFloat()

            // Oberer weißer Rand: die Spitze der Nadel landet in seiner Mitte.
            val top = needleLength - padding / 2f
            val rect = RectF(-cardWidth / 2f, top, cardWidth / 2f, top + cardHeight)
            val originX = point.x.toFloat()
            val originY = point.y.toFloat()
            // Aufgelöste Stapel liegen ordentlich im Raster um die Mitte des
            // Stapels. Die feine Nadel zeigt weiter auf den eigenen Ort.
            val pinX: Float
            val pinY: Float
            if (group.fanIndex > 0) {
                val center = projection.toPixels(
                    GeoPoint(group.fanCenterLat, group.fanCenterLon),
                    null,
                )
                pinX = center.x + group.fanDx
                pinY = center.y + group.fanDy
            } else {
                pinX = originX
                pinY = originY
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
            canvas.drawCircle(originX, originY, dotRadius, pinPaint)
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
                    val hit = hitAreas.lastOrNull { it.first.contains(event.x, event.y) }
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
                val hit = hitAreas.lastOrNull { it.first.contains(event.x, event.y) }
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
     * Stapel entstehen nach dem Abstand auf dem Bildschirm: Liegen zwei
     * Polaroids so nah, dass sie sich deutlich überdecken würden, wandern sie
     * auf einen Stapel. Beim Rauszoomen rücken die Orte zusammen und es
     * entstehen Stapel, beim Reinzoomen fallen sie von selbst wieder
     * auseinander. Ein angetippter Stapel bleibt aufgefächert, bis man
     * deutlich herauszoomt.
     */
    private fun groups(mapView: MapView): List<Group> {
        val zoom = mapView.zoomLevelDouble
        val projection = mapView.projection
        val zoomFactor = (1.0 + (zoom - 14.0) * 0.075).coerceIn(0.8, 1.3).toFloat()
        // Ungefähre Kartengröße (wie in draw): Polaroids, die sich mehr als ein
        // wenig überdecken würden, wandern auf einen Stapel.
        val scale = 4f * zoomFactor
        val cardWidth = 88f * scale + 10f * scale
        val cardHeight = 5f * scale + 88f * scale * 0.75f + 12f * scale
        val needle = 18f * zoomFactor
        val expanded = if (expandedPhotoIds.isNotEmpty() && zoom >= expandedAtZoom - 0.75) {
            expandedPhotoIds
        } else {
            emptySet()
        }
        val placed = photos.filter { it.latitude != null && it.longitude != null }
            .sortedBy { it.capturedAt ?: Long.MAX_VALUE }
        val screen = placed.associate { photo ->
            val point = projection.toPixels(GeoPoint(photo.latitude!!, photo.longitude!!), null)
            photo.id to (point.x.toFloat() to point.y.toFloat())
        }
        val buckets = mutableListOf<MutableList<Photo>>()
        placed.forEach { photo ->
            val (x, y) = screen.getValue(photo.id)
            val neighbour = buckets.firstOrNull { bucket ->
                bucket.any { other ->
                    val (ox, oy) = screen.getValue(other.id)
                    abs(x - ox) < cardWidth * 0.8f && abs(y - oy) < cardHeight * 0.8f
                }
            }
            if (neighbour != null) neighbour.add(photo) else buckets.add(mutableListOf(photo))
        }

        fun stopOf(items: List<Photo>) = items.mapNotNull(stopIdOf)
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }?.key

        return buckets.flatMap { items ->
            val sorted = items.sortedBy { it.capturedAt ?: Long.MAX_VALUE }
            val centerLat = sorted.mapNotNull { it.latitude }.average()
            val centerLon = sorted.mapNotNull { it.longitude }.average()
            if (items.size > 1 && items.any { it.id in expanded }) {
                // Aufgefächert: ordentliches Raster um die Mitte des Stapels.
                val columns = when {
                    sorted.size <= 2 -> sorted.size
                    sorted.size <= 4 -> 2
                    else -> 3
                }
                val rows = (sorted.size + columns - 1) / columns
                val stepX = cardWidth * 1.06f
                val stepY = (cardHeight + needle) * 1.04f
                sorted.mapIndexed { index, photo ->
                    val column = index % columns
                    val row = index / columns
                    val inRow = minOf(columns, sorted.size - row * columns)
                    Group(
                        ids = listOf(photo.id),
                        representative = photo,
                        latitude = photo.latitude!!,
                        longitude = photo.longitude!!,
                        fanIndex = index + 1,
                        stopId = stopIdOf(photo),
                        fanCenterLat = centerLat,
                        fanCenterLon = centerLon,
                        fanDx = (column - (inRow - 1) / 2f) * stepX,
                        fanDy = (row - (rows - 1) / 2f) * stepY - (cardHeight + needle) / 2f,
                    )
                }
            } else {
                // Das gerade in der Mitte der Timeline liegende Bild kommt nach vorn.
                val front = frontPhotoId?.let { id -> sorted.firstOrNull { it.id == id } }
                val ordered = if (front != null) {
                    sorted.filter { it.id != front.id } + front
                } else {
                    sorted
                }
                listOf(
                    Group(
                        ids = ordered.map { it.id },
                        representative = ordered.last(),
                        latitude = centerLat,
                        longitude = centerLon,
                        stopId = stopOf(items),
                    ),
                )
            }
        }.sortedBy { it.representative.capturedAt ?: Long.MAX_VALUE }
    }

    private fun bitmapFor(fileName: String): Bitmap? {
        cache.get(fileName)?.let { return it }
        val bitmap = PhotoImporter.decode(filePath(fileName), maxSide = 1024) ?: return null
        cache.put(fileName, bitmap)
        return bitmap
    }
}
