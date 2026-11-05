package dev.pvps.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// PVPS visual system: graphite instrument panels, phosphor-lime signal,
// iris for agent/subagent work, warm amber for execution states.
val Void = Color(0xFF07090B)
val DeepBackground = Void
val SurfaceDark = Color(0xFF0C1013)
val SurfaceRaised = Color(0xFF12181C)
val SurfaceHigh = Color(0xFF192227)
val OutlineDark = Color(0xFF283238)
val OutlineSoft = Color(0xFF1B242A)

val Accent = Color(0xFFD1FA76)
val AccentDim = Color(0xFF90B94D)
val SignalDeep = Color(0xFF28350E)
val Violet = Color(0xFFB8A8FF)
val IrisDeep = Color(0xFF241E3E)
val Ember = Color(0xFFF4C77A)
val Danger = Color(0xFFFF7B8A)
val Warning = Color(0xFFF4C77A)
val Success = Color(0xFF79E7B5)
val TextPrimary = Color(0xFFEDF2F1)
val TextSecondary = Color(0xFF96A4A6)
val TextFaint = Color(0xFF647276)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00251A),
    primaryContainer = SignalDeep,
    onPrimaryContainer = Color(0xFFC9FFE8),
    secondary = Violet,
    onSecondary = Color(0xFF1A1235),
    secondaryContainer = IrisDeep,
    onSecondaryContainer = Color(0xFFEAE3FF),
    tertiary = Ember,
    onTertiary = Color(0xFF2B1900),
    tertiaryContainer = Color(0xFF3A2A10),
    onTertiaryContainer = Color(0xFFFFE2B8),
    background = Void,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceRaised,
    onSurfaceVariant = TextSecondary,
    surfaceTint = Accent,
    inverseSurface = TextPrimary,
    inverseOnSurface = Void,
    outline = OutlineDark,
    outlineVariant = OutlineSoft,
    error = Danger,
    onError = Color(0xFF2B0008),
    errorContainer = Color(0xFF3D1420),
    onErrorContainer = Color(0xFFFFD9DF),
    scrim = Color(0xCC000000),
    surfaceBright = SurfaceHigh,
    surfaceDim = Color(0xFF070C09),
    surfaceContainerLowest = Color(0xFF040705),
    surfaceContainerLow = SurfaceDark,
    surfaceContainer = Color(0xFF0D1410),
    surfaceContainerHigh = SurfaceRaised,
    surfaceContainerHighest = SurfaceHigh
)

private val PvpsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
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
            // Added in API 29; minSdk is 28.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
        }
    }
    MaterialTheme(
        colorScheme = DarkColors,
        typography = PvpsTypography,
        shapes = PvpsShapes,
        content = content
    )
}
