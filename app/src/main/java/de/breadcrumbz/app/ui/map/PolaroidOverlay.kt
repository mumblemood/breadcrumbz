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
 * Eigene Kartenebene für Polaroid-Foto-Pins. Die Bilder werden verkleinert
 * gecacht und direkt auf dem Canvas gezeichnet, damit sie beim Zoomen exakt
 * an ihrem GPS-Punkt kleben.
 */
class PolaroidOverlay(
    private val photos: List<Photo>,
    private val filePath: (String) -> String,
    private val onPhotoClick: (String) -> Unit,
) : Overlay() {

    private val cache = LruCache<String, Bitmap>(24)
    private val hitAreas = mutableListOf<Pair<RectF, String>>()

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FBF4E4")
    }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55000000")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#3B2B20")
        strokeWidth = 2f
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D99B52")
        strokeWidth = 3f
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        hitAreas.clear()

        val projection = mapView.projection
        photos.forEach { photo ->
            val latitude = photo.latitude
            val longitude = photo.longitude
            if (latitude == null || longitude == null) return@forEach

            val bitmap = bitmapFor(photo.fileName) ?: return@forEach
            val point = projection.toPixels(GeoPoint(latitude, longitude), null)
            val tilt = when (abs(photo.id.hashCode()) % 3) {
                0 -> -8f
                1 -> 6f
                else -> -3f
            }

            val cardWidth = 72f
            val cardHeight = 86f
            val left = -cardWidth / 2
            val top = -cardHeight + 12f
            val rect = RectF(left, top, left + cardWidth, top + cardHeight)

            canvas.save()
            canvas.translate(point.x.toFloat(), point.y.toFloat())
            canvas.rotate(tilt)
            canvas.drawRect(
                rect.left + 3f,
                rect.top + 4f,
                rect.right + 3f,
                rect.bottom + 4f,
                shadowPaint,
            )
            canvas.drawRect(rect, framePaint)
            canvas.drawRect(rect, borderPaint)
            canvas.drawBitmap(
                bitmap,
                null,
                RectF(left + 5f, top + 5f, left + cardWidth - 5f, top + cardHeight - 22f),
                null,
            )
            canvas.restore()

            canvas.drawLine(
                point.x.toFloat(),
                point.y.toFloat(),
                point.x.toFloat(),
                point.y - 4f,
                pinPaint,
            )
            hitAreas.add(
                RectF(
                    point.x - cardWidth / 2,
                    point.y - cardHeight,
                    point.x + cardWidth / 2,
                    point.y + 8f,
                ) to photo.id,
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

    private fun bitmapFor(fileName: String): Bitmap? {
        cache.get(fileName)?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = 6 }
        val bitmap = BitmapFactory.decodeFile(filePath(fileName), options) ?: return null
        cache.put(fileName, bitmap)
        return bitmap
    }
}
