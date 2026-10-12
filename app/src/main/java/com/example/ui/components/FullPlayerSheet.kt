package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.data.model.Song
import com.example.media.PlaybackProgress
import com.example.media.PlayerUiState
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.LyricsUiState
import kotlinx.coroutines.flow.StateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    visible: Boolean,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    lyricsState: LyricsUiState,
    isFavorite: Boolean,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSetSleepTimer: (Int?) -> Unit,
    onOpenEqualizer: () -> Unit,
    onSelectQueueItem: (Song, Int) -> Unit,
    onShare: () -> Unit,
    onRetryLyrics: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible && playerState.currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val song = playerState.currentSong ?: return@AnimatedVisibility

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(NoxaBlack)
                .statusBarsPadding()
                .testTag("full_player_sheet")
        ) {
            // Ambient subtle radial gradient glow from top
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                NoxaLime.copy(alpha = 0.08f),
                                Color.Transparent
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("full_player_close_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Collapse",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "PLAYING FROM",
                            color = NoxaTextTertiary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = song.album,
                            color = NoxaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onShare,
                            modifier = Modifier.testTag("full_player_share_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Bagikan lagu",
                                tint = NoxaTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = onOpenEqualizer,
                            modifier = Modifier.testTag("full_player_eq_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Equalizer",
                                tint = NoxaTextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Artwork Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(NoxaSurfaceElevated)
                        .border(1.dp, NoxaBorder, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = song.artworkUrl,
                        contentDescription = "Full player artwork",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Title & Artist & Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.title,
                            color = NoxaTextPrimary,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = song.artist,
                                color = NoxaTextSecondary,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AudioQualityBadge(isPreview = song.isPreview)
                        }
                    }

                    IconButton(
                        onClick = onToggleFavorite,
                        modifier = Modifier.testTag("full_player_favorite_btn")
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFavorite) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Progress. Only this section reads the fast position flow.
                PlayerProgressSection(
                    progressFlow = progressFlow,
                    fallbackDurationMs = song.durationMs,
                    onSeekTo = onSeekTo
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Main Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onToggleShuffle,
                        modifier = Modifier.testTag("full_player_shuffle_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (playerState.isShuffle) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.testTag("full_player_prev_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(NoxaLime)
                            .clickable { onTogglePlayPause() }
                            .testTag("full_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Color.Black,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = Color.Black,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.testTag("full_player_next_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(
                        onClick = onToggleRepeat,
                        modifier = Modifier.testTag("full_player_repeat_btn")
                    ) {
                        val isOne = playerState.repeatMode == Player.REPEAT_MODE_ONE
                        val isAll = playerState.repeatMode == Player.REPEAT_MODE_ALL
                        Icon(
                            imageVector = if (isOne) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = if (isOne || isAll) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Secondary Actions (Sleep Timer, Lyrics, Queue)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showSleepTimerDialog = true },
                        modifier = Modifier.testTag("full_player_sleep_timer_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.NightsStay,
                                contentDescription = "Sleep Timer",
                                tint = if (playerState.sleepTimerMinutes != null) NoxaLime else NoxaTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            if (playerState.sleepTimerMinutes != null) {
                                SleepTimerLabel(progressFlow)
                            }
                        }
                    }

                    IconButton(
                        onClick = { showLyricsSheet = true },
                        modifier = Modifier.testTag("full_player_lyrics_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Subject,
                            contentDescription = "Lyrics",
                            tint = if (lyricsState is LyricsUiState.Ready) NoxaLime else NoxaTextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.testTag("full_player_queue_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = NoxaTextSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // Lyrics Bottom Sheet — synchronised with the real playback position.
        if (showLyricsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showLyricsSheet = false },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Lirik",
                        color = NoxaTextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${song.title} — ${song.artist}",
                        color = NoxaTextTertiary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LyricsPanel(
                        lyricsState = lyricsState,
                        progressFlow = progressFlow,
                        onSeekTo = onSeekTo,
                        onRetry = onRetryLyrics
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        // Queue Bottom Sheet
        if (showQueueSheet) {
            ModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Antrian (${playerState.queue.size})",
                        color = NoxaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyColumn(modifier = Modifier.height(320.dp)) {
                        itemsIndexed(playerState.queue, key = { _, item -> item.id }) { index, queueSong ->
                            val isCurrent = index == playerState.queueIndex
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isCurrent) NoxaSurfaceSecondary else Color.Transparent)
                                    .clickable {
                                        onSelectQueueItem(queueSong, index)
                                        showQueueSheet = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = if (isCurrent) NoxaLime else NoxaTextTertiary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(28.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = queueSong.title,
                                        color = if (isCurrent) NoxaLime else NoxaTextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = queueSong.artist,
                                        color = NoxaTextSecondary,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (queueSong.isPreview) {
                                    Text(
                                        text = "30s",
                                        color = NoxaTextTertiary,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sleep Timer Bottom Sheet
        if (showSleepTimerDialog) {
            ModalBottomSheet(
                onDismissRequest = { showSleepTimerDialog = false },
                containerColor = NoxaSurface,
                sheetState = rememberModalBottomSheetState()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Sleep Timer",
                        color = NoxaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    listOf(
                        "Matikan" to null,
                        "15 Menit" to 15,
                        "30 Menit" to 30,
                        "45 Menit" to 45,
                        "60 Menit" to 60
                    ).forEach { (label, mins) ->
                        val isSelected = playerState.sleepTimerMinutes == mins
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) NoxaSurfaceSecondary else Color.Transparent)
                                .clickable {
                                    onSetSleepTimer(mins)
                                    showSleepTimerDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) NoxaLime else NoxaTextPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The seek bar and its two labels.
 *
 * Deliberately its own composable: the playback position changes twice a second,
 * and only this subtree re-reads it while the rest of the sheet stays still.
 */
@Composable
private fun PlayerProgressSection(
    progressFlow: StateFlow<PlaybackProgress>,
    fallbackDurationMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val progress by progressFlow.collectAsState()
    var dragValue by remember { mutableStateOf<Float?>(null) }

    val totalDuration = when {
        progress.durationMs > 0 -> progress.durationMs
        else -> fallbackDurationMs
    }
    val position = dragValue?.toLong() ?: progress.positionMs

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = position.toFloat().coerceIn(0f, totalDuration.toFloat().coerceAtLeast(1f)),
            onValueChange = { dragValue = it },
            onValueChangeFinished = {
                dragValue?.let { onSeekTo(it.toLong()) }
                dragValue = null
            },
            valueRange = 0f..totalDuration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = NoxaLime,
                activeTrackColor = NoxaLime,
                inactiveTrackColor = NoxaSurfaceSecondary
            ),
            modifier = Modifier.testTag("full_player_slider")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = FormatUtils.formatDuration(position),
                color = NoxaTextTertiary,
                fontSize = 12.sp
            )
            Text(
                text = FormatUtils.formatDuration(totalDuration),
                color = NoxaTextTertiary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SleepTimerLabel(progressFlow: StateFlow<PlaybackProgress>) {
    val progress by progressFlow.collectAsState()
    val remaining = progress.sleepTimerRemainingSeconds ?: return
    Text(
        text = "${remaining / 60}m",
        color = NoxaLime,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold
    )
}

/**
 * Tells the truth about the audio: Audius serves the full track, Deezer legally
 * only allows a 30 second clip.
 */
@Composable
private fun AudioQualityBadge(isPreview: Boolean) {
    val label = if (isPreview) "PREVIEW 30s" else "FULL TRACK"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(NoxaLime.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = NoxaLime,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
