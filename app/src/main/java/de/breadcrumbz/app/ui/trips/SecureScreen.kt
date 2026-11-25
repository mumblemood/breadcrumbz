package de.breadcrumbz.app.ui.trips

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Bildschirmfoto-Sperre (FLAG_SECURE), solange [enabled] gilt: Screenshots und
 * Bildschirmaufnahmen werden schwarz, und die Vorschau in „Zuletzt verwendet“
 * zeigt nichts. Gilt für empfangene Reisen, damit ihre Fotos nicht einfach
 * abgespeichert werden können.
 */
@Composable
fun SecureScreen(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = context.findActivity()?.window
        if (enabled) window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
