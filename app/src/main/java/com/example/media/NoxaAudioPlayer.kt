package com.example.media

import android.content.Context
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

data class PlayerUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val queueIndex: Int = -1,
    val isShuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val sleepTimerMinutes: Int? = null,
    val sleepTimerRemainingSeconds: Long? = null,
    val equalizerState: EqualizerState = EqualizerState()
)

class NoxaAudioPlayer(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val database = NoxaDatabase.getDatabase(context)
    val equalizerManager = EqualizerManager()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .setUsage(C.USAGE_MEDIA)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var positionUpdateJob: Job? = null
    private var sleepTimer: CountDownTimer? = null

    init {
        setupPlayerListener()
        equalizerManager.init(exoPlayer.audioSessionId)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }
    }

    private fun setupPlayerListener() {
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.update { it.copy(isPlaying = isPlaying) }
                if (isPlaying) {
                    startPositionUpdater()
                } else {
                    stopPositionUpdater()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val isBuffering = playbackState == Player.STATE_BUFFERING
                val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _uiState.value.currentSong?.durationMs ?: 0L
                _uiState.update {
                    it.copy(
                        isBuffering = isBuffering,
                        durationMs = duration
                    )
                }

                if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnded()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("NoxaAudioPlayer", "Playback error: ${error.message}", error)
                _uiState.update { it.copy(isBuffering = false, isPlaying = false) }
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
                currentPositionMs = 0L,
                isBuffering = true
            )
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setAlbumTitle(song.album)
            .setArtworkUri(Uri.parse(song.artworkUrl))
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(song.streamUrl))
            .setMediaId(song.id)
            .setMediaMetadata(metadata)
            .build()

        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.play()

        // Reconnect equalizer to session if needed
        equalizerManager.init(exoPlayer.audioSessionId)
        _uiState.update { it.copy(equalizerState = equalizerManager.currentState) }

        // Record to Room history
        scope.launch(Dispatchers.IO) {
            database.noxaDao().insertHistory(HistorySongEntity.fromSong(song))
        }
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
        _uiState.update { it.copy(currentPositionMs = clamped) }
    }

    fun next() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        val nextIndex = if (state.isShuffle) {
            (state.queue.indices).random()
        } else {
            (state.queueIndex + 1) % state.queue.size
        }
        val nextSong = state.queue.getOrNull(nextIndex) ?: return
        playSong(nextSong, state.queue, nextIndex)
    }

    fun previous() {
        val state = _uiState.value
        if (state.queue.isEmpty()) return
        // If played more than 3 seconds, replay current song
        if (exoPlayer.currentPosition > 3000) {
            exoPlayer.seekTo(0)
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
                exoPlayer.seekTo(0)
                exoPlayer.play()
            }
            Player.REPEAT_MODE_ALL -> {
                next()
            }
            else -> {
                if (state.queueIndex < state.queue.size - 1) {
                    next()
                } else {
                    _uiState.update { it.copy(isPlaying = false, currentPositionMs = 0L) }
                }
            }
        }
    }

    fun toggleShuffle() {
        _uiState.update { it.copy(isShuffle = !it.isShuffle) }
    }

    fun toggleRepeat() {
        val nextMode = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        exoPlayer.repeatMode = nextMode
        _uiState.update { it.copy(repeatMode = nextMode) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimer?.cancel()
        sleepTimer = null

        if (minutes == null || minutes <= 0) {
            _uiState.update { it.copy(sleepTimerMinutes = null, sleepTimerRemainingSeconds = null) }
            return
        }

        val totalMs = minutes * 60 * 1000L
        _uiState.update {
            it.copy(
                sleepTimerMinutes = minutes,
                sleepTimerRemainingSeconds = totalMs / 1000
            )
        }

        sleepTimer = object : CountDownTimer(totalMs, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _uiState.update {
                    it.copy(sleepTimerRemainingSeconds = millisUntilFinished / 1000)
                }
            }

            override fun onFinish() {
                exoPlayer.pause()
                _uiState.update {
                    it.copy(
                        isPlaying = false,
                        sleepTimerMinutes = null,
                        sleepTimerRemainingSeconds = null
                    )
                }
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

    private fun startPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                val current = exoPlayer.currentPosition
                val duration = if (exoPlayer.duration > 0) exoPlayer.duration else _uiState.value.durationMs
                _uiState.update {
                    it.copy(
                        currentPositionMs = current,
                        durationMs = duration
                    )
                }
                delay(250)
            }
        }
    }

    private fun stopPositionUpdater() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    fun release() {
        stopPositionUpdater()
        sleepTimer?.cancel()
        equalizerManager.release()
        exoPlayer.release()
    }
}
