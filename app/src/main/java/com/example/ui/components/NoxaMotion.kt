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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NoxaAmbientLime
import com.example.ui.theme.NoxaAmbientWarm

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
 * Tap with an iOS-style light tick: the same soft press feedback as
 * [noxaClickable], plus a haptic pulse so taps on navigation and transport
 * controls feel physical. Uses the lightest feedback type so it never
 * becomes annoying.
 */
fun Modifier.noxaTap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    noxaClickable(enabled = enabled) {
        if (enabled) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        onClick()
    }
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

/**
 * Ambient backdrop behind a screen — the fix for a flat black canvas.
 *
 * Two very low-alpha radial glows (brand lime anchored top-start, a warm
 * ember bottom-end) bloom over pure black so the background has depth and a
 * sense of light source, the way Spotify/iOS dark modes breathe. It stays
 * subtle enough that text and album art keep full contrast; [content] draws
 * on top. Purely decorative and infinitely cheap (two static gradients).
 */
@Composable
fun NoxAmbientBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Lime bloom anchored top-start: the gradient centres inside its own
        // sub-box, so the glow sits near the header instead of mid-screen.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.55f)
                .align(Alignment.TopStart)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NoxaAmbientLime, Color.Transparent)
                    )
                )
        )
        // Warm ember bottom-end, same trick anchored to the far corner.
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.5f)
                .align(Alignment.BottomEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(NoxaAmbientWarm, Color.Transparent)
                    )
                )
        )
        content()
    }
}
