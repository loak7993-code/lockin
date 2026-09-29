package com.lockin.focus.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * M3 Expressive motion. One emphasized easing family, used with intent:
 * entrances decelerate, exits accelerate, and state changes spring.
 */
object Motion {
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val DURATION_SHORT = 200
    const val DURATION_MEDIUM = 300
    const val DURATION_LONG = 450

    val SpringGentle = spring<Float>(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
    val SpringSnappy = spring<Float>(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium)
    val SpringBouncy = spring<Float>(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
}

/** M3 shape scale, with the extra-large corner the card surfaces use. */
val LockInShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Cards and sheets in this app read as tiles, not documents: one large radius. */
val TileShape = RoundedCornerShape(28.dp)
val PillShape = RoundedCornerShape(percent = 50)
