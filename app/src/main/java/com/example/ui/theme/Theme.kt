package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val AdVmDarkColorScheme = darkColorScheme(
    primary = TelemetryCyan,
    onPrimary = ObsidianCanvas,
    primaryContainer = TelemetryCyanDark,
    onPrimaryContainer = TelemetryCyan,
    secondary = AndroidEmerald,
    onSecondary = ObsidianCanvas,
    secondaryContainer = AndroidEmeraldDark,
    onSecondaryContainer = AndroidEmerald,
    tertiary = WindowsCobalt,
    onTertiary = ObsidianCanvas,
    tertiaryContainer = WindowsCobaltDark,
    onTertiaryContainer = WindowsCobalt,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = SlateSurface,
    surfaceContainer = SlateElevated,
    surfaceContainerHigh = SlateHighlight,
    outline = HairlineBorder,
    outlineVariant = HairlineBorder,
    error = RoseAlert,
    onError = ObsidianCanvas,
    errorContainer = RoseAlertDark,
    onErrorContainer = RoseAlert
)

val AdVmShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun AdVmTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = AdVmDarkColorScheme,
        typography = AdVmTypography,
        shapes = AdVmShapes,
        content = content
    )
}
