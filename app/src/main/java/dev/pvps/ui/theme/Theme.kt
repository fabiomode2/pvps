package dev.pvps.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

data class PvpsPalette(
    val id: String,
    val label: String,
    val isLight: Boolean,
    val void: Color,
    val surfaceDark: Color,
    val surfaceRaised: Color,
    val surfaceHigh: Color,
    val outlineDark: Color,
    val outlineSoft: Color,
    val accent: Color,
    val onAccent: Color,
    val accentDim: Color,
    val signalDeep: Color,
    val violet: Color,
    val irisDeep: Color,
    val ember: Color,
    val danger: Color,
    val success: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textFaint: Color
)

val Graphite = PvpsPalette(
    id = "graphite", label = "Graphite", isLight = false,
    void = Color(0xFF07090B), surfaceDark = Color(0xFF0C1013),
    surfaceRaised = Color(0xFF12181C), surfaceHigh = Color(0xFF192227),
    outlineDark = Color(0xFF283238), outlineSoft = Color(0xFF1B242A),
    accent = Color(0xFFD1FA76), onAccent = Color(0xFF17210A),
    accentDim = Color(0xFF90B94D), signalDeep = Color(0xFF28350E),
    violet = Color(0xFFB8A8FF), irisDeep = Color(0xFF241E3E),
    ember = Color(0xFFF4C77A), danger = Color(0xFFFF7B8A),
    success = Color(0xFF79E7B5), textPrimary = Color(0xFFEDF2F1),
    textSecondary = Color(0xFF96A4A6), textFaint = Color(0xFF647276)
)

val Midnight = PvpsPalette(
    id = "midnight", label = "Midnight", isLight = false,
    void = Color(0xFF060A12), surfaceDark = Color(0xFF0B111C),
    surfaceRaised = Color(0xFF111A29), surfaceHigh = Color(0xFF18243A),
    outlineDark = Color(0xFF27364F), outlineSoft = Color(0xFF192337),
    accent = Color(0xFF8FD8FF), onAccent = Color(0xFF03202E),
    accentDim = Color(0xFF4EA8D8), signalDeep = Color(0xFF0E2C3D),
    violet = Color(0xFFC3B5FF), irisDeep = Color(0xFF211C3D),
    ember = Color(0xFFFFCE8A), danger = Color(0xFFFF8D9B),
    success = Color(0xFF84E9C5), textPrimary = Color(0xFFEAF2FF),
    textSecondary = Color(0xFF9AAAC4), textFaint = Color(0xFF66768F)
)

val EmberTheme = PvpsPalette(
    id = "ember", label = "Ember", isLight = false,
    void = Color(0xFF0D0907), surfaceDark = Color(0xFF15100C),
    surfaceRaised = Color(0xFF1E1711), surfaceHigh = Color(0xFF2A2018),
    outlineDark = Color(0xFF3D3025), outlineSoft = Color(0xFF291F18),
    accent = Color(0xFFFFB878), onAccent = Color(0xFF2C1604),
    accentDim = Color(0xFFD68A4D), signalDeep = Color(0xFF3A2310),
    violet = Color(0xFFD9B8FF), irisDeep = Color(0xFF2E1F3D),
    ember = Color(0xFFFFD29A), danger = Color(0xFFFF8A8A),
    success = Color(0xFFAED8A0), textPrimary = Color(0xFFFFF1E7),
    textSecondary = Color(0xFFC0A997), textFaint = Color(0xFF8C7665)
)

val Paper = PvpsPalette(
    id = "paper", label = "Paper", isLight = true,
    void = Color(0xFFF4F2EA), surfaceDark = Color(0xFFFBF9F2),
    surfaceRaised = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEDE9DC),
    outlineDark = Color(0xFFD3CCBC), outlineSoft = Color(0xFFE5DFD2),
    accent = Color(0xFF3B4A20), onAccent = Color(0xFFF8FFE8),
    accentDim = Color(0xFF64783C), signalDeep = Color(0xFFE2EACB),
    violet = Color(0xFF6B5CA5), irisDeep = Color(0xFFEAE3FF),
    ember = Color(0xFF9A641C), danger = Color(0xFFB33145),
    success = Color(0xFF276B50), textPrimary = Color(0xFF1B1D17),
    textSecondary = Color(0xFF5E6155), textFaint = Color(0xFF8A8D80)
)

