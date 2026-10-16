package com.petly_app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Terracota, onPrimary = Color.White,
    primaryContainer = RosaSuave, onPrimaryContainer = TextoCalido,
    secondary = Color(0xFF65534B), onSecondary = Color.White,
    secondaryContainer = RosaSuave, onSecondaryContainer = TextoCalido,
    tertiary = Color(0xFF4B633B), onTertiary = Color.White,
    tertiaryContainer = Salvia, onTertiaryContainer = TextoCalido,
    background = FondoCalido, onBackground = TextoCalido,
    surface = Color.White, onSurface = TextoCalido,
    surfaceVariant = Color(0xFFF1E7E0), onSurfaceVariant = TextoSecundario,
    outline = Color(0xFF81736B),
    error = Color(0xFFB3261E), onError = Color.White
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
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}
