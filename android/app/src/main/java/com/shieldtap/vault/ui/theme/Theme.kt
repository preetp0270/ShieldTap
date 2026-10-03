package com.shieldtap.vault.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = PureWhite,
    onPrimary = PureBlack,
    primaryContainer = SoftGrey,
    onPrimaryContainer = SoftWhite,
    secondary = LightGrey,
    onSecondary = PureBlack,
    secondaryContainer = MidGrey,
    onSecondaryContainer = SoftWhite,
    tertiary = MutedGrey,
    onTertiary = PureWhite,
    background = PureBlack,
    onBackground = SoftWhite,
    surface = NearBlack,
    onSurface = SoftWhite,
    surfaceVariant = DarkGrey,
    onSurfaceVariant = LightGrey,
    surfaceContainerHighest = SoftGrey,
    surfaceContainerHigh = MidGrey,
    surfaceContainer = DarkGrey,
    surfaceContainerLow = NearBlack,
    surfaceContainerLowest = PureBlack,
    outline = BorderGrey,
    outlineVariant = SoftGrey,
    error = ErrorRed,
    onError = PureBlack,
    errorContainer = Color(0xFF3D1F24),
    onErrorContainer = ErrorRed,
    inverseSurface = SoftWhite,
    inverseOnSurface = PureBlack,
    inversePrimary = PureBlack,
    scrim = PureBlack,
)

private val LightColors = lightColorScheme(
    primary = PureBlack,
    onPrimary = PureWhite,
    primaryContainer = SoftWhite,
    onPrimaryContainer = NearBlack,
    secondary = MutedGrey,
    onSecondary = PureWhite,
    secondaryContainer = OffWhite,
    onSecondaryContainer = NearBlack,
    tertiary = LightGrey,
    onTertiary = PureBlack,
    background = PureWhite,
    onBackground = NearBlack,
    surface = OffWhite,
    onSurface = NearBlack,
    surfaceVariant = SoftWhite,
    onSurfaceVariant = MutedGrey,
    surfaceContainerHighest = SoftWhite,
    surfaceContainerHigh = Color(0xFFEEEEEE),
    surfaceContainer = OffWhite,
    surfaceContainerLow = PureWhite,
    surfaceContainerLowest = PureWhite,
    outline = Color(0xFFCCCCCC),
    outlineVariant = SoftWhite,
    error = Color(0xFFB00020),
    onError = PureWhite,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFFB00020),
    inverseSurface = NearBlack,
    inverseOnSurface = SoftWhite,
    inversePrimary = PureWhite,
    scrim = PureBlack,
)

@Composable
fun ShieldTapTheme(
    themeMode: String = "system", // system | light | dark
    content: @Composable () -> Unit
) {
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    // Force monochrome – never use dynamic colors (they break B/W theme)
    val colorScheme = if (dark) DarkColors else LightColors

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
