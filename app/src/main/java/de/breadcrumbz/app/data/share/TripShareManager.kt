package de.breadcrumbz.app.data.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.room.withTransaction
import de.breadcrumbz.app.data.backup.toEntry
import de.breadcrumbz.app.data.backup.toJson
import de.breadcrumbz.app.data.backup.toPhoto
import de.breadcrumbz.app.data.backup.toSegmentMode
import de.breadcrumbz.app.data.backup.toStop
import de.breadcrumbz.app.data.backup.toTrip
import de.breadcrumbz.app.data.backup.toWaypoint
import de.breadcrumbz.app.data.db.BreadcrumbzDatabase
import de.breadcrumbz.app.data.db.EntryDao
import de.breadcrumbz.app.data.db.PhotoDao
import de.breadcrumbz.app.data.db.SegmentModeDao
import de.breadcrumbz.app.data.db.StopDao
import de.breadcrumbz.app.data.db.TripDao
import de.breadcrumbz.app.data.db.WaypointDao
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.photo.PhotoImporter
import de.breadcrumbz.app.domain.util.segmentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val FORMAT = "breadcrumbz-reise"
private const val TRIP_JSON = "reise.json"
private const val PHOTO_DIR = "bilder/"
private const val REDUCED_MAX_SIDE = 2000
const val SHARE_EXTENSION = "breadcrumbz"

/** Einstellungen beim Verschicken. */
data class ShareOptions(
    /** Erscheint beim Empfänger als „empfangen von …“ (darf leer sein). */
    val senderName: String,
    /** null = ohne Passwort. */
    val password: String?,
    /** true = Fotos auf etwa 2000 Pixel verkleinern. */
    val reducePhotos: Boolean,
)

sealed class ReceiveResult {
    /** Die Datei ist verschlüsselt – bitte Passwort eingeben. */
    data object NeedsPassword : ReceiveResult()
    data object WrongPassword : ReceiveResult()
    data class Success(
        val tripId: String,
        val title: String,
        val sender: String?,
        /** true = eine ältere Fassung dieser Reise wurde ersetzt. */
        val updated: Boolean,
        val photos: Int,
    ) : ReceiveResult()
    data class Failure(val message: String) : ReceiveResult()
}

/**
 * Verschickt eine einzelne Reise als Datei (`Name.breadcrumbz`) und nimmt
 * solche Dateien entgegen.
 *
 * - Die Datei ist eine ZIP mit `reise.json` und `bilder/`; mit Passwort wird
 *   sie komplett mit AES-256 verschlüsselt (siehe [ShareCrypto]).
 * - Empfangene Reisen bekommen eigene IDs mit Vorsatz „r-“. Dadurch kollidieren
 *   sie nie mit eigenen Reisen – auch nicht, wenn man die eigene Datei auf dem
 *   eigenen Handy öffnet. Eine neue Fassung derselben Reise ersetzt die alte.
 * - Eigene Reisen und Daten werden beim Empfangen nie verändert.
 */
