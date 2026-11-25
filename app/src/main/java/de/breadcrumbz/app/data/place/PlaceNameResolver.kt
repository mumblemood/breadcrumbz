package de.breadcrumbz.app.data.place

import de.breadcrumbz.app.BuildConfig
import de.breadcrumbz.app.data.db.PlaceNameDao
import de.breadcrumbz.app.data.model.PlaceNameEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Ermittelt aus GPS-Koordinaten einen lesbaren Ortsnamen über Nominatim
 * (OpenStreetMap). Das Ergebnis wird lokal in der Datenbank gecacht, damit
 * dieselbe Stelle nicht zweimal abgefragt wird. Bei fehlendem Internet
 * liefert die Funktion null – die App benutzt dann „Wegpunkt n“.
 */
class PlaceNameResolver(private val dao: PlaceNameDao) {

    suspend fun resolve(
        latitude: Double,
        longitude: Double,
        force: Boolean = false,
    ): String? = withContext(Dispatchers.IO) {
        val key = String.format(Locale.US, "%.3f,%.3f", latitude, longitude)
        if (!force) dao.find(key)?.let { return@withContext it.name }

        val name = try {
            val url = URL(
                "https://nominatim.openstreetmap.org/reverse?format=jsonv2" +
                    "&accept-language=de&zoom=10&lat=$latitude&lon=$longitude",
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent",
                "Breadcrumbz/${BuildConfig.VERSION_NAME} (+https://github.com/mumblemood/breadcrumbz)",
            )
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            parseName(text)
        } catch (_: Exception) {
            null
        }

        if (!name.isNullOrBlank()) {
            dao.upsert(PlaceNameEntity(key, name, System.currentTimeMillis()))
        }
        name
    }

    private fun parseName(jsonText: String): String? {
        val json = JSONObject(jsonText)
        val address = json.optJSONObject("address")
        val city = listOf(
            address?.optString("city"),
            address?.optString("town"),
            address?.optString("village"),
            address?.optString("municipality"),
            address?.optString("suburb"),
        ).firstOrNull { !it.isNullOrBlank() }?.trim()
        val state = listOf(
            address?.optString("country"),
            address?.optString("state"),
        ).firstOrNull { !it.isNullOrBlank() }?.trim()

        return when {
            !city.isNullOrBlank() && !state.isNullOrBlank() -> "$city, $state"
            !city.isNullOrBlank() -> city
            else -> json.optString("display_name").orEmpty()
                .split(",").take(2).joinToString(",").trim().ifBlank { null }
        }
    }
}
