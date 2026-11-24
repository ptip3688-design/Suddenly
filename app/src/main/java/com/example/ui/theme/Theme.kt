package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JarvisDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianSpace,
    primaryContainer = Color(0xFF0A3347),
    onPrimaryContainer = Color(0xFFB3F7FF),
    secondary = SapphireBlue,
    onSecondary = ObsidianSpace,
    secondaryContainer = Color(0xFF152A54),
    onSecondaryContainer = Color(0xFFD6E4FF),
    tertiary = NeuralViolet,
    onTertiary = TextPrimary,
    tertiaryContainer = Color(0xFF2E1A47),
    onTertiaryContainer = Color(0xFFEBD4FF),
    background = ObsidianSpace,
    onBackground = TextPrimary,
    surface = DeepHudSurface,
    onSurface = TextPrimary,
    surfaceVariant = CardHudSurface,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = DeepHudSurface,
    surfaceContainer = CardHudSurface,
    surfaceContainerHigh = ElevatedHudSurface,
    outline = OutlineGlow,
    outlineVariant = Color(0xFF172846),
    error = CrimsonError,
    onError = ObsidianSpace
)

val JarvisShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun JarvisAiTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = JarvisDarkColorScheme,
        typography = JarvisTypography,
        shapes = JarvisShapes,
        content = content
    )
}
