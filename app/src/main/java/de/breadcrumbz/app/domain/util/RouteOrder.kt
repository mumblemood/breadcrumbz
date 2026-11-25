package de.breadcrumbz.app.domain.util

import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop

/**
 * Chronologische Reihenfolge der Pins. Manuell gesetzte Zeiten gehen vor;
 * danach folgen Fotozeit, Eintragszeit und schließlich die Anlegezeit.
 * Wird eine Zeit geändert, wandert der Pin in der Route sofort mit.
 */
fun chronologicalStops(
    stops: List<Stop>,
    entries: List<Entry>,
    photos: List<Photo>,
): List<Stop> {
    val times = stopTimes(stops, entries, photos)
    return stops.sortedWith(
        compareBy<Stop> { times[it.id] ?: it.createdAt }.thenBy { it.sortIndex },
    )
}

/** Die Zeit, nach der jeder Pin in der Route einsortiert wird. */
fun stopTimes(
    stops: List<Stop>,
    entries: List<Entry>,
    photos: List<Photo>,
): Map<String, Long> {
    val entryByStop = buildMap<String, Entry> {
        entries.forEach { entry ->
            entry.stopId?.let { putIfAbsent(it, entry) }
        }
        stops.forEach { stop ->
            stop.entryId?.let { id ->
                entries.firstOrNull { it.id == id }?.let { put(stop.id, it) }
            }
        }
    }
    val entryIdsByStop = entryByStop.mapValues { (_, entry) -> entry.id }

    fun timeOf(stop: Stop): Long {
        val entry = entryByStop[stop.id]
        val linkedEntryId = entryIdsByStop[stop.id]
        val photoTimes = photos.filter { photo ->
            photo.stopId == stop.id ||
                (linkedEntryId != null && photo.entryId == linkedEntryId)
        }.mapNotNull { it.capturedAt ?: it.createdAt }
        val manualEntryTime = entry?.takeIf { !it.timeFromPhoto }?.entryAt
        return stop.visitedAt
            ?: manualEntryTime
            ?: photoTimes.minOrNull()
            ?: entry?.entryAt
            ?: stop.createdAt
    }

    return stops.associate { it.id to timeOf(it) }
}

/**
 * Zeit für einen neu gesetzten Pin: Er kommt dorthin in die Route, wo er den
 * kleinsten Umweg macht, und bekommt eine Zeit zwischen seinen Nachbarn.
 * So sortiert er sich von selbst ein und die Linie läuft gleich richtig.
 * An- und Abreise bleiben außen.
 */
fun fittingTimeForNewStop(
    latitude: Double,
    longitude: Double,
    stops: List<Stop>,
    entries: List<Entry>,
    photos: List<Photo>,
    now: Long = System.currentTimeMillis(),
): Long {
    if (stops.isEmpty()) return now
    val ordered = chronologicalStops(stops, entries, photos)
    val times = stopTimes(stops, entries, photos)
    fun time(stop: Stop) = times[stop.id] ?: stop.createdAt
    fun distance(a: Stop) = haversineMeters(a.latitude, a.longitude, latitude, longitude)
    fun between(a: Stop, b: Stop) = haversineMeters(a.latitude, a.longitude, b.latitude, b.longitude)

    val first = ordered.first()
    val last = ordered.last()
    val hour = 60L * 60L * 1000L
    // Kandidaten: Position i = vor ordered[i]; ordered.size = ganz ans Ende.
    var bestPosition = ordered.size
    var bestCost = Double.MAX_VALUE
    for (position in 0..ordered.size) {
        if (position == 0 && (first.isArrivalDeparture && ordered.size > 1)) continue
        if (position == ordered.size && last.isArrivalDeparture && ordered.size > 1) continue
        val cost = when (position) {
            0 -> distance(first)
            ordered.size -> distance(last)
            else -> distance(ordered[position - 1]) + distance(ordered[position]) -
                between(ordered[position - 1], ordered[position])
        }
        if (cost < bestCost) {
            bestCost = cost
            bestPosition = position
        }
    }
    return when (bestPosition) {
        0 -> time(first) - hour
        ordered.size -> {
            val lastTime = time(last)
            // Unterwegs gesetzt: die echte Uhrzeit. Nachträglich: kurz nach dem letzten Pin.
            if (now > lastTime && now - lastTime < 3L * 24L * hour) now else lastTime + hour
        }
        else -> {
            val before = time(ordered[bestPosition - 1])
            val after = time(ordered[bestPosition])
            before + (after - before).coerceAtLeast(2L) / 2L
        }
    }
}

/** Eindeutiger Schlüssel eines Abschnitts: Startpin und Zielpin. */
fun segmentKey(fromStopId: String, toStopId: String): String =
    "$fromStopId>$toStopId"
