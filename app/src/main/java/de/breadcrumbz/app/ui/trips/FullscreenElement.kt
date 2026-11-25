package de.breadcrumbz.app.ui.trips

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import de.breadcrumbz.app.data.photo.PhotoImporter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import de.breadcrumbz.app.R
import de.breadcrumbz.app.domain.model.Entry
import de.breadcrumbz.app.domain.model.Photo
import de.breadcrumbz.app.domain.model.Stop
import de.breadcrumbz.app.state.TripMapViewModel
import de.breadcrumbz.app.ui.map.PlacePinIcon
import de.breadcrumbz.app.ui.theme.BodyFont
import de.breadcrumbz.app.ui.theme.HandFont
import de.breadcrumbz.app.ui.theme.PaperAlertDialog
import de.breadcrumbz.app.ui.theme.PolaroidWhite
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Feste Papier-Optik: Diese Farben gelten unabhängig vom Systemthema (hell/dunkel).
private val paper = Color(0xFFF6EFE1)
private val leftPage = Color(0xFFEFE4CF)
// Die Seiten hinter der aktuellen Seite – leicht dunkler, damit man sie sieht.
private val pageBack = Color(0xFFE8D9BF)
private val pageEdge = Color(0xFFCDBB9C)

private val ink = Color(0xFF3B2B20)
private val frame = Color(0xFFDCCDB4)
private val notebookTab = Color(0xFF2E6B6B)
private val marginLine = Color(0xFFB8782F)
// Handschrift aus der App selbst (Caveat) – sieht auf allen Geräten gleich aus.
private val comic = HandFont
// Ledereinband und Lesebändchen des Notizbuchs.
private val leather = Color(0xFF5A3E2B)
private val leatherDark = Color(0xFF3E2A1D)

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

