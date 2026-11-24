package de.breadcrumbz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity

@Database(
    entities = [TripEntity::class, StopEntity::class, EntryEntity::class, PhotoEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class BreadcrumbzDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun entryDao(): EntryDao
    abstract fun photoDao(): PhotoDao
}
