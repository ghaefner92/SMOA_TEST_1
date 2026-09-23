package com.example.imiq.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF5EEAD4), onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF0A4F46), onPrimaryContainer = Color(0xFFB9F5E9),
    secondary = Color(0xFF9DD8F2), onSecondary = Color(0xFF003548),
    secondaryContainer = Color(0xFF17485E), onSecondaryContainer = Color(0xFFC8ECFF),
    tertiary = Color(0xFFD1BCFF), onTertiary = Color(0xFF382460),
    tertiaryContainer = Color(0xFF503C7A), onTertiaryContainer = Color(0xFFEBDDFF),
    background = Color(0xFF101416), onBackground = Color(0xFFE1E6E3),
    surface = Color(0xFF101416), onSurface = Color(0xFFE1E6E3),
    surfaceVariant = Color(0xFF3F4947), onSurfaceVariant = Color(0xFFBFC9C5),
    surfaceContainerLow = Color(0xFF171C1C), surfaceContainer = Color(0xFF1B2020),
    surfaceContainerHigh = Color(0xFF262B2B),
    outline = Color(0xFF89938F), outlineVariant = Color(0xFF3F4947),
    error = Color(0xFFFFB4AB), onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B5D), onPrimary = Color.White,
    primaryContainer = Color(0xFF8FF8E6), onPrimaryContainer = Color(0xFF00201B),
    secondary = Color(0xFF27657C), onSecondary = Color.White,
    secondaryContainer = Color(0xFFB4EAFF), onSecondaryContainer = Color(0xFF001F2A),
    background = Color(0xFFF8FBF8), onBackground = Color(0xFF191C1B),
    surface = Color(0xFFF8FBF8), onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDBE5E1), onSurfaceVariant = Color(0xFF3F4947),
    outline = Color(0xFF6F7976), outlineVariant = Color(0xFFBFC9C5),
    error = Color(0xFFBA1A1A), onError = Color.White,
    errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF410002),
)

@Composable
fun IMIQTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        shapes = ImiqShapes,
        content = content
    )
}
