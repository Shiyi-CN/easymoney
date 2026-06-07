package com.jiyixia.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.jiyixia.app.data.ThemeMode

private val LightColors = lightColorScheme(
    primary = ColorPrimary,
    onPrimary = ColorOnPrimary,
    primaryContainer = ColorPrimaryContainer,
    onPrimaryContainer = ColorOnPrimaryContainer,
    secondary = ColorSecondary,
    onSecondary = ColorOnSecondary,
    surface = ColorSurface,
    onSurface = ColorOnSurface,
    error = ColorError,
    onError = ColorOnError,
)

private val DarkColors = darkColorScheme(
    primary = ColorPrimaryDark,
    onPrimary = ColorOnPrimaryDark,
    primaryContainer = ColorPrimaryContainerDark,
    onPrimaryContainer = ColorOnPrimaryContainerDark,
    secondary = ColorSecondaryDark,
    onSecondary = ColorOnSecondaryDark,
    surface = ColorSurfaceDark,
    onSurface = ColorOnSurfaceDark,
    error = ColorErrorDark,
    onError = ColorOnErrorDark,
)

@Composable
fun JiYiXiaTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val isDarkTheme = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (isDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        isDarkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
