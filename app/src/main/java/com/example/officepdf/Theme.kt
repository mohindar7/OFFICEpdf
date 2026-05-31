package com.example.officepdf

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF80D8FF),        // Soft light blue
    onPrimary = Color(0xFF003547),
    primaryContainer = Color(0xFF004D66), // Deep blue-cyan container
    onPrimaryContainer = Color(0xFFB3E5FC),
    secondary = Color(0xFFB2DFDB),      // Soft teal
    onSecondary = Color(0xFF003734),
    secondaryContainer = Color(0xFF004D40),
    onSecondaryContainer = Color(0xFFE0F2F1),
    tertiary = Color(0xFFFFCC80),       // Soft amber
    onTertiary = Color(0xFF4D2C00),
    tertiaryContainer = Color(0xFF804C00),
    onTertiaryContainer = Color(0xFFFFE0B2),
    background = Color(0xFF0F1319),     // Deep charcoal
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF131720),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF414751),
    onSurfaceVariant = Color(0xFFC1C7D0),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006684),       // Clean corporate cyan-blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBCE9FF),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF006B5F),     // Clean emerald teal
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF76F8E3),
    onSecondaryContainer = Color(0xFF00201B),
    tertiary = Color(0xFF885200),      // Professional amber
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB6),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFF7F9FF),    // Off-white blue tint
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFDCE4EC),
    onSurfaceVariant = Color(0xFF414751),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF)
)

@Composable
fun OfficePdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
