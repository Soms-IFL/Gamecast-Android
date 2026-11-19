package com.iflgamecast.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How much room the screen content has (the navigation rail is not counted). Screens pick single-column, grid or two-pane layouts from it. */
enum class WinSize { Compact, Medium, Expanded }

val LocalWinSize = staticCompositionLocalOf { WinSize.Compact }

fun winSizeFor(contentWidth: Dp): WinSize = when {
    contentWidth >= 840.dp -> WinSize.Expanded
    contentWidth >= 600.dp -> WinSize.Medium
    else -> WinSize.Compact
}

/** Page margins grow a little on bigger screens. */
@Composable
fun pagePadding(): Dp = if (LocalWinSize.current == WinSize.Compact) 16.dp else 24.dp

/** Keeps single-column content at a comfortable reading width, centred, instead of stretching across a tablet. */
@Composable
fun CenteredContent(maxWidth: Dp = 760.dp, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = maxWidth).fillMaxSize()) { content() }
    }
}
