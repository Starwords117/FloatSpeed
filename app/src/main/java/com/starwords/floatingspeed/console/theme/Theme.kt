package com.starwords.floatingspeed.console.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3FB950),
    onPrimary = Color(0xFF0D1117),
    onPrimaryContainer = Color(0xFF0D1117),
    secondary = Color(0xFF58A6FF),
    onSecondary = Color(0xFF0D1117),
    background = Color(0xFF0D1117),
    onBackground = Color(0xFFF0F6FC),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFF0F6FC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF8B949E),
    surfaceContainer = Color(0xFF161B22),
    outline = Color(0xFF30363D),
    error = Color(0xFFF85149),
    onError = Color(0xFF0D1117),
    errorContainer = Color(0xFF3D1418),
    onErrorContainer = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A7F37),
    onPrimary = Color(0xFFFFFFFF),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF0969DA),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F2328),
    surface = Color(0xFFF6F8FA),
    onSurface = Color(0xFF1F2328),
    surfaceVariant = Color(0xFFEAEEF2),
    onSurfaceVariant = Color(0xFF57606A),
    surfaceContainer = Color(0xFFF6F8FA),
    outline = Color(0xFFD0D7DE),
    error = Color(0xFFCF222E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFEBE9),
    onErrorContainer = Color(0xFFCF222E),
)

@Composable
fun FloatSpeedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
