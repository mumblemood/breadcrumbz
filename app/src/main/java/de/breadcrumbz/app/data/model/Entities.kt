package de.breadcrumbz.app.data.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val startDate: Long?,
    val endDate: Long?,
    val coverPhotoId: String?,
    val overlayMode: String,
    @ColumnInfo(defaultValue = "CAR")
    val defaultTransportMode: String,
    @ColumnInfo(defaultValue = "AUTO")
    val routeMode: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "stops",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tripId"), Index("tripId", "sortIndex")],
)
data class StopEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val sortIndex: Int,
    val visitedAt: Long?,
    val source: String,
    val distanceFromPreviousMeters: Double?,
    val distanceSource: String?,
    val distanceUpdatedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

/**
 * Einträge bleiben auch ohne Stopp erhalten. Deshalb gehören sie direkt zur
 * Reise; der Stopp ist nur eine optionale Verknüpfung.
 */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["id"],
            childColumns = ["stopId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tripId"), Index("stopId"), Index("tripId", "sortIndex")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val stopId: String?,
    val title: String,
    val body: String,
    val sortIndex: Int,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = TripEntity::class,
            parentColumns = ["id"],
            childColumns = ["tripId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = StopEntity::class,
            parentColumns = ["id"],
            childColumns = ["stopId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("tripId"), Index("stopId")],
)
data class PhotoEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val stopId: String?,
    val fileName: String,
    val mimeType: String,
    val capturedAt: Long?,
    val latitude: Double?,
    val longitude: Double?,
    val caption: String,
    val sortIndex: Int,
    val createdAt: Long,
)

/** Lokaler Cache für gefunden Ortsnamen, damit nicht bei jedem Stopp online gefragt wird. */
@Entity(tableName = "place_names")
data class PlaceNameEntity(
    @PrimaryKey val key: String,
    val name: String,
    val createdAt: Long,
)
