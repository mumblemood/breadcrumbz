package de.breadcrumbz.app.data.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Kleine, rein lokale App-Einstellungen – ohne Konto und ohne Cloud. */
class SettingsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("breadcrumbz", Context.MODE_PRIVATE)

    private val _onlineRouting = MutableStateFlow(
        prefs.getBoolean(KEY_ONLINE_ROUTING, true),
    )
    /** Straßenlinie online holen. Standardmäßig eingeschaltet. */
    val onlineRouting: StateFlow<Boolean> = _onlineRouting.asStateFlow()

    fun setOnlineRouting(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLINE_ROUTING, enabled).apply()
        _onlineRouting.value = enabled
    }

    /** Name, mit dem verschickte Reisen beim Empfänger angezeigt werden. */
    var senderName: String
        get() = prefs.getString(KEY_SENDER_NAME, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_SENDER_NAME, value.trim()).apply()
        }

    private companion object {
        const val KEY_ONLINE_ROUTING = "online_routing"
        const val KEY_SENDER_NAME = "sender_name"
    }
}
