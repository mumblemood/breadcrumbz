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

/** Zeitlicher und örtlicher Abstand, der für eine Foto-Gruppe gilt. */
object PhotoGroupRule {
    const val TIME_MS = CLUSTER_TIME_MS
    const val DISTANCE_METERS = CLUSTER_DISTANCE_METERS
}

/** Passt ein Foto zu einem bereits vorhandenen Foto-Stopp? */
fun photoMatchesStop(
    photoLatitude: Double?,
    photoLongitude: Double?,
    photoTime: Long?,
    stopLatitude: Double,
    stopLongitude: Double,
    stopTime: Long?,
): Boolean {
    if (photoLatitude == null || photoLongitude == null || photoTime == null || stopTime == null) {
        return false
    }
    return abs(photoTime - stopTime) <= CLUSTER_TIME_MS &&
        haversineMeters(photoLatitude, photoLongitude, stopLatitude, stopLongitude) <=
        CLUSTER_DISTANCE_METERS
}

/**
 * Fasst Fotos zusammen, die zeitlich und örtlich nah beieinander liegen.
 * Es wird gegen alle bestehenden Gruppen geprüft – nicht nur gegen die letzte.
 */
fun clusterPhotos(photos: List<Photo>): List<List<Photo>> {
    val sorted = photos.sortedBy { it.capturedAt ?: Long.MAX_VALUE }
    val clusters = mutableListOf<MutableList<Photo>>()

    sorted.forEach { photo ->
        val latitude = photo.latitude
        val longitude = photo.longitude
        if (latitude == null || longitude == null) return@forEach

        val target = clusters.firstOrNull { cluster ->
            val anchor = cluster.first()
            photoMatchesStop(
                photoLatitude = latitude,
                photoLongitude = longitude,
                photoTime = photo.capturedAt,
                stopLatitude = anchor.latitude ?: return@firstOrNull false,
                stopLongitude = anchor.longitude ?: return@firstOrNull false,
                stopTime = anchor.capturedAt,
            )
        }

        if (target != null) {
            target.add(photo)
        } else {
            clusters.add(mutableListOf(photo))
        }
    }
    return clusters
}
