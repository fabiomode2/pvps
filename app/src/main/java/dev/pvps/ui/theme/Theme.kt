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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * A complete color story for one theme. Besides the Material roles this carries
 * the few extra neutrals the visual language needs: hairlines, glass fills and
 * a soft accent wash for "quiet emphasis" surfaces.
 */
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
    val hairline: Color,
    val accent: Color,
    val onAccent: Color,
    val accentDim: Color,
    val accentSoft: Color,
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
    void = Color(0xFF08090B), surfaceDark = Color(0xFF0D0F13),
    surfaceRaised = Color(0xFF14171D), surfaceHigh = Color(0xFF1C2028),
    outlineDark = Color(0xFF262B35), outlineSoft = Color(0xFF171A20),
    hairline = Color(0x1AFFFFFF),
    accent = Color(0xFFD9F76B), onAccent = Color(0xFF111507),
    accentDim = Color(0xFFAEC64F), accentSoft = Color(0xFF3A4513),
    signalDeep = Color(0xFF232B12),
    violet = Color(0xFFB7A6FF), irisDeep = Color(0xFF201B38),
    ember = Color(0xFFF6C57C), danger = Color(0xFFFF7B8F),
    success = Color(0xFF62E0A8), textPrimary = Color(0xFFF3F1EA),
    textSecondary = Color(0xFFA9A79E), textFaint = Color(0xFF8C8A82)
)

val Midnight = PvpsPalette(
    id = "midnight", label = "Midnight", isLight = false,
    void = Color(0xFF05080F), surfaceDark = Color(0xFF0A101A),
    surfaceRaised = Color(0xFF101827), surfaceHigh = Color(0xFF172133),
    outlineDark = Color(0xFF23324A), outlineSoft = Color(0xFF141D2B),
    hairline = Color(0x1FFFFFFF),
    accent = Color(0xFF8FD8FF), onAccent = Color(0xFF03151F),
    accentDim = Color(0xFF54A8CF), accentSoft = Color(0xFF123246),
    signalDeep = Color(0xFF0E2C3D),
    violet = Color(0xFFC3B5FF), irisDeep = Color(0xFF211C3D),
    ember = Color(0xFFFFCE8A), danger = Color(0xFFFF8D9B),
    success = Color(0xFF84E9C5), textPrimary = Color(0xFFEAF2FF),
    textSecondary = Color(0xFF9CAEC8), textFaint = Color(0xFF7C8CA6)
)

val EmberTheme = PvpsPalette(
    id = "ember", label = "Ember", isLight = false,
    void = Color(0xFF0C0806), surfaceDark = Color(0xFF14100B),
    surfaceRaised = Color(0xFF1D1610), surfaceHigh = Color(0xFF292019),
    outlineDark = Color(0xFF3A2E23), outlineSoft = Color(0xFF241B13),
    hairline = Color(0x1FFFE7D0),
    accent = Color(0xFFFFB878), onAccent = Color(0xFF2B1403),
    accentDim = Color(0xFFD68A4D), accentSoft = Color(0xFF4A2C12),
    signalDeep = Color(0xFF3A2310),
    violet = Color(0xFFD9B8FF), irisDeep = Color(0xFF2E1F3D),
    ember = Color(0xFFFFD29A), danger = Color(0xFFFF8A8A),
    success = Color(0xFFAED8A0), textPrimary = Color(0xFFFFF1E7),
    textSecondary = Color(0xFFC0A997), textFaint = Color(0xFFA08A78)
)

val Paper = PvpsPalette(
    id = "paper", label = "Paper", isLight = true,
    void = Color(0xFFF5F3EC), surfaceDark = Color(0xFFFCFAF4),
    surfaceRaised = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFEDE9DC),
    outlineDark = Color(0xFFD6CFBF), outlineSoft = Color(0xFFE7E1D4),
    hairline = Color(0x14000000),
    accent = Color(0xFF3B4A20), onAccent = Color(0xFFF8FFE8),
    accentDim = Color(0xFF64783C), accentSoft = Color(0xFFDCE7C4),
    signalDeep = Color(0xFFE2EACB),
    violet = Color(0xFF6B5CA5), irisDeep = Color(0xFFEAE3FF),
    ember = Color(0xFF9A641C), danger = Color(0xFFB33145),
    success = Color(0xFF276B50), textPrimary = Color(0xFF1B1D17),
    textSecondary = Color(0xFF5A5D51), textFaint = Color(0xFF6F7264)
)

val Themes = listOf(Graphite, Midnight, EmberTheme, Paper)

// Snapshot-backed so every composable that reads one of the palette color
// getters is invalidated immediately when the user changes theme at runtime.
private val activePaletteState = mutableStateOf(Graphite)
private var activePalette: PvpsPalette
    get() = activePaletteState.value
    set(value) { activePaletteState.value = value }

fun paletteFor(id: String): PvpsPalette = Themes.firstOrNull { it.id == id } ?: Graphite

/** Full palette for composables that need several roles at once (gradients, glows). */
val LocalPvpsPalette = staticCompositionLocalOf { Graphite }

// Existing composables import these names directly. They are getters so a
// theme switch re-colors the whole app without touching hundreds of call sites.
val Void: Color get() = activePalette.void
val DeepBackground: Color get() = activePalette.void
val SurfaceDark: Color get() = activePalette.surfaceDark
val SurfaceRaised: Color get() = activePalette.surfaceRaised
val SurfaceHigh: Color get() = activePalette.surfaceHigh
val OutlineDark: Color get() = activePalette.outlineDark
val OutlineSoft: Color get() = activePalette.outlineSoft
val Hairline: Color get() = activePalette.hairline
val Accent: Color get() = activePalette.accent
val OnAccent: Color get() = activePalette.onAccent
val AccentDim: Color get() = activePalette.accentDim
val AccentSoft: Color get() = activePalette.accentSoft
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

/** Background of the whole app: a slightly lifted version of the void. */
val AppCanvas: Color get() = activePalette.surfaceDark

private val PvpsShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),
    small = RoundedCornerShape(Radius.sm),
    medium = RoundedCornerShape(Radius.md),
    large = RoundedCornerShape(Radius.lg),
    extraLarge = RoundedCornerShape(Radius.xl)
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
    val safeUiScale = uiScale.coerceIn(0.85f, 1.2f)
    CompositionLocalProvider(
        LocalPvpsPalette provides palette,
        LocalDensity provides Density(
            density.density * safeUiScale,
            density.fontScale * safeScale
        )
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = PvpsTypography,
            shapes = PvpsShapes,
            content = content
        )
    }
}
