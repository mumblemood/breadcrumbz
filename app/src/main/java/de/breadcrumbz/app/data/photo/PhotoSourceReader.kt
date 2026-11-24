package de.breadcrumbz.app.data.photo

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import java.io.File

/** Ein gelesenes Foto samt Orts- und Zeitangaben, auch wenn sie nicht in den Bytes stehen. */
data class SourcePhoto(
    val bytes: ByteArray,
    val latitude: Double?,
    val longitude: Double?,
    val capturedAt: Long?,
)

/**
 * Liest die Bytes eines ausgewählten Fotos und zusätzlich die GPS-Daten aus dem
 * MediaStore. Seit Android 10 entfernt das System beim normalen Öffnen eines
 * Galerie-Fotos die GPS-Daten aus den EXIF-Informationen. Die App probiert
 * deshalb mehrere Wege:
 *
 * 1. das unveränderte Original über MediaStore.setRequireOriginal(),
 * 2. die Spalten latitude/longitude des MediaStore-Eintrags,
 * 3. die ganz normale Datei (wenn es sonst nichts gibt).
 */
object PhotoSourceReader {
    fun read(context: Context, uri: Uri): ByteArray? = readWithMetadata(context, uri)?.bytes

    fun readWithMetadata(context: Context, uri: Uri): SourcePhoto? {
        val originals = originalBytes(context, uri)
        val exifBytes = originals.firstOrNull { hasLocation(it) }
        val bytes = exifBytes ?: originals.firstOrNull() ?: plainBytes(context, uri) ?: return null
        val exif = metadataOf(bytes) ?: PhotoMetadata(null, null, null)
        val store = mediaMetadata(context, uri)

        return SourcePhoto(
            bytes = bytes,
            latitude = exif.latitude ?: store?.latitude,
            longitude = exif.longitude ?: store?.longitude,
            capturedAt = exif.capturedAt ?: store?.capturedAt,
        )
    }

    private fun metadataOf(bytes: ByteArray): PhotoMetadata? = try {
        PhotoImporter.read(bytes)
    } catch (_: Exception) {
        null
    }

    private fun hasLocation(bytes: ByteArray): Boolean {
        val meta = metadataOf(bytes)
        return meta?.latitude != null && meta.longitude != null
    }

    private fun plainBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (_: Exception) {
        null
    }

    private fun originalBytes(context: Context, uri: Uri): List<ByteArray> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        if (context.checkSelfPermission(Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return emptyList()
        }

        val result = mutableListOf<ByteArray>()
        candidates(context, uri).distinct().forEach { candidate ->
            try {
                val original = MediaStore.setRequireOriginal(candidate)
                val bytes = context.contentResolver.openInputStream(original)?.use { it.readBytes() }
                if (!bytes.isNullOrEmpty()) result.add(bytes)
            } catch (_: Exception) {
                // Nächste Möglichkeit probieren.
            }
        }
        rawFile(context, uri)?.let { file ->
            try {
                val bytes = file.readBytes()
                if (bytes.isNotEmpty()) result.add(bytes)
            } catch (_: Exception) {
                // Kein direkter Dateizugriff möglich.
            }
        }
        return result
    }

    private fun candidates(context: Context, uri: Uri): List<Uri> = buildList {
        if (uri.scheme == "content" && uri.authority == "media") add(uri)
        // getMediaUri gibt es erst ab API 30 – ältere Geräte dürfen nicht abstürzen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()?.let { add(it) }
        }
        documentId(context, uri)?.let { id ->
            val mediaId = id.substringAfterLast(':').toLongOrNull() ?: return@let
            add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaId))
            add(ContentUris.withAppendedId(MediaStore.Images.Media.INTERNAL_CONTENT_URI, mediaId))
        }
    }

    /** Liest lat/long aus dem MediaStore – auch wenn die Datei selbst GPS-verstümmelt ist. */
    private fun mediaMetadata(context: Context, uri: Uri): SourcePhoto? {
        candidates(context, uri).distinct().forEach { candidate ->
            val projection = arrayOf("latitude", "longitude", "datetaken")
            val rows = try {
                context.contentResolver.query(candidate, projection, null, null, null)
            } catch (_: Exception) {
                null
            } ?: return@forEach
            rows.use { cursor ->
                if (!cursor.moveToFirst()) return@use
                val lat = cursor.getDoubleOrNull("latitude")
                val lon = cursor.getDoubleOrNull("longitude")
                val time = cursor.getLongOrNull("datetaken")?.takeIf { it > 0L }
                if (lat != null && lon != null) {
                    return SourcePhoto(ByteArray(0), lat, lon, time)
                }
            }
        }
        return null
    }

    private fun android.database.Cursor.getDoubleOrNull(name: String): Double? = try {
        val index = getColumnIndex(name)
        if (index >= 0 && !isNull(index)) getDouble(index) else null
    } catch (_: Exception) {
        null
    }

    private fun android.database.Cursor.getLongOrNull(name: String): Long? = try {
        val index = getColumnIndex(name)
        if (index >= 0 && !isNull(index)) getLong(index) else null
    } catch (_: Exception) {
        null
    }

    private fun documentId(context: Context, uri: Uri): String? {
        if (!DocumentsContract.isDocumentUri(context, uri)) return null
        return runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
    }

    private fun rawFile(context: Context, uri: Uri): File? {
        if (uri.scheme == "file") return uri.path?.let { File(it) }
        val id = documentId(context, uri) ?: return null
        if (!id.startsWith("raw:", ignoreCase = true)) return null
        return File(id.removePrefix("raw:"))
    }
}
