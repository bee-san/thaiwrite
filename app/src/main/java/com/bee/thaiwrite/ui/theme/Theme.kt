package com.bee.thaiwrite.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Mist,
    secondary = Saffron,
    onSecondary = Mist,
    tertiary = Palm,
    background = Paper,
    surface = Mist,
    onSurface = Ink,
    surfaceVariant = Sand,
)

private val DarkColors = darkColorScheme(
    primary = Mist,
    onPrimary = Ink,
    secondary = Saffron,
    onSecondary = Ink,
    tertiary = Palm,
    background = Ink,
    surface = Color(0xFF16252C),
    onSurface = Mist,
    surfaceVariant = Color(0xFF26353B),
)

@Composable
fun ThaiWriteTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = ThaiWriteTypography,
        content = content,
    )
}
