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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
    void = Color(0xFF08090A), surfaceDark = Color(0xFF0E1012),
    surfaceRaised = Color(0xFF15181B), surfaceHigh = Color(0xFF1D2125),
    outlineDark = Color(0xFF2A2F34), outlineSoft = Color(0xFF1A1E22),
    accent = Color(0xFFFF6A3D), onAccent = Color(0xFF1A0A04),
    accentDim = Color(0xFFC24E29), signalDeep = Color(0xFF3A1A10),
    violet = Color(0xFF9FB4FF), irisDeep = Color(0xFF1A2033),
    ember = Color(0xFFF2B35E), danger = Color(0xFFFF6B72),
    success = Color(0xFF57D6A6), textPrimary = Color(0xFFF2F1EC),
    textSecondary = Color(0xFFA8A8A2), textFaint = Color(0xFF8E908A)
)

val Midnight = PvpsPalette(
    id = "midnight", label = "Midnight", isLight = false,
    void = Color(0xFF06080D), surfaceDark = Color(0xFF0C1017),
    surfaceRaised = Color(0xFF10151E), surfaceHigh = Color(0xFF181F2B),
    outlineDark = Color(0xFF26303D), outlineSoft = Color(0xFF171E29),
    accent = Color(0xFF7DD3FC), onAccent = Color(0xFF04202B),
    accentDim = Color(0xFF4BA6CC), signalDeep = Color(0xFF0D2A38),
    violet = Color(0xFFC4B5FD), irisDeep = Color(0xFF211D38),
    ember = Color(0xFFF6C177), danger = Color(0xFFFF7B85),
    success = Color(0xFF63D6B0), textPrimary = Color(0xFFEAF0F8),
    textSecondary = Color(0xFFA0AAB8), textFaint = Color(0xFF8994A3)
)

val EmberTheme = PvpsPalette(
    id = "ember", label = "Ember", isLight = false,
    void = Color(0xFF0C0806), surfaceDark = Color(0xFF120E0B),
    surfaceRaised = Color(0xFF17110D), surfaceHigh = Color(0xFF211913),
    outlineDark = Color(0xFF382B21), outlineSoft = Color(0xFF241A14),
    accent = Color(0xFFFF9E5E), onAccent = Color(0xFF2A1205),
    accentDim = Color(0xFFC46F3A), signalDeep = Color(0xFF3B1D0C),
    violet = Color(0xFFD7B8FF), irisDeep = Color(0xFF2A1F38),
    ember = Color(0xFFE8B36B), danger = Color(0xFFFF8585),
    success = Color(0xFF9ED9A4), textPrimary = Color(0xFFF5EEE7),
    textSecondary = Color(0xFFB8A99B), textFaint = Color(0xFF9A8B7D)
)

val Paper = PvpsPalette(
    id = "paper", label = "Paper", isLight = true,
    void = Color(0xFFF2F0E9), surfaceDark = Color(0xFFF8F6F0),
    surfaceRaised = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFE9E5DA),
    outlineDark = Color(0xFFCFC9BC), outlineSoft = Color(0xFFE2DCD0),
    accent = Color(0xFFB43E0F), onAccent = Color(0xFFFFF8F3),
    accentDim = Color(0xFF7C2B0A), signalDeep = Color(0xFFF3D8C8),
    violet = Color(0xFF5B4A8F), irisDeep = Color(0xFFE7E0F5),
    ember = Color(0xFF8A5A16), danger = Color(0xFFA4262C),
    success = Color(0xFF176B4D), textPrimary = Color(0xFF1C1E1B),
    textSecondary = Color(0xFF575B54), textFaint = Color(0xFF5F635B)
)

val Themes = listOf(Graphite, Midnight, EmberTheme, Paper)

/** Palette id used to invalidate color-dependent text caches. */
val currentPaletteId: String get() = activePalette.id

// Snapshot-backed so every composable that reads one of the palette color
// getters is invalidated immediately when the user changes theme at runtime.
private val activePaletteState = mutableStateOf(Graphite)
private var activePalette: PvpsPalette
    get() = activePaletteState.value
    set(value) { activePaletteState.value = value }

fun paletteFor(id: String): PvpsPalette = Themes.firstOrNull { it.id == id } ?: Graphite

/** Apply the persisted palette before the first composition, avoiding a second full pass. */
fun primeTheme(id: String) {
    activePalette = paletteFor(id)
}

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
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun PvpsTheme(
    themeId: String = "graphite",
    fontScale: Float = 1f,
    uiScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val palette = paletteFor(themeId)
    SideEffect { activePalette = palette }
    val scheme = remember(palette) { if (palette.isLight) {
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
    } }
    val shapes = remember { PvpsShapes }
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
    val safeUiScale = uiScale.coerceIn(0.85f, 1.2f)
    CompositionLocalProvider(
        LocalDensity provides Density(
            density.density * safeUiScale,
            density.fontScale * safeScale
        )
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PvpsTypography,
            shapes = shapes,
            content = content
        )
    }
}
