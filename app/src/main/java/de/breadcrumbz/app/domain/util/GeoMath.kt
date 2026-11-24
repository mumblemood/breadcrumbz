package de.breadcrumbz.app.domain.util

import de.breadcrumbz.app.domain.model.Photo
import kotlin.math.abs

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val CLUSTER_TIME_MS = 30 * 60 * 1_000L
private const val CLUSTER_DISTANCE_METERS = 200.0

/** Entfernung zwischen zwei GPS-Punkten in Metern (Luftlinie). */
fun haversineMeters(
    latitude1: Double,
    longitude1: Double,
    latitude2: Double,
    longitude2: Double,
): Double {
    val deltaLatitude = Math.toRadians(latitude2 - latitude1)
    val deltaLongitude = Math.toRadians(longitude2 - longitude1)
    val a = kotlin.math.sin(deltaLatitude / 2) * kotlin.math.sin(deltaLatitude / 2) +
        kotlin.math.cos(Math.toRadians(latitude1)) * kotlin.math.cos(Math.toRadians(latitude2)) *
        kotlin.math.sin(deltaLongitude / 2) * kotlin.math.sin(deltaLongitude / 2)
    return 2 * EARTH_RADIUS_METERS * kotlin.math.atan2(
        kotlin.math.sqrt(a),
        kotlin.math.sqrt(1 - a),
    )
}

/**
 * Fasst Fotos zusammen, die zeitlich und örtlich nah beieinander liegen.
 * Fotos ohne Zeit oder ohne GPS werden einzeln betrachtet.
 */
fun clusterPhotos(photos: List<Photo>): List<List<Photo>> {
    val sorted = photos.sortedBy { it.capturedAt ?: Long.MAX_VALUE }
    val clusters = mutableListOf<MutableList<Photo>>()

    sorted.forEach { photo ->
        val latitude = photo.latitude
        val longitude = photo.longitude
        if (latitude == null || longitude == null) return@forEach

        val current = clusters.lastOrNull()
        val anchor = current?.firstOrNull()
        val fits = if (current != null && anchor != null &&
            anchor.latitude != null && anchor.longitude != null &&
            photo.capturedAt != null && anchor.capturedAt != null
        ) {
            abs(photo.capturedAt - anchor.capturedAt) <= CLUSTER_TIME_MS &&
                haversineMeters(
                    anchor.latitude,
                    anchor.longitude,
                    latitude,
                    longitude,
                ) <= CLUSTER_DISTANCE_METERS
        } else {
            false
        }

        if (fits && current != null) {
            current.add(photo)
        } else {
            clusters.add(mutableListOf(photo))
        }
    }
    return clusters
}
