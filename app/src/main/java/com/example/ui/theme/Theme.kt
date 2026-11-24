package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val SkaCinemaColorScheme = darkColorScheme(
    primary = TanzanianGold,
    onPrimary = ObsidianBg,
    primaryContainer = TanzanianGoldDark,
    onPrimaryContainer = TanzanianGoldLight,
    secondary = TanzaniteTeal,
    onSecondary = ObsidianBg,
    secondaryContainer = TanzaniteTealContainer,
    onSecondaryContainer = TanzaniteTeal,
    tertiary = VipSapphire,
    onTertiary = TextPrimary,
    tertiaryContainer = VipSapphireContainer,
    onTertiaryContainer = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = OnyxSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateCard,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLow = OnyxSurface,
    surfaceContainer = SlateCard,
    surfaceContainerHigh = SlateCardElevated,
    outline = DividerSubtle,
    error = ErrorCoral,
    onError = TextPrimary
)

private val SkaShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun SkaMacconnectionsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = SkaCinemaColorScheme,
        typography = AppTypography,
        shapes = SkaShapes,
        content = content
    )
}
