package de.breadcrumbz.app.ui.trips

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.PlacePinIcon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Feste Papier-Optik: Diese Farben gelten unabhängig vom Systemthema (hell/dunkel).
private val paper = Color(0xFFF6EFE1)
private val ink = Color(0xFF3B2B20)
private val frame = Color(0xFFDCCDB4)
// Comic Sans ist nicht auf allen Geräten installiert. Die systemweite
// Comic/Cursive-Schrift ist die zuverlässige Wahl.
private val comic = FontFamily.Cursive

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

/** Zeit und Ort stehen klein unter dem Bild und haben ggf. einen Pin davor. */
@Composable
private fun SmallMeta(text: String, withPin: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withPin) {
            PlacePinIcon(tint = ink, size = 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = ink, style = MaterialTheme.typography.labelSmall)
    }
}

/**
 * Pop-up für jedes Element: Foto, Pin oder Eintrag. Der Hauptbereich bleibt
 * sichtbar und wird nur leicht abgedunkelt.
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
    val context = LocalContext.current
    val density = LocalDensity.current

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnOutsideClick = true,
            dismissOnBackPress = true,
        ),
    ) {
        // Nur leicht dimmen, damit Karte und Timeline darunter erkennbar bleiben.
        // Muss im Dialog-Fenster selbst gesetzt werden, nicht im Hauptfenster.
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            val params = dialogWindow.attributes
            params.dimAmount = 0.18f
            dialogWindow.attributes = params
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .clickable { onClose() },
            contentAlignment = Alignment.Center,
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = paper),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 620.dp)
                    // Klicks im Kartenkartenbereich nicht nach außen durchreichen.
                    .clickable { },
            ) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Button(
                            onClick = onClose,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ink,
                                contentColor = paper,
                            ),
                        ) { Text("← Zurück", color = paper) }
                        Spacer(Modifier.height(8.dp))

                        when {
                            elementKey.startsWith("photo-") -> {
                                val id = elementKey.removePrefix("photo-")
                                val photo = photos.firstOrNull { it.id == id }
                                if (photo == null) {
                                    MissingElement(onClose)
                                } else {
                                    val bitmap = rememberBigImage(viewModel, photo.fileName)
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        // Großer Polaroid-Rahmen, Titel im dicken unteren Rand.
                                        Column(
                                            modifier = Modifier
                                                .width(280.dp)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(paper)
                                                .border(3.dp, ink, RoundedCornerShape(4.dp))
                                                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(220.dp)
                                                    .background(Color(0xFFE7DAC2)),
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
                                                    Text(
                                                        "Foto konnte nicht geladen werden",
                                                        color = ink,
                                                    )
                                                }
                                            }
                                            Spacer(Modifier.height(16.dp))
                                            Text(
                                                text = photo.caption.ifBlank { "Foto" },
                                                color = ink,
                                                fontFamily = comic,
                                                style = MaterialTheme.typography.titleMedium,
                                                textAlign = TextAlign.Center,
                                            )
                                            Spacer(Modifier.height(18.dp))
                                        }
                                        Spacer(Modifier.height(12.dp))
                                        SmallMeta(dateText(photo.capturedAt))
                                        SmallMeta(
                                            coordText(photo.latitude, photo.longitude),
                                            withPin = true,
                                        )
                                        val entry = entries.firstOrNull { it.id == photo.entryId }
                                        if (entry != null) {
                                            InkTextButton(onClick = {
                                                onOpenElement("entry-${entry.id}")
                                            }) {
                                                Text(entry.title.ifBlank { "Eintrag" })
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            InkTextButton(onClick = { viewModel.setCoverPhoto(photo.id) }) {
                                                Text("Titelbild")
                                            }
                                            InkTextButton(onClick = { onEditPhoto(photo.id) }) {
                                                Text("Titel bearbeiten")
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
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
                                        it.stopId == stop.id ||
                                            it.entryId in linkedEntries.map { entry -> entry.id }
                                    }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            PlacePinIcon(
                                                tint = ink,
                                                size = with(density) {
                                                    MaterialTheme.typography.headlineSmall.fontSize.toDp()
                                                },
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (stop.title.isBlank()) "Pin" else stop.title,
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = ink,
                                            )
                                        }
                                        if (stop.visitedAt != null) SmallMeta(dateText(stop.visitedAt))
                                        SmallMeta(
                                            coordText(stop.latitude, stop.longitude),
                                            withPin = true,
                                        )
                                        Text(
                                            "Verkehrsmittel hierher: " +
                                                (stop.transportMode?.label ?: "Standard der Reise"),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = ink,
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
                                            InkTextButton(onClick = {
                                                onOpenElement("entry-${entry.id}")
                                            }) {
                                                Text(entry.title.ifBlank { "Eintrag" })
                                            }
                                            if (entry.body.isNotBlank()) {
                                                Text(entry.body, color = ink)
                                            }
                                        }
                                        if (linkedPhotos.isNotEmpty()) {
                                            Spacer(Modifier.height(12.dp))
                                            Text("Fotos", fontWeight = FontWeight.Bold, color = ink)
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
                                    var titleDraft by remember(entry.id) { mutableStateOf(entry.title) }
                                    var bodyDraft by remember(entry.id) { mutableStateOf(entry.body) }
                                    var placePickerOpen by remember(entry.id) {
                                        mutableStateOf(false)
                                    }
                                    val childStops = stops.filter { it.entryId == entry.id }
                                    val stopTitle = entry.stopId
                                        ?.let { target -> stops.firstOrNull { it.id == target }?.title }
                                    val timeIsAuto = entry.timeFromPhoto && entry.entryAt != null
                                    val placeIsAuto = entry.placeFromPhoto && entry.stopId != null
                                    val fieldColors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = ink,
                                        unfocusedTextColor = ink,
                                        disabledTextColor = ink,
                                        cursorColor = ink,
                                        focusedLabelColor = ink,
                                        unfocusedLabelColor = ink,
                                        focusedBorderColor = ink,
                                        unfocusedBorderColor = frame,
                                        focusedContainerColor = paper,
                                        unfocusedContainerColor = paper,
                                    )

                                    fun pickDateTime() {
                                        val start = Calendar.getInstance().apply {
                                            timeInMillis = entry.entryAt ?: System.currentTimeMillis()
                                        }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val withDate = Calendar.getInstance().apply {
                                                    timeInMillis = entry.entryAt
                                                        ?: System.currentTimeMillis()
                                                    set(year, month, day)
                                                }
                                                TimePickerDialog(
                                                    context,
                                                    { _, hour, minute ->
                                                        val chosen = Calendar.getInstance().apply {
                                                            timeInMillis = withDate.timeInMillis
                                                            set(Calendar.HOUR_OF_DAY, hour)
                                                            set(Calendar.MINUTE, minute)
                                                        }.timeInMillis
                                                        viewModel.setEntryTime(entry.id, chosen)
                                                    },
                                                    withDate.get(Calendar.HOUR_OF_DAY),
                                                    withDate.get(Calendar.MINUTE),
                                                    true,
                                                ).show()
                                            },
                                            start.get(Calendar.YEAR),
                                            start.get(Calendar.MONTH),
                                            start.get(Calendar.DAY_OF_MONTH),
                                        ).show()
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        OutlinedTextField(
                                            value = titleDraft,
                                            onValueChange = {
                                                titleDraft = it
                                                viewModel.updateEntryText(entry.id, it, bodyDraft)
                                            },
                                            label = { Text("Titel", color = ink) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        // Zeit und Ort direkt unterm Titel.
                                        Row(
                                            modifier = Modifier.clickable { pickDateTime() },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = if (entry.entryAt == null) {
                                                    "Zeit: wird aus erstem Foto übernommen"
                                                } else {
                                                    dateText(entry.entryAt)
                                                },
                                                color = ink,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = if (timeIsAuto) FontStyle.Italic else FontStyle.Normal,
                                            )
                                            if (timeIsAuto) {
                                                Text(
                                                    " (aus erstem Foto)",
                                                    color = ink,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontStyle = FontStyle.Italic,
                                                )
                                            }
                                        }
                                        Row(
                                            modifier = Modifier.clickable { placePickerOpen = true },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            PlacePinIcon(
                                                tint = ink,
                                                size = with(density) {
                                                    MaterialTheme.typography.bodySmall.fontSize.toDp()
                                                },
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (stopTitle == null) {
                                                    "Ort: wird aus erstem Foto übernommen"
                                                } else {
                                                    stopTitle
                                                },
                                                color = ink,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontStyle = if (placeIsAuto) FontStyle.Italic else FontStyle.Normal,
                                            )
                                            if (placeIsAuto) {
                                                Text(
                                                    " (aus erstem Foto)",
                                                    color = ink,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontStyle = FontStyle.Italic,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        OutlinedTextField(
                                            value = bodyDraft,
                                            onValueChange = {
                                                bodyDraft = it
                                                viewModel.updateEntryText(entry.id, titleDraft, it)
                                            },
                                            label = { Text("Tagebuch", color = ink) },
                                            minLines = 4,
                                            textStyle = MaterialTheme.typography.bodyMedium,
                                            colors = fieldColors,
                                            modifier = Modifier.fillMaxWidth(),
                                        )

                                        // Angehängte Fotos direkt im Eintrag zeigen.
                                        val entryPhotos = photos.filter {
                                            it.entryId == entry.id
                                        }
                                        if (entryPhotos.isNotEmpty()) {
                                            Spacer(Modifier.height(10.dp))
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState()),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                entryPhotos.forEach { photo ->
                                                    val bitmap = photoBitmap(photo.id)
                                                    Box(
                                                        modifier = Modifier
                                                            .size(84.dp)
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
                                                                contentDescription =
                                                                    photo.caption.ifBlank { "Foto" },
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier.fillMaxSize(),
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        Spacer(Modifier.height(10.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            InkTextButton(onClick = { onAddPhotos(entry.id) }) {
                                                Text("Fotos hinzufügen")
                                            }
                                            InkTextButton(onClick = {
                                                viewModel.deleteEntry(entry.id)
                                                onClose()
                                            }) { Text("Löschen") }
                                        }
                                        if (childStops.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            Text("Pins im Eintrag", fontWeight = FontWeight.Bold, color = ink)
                                            childStops.forEach { stop ->
                                                InkTextButton(onClick = {
                                                    onOpenElement("stop-${stop.id}")
                                                }) {
                                                    Text(stop.title.ifBlank { "Pin" })
                                                }
                                            }
                                        }
                                    }

                                    if (placePickerOpen) {
                                        AlertDialog(
                                            onDismissRequest = { placePickerOpen = false },
                                            title = { Text("Ort auswählen") },
                                            text = {
                                                Column(Modifier.verticalScroll(rememberScrollState())) {
                                                    stops.forEach { stop ->
                                                        TextButton(onClick = {
                                                            viewModel.assignEntryToStop(entry.id, stop.id)
                                                            placePickerOpen = false
                                                        }) {
                                                            Text(stop.title.ifBlank { "Pin" })
                                                        }
                                                    }
                                                    TextButton(onClick = {
                                                        viewModel.beginEntryPlacement(entry.id)
                                                        placePickerOpen = false
                                                        onClose()
                                                    }) {
                                                        Text("Neuen Pin auf der Karte setzen")
                                                    }
                                                }
                                            },
                                            confirmButton = {
                                                TextButton(onClick = { placePickerOpen = false }) {
                                                    Text("Abbrechen")
                                                }
                                            },
                                        )
                                    }
                                }
                            }

                            else -> MissingElement(onClose)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingElement(onClose: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
