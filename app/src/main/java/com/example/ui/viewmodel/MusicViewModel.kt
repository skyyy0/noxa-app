package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.view.SurfaceView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoxaApplication
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.PlaylistSongEntity
import com.example.data.local.PlaylistSummary
import com.example.data.local.UsagePolicyStore
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.example.data.model.TrackLyrics
import com.example.data.remote.HomeMusicSection
import com.example.data.remote.YouTubeSourceRepository
import com.example.media.MvPlayer
import com.example.media.PlaybackProgress
import com.example.media.PlayerUiState
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

/** How many preview tracks in a queue get warmed for full audio ahead of time. */
private const val PREVIEW_WARM_LIMIT = 3

/** What the lyrics panel should show for the track that is playing. */
sealed interface LyricsUiState {
    data object Idle : LyricsUiState
    data object Loading : LyricsUiState
    data class Ready(val lyrics: TrackLyrics) : LyricsUiState
    data class Unavailable(val message: String) : LyricsUiState
}

/** What the full player's "Foto / Video MV" area should show. */
sealed interface VideoUiState {
    /** Photo (artwork) mode — no video involved. */
    data object Idle : VideoUiState

    /** Video mode requested; the MV stream is being resolved. */
    data object Loading : VideoUiState

    /** Video mode with a playable (video-only) stream URL. */
    data class Ready(val url: String) : VideoUiState

    /** Video mode requested but no instance could provide a video stream. */
    data class Unavailable(val message: String) : VideoUiState
}

/** What the artist page should show. */
sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data class Ready(
        val profile: ArtistProfile,
        val topSongs: List<Song>,
        val albums: List<AlbumInfo>
    ) : ArtistUiState
    data class Error(val message: String) : ArtistUiState
}

