package de.breadcrumbz.app.ui.trips

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.state.TripMapViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Feste Papier-Optik: Diese Farben gelten unabhängig vom Systemthema (hell/dunkel).
private val paper = Color(0xFFF6EFE1)
private val ink = Color(0xFF3B2B20)
private val frame = Color(0xFFDCCDB4)

/** TextButton mit Lesbarkeit auf dem festen Papierhintergrund. */
@Composable
private fun InkTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    TextButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.textButtonColors(contentColor = ink),
        content = content,
    )
}

@Composable
private fun rememberBigImage(viewModel: TripMapViewModel, fileName: String): ImageBitmap? =
    remember(fileName) {
        BitmapFactory.decodeFile(viewModel.photoPath(fileName))?.asImageBitmap()
    }

private fun dateText(time: Long?): String =
    time?.let { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(it)) }
        ?: "Ohne Zeit"

private fun coordText(latitude: Double?, longitude: Double?): String =
    if (latitude != null && longitude != null) {
        String.format(Locale.GERMANY, "%.5f, %.5f", latitude, longitude)
    } else {
        "Noch kein Ort"
    }

/**
 * Vollbild für jedes Element: Foto, Pin oder Eintrag. Immer mit Zurück-Knopf.
 */
@Composable
fun FullscreenElement(
    elementKey: String,
    viewModel: TripMapViewModel,
    stops: List<Stop>,
    photos: List<Photo>,
    entries: List<Entry>,
    photoBitmap: (String) -> ImageBitmap?,
    onClose: () -> Unit,
    onOpenElement: (String) -> Unit,
    onAddPhotos: (String) -> Unit,
    onEditPhoto: (String) -> Unit,
    onAdoptPhotoLocation: (String) -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        CompositionLocalProvider(LocalContentColor provides ink) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(paper),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ink,
                        contentColor = paper,
                    ),
                ) { Text("← Zurück", color = paper) }
            }

            when {
                elementKey.startsWith("photo-") -> {
                    val id = elementKey.removePrefix("photo-")
                    val photo = photos.firstOrNull { it.id == id }
                    if (photo == null) {
                        MissingElement(onClose)
                    } else {
                        val bitmap = rememberBigImage(viewModel, photo.fileName)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(420.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFF3B2B20)),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = photo.caption.ifBlank { "Foto" },
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Text("Foto konnte nicht geladen werden", color = Color.White)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                photo.caption.ifBlank { "Foto" },
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(dateText(photo.capturedAt))
                            Text(coordText(photo.latitude, photo.longitude))
                            val entry = entries.firstOrNull { it.id == photo.entryId }
                            if (entry != null) {
                                InkTextButton(onClick = { onOpenElement("entry-${entry.id}") }) {
                                    Text("Eintrag: ${entry.title.ifBlank { "Tagebucheintrag" }}")
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InkTextButton(onClick = { viewModel.setCoverPhoto(photo.id) }) {
                                    Text("Titelbild")
                                }
                                InkTextButton(onClick = { onEditPhoto(photo.id) }) {
                                    Text("Titel bearbeiten")
                                }
                                if (photo.latitude != null && photo.longitude != null &&
                                    entry != null
                                ) {
                                    InkTextButton(onClick = {
                                        onAdoptPhotoLocation(photo.id)
                                        onClose()
                                    }) {
                                        Text("Ort in Eintrag übernehmen")
                                    }
                                }
                                if (photo.latitude == null || photo.longitude == null) {
                                    InkTextButton(onClick = {
                                        viewModel.beginPlacement(photo.id)
                                        onClose()
                                    }) {
                                        Text("Ort setzen")
                                    }
                                }
                            }
                        }
                    }
                }

                elementKey.startsWith("stop-") -> {
                    val id = elementKey.removePrefix("stop-")
                    val stop = stops.firstOrNull { it.id == id }
                    if (stop == null) {
                        MissingElement(onClose)
                    } else {
                        val linkedEntries = entries.filter { it.stopId == stop.id }
                        val linkedPhotos = photos.filter {
                            it.stopId == stop.id || it.entryId in linkedEntries.map { entry -> entry.id }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(
                                if (stop.title.isBlank()) "Pin" else stop.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                            )
                            if (stop.visitedAt != null) Text(dateText(stop.visitedAt))
                            Text(coordText(stop.latitude, stop.longitude))
                            Text(
                                "Verkehrsmittel hierher: " +
                                    (stop.transportMode?.label ?: "Standard der Reise"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                TransportMode.entries.forEach { mode ->
                                    InkTextButton(onClick = {
                                        viewModel.setStopTransportMode(stop.id, mode)
                                    }) {
                                        Text(mode.label)
                                    }
                                }
                            }
                            linkedEntries.forEach { entry ->
                                Spacer(Modifier.height(12.dp))
                                if (entry.title.isNotBlank()) {
                                    Text(entry.title, fontWeight = FontWeight.Bold)
                                }
                                if (entry.body.isNotBlank()) {
                                    Text(entry.body)
                                }
                                InkTextButton(onClick = { onOpenElement("entry-${entry.id}") }) {
                                    Text("Eintrag öffnen")
                                }
                            }
                            if (linkedPhotos.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("${linkedPhotos.size} Fotos", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    linkedPhotos.forEach { photo ->
                                        val bitmap = photoBitmap(photo.id)
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(frame)
                                                .clickable {
                                                    onOpenElement("photo-${photo.id}")
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = photo.caption,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                elementKey.startsWith("entry-") -> {
                    val id = elementKey.removePrefix("entry-")
                    val entry = entries.firstOrNull { it.id == id }
                    if (entry == null) {
                        MissingElement(onClose)
                    } else {
                        val context = LocalContext.current
                        var titleDraft by remember(entry.id) { mutableStateOf(entry.title) }
                        var bodyDraft by remember(entry.id) { mutableStateOf(entry.body) }
                        val childPhotos = photos.filter { it.entryId == entry.id }
                        val childStops = stops.filter { it.entryId == entry.id }
                        val stopTitle = entry.stopId
                            ?.let { target -> stops.firstOrNull { it.id == target }?.title }
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = titleDraft,
                                onValueChange = {
                                    titleDraft = it
                                    viewModel.updateEntryText(entry.id, it, bodyDraft)
                                },
                                label = { Text("Titel") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = bodyDraft,
                                onValueChange = {
                                    bodyDraft = it
                                    viewModel.updateEntryText(entry.id, titleDraft, it)
                                },
                                label = { Text("Tagebuch") },
                                minLines = 5,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    val calendar = Calendar.getInstance().apply {
                                        timeInMillis = entry.entryAt ?: System.currentTimeMillis()
                                    }
                                    DatePickerDialog(
                                        context,
                                        { _, year, month, day ->
                                            val next = Calendar.getInstance().apply {
                                                timeInMillis = entry.entryAt
                                                    ?: System.currentTimeMillis()
                                                set(year, month, day)
                                            }.timeInMillis
                                            viewModel.setEntryTime(entry.id, next)
                                        },
                                        calendar.get(Calendar.YEAR),
                                        calendar.get(Calendar.MONTH),
                                        calendar.get(Calendar.DAY_OF_MONTH),
                                    ).show()
                                }) {
                                    Text("Datum")
                                }
                                Button(onClick = {
                                    val calendar = Calendar.getInstance().apply {
                                        timeInMillis = entry.entryAt ?: System.currentTimeMillis()
                                    }
                                    TimePickerDialog(
                                        context,
                                        { _, hour, minute ->
                                            val next = Calendar.getInstance().apply {
                                                timeInMillis = entry.entryAt
                                                    ?: System.currentTimeMillis()
                                                set(Calendar.HOUR_OF_DAY, hour)
                                                set(Calendar.MINUTE, minute)
                                            }.timeInMillis
                                            viewModel.setEntryTime(entry.id, next)
                                        },
                                        calendar.get(Calendar.HOUR_OF_DAY),
                                        calendar.get(Calendar.MINUTE),
                                        true,
                                    ).show()
                                }) {
                                    Text("Uhrzeit")
                                }
                            }
                            Text(
                                "Zeit: ${entry.entryAt?.let { dateText(it) } ?: "aus erstem Foto"}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (entry.entryAt != null) {
                                InkTextButton(onClick = { viewModel.setEntryTime(entry.id, null) }) {
                                    Text("Zeit leeren")
                                }
                            }
                            Text(
                                if (stopTitle == null) {
                                    "Ohne Ort – Foto, Pin oder Drag & Drop ergänzt ihn"
                                } else {
                                    "Ort: $stopTitle"
                                },
                                style = MaterialTheme.typography.bodySmall,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                InkTextButton(onClick = { onAddPhotos(entry.id) }) {
                                    Text("Fotos hinzufügen")
                                }
                                if (entry.stopId == null) {
                                    InkTextButton(onClick = {
                                        viewModel.beginEntryPlacement(entry.id)
                                        onClose()
                                    }) { Text("Ort setzen") }
                                }
                                InkTextButton(onClick = {
                                    viewModel.deleteEntry(entry.id)
                                    onClose()
                                }) { Text("Löschen") }
                            }
                            if (childStops.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Pins im Eintrag", fontWeight = FontWeight.Bold)
                                childStops.forEach { stop ->
                                    InkTextButton(onClick = { onOpenElement("stop-${stop.id}") }) {
                                        Text(stop.title.ifBlank { "Pin" })
                                    }
                                }
                            }
                            if (childPhotos.isNotEmpty()) {
                                Spacer(Modifier.height(12.dp))
                                Text("Bilder im Eintrag", fontWeight = FontWeight.Bold)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    childPhotos.forEach { photo ->
                                        val bitmap = photoBitmap(photo.id)
                                        Box(
                                            modifier = Modifier
                                                .size(96.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(frame)
                                                .clickable {
                                                    onOpenElement("photo-${photo.id}")
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (bitmap != null) {
                                                Image(
                                                    bitmap = bitmap,
                                                    contentDescription = photo.caption,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                else -> MissingElement(onClose)
            }
        }
        }
    }
}

@Composable
private fun MissingElement(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Dieses Element gibt es nicht mehr.", color = ink)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onClose,
            colors = ButtonDefaults.buttonColors(
                containerColor = ink,
                contentColor = paper,
            ),
        ) { Text("← Zurück", color = paper) }
    }
}
