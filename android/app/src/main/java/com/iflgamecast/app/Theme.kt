package com.iflgamecast.app

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.google.android.material.color.utilities.CorePalette
import com.google.android.material.color.utilities.TonalPalette

const val DEFAULT_SEED = 0xFF4A4FD0.toInt()

@SuppressLint("RestrictedApi")
private fun c(p: TonalPalette, tone: Int) = Color(p.tone(tone))

/** Builds a complete Material 3 colour scheme from one seed colour using Google's tonal palettes. */
@SuppressLint("RestrictedApi")
fun schemeFromSeed(seed: Int, dark: Boolean): ColorScheme {
    val p = CorePalette.of(seed)
    return if (!dark) lightColorScheme(
        primary = c(p.a1, 40), onPrimary = c(p.a1, 100), primaryContainer = c(p.a1, 90), onPrimaryContainer = c(p.a1, 10),
        inversePrimary = c(p.a1, 80),
        secondary = c(p.a2, 40), onSecondary = c(p.a2, 100), secondaryContainer = c(p.a2, 90), onSecondaryContainer = c(p.a2, 10),
        tertiary = c(p.a3, 40), onTertiary = c(p.a3, 100), tertiaryContainer = c(p.a3, 90), onTertiaryContainer = c(p.a3, 10),
        background = c(p.n1, 99), onBackground = c(p.n1, 10), surface = c(p.n1, 99), onSurface = c(p.n1, 10),
        surfaceVariant = c(p.n2, 90), onSurfaceVariant = c(p.n2, 30), surfaceTint = c(p.a1, 40),
        inverseSurface = c(p.n1, 20), inverseOnSurface = c(p.n1, 95),
        error = c(p.error, 40), onError = c(p.error, 100), errorContainer = c(p.error, 90), onErrorContainer = c(p.error, 10),
        outline = c(p.n2, 50), outlineVariant = c(p.n2, 80), scrim = Color.Black,
        surfaceBright = c(p.n1, 98), surfaceDim = c(p.n1, 87),
        surfaceContainerLowest = c(p.n1, 100), surfaceContainerLow = c(p.n1, 96), surfaceContainer = c(p.n1, 94),
        surfaceContainerHigh = c(p.n1, 92), surfaceContainerHighest = c(p.n1, 90),
    ) else darkColorScheme(
        primary = c(p.a1, 80), onPrimary = c(p.a1, 20), primaryContainer = c(p.a1, 30), onPrimaryContainer = c(p.a1, 90),
        inversePrimary = c(p.a1, 40),
        secondary = c(p.a2, 80), onSecondary = c(p.a2, 20), secondaryContainer = c(p.a2, 30), onSecondaryContainer = c(p.a2, 90),
        tertiary = c(p.a3, 80), onTertiary = c(p.a3, 20), tertiaryContainer = c(p.a3, 30), onTertiaryContainer = c(p.a3, 90),
        background = c(p.n1, 6), onBackground = c(p.n1, 90), surface = c(p.n1, 6), onSurface = c(p.n1, 90),
        surfaceVariant = c(p.n2, 30), onSurfaceVariant = c(p.n2, 80), surfaceTint = c(p.a1, 80),
        inverseSurface = c(p.n1, 90), inverseOnSurface = c(p.n1, 20),
        error = c(p.error, 80), onError = c(p.error, 20), errorContainer = c(p.error, 30), onErrorContainer = c(p.error, 90),
        outline = c(p.n2, 60), outlineVariant = c(p.n2, 30), scrim = Color.Black,
        surfaceBright = c(p.n1, 24), surfaceDim = c(p.n1, 6),
        surfaceContainerLowest = c(p.n1, 4), surfaceContainerLow = c(p.n1, 10), surfaceContainer = c(p.n1, 12),
        surfaceContainerHigh = c(p.n1, 17), surfaceContainerHighest = c(p.n1, 22),
    )
}

@Composable
fun isDark(mode: String): Boolean = when (mode) { "light" -> false; "dark" -> true; else -> isSystemInDarkTheme() }

@Composable
fun IflTheme(seed: Int, mode: String, content: @Composable () -> Unit) {
    val dark = isDark(mode)
    val scheme = remember(seed, dark) { schemeFromSeed(seed, dark) }
    MaterialTheme(colorScheme = scheme, content = content)
}

// Semantic colours that don't exist in the M3 scheme
@Composable fun successColor(): Color = if (isDark(LocalSettings.current.theme)) Color(0xFF7BDC8F) else Color(0xFF1B7F3B)
