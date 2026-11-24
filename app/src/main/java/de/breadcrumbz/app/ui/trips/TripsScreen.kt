package de.breadcrumbz.app.ui.trips

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.state.TripsViewModel
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
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val photosByTrip by viewModel.photosByTrip.collectAsStateWithLifecycle()

    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var titleInput by rememberSaveable { mutableStateOf("") }
    var descriptionInput by rememberSaveable { mutableStateOf("") }
    var startDateInput by rememberSaveable { mutableStateOf("") }
    var endDateInput by rememberSaveable { mutableStateOf("") }
    var transportMode by rememberSaveable { mutableStateOf(TransportMode.CAR) }
    var showModePicker by remember { mutableStateOf(false) }
    var deleteCandidate by remember { mutableStateOf<Trip?>(null) }

    fun openEditor(trip: Trip? = null) {
        editingId = trip?.id
        titleInput = trip?.title.orEmpty()
        descriptionInput = trip?.description.orEmpty()
        startDateInput = formatDate(trip?.startDate)
        endDateInput = formatDate(trip?.endDate)
        transportMode = trip?.defaultTransportMode ?: TransportMode.CAR
        showEditor = true
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text("Breadcrumbz") })
        },
        floatingActionButton = {
            Button(
                onClick = { openEditor() },
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("+ Neue Reise")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
        ) {
            Text("Deine Reisen", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (trips.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text(
                        text = "Noch keine Reise angelegt.\nTippe unten auf „+ Neue Reise“.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(trips, key = { it.id }) { trip ->
                        val coverPhoto: Photo? = trip.coverPhotoId?.let { id ->
                            photosByTrip[trip.id]?.firstOrNull { it.id == id }
                        }
                        val coverPath = coverPhoto?.let { viewModel.photoPath(it.fileName) }
                        TripCard(
                            trip = trip,
                            coverPath = coverPath,
                            onOpen = { onOpenTrip(trip.id) },
                            onEdit = { openEditor(trip) },
                            onDelete = { deleteCandidate = trip },
                        )
                    }
                }
            }
        }
    }

    if (showEditor) {
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(if (editingId == null) "Neue Reise" else "Reise bearbeiten") },
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
                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = { descriptionInput = it },
                        label = { Text("Kurze Beschreibung") },
                        minLines = 2,
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = transportMode.label,
                        onValueChange = {},
                        label = { Text("Standard-Verkehrsmittel") },
                        readOnly = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = { showModePicker = true }) {
                        Text("Verkehrsmittel ändern")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveTrip(
                            id = editingId,
                            title = titleInput,
                            description = descriptionInput,
                            startDate = parseGermanDate(startDateInput),
                            endDate = parseGermanDate(endDateInput),
                            transportMode = transportMode,
                        )
                        showEditor = false
                    },
                    enabled = titleInput.isNotBlank(),
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditor = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }

    if (showModePicker) {
        AlertDialog(
            onDismissRequest = { showModePicker = false },
            title = { Text("Verkehrsmittel") },
            text = {
                Column {
                    TransportMode.entries.forEach { mode ->
                        TextButton(
                            onClick = {
                                transportMode = mode
                                showModePicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (mode == transportMode) "✓ ${mode.label}" else mode.label,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModePicker = false }) { Text("Schließen") }
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
    trip: Trip,
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
        Column(modifier = Modifier.padding(20.dp)) {
            val range = listOfNotNull(
                trip.startDate?.let { formatDate(it) },
                trip.endDate?.let { formatDate(it) },
            ).joinToString(" - ")
            Text(
                text = if (range.isBlank()) trip.title else "${trip.title} · $range",
                style = MaterialTheme.typography.titleLarge,
            )
            if (trip.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(trip.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Verkehrsmittel: ${trip.defaultTransportMode.label}",
                style = MaterialTheme.typography.bodySmall,
            )

            if (coverPath != null) {
                val bitmap = remember(coverPath) {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(coverPath, options)?.asImageBitmap()
                }
                if (bitmap != null) {
                    Spacer(Modifier.height(10.dp))
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Titelbild",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "Tippen: öffnen · Lange drücken: bearbeiten",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onDelete) { Text("Löschen") }
        }
    }
}
