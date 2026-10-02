package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = StudyIndigoLight,
    onPrimary = DarkBackground,
    primaryContainer = StudyIndigoDark,
    onPrimaryContainer = StudyIndigoLight,
    secondary = FocusEmerald,
    onSecondary = DarkBackground,
    secondaryContainer = FocusEmeraldContainer,
    error = DistractionRose,
    onError = DarkBackground,
    errorContainer = DistractionRoseContainer,
    background = DarkBackground,
    onBackground = TextPrimaryDark,
    surface = DarkSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = DarkSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark
)

private val LightColorScheme = lightColorScheme(
    primary = StudyIndigoPrimary,
    onPrimary = LightSurface,
    primaryContainer = StudyIndigoLight.copy(alpha = 0.2f),
    onPrimaryContainer = StudyIndigoDark,
    secondary = FocusEmerald,
    onSecondary = LightSurface,
    secondaryContainer = FocusEmeraldContainer,
    error = DistractionRose,
    onError = LightSurface,
    errorContainer = DistractionRoseContainer,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceElevated,
    onSurfaceVariant = TextSecondaryLight
)

@Composable
fun SmartStudyTheme(
    darkTheme: Boolean = true, // Default to a focused dark theme for eye-friendly study sessions
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
