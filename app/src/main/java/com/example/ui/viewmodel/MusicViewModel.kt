package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoxaApplication
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.PlaylistEntity
import com.example.data.model.Song
import com.example.data.remote.HomeMusicSection
import com.example.media.PlayerUiState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoxaApplication
    private val player = app.player
    private val repository = app.youTubeRepository
    private val dao = app.database.noxaDao()
    val authManager = app.authManager

    val currentUser: StateFlow<FirebaseUser?> = authManager.currentUser
    val isGuestMode: StateFlow<Boolean> = authManager.isGuestMode
    val isSigningIn: StateFlow<Boolean> = authManager.isSigningIn

    val playerState: StateFlow<PlayerUiState> = player.uiState

    private val _isFullPlayerVisible = MutableStateFlow(false)
    val isFullPlayerVisible: StateFlow<Boolean> = _isFullPlayerVisible.asStateFlow()

    private val _isQueueVisible = MutableStateFlow(false)
    val isQueueVisible: StateFlow<Boolean> = _isQueueVisible.asStateFlow()

    private val _isLyricsVisible = MutableStateFlow(false)
    val isLyricsVisible: StateFlow<Boolean> = _isLyricsVisible.asStateFlow()

    // Home Sections
    private val _homeSections = MutableStateFlow<List<HomeMusicSection>>(repository.getHomeMusicSections())
    val homeSections: StateFlow<List<HomeMusicSection>> = _homeSections.asStateFlow()

    // Hero Song
    private val _heroSong = MutableStateFlow<Song?>(
        _homeSections.value.firstOrNull()?.songs?.firstOrNull()
    )
    val heroSong: StateFlow<Song?> = _heroSong.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private var searchJob: Job? = null

    // Room Favorites & History
    val favoriteSongs: StateFlow<List<Song>> = dao.getFavoriteSongs()
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historySongs: StateFlow<List<Song>> = dao.getPlaybackHistory()
        .map { list -> list.map { it.toSong() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = dao.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        authManager.attemptSilentAutoSignIn(viewModelScope)
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

    // Playback delegation
    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        player.playSong(song, queue, index)
    }

    fun togglePlayPause() {
        player.togglePlayPause()
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

    // Full Player visibility
    fun openFullPlayer() {
        _isFullPlayerVisible.value = true
    }

    fun closeFullPlayer() {
        _isFullPlayerVisible.value = false
    }

    fun toggleQueue(show: Boolean? = null) {
        _isQueueVisible.value = show ?: !_isQueueVisible.value
    }

    fun toggleLyrics(show: Boolean? = null) {
        _isLyricsVisible.value = show ?: !_isLyricsVisible.value
    }

    // Room Favorites
    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val isFav = favoriteSongs.value.any { it.id == song.id }
            if (isFav) {
                dao.deleteFavorite(song.id)
            } else {
                dao.insertFavorite(FavoriteSongEntity.fromSong(song))
            }
        }
    }

    fun isFavorite(songId: String): Boolean {
        return favoriteSongs.value.any { it.id == songId }
    }

    fun clearHistory() {
        viewModelScope.launch {
            dao.clearHistory()
        }
    }

    fun createPlaylist(name: String, description: String = "") {
        viewModelScope.launch {
            dao.insertPlaylist(PlaylistEntity(name = name, description = description))
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            dao.deletePlaylist(id)
        }
    }
}
