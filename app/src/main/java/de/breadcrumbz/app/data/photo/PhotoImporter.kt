package de.breadcrumbz.app.data.photo

import android.graphics.BitmapFactory
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
    fun decodeThumbnail(bytes: ByteArray): android.graphics.Bitmap? {
        val options = BitmapFactory.Options().apply { inSampleSize = 6 }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }
}
