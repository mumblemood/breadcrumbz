package de.breadcrumbz.app.data.place

import de.breadcrumbz.app.data.db.StopDao
import de.breadcrumbz.app.data.db.TripDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Frischt gespeicherte Pinnamen einmalig aus den Koordinaten auf.
 * Ergebnis ist immer das einheitliche Format „Stadt, Staat".
 */
class PlaceNameRefresher(
    private val stopDao: StopDao,
    private val tripDao: TripDao,
    private val resolver: PlaceNameResolver,
) {
    suspend fun refreshAll(force: Boolean = true): Int = withContext(Dispatchers.IO) {
        var changed = 0
        // Empfangene Reisen bleiben genau so, wie der Absender sie geschickt hat.
        val received = tripDao.receivedIds().toSet()
        stopDao.observeAll().first().filterNot { it.tripId in received }.forEach { stop ->
            val name = resolver.resolve(stop.latitude, stop.longitude, force)
            if (!name.isNullOrBlank() && name != stop.title) {
                stopDao.update(stop.copy(title = name, updatedAt = System.currentTimeMillis()))
                changed++
            }
        }
        changed
    }
}