class TripShareManager(
    private val context: Context,
    private val database: BreadcrumbzDatabase,
    private val tripDao: TripDao,
    private val stopDao: StopDao,
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
    private val waypointDao: WaypointDao,
    private val segmentModeDao: SegmentModeDao,
    private val photoStore: InternalPhotoStore,
    private val appVersion: String,
) {

    /** Dateiname für die Reise, z. B. „Italien 2026.breadcrumbz“. */
    fun fileNameFor(title: String): String {
        val clean = title.replace(Regex("[^\\p{L}\\p{N} _.-]"), "").trim().take(60)
        return "${clean.ifBlank { "Reise" }}.$SHARE_EXTENSION"
    }

    /**
     * Schreibt die Datei in den Zwischenspeicher der App, damit sie über das
     * Android-Teilen-Menü verschickt werden kann. Ältere Dateien dort werden
     * vorher gelöscht.
     */
    suspend fun exportToShareFile(tripId: String, options: ShareOptions): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trip = tripDao.findById(tripId) ?: error("Reise nicht gefunden.")
                val dir = File(context.cacheDir, "share").apply {
                    deleteRecursively()
                    mkdirs()
                }
                val file = File(dir, fileNameFor(trip.title))
                try {
                    file.outputStream().use { writeTrip(tripId, it, options) }
                } catch (error: Throwable) {
                    file.delete()
                    throw error
                }
                file
            }
        }

    /** Speichert die Datei an einem selbst gewählten Ort (System-Auswahl). */
    suspend fun exportToUri(tripId: String, uri: Uri, options: ShareOptions): String =
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri)
                    ?: return@withContext "Speichern fehlgeschlagen: Datei nicht schreibbar."
                val photos = stream.use { writeTrip(tripId, it, options) }
                "Reise gespeichert ($photos Fotos)" +
                    if (options.password != null) ", mit Passwort geschützt." else "."
            } catch (error: Exception) {
                "Speichern fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}"
            }
        }

    /** Gibt die Zahl der verpackten Fotos zurück. */
    private suspend fun writeTrip(tripId: String, target: OutputStream, options: ShareOptions): Int {
        val trip = tripDao.findById(tripId) ?: error("Reise nicht gefunden.")
        // Empfangene Reisen dürfen nicht weitergeschickt werden.
        if (trip.isReceived) error("Empfangene Reisen können nicht weitergeschickt werden.")
        val stops = stopDao.observeForTrip(tripId).first()
        val entries = entryDao.observeForTrip(tripId).first()
        val photos = photoDao.observeForTrip(tripId).first()
        val waypoints = waypointDao.observeForTrip(tripId).first()
        val segmentModes = segmentModeDao.observeForTrip(tripId).first()

        val password = options.password
        val output: OutputStream = if (password != null) {
            ShareCrypto.EncryptingOutputStream(target.buffered(), password)
        } else {
            target.buffered()
        }
        var written = 0
        val photoRows = JSONArray()
        ZipOutputStream(output).use { zip ->
            photos.forEach { photo ->
                val file = photoStore.fileFor(photo.fileName)
                if (!file.exists()) return@forEach
                val reduced = if (options.reducePhotos) shrink(file) else null
                val extension = if (reduced != null) {
                    "jpg"
                } else {
                    file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,5}")) } ?: "jpg"
                }
                val name = "${photo.id}.$extension"
                zip.putNextEntry(ZipEntry(PHOTO_DIR + name))
                if (reduced != null) {
                    zip.write(reduced)
                } else {
                    file.inputStream().use { it.copyTo(zip) }
                }
                zip.closeEntry()
                written++
                photoRows.put(
                    photo.copy(
                        fileName = name,
                        mimeType = if (reduced != null) "image/jpeg" else photo.mimeType,
                    ).toJson(),
                )
            }
            val data = JSONObject().apply {
                put("format", FORMAT)
                put("version", 1)
                put("appVersion", appVersion)
                put("exportedAt", System.currentTimeMillis())
                put("sender", options.senderName.trim())
                put("reducedPhotos", options.reducePhotos)
                put("trip", trip.toJson())
                put("stops", JSONArray(stops.map { it.toJson() }))
                put("entries", JSONArray(entries.map { it.toJson() }))
                put("photos", photoRows)
                put("waypoints", JSONArray(waypoints.map { it.toJson() }))
                put("segmentModes", JSONArray(segmentModes.map { it.toJson() }))
            }
            // Die Reisedaten kommen zuletzt: So steht fest, welche Fotos dabei sind.
            zip.putNextEntry(ZipEntry(TRIP_JSON))
            zip.write(data.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return written
    }

    /** Foto in Anzeige-Ausrichtung auf ca. 2000 Pixel verkleinern (JPEG). */
    private fun shrink(file: File): ByteArray? = try {
        val decoded = PhotoImporter.decode(file.absolutePath, maxSide = REDUCED_MAX_SIDE)
        decoded?.let { bitmap ->
            val longest = maxOf(bitmap.width, bitmap.height)
            val scaled = if (longest > REDUCED_MAX_SIDE) {
                val factor = REDUCED_MAX_SIDE.toFloat() / longest
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * factor).toInt().coerceAtLeast(1),
                    (bitmap.height * factor).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
            out.toByteArray()
        }
    } catch (_: Throwable) {
        // Lässt sich das Foto nicht verkleinern, geht das Original mit.
        null
    }

    /**
     * Nimmt eine Reise-Datei an. Ohne Passwort wird bei einer verschlüsselten
     * Datei [ReceiveResult.NeedsPassword] gemeldet; dann einfach mit Passwort
     * erneut aufrufen.
     */
    suspend fun receive(uri: Uri, password: String?): ReceiveResult = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "receive-${System.currentTimeMillis()}")
        try {
            staging.mkdirs()
            receiveStaged(uri, password, staging)
        } catch (_: ShareCrypto.WrongPasswordException) {
            ReceiveResult.WrongPassword
        } catch (error: Exception) {
            ReceiveResult.Failure(
                "Die Reise konnte nicht geöffnet werden (${error.message ?: "Fehler"}). " +
                    "Es wurde nichts geändert.",
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun receiveStaged(uri: Uri, password: String?, staging: File): ReceiveResult {
        val raw = context.contentResolver.openInputStream(uri)
            ?: return ReceiveResult.Failure("Die Datei ist nicht lesbar.")
        var content = ""
        var isBackup = false
        BufferedInputStream(raw).use { buffered ->
            buffered.mark(16)
            val start = ByteArray(8)
            val read = buffered.read(start)
            buffered.reset()
            val encrypted = read == 8 && ShareCrypto.isEncrypted(start)
            val isZip = read >= 2 && start[0] == 'P'.code.toByte() && start[1] == 'K'.code.toByte()
            val input: InputStream = when {
                encrypted && password == null -> return ReceiveResult.NeedsPassword
                encrypted -> ShareCrypto.DecryptingInputStream(buffered, password!!)
                isZip -> buffered
                else -> return ReceiveResult.Failure(
                    "Das ist keine Breadcrumbz-Reise. Es wurde nichts geändert.",
                )
            }
            ZipInputStream(input).use { zip ->
                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    val name = entry?.name.orEmpty()
                    when {
                        name == TRIP_JSON -> content = zip.readBytes().toString(Charsets.UTF_8)
                        name == "reise_daten.json" -> isBackup = true
                        name.startsWith(PHOTO_DIR) -> {
                            val fileName = name.removePrefix(PHOTO_DIR)
                            if (isSafeName(fileName)) {
                                File(staging, fileName).outputStream().use { zip.copyTo(it) }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        if (content.isBlank()) {
            return ReceiveResult.Failure(
                if (isBackup) {
                    "Das ist ein Backup, keine einzelne Reise. Bitte über „Backup einspielen“ öffnen."
                } else {
                    "In der Datei steckt keine Reise. Es wurde nichts geändert."
                },
            )
        }
        val root = JSONObject(content)
        if (root.optString("format") != FORMAT) {
            return ReceiveResult.Failure("Das ist keine Breadcrumbz-Reise. Es wurde nichts geändert.")
        }
        if (root.optInt("version", 1) > 1) {
            return ReceiveResult.Failure(
                "Diese Reise stammt aus einer neueren Breadcrumbz-Version. Bitte erst die App aktualisieren.",
            )
        }
        val sender = root.optString("sender").trim().ifBlank { null }
        val tripJson = root.optJSONObject("trip")
            ?: return ReceiveResult.Failure("In der Datei steckt keine Reise. Es wurde nichts geändert.")
        val original = tripJson.toTrip()
        fun rid(id: String) = InternalPhotoStore.RECEIVED_PREFIX + id
        val newTripId = rid(original.id)

        val existing = tripDao.findById(newTripId)
        if (existing != null && !existing.isReceived) {
            // Darf nie passieren – eigene Reisen werden nie überschrieben.
            return ReceiveResult.Failure("Diese Reise gibt es schon als eigene Reise. Es wurde nichts geändert.")
        }

        val stops = root.optJSONArray("stops").rows { it.toStop() }
        val stopIds = stops.map { it.id }.toSet()
        val entries = root.optJSONArray("entries").rows { it.toEntry() }
        val entryIds = entries.map { it.id }.toSet()
        val photoRows = root.optJSONArray("photos").rows { it.toPhoto() }
        val waypoints = root.optJSONArray("waypoints").rows { it.toWaypoint() }
        val modes = root.optJSONArray("segmentModes").rows { it.toSegmentMode() }

        // Fotos nur übernehmen, wenn die Bilddatei wirklich mitgekommen ist.
        val photos = photoRows.mapNotNull { photo ->
            val staged = File(staging, photo.fileName)
            if (!isSafeName(photo.fileName) || !staged.exists()) return@mapNotNull null
            photo to staged
        }
        val photoIds = photos.map { it.first.id }.toSet()
        val now = System.currentTimeMillis()
        val oldFiles = existing?.let { photoDao.fileNamesForTrip(newTripId) }.orEmpty()

        fun stopRef(id: String?) = id?.takeIf { it in stopIds }?.let(::rid)
        fun entryRef(id: String?) = id?.takeIf { it in entryIds }?.let(::rid)

        val newFiles = mutableListOf<Pair<String, File>>()
        database.withTransaction {
            if (existing != null) tripDao.deleteById(newTripId)
            tripDao.insert(
                original.copy(
                    id = newTripId,
                    coverPhotoId = original.coverPhotoId?.takeIf { it in photoIds }?.let(::rid),
                    titleIsAuto = false,
                    // Erstes Empfangen: oben in der Liste. Neue Fassung: Platz behalten.
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                    isReceived = true,
                    receivedFrom = sender,
                    receivedAt = now,
                ),
            )
            stops.forEach { stop ->
                stopDao.insertIfMissing(
                    stop.copy(
                        id = rid(stop.id),
                        tripId = newTripId,
                        entryId = entryRef(stop.entryId),
                    ),
                )
            }
            entries.forEach { entry ->
                entryDao.insertIfMissing(
                    entry.copy(
                        id = rid(entry.id),
                        tripId = newTripId,
                        stopId = stopRef(entry.stopId),
                    ),
                )
            }
            photos.forEach { (photo, staged) ->
                val fileName = rid(photo.fileName)
                photoDao.insertIfMissing(
                    photo.copy(
                        id = rid(photo.id),
                        tripId = newTripId,
                        stopId = stopRef(photo.stopId),
                        entryId = entryRef(photo.entryId),
                        fileName = fileName,
                    ),
                )
                newFiles += fileName to staged
            }
            waypoints.forEach { point ->
                waypointDao.insertIfMissing(
                    point.copy(
                        id = rid(point.id),
                        tripId = newTripId,
                        afterStopId = stopRef(point.afterStopId),
                    ),
                )
            }
            modes.filter { it.fromStopId in stopIds && it.toStopId in stopIds }.forEach { mode ->
                val from = rid(mode.fromStopId)
                val to = rid(mode.toStopId)
                segmentModeDao.insertIfMissing(
                    mode.copy(
                        id = segmentKey(from, to),
                        tripId = newTripId,
                        fromStopId = from,
                        toStopId = to,
                    ),
                )
            }
        }

        // Erst nach dem Speichern: Bilddateien an ihren Platz, alte Fassung aufräumen.
        newFiles.forEach { (name, staged) -> photoStore.replaceReceived(name, staged) }
        val keep = newFiles.map { it.first }.toSet()
        oldFiles.filterNot { it in keep }.forEach { photoStore.deleteReceived(it) }

        return ReceiveResult.Success(
            tripId = newTripId,
            title = original.title,
            sender = sender,
            updated = existing != null,
            photos = newFiles.size,
        )
    }

    private fun isSafeName(fileName: String): Boolean =
        fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\') &&
            fileName != "." && fileName != ".." && fileName.length <= 120

    private fun <T> JSONArray?.rows(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            runCatching { map(getJSONObject(index)) }.getOrNull()
        }
    }
}
