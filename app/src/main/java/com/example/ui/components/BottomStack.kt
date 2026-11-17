package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.media.PlaybackProgress
import com.example.ui.Screen
import com.example.ui.i18n.string
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaTextTertiary
import kotlinx.coroutines.flow.StateFlow

/**
 * The bottom of every main tab: a floating glass mini player hovering above its
 * own light navigation pill, with a real gap between them.
 *
 * ── v3.7.5: one surface, not two stickers ─────────────────────────────────
 * The player and the dock used to be two dark capsules dropped onto a flat
 * black strip, which read as pasted-on UI. Now they sit inside a **scrim**: a
 * vertical gradient that starts transparent and settles into the canvas, so the
 * shelf behind them fades away instead of ending at a hard edge. Each capsule
 * is more translucent, carries only a whisper of edge light, and the selected
 * tab gets an iOS-style tinted pill behind its icon rather than a floating
 * colour change. Nothing is drawn edge-to-edge, so the stack floats.
 */
@Composable
internal fun NoxaBottomStack(
    /** False on the NOXA AI screen: the composer needs that height. */
    miniPlayerVisible: Boolean = true,
    currentSong: Song?,
    isPlaying: Boolean,
    isBuffering: Boolean,
    isFavorite: Boolean,
    progressFlow: StateFlow<PlaybackProgress>,
    currentRoute: String?,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onOpenPlayer: () -> Unit,
    onNavigateTab: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Library,
        Screen.Equalizer,
        Screen.Profile
    )

    Box(modifier = modifier.fillMaxWidth()) {
        // The scrim: content slides under this, so the dock never meets a hard
        // rectangular edge. Three stops keep the fade smooth on OLED.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            NoxaBlack.copy(alpha = 0.55f),
                            NoxaBlack.copy(alpha = 0.92f),
                            NoxaBlack
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 15.dp)
        ) {
            AnimatedVisibility(
                visible = miniPlayerVisible,
                enter = fadeIn(tween(180)) + expandVertically(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Bottom
                ),
                exit = fadeOut(tween(140)) + shrinkVertically(
                    animationSpec = tween(200, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Bottom
                )
            ) {
                Column {
                    MiniPlayer(
                        currentSong = currentSong,
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        progressFlow = progressFlow,
                        isFavorite = isFavorite,
                        onTogglePlayPause = onTogglePlayPause,
                        onNext = onNext,
                        onToggleFavorite = onToggleFavorite,
                        onClick = onOpenPlayer,
                        // Floats clear of both edges: a panel, not a strip.
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                    )

                    if (currentSong != null) {
                        Spacer(modifier = Modifier.height(7.dp))
                    }
                }
            }

            // A true pill, wider and softer than before, sitting on the scrim
            // with no drawn outline — the edge comes from light, not a stroke.
            val navShape = RoundedCornerShape(percent = 50)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .shadow(10.dp, navShape, clip = false, ambientColor = Color.Black)
                    .clip(navShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                NoxaSurface.copy(alpha = 0.82f),
                                NoxaBlack.copy(alpha = 0.78f)
                            )
                        )
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.07f),
                                Color.Transparent
                            )
                        )
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomNavItems.forEach { screen ->
                        val isSelected = currentRoute == screen.route
                        val iconScale by animateFloatAsState(
                            targetValue = if (isSelected) 1.07f else 1f,
                            animationSpec = NoxaMotion.snappySpring,
                            label = "nav_icon_scale"
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(22.dp))
                                .noxaTap { onNavigateTab(screen) }
                                .padding(vertical = 4.dp)
                                .testTag("nav_item_${screen.route}"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // A tinted pill behind the active icon: iOS uses a
                            // soft fill for selection instead of recolouring
                            // the whole bar.
                            Box(
                                modifier = Modifier
                                    .size(width = 38.dp, height = 24.dp)
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (isSelected) NoxaLime.copy(alpha = 0.16f)
                                        else Color.Transparent
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) NoxaLime else NoxaTextTertiary,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .graphicsLayer {
                                            scaleX = iconScale
                                            scaleY = iconScale
                                        }
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when (screen.route) {
                                    Screen.Home.route -> string("nav.home")
                                    Screen.Search.route -> string("nav.search")
                                    Screen.Library.route -> string("nav.library")
                                    Screen.Profile.route -> string("nav.profile")
                                    else -> screen.title
                                },
                                color = if (isSelected) NoxaLime else NoxaTextTertiary,
                                fontSize = 9.5.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}
