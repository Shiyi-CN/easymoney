package com.jiyixia.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

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
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
