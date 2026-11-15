package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaLimeDeep
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextTertiary

/**
 * **Layar pembuka NOXA** (v3.7.5).
 *
 * Before this existed, opening the app showed pure black until the feed
 * arrived — the app felt dead for a second and gave no sign it was NOXA. The
 * splash is deliberately short and quiet:
 *
 *  * a lime squircle mark whose three bars breathe like a playing equalizer,
 *  * the wordmark and a hairline "MUSIC",
 *  * a slow ambient glow and a travelling hairline at the bottom.
 *
 * It is a *brand* moment, not a loading screen with a spinner: the animation
 * runs on its own clock so it never flickers depending on network speed, and
 * it fades away instead of snapping.
 */
@Composable
fun NoxaSplash(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        exit = fadeOut(tween(420, easing = FastOutSlowInEasing)) +
            scaleOut(targetScale = 1.04f, animationSpec = tween(420)),
        modifier = modifier
    ) {
        val interaction = remember { MutableInteractionSource() }
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }

        val markScale by animateFloatAsState(
            targetValue = if (entered) 1f else 0.78f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = 320f),
            label = "splash_mark"
        )
        val markAlpha by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(420),
            label = "splash_alpha"
        )

        val infinite = rememberInfiniteTransition(label = "splash")
        val glow by infinite.animateFloat(
            initialValue = 0.30f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse),
            label = "splash_glow"
        )
        val sweep by infinite.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
            label = "splash_sweep"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NoxaBlack)
                // Swallows taps for the ~1.2 s the splash owns the screen.
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = {}
                )
                .testTag("noxa_splash")
        ) {
            // Ambient light: a cool wash from above and a warm lime pool under
            // the mark, so the black is never a flat rectangle.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                NoxaLime.copy(alpha = glow * 0.16f),
                                Color.Transparent
                            ),
                            center = Offset(0.5f, 0.42f),
                            radius = 900f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 72.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .graphicsLayer {
                            scaleX = markScale
                            scaleY = markScale
                            alpha = markAlpha
                        }
                        .shadow(24.dp, RoundedCornerShape(28.dp), clip = false, ambientColor = NoxaLime)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.linearGradient(listOf(NoxaLime, NoxaLimeDeep))
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Three bars that breathe at different rates — the same
                    // "now playing" language used across the app.
                    val bar1 by infinite.animateFloat(
                        initialValue = 0.35f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            tween(620, easing = LinearEasing), RepeatMode.Reverse
                        ),
                        label = "bar1"
                    )
                    val bar2 by infinite.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 0.4f,
                        animationSpec = infiniteRepeatable(
                            tween(480, easing = LinearEasing), RepeatMode.Reverse
                        ),
                        label = "bar2"
                    )
                    val bar3 by infinite.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 0.95f,
                        animationSpec = infiniteRepeatable(
                            tween(760, easing = LinearEasing), RepeatMode.Reverse
                        ),
                        label = "bar3"
                    )
                    Canvas(modifier = Modifier.size(44.dp)) {
                        val barWidth = size.width / 5f
                        val gap = barWidth * 0.9f
                        val heights = listOf(bar1, bar2, bar3)
                        heights.forEachIndexed { index, fraction ->
                            val left = index * (barWidth + gap)
                            val height = size.height * fraction
                            drawLine(
                                color = NoxaBlack,
                                start = Offset(left + barWidth / 2f, size.height / 2f - height / 2f),
                                end = Offset(left + barWidth / 2f, size.height / 2f + height / 2f),
                                strokeWidth = barWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "NOXA",
                    color = NoxaTextPrimary,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 8.sp,
                    modifier = Modifier.graphicsLayer { alpha = markAlpha }
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "M U S I C",
                    color = NoxaTextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 4.sp,
                    modifier = Modifier.graphicsLayer { alpha = markAlpha }
                )
            }

            // Footer: a hairline sweeping left to right — the only "loading"
            // signal, and it reads as a waveform rather than a progress bar.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 96.dp, vertical = 54.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.06f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.34f)
                            .height(2.dp)
                            .graphicsLayer { translationX = sweep * 520f }
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, NoxaLime, Color.Transparent)
                                )
                            )
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(NoxaLime)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "MENYIAPKAN MUSIK",
                        color = NoxaTextTertiary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.6.sp
                    )
                }
            }
        }
    }
}
