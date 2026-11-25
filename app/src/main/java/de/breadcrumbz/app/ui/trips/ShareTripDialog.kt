package de.breadcrumbz.app.ui.trips

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import de.breadcrumbz.app.data.settings.SettingsStore
import de.breadcrumbz.app.data.share.ReceiveResult
import de.breadcrumbz.app.data.share.ShareOptions
import de.breadcrumbz.app.data.share.TripShareManager
import de.breadcrumbz.app.ui.theme.PaperAlertDialog
import de.breadcrumbz.app.ui.theme.SealRed
import de.breadcrumbz.app.ui.theme.Sepia
import kotlinx.coroutines.launch

private const val MIN_PASSWORD = 6

/**
 * „Reise verschicken“: Name, optionales Passwort und Fotogröße wählen, dann
 * über das Android-Teilen-Menü verschicken oder als Datei speichern.
 */
@Composable
fun ShareTripDialog(
    tripId: String,
    tripTitle: String,
    shareManager: TripShareManager,
    settingsStore: SettingsStore,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var senderName by remember { mutableStateOf(settingsStore.senderName) }
    var usePassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordRepeat by remember { mutableStateOf("") }
    var reducePhotos by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }

    val passwordProblem = when {
        !usePassword -> null
        password.length < MIN_PASSWORD -> "Mindestens $MIN_PASSWORD Zeichen."
        password != passwordRepeat -> "Die beiden Passwörter sind nicht gleich."
        else -> null
    }
    val ready = !working && passwordProblem == null

    fun options(): ShareOptions {
        settingsStore.senderName = senderName
        return ShareOptions(
            senderName = senderName,
            password = if (usePassword) password else null,
            reducePhotos = reducePhotos,
        )
    }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        working = true
        val chosen = options()
        scope.launch {
            val message = shareManager.exportToUri(tripId, uri, chosen)
            working = false
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            onDismiss()
        }
    }

    fun shareNow() {
        working = true
        val chosen = options()
        scope.launch {
            val result = shareManager.exportToShareFile(tripId, chosen)
            working = false
            result.onSuccess { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file,
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/octet-stream"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, tripTitle)
                    clipData = ClipData.newRawUri(file.name, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                runCatching {
                    context.startActivity(Intent.createChooser(send, "Reise verschicken"))
                }.onFailure {
                    Toast.makeText(context, "Keine App zum Verschicken gefunden.", Toast.LENGTH_LONG)
                        .show()
                }
                onDismiss()
            }.onFailure { error ->
                Toast.makeText(
                    context,
                    "Verpacken fehlgeschlagen: ${error.message ?: "unbekannter Fehler"}",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    PaperAlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        title = { Text("Reise verschicken") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "„$tripTitle“ wird als Datei verpackt. Der Empfänger braucht " +
                        "Breadcrumbz und kann die Reise nur ansehen. Fotos lassen sich " +
                        "dort nicht speichern oder weiterschicken.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = senderName,
                    onValueChange = { senderName = it.take(40) },
                    label = { Text("Dein Name (für den Empfänger)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mit Passwort schützen", modifier = Modifier.weight(1f))
                    Switch(checked = usePassword, onCheckedChange = { usePassword = it })
                }
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = passwordRepeat,
                        onValueChange = { passwordRepeat = it },
                        label = { Text("Passwort wiederholen") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        passwordProblem ?: "Das Passwort separat mitteilen (z. B. mündlich). " +
                            "Ohne Passwort lässt sich die Datei nicht öffnen – auch nicht " +
                            "wiederherstellen.",
                        color = if (passwordProblem != null) SealRed else Sepia,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text("Fotos", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.clickable { reducePhotos = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = reducePhotos, onClick = { reducePhotos = true })
                    Column {
                        Text("Verkleinert (empfohlen)")
                        Text(
                            "ca. 2000 Pixel – reicht fürs Handy, Datei viel kleiner",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sepia,
                        )
                    }
                }
                Row(
                    modifier = Modifier.clickable { reducePhotos = false },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = !reducePhotos, onClick = { reducePhotos = false })
                    Column {
                        Text("Originalgröße")
                        Text(
                            "beste Qualität, Datei kann sehr groß werden",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sepia,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(
                    enabled = ready,
                    onClick = { saveLauncher.launch(shareManager.fileNameFor(tripTitle)) },
                ) {
                    Text("Stattdessen als Datei speichern")
                }
                if (working) {
                    Text("Reise wird verpackt …", color = Sepia)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { shareNow() }) { Text("Verschicken") }
        },
        dismissButton = {
            TextButton(enabled = !working, onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

/**
 * „Reise empfangen“: nimmt die gewählte Datei an und fragt bei Bedarf nach
 * dem Passwort. [onDone] bekommt die neue Reise (oder null bei Abbruch).
 */
@Composable
fun rememberReceiveTrip(
    shareManager: TripShareManager,
    onDone: (ReceiveResult.Success?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var askPassword by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var wrongPassword by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }

    fun handle(result: ReceiveResult) {
        working = false
        when (result) {
            ReceiveResult.NeedsPassword -> {
                askPassword = true
                wrongPassword = false
            }
            ReceiveResult.WrongPassword -> {
                askPassword = true
                wrongPassword = true
            }
            is ReceiveResult.Failure -> {
                askPassword = false
                pendingUri = null
                Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                onDone(null)
            }
            is ReceiveResult.Success -> {
                askPassword = false
                pendingUri = null
                password = ""
                val from = result.sender?.let { " von $it" }.orEmpty()
                val what = if (result.updated) "aktualisiert" else "empfangen"
                Toast.makeText(
                    context,
                    "Reise „${result.title}“$from $what (${result.photos} Fotos).",
                    Toast.LENGTH_LONG,
                ).show()
                onDone(result)
            }
        }
    }

    val pickLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingUri = uri
        password = ""
        working = true
        scope.launch { handle(shareManager.receive(uri, null)) }
    }


    if (askPassword) {
        PaperAlertDialog(
            onDismissRequest = {
                if (!working) {
                    askPassword = false
                    pendingUri = null
                    onDone(null)
                }
            },
            title = { Text("Passwort eingeben") },
            text = {
                Column {
                    Text("Diese Reise ist mit einem Passwort geschützt.")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            wrongPassword = false
                        },
                        label = { Text("Passwort") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (wrongPassword) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Das Passwort stimmt nicht.",
                            color = SealRed,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (working) Text("Wird geöffnet …", color = Sepia)
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !working && password.isNotEmpty(),
                    onClick = {
                        val uri = pendingUri ?: return@TextButton
                        working = true
                        scope.launch { handle(shareManager.receive(uri, password)) }
                    },
                ) { Text("Öffnen") }
            },
            dismissButton = {
                TextButton(
                    enabled = !working,
                    onClick = {
                        askPassword = false
                        pendingUri = null
                        onDone(null)
                    },
                ) { Text("Abbrechen") }
            },
        )
    }

    return remember(pickLauncher) { { pickLauncher.launch(arrayOf("*/*")) } }
}
