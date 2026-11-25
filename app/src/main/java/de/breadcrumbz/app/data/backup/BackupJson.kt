package de.breadcrumbz.app.data.backup

import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.PlaceNameEntity
import de.breadcrumbz.app.data.model.RouteSegmentModeEntity
import de.breadcrumbz.app.data.model.RouteWaypointEntity
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity
import org.json.JSONObject
import java.util.UUID

/*
 * JSON-Form der Datenbankzeilen. Wird vom Backup und vom Verschicken einzelner
 * Reisen gemeinsam benutzt, damit beide immer dasselbe Format schreiben.
 */

internal fun JSONObject.longOrNull(key: String): Long? =
    if (has(key) && !isNull(key)) optLong(key) else null

internal fun JSONObject.doubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key) else null

internal fun JSONObject.putNullable(key: String, value: Long?) {
    put(key, value ?: JSONObject.NULL)
}

internal fun JSONObject.putNullable(key: String, value: Double?) {
    put(key, value ?: JSONObject.NULL)
}

internal fun TripEntity.toJson() = JSONObject().apply {
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

internal fun JSONObject.toTrip() = TripEntity(
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
    // Fehlt der Zeitstempel (alte Backups), gewinnt beim Import der Stand auf dem Handy.
    updatedAt = longOrNull("updatedAt") ?: longOrNull("createdAt") ?: 0L,
)

internal fun StopEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("tripId", tripId)
    put("entryId", entryId ?: JSONObject.NULL)
    put("isArrivalDeparture", isArrivalDeparture)
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

internal fun JSONObject.toStop() = StopEntity(
    id = optString("id").ifBlank { UUID.randomUUID().toString() },
    tripId = optString("tripId"),
    entryId = if (isNull("entryId")) null else optString("entryId"),
    // Alte Backups ohne Feld behalten ihre bisherige Gesamtansicht.
    isArrivalDeparture = optBoolean("isArrivalDeparture", false),
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
    // Fehlt der Zeitstempel (alte Backups), gewinnt beim Import der Stand auf dem Handy.
    updatedAt = longOrNull("updatedAt") ?: longOrNull("createdAt") ?: 0L,
)

internal fun EntryEntity.toJson() = JSONObject().apply {
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

internal fun JSONObject.toEntry() = EntryEntity(
    id = optString("id").ifBlank { UUID.randomUUID().toString() },
    tripId = optString("tripId"),
    stopId = if (isNull("stopId")) null else optString("stopId"),
    entryAt = longOrNull("entryAt"),
    title = optString("title", ""),
    body = optString("body", ""),
    sortIndex = optInt("sortIndex", 0),
    createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
    // Fehlt der Zeitstempel (alte Backups), gewinnt beim Import der Stand auf dem Handy.
    updatedAt = longOrNull("updatedAt") ?: longOrNull("createdAt") ?: 0L,
    timeFromPhoto = optBoolean("timeFromPhoto", false),
    placeFromPhoto = optBoolean("placeFromPhoto", false),
)

internal fun PhotoEntity.toJson() = JSONObject().apply {
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

internal fun JSONObject.toPhoto() = PhotoEntity(
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

internal fun PlaceNameEntity.toJson() = JSONObject().apply {
    put("key", key)
    put("name", name)
    put("createdAt", createdAt)
}

internal fun JSONObject.toPlace() = PlaceNameEntity(
    key = optString("key"),
    name = optString("name"),
    createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
)

internal fun RouteWaypointEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("tripId", tripId)
    put("afterStopId", afterStopId ?: JSONObject.NULL)
    put("latitude", latitude)
    put("longitude", longitude)
    put("sortIndex", sortIndex)
    put("createdAt", createdAt)
}

internal fun JSONObject.toWaypoint() = RouteWaypointEntity(
    id = optString("id").ifBlank { UUID.randomUUID().toString() },
    tripId = optString("tripId"),
    afterStopId = if (isNull("afterStopId")) null else optString("afterStopId"),
    latitude = doubleOrNull("latitude") ?: 0.0,
    longitude = doubleOrNull("longitude") ?: 0.0,
    sortIndex = optInt("sortIndex", 0),
    createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
)

internal fun RouteSegmentModeEntity.toJson() = JSONObject().apply {
    put("id", id)
    put("tripId", tripId)
    put("fromStopId", fromStopId)
    put("toStopId", toStopId)
    put("mode", mode)
    put("createdAt", createdAt)
    put("updatedAt", updatedAt)
}

internal fun JSONObject.toSegmentMode() = RouteSegmentModeEntity(
    id = optString("id").ifBlank { UUID.randomUUID().toString() },
    tripId = optString("tripId"),
    fromStopId = optString("fromStopId"),
    toStopId = optString("toStopId"),
    mode = optString("mode", "CAR"),
    createdAt = longOrNull("createdAt") ?: System.currentTimeMillis(),
    // Fehlt der Zeitstempel (alte Backups), gewinnt beim Import der Stand auf dem Handy.
    updatedAt = longOrNull("updatedAt") ?: longOrNull("createdAt") ?: 0L,
)
