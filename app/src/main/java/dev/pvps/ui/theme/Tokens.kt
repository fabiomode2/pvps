package dev.pvps.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Design tokens for the second-generation visual language.
 *
 * Rules of thumb:
 *  - Spacing is always a token, never an arbitrary dp value.
 *  - Radii grow with the surface size: chips 12, rows 18, cards 24, sheets 30.
 *  - Exactly one accent-filled element per screen. Everything else is neutral.
 */
object Space {
    val xxs: Dp = 2.dp
    val xs: Dp = 4.dp
    val sm: Dp = 8.dp
    val md: Dp = 12.dp
    val lg: Dp = 16.dp
    val xl: Dp = 22.dp
    val xxl: Dp = 30.dp
    val huge: Dp = 44.dp

    /** Horizontal page gutter. Wider screens get a little more breathing room. */
    val gutter: Dp = 18.dp
    val gutterWide: Dp = 28.dp

    /** Vertical gap between top-level blocks. */
    val section: Dp = 26.dp
    val block: Dp = 14.dp

    /** Clearance for the floating dock. */
    val dockClearance: Dp = 30.dp
}

object Radius {
    val xs: Dp = 10.dp
    val sm: Dp = 14.dp
    val md: Dp = 18.dp
    val lg: Dp = 24.dp
    val xl: Dp = 30.dp
    val hero: Dp = 34.dp
    val pill: Dp = 100.dp
}

/** Default radius for raised content cards. */
val CardRadius: Dp = Radius.lg

object Motion {
    const val fastMs = 160
    const val baseMs = 260
    const val slowMs = 420

    val fast = tween<Float>(fastMs)
    val base = tween<Float>(baseMs)

    fun <T> press(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessHigh)

    fun <T> settle(): androidx.compose.animation.core.FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow)
}

/** Hairline that catches the light on the top edge of a surface. */
fun topEdgeLight(strength: Float = 0.10f): Brush = Brush.verticalGradient(
    0f to Color.White.copy(alpha = strength),
    1f to Color.White.copy(alpha = 0.012f)
)

/** Fill used by raised cards: a barely-there vertical wash instead of a flat tint. */
fun cardFill(top: Color, bottom: Color): Brush = Brush.verticalGradient(
    listOf(top, bottom)
)

/**
 * Tactile press feedback: springs the element down a hair and dims it, with no
 * ripple (`indication = null`). Used for every tappable surface in the app so
 * touch feedback is consistent and quiet.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    scaleDown: Float = 0.968f,
    onClickLabel: String? = null,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) scaleDown else 1f,
        animationSpec = Motion.press(),
        label = "pressScale"
    )
    return this
        .scale(scale)
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
}
