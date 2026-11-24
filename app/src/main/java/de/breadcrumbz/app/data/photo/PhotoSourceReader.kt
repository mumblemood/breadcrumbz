package de.breadcrumbz.app.data.photo

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File
import java.io.InputStream

/**
 * Liest die Bytes eines ausgewählten Fotos.
 *
 * Seit Android 10 entfernt das System beim normalen Öffnen eines Galerie-Fotos
 * die GPS-Daten aus den EXIF-Informationen. Über die MediaStore-Verknüpfung
 * lässt sich das unveränderte Original lesen – wenn die Erlaubnis dafür
 * erteilt wurde. Das funktioniert auch für SAF-Dokument-Uris
 * ("image:1234"), wie sie die Foto-Auswahl liefert. Falls nichts geht, wird
 * ganz normal weitergelesen.
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

        val candidates = buildList {
            // getMediaUri gibt es erst ab API 30 – ältere Geräte dürfen nicht abstürzen.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                runCatching { MediaStore.getMediaUri(context, uri) }
                    .getOrNull()?.let { add(it) }
            }
            documentMediaUri(context, uri)?.let { add(it) }
        }

        candidates.distinct().forEach { candidate ->
            try {
                val original = MediaStore.setRequireOriginal(candidate)
                val stream = context.contentResolver.openInputStream(original)
                if (stream != null) return stream
            } catch (_: Exception) {
                // Nächste Möglichkeit probieren.
            }
        }

        // documentsui liefert für lokale Dateien manchmal "raw:/pfad/datei.jpg".
        if (DocumentsContract.isDocumentUri(context, uri)) {
            val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            if (id?.startsWith("raw:") == true) {
                val file = File(id.removePrefix("raw:"))
                if (file.exists()) return runCatching { file.inputStream() }.getOrNull()
            }
        }
        return null
    }

    private fun documentMediaUri(context: Context, uri: Uri): Uri? {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        val id = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
            ?: return null
        val mediaId = id.substringAfterLast(':').toLongOrNull() ?: return null
        val base = when {
            id.startsWith("image", ignoreCase = true) ||
                id.startsWith("raw", ignoreCase = true) ->
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            id.startsWith("video", ignoreCase = true) ->
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        return ContentUris.withAppendedId(base, mediaId)
    }
}
