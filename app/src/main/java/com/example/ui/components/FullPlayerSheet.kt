package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subject
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.data.model.MusicSource
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import com.example.data.model.TrackLyrics
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
import com.example.ui.viewmodel.VideoUiState
import kotlinx.coroutines.flow.StateFlow

/** 16:9 frame for the music-video view (photos stay square). */
private const val VIDEO_ASPECT_RATIO = 16f / 9f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullPlayerSheet(
    visible: Boolean,
    playerState: PlayerUiState,
    progressFlow: StateFlow<PlaybackProgress>,
    lyricsState: LyricsUiState,
    videoUiState: VideoUiState,
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
    onSetVideoMode: (Boolean) -> Unit,
    onAttachVideoSurface: (SurfaceView?) -> Unit,
    /** Karaoke-style synced lyric line over the artwork (setting toggle). */
    karaokeOverlayEnabled: Boolean = true,
    /** Opens the artist profile from the now-playing artist line (nullable). */
    onOpenArtist: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }

    // ------------------------------------------------ spectrum visualizer
    // One icon in the top bar arms it; the style chips only surface while
    // it is on (iOS-ish: controls appear on demand, never clutter the bar).
    val context = LocalContext.current
    var spectrumOn by rememberSaveable { mutableStateOf(false) }
    var spectrumStyle by rememberSaveable { mutableStateOf(SpectrumStyle.BARS) }
    var spectrumNotice by remember { mutableStateOf<String?>(null) }
    val spectrumLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            spectrumNotice = null
            spectrumOn = true
        } else {
            spectrumOn = false
            spectrumNotice =
                "Izin mikrofon dipakai Android untuk spektrum audio — NOXA tidak pernah " +
                "mereka apapun. Tanpa izin, visualizer dimatikan."
        }
    }

    fun toggleSpectrum() {
        if (spectrumOn) {
            spectrumOn = false
            com.example.media.SpectrumCapture.stop()
            return
        }
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            spectrumOn = true
        } else {
            spectrumLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Bind/unbind capture to the player's audio session with the sheet.
    LaunchedEffect(spectrumOn, visible) {
        if (spectrumOn && visible) {
            val sessionId = com.example.NoxaApplication.instance.player.exoPlayer.audioSessionId
            if (sessionId <= 0) {
                spectrumOn = false
                spectrumNotice = "Putar lagu dulu — spektrum butuh audio yang sedang berjalan."
            } else if (!com.example.media.SpectrumCapture.start(sessionId)) {
                spectrumOn = false
                spectrumNotice = "Spektrum tidak tersedia di perangkat ini."
            }
        } else {
            com.example.media.SpectrumCapture.stop()
        }
    }
    DisposableEffect(Unit) {
        onDispose { com.example.media.SpectrumCapture.stop() }
    }

    // Foto vs Video MV: the video view exists while the stream is resolving
    // (spinner) or ready (surface).
    val isVideoActive = videoUiState is VideoUiState.Loading || videoUiState is VideoUiState.Ready

    AnimatedVisibility(
        visible = visible && playerState.currentSong != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        val song = playerState.currentSong ?: return@AnimatedVisibility

        // Tracks that can show a music video: native YouTube, or a Deezer track
        // that was matched to a YouTube video when its full audio was resolved.
        val canHaveMv = song.source == MusicSource.YOUTUBE || song.matchedVideoId.isNotBlank()

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
                            onClick = { toggleSpectrum() },
                            modifier = Modifier.testTag("full_player_spectrum_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Visualizer spektrum",
                                tint = if (spectrumOn) NoxaLime else NoxaTextPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
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
                }                // Artwork Card — or the music video when "Video MV" is selected.
                // MV-capable tracks get a vertical Foto/Video rail beside the art.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = if (canHaveMv) 0.dp else 14.dp)
                            .aspectRatio(if (isVideoActive) VIDEO_ASPECT_RATIO else 1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(NoxaSurfaceElevated)
                            .border(1.dp, NoxaBorder, RoundedCornerShape(24.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isVideoActive && videoUiState is VideoUiState.Ready -> {
                                // A separate muted player renders here; the main audio
                                // (and the notification controls) never pause.
                                AndroidView(
                                    factory = { context -> SurfaceView(context) },
                                    update = { surface -> onAttachVideoSurface(surface) },
                                    onRelease = { onAttachVideoSurface(null) },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            isVideoActive -> {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    color = NoxaLime,
                                    strokeWidth = 3.dp
                                )
                            }

                            else -> {
                                AsyncImage(
                                    model = song.artworkUrl,
                                    contentDescription = "Full player artwork",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Karaoke: the current (and next) synced line rides over
                        // the artwork, following the playback position.
                        if (!isVideoActive && karaokeOverlayEnabled &&
                            lyricsState is LyricsUiState.Ready
                        ) {
                            KaraokeOverlay(
                                lyrics = (lyricsState as LyricsUiState.Ready).lyrics,
                                progressFlow = progressFlow
                            )
                        }
                    }

                    if (canHaveMv) {
                        Spacer(modifier = Modifier.width(14.dp))
                        MediaModeRail(
                            videoSelected = isVideoActive,
                            onSelectVideo = { onSetVideoMode(true) },
                            onSelectPhoto = { onSetVideoMode(false) }
                        )
                    }
                }

                if (videoUiState is VideoUiState.Unavailable) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = videoUiState.message,
                        color = NoxaTextTertiary,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.height(if (canHaveMv) 14.dp else 28.dp))

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
                                overflow = TextOverflow.Ellipsis,
                                modifier = if (onOpenArtist != null) {
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable { onOpenArtist(song.artistId, song.artist) }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                } else {
                                    Modifier
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AudioQualityBadge(song = song)
                            if (PinnedTracks.isPinned(song)) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Lagu pin Sky",
                                    tint = NoxaLime,
                                    modifier = Modifier
                                        .size(15.dp)
                                        .testTag("pinned_star")
                                )
                            }
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

                Spacer(modifier = Modifier.height(16.dp))

                // Spectrum strip — appears only when armed, with its style
                // chips inline. A one-line hint replaces it when capture is
                // unavailable (permission missing / no signal yet).
                AnimatedVisibility(visible = spectrumOn) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SpectrumVisualizer(
                            style = spectrumStyle,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            SpectrumStyle.entries.forEach { entry ->
                                val selected = entry == spectrumStyle
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 4.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) NoxaLime.copy(alpha = 0.16f)
                                            else NoxaSurfaceSecondary
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) NoxaLime.copy(alpha = 0.55f)
                                            else NoxaBorder,
                                            RoundedCornerShape(14.dp)
                                        )
                                        .clickable { spectrumStyle = entry }
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        entry.label,
                                        color = if (selected) NoxaLime else NoxaTextTertiary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
                spectrumNotice?.let { notice ->
                    Text(
                        text = notice,
                        color = NoxaTextTertiary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

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
                            .noxaClickable { onTogglePlayPause() }
                            .testTag("full_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        // The glyph pops softly on every toggle — the small,
                        // alive feeling iOS transport buttons have.
                        val iconPop = remember { Animatable(1f) }
                        LaunchedEffect(playerState.isPlaying) {
                            iconPop.snapTo(0.72f)
                            iconPop.animateTo(
                                1f,
                                spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessHigh)
                            )
                        }
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
                                modifier = Modifier
                                    .size(36.dp)
                                    .graphicsLayer {
                                        scaleX = iconPop.value
                                        scaleY = iconPop.value
                                    }
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
                            Spacer(modifier = Modifier.height(3.dp))
                            if (playerState.sleepTimerMinutes != null) {
                                SleepTimerLabel(progressFlow)
                            } else {
                                Text(
                                    text = "Tidur",
                                    color = NoxaTextTertiary,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { showLyricsSheet = true },
                        modifier = Modifier.testTag("full_player_lyrics_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Subject,
                                contentDescription = "Lyrics",
                                tint = if (lyricsState is LyricsUiState.Ready) NoxaLime else NoxaTextSecondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Lirik",
                                color = NoxaTextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    IconButton(
                        onClick = { showQueueSheet = true },
                        modifier = Modifier.testTag("full_player_queue_btn")
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Queue",
                                tint = NoxaTextSecondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Antrian",
                                color = NoxaTextTertiary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Sky's words for a pinned track — the "why", under the controls.
                PinnedQuoteBlock(song = song)
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
                                if (PinnedTracks.isPinned(queueSong)) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "Lagu pin Sky",
                                        tint = NoxaLime,
                                        modifier = Modifier.size(13.dp)
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

/** Foto / Video MV as a vertical rail beside the artwork (MV-capable tracks). */
@Composable
private fun MediaModeRail(
    videoSelected: Boolean,
    onSelectVideo: () -> Unit,
    onSelectPhoto: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MediaModeChip(
            label = "Foto",
            icon = Icons.Default.Image,
            selected = !videoSelected,
            onClick = onSelectPhoto,
            tag = "player_mode_Foto"
        )
        MediaModeChip(
            label = "MV",
            icon = Icons.Default.Videocam,
            selected = videoSelected,
            onClick = onSelectVideo,
            tag = "player_mode_Video_MV"
        )
    }
}

@Composable
private fun MediaModeChip(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) NoxaLime else NoxaSurfaceElevated)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = NoxaBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 9.dp)
            .testTag(tag),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) NoxaBlack else NoxaTextSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = if (selected) NoxaBlack else NoxaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Karaoke overlay: the synced line being sung rides over the artwork in lime,
 * with the next line fading in below — so eyes can follow without a sheet.
 */
@Composable
private fun KaraokeOverlay(
    lyrics: TrackLyrics,
    progressFlow: StateFlow<PlaybackProgress>
) {
    if (!lyrics.isSynced) return
    val progress by progressFlow.collectAsState()
    val index = lyrics.indexAt(progress.positionMs)
    val current = lyrics.lines.getOrNull(index) ?: return
    val next = lyrics.lines.getOrNull(index + 1)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                )
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = current.text.ifBlank { "♪" },
                color = NoxaLime,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (next != null && next.text.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = next.text,
                    color = NoxaTextSecondary.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Sky's quote for a pinned track, shown beneath the transport controls — the
 * reason the song sits on the pin shelf in the first place.
 */
@Composable
private fun PinnedQuoteBlock(song: Song) {
    val pin = PinnedTracks.find(song) ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(NoxaLime.copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 3.dp)
                .testTag("pinned_quote_badge")
        ) {
            Text(
                text = "PINNED BY SKY",
                color = NoxaLime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "\u201C${pin.quote}\u201D",
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            fontStyle = FontStyle.Italic,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.testTag("pinned_quote_text")
        )
    }
}

@Composable
private fun AudioQualityBadge(song: Song) {
    // Audio always resolves to full length at play time (YouTube matcher +
    // on-device extractor). The badge is kept neutral — no provider names —
    // so NOXA reads as a standalone music app rather than a front for other
    // services.
    val label = "FULL"
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
