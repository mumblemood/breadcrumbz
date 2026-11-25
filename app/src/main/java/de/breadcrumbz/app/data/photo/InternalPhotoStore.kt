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

    /**
     * Fotos empfangener Reisen liegen getrennt: Dieser Ordner ist für den
     * Android-Bildviewer (FileProvider) nicht freigegeben und wird von keiner
     * Sicherung erfasst. Ihre Dateinamen beginnen immer mit [RECEIVED_PREFIX].
     */
    private val receivedDirectory =
        File(context.applicationContext.filesDir, "received_photos").apply { mkdirs() }

    private fun dirFor(fileName: String): File =
        if (fileName.startsWith(RECEIVED_PREFIX)) receivedDirectory else directory

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

    fun fileFor(fileName: String): File = File(dirFor(fileName), fileName)

    /** Ist das ein Foto einer empfangenen Reise? Dann nie nach außen geben. */
    fun isReceived(fileName: String): Boolean = fileName.startsWith(RECEIVED_PREFIX)

    /** Ersetzt ein Foto einer empfangenen Reise (neue Version vom Absender). */
    fun replaceReceived(fileName: String, source: File): Boolean {
        if (!isReceived(fileName) || !isSafe(fileName)) return false
        val target = File(receivedDirectory, fileName)
        return try {
            source.copyTo(target, overwrite = true)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteReceived(fileName: String) {
        if (isReceived(fileName) && isSafe(fileName)) File(receivedDirectory, fileName).delete()
    }

    private fun isSafe(fileName: String): Boolean =
        fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\') &&
            fileName != "." && fileName != ".."

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
        // Ein Backup darf nie in den Ordner der empfangenen Fotos schreiben.
        if (isReceived(fileName)) return false
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
            fileFor(fileName).delete()
        }
    }

    companion object {
        const val RECEIVED_PREFIX = "r-"
    }
}
