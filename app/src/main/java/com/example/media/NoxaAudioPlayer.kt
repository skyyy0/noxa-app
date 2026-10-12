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
 * The playback position deliberately does **not** live here. It changes four
 * times a second, and keeping it in this object made every screen that observes
 * the player rebuild on every tick — which is what made the home screen lag.
 * Use [NoxaAudioPlayer.progress] for anything time based.
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
 * URL**. Every [Song] produced by the repository carries a genuine, playable
 * [Song.streamUrl], so:
 *
 * - the mini player, full player and lock screen all reflect the same player,
 * - the [MediaPlaybackService] MediaSession drives the system media notification
 *   and background playback,
 * - nothing is ever faked: if a track has no playable URL an error is reported.
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
                    handleTrackEnded()
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

    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        val newQueue = if (queue.isEmpty()) listOf(song) else queue
        val validIndex = if (index in newQueue.indices) index else 0

        _uiState.update {
            it.copy(
                currentSong = song,
                queue = newQueue,
                queueIndex = validIndex,
                durationMs = song.durationMs,
                isBuffering = true,
                isPlaying = false,
                errorMessage = null
            )
        }
        _progress.update { PlaybackProgress(positionMs = 0L, durationMs = song.durationMs) }

        scope.launch(Dispatchers.IO) {
            database.noxaDao().insertHistory(HistorySongEntity.fromSong(song))
        }

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

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(song.streamUrl))
            .setMediaId(song.id)
            .setMediaMetadata(song.toMediaMetadata())
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        // Reconnect the equalizer to the (possibly new) audio session.
        equalizerManager.init(exoPlayer.audioSessionId)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }

        // The MediaSession lives in the service; starting it enables the system
        // media notification, lock screen controls and background playback.
        startPlaybackService()
        persistSession(force = true)
    }

    private fun Song.toMediaMetadata(): MediaMetadata = MediaMetadata.Builder()
        .setTitle(title)
        .setArtist(artist)
        .setAlbumTitle(album)
        .setIsPlayable(true)
        .setArtworkUri(artworkUrl.takeIf { it.isNotBlank() }?.let { Uri.parse(it) })
        .build()

    private fun startPlaybackService() {
        try {
            context.startService(Intent(context, MediaPlaybackService::class.java))
        } catch (e: Exception) {
            Log.w("NoxaAudioPlayer", "Could not start MediaPlaybackService: ${e.message}")
        }
    }

    /** Plays [song] right after the current one. */
    fun playNext(song: Song) {
        val state = _uiState.value
        if (state.currentSong == null) {
            playSong(song)
            return
        }
        val queue = state.queue.toMutableList()
        val insertAt = (state.queueIndex + 1).coerceIn(0, queue.size)
        queue.removeAll { it.id == song.id }
        queue.add(insertAt.coerceAtMost(queue.size), song)
        _uiState.update { it.copy(queue = queue) }
        persistSession(force = true)
    }

    /** Appends [song] to the end of the queue. */
    fun addToQueue(song: Song) {
        val state = _uiState.value
        if (state.currentSong == null) {
            playSong(song)
            return
        }
        if (state.queue.any { it.id == song.id }) return
        _uiState.update { it.copy(queue = state.queue + song) }
        persistSession(force = true)
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
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val nextIndex = if (state.isShuffle) {
            state.queue.indices.random()
        } else {
            (state.queueIndex + 1) % state.queue.size
        }
        val nextSong = state.queue.getOrNull(nextIndex) ?: return
        playSong(nextSong, state.queue, nextIndex)
    }

    fun previous() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        // If played more than 3 seconds, replay the current song.
        if (_progress.value.positionMs > 3000) {
            seekTo(0L)
            return
        }
        val prevIndex = if (state.queueIndex - 1 < 0) state.queue.size - 1 else state.queueIndex - 1
        val prevSong = state.queue.getOrNull(prevIndex) ?: return
        playSong(prevSong, state.queue, prevIndex)
    }

    private fun handleTrackEnded() {
        val state = _uiState.value
        when (state.repeatMode) {
            Player.REPEAT_MODE_ONE -> {
                val current = state.currentSong
                if (current != null) playSong(current, state.queue, state.queueIndex.coerceAtLeast(0))
            }
            Player.REPEAT_MODE_ALL -> next()
            else -> {
                if (state.queueIndex < state.queue.size - 1) {
                    next()
                } else {
                    _uiState.update { it.copy(isPlaying = false) }
                    _progress.update { it.copy(positionMs = 0L) }
                }
            }
        }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    /**
     * Repeat is handled entirely at app-queue level (see [handleTrackEnded]).
     * ExoPlayer only ever holds a single media item, so its own repeat modes
     * would loop that one item instead of advancing the real queue.
     */
    fun toggleRepeat() {
        val nextMode = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _uiState.update { it.copy(repeatMode = nextMode) }
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
     * Restores the queue stored by [PlaybackSessionStore] **without** starting
     * playback. Used at launch so the mini player and the "lanjutkan" card show
     * what was playing before the device was rebooted.
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

    /** Loads the persisted session and prepares its first item for playback. */
    fun prepareResumableSession(): PlaybackSession? = sessionStore.load()

    private fun persistSession(force: Boolean) {
        val state = _uiState.value
        val song = state.currentSong ?: return
        val now = System.currentTimeMillis()
        if (!force && now - lastSessionSaveMs < SESSION_SAVE_INTERVAL_MS) return
        lastSessionSaveMs = now
        val position = if (force) _progress.value.positionMs else _progress.value.positionMs
        sessionStore.save(
            PlaybackSession(
                songs = state.queue.ifEmpty { listOf(song) },
                index = state.queueIndex.coerceAtLeast(0),
                positionMs = position,
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
