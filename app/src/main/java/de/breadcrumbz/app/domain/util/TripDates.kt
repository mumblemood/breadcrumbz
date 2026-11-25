package de.breadcrumbz.app.domain.util

import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop

/**
 * Zeitraum der eigentlichen Reise. Als An-/Abreise markierte Pins zählen nicht
 * mit – genauso wenig wie Einträge und Fotos, die an diesen Pins hängen.
 * Zurückgegeben werden (frühester, letzter) Zeitpunkt; beide dürfen null sein.
 */
fun tripTimeRange(
    stops: List<Stop>,
    entries: List<Entry>,
    photos: List<Photo>,
): Pair<Long?, Long?> {
    val excludedStops: Set<String> = stops.filter { it.isArrivalDeparture }
        .map { it.id }.toSet()
    val excludedStopEntries: Set<String> = stops.filter { it.isArrivalDeparture }
        .mapNotNull { it.entryId }.toSet()

    // Eintrag und Pin können in beide Richtungen verlinkt sein. Beide Fälle
    // müssen denselben Eintrag ausschließen, sonst bleibt eine Abreise im Datum.
    val excludedEntries: Set<String> = buildSet {
        entries.forEach { entry ->
            val excludedByStop = entry.stopId in excludedStops
            val excludedByPin = stops.any { stop ->
                stop.isArrivalDeparture && stop.entryId == entry.id
            }
            if (excludedByStop || excludedByPin) add(entry.id)
        }
        addAll(excludedStopEntries)
    }

    val times = mutableListOf<Long>()
    stops.filterNot { it.isArrivalDeparture }
        .mapNotNull { it.visitedAt }
        .let(times::addAll)
    entries.filterNot { it.id in excludedEntries }
        .mapNotNull { it.entryAt }
        .let(times::addAll)
    photos.filter { photo ->
        photo.stopId !in excludedStops && photo.entryId !in excludedEntries
    }.mapNotNull { it.capturedAt }
        .let(times::addAll)
    val sorted = times.sorted()
    return sorted.firstOrNull() to sorted.lastOrNull()
}
