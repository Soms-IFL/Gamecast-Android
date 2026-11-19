package com.iflgamecast.app

import android.graphics.Typeface
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.ColorUtils

/*
 * Look and feel: a sports-network style. Near-black header, white cards on a light grey page, one red accent,
 * bold condensed headings, square-ish corners. "My team" swaps the red accent for the team's colour.
 */

/** The default accent (a broadcast red). */
const val DEFAULT_SEED = 0xFFD00000.toInt()

/** Header colour: dark in both light and dark mode. */
val TopBarColor = Color(0xFF0D0D0D)

/** "LIVE" is always red, whatever the accent. */
val LiveRed = Color(0xFFD00000)

/** Corner radius shared by cards. */
val CardShape = RoundedCornerShape(6.dp)

private fun mix(a: Color, b: Color, t: Float) = Color(ColorUtils.blendARGB(a.toArgb(), b.toArgb(), t))

/** Nudges [c] toward black (light page) or white (dark page) until it reads clearly against [bg]. */
private fun accentOn(c: Color, bg: Color, dark: Boolean): Color {
    var x = c
    val target = if (dark) Color.White else Color.Black
    var i = 0
    while (ColorUtils.calculateContrast(x.toArgb(), bg.toArgb()) < 4.5 && i < 12) { x = mix(x, target, 0.1f); i++ }
    return x
}

/** Builds the colour scheme: fixed neutrals plus one accent. */
fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val base = Color(seed)
    return if (!dark) {
        val accent = accentOn(base, Color.White, false)
        lightColorScheme(
            primary = accent, onPrimary = readableOn(accent),
            primaryContainer = mix(accent, Color.White, 0.88f), onPrimaryContainer = mix(accent, Color.Black, 0.6f),
            inversePrimary = mix(base, Color.White, 0.4f),
            secondary = Color(0xFF444444), onSecondary = Color.White,
            secondaryContainer = Color(0xFFE6E6E6), onSecondaryContainer = Color(0xFF111111),
            tertiary = Color(0xFF8A5A00), onTertiary = Color.White,
            tertiaryContainer = Color(0xFFFFE7A3), onTertiaryContainer = Color(0xFF3B2A00),
            background = Color(0xFFF2F2F2), onBackground = Color(0xFF111111),
            surface = Color.White, onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFE9E9E9), onSurfaceVariant = Color(0xFF5F5F5F), surfaceTint = accent,
            inverseSurface = Color(0xFF222222), inverseOnSurface = Color(0xFFF2F2F2),
            error = Color(0xFFC62828), onError = Color.White, errorContainer = Color(0xFFFDE2E2), onErrorContainer = Color(0xFF5A0000),
            outline = Color(0xFF8C8C8C), outlineVariant = Color(0xFFDDDDDD), scrim = Color.Black,
            surfaceBright = Color.White, surfaceDim = Color(0xFFE4E4E4),
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color(0xFFFAFAFA),
            surfaceContainerHigh = Color(0xFFF4F4F4), surfaceContainerHighest = Color(0xFFEBEBEB),
        )
    } else {
        val card = Color(0xFF1A1A1A)
        val accent = accentOn(base, card, true)
        darkColorScheme(
            primary = accent, onPrimary = readableOn(accent),
            primaryContainer = mix(base, Color(0xFF0A0A0A), 0.72f), onPrimaryContainer = mix(accent, Color.White, 0.8f),
            inversePrimary = mix(base, Color.Black, 0.2f),
            secondary = Color(0xFFCFCFCF), onSecondary = Color(0xFF111111),
            secondaryContainer = Color(0xFF333333), onSecondaryContainer = Color(0xFFF0F0F0),
            tertiary = Color(0xFFFFC857), onTertiary = Color(0xFF3B2A00),
            tertiaryContainer = Color(0xFF4A3B00), onTertiaryContainer = Color(0xFFFFE7A3),
            background = Color(0xFF0A0A0A), onBackground = Color(0xFFF2F2F2),
            surface = Color(0xFF0A0A0A), onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF2A2A2A), onSurfaceVariant = Color(0xFFA8A8A8), surfaceTint = accent,
            inverseSurface = Color(0xFFF2F2F2), inverseOnSurface = Color(0xFF222222),
            error = Color(0xFFFF6B6B), onError = Color(0xFF3B0000), errorContainer = Color(0xFF5A1A1A), onErrorContainer = Color(0xFFFFD9D9),
            outline = Color(0xFF6E6E6E), outlineVariant = Color(0xFF303030), scrim = Color.Black,
            surfaceBright = Color(0xFF333333), surfaceDim = Color(0xFF0A0A0A),
            surfaceContainerLowest = Color.Black, surfaceContainerLow = card, surfaceContainer = Color(0xFF1E1E1E),
            surfaceContainerHigh = Color(0xFF262626), surfaceContainerHighest = Color(0xFF2E2E2E),
        )
    }
}

/* ---------------------------------------------------------------- type and shape */

/** Bold condensed face for scores, headings and labels (built into Android, no font files to ship). */
private val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.BOLD))

private fun TextStyle.cond(spacing: Float = 0f) = copy(fontFamily = Condensed, fontWeight = FontWeight.Bold, letterSpacing = spacing.sp)

private fun appTypography(): Typography {
    val d = Typography()
    return d.copy(
        displayLarge = d.displayLarge.cond(), displayMedium = d.displayMedium.cond(), displaySmall = d.displaySmall.cond(),
        headlineLarge = d.headlineLarge.cond(), headlineMedium = d.headlineMedium.cond(), headlineSmall = d.headlineSmall.cond(),
        titleLarge = d.titleLarge.cond(), titleMedium = d.titleMedium.cond(0.2f), titleSmall = d.titleSmall.cond(0.2f),
        labelLarge = d.labelLarge.cond(0.4f), labelMedium = d.labelMedium.cond(0.4f), labelSmall = d.labelSmall.cond(0.4f),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(10.dp),
)

@Composable
fun isDark(mode: String): Boolean = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }

@Composable
fun IflTheme(seed: Int, mode: String, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val scheme = remember(seed, dark) { schemeFromSeed(seed, dark) }
    val type = remember { appTypography() }
    MaterialTheme(colorScheme = scheme, typography = type, shapes = AppShapes, content = content)
}

// Semantic colours that don't exist in the M3 scheme
@Composable fun successColor(): Color = if (isDark(LocalSettings.current.theme)) Color(0xFF7BDC8F) else Color(0xFF1B7F3B)
