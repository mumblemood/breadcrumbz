package de.breadcrumbz.app.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.domain.model.Trip
import de.breadcrumbz.app.state.TripsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsScreen(
    viewModel: TripsViewModel,
    onOpenTrip: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()

    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var titleInput by rememberSaveable { mutableStateOf("") }
    var descriptionInput by rememberSaveable { mutableStateOf("") }
    var deleteCandidate by remember { mutableStateOf<Trip?>(null) }

    fun openEditor(trip: Trip? = null) {
        editingId = trip?.id
        titleInput = trip?.title.orEmpty()
        descriptionInput = trip?.description.orEmpty()
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
                        TripCard(
                            trip = trip,
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
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = descriptionInput,
                        onValueChange = { descriptionInput = it },
                        label = { Text("Kurze Beschreibung") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveTrip(editingId, titleInput, descriptionInput)
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

@Composable
private fun TripCard(
    trip: Trip,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(trip.title, style = MaterialTheme.typography.titleLarge)
            if (trip.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(trip.description, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(10.dp))
            Row {
                TextButton(onClick = onOpen) { Text("Karte öffnen") }
                TextButton(onClick = onEdit) { Text("Bearbeiten") }
                TextButton(onClick = onDelete) { Text("Löschen") }
            }
        }
    }
}
