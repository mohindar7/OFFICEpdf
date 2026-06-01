package com.example.officepdf

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFA8C8FF),          // Vibrant light blue
    onPrimary = Color(0xFF003062),
    primaryContainer = Color(0xFF00468B),   // Deep brand blue
    onPrimaryContainer = Color(0xFFD6E3FF),
    secondary = Color(0xFFB5C8E2),        // Tonal slate blue
    onSecondary = Color(0xFF203146),
    secondaryContainer = Color(0xFF36485E),
    onSecondaryContainer = Color(0xFFD1E4FF),
    tertiary = Color(0xFFEEB8C8),         // Energetic warm accent
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD9E3),
    background = Color(0xFF191C1E),       // Cozy dark surface
    onBackground = Color(0xFFE1E2E5),
    surface = Color(0xFF111416),
    onSurface = Color(0xFFE1E2E5),
    surfaceVariant = Color(0xFF424752),
    onSurfaceVariant = Color(0xFFC2C6D4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000a),
    onErrorContainer = Color(0xFFFFDAD6)
)

internal val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00488D),          // Premium brand blue
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD6E3FF),   // Light tint blue container
    onPrimaryContainer = Color(0xFF001B3D),
    secondary = Color(0xFF4E6076),        // Elegant slate/steel blue
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD1E4FF),
    onSecondaryContainer = Color(0xFF081D30),
    tertiary = Color(0xFF653D4A),         // Soft creative plum
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD9E3),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFF8F9FC),       // Clean light neutral background
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFE1E2E5),
    onSurfaceVariant = Color(0xFF424752),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A)
)

@Composable
expect fun OfficePdfTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
)
