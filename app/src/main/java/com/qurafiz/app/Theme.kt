package com.qurafiz.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Emerald = Color(0xFF0F6B4F)
val Gold = Color(0xFFC9A227)
val CorrectGreen = Color(0xFF2E9E6B)
val ErrorRed = Color(0xFFD64545)

private val Light = lightColorScheme(
    primary = Emerald, onPrimary = Color.White,
    secondary = Gold, onSecondary = Color.Black,
    background = Color(0xFFFAF6EC), surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFEFE8D5), onBackground = Color(0xFF1B1B16), onSurface = Color(0xFF1B1B16),
    error = ErrorRed,
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5FD3A5), onPrimary = Color(0xFF00382A),
    secondary = Gold, onSecondary = Color.Black,
    background = Color(0xFF101512), surface = Color(0xFF1A211D),
    surfaceVariant = Color(0xFF26302A), onBackground = Color(0xFFE6E2D6), onSurface = Color(0xFFE6E2D6),
    error = Color(0xFFFF8A8A),
)

@Composable
fun QurafizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
