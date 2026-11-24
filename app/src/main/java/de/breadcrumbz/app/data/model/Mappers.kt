package de.breadcrumbz.app.data.model

import de.breadcrumbz.app.domain.model.DistanceSource
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.OverlayMode
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.StopSource
import de.breadcrumbz.app.domain.model.RouteMode
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.Trip

private inline fun <reified T : Enum<T>> enumValue(name: String?, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: fallback

fun TripEntity.toDomain() = Trip(
    id = id,
    title = title,
    description = description,
    startDate = startDate,
    endDate = endDate,
    coverPhotoId = coverPhotoId,
    overlayMode = enumValue(overlayMode, OverlayMode.STANDARD),
    defaultTransportMode = enumValue(defaultTransportMode, TransportMode.CAR),
    routeMode = enumValue(routeMode, RouteMode.AUTO),
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Trip.toEntity() = TripEntity(
    id = id,
    title = title,
    description = description,
    startDate = startDate,
    endDate = endDate,
    coverPhotoId = coverPhotoId,
    overlayMode = overlayMode.name,
    defaultTransportMode = defaultTransportMode.name,
    routeMode = routeMode.name,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun StopEntity.toDomain() = Stop(
    id = id,
    tripId = tripId,
    entryId = entryId,
    transportMode = transportMode?.let { enumValue<TransportMode>(it, TransportMode.CAR) },
    title = title,
    latitude = latitude,
    longitude = longitude,
    sortIndex = sortIndex,
    visitedAt = visitedAt,
    source = enumValue(source, StopSource.MANUAL),
    distanceFromPreviousMeters = distanceFromPreviousMeters,
    distanceSource = distanceSource?.let { enumValue<DistanceSource>(it, DistanceSource.STRAIGHT) },
    distanceUpdatedAt = distanceUpdatedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Stop.toEntity() = StopEntity(
    id = id,
    tripId = tripId,
    entryId = entryId,
    transportMode = transportMode?.name,
    title = title,
    latitude = latitude,
    longitude = longitude,
    sortIndex = sortIndex,
    visitedAt = visitedAt,
    source = source.name,
    distanceFromPreviousMeters = distanceFromPreviousMeters,
    distanceSource = distanceSource?.name,
    distanceUpdatedAt = distanceUpdatedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun EntryEntity.toDomain() = Entry(
    id = id,
    tripId = tripId,
    stopId = stopId,
    title = title,
    body = body,
    sortIndex = sortIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Entry.toEntity() = EntryEntity(
    id = id,
    tripId = tripId,
    stopId = stopId,
    title = title,
    body = body,
    sortIndex = sortIndex,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun PhotoEntity.toDomain() = Photo(
    id = id,
    tripId = tripId,
    stopId = stopId,
    entryId = entryId,
    fileName = fileName,
    mimeType = mimeType,
    capturedAt = capturedAt,
    latitude = latitude,
    longitude = longitude,
    caption = caption,
    sortIndex = sortIndex,
    createdAt = createdAt,
)

fun Photo.toEntity() = PhotoEntity(
    id = id,
    tripId = tripId,
    stopId = stopId,
    entryId = entryId,
    fileName = fileName,
    mimeType = mimeType,
    capturedAt = capturedAt,
    latitude = latitude,
    longitude = longitude,
    caption = caption,
    sortIndex = sortIndex,
    createdAt = createdAt,
)
