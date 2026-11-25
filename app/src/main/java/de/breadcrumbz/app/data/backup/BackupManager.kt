package de.breadcrumbz.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import de.breadcrumbz.app.data.db.BreadcrumbzDatabase
import de.breadcrumbz.app.data.db.EntryDao
import de.breadcrumbz.app.data.db.PhotoDao
import de.breadcrumbz.app.data.db.PlaceNameDao
import de.breadcrumbz.app.data.db.SegmentModeDao
import de.breadcrumbz.app.data.db.StopDao
import de.breadcrumbz.app.data.db.TripDao
import de.breadcrumbz.app.data.db.WaypointDao
import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.PlaceNameEntity
import de.breadcrumbz.app.data.model.RouteSegmentModeEntity
import de.breadcrumbz.app.data.model.RouteWaypointEntity
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.domain.util.segmentKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Komplett lokales Backup über die System-Auswahl (SAF):
 * `reise_daten.json`, `bilder/` und `manifest.json` in einer ZIP-Datei.
 * Vor einem Import wird die aktuelle Datenbank als Snapshot kopiert.
 */
private const val DB_NAME = "breadcrumbz.db"

class BackupManager(
    private val context: Context,
    private val database: BreadcrumbzDatabase,
    private val tripDao: TripDao,
    private val stopDao: StopDao,
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
    private val placeNameDao: PlaceNameDao,
    private val waypointDao: WaypointDao,
    private val segmentModeDao: SegmentModeDao,
    private val photoStore: InternalPhotoStore,
    private val appVersion: String,
) {
    suspend fun exportTo(uri: Uri): String = withContext(Dispatchers.IO) {
        // Empfangene Reisen gehören dem Absender: Sie kommen nie ins eigene
        // Backup, damit ihre Fotos die App auf diesem Weg nicht verlassen.
        val allTrips = tripDao.observeAll().first()
        val received = allTrips.filter { it.isReceived }.map { it.id }.toSet()
        val trips = allTrips.filterNot { it.id in received }
        val stops = stopDao.observeAll().first().filterNot { it.tripId in received }
        val entries = entryDao.observeAll().first().filterNot { it.tripId in received }
        val photos = photoDao.observeAll().first().filterNot {
            it.tripId in received || photoStore.isReceived(it.fileName)
        }
        val places = placeNameDao.observeAll().first()
        val waypoints = waypointDao.observeAll().first().filterNot { it.tripId in received }
        val segmentModes = segmentModeDao.observeAll().first().filterNot { it.tripId in received }

        val data = JSONObject().apply {
            put("format", "breadcrumbz-backup")
            put("version", 1)
            put("trips", JSONArray(trips.map { it.toJson() }))
            put("stops", JSONArray(stops.map { it.toJson() }))
            put("entries", JSONArray(entries.map { it.toJson() }))
            put("photos", JSONArray(photos.map { it.toJson() }))
            put("placeNames", JSONArray(places.map { it.toJson() }))
            put("waypoints", JSONArray(waypoints.map { it.toJson() }))
            put("segmentModes", JSONArray(segmentModes.map { it.toJson() }))
        }
        val manifest = JSONObject().apply {
            put("format", "breadcrumbz-backup")
            put("version", 1)
            put("appVersion", appVersion)
            put("exportedAt", System.currentTimeMillis())
            put("trips", trips.size)
            put("stops", stops.size)
            put("entries", entries.size)
            put("photos", photos.size)
            put("waypoints", waypoints.size)
            put("segmentModes", segmentModes.size)
        }

        var writtenImages = 0
        val stream = context.contentResolver.openOutputStream(uri)
            ?: return@withContext "Export fehlgeschlagen: Datei nicht schreibbar."
        stream.use { raw ->
            ZipOutputStream(raw.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("reise_daten.json"))
                zip.write(data.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
                photos.forEach { photo ->
                    val file = photoStore.fileFor(photo.fileName)
                    if (file.exists()) {
                        zip.putNextEntry(ZipEntry("bilder/${photo.fileName}"))
                        file.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                        writtenImages++
                    }
                }
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        "Backup gespeichert: ${trips.size} Reisen, ${stops.size} Pins, " +
            "${entries.size} Einträge, $writtenImages Bilder, " +
            "${waypoints.size} Routenpunkte, ${segmentModes.size} Abschnittsmodi."
    }

    /**
     * Spielt ein Backup ein, ohne etwas auf dem Handy zu löschen:
     * - Was es noch nicht gibt, wird neu angelegt.
     * - Was es schon gibt, wird nur überschrieben, wenn das Backup neuer ist
     *   (Zeitstempel `updatedAt`). Fotos und Routenpunkte ohne Zeitstempel
     *   bleiben so, wie sie auf dem Handy sind.
     * - Nie INSERT OR REPLACE auf Reisen oder Pins: das würde über die
     *   Fremdschlüssel alle Einträge, Pins und Fotos einer Reise mitlöschen.
     * - Alles läuft in einer Datenbank-Transaktion: bricht der Import ab,
     *   bleibt der alte Stand vollständig erhalten.
     * - Bilder werden einzeln in einen Zwischenordner gestreamt statt alle
     *   gleichzeitig in den Speicher geladen.
     */
    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        val staging = File(context.cacheDir, "import-${System.currentTimeMillis()}")
        try {
            importStaged(uri, staging)
        } finally {
            staging.deleteRecursively()
        }
    }

    private suspend fun importStaged(uri: Uri, staging: File): String {
        staging.mkdirs()
        var content = ""
        val input = context.contentResolver.openInputStream(uri)
            ?: return "Import fehlgeschlagen: Datei nicht lesbar."
        try {
            input.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry: ZipEntry?
                    while (zip.nextEntry.also { entry = it } != null) {
                        when (val name = entry?.name.orEmpty()) {
                            "reise_daten.json" ->
                                content = zip.readBytes().toString(Charsets.UTF_8)
                            "manifest.json" -> Unit
                            else -> if (name.startsWith("bilder/")) {
                                val fileName = name.removePrefix("bilder/")
                                if (isSafeFileName(fileName)) {
                                    File(staging, fileName).outputStream().use { out ->
                                        zip.copyTo(out)
                                    }
                                }
                            }
                        }
                        zip.closeEntry()
                    }
                }
            }
        } catch (_: Exception) {
            return "Import fehlgeschlagen: Die ZIP-Datei ist beschädigt. Es wurde nichts geändert."
        }
        if (content.isBlank()) {
            return "Keine reise_daten.json in der ZIP-Datei gefunden. Es wurde nichts geändert."
        }

        val root = try {
            JSONObject(content)
        } catch (_: Exception) {
            return "Die Datei ist kein gültiges Breadcrumbz-Backup. Es wurde nichts geändert."
        }
        // Ohne Sicherung kein Import: lieber abbrechen als ungesichert schreiben.
        val dbExists = context.getDatabasePath(DB_NAME).exists()
        if (dbExists && snapshot() == null) {
            return "Import abgebrochen: Die Sicherung des aktuellen Stands ist " +
                "fehlgeschlagen. Es wurde nichts geändert."
        }

        // Bilder zuerst an ihren Platz: vorhandene Dateien werden nie überschrieben.
        staging.listFiles()?.forEach { file -> photoStore.importFile(file.name, file) }

        val count = mutableMapOf<String, Int>()
        val kept = mutableMapOf<String, Int>()
        fun added(kind: String) { count[kind] = (count[kind] ?: 0) + 1 }
        fun skipped(kind: String) { kept[kind] = (kept[kind] ?: 0) + 1 }

        // Alte Backups haben das Verkehrsmittel noch am Pin. Diese Werte werden
        // beim Import auf den Abschnitt davor umgeschrieben, damit nichts verloren geht.
        val legacyModes = mutableMapOf<String, String>()
        database.withTransaction {
            root.optJSONArray("trips").forEachRow { obj ->
                val row = obj.toTrip()
                // Empfangene Reisen werden nur über „Reise empfangen“ angelegt.
                if (tripDao.isReceived(row.id) == true) return@forEachRow
                when {
                    tripDao.insertIfMissing(row) != -1L -> added("trips")
                    row.updatedAt > (tripDao.updatedAtOf(row.id) ?: Long.MAX_VALUE) -> {
                        tripDao.update(row); added("trips")
                    }
                    else -> skipped("trips")
                }
            }
            root.optJSONArray("stops").forEachRow { obj ->
                if (obj.has("transportMode") && !obj.isNull("transportMode")) {
                    obj.optString("id").takeIf { it.isNotBlank() }?.let { id ->
                        legacyModes[id] = obj.optString("transportMode")
                    }
                }
                val row = obj.toStop()
                when {
                    stopDao.insertIfMissing(row) != -1L -> added("stops")
                    row.updatedAt > (stopDao.updatedAtOf(row.id) ?: Long.MAX_VALUE) -> {
                        stopDao.update(row); added("stops")
                    }
                    else -> skipped("stops")
                }
            }
            root.optJSONArray("entries").forEachRow { obj ->
                val row = obj.toEntry()
                when {
                    entryDao.insertIfMissing(row) != -1L -> added("entries")
                    row.updatedAt > (entryDao.updatedAtOf(row.id) ?: Long.MAX_VALUE) -> {
                        entryDao.update(row); added("entries")
                    }
                    else -> skipped("entries")
                }
            }
            root.optJSONArray("photos").forEachRow { obj ->
                val row = obj.toPhoto()
                // Ohne Bilddatei kein Foto-Eintrag – sonst entstünde ein leeres Bild.
                if (!photoStore.fileFor(row.fileName).exists()) return@forEachRow
                if (photoDao.insertIfMissing(row) != -1L) added("photos") else skipped("photos")
            }
            root.optJSONArray("placeNames").forEachRow { obj ->
                placeNameDao.upsert(obj.toPlace()); added("places")
            }
            root.optJSONArray("waypoints").forEachRow { obj ->
                if (waypointDao.insertIfMissing(obj.toWaypoint()) != -1L) {
                    added("waypoints")
                } else {
                    skipped("waypoints")
                }
            }
            root.optJSONArray("segmentModes").forEachRow { obj ->
                val row = obj.toSegmentMode()
                when {
                    segmentModeDao.insertIfMissing(row) != -1L -> added("segmentModes")
                    row.updatedAt > (segmentModeDao.updatedAtOf(row.id) ?: Long.MAX_VALUE) -> {
                        segmentModeDao.update(row); added("segmentModes")
                    }
                    else -> skipped("segmentModes")
                }
            }
        }

        // Erst nach dem Commit: dann sieht die Abfrage auch die eben importierten Pins.
        val newModes = root.optJSONArray("segmentModes")
        if ((newModes?.length() ?: 0) == 0 && legacyModes.isNotEmpty()) {
            val allStops = stopDao.observeAll().first()
            val now = System.currentTimeMillis()
            legacyModes.forEach { (toId, mode) ->
                val to = allStops.firstOrNull { it.id == toId } ?: return@forEach
                val from = allStops.filter {
                    it.tripId == to.tripId && it.sortIndex < to.sortIndex
                }.maxByOrNull { it.sortIndex } ?: return@forEach
                val inserted = segmentModeDao.insertIfMissing(
                    RouteSegmentModeEntity(
                        id = segmentKey(from.id, to.id),
                        tripId = to.tripId,
                        fromStopId = from.id,
                        toStopId = to.id,
                        mode = mode,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                if (inserted != -1L) added("segmentModes")
            }
        }

        val keptTotal = kept.values.sum()
        return "Import fertig: ${count["trips"] ?: 0} Reisen, ${count["stops"] ?: 0} Pins, " +
            "${count["entries"] ?: 0} Einträge, ${count["photos"] ?: 0} Fotos neu oder " +
            "aktualisiert. " +
            (if (keptTotal > 0) "$keptTotal Elemente waren auf dem Handy schon aktuell und " +
                "blieben unverändert. " else "") +
            "Nichts wurde gelöscht."
    }

    private fun isSafeFileName(fileName: String): Boolean =
        fileName.isNotBlank() && !fileName.contains('/') && !fileName.contains('\\') &&
            fileName != "." && fileName != ".."

    /**
     * Kopiert die Datenbank vor einem Import in den App-Ordner `snapshots/`.
     * Vorher werden offene Änderungen aus der WAL-Datei in die Hauptdatei
     * geschrieben, damit die Kopie vollständig ist.
     */
    fun snapshot(): File? = try {
        val db = context.getDatabasePath(DB_NAME)
        if (db.exists()) {
            try {
                database.openHelper.writableDatabase
                    .query("PRAGMA wal_checkpoint(FULL)").close()
            } catch (_: Exception) {
                // Dann werden WAL und SHM einfach mitkopiert.
            }
            val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
            val target = File(dir, "vor-import-${System.currentTimeMillis()}")
            db.copyTo(File(target, DB_NAME), overwrite = true)
            listOf("-wal", "-shm").forEach { suffix ->
                val extra = File(db.parentFile, DB_NAME + suffix)
                if (extra.exists()) {
                    extra.copyTo(File(target, DB_NAME + suffix), overwrite = true)
                }
            }
            target
        } else {
            null
        }
    } catch (_: Exception) {
        null
    }

    private suspend fun JSONArray?.forEachRow(block: suspend (JSONObject) -> Unit) {
        if (this == null) return
        for (index in 0 until length()) {
            try {
                block(getJSONObject(index))
            } catch (_: Exception) {
                // Toleranter Import: eine beschädigte Zeile darf den Rest nicht stoppen.
            }
        }
    }
}
