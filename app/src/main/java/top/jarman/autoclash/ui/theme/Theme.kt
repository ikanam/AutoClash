package top.jarman.autoclash.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Deep blue / purple tones for a premium look
private val PrimaryDark = Color(0xFF8B5CF6)       // Vibrant purple
private val OnPrimaryDark = Color(0xFFFFFFFF)
private val PrimaryContainerDark = Color(0xFF2D1B69)
private val SecondaryDark = Color(0xFF06B6D4)      // Cyan accent
private val OnSecondaryDark = Color(0xFF003544)
private val SecondaryContainerDark = Color(0xFF004D61)
private val TertiaryDark = Color(0xFF34D399)       // Emerald green
private val BackgroundDark = Color(0xFF0F0F1A)     // Very dark blue-black
private val SurfaceDark = Color(0xFF1A1A2E)        // Dark navy
private val SurfaceVariantDark = Color(0xFF252540)
private val OnBackgroundDark = Color(0xFFE8E0F0)
private val OnSurfaceDark = Color(0xFFE8E0F0)
private val OnSurfaceVariantDark = Color(0xFFCAC2D8)
private val ErrorDark = Color(0xFFEF4444)

private val PrimaryLight = Color(0xFF6D28D9)
private val OnPrimaryLight = Color(0xFFFFFFFF)
private val PrimaryContainerLight = Color(0xFFEDE0FF)
private val SecondaryLight = Color(0xFF0891B2)
private val SecondaryContainerLight = Color(0xFFCCF3FF)
private val TertiaryLight = Color(0xFF059669)
private val BackgroundLight = Color(0xFFFCFCFF)
private val SurfaceLight = Color(0xFFF5F3FF)
private val OnBackgroundLight = Color(0xFF1A1A2E)
private val OnSurfaceLight = Color(0xFF1A1A2E)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = OnPrimaryDark,
    primaryContainer = PrimaryContainerDark,
    secondary = SecondaryDark,
    onSecondary = OnSecondaryDark,
    secondaryContainer = SecondaryContainerDark,
    tertiary = TertiaryDark,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = OnBackgroundDark,
    onSurface = OnSurfaceDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    error = ErrorDark
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = OnPrimaryLight,
    primaryContainer = PrimaryContainerLight,
    secondary = SecondaryLight,
    secondaryContainer = SecondaryContainerLight,
    tertiary = TertiaryLight,
    background = BackgroundLight,
    surface = SurfaceLight,
    onBackground = OnBackgroundLight,
    onSurface = OnSurfaceLight
)

@Composable
fun AutoClashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

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
        content = content
    )
}
