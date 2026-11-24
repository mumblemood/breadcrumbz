package de.breadcrumbz.app

import android.content.Context
import androidx.room.Room
import de.breadcrumbz.app.data.db.BreadcrumbzDatabase
import de.breadcrumbz.app.data.photo.InternalPhotoStore
import de.breadcrumbz.app.data.repository.RoomBreadcrumbzRepository
import de.breadcrumbz.app.domain.repository.BreadcrumbzRepository

/** Zentraler Ort, an dem alle Bausteine zusammengesetzt werden. */
class AppContainer(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        BreadcrumbzDatabase::class.java,
        "breadcrumbz.db",
    ).build()

    val repository: BreadcrumbzRepository = RoomBreadcrumbzRepository(
        tripDao = database.tripDao(),
        stopDao = database.stopDao(),
        entryDao = database.entryDao(),
        photoDao = database.photoDao(),
    )

    val photoStore = InternalPhotoStore(context)
}
