package de.breadcrumbz.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.PlaceNameEntity
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Query("SELECT * FROM trips ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips WHERE id = :id")
    fun observeById(id: String): Flow<TripEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface StopDao {
    @Query("SELECT * FROM stops WHERE tripId = :tripId ORDER BY sortIndex ASC")
    fun observeForTrip(tripId: String): Flow<List<StopEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stop: StopEntity)

    @Query("DELETE FROM stops WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM stops WHERE tripId = :tripId")
    suspend fun nextSortIndex(tripId: String): Int
}

@Dao
interface EntryDao {
    @Query("SELECT * FROM entries WHERE stopId = :stopId ORDER BY sortIndex ASC")
    fun observeForStop(stopId: String): Flow<List<EntryEntity>>

    /** Alle Einträge der Reise – auch die ohne Stopp. */
    @Query("SELECT * FROM entries WHERE tripId = :tripId ORDER BY createdAt ASC")
    fun observeForTrip(tripId: String): Flow<List<EntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM entries WHERE tripId = :tripId")
    suspend fun nextSortIndex(tripId: String): Int
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE tripId = :tripId ORDER BY sortIndex ASC")
    fun observeForTrip(tripId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos ORDER BY tripId, sortIndex ASC")
    fun observeAll(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun findById(id: String): PhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(photo: PhotoEntity)

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COALESCE(MAX(sortIndex), -1) + 1 FROM photos WHERE tripId = :tripId")
    suspend fun nextSortIndex(tripId: String): Int
}

@Dao
interface PlaceNameDao {
    @Query("SELECT * FROM place_names WHERE `key` = :key")
    suspend fun find(key: String): PlaceNameEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(place: PlaceNameEntity)
}
