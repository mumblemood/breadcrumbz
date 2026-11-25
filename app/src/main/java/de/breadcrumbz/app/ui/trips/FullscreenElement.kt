package de.breadcrumbz.app.ui.trips

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RectangleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.core.content.FileProvider
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.domain.model.TransportMode
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.PlacePinIcon
import java.io.File
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
 * Das Polaroid selbst: Der Rand wird gemeinsam mit dem Bild verschoben und gezoomt, das Bild
 * bleibt auf ein normales 4:3-Format zugeschnitten. Ein Klick auf den Titel
 * schaltet ihn in den Bearbeitungsmodus, ein Klick auf das Bild öffnet das
 * Original im Android-Bildviewer.
 */
@Composable
private fun PhotoPolaroidDetail(
    photo: Photo,
    bitmap: ImageBitmap?,
    viewModel: TripMapViewModel,
    scale: Float,
    pan: Offset,
    onOpenOriginal: () -> Unit,
) {
    var editingTitle by remember(photo.id) { mutableStateOf(false) }
    var titleDraft by remember(photo.id, photo.caption) { mutableStateOf(photo.caption) }
    val focusRequester = remember { FocusRequester() }
    // Titel sofort fokussieren, damit Tippen ihn wirklich bearbeitbar macht.
    LaunchedEffect(editingTitle) {
        if (editingTitle) focusRequester.requestFocus()
    }
    // Bei jedem Öffnen ein neuer, kleiner Schrägwinkel.
    val tilt = remember(photo.id) { (-9..9).random().toFloat() }
    val titleIsEmpty = photo.caption.isBlank()

    Column(
        modifier = Modifier
            .width(300.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationZ = tilt,
                translationX = pan.x,
                translationY = pan.y,
            )
            .clip(RoundedCornerShape(4.dp))
            .background(paper)
            .border(3.dp, ink, RoundedCornerShape(4.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFFE7DAC2))
                .clickable {
                    if (editingTitle) {
                        editingTitle = false
                    } else {
                        onOpenOriginal()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Original anzeigen",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("Foto konnte nicht geladen werden", color = ink)
            }
        }
        Spacer(Modifier.height(12.dp))
        if (editingTitle) {
            BasicTextField(
                value = titleDraft,
                onValueChange = {
                    titleDraft = it
                    viewModel.updatePhotoCaption(photo.id, it)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleMedium.copy(
                    color = ink,
                    fontFamily = comic,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        } else {
            Text(
                text = photo.caption.ifBlank { "Titel" },
                color = if (titleIsEmpty) ink.copy(alpha = 0.55f) else ink,
                fontFamily = comic,
                fontStyle = if (titleIsEmpty) FontStyle.Italic else FontStyle.Normal,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { editingTitle = true },
            )
        }
        Spacer(Modifier.height(6.dp))
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
    onAddEntry: (String) -> Unit,
    onAdoptPhotoLocation: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // Die Foto-Detailansicht zeigt wirklich nur das Polaroid – kein Extra-Kartenform.
    val photoOnly = elementKey.startsWith("photo-")
    val detailPhoto = if (photoOnly) {
        photos.firstOrNull { it.id == elementKey.removePrefix("photo-") }
    } else {
        null
    }
    // Gesten wirken über den ganzen Bildschirm, nicht nur auf einem Kartenausschnitt.
    var photoScale by remember(detailPhoto?.id) { mutableStateOf(1f) }
    var photoPan by remember(detailPhoto?.id) { mutableStateOf(Offset.Zero) }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
        ),
    ) {
        // Etwas dunkler dimmen, damit sich die Detailansicht klar abhebt.
        // Muss im Dialog-Fenster selbst gesetzt werden, nicht im Hauptfenster.
        val dialogView = LocalView.current
        val dialogWindow = (dialogView.parent as? DialogWindowProvider)?.window
        if (dialogWindow != null) {
            val params = dialogWindow.attributes
            params.dimAmount = 0.38f
            dialogWindow.attributes = params
            dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (photoOnly) Modifier else Modifier.padding(20.dp))
                .clickable { onClose() }
                .then(
                    if (photoOnly) {
                        Modifier.pointerInput(detailPhoto?.id) {
                            detectTransformGestures { _, panChange, zoom, _ ->
                                photoScale = (photoScale * zoom).coerceIn(1f, 5f)
                                photoPan = Offset(
                                    (photoPan.x + panChange.x)
                                        .coerceIn(-1_200f * photoScale, 1_200f * photoScale),
                                    (photoPan.y + panChange.y)
                                        .coerceIn(-1_600f * photoScale, 1_600f * photoScale),
                                )
                            }
                        }
                    } else {
                        Modifier
                    },
                ),
            // Einträge liegen bündig links oben; das Polaroid bleibt zentriert.
            contentAlignment = if (photoOnly) Alignment.Center else Alignment.TopStart,
        ) {
            Card(
                // Keine runden Ecken mehr – das Detailfenster ist eine flache Fläche.
                shape = RectangleShape,
                colors = CardDefaults.cardColors(
                    containerColor = if (photoOnly) Color.Transparent else paper,
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier
                    .then(
                        if (photoOnly) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxWidth().heightIn(max = 620.dp)
                        },
                    )
                    // Klicks im Kartenbereich nicht nach außen durchreichen.
                    .clickable { if (photoOnly) onClose() },
            ) {
                CompositionLocalProvider(LocalContentColor provides ink) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (photoOnly) Modifier else Modifier.verticalScroll(
                                    rememberScrollState(),
                                ),
                            )
                            .then(
                                if (photoOnly) Modifier else Modifier.padding(
                                    horizontal = 16.dp, vertical = 10.dp,
                                ),
                            ),
                    ) {
                        // Kein Zurück-Knopf: Tippen daneben oder die Back-Taste schließen.
                        when {
                            elementKey.startsWith("photo-") -> {
                                val id = elementKey.removePrefix("photo-")
                                val currentPhoto = photos.firstOrNull { it.id == id }
                                if (currentPhoto == null) {
                                    MissingElement(onClose)
                                } else {
                                    val bitmap = rememberBigImage(viewModel, currentPhoto.fileName)
                                    PhotoPolaroidDetail(
                                        photo = currentPhoto,
                                        bitmap = bitmap,
                                        viewModel = viewModel,
                                        scale = photoScale,
                                        pan = photoPan,
                                        onOpenOriginal = {
                                            val file = File(viewModel.photoPath(currentPhoto.fileName))
                                            val uri = FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.fileprovider",
                                                file,
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                setDataAndType(uri, "image/*")
                                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                            }
                                            context.startActivity(
                                                Intent.createChooser(intent, "Original anzeigen"),
                                            )
                                        },
                                    )
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
                                    var confirmDeletePin by remember(stop.id) {
                                        mutableStateOf(false)
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
                                        Spacer(Modifier.height(12.dp))
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            InkTextButton(onClick = { onAddEntry(stop.id) }) {
                                                Text("+ Eintrag hier")
                                            }
                                            InkTextButton(onClick = { confirmDeletePin = true }) {
                                                Text("Pin löschen")
                                            }
                                        }
                                        if (confirmDeletePin) {
                                            AlertDialog(
                                                onDismissRequest = { confirmDeletePin = false },
                                                title = { Text("Pin löschen?") },
                                                text = {
                                                    Text(
                                                        "Der Pin wird entfernt. Einträge bleiben " +
                                                            "ohne Ort in der Timeline erhalten und " +
                                                            "Fotos kommen zurück in „Ohne Ort“.",
                                                    )
                                                },
                                                confirmButton = {
                                                    TextButton(onClick = {
                                                        viewModel.deleteStop(stop.id)
                                                        viewModel.selectStop(null)
                                                        confirmDeletePin = false
                                                        onClose()
                                                    }) { Text("Löschen") }
                                                },
                                                dismissButton = {
                                                    TextButton(onClick = {
                                                        confirmDeletePin = false
                                                    }) { Text("Abbrechen") }
                                                },
                                            )
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
                                    val stopTitle = entry.stopId
                                        ?.let { target -> stops.firstOrNull { it.id == target }?.title }
                                    val timeIsAuto = entry.timeFromPhoto && entry.entryAt != null
                                    val placeIsAuto = entry.placeFromPhoto && entry.stopId != null
                                    // Tagebuch-Schrift: kein Rahmen, kein Label,
                                    // nur eine feine Bindung links (siehe unten).
                                    val diaryStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = ink,
                                        fontFamily = comic,
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

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            // Buchrücken: eine Linie links, sonst kein Rahmen.
                                            .drawBehind {
                                                val x = 2.dp.toPx()
                                                drawLine(
                                                    color = frame,
                                                    start = Offset(x, 0f),
                                                    end = Offset(x, size.height),
                                                    strokeWidth = 3.dp.toPx(),
                                                )
                                            }
                                            .padding(start = 16.dp),
                                    ) {
                                        BasicTextField(
                                            value = titleDraft,
                                            onValueChange = {
                                                titleDraft = it
                                                viewModel.updateEntryText(entry.id, it, bodyDraft)
                                            },
                                            singleLine = true,
                                            textStyle = diaryStyle.copy(
                                                fontWeight = FontWeight.Bold,
                                            ),
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
                                        BasicTextField(
                                            value = bodyDraft,
                                            onValueChange = {
                                                bodyDraft = it
                                                viewModel.updateEntryText(entry.id, titleDraft, it)
                                            },
                                            minLines = 5,
                                            textStyle = diaryStyle,
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
