package de.breadcrumbz.app

import android.content.Context
import androidx.room.Room
import de.breadcrumbz.app.data.db.BreadcrumbzDatabase
import de.breadcrumbz.app.data.db.MIGRATION_1_2
import de.breadcrumbz.app.data.db.MIGRATION_2_3
import de.breadcrumbz.app.data.db.MIGRATION_3_4
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.place.PlaceNameResolver
import de.breadcrumbz.app.data.repository.RoomBreadcrumbzRepository
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository
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
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()

    val photoStore = InternalPhotoStore(appContext)

    val placeNameResolver = PlaceNameResolver(database.placeNameDao())

    val repository: BreadcrumbzRepository = RoomBreadcrumbzRepository(
        tripDao = database.tripDao(),
        stopDao = database.stopDao(),
        entryDao = database.entryDao(),
        photoDao = database.photoDao(),
        photoStore = photoStore,
    )
}
