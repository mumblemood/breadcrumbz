package de.breadcrumbz.app.ui.trips

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.R
import de.breadcrumbz.app.data.backup.BackupManager
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.state.TripOverview
import de.breadcrumbz.app.state.TripsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun parseGermanDate(value: String): Long? = try {
    if (value.isBlank()) null
    else SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY).parse(value.trim())?.time
} catch (_: Exception) {
    null
}

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
    modifier: Modifier = Modifier,
) {
    val overviews by viewModel.overviews.collectAsStateWithLifecycle()
    val sortedTrips = remember(overviews) {
        overviews.sortedByDescending { it.trip.createdAt }
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var menuExpanded by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var titleInput by rememberSaveable { mutableStateOf("") }
    var startDateInput by rememberSaveable { mutableStateOf("") }
    var endDateInput by rememberSaveable { mutableStateOf("") }
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
        startDateInput = formatDate(trip.startDate)
        endDateInput = formatDate(trip.endDate)
        showEditor = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Breadcrumbz") },
                navigationIcon = {
                    IconButton(onClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.ic_launcher_foreground),
                            contentDescription = "Breadcrumbz",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                },
                actions = {
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
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Plus oben mittig innerhalb der Liste.
                item(key = "add-trip") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = androidx.compose.ui.Alignment.Center,
                    ) {
                        IconButton(onClick = { viewModel.createTrip(onOpenTrip) }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = "Neue Reise anlegen",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    }
                }
                if (sortedTrips.isEmpty()) {
                    item(key = "empty") {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                        ) {
                            Text(
                                text = "Noch keine Reise angelegt.\nTippe auf „+“ über der Liste.",
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
                        TripCard(
                            overview = overview,
                            coverPath = coverPath,
                            onOpen = { onOpenTrip(overview.trip.id) },
                            onEdit = { openEditor(overview.trip) },
                            onDelete = { deleteCandidate = overview.trip },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
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
        AlertDialog(
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
                    Row {
                        OutlinedTextField(
                            value = startDateInput,
                            onValueChange = { startDateInput = it },
                            label = { Text("Von (dd.MM.yyyy)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        OutlinedTextField(
                            value = endDateInput,
                            onValueChange = { endDateInput = it },
                            label = { Text("Bis (dd.MM.yyyy)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
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
                            startDate = parseGermanDate(startDateInput),
                            endDate = parseGermanDate(endDateInput),
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
        AlertDialog(
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TripCard(
    overview: TripOverview,
    coverPath: String?,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onEdit,
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        // Ein festgelegtes Titelbild liegt nur ganz leicht sichtbar hinter den Feldern.
        val coverBitmap = remember(coverPath) {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            coverPath?.let { BitmapFactory.decodeFile(it, options) }?.asImageBitmap()
        }
        Box {
            if (coverBitmap != null) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(24.dp))
                        .alpha(0.35f),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
                        ),
                )
            }
            Column(modifier = Modifier.padding(20.dp)) {
                val range = listOfNotNull(
                    overview.startDate?.let { formatDate(it) },
                    overview.endDate?.let { formatDate(it) },
                ).joinToString(" - ")
                val headline = listOfNotNull(
                    overview.trip.title,
                    range.ifBlank { null },
                ).joinToString(" · ")
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}
