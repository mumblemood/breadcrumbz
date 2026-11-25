package de.breadcrumbz.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

// Die alte Reisekarte gibt es nur hell: Papier und Tinte sehen im
// Dunkelmodus nicht mehr nach Karte aus. Feste Farben in Zeitleiste und
// Notizbuch passen dadurch auch überall zum Rest der App.
private val PaperColors = lightColorScheme(
    primary = Ink,
    onPrimary = PaperCard,
    primaryContainer = Parchment,
    onPrimaryContainer = Ink,
    secondary = Copper,
    onSecondary = PaperCard,
    secondaryContainer = Parchment,
    onSecondaryContainer = Ink,
    tertiary = RiverBlue,
    onTertiary = PaperCard,
    error = SealRed,
    onError = PaperCard,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = PaperCard,
    onSurfaceVariant = Sepia,
    surfaceTint = Paper,
    outline = Ink,
    outlineVariant = ParchmentEdge,
    surfaceBright = PaperCard,
    surfaceDim = Parchment,
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = PaperCard,
    surfaceContainer = PaperCard,
    surfaceContainerHigh = PaperCard,
    surfaceContainerHighest = Parchment,
)

@Composable
fun BreadcrumbzTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = PaperColors,
        typography = BreadcrumbzTypography,
        content = content,
    )
}