/** Kleines Polaroid mit überlappendem Plus – Taste für neue Fotos im Eintrag. */
@Composable
private fun AddPhotoIcon() {
    // Etwas größer als das Polaroid: das Plus bleibt vollständig sichtbar.
    Box(
        modifier = Modifier.size(38.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Weißer Papierrand, leicht gedreht, mit Bildausschnitt und schmalem Streifen.
        Box(
            modifier = Modifier
                .size(width = 23.dp, height = 27.dp)
                .offset(x = (-3).dp, y = 2.dp)
                .graphicsLayer(rotationZ = -9f)
                .background(paper, RoundedCornerShape(2.dp))
                .border(1.dp, pageEdge, RoundedCornerShape(2.dp))
                .padding(3.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(ink.copy(alpha = 0.6f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(pageEdge),
            )
        }
        // Das Plus liegt oben rechts über dem Polaroid, aber innerhalb der Taste.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(18.dp)
                .clip(CircleShape)
                .background(frame)
                .border(1.dp, ink, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Fotos hinzufügen",
                tint = ink,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun rememberBigImage(viewModel: TripMapViewModel, fileName: String): ImageBitmap? {
    // Im Hintergrund und auf Bildschirmgröße verkleinert laden: ein volles
    // 50-MP-Foto würde sonst ruckeln oder die App abstürzen lassen.
    val image by produceState<ImageBitmap?>(initialValue = null, fileName) {
        value = withContext(Dispatchers.IO) {
            PhotoImporter.decode(viewModel.photoPath(fileName), maxSide = BIG_IMAGE_MAX_SIDE)
                ?.asImageBitmap()
        }
    }
    return image
}

private const val BIG_IMAGE_MAX_SIDE = 2048

private fun dateText(time: Long?): String =
    time?.let { SimpleDateFormat("dd.MM.yyyy · HH:mm", Locale.GERMANY).format(Date(it)) }
        ?: "Ohne Zeit"

/** Zeit und Ort: bewusst klein, damit der Text des Eintrags im Vordergrund steht. */
private val MetaTextStyle = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, lineHeight = 13.sp)

/** Zeit und Ort stehen klein unter dem Bild und haben ggf. einen Pin davor. */
@Composable
private fun SmallMeta(text: String, withPin: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withPin) {
            PlacePinIcon(tint = ink, size = 14.dp)
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = ink, fontSize = 10.sp, lineHeight = 12.sp)
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
    placeText: String,
    viewModel: TripMapViewModel,
    scale: Float,
    pan: Offset,
    /** Mit zwei Fingern gedreht – wie ein Foto in der Hand. */
    rotation: Float = 0f,
    onOpenOriginal: () -> Unit,
) {
    var editingTitle by remember(photo.id) { mutableStateOf(false) }
    // Nur an das Foto gebunden: jede Speicherung würde sonst die Eingabe zurücksetzen.
    var titleDraft by remember(photo.id) { mutableStateOf(photo.caption) }
    DisposableEffect(photo.id) {
        onDispose { viewModel.flushPendingTexts() }
    }
    val focusRequester = remember { FocusRequester() }
    // Titel sofort fokussieren, damit Tippen ihn wirklich bearbeitbar macht.
    LaunchedEffect(editingTitle) {
        if (editingTitle) focusRequester.requestFocus()
    }
    // Bei jedem Öffnen ein neuer, kleiner Schrägwinkel.
    val tilt = remember(photo.id) { (-9..9).random().toFloat() }
    val titleIsEmpty = titleDraft.isBlank()
    // Ortsnamen wie überall: „Stadt, Staat". Koordinaten erscheinen nie.
    var resolvedPlace by remember(photo.id, placeText) {
        mutableStateOf(placeText)
    }
    LaunchedEffect(photo.id, photo.latitude, photo.longitude, placeText) {
        val lat = photo.latitude
        val lon = photo.longitude
        if (lat != null && lon != null &&
            (placeText == "Ort wird geladen" || placeText == "Ohne Ort")
        ) {
            resolvedPlace = viewModel.placeName(lat, lon) ?: "Ohne Ort"
        } else {
            resolvedPlace = placeText
        }
    }

    Column(
        modifier = Modifier
            .width(300.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                rotationZ = tilt + rotation,
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
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
        } else {
            Text(
                text = titleDraft.ifBlank { "Titel" },
                color = if (titleIsEmpty) ink.copy(alpha = 0.55f) else ink,
                fontFamily = comic,
                fontSize = 24.sp,
                fontStyle = if (titleIsEmpty) FontStyle.Italic else FontStyle.Normal,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { editingTitle = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        // Ort und Zeitpunkt ganz unten, in einer Zeile und bewusst blasser
        // als der Titel – sie sollen nicht vom Foto ablenken.
        val metaInk = ink.copy(alpha = 0.55f)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_clock),
                contentDescription = "Zeitpunkt",
                tint = metaInk,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = dateText(photo.capturedAt ?: photo.createdAt),
                color = metaInk,
                style = MaterialTheme.typography.labelSmall,
                fontStyle = if (photo.capturedAt == null) {
                    FontStyle.Italic
                } else {
                    FontStyle.Normal
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(12.dp))
            PlacePinIcon(tint = metaInk, size = 13.dp)
            Spacer(Modifier.width(4.dp))
            Text(
                text = resolvedPlace,
                color = metaInk,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(4.dp))
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
    val entryOnly = elementKey.startsWith("entry-")
    val detailPhoto = if (photoOnly) {
        photos.firstOrNull { it.id == elementKey.removePrefix("photo-") }
    } else {
        null
    }
    // Gesten wirken über den ganzen Bildschirm, nicht nur auf einem Kartenausschnitt.
    var photoScale by remember(detailPhoto?.id) { mutableStateOf(1f) }
    var photoPan by remember(detailPhoto?.id) { mutableStateOf(Offset.Zero) }
    var photoRotation by remember(detailPhoto?.id) { mutableStateOf(0f) }
    // Blättern im Notizbuch: vorheriger und nächster Eintrag der Reise.
    val pageFlip = remember { Animatable(0f) }
    val flipScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val orderedEntries = remember(entries) {
        entries.sortedWith(compareBy<Entry>({ it.entryAt ?: Long.MAX_VALUE }, { it.createdAt }))
    }
    val currentEntryIndex = if (entryOnly) {
        orderedEntries.indexOfFirst { it.id == elementKey.removePrefix("entry-") }
    } else {
        -1
    }
    val nextEntryKey = orderedEntries.getOrNull(currentEntryIndex + 1)
        ?.takeIf { currentEntryIndex >= 0 }?.let { "entry-${it.id}" }
    val previousEntryKey = orderedEntries.getOrNull(currentEntryIndex - 1)
        ?.let { "entry-${it.id}" }

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
                .then(
                    if (photoOnly || entryOnly) Modifier else Modifier.padding(20.dp),
                )
                .clickable { onClose() }
                .then(
                    if (photoOnly) {
                        Modifier.pointerInput(detailPhoto?.id) {
                            detectTransformGestures { _, panChange, zoom, turn ->
                                photoScale = (photoScale * zoom).coerceIn(1f, 5f)
                                photoRotation += turn
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
            // Das aufgeschlagene Buch liegt links am Rand und vertikal in der Mitte.
            contentAlignment = when {
                photoOnly -> Alignment.Center
                entryOnly -> Alignment.CenterStart
                else -> Alignment.TopStart
            },
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
                        when {
                            photoOnly -> Modifier.fillMaxSize()
                            entryOnly -> Modifier
                                .fillMaxWidth()
                                // Groß: das Notizbuch nimmt fast den ganzen Bildschirm ein.
                                .fillMaxHeight(0.9f)
                                .graphicsLayer {
                                    // Umblättern: die Seite dreht sich um den Falz.
                                    rotationY = pageFlip.value
                                    transformOrigin = TransformOrigin(0f, 0.5f)
                                    cameraDistance = 14f * this.density
                                }
                                .pointerInput(elementKey) {
                                    var total = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { total = 0f },
                                        onHorizontalDrag = { change, amount ->
                                            total += amount
                                            change.consume()
                                        },
                                        onDragEnd = {
                                            val target = when {
                                                total < -90f -> nextEntryKey
                                                total > 90f -> previousEntryKey
                                                else -> null
                                            }
                                            if (target != null) {
                                                val forward = total < 0f
                                                flipScope.launch {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.TextHandleMove,
                                                    )
                                                    pageFlip.animateTo(
                                                        if (forward) -90f else 90f,
                                                        tween(230, easing = FastOutLinearInEasing),
                                                    )
                                                    onOpenElement(target)
                                                    pageFlip.snapTo(if (forward) 90f else -90f)
                                                    pageFlip.animateTo(
                                                        0f,
                                                        tween(320, easing = LinearOutSlowInEasing),
                                                    )
                                                }
                                            }
                                        },
                                    )
                                }
                                // Ledereinband hinter allen Seiten, Lesebändchen
                                // oben rechts über der Seite.
                                .drawWithContent {
                                    drawRoundRect(
                                        color = leather,
                                        topLeft = Offset(-12.dp.toPx(), -8.dp.toPx()),
                                        size = Size(
                                            size.width + 10.dp.toPx(),
                                            size.height + 14.dp.toPx(),
                                        ),
                                        cornerRadius = CornerRadius(12.dp.toPx()),
                                    )
                                    drawRoundRect(
                                        color = leatherDark,
                                        topLeft = Offset(-12.dp.toPx(), -8.dp.toPx()),
                                        size = Size(
                                            size.width + 10.dp.toPx(),
                                            size.height + 14.dp.toPx(),
                                        ),
                                        cornerRadius = CornerRadius(12.dp.toPx()),
                                        style = Stroke(width = 2.dp.toPx()),
                                    )
                                    drawContent()
                                    val ribbonWidth = 14.dp.toPx()
                                    val ribbonLeft = size.width - 20.dp.toPx() -
                                        44.dp.toPx()
                                    val ribbonBottom = 64.dp.toPx()
                                    val notch = 6.dp.toPx()
                                    val ribbon = Path().apply {
                                        moveTo(ribbonLeft, -8.dp.toPx())
                                        lineTo(ribbonLeft + ribbonWidth, -8.dp.toPx())
                                        lineTo(ribbonLeft + ribbonWidth, ribbonBottom)
                                        lineTo(ribbonLeft + ribbonWidth / 2f, ribbonBottom - notch)
                                        lineTo(ribbonLeft, ribbonBottom)
                                        close()
                                    }
                                    drawPath(ribbon, notebookTab)
                                }
                                // Rechts und unten bleibt Platz für die Seiten
                                // dahinter – das Buch läuft nicht an den Rand.
                                .padding(end = 26.dp, bottom = 18.dp)
                                .drawBehind {
                                    // Notizbuch: Seitenstapel, Bindungslöcher,
                                    // Falz und feine Linien auf der rechten Seite.
                                    val strip = 36.dp.toPx()
                                    val fold = 5.dp.toPx()
                                    val step = 7.dp.toPx()
                                    // Drei weitere Seiten liegen deutlich versetzt hinten.
                                    for (layer in 3 downTo 1) {
                                        val dx = layer * step
                                        val dy = layer * step * 0.6f
                                        drawRect(
                                            color = pageEdge,
                                            topLeft = Offset(dx, dy),
                                            size = Size(size.width - dx, size.height - dy),
                                        )
                                        drawRect(
                                            color = pageBack,
                                            topLeft = Offset(dx - 1.5f, dy - 1.5f),
                                            size = Size(size.width - dx, size.height - dy),
                                        )
                                        // Feine Kante jeder Seite.
                                        drawRect(
                                            color = ink.copy(alpha = 0.22f),
                                            topLeft = Offset(dx - 1.5f, dy - 1.5f),
                                            size = Size(size.width - dx, size.height - dy),
                                            style = Stroke(width = 1.dp.toPx()),
                                        )
                                    }
                                    // Linke Seite: sanft vom Papierton zum Falz hin abgedunkelt.
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(leftPage, leftPage, paper),
                                            startX = 0f,
                                            endX = strip,
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(strip, size.height),
                                    )
                                    // Feine Linierung und eine schmale Randlinie.
                                    val marginX = strip + fold + 14.dp.toPx()
                                    val lineStartX = marginX + 4.dp.toPx()
                                    val lineEndX = size.width - 8.dp.toPx()
                                    var lineY = 104.dp.toPx()
                                    while (lineY < size.height - 8.dp.toPx()) {
                                        drawLine(
                                            color = frame.copy(alpha = 0.55f),
                                            start = Offset(lineStartX, lineY),
                                            end = Offset(lineEndX, lineY),
                                            strokeWidth = 1.dp.toPx(),
                                        )
                                        lineY += 30.dp.toPx()
                                    }
                                    // Drei Bindungslöcher in der linken Lasche.
                                    val holeRadius = 3.dp.toPx()
                                    listOf(84.dp.toPx(), 168.dp.toPx(), 252.dp.toPx())
                                        .forEach { holeY ->
                                            if (holeY < size.height - 12.dp.toPx()) {
                                                val center = Offset(strip / 2f, holeY)
                                                drawCircle(
                                                    color = pageEdge,
                                                    radius = holeRadius,
                                                    center = center,
                                                )
                                                drawCircle(
                                                    color = leftPage,
                                                    radius = holeRadius * 0.62f,
                                                    center = center,
                                                )
                                            }
                                        }
                                    // Sehr weicher Schatten rechts am Falz.
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                ink.copy(alpha = 0.28f),
                                                ink.copy(alpha = 0.08f),
                                                Color.Transparent,
                                            ),
                                            startX = strip - 6.dp.toPx(),
                                            endX = strip + 30.dp.toPx(),
                                        ),
                                        topLeft = Offset(strip - 6.dp.toPx(), 0f),
                                        size = Size(36.dp.toPx(), size.height),
                                    )
                                    drawLine(
                                        color = frame,
                                        start = Offset(strip, 0f),
                                        end = Offset(strip, size.height),
                                        strokeWidth = fold,
                                    )
                                    drawLine(
                                        color = ink.copy(alpha = 0.35f),
                                        start = Offset(strip + fold, 0f),
                                        end = Offset(strip + fold, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    drawLine(
                                        color = marginLine.copy(alpha = 0.38f),
                                        start = Offset(marginX, 0f),
                                        end = Offset(marginX, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    // Feine Unterkante der rechten Seite.
                                    drawLine(
                                        color = pageEdge,
                                        start = Offset(strip, size.height - 1.dp.toPx()),
                                        end = Offset(size.width, size.height - 1.dp.toPx()),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                }
                            else -> Modifier.fillMaxWidth().heightIn(max = 620.dp)
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
                                if (photoOnly) Modifier.fillMaxHeight() else Modifier.verticalScroll(
                                    rememberScrollState(),
                                ),
                            )
                            .then(
                                when {
                                    photoOnly -> Modifier
                                    entryOnly -> Modifier.padding(
                                        // Text beginnt rechts der Randlinie des Notizbuchs.
                                        start = 62.dp, end = 16.dp,
                                        top = 10.dp, bottom = 10.dp,
                                    )
                                    else -> Modifier.padding(
                                        horizontal = 16.dp, vertical = 10.dp,
                                    )
                                },
                            ),
                        verticalArrangement = if (photoOnly) {
                            Arrangement.Center
                        } else {
                            Arrangement.Top
                        },
                        horizontalAlignment = if (photoOnly) {
                            Alignment.CenterHorizontally
                        } else {
                            Alignment.Start
                        },
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
                                    val photoStopTitle = currentPhoto.stopId?.let { stopId ->
                                        stops.firstOrNull { it.id == stopId }?.title
                                    }
                                    val photoPlaceText = when {
                                        !photoStopTitle.isNullOrBlank() -> photoStopTitle
                                        currentPhoto.latitude != null &&
                                            currentPhoto.longitude != null -> "Ort wird geladen"
                                        else -> "Ohne Ort"
                                    }
                                    PhotoPolaroidDetail(
                                        photo = currentPhoto,
                                        bitmap = bitmap,
                                        placeText = photoPlaceText,
                                        viewModel = viewModel,
                                        scale = photoScale,
                                        pan = photoPan,
                                        rotation = photoRotation,
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
                                    fun pickStopDateTime() {
                                        val start = Calendar.getInstance().apply {
                                            timeInMillis = stop.visitedAt
                                                ?: System.currentTimeMillis()
                                        }
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                val withDate = Calendar.getInstance().apply {
                                                    timeInMillis = stop.visitedAt
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
                                                        viewModel.setStopVisitedAt(stop.id, chosen)
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
                                        if (stop.entryId == null) {
                                            // Alleinstehender Pin: Datum + Uhrzeit direkt
                                            // bearbeiten, ohne Umweg über einen Eintrag.
                                            Row(
                                                modifier = Modifier.clickable {
                                                    pickStopDateTime()
                                                },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_clock),
                                                    contentDescription = "Datum und Uhrzeit",
                                                    tint = ink,
                                                    modifier = Modifier.size(
                                                        with(density) {
                                                            MetaTextStyle.fontSize.toDp()
                                                        },
                                                    ),
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    text = if (stop.visitedAt == null) {
                                                        "Datum + Uhrzeit"
                                                    } else {
                                                        dateText(stop.visitedAt)
                                                    },
                                                    color = if (stop.visitedAt == null) {
                                                        ink.copy(alpha = 0.45f)
                                                    } else {
                                                        ink
                                                    },
                                                    style = MetaTextStyle,
                                                    fontStyle = if (stop.visitedAt == null) {
                                                        FontStyle.Italic
                                                    } else {
                                                        FontStyle.Normal
                                                    },
                                                )
                                            }
                                        } else if (stop.visitedAt != null) {
                                            SmallMeta(dateText(stop.visitedAt))
                                        }
                                        // Pins haben kein Verkehrsmittel. Das
                                        // wird ausschließlich am Reiseabschnitt
                                        // eingestellt (Tippen auf die Linie).
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
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (stop.entryId == null && linkedEntries.isEmpty()) {
                                                // Notizbuch-Symbol: direkt ein leerer tbe
                                                // an diesem Pin anlegen und öffnen.
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(frame)
                                                        .border(1.dp, ink, CircleShape)
                                                        .clickable {
                                                            onAddEntry(stop.id)
                                                        },
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Text(
                                                        "📝",
                                                        fontSize = 19.sp,
                                                    )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            IconButton(onClick = { confirmDeletePin = true }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Pin löschen",
                                                    tint = ink,
                                                )
                                            }
                                        }
                                        if (confirmDeletePin) {
                                            PaperAlertDialog(
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
                                    // Beim Schließen des tbe wird der Text sofort gespeichert.
                                    DisposableEffect(entry.id) {
                                        onDispose { viewModel.flushPendingTexts() }
                                    }
                                    var placePickerOpen by remember(entry.id) {
                                        mutableStateOf(false)
                                    }
                                    val stopTitle = entry.stopId
                                        ?.let { target -> stops.firstOrNull { it.id == target }?.title }
                                    val timeIsAuto = entry.timeFromPhoto && entry.entryAt != null
                                    val placeIsAuto = entry.placeFromPhoto && entry.stopId != null
                                    // Tagebuch-Schrift auf linierter Seite;
                                    // Bindung und Register kommen direkt darunter.
                                    // Text in Nunito, Titel in Handschrift – wie überall.
                                    val diaryStyle = MaterialTheme.typography.bodyLarge.copy(
                                        color = ink,
                                        fontFamily = BodyFont,
                                        fontSize = 17.sp,
                                        lineHeight = 28.sp,
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

                                    // Der Falz wird außen am Buch gezeichnet (siehe Card).
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        // Kleines Register, damit die Seite wie ein
                                        // echtes Notizbuch wirkt.
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    notebookTab,
                                                    RoundedCornerShape(4.dp),
                                                )
                                                .padding(horizontal = 8.dp, vertical = 3.dp),
                                        ) {
                                            Text(
                                                text = "TAGEBUCH",
                                                color = paper,
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                ),
                                            )
                                        }
                                        Spacer(Modifier.height(10.dp))
                                        BasicTextField(
                                            value = titleDraft,
                                            onValueChange = {
                                                titleDraft = it
                                                viewModel.updateEntryText(entry.id, it, bodyDraft)
                                            },
                                            singleLine = true,
                                            textStyle = diaryStyle.copy(
                                                fontFamily = comic,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 30.sp,
                                                lineHeight = 34.sp,
                                            ),
                                            modifier = Modifier.fillMaxWidth(),
                                            decorationBox = { innerTextField ->
                                                if (titleDraft.isEmpty()) {
                                                    Text(
                                                        text = "Titel",
                                                        color = ink.copy(alpha = 0.45f),
                                                        fontStyle = FontStyle.Italic,
                                                        style = diaryStyle.copy(
                                                            fontFamily = comic,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 30.sp,
                                                            lineHeight = 34.sp,
                                                        ),
                                                    )
                                                }
                                                innerTextField()
                                            },
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        // Datum und Pin direkt unterm Titel.
                                        Row(
                                            modifier = Modifier.clickable { pickDateTime() },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_clock),
                                                contentDescription = "Uhrzeit",
                                                tint = ink,
                                                modifier = Modifier.size(
                                                    with(density) {
                                                        MetaTextStyle.fontSize.toDp()
                                                    },
                                                ),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (entry.entryAt == null) {
                                                    "..."
                                                } else {
                                                    dateText(entry.entryAt)
                                                },
                                                color = if (entry.entryAt == null) {
                                                    ink.copy(alpha = 0.45f)
                                                } else {
                                                    ink
                                                },
                                                style = MetaTextStyle,
                                                fontStyle = if (entry.entryAt == null || timeIsAuto) {
                                                    FontStyle.Italic
                                                } else {
                                                    FontStyle.Normal
                                                },
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.clickable { placePickerOpen = true },
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            PlacePinIcon(
                                                tint = ink,
                                                size = with(density) {
                                                    MetaTextStyle.fontSize.toDp()
                                                },
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(
                                                text = if (stopTitle == null) "..." else stopTitle,
                                                color = if (stopTitle == null) {
                                                    ink.copy(alpha = 0.45f)
                                                } else {
                                                    ink
                                                },
                                                style = MetaTextStyle,
                                                fontStyle = if (stopTitle == null || placeIsAuto) {
                                                    FontStyle.Italic
                                                } else {
                                                    FontStyle.Normal
                                                },
                                            )
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
                                            decorationBox = { innerTextField ->
                                                if (bodyDraft.isEmpty()) {
                                                    Text(
                                                        text = "Tagebuch",
                                                        color = ink.copy(alpha = 0.45f),
                                                        fontStyle = FontStyle.Italic,
                                                        style = diaryStyle,
                                                    )
                                                }
                                                innerTextField()
                                            },
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
                                                entryPhotos.forEachIndexed { index, photo ->
                                                    val bitmap = photoBitmap(photo.id)
                                                    // Eingeklebtes Polaroid.
                                                    Box(
                                                        modifier = Modifier
                                                            .padding(vertical = 10.dp, horizontal = 2.dp)
                                                            .graphicsLayer(
                                                                rotationZ = if (index % 2 == 0) -3f else 3f,
                                                            )
                                                            .background(PolaroidWhite)
                                                            .border(1.dp, pageEdge)
                                                            .clickable {
                                                                onOpenElement("photo-${photo.id}")
                                                            }
                                                            .padding(
                                                                start = 7.dp,
                                                                end = 7.dp,
                                                                top = 7.dp,
                                                                bottom = 20.dp,
                                                            ),
                                                    ) {
                                                        // Groß und fast im Originalformat.
                                                        val ratio = bitmap?.let {
                                                            it.width.toFloat() / it.height.coerceAtLeast(1)
                                                        }?.coerceIn(0.56f, 1.78f) ?: 1f
                                                        Box(
                                                            modifier = Modifier
                                                                .size(width = 150.dp * ratio, height = 150.dp)
                                                                .background(frame),
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
                                        }

                                        Spacer(Modifier.height(12.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            // Keine IconButton-Kreismaske: sie schnitt
                                            // das Polaroid und das Plus an den Ecken ab.
                                            Box(
                                                modifier = Modifier
                                                    .size(42.dp)
                                                    .clickable { onAddPhotos(entry.id) },
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                AddPhotoIcon()
                                            }
                                            IconButton(onClick = {
                                                viewModel.deleteEntry(entry.id)
                                                onClose()
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Eintrag löschen",
                                                    tint = ink,
                                                )
                                            }
                                        }
                                    }

                                    if (placePickerOpen) {
                                        PaperAlertDialog(
                                            onDismissRequest = { placePickerOpen = false },
                                            title = { Text("Pin auswählen") },
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
