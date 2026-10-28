package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaTextTertiary
import kotlinx.coroutines.flow.StateFlow

/**
 * The bottom of every main tab: a floating glass mini player hovering above its
 * own light navigation pill, with a real gap between them.
 *
 * ── Two surfaces, one system ─────────────────────────────────────────────
 * Both wear the same material — translucent sheet, top sheen, hairline edge,
 * soft shadow — so they read as siblings instead of as one heavy slab. Each
 * keeps its own boundary, its own shape and its own inset, which is what keeps
 * the stack feeling light. The pill is rounded to half its own height and the
 * active tab is marked by colour alone, so nothing in the bar inflates its
 * height or squares off its silhouette.
 *
 * Extracted from NoxaApp so a screenshot test can render the real thing.
 */
@Composable
internal fun NoxaBottomStack(
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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
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
            // Inset from both edges: the panel floats, it is not a
            // full-width strip welded to the screen.
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )

        // The gap only exists while a song is loaded — a standing
        // spacer would lift the bar for no reason.
        if (currentSong != null) {
            Spacer(modifier = Modifier.height(6.dp))
        }

        // A true pill, not a slab: rounded to half its own height,
        // a whisper of shadow and a sheen instead of an outline.
        val navShape = RoundedCornerShape(percent = 50)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .shadow(7.dp, navShape, clip = false)
                .clip(navShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NoxaSurface.copy(alpha = 0.95f),
                            NoxaSurface.copy(alpha = 0.88f)
                        )
                    )
                )
                // Light gathers along the top edge and is gone by
                // the time it reaches the icons: the glass cue,
                // kept cheap enough to run every frame.
                .background(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
                    )
                )
                // Hairline only where the light would actually
                // catch — no full outline, so the bar reads round.
                .border(
                    1.dp,
                    Brush.verticalGradient(
                        listOf(
                            Color.White.copy(alpha = 0.10f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    ),
                    navShape
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomNavItems.forEach { screen ->
                    val isSelected = currentRoute == screen.route
                    // The selected icon settles a touch larger on a
                    // spring — a living detail without any flashing.
                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = NoxaMotion.snappySpring,
                        label = "nav_icon_scale"
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .noxaTap { onNavigateTab(screen) }
                            .padding(vertical = 6.dp)
                            .testTag("nav_item_${screen.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = screen.icon,
                            contentDescription = screen.title,
                            tint = if (isSelected) NoxaLime else NoxaTextTertiary,
                            modifier = Modifier
                                .size(21.dp)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                }
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        // Labels are always present: an evenly weighted,
                        // quiet bar instead of a label that expands and
                        // shoves its neighbours around.
                        Text(
                            text = when (screen.route) {
                                Screen.Home.route -> string("nav.home")
                                Screen.Search.route -> string("nav.search")
                                Screen.Library.route -> string("nav.library")
                                Screen.Profile.route -> string("nav.profile")
                                else -> screen.title
                            },
                            // The active tab speaks in lime — the
                            // indicator is the colour itself, so no
                            // extra row of dots inflates the height.
                            color = if (isSelected) NoxaLime else NoxaTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Breathing room under the pill so it never sits glued to
        // the gesture inset.
        Spacer(modifier = Modifier.height(8.dp))
    }
}
