package de.breadcrumbz.app

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import de.breadcrumbz.app.data.backup.BackupManager
import de.breadcrumbz.app.data.db.BreadcrumbzDatabase
import de.breadcrumbz.app.data.db.MIGRATION_1_2
import de.breadcrumbz.app.data.db.MIGRATION_2_3
import de.breadcrumbz.app.data.db.MIGRATION_3_4
import de.breadcrumbz.app.data.db.MIGRATION_4_5
import de.breadcrumbz.app.data.db.MIGRATION_5_6
import de.breadcrumbz.app.data.db.MIGRATION_6_7
import de.breadcrumbz.app.data.db.MIGRATION_7_8
import de.breadcrumbz.app.data.db.MIGRATION_8_9
import de.breadcrumbz.app.data.db.MIGRATION_9_10
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.place.PlaceNameRefresher
import de.breadcrumbz.app.data.place.PlaceNameResolver
import de.breadcrumbz.app.data.repository.RoomBreadcrumbzRepository
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import java.io.File

/** Zentraler Ort, an dem alle Bausteine zusammengesetzt werden. */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    init {
        // Karten werden lokal gecacht. Der Test-Schlüssel und die Kacheln
        // bleiben damit vollständig auf dem Gerät.
        val mapBase = File(appContext.filesDir, "osmdroid")
        val tileCache = File(mapBase, "tiles")
        mapBase.mkdirs()
        tileCache.mkdirs()
        Configuration.getInstance().apply {
            userAgentValue = "Breadcrumbz/0.5.0 (+https://github.com/mumblemood/breadcrumbz)"
            osmdroidBasePath = mapBase
            osmdroidTileCache = tileCache
        }
    }

    private val database = Room.databaseBuilder(
        appContext,
        BreadcrumbzDatabase::class.java,
        "breadcrumbz.db",
    ).addMigrations(
        MIGRATION_1_2,
        MIGRATION_2_3,
        MIGRATION_3_4,
        MIGRATION_4_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
    ).build()

    private val tripDao = database.tripDao()
    private val stopDao = database.stopDao()
    private val entryDao = database.entryDao()
    private val photoDao = database.photoDao()
    private val placeNameDao = database.placeNameDao()

    val photoStore = InternalPhotoStore(appContext)

    val placeNameResolver = PlaceNameResolver(placeNameDao)

    val repository: BreadcrumbzRepository = RoomBreadcrumbzRepository(
        tripDao = tripDao,
        stopDao = stopDao,
        entryDao = entryDao,
        photoDao = photoDao,
        photoStore = photoStore,
    )

    val backupManager = BackupManager(
        context = appContext,
        tripDao = tripDao,
        stopDao = stopDao,
        entryDao = entryDao,
        photoDao = photoDao,
        placeNameDao = placeNameDao,
        photoStore = photoStore,
        appVersion = "0.14.0",
    )

    private val placeRefresher = PlaceNameRefresher(stopDao, placeNameResolver)
    private val settings: SharedPreferences =
        appContext.getSharedPreferences("breadcrumbz", Context.MODE_PRIVATE)
    private val workScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Beim ersten Start dieser Version alle alten Orte einmalig auffrischen. */
    fun refreshPlaceNamesOnce() {
        if (settings.getBoolean("places_refreshed_v8", false)) return
        workScope.launch {
            placeRefresher.refreshAll(force = true)
            settings.edit().putBoolean("places_refreshed_v8", true).apply()
        }
    }

    /** Manuell aus den Einstellungen: alle Pinnamen neu aus den Koordinaten holen. */
    fun refreshPlaceNamesNow() {
        workScope.launch { placeRefresher.refreshAll(force = true) }
    }
}
