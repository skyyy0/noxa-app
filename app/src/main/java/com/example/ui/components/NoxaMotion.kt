package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import android.provider.Settings
import com.example.ui.util.FormatUtils
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
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.965f else 1f,
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
fun Modifier.noxaTap(
    enabled: Boolean = true,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onClick: () -> Unit
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    noxaClickable(enabled = enabled) {
        if (enabled) haptic.performHapticFeedback(hapticType)
        onClick()
    }
}

/**
 * Same soft press feedback as [noxaTap] but with the heavier tick reserved
 * for consequential actions — transport controls, play/pause, destructive
 * confirms — so they read as more important than a plain navigation tap.
 */
fun Modifier.noxaStrongTap(enabled: Boolean = true, onClick: () -> Unit): Modifier =
    noxaTap(enabled = enabled, hapticType = HapticFeedbackType.LongPress, onClick = onClick)

/**
 * [noxaClickable] for rows that also support a long-press gesture (the song
 * row quick-menu): same dip-under-finger scale, a light tick on tap and the
 * heavier long-press tick, all while keeping the material ripple.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.noxaCombinedClickable(
    enabled: Boolean = true,
    hapticType: HapticFeedbackType = HapticFeedbackType.TextHandleMove,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduced) 0.975f else 1f,
        animationSpec = NoxaMotion.pressSpring,
        label = "rowPressScale"
    )
    val longHandler: (() -> Unit)? = onLongClick?.let { original ->
        {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            original()
        }
    }
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            enabled = enabled,
            onLongClick = longHandler,
            onClick = {
                if (enabled) haptic.performHapticFeedback(hapticType)
                onClick()
            }
        )
}

/**
 * Gentle entrance: fade in while rising a few pixels, iOS-style. Applied to
 * shelf cards so a row of cards materialises instead of popping in at once;
 * [delayMillis] staggers neighbouring cards.
 */
fun Modifier.noxaAppear(delayMillis: Int = 0): Modifier = composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val reduced = rememberReducedMotion()
    val alpha = remember { Animatable(if (reduced) 1f else 0f) }
    val rise = remember { Animatable(if (reduced) 0f else 16f) }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
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

/* =========================================================================
 * Micro-detail layer — the small, iOS-flavoured touches applied all over
 * the app: motion preferences, state pops, counting numbers, breathing art
 * and scrubber ticks.
 * ======================================================================= */

/**
 * True when the system's accessibility "Remove animations" switch is on
 * (Android exposes it as the global transition animation scale being 0).
 *
 * Every NOXA entrance, pop and breathing loop reads this so the app stays
 * completely usable — and still — for people who asked for less motion.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                1f
            )
        }.getOrDefault(1f) == 0f
    }
}

/**
 * The little pop a control makes when it changes state — the heart filling
 * in, a follow button, a checkmark appearing. Overshoots once, then settles,
 * the way iOS springs a newly-activated control to life. Fires only on a
 * real transition so recomposition or scrolling never re-triggers it.
 */
fun Modifier.noxaPop(trigger: Boolean): Modifier = composed {
    val reduced = rememberReducedMotion()
    val scale = remember { Animatable(1f) }
    var wasTrigger by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger, reduced) {
        if (trigger == wasTrigger) return@LaunchedEffect
        wasTrigger = trigger
        if (reduced) {
            scale.snapTo(1f)
            return@LaunchedEffect
        }
        if (trigger) {
            scale.snapTo(0.62f)
            scale.animateTo(
                1f,
                spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessHigh)
            )
        } else {
            scale.animateTo(1f, NoxaMotion.snappySpring)
        }
    }
    this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Breathing loop for the now-playing artwork: an almost imperceptible
 * 1.0 → 1.018 swell on a slow in-out cycle while audio runs, freezing the
 * moment playback pauses. Cheap (one float animation) but it makes the
 * player feel alive instead of like a static poster.
 */
fun Modifier.noxaBreathe(active: Boolean): Modifier = composed {
    val reduced = rememberReducedMotion()
    val breathing = active && !reduced
    // A single shared infinite transition (the pattern every other looping
    // animation in NOXA uses) instead of a hand-rolled `while(true)`
    // animateTo chain: the coroutine version never signalled idleness to the
    // Robolectric/Espresso test harness, which kept the smoke test waiting
    // for Compose to settle even though the visual result is identical.
    val transition = rememberInfiniteTransition(label = "noxa_breathe")
    val swell by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.018f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "noxa_breathe_swell"
    )
    this.graphicsLayer {
        val s = if (breathing) swell else 1f
        scaleX = s
        scaleY = s
    }
}

/**
 * A number that races up to its value the first time it appears — the way
 * iOS stats tick into place instead of just being there. Used for monthly
 * listeners, follower counts and chart positions.
 */
@Composable
fun NoxaCountUp(
    target: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    format: (Long) -> String = { FormatUtils.formatCount(it) }
) {
    val reduced = rememberReducedMotion()
    val value = remember(target) { Animatable(0f) }
    LaunchedEffect(target, reduced) {
        if (reduced || target <= 0) {
            value.snapTo(target.toFloat())
        } else {
            value.animateTo(
                target.toFloat(),
                tween(durationMillis = 750, easing = FastOutSlowInEasing)
            )
        }
    }
    Text(
        text = format(value.value.toLong()),
        modifier = modifier,
        style = style,
        color = color
    )
}

/**
 * iOS-style scrubber ticks: feed the current drag fraction (0f..1f) to the
 * returned lambda and it fires one light haptic each time the finger crosses
 * another [stepPercent] of the track, so scrubbing feels like it has
 * detents under the finger instead of sliding on glass.
 */
@Composable
fun rememberScrubTicker(stepPercent: Float = 0.05f): (Float) -> Unit {
    val haptic = LocalHapticFeedback.current
    var lastBucket by remember { mutableStateOf(-1) }
    return { fraction ->
        val bucket = (fraction.coerceIn(0f, 1f) / stepPercent).toInt()
        if (bucket != lastBucket) {
            lastBucket = bucket
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
}
