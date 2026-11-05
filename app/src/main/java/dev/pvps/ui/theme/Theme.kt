package dev.pvps.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DeepBackground = Color(0xFF070B12)
val SurfaceDark = Color(0xFF0D141E)
val SurfaceRaised = Color(0xFF141E2A)
val OutlineDark = Color(0xFF223044)
val Accent = Color(0xFF5EEAD4)
val AccentDim = Color(0xFF2DD4BF)
val Violet = Color(0xFFA78BFA)
val TextPrimary = Color(0xFFEAF2F8)
val TextSecondary = Color(0xFF8FA3B8)
val Danger = Color(0xFFFB7185)
val Warning = Color(0xFFFBBF24)
val Success = Color(0xFF34D399)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00201D),
    primaryContainer = Color(0xFF103B37),
    onPrimaryContainer = Color(0xFFB8FFF6),
    secondary = Violet,
    onSecondary = Color(0xFF1B103A),
    secondaryContainer = Color(0xFF2B2150),
    onSecondaryContainer = Color(0xFFEDE6FF),
    tertiary = Warning,
    onTertiary = Color(0xFF2A1A00),
    background = DeepBackground,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    outlineVariant = Color(0xFF182433),
    error = Danger,
    onError = Color(0xFF2B0008)
)

@Composable
fun PvpsTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PvpsTypography,
        content = content
    )
}
