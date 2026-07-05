package com.routersync.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette ispirata a reti/connettività: indaco come colore primario, verde acqua come accento.
private val Indigo = Color(0xFF3D5AFE)
private val IndigoDark = Color(0xFF0031CA)
private val Teal = Color(0xFF00BFA5)
private val TealDark = Color(0xFF00867D)

private val LightColors = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1FF),
    onPrimaryContainer = Color(0xFF00105C),
    secondary = Teal,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC6F4EB),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFF7B5800),
    tertiaryContainer = Color(0xFFFFE08A),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFFBF8FF),
    surface = Color(0xFFFBF8FF),
    surfaceVariant = Color(0xFFE3E1EC)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBAC3FF),
    onPrimary = IndigoDark,
    primaryContainer = Color(0xFF16227A),
    onPrimaryContainer = Color(0xFFDEE1FF),
    secondary = Color(0xFF80D8C7),
    onSecondary = TealDark,
    secondaryContainer = Color(0xFF00504A),
    onSecondaryContainer = Color(0xFFC6F4EB),
    tertiary = Color(0xFFFFDF9D),
    tertiaryContainer = Color(0xFF5B4200),
    onTertiaryContainer = Color(0xFFFFE08A),
    background = Color(0xFF131318),
    surface = Color(0xFF131318),
    surfaceVariant = Color(0xFF46464F)
)

private val AppTypography = Typography()

@Composable
fun RouterSyncTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
