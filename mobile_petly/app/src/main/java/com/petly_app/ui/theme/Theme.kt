package com.petly_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Primary, onPrimary = Color.White,
    primaryContainer = Secondary, onPrimaryContainer = TextPrimary,
    secondary = TextSecondary, onSecondary = Color.White,
    secondaryContainer = Secondary, onSecondaryContainer = TextPrimary,
    tertiary = Accent, onTertiary = Color(0xFF4A2C10),
    tertiaryContainer = Color(0xFFFFF3E0), onTertiaryContainer = Color(0xFF4A2C10),
    background = Background, onBackground = TextPrimary,
    surface = Surface, onSurface = TextPrimary,
    surfaceVariant = Secondary, onSurfaceVariant = TextSecondary,
    outline = Border,
    error = Error, onError = Color.White,
    errorContainer = ErrorContainer, onErrorContainer = Error
)

private val DarkColors = darkColorScheme(
    primary = PrimaryLight, onPrimary = Color(0xFF0D3D2F),
    primaryContainer = PrimaryDark, onPrimaryContainer = Background,
    secondary = Secondary, onSecondary = TextPrimary,
    secondaryContainer = PrimaryDark, onSecondaryContainer = Background,
    tertiary = Accent, onTertiary = Color(0xFF4A2C10),
    tertiaryContainer = Color(0xFF4A2C10), onTertiaryContainer = Color(0xFFFFF3E0),
    background = Color(0xFF0F1A15), onBackground = Background,
    surface = Color(0xFF121A15), onSurface = Background,
    surfaceVariant = PrimaryDark, onSurfaceVariant = TextSecondary,
    outline = Border,
    error = Error, onError = Color.White,
    errorContainer = ErrorContainer, onErrorContainer = Error
)

@Composable
fun Petly_appTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Sin colores dinámicos para conservar la identidad elegida también en Android 12+.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
