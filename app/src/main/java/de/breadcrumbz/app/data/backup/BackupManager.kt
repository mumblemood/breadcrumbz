package de.breadcrumbz.app.data.backup

import android.content.Context
import android.net.Uri
import de.breadcrumbz.app.data.db.EntryDao
import de.breadcrumbz.app.data.db.PhotoDao
import de.breadcrumbz.app.data.db.PlaceNameDao
import de.breadcrumbz.app.data.db.StopDao
import de.breadcrumbz.app.data.db.TripDao
import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.PlaceNameEntity
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity
import de.breadcrumbz.app.data.photo.InternalPhotoStore
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
class BackupManager(
    private val context: Context,
    private val tripDao: TripDao,
    private val stopDao: StopDao,
    private val entryDao: EntryDao,
    private val photoDao: PhotoDao,
    private val placeNameDao: PlaceNameDao,
    private val photoStore: InternalPhotoStore,
    private val appVersion: String,
) {
    suspend fun exportTo(uri: Uri): String = withContext(Dispatchers.IO) {
        val trips = tripDao.observeAll().first()
        val stops = stopDao.observeAll().first()
        val entries = entryDao.observeAll().first()
        val photos = photoDao.observeAll().first()
        val places = placeNameDao.observeAll().first()

        val data = JSONObject().apply {
            put("format", "breadcrumbz-backup")
            put("version", 1)
            put("trips", JSONArray(trips.map { it.toJson() }))
            put("stops", JSONArray(stops.map { it.toJson() }))
            put("entries", JSONArray(entries.map { it.toJson() }))
            put("photos", JSONArray(photos.map { it.toJson() }))
            put("placeNames", JSONArray(places.map { it.toJson() }))
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
            "${entries.size} Einträge, $writtenImages Bilder."
    }

    suspend fun importFrom(uri: Uri): String = withContext(Dispatchers.IO) {
        var content = ""
        val images = mutableMapOf<String, ByteArray>()
        val input = context.contentResolver.openInputStream(uri)
            ?: return@withContext "Import fehlgeschlagen: Datei nicht lesbar."
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry: ZipEntry?
                while (zip.nextEntry.also { entry = it } != null) {
                    when (val name = entry?.name.orEmpty()) {
                        "reise_daten.json" -> content = zip.readBytes().toString(Charsets.UTF_8)
                        "manifest.json" -> Unit
                        else -> if (name.startsWith("bilder/")) {
                            val fileName = name.removePrefix("bilder/")
                            if (fileName.isNotBlank() && !fileName.contains('/') &&
                                !fileName.contains('\\')
                            ) {
                                images[fileName] = zip.readBytes()
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }
        if (content.isBlank()) {
            return@withContext "Keine reise_daten.json in der ZIP-Datei gefunden."
        }

        val root = try {
            JSONObject(content)
        } catch (_: Exception) {
            return@withContext "Die Datei ist kein gültiges Breadcrumbz-Backup."
        }
        snapshot()

        var trips = 0
        var stops = 0
        var entries = 0
        var photos = 0
        var places = 0
        root.optJSONArray("trips").forEachRow { obj ->
            tripDao.upsert(obj.toTrip()); trips++
        }
        root.optJSONArray("stops").forEachRow { obj ->
            stopDao.upsert(obj.toStop()); stops++
        }
        root.optJSONArray("entries").forEachRow { obj ->
            entryDao.upsert(obj.toEntry()); entries++
        }
        root.optJSONArray("photos").forEachRow { obj ->
            val photo = obj.toPhoto()
            photoDao.upsert(photo)
            images[photo.fileName]?.let { photoStore.write(photo.fileName, it) }
            photos++
        }
        root.optJSONArray("placeNames").forEachRow { obj ->
            placeNameDao.upsert(obj.toPlace()); places++
        }
        "Import fertig: $trips Reisen, $stops Pins, $entries Einträge, " +
            "$photos Fotos, $places Ortsnamen. Alter Stand liegt im Snapshot."
    }

    /** Kopiert die Datenbank vor einem Import – nichts wird endgültig überschrieben. */
    fun snapshot(): File? = try {
        val dir = File(context.filesDir, "snapshots").apply { mkdirs() }
        val target = File(dir, "vor-import-${System.currentTimeMillis()}")
        val db = File(context.filesDir, "databases/breadcrumbz.db")
        if (db.exists()) {
            db.copyTo(File(target, "breadcrumbz.db"), overwrite = true)
            listOf("breadcrumbz.db-wal", "breadcrumbz.db-shm").forEach { suffix ->
                val extra = File(context.filesDir, "databases/$suffix")
                if (extra.exists()) {
                    extra.copyTo(File(target, suffix), overwrite = true)
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

    private fun JSONObject.longOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.doubleOrNull(key: String): Double? =
        if (has(key) && !isNull(key)) optDouble(key) else null

    private fun JSONObject.putNullable(key: String, value: Long?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun JSONObject.putNullable(key: String, value: Double?) {
        put(key, value ?: JSONObject.NULL)
    }

    private fun TripEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("titleIsAuto", titleIsAuto)
        put("description", description)
        putNullable("startDate", startDate)
        putNullable("endDate", endDate)
        put("coverPhotoId", coverPhotoId ?: JSONObject.NULL)
        put("overlayMode", overlayMode)
        put("defaultTransportMode", defaultTransportMode)
        put("routeMode", routeMode)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toTrip() = TripEntity(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        title = optString("title", "Neue Reise"),
        titleIsAuto = optBoolean("titleIsAuto", false),
        description = optString("description", ""),
        startDate = longOrNull("startDate"),
        endDate = longOrNull("endDate"),
        coverPhotoId = if (isNull("coverPhotoId")) null else optString("coverPhotoId"),
        overlayMode = optString("overlayMode", "STANDARD"),
        defaultTransportMode = optString("defaultTransportMode", "CAR"),
        routeMode = optString("routeMode", "AUTO"),
        createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
        updatedAt = longOrNull("updatedAt") ?: System.currentTimeMillis(),
    )

    private fun StopEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("tripId", tripId)
        put("entryId", entryId ?: JSONObject.NULL)
        put("transportMode", transportMode ?: JSONObject.NULL)
        put("title", title)
        put("latitude", latitude)
        put("longitude", longitude)
        put("sortIndex", sortIndex)
        putNullable("visitedAt", visitedAt)
        put("source", source)
        putNullable("distanceFromPreviousMeters", distanceFromPreviousMeters)
        put("distanceSource", distanceSource ?: JSONObject.NULL)
        putNullable("distanceUpdatedAt", distanceUpdatedAt)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    private fun JSONObject.toStop() = StopEntity(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        tripId = optString("tripId"),
        entryId = if (isNull("entryId")) null else optString("entryId"),
        transportMode = if (isNull("transportMode")) null else optString("transportMode"),
        title = optString("title", "Wegpunkt"),
        latitude = doubleOrNull("latitude") ?: 0.0,
        longitude = doubleOrNull("longitude") ?: 0.0,
        sortIndex = optInt("sortIndex", 0),
        visitedAt = longOrNull("visitedAt"),
        source = optString("source", "MANUAL"),
        distanceFromPreviousMeters = doubleOrNull("distanceFromPreviousMeters"),
        distanceSource = if (isNull("distanceSource")) null else optString("distanceSource"),
        distanceUpdatedAt = longOrNull("distanceUpdatedAt"),
        createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
        updatedAt = longOrNull("updatedAt") ?: System.currentTimeMillis(),
    )

    private fun EntryEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("tripId", tripId)
        put("stopId", stopId ?: JSONObject.NULL)
        putNullable("entryAt", entryAt)
        put("title", title)
        put("body", body)
        put("sortIndex", sortIndex)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("timeFromPhoto", timeFromPhoto)
        put("placeFromPhoto", placeFromPhoto)
    }

    private fun JSONObject.toEntry() = EntryEntity(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        tripId = optString("tripId"),
        stopId = if (isNull("stopId")) null else optString("stopId"),
        entryAt = longOrNull("entryAt"),
        title = optString("title", ""),
        body = optString("body", ""),
        sortIndex = optInt("sortIndex", 0),
        createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
        updatedAt = longOrNull("updatedAt") ?: System.currentTimeMillis(),
        timeFromPhoto = optBoolean("timeFromPhoto", false),
        placeFromPhoto = optBoolean("placeFromPhoto", false),
    )

    private fun PhotoEntity.toJson() = JSONObject().apply {
        put("id", id)
        put("tripId", tripId)
        put("stopId", stopId ?: JSONObject.NULL)
        put("entryId", entryId ?: JSONObject.NULL)
        put("fileName", fileName)
        put("mimeType", mimeType)
        putNullable("capturedAt", capturedAt)
        putNullable("latitude", latitude)
        putNullable("longitude", longitude)
        put("caption", caption)
        put("sortIndex", sortIndex)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toPhoto() = PhotoEntity(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        tripId = optString("tripId"),
        stopId = if (isNull("stopId")) null else optString("stopId"),
        entryId = if (isNull("entryId")) null else optString("entryId"),
        fileName = optString("fileName").ifBlank { UUID.randomUUID().toString() + ".jpg" },
        mimeType = optString("mimeType", "image/jpeg"),
        capturedAt = longOrNull("capturedAt"),
        latitude = doubleOrNull("latitude"),
        longitude = doubleOrNull("longitude"),
        caption = optString("caption", ""),
        sortIndex = optInt("sortIndex", 0),
        createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
    )

    private fun PlaceNameEntity.toJson() = JSONObject().apply {
        put("key", key)
        put("name", name)
        put("createdAt", createdAt)
    }

    private fun JSONObject.toPlace() = PlaceNameEntity(
        key = optString("key"),
        name = optString("name"),
        createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
    )
}
