package de.breadcrumbz.app.ui.trips

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.breadcrumbz.app.state.SharedPhotos
import de.breadcrumbz.app.state.TripOverview
import de.breadcrumbz.app.ui.theme.Ink
import de.breadcrumbz.app.ui.theme.PaperAlertDialog
import de.breadcrumbz.app.ui.theme.Sepia
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notizzettel, wenn Fotos aus Google Fotos (oder einer anderen App) geteilt
 * wurden: in welche Reise sollen sie? Empfangene Reisen fehlen, weil sie
 * nicht verändert werden dürfen.
 */
@Composable
fun SharedPhotosDialog(
    state: SharedPhotos,
    trips: List<TripOverview>,
    onPickTrip: (String) -> Unit,
    onNewTrip: () -> Unit,
    onDismiss: () -> Unit,
) {
    val editable = trips.filterNot { it.trip.isReceived }
        .sortedByDescending { it.endDate ?: it.startDate ?: it.trip.createdAt }
    PaperAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (state) {
                    is SharedPhotos.Reading -> "Fotos werden gelesen …"
                    is SharedPhotos.Ready -> if (state.photos.size == 1) {
                        "1 Foto – in welche Reise?"
                    } else {
                        "${state.photos.size} Fotos – in welche Reise?"
                    }
                    SharedPhotos.Failed -> "Keine Fotos gefunden"
                },
            )
        },
        text = {
            when (state) {
                is SharedPhotos.Reading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(color = Ink, strokeWidth = 2.dp)
                    Text(
                        "Cloud-Fotos lädt Google Fotos dabei erst herunter. " +
                            "Das kann einen Moment dauern.",
                    )
                }
                SharedPhotos.Failed -> Text(
                    "Die geteilten Dateien ließen sich nicht als Bild lesen. " +
                        "Videos werden nicht übernommen.",
                )
                is SharedPhotos.Ready -> Column {
                    if (state.skipped > 0) {
                        Text(
                            "${state.skipped} Datei(en) übersprungen (z. B. Videos).",
                            style = MaterialTheme.typography.bodySmall,
                            color = Sepia,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "Die Fotos kommen ohne Tagebucheintrag in die Zeitleiste " +
                            "und – wenn sie einen Ort haben – an den passenden Pin. " +
                            "Per Drag & Drop kannst du sie später einem Eintrag zuordnen.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(editable, key = { it.trip.id }) { overview ->
                            TripChoice(overview, onClick = { onPickTrip(overview.trip.id) })
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onNewTrip) { Text("+ Neue Reise") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (state is SharedPhotos.Failed) "OK" else "Abbrechen")
            }
        },
    )
}

@Composable
private fun TripChoice(overview: TripOverview, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Ink.copy(alpha = 0.5f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            overview.trip.title.ifBlank { "Reise ohne Titel" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val details = listOfNotNull(overview.place, dateRange(overview))
        if (details.isNotEmpty()) {
            Text(
                details.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Sepia,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun dateRange(overview: TripOverview): String? {
    val format = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
    val start = overview.startDate?.let { format.format(Date(it)) }
    val end = overview.endDate?.let { format.format(Date(it)) }
    return when {
        start != null && end != null && start != end -> "$start – $end"
        else -> start ?: end
    }
}
