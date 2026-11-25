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

    return stops.sortedWith(
        compareBy<Stop> { timeOf(it) }.thenBy { it.sortIndex },
    )
}

/** Eindeutiger Schlüssel eines Abschnitts: Startpin und Zielpin. */
fun segmentKey(fromStopId: String, toStopId: String): String =
    "$fromStopId>$toStopId"
