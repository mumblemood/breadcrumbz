package de.breadcrumbz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import de.breadcrumbz.app.R

/** Handschrift für Titel (Caveat, SIL Open Font License). */
@OptIn(ExperimentalTextApi::class)
val HandFont = FontFamily(
    Font(
        R.font.caveat,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
    Font(
        R.font.caveat,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

/** Gut lesbare Schrift für alles Kleine (Nunito, SIL Open Font License). */
@OptIn(ExperimentalTextApi::class)
val BodyFont = FontFamily(
    Font(
        R.font.nunito,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.nunito,
        weight = FontWeight.SemiBold,
        variationSettings = FontVariation.Settings(FontVariation.weight(600)),
    ),
    Font(
        R.font.nunito,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)

private val base = Typography()

private fun TextStyle.body() = copy(fontFamily = BodyFont)

private fun TextStyle.hand(size: Int) = copy(
    fontFamily = HandFont,
    fontWeight = FontWeight.Bold,
    fontSize = size.sp,
    lineHeight = (size + 4).sp,
)

/** Schrift A: Caveat für Titel, Nunito für Text, Zahlen und Knöpfe. */
val BreadcrumbzTypography = Typography(
    displayLarge = base.displayLarge.hand(52),
    displayMedium = base.displayMedium.hand(44),
    displaySmall = base.displaySmall.hand(38),
    headlineLarge = base.headlineLarge.hand(36),
    headlineMedium = base.headlineMedium.hand(32),
    headlineSmall = base.headlineSmall.hand(29),
    titleLarge = base.titleLarge.hand(28),
    titleMedium = base.titleMedium.body().copy(fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.body().copy(fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.body(),
    bodyMedium = base.bodyMedium.body(),
    bodySmall = base.bodySmall.body(),
    labelLarge = base.labelLarge.body().copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.body().copy(fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.body().copy(fontWeight = FontWeight.Bold),
)
