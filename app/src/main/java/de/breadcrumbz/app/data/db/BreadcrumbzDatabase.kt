package de.breadcrumbz.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import de.breadcrumbz.app.data.model.EntryEntity
import de.breadcrumbz.app.data.model.PhotoEntity
import de.breadcrumbz.app.data.model.PlaceNameEntity
import de.breadcrumbz.app.data.model.RouteSegmentModeEntity
import de.breadcrumbz.app.data.model.RouteWaypointEntity
import de.breadcrumbz.app.domain.util.segmentKey
import de.breadcrumbz.app.data.model.StopEntity
import de.breadcrumbz.app.data.model.TripEntity
import java.util.UUID

@Database(
    entities = [
        TripEntity::class,
        StopEntity::class,
        EntryEntity::class,
        PhotoEntity::class,
        PlaceNameEntity::class,
        RouteWaypointEntity::class,
        RouteSegmentModeEntity::class,
    ],
    version = 14,
    exportSchema = true,
)
abstract class BreadcrumbzDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun stopDao(): StopDao
    abstract fun entryDao(): EntryDao
    abstract fun photoDao(): PhotoDao
    abstract fun placeNameDao(): PlaceNameDao
    abstract fun waypointDao(): WaypointDao
    abstract fun segmentModeDao(): SegmentModeDao
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

/** Version 3: Standard-Verkehrsmittel und Linienmodus kommen zur Reise hinzu. */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `trips` ADD COLUMN `defaultTransportMode` TEXT NOT NULL DEFAULT 'CAR'",
        )
        db.execSQL(
            "ALTER TABLE `trips` ADD COLUMN `routeMode` TEXT NOT NULL DEFAULT 'AUTO'",
        )
    }
}

/** Version 4: Fotos können an einem Tagebucheintrag hängen. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `photos` ADD COLUMN `entryId` TEXT")
    }
}

/** Version 5: Pins können einem Eintrag zugeordnet sein. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stops` ADD COLUMN `entryId` TEXT")
    }
}

/** Version 6: Jeder Abschnitt kann ein eigenes Verkehrsmittel haben. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `stops` ADD COLUMN `transportMode` TEXT")
    }
}

/**
 * Version 7: Jedes Foto gehört zu einem Eintrag. Alte Einzelbilder bekommen
 * je einen eigenen Eintrag mit ihrer Zeit und ihrem Ort. Dabei wird nichts
 * gelöscht oder überschrieben.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cursor = db.query(
            "SELECT `id`, `tripId`, `stopId`, `caption`, `capturedAt`, `createdAt`, `sortIndex` " +
                "FROM `photos` WHERE `entryId` IS NULL",
        )
        val pending = mutableListOf<Array<Any?>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val tripId = it.getString(1)
                val stopId = if (it.isNull(2)) null else it.getString(2)
                val caption = it.getString(3).orEmpty()
                val capturedAt = if (it.isNull(4)) it.getLong(5) else it.getLong(4)
                val sortIndex = it.getInt(6)
                pending.add(
                    arrayOf(
                        UUID.randomUUID().toString(),
                        tripId,
                        stopId,
                        caption.take(12).ifBlank { "Foto" },
                        capturedAt,
                        sortIndex,
                        id,
                    ),
                )
            }
        }
        pending.forEach { row ->
            db.execSQL(
                "INSERT INTO `entries` " +
                    "(`id`, `tripId`, `stopId`, `title`, `body`, `sortIndex`, `createdAt`, `updatedAt`) " +
                    "VALUES (?, ?, ?, ?, '', ?, ?, ?)",
                arrayOf(row[0], row[1], row[2], row[3], row[5], row[4], row[4]),
            )
            db.execSQL(
                "UPDATE `photos` SET `entryId` = ? WHERE `id` = ?",
                arrayOf(row[0], row[6]),
            )
        }
    }
}

/**
 * Version 8: Merkt sich, ob der Reisetitel noch automatisch ist („neue Reise“).
 * Ein selbst vergebener Titel wird nicht mehr automatisch überschrieben.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `trips` ADD COLUMN `titleIsAuto` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "UPDATE `trips` SET `titleIsAuto` = 1 " +
                "WHERE TRIM(`title`) = '' OR LOWER(TRIM(`title`)) = 'neue reise'",
        )
    }
}

/**
 * Version 9: Einträge bekommen eine eigene, bearbeitbare Zeit. Sie bleibt leer,
 * damit das erste hinzugefügte Foto Zeit und Ort nur in leere Felder schreiben
 * kann. Bestehende Einträge behalten ihre bisherigen Daten unverändert.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `entries` ADD COLUMN `entryAt` INTEGER")
    }
}

/**
 * Version 10: Merkt sich, ob Zeit und Ort aus dem ersten Foto stammen. Dann
 * stehen sie im Detailfenster kursiv mit „(aus erstem Foto)". Alte Einträge
 * behalten ihre Werte; die neuen Felder starten auf 0.
 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `entries` ADD COLUMN `timeFromPhoto` INTEGER NOT NULL DEFAULT 0",
        )
        db.execSQL(
            "ALTER TABLE `entries` ADD COLUMN `placeFromPhoto` INTEGER NOT NULL DEFAULT 0",
        )
    }
}


/**
 * Version 11: Selbst gezogene Punkte auf der Reiselinie. Bestehende Reisen
 * bleiben unverändert; die neue Tabelle startet leer.
 */
