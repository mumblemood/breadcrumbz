package de.breadcrumbz.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.breadcrumbz.app.R
import de.breadcrumbz.app.domain.model.TransportMode

/** Gezeichnetes Strich-Symbol je Verkehrsmittel – ersetzt die Emoji. */
@DrawableRes
fun TransportMode.iconRes(): Int = when (this) {
    TransportMode.WALK -> R.drawable.ic_mode_walk
    TransportMode.BIKE -> R.drawable.ic_mode_bike
    TransportMode.KAYAK -> R.drawable.ic_mode_kayak
    TransportMode.CAR -> R.drawable.ic_mode_car
    TransportMode.TRAIN -> R.drawable.ic_mode_train
    TransportMode.PLANE -> R.drawable.ic_mode_plane
    TransportMode.CAMPER -> R.drawable.ic_mode_camper
}

@Composable
fun TransportIcon(
    mode: TransportMode,
    modifier: Modifier = Modifier,
    tint: Color = Ink,
    size: Dp = 22.dp,
) {
    Icon(
        painter = painterResource(mode.iconRes()),
        contentDescription = mode.label,
        tint = tint,
        modifier = modifier.size(size),
    )
}

/** Runder Papierknopf mit Tintenrand – für Knöpfe auf der Karte. */
@Composable
fun PaperIconButton(
    onClick: () -> Unit,
    @DrawableRes icon: Int,
    contentDescription: String,
    modifier: Modifier = Modifier,
    keepIconColors: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .background(PaperCard, CircleShape)
            .border(2.dp, Ink, CircleShape),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = if (keepIconColors) Color.Unspecified else Ink,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Pop-up als Notizzettel: Papier, Tintenrand und ein Streifen Klebeband.
 * Gleiche Parameter wie AlertDialog, damit alle Dialoge gleich aussehen.
 */
@Composable
fun PaperAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = modifier
                .padding(horizontal = 24.dp, vertical = 32.dp)
                .widthIn(min = 280.dp, max = 560.dp),
        ) {
            Column(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .shadow(10.dp, RoundedCornerShape(18.dp))
                    .background(PaperCard, RoundedCornerShape(18.dp))
                    .border(2.dp, Ink, RoundedCornerShape(18.dp))
                    .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 12.dp),
            ) {
                CompositionLocalProvider(LocalContentColor provides Ink) {
                    if (title != null) {
                        ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                            title()
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (text != null) {
                        // Langer Inhalt schrumpft und scrollt, die Knöpfe bleiben sichtbar.
                        Box(modifier = Modifier.weight(1f, fill = false)) {
                            ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                                text()
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
            // Klebeband oben in der Mitte – liegt über der Kante des Zettels.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-2).dp)
                    .rotate(2f)
                    .size(width = 76.dp, height = 20.dp)
                    .background(Tape),
            )
        }
    }
}
