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
     * Dekodiert ein gespeichertes Foto und dreht es entsprechend der EXIF-Angabe
     * in die Anzeige-Ausrichtung. Die Datei selbst bleibt unverändert, damit vorhandene
     * Fotos und Backups nicht nachbearbeitet werden müssen.
     */
    fun decode(filePath: String, sampleSize: Int = 1): Bitmap? {
        val degrees = try {
            rotationDegrees(ExifInterface(filePath))
        } catch (_: Exception) {
            0
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return rotate(BitmapFactory.decodeFile(filePath, options) ?: return null, degrees)
    }

    fun decode(bytes: ByteArray, sampleSize: Int = 1): Bitmap? {
        val degrees = try {
            rotationDegrees(ExifInterface(ByteArrayInputStream(bytes)))
        } catch (_: Exception) {
            0
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return rotate(
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null,
            degrees,
        )
    }

    private fun rotationDegrees(exif: ExifInterface): Int = when (
        exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    ) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90
        ExifInterface.ORIENTATION_ROTATE_180 -> 180
        ExifInterface.ORIENTATION_ROTATE_270 -> 270
        else -> 0
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
