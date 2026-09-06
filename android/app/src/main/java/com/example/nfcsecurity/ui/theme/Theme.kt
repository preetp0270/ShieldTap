package com.example.nfcsecurity.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultDarkScheme = darkColorScheme(
    primary = VaultPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2A2250),
    onPrimaryContainer = VaultPurpleSoft,
    secondary = VaultAccent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1E2A4A),
    onSecondaryContainer = VaultAccent,
    tertiary = VaultSuccess,
    background = VaultBg,
    onBackground = VaultText,
    surface = VaultSurface,
    onSurface = VaultText,
    surfaceVariant = VaultCard,
    onSurfaceVariant = VaultMuted,
    error = VaultDanger,
    onError = Color.White,
    outline = Color(0xFF3A3A4A)
)

private val VaultLightScheme = lightColorScheme(
    primary = Color(0xFF5B3FD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE7FF),
    onPrimaryContainer = Color(0xFF2A1570),
    secondary = Color(0xFF3D6BDB),
    background = Color(0xFFF5F4FA),
    onBackground = Color(0xFF1A1A24),
    surface = Color.White,
    onSurface = Color(0xFF1A1A24),
    surfaceVariant = Color(0xFFF0EEF8),
    onSurfaceVariant = Color(0xFF5A5A6E),
    error = Color(0xFFD32F2F),
    outline = Color(0xFFD0D0DC)
)

@Composable
fun NFCSecurityTheme(
    darkTheme: Boolean = true, // default dark like reference apps
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) VaultDarkScheme else VaultLightScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
