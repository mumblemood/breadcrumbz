package de.breadcrumbz.app.ui.trips

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import de.breadcrumbz.app.ui.theme.Sepia
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.backup.BackupManager
import de.breadcrumbz.app.data.settings.SettingsStore
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.state.TripsViewModel
import de.breadcrumbz.app.ui.theme.PaperCard
import de.breadcrumbz.app.ui.theme.PaperAlertDialog
import de.breadcrumbz.app.ui.theme.SealRed
import de.breadcrumbz.app.ui.theme.SealRedDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun formatDate(value: Long?): String = value?.let {
    SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).format(Date(it))
} ?: ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    backupManager: BackupManager,
    onOpenTrip: (String) -> Unit,
    onRefreshPlaces: () -> Unit,
    settingsStore: SettingsStore,
    modifier: Modifier = Modifier,
) {
    val overviews by viewModel.overviews.collectAsStateWithLifecycle()
    val onlineRouting by settingsStore.onlineRouting.collectAsStateWithLifecycle()
    val sortedTrips = remember(overviews) {
        overviews.sortedByDescending { it.trip.createdAt }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var titleInput by rememberSaveable { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<Trip?>(null) }
    val editingTrip = overviews.firstOrNull { it.trip.id == editingId }?.trip

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val message = backupManager.exportTo(uri)
                withContext(Dispatchers.Main) { toast(message) }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val message = backupManager.importFrom(uri)
                withContext(Dispatchers.Main) { toast(message) }
            }
        }
    }

    fun openEditor(trip: Trip) {
        editingId = trip.id
        titleInput = trip.title
        showEditor = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Breadcrumbz") },
                navigationIcon = {
                    Icon(
                        painter = painterResource(R.drawable.ic_compass),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier
                            .padding(start = 12.dp, end = 4.dp)
                            .size(34.dp),
                    )
                },
                actions = {
                    // Neue Reise: rotes Siegel oben rechts.
                    IconButton(
                        onClick = { viewModel.createTrip(onOpenTrip) },
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .size(44.dp)
                            .background(SealRed, CircleShape)
                            .border(3.dp, SealRedDark, CircleShape),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = "Neue Reise anlegen",
                            tint = PaperCard,
                        )
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_menu),
                                contentDescription = "Menü",
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Einstellungen") },
                                onClick = {
                                    menuExpanded = false
                                    showSettings = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Backup erstellen") },
                                onClick = {
                                    menuExpanded = false
                                    exportLauncher.launch("breadcrumbz-backup.zip")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Backup einspielen") },
                                onClick = {
                                    menuExpanded = false
                                    importLauncher.launch(
                                        arrayOf("application/zip", "application/octet-stream"),
                                    )
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Info") },
                                onClick = {
                                    menuExpanded = false
                                    showInfo = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (sortedTrips.isEmpty()) {
                    item(key = "empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Text(
                                text = "Noch keine Reise angelegt.\nTippe oben rechts auf das rote „+“.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),
                            )
                        }
                    }
                } else {
                    items(sortedTrips, key = { it.trip.id }) { overview ->
                        val coverPath = overview.coverPhoto?.let {
                            viewModel.photoPath(it.fileName)
                        }
                        val storedModes by remember(overview.trip.id) {
                            viewModel.segmentModes(overview.trip.id)
                        }.collectAsState(initial = emptyList())
                        TripMapCard(
                            overview = overview,
                            coverPath = coverPath,
                            segmentModes = storedModes.associate { it.id to it.mode },
                            onOpen = { onOpenTrip(overview.trip.id) },
                            onEdit = { openEditor(overview.trip) },
                        )
                    }
                }
            }
        }
    }

    if (showInfo) {
        InfoDialog(onDismiss = { showInfo = false })
    }

    if (showSettings) {
        PaperAlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Einstellungen") },
            text = {
                Column {
                    Text(
                        "Alle Daten bleiben lokal auf diesem Gerät. Es gibt kein Konto " +
                            "und keinen Cloud-Zwang. Karten werden lokal zwischengespeichert.",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Helles und dunkles Systemthema werden automatisch unterstützt.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Online-Routing")
                            Text(
                                "Straßenlinie für Auto und Campingbus aus dem Internet holen. " +
                                    "Ausgeschaltet bleibt die Linie komplett lokal gezeichnet.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(
                            checked = onlineRouting,
                            onCheckedChange = { settingsStore.setOnlineRouting(it) },
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = {
                        onRefreshPlaces()
                        toast("Ortsnamen werden aus den Koordinaten aktualisiert.")
                        showSettings = false
                    }) {
                        Text("Ortsnamen jetzt aktualisieren")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettings = false }) { Text("Schließen") }
            },
        )
    }

    if (showEditor) {
        PaperAlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text("Reise bearbeiten") },
            text = {
                Column {
                    OutlinedTextField(
                        value = titleInput,
                        onValueChange = { titleInput = it },
                        label = { Text("Titel") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Der Zeitraum entsteht automatisch aus Fotos, Pins und Einträgen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Löschen nur hier als Symbol – nicht mehr auf der Reise-Karte.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = {
                                editingTrip?.let {
                                    deleteCandidate = it
                                    showEditor = false
                                }
                            },
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Reise löschen",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveTrip(
                            id = editingId,
                            title = titleInput,
                            // Nicht angezeigte Felder unverändert weitergeben.
                            description = editingTrip?.description.orEmpty(),
                            // Zeitraum bleibt automatisch und wird nicht angefasst.
                            startDate = editingTrip?.startDate,
                            endDate = editingTrip?.endDate,
                            transportMode = editingTrip?.defaultTransportMode
                                ?: TransportMode.CAR,
                        )
                        showEditor = false
                    },
                    enabled = titleInput.isNotBlank(),
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) { Text("Abbrechen") }
            },
        )
    }

    deleteCandidate?.let { trip ->
        PaperAlertDialog(
            onDismissRequest = { deleteCandidate = null },
            title = { Text("Reise löschen?") },
            text = { Text("${trip.title} und alle zugehörigen Daten werden gelöscht.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteTrip(trip.id)
                        deleteCandidate = null
                    },
                ) {
                    Text("Löschen")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteCandidate = null }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

/** Adresse für „Buy me a coffee“ – Platzhalter, bis der Name feststeht. */
private const val COFFEE_URL = ""
private const val GITHUB_URL = "https://github.com/mumblemood/breadcrumbz"

/** Info: Version, Entwickler, GitHub und (vorbereitet) Buy me a coffee. */
@Composable
private fun InfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    PaperAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Breadcrumbz") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Dein Reisetagebuch mit Karte, Fotos und Zeitleiste.")
                if (version.isNotBlank()) {
                    Text("Version $version", color = Sepia)
                }
                Text("Entwickelt von mumblemood")
                TextButton(onClick = { runCatching { uriHandler.openUri(GITHUB_URL) } }) {
                    Text("Auf GitHub ansehen")
                }
                if (COFFEE_URL.isNotBlank()) {
                    TextButton(onClick = { runCatching { uriHandler.openUri(COFFEE_URL) } }) {
                        Text("☕ Buy me a coffee")
                    }
                } else {
                    Text("☕ Buy me a coffee – kommt bald", color = Sepia)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Schließen") }
        },
    )
}

