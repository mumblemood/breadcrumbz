package de.breadcrumbz.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Petrol700,
    onPrimary = Cream100,
    primaryContainer = Amber400,
    onPrimaryContainer = Petrol900,
    secondary = Amber400,
    onSecondary = Petrol900,
    surface = Cream100,
    onSurface = Petrol900,
    background = Cream100,
    onBackground = Petrol900,
)

private val DarkColors = darkColorScheme(
    primary = Amber400,
    onPrimary = Petrol900,
    primaryContainer = Petrol700,
    onPrimaryContainer = Cream100,
    secondary = Amber400,
    onSecondary = Petrol900,
    surface = Petrol900,
    onSurface = Cream100,
    background = Petrol900,
    onBackground = Cream100,
)

@Composable
fun BreadcrumbzTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
