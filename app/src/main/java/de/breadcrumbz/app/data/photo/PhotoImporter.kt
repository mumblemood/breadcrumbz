package de.breadcrumbz.app.data.photo

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale

data class PhotoMetadata(
    val capturedAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
)

/**
 * Ein aus der Galerie ausgewähltes Foto, bevor es kopiert wird.
 * Die Orts- und Zeitangaben können auch aus dem MediaStore stammen, wenn die
 * ausgelesene Datei selbst keine GPS-Daten mehr enthält.
 */
data class ImportedPhoto(
    val bytes: ByteArray,
    val mimeType: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val capturedAt: Long? = null,
)

/** Liest Aufnahmezeit und GPS aus den EXIF-Daten eines Fotos. */
object PhotoImporter {
    fun read(bytes: ByteArray): PhotoMetadata {
        val exif = ExifInterface(ByteArrayInputStream(bytes))
        val position = FloatArray(2)
        val hasPosition = exif.getLatLong(position)
        val capturedAt = readDate(exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            ?: readDate(exif.getAttribute(ExifInterface.TAG_DATETIME))

        return PhotoMetadata(
            capturedAt = capturedAt,
            latitude = if (hasPosition) position[0].toDouble() else null,
            longitude = if (hasPosition) position[1].toDouble() else null,
        )
    }

    private fun readDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.GERMANY).parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }

    /** Kleine Vorschau für Listen und Karten-Pins. */
    fun decodeThumbnail(bytes: ByteArray): Bitmap? = decode(bytes, sampleSize = 6)

    /**
     * Dekodiert ein gespeichertes Foto und bringt es entsprechend der EXIF-Angabe
     * in die Anzeige-Ausrichtung (gedreht und/oder gespiegelt). Die Datei selbst
     * bleibt unverändert, damit vorhandene Fotos und Backups nicht nachbearbeitet
     * werden müssen.
     *
     * @param maxSide größer 0: das Bild wird beim Laden so weit verkleinert, dass
     *   die längere Seite höchstens etwa so lang ist. Das schützt vor Abstürzen
     *   bei sehr großen Fotos (z. B. 50 Megapixel). Dann wird sampleSize ignoriert.
     */
    fun decode(filePath: String, sampleSize: Int = 1, maxSide: Int = 0): Bitmap? {
        val orientation = try {
            orientationOf(ExifInterface(filePath))
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val sample = if (maxSide > 0) {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, bounds)
            sampleFor(bounds.outWidth, bounds.outHeight, maxSide)
        } else {
            sampleSize
        }
        val bitmap = try {
            BitmapFactory.decodeFile(
                filePath,
                BitmapFactory.Options().apply { inSampleSize = sample },
            )
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        return applyOrientation(bitmap, orientation)
    }

    fun decode(bytes: ByteArray, sampleSize: Int = 1): Bitmap? {
        val orientation = try {
            orientationOf(ExifInterface(ByteArrayInputStream(bytes)))
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val bitmap = try {
            BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize },
            )
        } catch (_: OutOfMemoryError) {
            null
        } ?: return null
        return applyOrientation(bitmap, orientation)
    }

    /** Größte Zweierpotenz, bei der die längere Seite noch mindestens maxSide bleibt. */
    private fun sampleFor(width: Int, height: Int, maxSide: Int): Int {
        val longest = maxOf(width, height)
        if (longest <= 0) return 1
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        return sample
    }

    private fun orientationOf(exif: ExifInterface): Int =
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

    /** Alle acht EXIF-Ausrichtungen, auch gespiegelte (z. B. manche Selfie-Kameras). */
    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }
        return try {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                .also { if (it !== bitmap) bitmap.recycle() }
        } catch (_: OutOfMemoryError) {
            bitmap
        }
    }
}
