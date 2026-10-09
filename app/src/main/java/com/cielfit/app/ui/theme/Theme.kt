package com.cielfit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = CalorieOrange,
    onPrimary = Color.Black,
    secondary = SuccessGreen,
    onSecondary = Color.Black,
    tertiary = ProteinBlue,
    background = Bg,
    onBackground = Color(0xFFF2F2F7),
    surface = Surface,
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = SurfaceHigh,
    onSurfaceVariant = Color(0xFF9A9AB0),
    outline = Outline,
    error = DangerRed
)

@Composable
fun CielFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkScheme, content = content)
}