/** What the album page should show. */
sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Ready(val album: AlbumInfo, val tracks: List<Song>) : AlbumUiState
    data class Error(val message: String) : AlbumUiState
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as NoxaApplication
    private val player = app.player
    private val repository = app.musicRepository
    private val lyricsRepository = app.lyricsRepository
    private val streamResolver = app.streamResolver
    private val youtubeRepository = app.youtubeSourceRepository
    private val usagePolicyStore = app.usagePolicyStore
    private val mvPlayer = MvPlayer(application)
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

    // "Video MV" in the full player (YouTube tracks only).
    private val _videoUiState = MutableStateFlow<VideoUiState>(VideoUiState.Idle)
    val videoUiState: StateFlow<VideoUiState> = _videoUiState.asStateFlow()

    /** A YouTube play held back until the user accepts the usage policy. */
    data class PendingYouTubePlay(val song: Song, val queue: List<Song>, val index: Int)

    private val _pendingYouTubePlay = MutableStateFlow<PendingYouTubePlay?>(null)
    val pendingYouTubePlay: StateFlow<PendingYouTubePlay?> = _pendingYouTubePlay.asStateFlow()

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

    // Artist & album pages — one load at a time per page.
    private val _artistUiState = MutableStateFlow<ArtistUiState>(ArtistUiState.Loading)
    val artistUiState: StateFlow<ArtistUiState> = _artistUiState.asStateFlow()

    private val _albumUiState = MutableStateFlow<AlbumUiState>(AlbumUiState.Loading)
    val albumUiState: StateFlow<AlbumUiState> = _albumUiState.asStateFlow()

    private var artistJob: Job? = null
    private var albumJob: Job? = null

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
        // Routed through playSong so a restored YouTube queue gets its audio
        // resolved (and the usage policy honoured) instead of playing a stale,
        // long-expired URL.
        playSong(song, session.songs, session.index.coerceIn(0, session.songs.size - 1))
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
                .collect { song ->
                    resetVideoMode()
                    loadLyrics(song)
                }
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
            val results = searchAll(query.trim())
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    fun performSearch(query: String) {
        _searchQuery.value = query
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = searchAll(query.trim())
            _searchResults.value = results
            _isSearching.value = false
        }
    }

    /**
     * Searches the keyless catalogues and YouTube in parallel, then merges and
     * de-duplicates. Catalog tracks come first because they start instantly;
     * YouTube tracks resolve their audio through instances at play time.
     */
    private suspend fun searchAll(query: String): List<Song> = coroutineScope {
        val catalog = async { runCatching { repository.search(query) }.getOrDefault(emptyList()) }
        val youtube = async {
            runCatching { youtubeRepository.search(query) }.getOrDefault(emptyList())
        }
        (catalog.await() + youtube.await()).distinctBy { it.id }
    }

    /** True when a full-length stream resolver is configured for this build. */
    val isFullTrackResolverConfigured: Boolean get() = streamResolver.isConfigured

    /**
     * Playback delegation.
     *
     * Three layers, in order:
     *  1. **Usage policy** — a YouTube track cannot play until the user has
     *     accepted the one-time policy dialog ([pendingYouTubePlay] holds the
     *     request while it is shown).
     *  2. **Your own resolver** (`STREAM_RESOLVER_URL`) — asked for every track,
     *     so a licensed backend always wins.
     *  3. **Source-native playback** — Deezer/Audius URLs already exist; YouTube
     *     audio is resolved through Piped/Invidious instances at play time.
     *
     * The lookup has to finish before playback starts, otherwise ExoPlayer
     * would already be buffering the wrong URL. The UI shows a spinner via
     * [NoxaAudioPlayer.showResolving] while this runs.
     */
    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        _resumableSong.value = null
        // YouTube tracks always need the policy — there is no other audio.
        // Deezer previews only *may* use YouTube for full-length audio, so the
        // dialog is asked once: accepted -> full audio attempted, declined ->
        // the 30s preview plays quietly from then on without nagging again.
        val policyAccepted = usagePolicyStore.isYouTubePolicyAccepted
        val needsPolicy = when {
            song.source == MusicSource.YOUTUBE -> !policyAccepted
            song.source == MusicSource.DEEZER && song.isPreview ->
                !policyAccepted && !usagePolicyStore.isYouTubePolicyDeclined
            else -> false
        }
        if (needsPolicy) {
            _pendingYouTubePlay.value = PendingYouTubePlay(song, queue, index)
            return
        }
        resolveAndPlay(song, queue, index)
    }

    /** The user accepted the YouTube usage policy: continue the held play. */
    fun acceptYouTubePolicy() {
        usagePolicyStore.acceptYouTubePolicy()
        val pending = _pendingYouTubePlay.value ?: return
        _pendingYouTubePlay.value = null
        resolveAndPlay(pending.song, pending.queue, pending.index)
    }

    /**
     * The user declined (or dismissed) the policy: drop the held play and
     * remember the refusal, so preview tracks stop asking and keep their
     * honest 30 second clip instead.
     */
    fun declineYouTubePolicy() {
        usagePolicyStore.declineYouTubePolicy()
        _pendingYouTubePlay.value = null
    }

    private fun resolveAndPlay(song: Song, queue: List<Song>, index: Int) {
        val needsYoutubeResolve = song.source == MusicSource.YOUTUBE && song.streamUrl.isBlank()
        // Full audio for a preview track: match it on YouTube and resolve the
        // audio through instances — only after the user accepted the policy.
        val wantsFullAudio = song.source == MusicSource.DEEZER && song.isPreview &&
            usagePolicyStore.isYouTubePolicyAccepted
        if (!streamResolver.isConfigured && !needsYoutubeResolve && !wantsFullAudio) {
            player.playSong(song, queue, index)
            return
        }
        if (needsYoutubeResolve || wantsFullAudio) player.showResolving()
        viewModelScope.launch {
            var resolved = streamResolver.resolve(song)
            if (resolved == null && needsYoutubeResolve) {
                resolved = runCatching { youtubeRepository.resolveAudio(song) }.getOrNull()
            }
            if (resolved == null && wantsFullAudio) {
                resolved = runCatching { youtubeRepository.findFullAudio(song) }.getOrNull()
            }
            when {
                resolved != null -> {
                    val resolvedQueue = queue.map { if (it.id == resolved.id) resolved else it }
                    player.playSong(resolved, resolvedQueue, index)
                    warmQueueStreams(resolvedQueue)
                }

                needsYoutubeResolve -> player.showError(
                    "Audio \"${song.title}\" tidak bisa diambil sekarang. " +
                        "Instance YouTube publik sedang sibuk atau tidak tersedia — coba lagi nanti."
                )

                // No confident match: play the honest 30s preview (the badge says so).
                else -> player.playSong(song, queue, index)
            }
        }
    }

    /**
     * YouTube URLs in the rest of the queue resolve lazily in the background
     * and are swapped into ExoPlayer when ready, so pressing "next" later just
     * works without blocking the current song. Deezer previews get the same
     * treatment through the full-audio matcher, capped so a long album queue
     * does not turn into one YouTube search per track.
     */
    private fun warmQueueStreams(queue: List<Song>) {
        viewModelScope.launch {
            queue
                .filter { it.source == MusicSource.YOUTUBE && it.streamUrl.isBlank() }
                .forEach { queued ->
                    youtubeRepository.resolveAudio(queued)?.let { player.updateSongStream(it) }
                }

            if (usagePolicyStore.isYouTubePolicyAccepted) {
                queue
                    .filter { it.source == MusicSource.DEEZER && it.isPreview }
                    .take(PREVIEW_WARM_LIMIT)
                    .forEach { queued ->
                        youtubeRepository.findFullAudio(queued)?.let { player.updateSongStream(it) }
                    }
            }
        }
    }

    fun playNext(song: Song) {
        enqueue(song) { player.playNext(it) }
    }

    fun addToQueue(song: Song) {
        enqueue(song) { player.addToQueue(it) }
    }

    /** Resolves audio first so queued items are playable when reached. */
    private fun enqueue(song: Song, play: (Song) -> Unit) {
        val needsYoutube = song.source == MusicSource.YOUTUBE && song.streamUrl.isBlank()
        val wantsFullAudio = song.source == MusicSource.DEEZER && song.isPreview &&
            usagePolicyStore.isYouTubePolicyAccepted
        if (!needsYoutube && !wantsFullAudio) {
            play(song)
            return
        }
        viewModelScope.launch {
            val resolved = if (needsYoutube) {
                youtubeRepository.resolveAudio(song)
            } else {
                runCatching { youtubeRepository.findFullAudio(song) }.getOrNull()
            }
            play(resolved ?: song)
        }
    }

    fun togglePlayPause() {
        player.togglePlayPause()
        // Keep the muted MV picture in step with the audio it decorates.
        if (_videoUiState.value is VideoUiState.Ready) {
            if (player.uiState.value.isPlaying) mvPlayer.play() else mvPlayer.pause()
        }
    }

    fun clearPlayerError() {
        player.clearError()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
        if (_videoUiState.value is VideoUiState.Ready) mvPlayer.seekTo(positionMs)
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

    // ------------------------------------------------------------------ Video MV

    /**
     * Switches the full player between the artwork photo and the music video.
     * Only YouTube tracks can have an MV; resolving can take a moment, so the
     * UI shows a spinner until [VideoUiState.Ready] arrives.
     */
    fun setVideoMode(enabled: Boolean) {
        val song = player.uiState.value.currentSong
        if (!enabled || song == null || song.source != MusicSource.YOUTUBE) {
            resetVideoMode()
            return
        }
        if (_videoUiState.value is VideoUiState.Loading || _videoUiState.value is VideoUiState.Ready) {
            return
        }
        _videoUiState.value = VideoUiState.Loading
        viewModelScope.launch {
            val url = youtubeRepository.resolveVideoUrl(song)
            _videoUiState.value = if (url == null) {
                VideoUiState.Unavailable("MV tidak tersedia untuk lagu ini.")
            } else {
                VideoUiState.Ready(url)
            }
        }
    }

    /** Called by the sheet with its SurfaceView when the video view appears. */
    fun attachMvSurface(surfaceView: SurfaceView?) {
        val ready = _videoUiState.value as? VideoUiState.Ready ?: return
        if (surfaceView == null) {
            mvPlayer.detachAndReleaseSoon()
            return
        }
        mvPlayer.playOnSurface(ready.url, surfaceView, player.progress.value.positionMs)
    }

    /** Back to photo mode: detach the surface and stop the muted video player. */
    private fun resetVideoMode() {
        mvPlayer.release()
        _videoUiState.value = VideoUiState.Idle
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

    // ---------------------------------------------------- artist & album pages

    /**
     * Opens an artist page for a track. Uses the id baked into the song when
     * the catalogue provided one, otherwise resolves the artist by name first
     * (and says so honestly when no artist matches).
     */
    fun openArtistProfile(artistId: String, artistName: String, onFound: (Long) -> Unit) {
        artistId.toLongOrNull()?.let { onFound(it); return }
        if (artistName.isBlank()) return
        viewModelScope.launch {
            val id = runCatching { repository.findArtistId(artistName) }.getOrNull()
            if (id != null) {
                onFound(id)
            } else {
                player.showError("Profil artis \"$artistName\" tidak tersedia.")
            }
        }
    }

    /** Loads the artist page: header, top tracks and discography. */
    fun loadArtist(artistId: Long) {
        artistJob?.cancel()
        artistJob = viewModelScope.launch {
            _artistUiState.value = ArtistUiState.Loading
            val profile = runCatching { repository.artistProfile(artistId) }.getOrNull()
            if (profile == null) {
                _artistUiState.value = ArtistUiState.Error(
                    "Profil artis tidak dapat dimuat. Periksa koneksi internet lalu coba lagi."
                )
                return@launch
            }
            val topSongs = runCatching { repository.artistTopTracks(artistId, 20) }
                .getOrDefault(emptyList())
            val albums = runCatching { repository.artistAlbums(artistId) }
                .getOrDefault(emptyList())
            _artistUiState.value = ArtistUiState.Ready(profile, topSongs, albums)
        }
    }

    /** Loads the album page: header plus the full tracklist. */
    fun loadAlbum(albumId: Long) {
        albumJob?.cancel()
        albumJob = viewModelScope.launch {
            _albumUiState.value = AlbumUiState.Loading
            val detail = runCatching { repository.albumDetail(albumId) }.getOrNull()
            if (detail == null) {
                _albumUiState.value = AlbumUiState.Error(
                    "Album tidak dapat dimuat. Periksa koneksi internet lalu coba lagi."
                )
                return@launch
            }
            _albumUiState.value = AlbumUiState.Ready(detail.album, detail.tracks)
        }
    }

    override fun onCleared() {
        mvPlayer.release()
        super.onCleared()
    }
}
