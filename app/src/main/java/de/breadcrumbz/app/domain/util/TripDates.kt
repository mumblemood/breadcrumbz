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
    // Nullbare Sets, damit auch Einträge/Fotos ohne Pin sicher geprüft werden.
    val excludedStops: Set<String?> = stops.filter { it.isArrivalDeparture }
        .map { it.id }.toSet()
    val excludedEntries: Set<String?> = entries.filter { it.stopId in excludedStops }
        .map { it.id }.toSet()
    val times = mutableListOf<Long>()
    stops.filterNot { it.isArrivalDeparture }
        .mapNotNull { it.visitedAt }
        .let(times::addAll)
    entries.filter { it.stopId !in excludedStops }
        .mapNotNull { it.entryAt }
        .let(times::addAll)
    photos.filter { it.stopId !in excludedStops && it.entryId !in excludedEntries }
        .mapNotNull { it.capturedAt }
        .let(times::addAll)
    val sorted = times.sorted()
    return sorted.firstOrNull() to sorted.lastOrNull()
}