val Themes = listOf(Graphite, Midnight, EmberTheme, Paper)

@Volatile
private var activePalette: PvpsPalette = Graphite

fun paletteFor(id: String): PvpsPalette = Themes.firstOrNull { it.id == id } ?: Graphite

// Existing composables import these names directly. They are getters so a
// theme switch re-colors the whole app without touching hundreds of call sites.
val Void: Color get() = activePalette.void
val DeepBackground: Color get() = activePalette.void
val SurfaceDark: Color get() = activePalette.surfaceDark
val SurfaceRaised: Color get() = activePalette.surfaceRaised
val SurfaceHigh: Color get() = activePalette.surfaceHigh
val OutlineDark: Color get() = activePalette.outlineDark
val OutlineSoft: Color get() = activePalette.outlineSoft
val Accent: Color get() = activePalette.accent
val OnAccent: Color get() = activePalette.onAccent
val AccentDim: Color get() = activePalette.accentDim
val SignalDeep: Color get() = activePalette.signalDeep
val Violet: Color get() = activePalette.violet
val IrisDeep: Color get() = activePalette.irisDeep
val Ember: Color get() = activePalette.ember
val Danger: Color get() = activePalette.danger
val Warning: Color get() = activePalette.ember
val Success: Color get() = activePalette.success
val TextPrimary: Color get() = activePalette.textPrimary
val TextSecondary: Color get() = activePalette.textSecondary
val TextFaint: Color get() = activePalette.textFaint

private val PvpsShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PvpsTheme(
    themeId: String = "graphite",
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeId)
    activePalette = palette
    val scheme = if (palette.isLight) {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.signalDeep,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.violet,
            onSecondary = Color.White,
            secondaryContainer = palette.irisDeep,
            onSecondaryContainer = palette.textPrimary,
            tertiary = palette.ember,
            onTertiary = Color.White,
            background = palette.void,
            onBackground = palette.textPrimary,
            surface = palette.surfaceDark,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outlineDark,
            outlineVariant = palette.outlineSoft,
            error = palette.danger,
            onError = Color.White,
            surfaceContainerLowest = palette.void,
            surfaceContainerLow = palette.surfaceDark,
            surfaceContainer = palette.surfaceRaised,
            surfaceContainerHigh = palette.surfaceHigh,
            surfaceContainerHighest = palette.surfaceHigh
        )
    } else {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = palette.onAccent,
            primaryContainer = palette.signalDeep,
            onPrimaryContainer = palette.textPrimary,
            secondary = palette.violet,
            onSecondary = palette.irisDeep,
            secondaryContainer = palette.irisDeep,
            onSecondaryContainer = palette.textPrimary,
            tertiary = palette.ember,
            onTertiary = palette.signalDeep,
            background = palette.void,
            onBackground = palette.textPrimary,
            surface = palette.surfaceDark,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surfaceRaised,
            onSurfaceVariant = palette.textSecondary,
            surfaceTint = palette.accent,
            outline = palette.outlineDark,
            outlineVariant = palette.outlineSoft,
            error = palette.danger,
            onError = palette.void,
            errorContainer = palette.danger.copy(alpha = 0.18f),
            onErrorContainer = palette.textPrimary,
            scrim = Color(0xCC000000),
            surfaceBright = palette.surfaceHigh,
            surfaceDim = palette.void,
            surfaceContainerLowest = palette.void,
            surfaceContainerLow = palette.surfaceDark,
            surfaceContainer = palette.surfaceRaised,
            surfaceContainerHigh = palette.surfaceRaised,
            surfaceContainerHighest = palette.surfaceHigh
        )
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = palette.isLight
                isAppearanceLightNavigationBars = palette.isLight
            }
        }
    }
    val density = LocalDensity.current
    val safeScale = fontScale.coerceIn(0.8f, 1.35f)
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, density.fontScale * safeScale)
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PvpsTypography,
            shapes = PvpsShapes,
            content = content
        )
    }
}
