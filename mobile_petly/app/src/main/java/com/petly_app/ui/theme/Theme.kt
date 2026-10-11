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
    primary = Color(0xFFFFB59E), onPrimary = Color(0xFF5C2013),
    primaryContainer = Color(0xFF803923), onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Color(0xFFDBC5BB), onSecondary = Color(0xFF3D2D26),
    secondaryContainer = Color(0xFF554038), onSecondaryContainer = Color(0xFFF4DDD2),
    tertiary = Color(0xFFB7C8A3), onTertiary = Color(0xFF243619),
    tertiaryContainer = Color(0xFF3D5131), onTertiaryContainer = Color(0xFFD2E6BF),
    background = Color(0xFF1F1713), onBackground = Color(0xFFF0DFD6),
    surface = Color(0xFF241B17), onSurface = Color(0xFFF0DFD6),
    surfaceVariant = Color(0xFF51443D), onSurfaceVariant = Color(0xFFD3C2B9),
    outline = Color(0xFFA08F86)
)

@Composable
fun Petly_appTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // Sin colores dinámicos para conservar la identidad elegida también en Android 12+.
    MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography, content = content)
}
