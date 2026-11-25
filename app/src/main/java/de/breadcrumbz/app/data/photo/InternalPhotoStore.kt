package de.breadcrumbz.app.data.photo

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Fotos werden als Kopie im privaten App-Speicher abgelegt.
 * So bleiben Bilder erhalten, wenn sie später aus der Galerie gelöscht werden,
 * und ein Backup kann alle Bilder vollständig mitnehmen.
 */
class InternalPhotoStore(context: Context) {
    private val directory = File(context.applicationContext.filesDir, "photos").apply { mkdirs() }

    fun createFileName(mimeType: String): String {
        val extension = when (mimeType.lowercase()) {
            "image/png" -> "png"
            "image/webp" -> "webp"
            "image/heic", "image/heif" -> "heic"
            else -> "jpg"
        }
        return "${UUID.randomUUID()}.$extension"
    }

    suspend fun save(bytes: ByteArray, mimeType: String): String = withContext(Dispatchers.IO) {
        val fileName = createFileName(mimeType)
        File(directory, fileName).writeBytes(bytes)
        fileName
    }

    fun fileFor(fileName: String): File = File(directory, fileName)

    /** Schreibt eine Bilddatei mit vorhandenem Namen (für den Backup-Import). */
    suspend fun write(fileName: String, bytes: ByteArray): Boolean = withContext(Dispatchers.IO) {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') ||
            fileName == "." || fileName == ".."
        ) {
            false
        } else {
            File(directory, fileName).writeBytes(bytes)
            true
        }
    }

    /**
     * Übernimmt eine Bilddatei aus dem Backup-Zwischenordner. Eine vorhandene
     * Datei gleichen Namens bleibt unangetastet (gleicher Name = gleiches Foto).
     */
    fun importFile(fileName: String, source: File): Boolean {
        if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\') ||
            fileName == "." || fileName == ".."
        ) {
            return false
        }
        val target = File(directory, fileName)
        if (target.exists()) return true
        return try {
            source.copyTo(target)
            true
        } catch (_: Exception) {
            target.delete()
            false
        }
    }

    suspend fun delete(fileName: String): Boolean = withContext(Dispatchers.IO) {
        if (fileName.contains('/') || fileName.contains('\\')) {
            false
        } else {
            File(directory, fileName).delete()
        }
    }
}
