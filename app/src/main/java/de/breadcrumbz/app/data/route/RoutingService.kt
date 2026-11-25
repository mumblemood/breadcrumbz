package de.breadcrumbz.app.data.route

import de.breadcrumbz.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.util.GeoPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Holt die Straßenlinie von OSRM (OpenStreetMap Routing). Nur Koordinaten
 * werden gesendet; es gibt kein Konto und keinen Nutzer-Namen.
 *
 * Fällt der Dienst aus, bleibt die bisherige, lokal gezeichnete Linie sichtbar.
 */
class RoutingService {
    private val cache = object : LinkedHashMap<String, List<GeoPoint>>(48, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<GeoPoint>>?,
        ): Boolean = size > 48
    }

    /**
     * @param points Pins und selbst gesetzte Zwischenpunkte in Fahrtrichtung.
     * @return Straßenpunkte oder null, wenn keine Verbindung da ist.
     */
    suspend fun route(points: List<GeoPoint>): List<GeoPoint>? = withContext(Dispatchers.IO) {
        if (points.size < 2) return@withContext null
        val key = points.joinToString(";") {
            String.format(java.util.Locale.US, "%.5f,%.5f", it.longitude, it.latitude)
        }
        cache[key]?.let { return@withContext it }

        val address = "https://router.project-osrm.org/route/v1/driving/" +
            URLEncoder.encode(key, "UTF-8") +
            "?overview=full&geometries=geojson&alternatives=false&steps=false"
        try {
            val connection = URL(address).openConnection() as HttpURLConnection
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            connection.setRequestProperty(
                "User-Agent", "Breadcrumbz/${BuildConfig.VERSION_NAME} (+https://github.com/mumblemood/breadcrumbz)",
            )
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val routes = JSONObject(body).optJSONArray("routes")
            val coordinates = routes?.optJSONObject(0)
                ?.optJSONObject("geometry")?.optJSONArray("coordinates")
                ?: return@withContext null
            val result = buildList {
                for (index in 0 until coordinates.length()) {
                    val pair = coordinates.optJSONArray(index) ?: continue
                    if (pair.length() < 2) continue
                    add(GeoPoint(pair.optDouble(1), pair.optDouble(0)))
                }
            }
            if (result.size < 2) return@withContext null
            cache[key] = result
            result
        } catch (_: Exception) {
            // Offline oder Dienst nicht erreichbar: alte Zeichnung bleibt.
            null
        }
    }
}
