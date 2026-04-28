package dev.agentone.ui.theme

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

private val LightColors = lightColorScheme(
    primary = Color(0xFF6200EE),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFBB86FC),
    onPrimaryContainer = Color(0xFF3700B3),
    secondary = Color(0xFF03DAC6),
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFFCF6679),
    onSecondaryContainer = Color.White,
    surface = Color(0xFFFDFDFD),
    onSurface = Color(0xFF121212),
    surfaceVariant = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFF424242),
    background = Color.White,
    onBackground = Color(0xFF121212),
    error = Color(0xFFB00020),
    onError = Color.White
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFBB86FC), // Neon Purple
    onPrimary = Color(0xFF121212),
    primaryContainer = Color(0xFF3700B3),
    onPrimaryContainer = Color(0xFFBB86FC),
    secondary = Color(0xFF03DAC6), // Neon Cyan
    onSecondary = Color(0xFF121212),
    secondaryContainer = Color(0xFF018786),
    onSecondaryContainer = Color(0xFF03DAC6),
    surface = Color(0xFF1E1E2E), // Deep Blue-Gray Surface
    onSurface = Color(0xFFE1E1E1),
    surfaceVariant = Color(0xFF2B2B3D),
    onSurfaceVariant = Color(0xFFB0B0B0),
    background = Color(0xFF0F0F1A), // Deep Dark Background
    onBackground = Color(0xFFF0F0F0),
    error = Color(0xFFCF6679),
    onError = Color(0xFF121212)
)

@Composable
fun AgentOneTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
