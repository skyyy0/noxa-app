package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Motion helpers tuned to feel like iOS: soft springs, short travel distances
 * and gentle press feedback instead of harsh linear moves.
 */
object NoxaMotion {

    /** Settling spring used for presses and small pops (slight overshoot). */
    val pressSpring = spring<Float>(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    /** Snappy spring for buttons that should feel instant but soft. */
    val snappySpring = spring<Float>(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessHigh
    )
}

/**
 * Press feedback in the iOS spirit: the element dips slightly under the
 * finger and springs back, using this modifier in place of a plain
 * [clickable] so taps keep the ripple from the material theme.
 */
fun Modifier.noxaClickable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.965f else 1f,
        animationSpec = NoxaMotion.pressSpring,
        label = "pressScale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/**
 * Gentle entrance: fade in while rising a few pixels, iOS-style. Applied to
 * shelf cards so a row of cards materialises instead of popping in at once;
 * [delayMillis] staggers neighbouring cards.
 */
fun Modifier.noxaAppear(delayMillis: Int = 0): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val alpha = remember { Animatable(0f) }
    val rise = remember { Animatable(16f) }
    LaunchedEffect(Unit) {
        delay(delayMillis.toLong())
        launch {
            alpha.animateTo(1f, tween(durationMillis = 380, easing = FastOutSlowInEasing))
        }
        launch {
            rise.animateTo(
                0f,
                spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessMediumLow)
            )
        }
    }
    this.graphicsLayer {
        this.alpha = alpha.value
        translationY = rise.value * density.density
    }
}
