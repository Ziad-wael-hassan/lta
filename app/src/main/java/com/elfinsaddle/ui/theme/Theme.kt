// ui/theme/Theme.kt
package com.elfinsaddle.ui.theme

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// --- LIGHT THEME (Unchanged) ---
private val LightColorScheme = lightColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    background = AppGrayBackground,
    surface = Color.White,
    onSurface = AppTextPrimary,
    onSurfaceVariant = AppTextSecondary,
    outline = Color(0xFFE5E7EB),
    error = StatusRed,
    errorContainer = StatusRedContainer,
    onErrorContainer = StatusRed
)

// --- NEW & IMPROVED DARK THEME ---
private val DarkColorScheme = darkColorScheme(
    primary = AppBlue,
    onPrimary = Color.White,
    background = Color(0xFF111827), // Dark blue-gray background
    surface = Color(0xFF1F2937),   // Slightly lighter card background
    onSurface = Color(0xFFF9FAFB), // Near-white text for high contrast
    onSurfaceVariant = Color(0xFF9CA3AF), // Lighter gray for secondary text
    outline = Color(0xFF4B5563), // Visible but not distracting borders
    error = StatusRed,
    errorContainer = StatusRedContainer,
    onErrorContainer = StatusRed
)

@Composable
fun LtaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
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