val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `route_waypoints` (
                `id` TEXT NOT NULL,
                `tripId` TEXT NOT NULL,
                `afterStopId` TEXT,
                `latitude` REAL NOT NULL,
                `longitude` REAL NOT NULL,
                `sortIndex` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_route_waypoints_tripId` " +
                "ON `route_waypoints` (`tripId`)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_route_waypoints_tripId_sortIndex` " +
                "ON `route_waypoints` (`tripId`, `sortIndex`)",
        )
    }
}

/**
 * Version 12: Kennzeichnet An- und Abreise. Alle vorhandenen Pins bleiben
 * ganz normal in Datum und Kilometern; nur neue Häkchen nehmen sie heraus.
 */
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `stops` ADD COLUMN `isArrivalDeparture` " +
                "INTEGER NOT NULL DEFAULT 0",
        )
    }
}

/**
 * Version 13: Das Verkehrsmittel gehört nicht mehr zum Pin, sondern zum
 * Abschnitt davor. Vorhandene Wahlwerte werden in die neue Tabelle kopiert;
 * alle Pins, Einträge, Fotos und Zeiten bleiben unverändert erhalten.
 */
val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Room führt Migrationen in einer Transaktion aus; dort lässt sich
        // foreign_keys nicht mehr abschalten. defer_foreign_keys genügt:
        // nach dem Umbenennen zeigen alle Kindtabellen wieder auf `stops`.
        db.execSQL("PRAGMA defer_foreign_keys=ON")
        try {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `route_segment_modes` (
                    `id` TEXT NOT NULL,
                    `tripId` TEXT NOT NULL,
                    `fromStopId` TEXT NOT NULL,
                    `toStopId` TEXT NOT NULL,
                    `mode` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_route_segment_modes_tripId` " +
                    "ON `route_segment_modes` (`tripId`)",
            )

            // Die alten Werte saßen am Ankunftspin. Reihenfolge und Pin-Paare
            // bleiben beim Umzug exakt erhalten.
            val cursor = db.query(
                "SELECT `id`, `tripId`, `transportMode` FROM `stops` " +
                    "ORDER BY `tripId`, `sortIndex` ASC",
            )
            val now = System.currentTimeMillis()
            cursor.use { rows ->
                var currentTrip: String? = null
                var previousStop: String? = null
                while (rows.moveToNext()) {
                    val id = rows.getString(0)
                    val tripId = rows.getString(1)
                    val mode = if (rows.isNull(2)) null else rows.getString(2)
                    if (tripId != currentTrip) {
                        currentTrip = tripId
                        previousStop = null
                    }
                    val from = previousStop
                    if (from != null && !mode.isNullOrBlank()) {
                        db.execSQL(
                            "INSERT OR REPLACE INTO `route_segment_modes` " +
                                "(`id`, `tripId`, `fromStopId`, `toStopId`, `mode`, " +
                                "`createdAt`, `updatedAt`) VALUES (?, ?, ?, ?, ?, ?, ?)",
                            arrayOf(segmentKey(from, id), tripId, from, id, mode, now, now),
                        )
                    }
                    previousStop = id
                }
            }

            // Die Spalte verschwindet aus dem Pin. Alle anderen Felder werden
            // eins zu eins in die neue Tabelle übernommen.
            db.execSQL(
                """
                CREATE TABLE `stops_new` (
                    `id` TEXT NOT NULL,
                    `tripId` TEXT NOT NULL,
                    `entryId` TEXT,
                    `isArrivalDeparture` INTEGER NOT NULL DEFAULT 0,
                    `title` TEXT NOT NULL,
                    `latitude` REAL NOT NULL,
                    `longitude` REAL NOT NULL,
                    `sortIndex` INTEGER NOT NULL,
                    `visitedAt` INTEGER,
                    `source` TEXT NOT NULL,
                    `distanceFromPreviousMeters` REAL,
                    `distanceSource` TEXT,
                    `distanceUpdatedAt` INTEGER,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`tripId`) REFERENCES `trips`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO `stops_new` (
                    `id`, `tripId`, `entryId`, `isArrivalDeparture`, `title`,
                    `latitude`, `longitude`, `sortIndex`, `visitedAt`, `source`,
                    `distanceFromPreviousMeters`, `distanceSource`,
                    `distanceUpdatedAt`, `createdAt`, `updatedAt`
                )
                SELECT `id`, `tripId`, `entryId`, `isArrivalDeparture`, `title`,
                    `latitude`, `longitude`, `sortIndex`, `visitedAt`, `source`,
                    `distanceFromPreviousMeters`, `distanceSource`,
                    `distanceUpdatedAt`, `createdAt`, `updatedAt`
                FROM `stops`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `stops`")
            db.execSQL("ALTER TABLE `stops_new` RENAME TO `stops`")
            db.execSQL("CREATE INDEX `index_stops_tripId` ON `stops` (`tripId`)")
            db.execSQL(
                "CREATE INDEX `index_stops_tripId_sortIndex` " +
                    "ON `stops` (`tripId`, `sortIndex`)",
            )
        } finally {
            // defer_foreign_keys gilt nur für diese Transaktion und wird beim
            // Commit automatisch zurückgesetzt.
        }
    }
}

/**
 * v14: Empfangene Reisen werden markiert. Nur neue Spalten mit Standardwert –
 * alle vorhandenen Reisen bleiben unverändert und gelten als eigene Reisen.
 */
val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `isReceived` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `receivedFrom` TEXT")
        db.execSQL("ALTER TABLE `trips` ADD COLUMN `receivedAt` INTEGER")
    }
}
