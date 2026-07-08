package com.routersync.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Palette ispirata al progetto SyncDrive (blu/slate pulito), portata in Material3 e
 * arricchita con un accento secondario (verde acqua) che nell'originale mancava, per dare
 * più carattere agli elementi interattivi secondari senza intaccare la leggibilità.
 */
private val Primary = Color(0xFF2563EB)
private val PrimaryDark = Color(0xFF3B82F6)
private val Accent = Color(0xFF0EA5A5) // aggiunta rispetto all'originale: accento secondario acqua

private val BackgroundLight = Color(0xFFF8FAFC)
private val BackgroundDark = Color(0xFF0F172A)
private val SurfaceLight = Color(0xFFFFFFFF)
private val SurfaceDark = Color(0xFF1E293B)
private val ForegroundLight = Color(0xFF0F172A)
private val ForegroundDark = Color(0xFFF1F5F9)
private val MutedLight = Color(0xFF64748B)
private val MutedDark = Color(0xFF94A3B8)
private val BorderLight = Color(0xFFE2E8F0)
private val BorderDark = Color(0xFF334155)

val SuccessLight = Color(0xFF10B981)
val SuccessDark = Color(0xFF34D399)
val WarningLight = Color(0xFFF59E0B)
val WarningDark = Color(0xFFFBBF24)
val ErrorLight = Color(0xFFEF4444)
val ErrorDark = Color(0xFFF87171)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE7FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFC7F3F0),
    onSecondaryContainer = Color(0xFF00201F),
    tertiary = WarningLight,
    tertiaryContainer = Color(0xFFFFE8BE),
    onTertiaryContainer = Color(0xFF2A1800),
    background = BackgroundLight,
    surface = SurfaceLight,
    onSurface = ForegroundLight,
    onBackground = ForegroundLight,
    surfaceVariant = Color(0xFFEEF2F7),
    onSurfaceVariant = MutedLight,
    outline = BorderLight,
    outlineVariant = BorderLight,
    error = ErrorLight
)

private val DarkColors = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color(0xFF00224D),
    primaryContainer = Color(0xFF163A73),
    onPrimaryContainer = Color(0xFFDCE7FF),
    secondary = Color(0xFF5EEAE3),
    onSecondary = Color(0xFF00332F),
    secondaryContainer = Color(0xFF004E48),
    onSecondaryContainer = Color(0xFFC7F3F0),
    tertiary = WarningDark,
    tertiaryContainer = Color(0xFF5B3F00),
    onTertiaryContainer = Color(0xFFFFE8BE),
    background = BackgroundDark,
    surface = SurfaceDark,
    onSurface = ForegroundDark,
    onBackground = ForegroundDark,
    surfaceVariant = Color(0xFF25324A),
    onSurfaceVariant = MutedDark,
    outline = BorderDark,
    outlineVariant = BorderDark,
    error = ErrorDark
)

/** Angoli morbidi e coerenti su tutti i componenti, come nelle card SyncDrive (rounded-2xl). */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    )
}

@Composable
fun RouterSyncTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
}
