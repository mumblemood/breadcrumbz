package de.breadcrumbz.app.state

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import de.breadcrumbz.app.data.photo.ImportedPhoto
import de.breadcrumbz.app.data.photo.PhotoSourceReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Fotos, die eine andere App (z. B. Google Fotos) an Breadcrumbz geteilt hat. */
sealed interface SharedPhotos {
    /** Die Bilder werden gerade gelesen. */
    data class Reading(val count: Int) : SharedPhotos

    /** Fertig gelesen, wartet auf die Wahl der Reise. */
    data class Ready(val photos: List<ImportedPhoto>, val skipped: Int) : SharedPhotos

    /** Nichts Lesbares dabei. */
    data object Failed : SharedPhotos
}

/**
 * Liest geteilte Bilder sofort ein: Die Leseerlaubnis der fremden App gilt nur
 * kurz, und Cloud-Fotos aus Google Fotos sind danach evtl. nicht mehr
 * erreichbar. Die Bytes landen erst nach der Wahl der Reise in der App.
 */
class SharedPhotosViewModel : ViewModel() {
    private val _state = MutableStateFlow<SharedPhotos?>(null)
    val state: StateFlow<SharedPhotos?> = _state

    fun receive(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        val appContext = context.applicationContext
        _state.value = SharedPhotos.Reading(uris.size)
        viewModelScope.launch(Dispatchers.IO) {
            val photos = mutableListOf<ImportedPhoto>()
            uris.distinct().forEach { uri ->
                try {
                    val mimeType = appContext.contentResolver.getType(uri) ?: "image/jpeg"
                    // Videos und andere Dateien lassen wir aus.
                    if (!mimeType.startsWith("image/")) return@forEach
                    val source = PhotoSourceReader.readWithMetadata(appContext, uri)
                        ?: return@forEach
                    photos.add(
                        ImportedPhoto(
                            bytes = source.bytes,
                            mimeType = mimeType,
                            latitude = source.latitude,
                            longitude = source.longitude,
                            capturedAt = source.capturedAt,
                        ),
                    )
                } catch (_: Throwable) {
                    // Dieses eine Bild überspringen – der Rest kommt trotzdem.
                }
            }
            _state.value = if (photos.isEmpty()) {
                SharedPhotos.Failed
            } else {
                SharedPhotos.Ready(photos, skipped = uris.distinct().size - photos.size)
            }
        }
    }

    /** Fertig gelesene Fotos, die an eine gewählte Reise übergeben werden. */
    data class Delivery(val tripId: String, val photos: List<ImportedPhoto>)

    private val _delivery = MutableStateFlow<Delivery?>(null)
    val delivery: StateFlow<Delivery?> = _delivery

    fun deliverTo(tripId: String) {
        val ready = _state.value as? SharedPhotos.Ready ?: return
        _delivery.value = Delivery(tripId, ready.photos)
        _state.value = null
    }

    /** Die Reise hat die Fotos gespeichert. */
    fun delivered() {
        _delivery.value = null
    }

    fun clear() {
        _state.value = null
    }
}
