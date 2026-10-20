package io.shamash.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** Shamash's colors: night blue, and the Sun's gold for what's focused and important. */
object Palette {
    val Night = Color(0xFF0A0C1E)
    val Dusk = Color(0xFF14172E)
    val Gold = Color(0xFFF4C450)
    val Dawn = Color(0xFFFFE28C)
    val Ink = Color(0xFFF5F0E6)
    val Dim = Color(0xB3F5F0E6)
}

@Composable
fun ShamashTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Palette.Night,
            surface = Palette.Dusk,
            onSurface = Palette.Ink,
            background = Palette.Night,
            onBackground = Palette.Ink,
        ),
        content = content,
    )
}
