package de.breadcrumbz.app.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.breadcrumbz.app.state.DatabaseTestViewModel

@Composable
fun DatabaseTestScreen(
    viewModel: DatabaseTestViewModel,
    modifier: Modifier = Modifier,
) {
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val stops by viewModel.stops.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Breadcrumbz",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Meilenstein 1 – Datenbank-Test",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Reisen: ${trips.size}", style = MaterialTheme.typography.titleLarge)
                Text("Stationen: ${stops.size}", style = MaterialTheme.typography.titleLarge)
                Text("Einträge: ${entries.size}", style = MaterialTheme.typography.titleLarge)

                trips.firstOrNull()?.let {
                    Spacer(Modifier.height(12.dp))
                    Text("Reise: ${it.title}", style = MaterialTheme.typography.bodyLarge)
                }
                stops.firstOrNull()?.let {
                    Text("Ort: ${it.title}", style = MaterialTheme.typography.bodyLarge)
                }
                entries.firstOrNull()?.let {
                    Text("Text: ${it.body}", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = viewModel::createSample,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Beispieldaten anlegen")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = viewModel::clearAll,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Alles löschen")
        }
    }
}
