package de.breadcrumbz.app.data.photo

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.InputStream

/**
 * Liest die Bytes eines ausgewählten Fotos.
 *
 * Seit Android 10 entfernt das System beim normalen Öffnen eines Galerie-Fotos
 * die GPS-Daten aus den EXIF-Informationen. Über die MediaStore-Verknüpfung
 * lässt sich das unveränderte Original lesen – wenn die Erlaubnis dafür
 * erteilt wurde. Falls das nicht geht, wird ganz normal weitergelesen.
 */
object PhotoSourceReader {
    fun read(context: Context, uri: Uri): ByteArray? {
        originalStream(context, uri)?.use { stream ->
            val bytes = stream.readBytes()
            val metadata = PhotoImporter.read(bytes)
            if (metadata.latitude != null && metadata.longitude != null) {
                return bytes
            }
        }
        return context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    }

    private fun originalStream(context: Context, uri: Uri): InputStream? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val mediaUri = MediaStore.getMediaUri(context, uri) ?: return null
            val originalUri = MediaStore.setRequireOriginal(mediaUri)
            context.contentResolver.openInputStream(originalUri)
        } catch (_: Exception) {
            null
        }
    }
}
