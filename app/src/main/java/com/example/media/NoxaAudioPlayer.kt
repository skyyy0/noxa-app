package com.example.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CountDownTimer
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.data.local.HistorySongEntity
import com.example.data.local.NoxaDatabase
import com.example.data.local.PlaybackSession
import com.example.data.local.PlaybackSessionStore
import com.example.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The slow-changing player state: everything a screen needs to draw itself.
 *
 * The playback position deliberately does **not** live here. It changes twice a
 * second, and keeping it in this object made every screen that observes the
 * player rebuild on every tick — which is what made the home screen lag. Use
 * [NoxaAudioPlayer.progress] for anything time based.
 */
data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleepTimerMinutes: Int? = null,
    /** Honest error surfaced to the UI (never hidden behind simulated playback). */
    val errorMessage: String? = null,
    val equalizerState: EqualizerState = EqualizerState()
)

/** The fast-changing player state: only the leaf composables that need it read it. */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val sleepTimerRemainingSeconds: Long? = null
) {
    val fraction: Float
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
}

/**
 * The single playback facade used by the whole UI.
 *
 * There is exactly one audio engine: **Media3 ExoPlayer streaming a real audio
 * URL**, and — importantly — the **queue lives inside ExoPlayer itself**
 * (`setMediaItems`). That is what makes the system media notification complete:
 * Android only offers previous/next and a working seek bar when the player
 * really has a playlist, because the System UI builds those controls from the
 * player's available commands. Keeping the queue in app state instead is exactly
 * why the notification showed up with only a play/pause button.
 */
class NoxaAudioPlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val database = NoxaDatabase.getDatabase(context)
    private val sessionStore = PlaybackSessionStore(context)
    val equalizerManager = EqualizerManager()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        // Keeps network streaming alive while the app is in the background.
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val _progress = MutableStateFlow(PlaybackProgress())
    val progress: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    /**
     * Full song objects keyed by the media id that is stored inside each
     * [MediaItem]. ExoPlayer only carries ids and metadata, so this is how the
     * queue is mapped back onto the app model.
     */
    private val songsById = HashMap<String, Song>()

    private var positionUpdateJob: Job? = null
    private var sleepTimer: CountDownTimer? = null
    private var lastSessionSaveMs = 0L

    init {
        setupPlayerListener()
        equalizerManager.init(exoPlayer.audioSessionId)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) startPositionUpdater() else stopPositionUpdater()
                if (!isPlaying) persistSession(force = true)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val song = mediaItem?.mediaId?.let { songsById[it] }
                if (song != null) {
                    _uiState.update {
                        it.copy(
                            currentSong = song,
                            durationMs = if (song.durationMs > 0) song.durationMs else it.durationMs,
                            errorMessage = null
                        )
                    }
                    _progress.update { it.copy(positionMs = 0L) }
                    recordHistory(song)
                }
                syncQueueFromPlayer()
                persistSession(force = true)
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                syncQueueFromPlayer()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(isShuffle = shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val duration = if (exoPlayer.duration > 0) {
                    exoPlayer.duration
                } else {
                    _uiState.value.currentSong?.durationMs ?: 0L
                }
                _uiState.update { it.copy(isBuffering = isBuffering, durationMs = duration) }
                _progress.update { it.copy(durationMs = duration) }

                if (playbackState == Player.STATE_ENDED) {
                    stopPositionUpdater()
                    _progress.update { it.copy(positionMs = 0L) }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("NoxaAudioPlayer", "Playback error: ${error.message}", error)
                _uiState.update {
                    it.copy(
                        isBuffering = false,
                        isPlaying = false,
                        errorMessage = "Gagal memutar audio (${error.errorCodeName}). Periksa koneksi internet."
                    )
                }
            }
        })
    }

    /** Mirrors ExoPlayer's timeline back into [PlayerUiState.queue]. */
    private fun syncQueueFromPlayer() {
        val count = exoPlayer.mediaItemCount
        val songs = (0 until count).mapNotNull { index ->
            songsById[exoPlayer.getMediaItemAt(index).mediaId]
        }
        _uiState.update {
            it.copy(
                queue = if (songs.isNotEmpty()) songs else it.queue,
                queueIndex = exoPlayer.currentMediaItemIndex.coerceAtLeast(0),
                isShuffle = exoPlayer.shuffleModeEnabled,
                repeatMode = exoPlayer.repeatMode
            )
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        val songs = if (queue.isEmpty()) listOf(song) else queue
        val targetIndex = if (song.id in songs.map { it.id }) {
            songs.indexOfFirst { it.id == song.id }
        } else {
            index
        }.coerceIn(0, songs.size - 1)

        songs.forEach { songsById[it.id] = it }

        _uiState.update {
            it.copy(
                currentSong = song,
                queue = songs,
                queueIndex = targetIndex,
                durationMs = song.durationMs,
                isBuffering = true,
                isPlaying = false,
                errorMessage = null
            )
        }
        _progress.update { PlaybackProgress(positionMs = 0L, durationMs = song.durationMs) }

        if (!isPlayable(song.streamUrl)) {
            _uiState.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    errorMessage = "Lagu ini tidak memiliki sumber audio yang bisa diputar."
                )
            }
            return
        }

        // The whole queue goes into ExoPlayer: that is what gives the system
        // notification its previous/next buttons and its seek bar.
        exoPlayer.setMediaItems(songs.map { it.toMediaItem() }, targetIndex, C.TIME_UNSET)
        exoPlayer.prepare()
        exoPlayer.play()

        // Reconnect the equalizer to the (possibly new) audio session.
        equalizerManager.init(exoPlayer.audioSessionId)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }

        // The MediaSession lives in the service; starting it enables the system
        // media notification, lock screen controls and background playback.
        startPlaybackService()
        recordHistory(song)
        syncQueueFromPlayer()
        persistSession(force = true)
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(Uri.parse(streamUrl))
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setIsPlayable(true)
                .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
                .build()
        )
        .build()

    private fun recordHistory(song: Song) {
        scope.launch(Dispatchers.IO) {
            try {
                database.noxaDao().insertHistory(HistorySongEntity.fromSong(song))
            } catch (e: Exception) {
                Log.w("NoxaAudioPlayer", "Could not record history: ${e.message}")
            }
        }
    }

    private fun startPlaybackService() {
        try {
            context.startService(Intent(context, MediaPlaybackService::class.java))
        } catch (e: Exception) {
            Log.w("NoxaAudioPlayer", "Could not start MediaPlaybackService: ${e.message}")
        }
    }

    /** Plays [song] right after the current one. */
    fun playNext(song: Song) {
        if (exoPlayer.mediaItemCount == 0) {
            playSong(song)
            return
        }
        songsById[song.id] = song
        val insertAt = (exoPlayer.currentMediaItemIndex + 1).coerceIn(0, exoPlayer.mediaItemCount)
        exoPlayer.addMediaItem(insertAt, song.toMediaItem())
        syncQueueFromPlayer()
        persistSession(force = true)
    }

    /** Appends [song] to the end of the queue. */
    fun addToQueue(song: Song) {
        if (exoPlayer.mediaItemCount == 0) {
            playSong(song)
            return
        }
        val alreadyQueued = (0 until exoPlayer.mediaItemCount)
            .any { exoPlayer.getMediaItemAt(it).mediaId == song.id }
        if (alreadyQueued) return
        songsById[song.id] = song
        exoPlayer.addMediaItem(song.toMediaItem())
        syncQueueFromPlayer()
        persistSession(force = true)
    }

    /**
     * Replaces the queued media item for [song] with its freshly resolved
     * stream URL (YouTube tracks resolve lazily, one by one, after playback
     * already started). The currently playing item is never touched — it is
     * already streaming from its own URL.
     */
    fun updateSongStream(song: Song) {
        if (!isPlayable(song.streamUrl)) return
        val index = (0 until exoPlayer.mediaItemCount)
            .firstOrNull { exoPlayer.getMediaItemAt(it).mediaId == song.id } ?: return
        if (index == exoPlayer.currentMediaItemIndex) return
        songsById[song.id] = song
        exoPlayer.replaceMediaItem(index, song.toMediaItem())
        syncQueueFromPlayer()
    }

    /** Jumps straight to a queue position. */
    fun playQueueIndex(index: Int) {
        if (index !in 0 until exoPlayer.mediaItemCount) return
        exoPlayer.seekTo(index, 0L)
        if (!exoPlayer.isPlaying) exoPlayer.play()
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_IDLE || exoPlayer.mediaItemCount == 0) {
                _uiState.value.currentSong?.let { playSong(it) }
            } else {
                exoPlayer.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceIn(0L, _uiState.value.durationMs.coerceAtLeast(1L))
        exoPlayer.seekTo(clamped)
        _progress.update { it.copy(positionMs = clamped) }
    }

    fun next() {
        if (exoPlayer.hasNextMediaItem()) {
            exoPlayer.seekToNextMediaItem()
        } else if (exoPlayer.repeatMode == Player.REPEAT_MODE_ALL && exoPlayer.mediaItemCount > 0) {
            exoPlayer.seekTo(0, 0L)
        }
    }

    fun previous() {
        if (_progress.value.positionMs > 3000 || !exoPlayer.hasPreviousMediaItem()) {
            seekTo(0L)
            return
        }
        exoPlayer.seekToPreviousMediaItem()
    }

    fun toggleShuffle() {
        exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
    }

    /**
     * Repeat cycles off -> all -> one, exactly like the notification's repeat
     * button. ExoPlayer owns this now, so the in-app button and the system
     * control always agree.
     */
    fun toggleRepeat() {
        exoPlayer.repeatMode = when (exoPlayer.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimer?.cancel()
        sleepTimer = null

        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerMinutes = null) }
            _progress.update { it.copy(sleepTimerRemainingSeconds = null) }
            return
        }

        val totalMs = minutes * 60 * 1000L
        _uiState.update { it.copy(sleepTimerMinutes = minutes) }
        _progress.update { it.copy(sleepTimerRemainingSeconds = totalMs / 1000) }

        sleepTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _progress.update { it.copy(sleepTimerRemainingSeconds = millisUntilFinished / 1000) }
            }

            override fun onFinish() {
                exoPlayer.pause()
                _uiState.update { it.copy(isPlaying = false, sleepTimerMinutes = null) }
                _progress.update { it.copy(sleepTimerRemainingSeconds = null) }
            }
        }.start()
    }

    fun setEqualizerPreset(presetName: String) {
        equalizerManager.applyPreset(presetName)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    fun setEqualizerBand(bandIndex: Int, levelMilliBels: Int) {
        equalizerManager.setBandLevel(bandIndex, levelMilliBels)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        equalizerManager.setEnabled(enabled)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    fun setBassBoost(strength: Int) {
        equalizerManager.setBassBoost(strength)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    fun setVirtualizer(strength: Int) {
        equalizerManager.setVirtualizer(strength)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Shows the buffering state while a stream URL is being resolved (e.g. a
     * YouTube track's audio fetched from an instance) so the UI never looks
     * frozen between the tap and the actual playback.
     */
    fun showResolving() {
        _uiState.update { it.copy(isBuffering = true, errorMessage = null) }
    }

    /** Surfaces an honest, user-visible playback problem (e.g. all instances down). */
    fun showError(message: String) {
        _uiState.update {
            it.copy(isBuffering = false, isPlaying = false, errorMessage = message)
        }
    }

    /**
     * Restores the queue stored by [PlaybackSessionStore] **without** starting
     * playback or touching ExoPlayer, so the mini player can show what was
     * playing before the device was rebooted. Playback is only prepared when the
     * user asks to continue, via [playSong].
     */
    fun restoreSession(session: PlaybackSession): Boolean {
        if (session.songs.isEmpty() || _uiState.value.currentSong != null) return false
        val index = session.index.coerceIn(0, session.songs.size - 1)
        _uiState.update {
            it.copy(
                currentSong = session.songs[index],
                queue = session.songs,
                queueIndex = index,
                durationMs = session.songs[index].durationMs,
                isPlaying = false,
                isBuffering = false
            )
        }
        _progress.update {
            PlaybackProgress(
                positionMs = session.positionMs,
                durationMs = session.songs[index].durationMs
            )
        }
        return true
    }

    /** Loads the persisted session, used by both the UI and media resumption. */
    fun prepareResumableSession(): PlaybackSession? = sessionStore.load()

    private fun persistSession(force: Boolean) {
        val state = _uiState.value
        val song = state.currentSong ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionSaveMs < SESSION_SAVE_INTERVAL_MS) return
        lastSessionSaveMs = now
        sessionStore.save(
            PlaybackSession(
                songs = state.queue.ifEmpty { listOf(song) },
                index = state.queueIndex.coerceAtLeast(0),
                positionMs = _progress.value.positionMs,
                savedAt = now
            )
        )
    }

    private fun startPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                val current = exoPlayer.currentPosition
                val duration = if (exoPlayer.duration > 0) {
                    exoPlayer.duration
                } else {
                    _uiState.value.durationMs
                }
                _progress.update { it.copy(positionMs = current, durationMs = duration) }
                persistSession(force = false)
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun stopPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    private fun isPlayable(url: String): Boolean =
        url.startsWith("http://") || url.startsWith("https://")

    fun release() {
        stopPositionUpdater()
        sleepTimer?.cancel()
        equalizerManager.release()
        exoPlayer.release()
    }

    private companion object {
        const val POSITION_TICK_MS = 500L
        const val SESSION_SAVE_INTERVAL_MS = 10_000L
    }
}
