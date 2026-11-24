package de.breadcrumbz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.PlaceNameEntity
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity

@Database(
    entities = [
        TripEntity::class,
        StopEntity::class,
        EntryEntity::class,
        PhotoEntity::class,
        PlaceNameEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BreadcrumbzDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun entryDao(): EntryDao
    abstract fun photoDao(): PhotoDao
    abstract fun placeNameDao(): PlaceNameDao
}

/**
 * Version 2: Einträge gehören direkt zur Reise und dürfen ohne Stopp existieren.
 * Beim Löschen eines Pins bleibt der Text erhalten. Außerdem kommt ein Cache
 * für gefundene Ortsnamen hinzu.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE `entries_new` (
                `id` TEXT NOT NULL,
                `tripId` TEXT NOT NULL,
                `stopId` TEXT,
                `title` TEXT NOT NULL,
                `body` TEXT NOT NULL,
                `sortIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                FOREIGN KEY(`stopId`) REFERENCES `stops`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO `entries_new`
                (`id`, `tripId`, `stopId`, `title`, `body`, `sortIndex`, `createdAt`, `updatedAt`)
            SELECT e.`id`, s.`tripId`, e.`stopId`, e.`title`, e.`body`,
                   e.`sortIndex`, e.`createdAt`, e.`updatedAt`
            FROM `entries` e INNER JOIN `stops` s ON e.`stopId` = s.`id`
            """.trimIndent(),
        )
        db.execSQL("DROP TABLE `entries`")
        db.execSQL("ALTER TABLE `entries_new` RENAME TO `entries`")
        db.execSQL("CREATE INDEX `index_entries_tripId` ON `entries` (`tripId`)")
        db.execSQL("CREATE INDEX `index_entries_stopId` ON `entries` (`stopId`)")
        db.execSQL("CREATE INDEX `index_entries_tripId_sortIndex` ON `entries` (`tripId`, `sortIndex`)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `place_names` (
                `key` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`key`)
            )
            """.trimIndent(),
        )
    }
}
