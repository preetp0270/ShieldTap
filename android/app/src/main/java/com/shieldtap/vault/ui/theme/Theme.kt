package com.shieldtap.vault.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = Indigo200,
    onPrimary = Slate900,
    secondary = Indigo500,
    background = Color.Black,
    surface = Slate800,
    onBackground = Color.White,
    onSurface = Color.White,
    error = Rose500
)

private val LightColors = lightColorScheme(
    primary = Indigo500,
    onPrimary = Color.White,
    secondary = Indigo700,
    background = Color.White,
    surface = Color.White,
    onBackground = Slate900,
    onSurface = Slate900,
    error = Rose500
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

    val colorScheme = when {
        dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicDarkColorScheme(LocalContext.current)
        }
        !dark && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            dynamicLightColorScheme(LocalContext.current)
        }
        dark -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
