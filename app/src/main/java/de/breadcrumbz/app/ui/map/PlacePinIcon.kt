package de.breadcrumbz.app.ui.map

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import de.breadcrumbz.app.R

/**
 * Einfarbiges Pin-Symbol vor Ortsnamen. Die Größe wird von der Textgröße
 * übergeben, damit Symbol und Schrift zusammenpassen.
 */
@Composable
fun PlacePinIcon(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Icon(
        painter = painterResource(R.drawable.ic_stop_marker),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}
