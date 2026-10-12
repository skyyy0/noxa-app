package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoxaApplication
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistSongEntity
import com.example.data.local.PlaylistSummary
import com.example.data.model.Song
import com.example.data.model.TrackLyrics
import com.example.data.remote.HomeMusicSection
import com.example.media.PlaybackProgress
import com.example.media.PlayerUiState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the lyrics panel should show for the track that is playing. */
sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lyrics: TrackLyrics) : LyricsUiState
    data class Unavailable(val message: String) : LyricsUiState
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoxaApplication
    private val player = app.player
    private val repository = app.musicRepository
    private val lyricsRepository = app.lyricsRepository
    private val streamResolver = app.streamResolver
    private val dao = app.database.noxaDao()
    val authManager = app.authManager

    val currentUser: StateFlow<FirebaseUser?> = authManager.currentUser
    val isGuestMode: StateFlow<Boolean> = authManager.isGuestMode
    val isSigningIn: StateFlow<Boolean> = authManager.isSigningIn

    val playerState: StateFlow<PlayerUiState> = player.uiState

    /**
     * The playback position lives in its own flow. Screens collect [playerState]
     * (which changes only on real player events) and let just the progress bar
     * observe this one, which is what removed the home screen's lag.
     */
    val playbackProgress: StateFlow<PlaybackProgress> = player.progress

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    // Home Sections — combined Deezer + Audius feed.
    private val _homeSections = MutableStateFlow<List<HomeMusicSection>>(emptyList())
    val homeSections: StateFlow<List<HomeMusicSection>> = _homeSections.asStateFlow()

    private val _isHomeLoading = MutableStateFlow(false)
    val isHomeLoading: StateFlow<Boolean> = _isHomeLoading.asStateFlow()

    private val _homeError = MutableStateFlow<String?>(null)
    val homeError: StateFlow<String?> = _homeError.asStateFlow()

    // Hero banner — "Blinding Lights" by The Weeknd, fetched live.
    private val _heroSong = MutableStateFlow<Song?>(null)
    val heroSong: StateFlow<Song?> = _heroSong.asStateFlow()

    // "Lanjutkan mendengarkan" — the session restored after a reboot.
    private val _resumableSong = MutableStateFlow<Song?>(null)
    val resumableSong: StateFlow<Song?> = _resumableSong.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // Lyrics for the track that is currently loaded in the player.
    private val _lyricsState = MutableStateFlow<LyricsUiState>(LyricsUiState.Idle)
    val lyricsState: StateFlow<LyricsUiState> = _lyricsState.asStateFlow()

    private var lyricsJob: Job? = null

    // Room Favorites & History
    val favoriteSongs: StateFlow<List<Song>> = dao.getFavoriteSongs()
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * The favourite ids as a set.
     *
     * Screens receive this instead of querying the view model during composition,
     * so a screen can never render a stale heart: tapping like recomposes every
     * list that shows that song.
     */
    val favoriteIds: StateFlow<Set<String>> = favoriteSongs
        .map { songs -> songs.mapTo(HashSet()) { it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val historySongs: StateFlow<List<Song>> = dao.getPlaybackHistory()
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlistSummaries: StateFlow<List<PlaylistSummary>> =
        dao.getPlaylistSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        authManager.attemptSilentAutoSignIn(viewModelScope)
        restoreLastSession()
        loadHome()
        observeCurrentSong()
    }

    /**
     * Brings back the queue that was playing before the app was closed, without
     * starting playback, and surfaces it as a resume card.
     */
    private fun restoreLastSession() {
        val session = player.prepareResumableSession() ?: return
        if (player.restoreSession(session)) {
            _resumableSong.value = session.songs.getOrNull(session.index)
        }
    }

    fun resumeLastSession() {
        val session = player.prepareResumableSession() ?: return
        val song = session.songs.getOrNull(session.index.coerceAtLeast(0)) ?: return
        player.playSong(song, session.songs, session.index.coerceIn(0, session.songs.size - 1))
        _resumableSong.value = null
    }

    fun dismissResume() {
        _resumableSong.value = null
    }

    private fun observeCurrentSong() {
        viewModelScope.launch {
            player.uiState
                .map { it.currentSong }
                .distinctUntilChangedBy { it?.id }
                .collect { song -> loadLyrics(song) }
        }
    }

    private fun loadLyrics(song: Song?) {
        lyricsJob?.cancel()
        if (song == null) {
            _lyricsState.value = LyricsUiState.Idle
            return
        }
        _lyricsState.value = LyricsUiState.Loading
        lyricsJob = viewModelScope.launch {
            val lyrics = lyricsRepository.lyricsFor(song)
            _lyricsState.value = if (lyrics == null) {
                LyricsUiState.Unavailable("Lirik untuk \"${song.title}\" belum tersedia.")
            } else {
                LyricsUiState.Ready(lyrics)
            }
        }
    }

    /** Manual retry for the lyrics panel. */
    fun retryLyrics() {
        loadLyrics(player.uiState.value.currentSong)
    }

    /**
     * Loads the home feed from both music providers with a real loading state.
     * Never falls back to simulated songs: on failure [homeError] is populated.
     */
    fun loadHome() {
        viewModelScope.launch {
            _isHomeLoading.value = true
            _homeError.value = null

            val feed = repository.getHomeFeed()
            _homeSections.value = feed.sections
            _heroSong.value = feed.hero
            if (feed.sections.isEmpty() && feed.hero == null) {
                _homeError.value =
                    "Tidak ada data dari server musik. Periksa koneksi internet lalu coba lagi."
            }
            _isHomeLoading.value = false
        }
    }

    fun signInWithGoogle(
        activity: Activity,
        onSuccess: (FirebaseUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        authManager.signInWithGoogle(activity, viewModelScope, onSuccess, onError)
    }

    fun continueAsGuest() {
        authManager.continueAsGuest()
    }

    fun signOut(onComplete: () -> Unit = {}) {
        authManager.signOut(viewModelScope, onComplete)
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        if (query.trim().isEmpty()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            _isSearching.value = true
            val results = repository.search(query.trim())
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun performSearch(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = repository.search(query.trim())
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    /** True when a full-length stream resolver is configured for this build. */
    val isFullTrackResolverConfigured: Boolean get() = streamResolver.isConfigured

    /**
     * Playback delegation.
     *
     * When a stream resolver is configured, the track is first offered to it so a
     * full-length audio URL can replace the short preview. The lookup has to
     * finish before playback starts, otherwise ExoPlayer would already be
     * buffering the preview. If the resolver declines (or is unreachable) the
     * original source is played unchanged.
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        _resumableSong.value = null
        if (!streamResolver.isConfigured) {
            player.playSong(song, queue, index)
            return
        }
        viewModelScope.launch {
            val resolved = streamResolver.resolve(song)
            if (resolved == null) {
                player.playSong(song, queue, index)
            } else {
                val resolvedQueue = queue.map { if (it.id == resolved.id) resolved else it }
                player.playSong(resolved, resolvedQueue, index)
            }
        }
    }

    fun playNext(song: Song) {
        player.playNext(song)
    }

    fun addToQueue(song: Song) {
        player.addToQueue(song)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
    }

    fun clearPlayerError() {
        player.clearError()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun next() {
        player.next()
    }

    fun previous() {
        player.previous()
    }

    fun toggleShuffle() {
        player.toggleShuffle()
    }

    fun toggleRepeat() {
        player.toggleRepeat()
    }

    fun setSleepTimer(minutes: Int?) {
        player.setSleepTimer(minutes)
    }

    fun setEqualizerPreset(presetName: String) {
        player.setEqualizerPreset(presetName)
    }

    fun setEqualizerBand(bandIndex: Int, levelMilliBels: Int) {
        player.setEqualizerBand(bandIndex, levelMilliBels)
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        player.setEqualizerEnabled(enabled)
    }

    fun setBassBoost(strength: Int) {
        player.setBassBoost(strength)
    }

    fun setVirtualizer(strength: Int) {
        player.setVirtualizer(strength)
    }

    // Full Player visibility
    fun openFullPlayer() {
        _isFullPlayerVisible.value = true
    }

    fun closeFullPlayer() {
        _isFullPlayerVisible.value = false
    }

    // Room Favorites
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            // The current state is read from the database, not from a cached list,
            // so two fast taps can never cancel each other out.
            if (dao.isFavoriteOnce(song.id)) {
                dao.deleteFavorite(song.id)
            } else {
                dao.insertFavorite(FavoriteSongEntity.fromSong(song))
            }
        }
    }

    fun isFavorite(songId: String): Boolean = favoriteIds.value.contains(songId)

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }

    // Playlists
    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            dao.insertPlaylist(PlaylistEntity(name = name, description = description))
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            dao.deletePlaylistSongs(id)
            dao.deletePlaylist(id)
        }
    }

    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        dao.getPlaylistSongs(playlistId).map { list -> list.map { it.toSong() } }

    fun playlistSongIds(playlistId: Long): Flow<List<String>> = dao.getPlaylistSongIds(playlistId)

    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = Int.MAX_VALUE) {
        viewModelScope.launch {
            dao.insertPlaylistSong(PlaylistSongEntity.fromSong(playlistId, song, position))
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            dao.deletePlaylistSong(playlistId, songId)
        }
    }
}
