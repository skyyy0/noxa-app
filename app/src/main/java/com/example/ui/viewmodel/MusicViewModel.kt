package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoxaApplication
import com.example.data.local.AppSettings
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.PlayRecordEntity
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
import com.example.data.auth.NoxaUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MusicViewModel"

/** SharedPreferences key: the versionCode an optional update was skipped on. */
private const val KEY_SKIPPED_UPDATE = "skipped_update_version"

/** How many preview tracks in a queue get warmed for full audio ahead of time. */
private const val PREVIEW_WARM_LIMIT = 3

/** Autoplay-similar: trigger window before the end of the queue's last song. */
private const val AUTOPLAY_NEAR_END_MS = 15_000L

/** How many similar tracks get appended when autoplay kicks in. */
private const val AUTOPLAY_APPEND_LIMIT = 5

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
private const val PLAY_RECORD_RETENTION_MS = 30L * 24 * 60 * 60 * 1000

/** Dengar Bareng: how often the room is polled, and position heartbeat. */
private const val ROOM_POLL_MS = 2_000L
private const val ROOM_HEARTBEAT_MS = 10_000L

/** The mini weekly recap shown on the profile screen. */
data class WeeklyRecap(
    val plays: Int = 0,
    val topArtist: String = "",
    val topSongTitle: String = "",
    val topSongPlays: Int = 0
) {
    companion object {
        val EMPTY = WeeklyRecap()
    }
}

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
    private val mvPlayer = MvPlayer(application).apply {
        // A stream URL that expired mid-play used to leave a black surface;
        // now the ViewModel re-resolves once and attaches the fresh stream.
        onPlaybackError = { handleMvPlaybackError() }
    }

    /** Auto-retries guard: one free retry per user toggle. */
    private var mvAutoRetries = 0

    /** Tried in order after the chosen MV quality fails to resolve. */
    private val MV_FALLBACK_QUALITIES = listOf(360, 240)

    /** Last surface handed to us, so a retried stream can re-attach itself. */
    private var lastMvSurface: SurfaceView? = null
    private val dao = app.database.noxaDao()
    val authManager = app.authManager

    val currentUser: StateFlow<NoxaUser?> = authManager.currentUser
    val isGuestMode: StateFlow<Boolean> = authManager.isGuestMode
    val isSigningIn: StateFlow<Boolean> = authManager.isSigningIn

    // ------------------------------------------------------------ app settings

    private val settingsStore = app.appSettingsStore

    private val _appSettings = MutableStateFlow(
        AppSettings(
            mvQualityHeight = settingsStore.mvQualityHeight,
            autoplaySimilar = settingsStore.autoplaySimilar,
            karaokeOverlay = settingsStore.karaokeOverlay,
            autoResumeOnOpen = settingsStore.autoResumeOnOpen
        )
    )

    /** MV quality, autoplay-similar, karaoke overlay and auto-resume. */
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    fun setMvQualityHeight(height: Int) {
        settingsStore.mvQualityHeight = height
        _appSettings.value = _appSettings.value.copy(mvQualityHeight = height)
    }

    fun setAutoplaySimilar(enabled: Boolean) {
        settingsStore.autoplaySimilar = enabled
        _appSettings.value = _appSettings.value.copy(autoplaySimilar = enabled)
    }

    fun setKaraokeOverlay(enabled: Boolean) {
        settingsStore.karaokeOverlay = enabled
        _appSettings.value = _appSettings.value.copy(karaokeOverlay = enabled)
    }

    fun setAutoResumeOnOpen(enabled: Boolean) {
        settingsStore.autoResumeOnOpen = enabled
        _appSettings.value = _appSettings.value.copy(autoResumeOnOpen = enabled)
    }

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

    // Sky's pinned pick(s) — the dedicated Home shelf.
    private val _pinnedSongs = MutableStateFlow<List<Song>>(emptyList())
    val pinnedSongs: StateFlow<List<Song>> = _pinnedSongs.asStateFlow()

    // Artist avatars behind the Home "Artis Populer" shelf.
    private val _popularArtists = MutableStateFlow<List<ArtistProfile>>(emptyList())
    val popularArtists: StateFlow<List<ArtistProfile>> = _popularArtists.asStateFlow()

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

    // Mini weekly recap — plays from the last seven days.
    private val _weeklyRecap = MutableStateFlow(WeeklyRecap.EMPTY)
    val weeklyRecap: StateFlow<WeeklyRecap> = _weeklyRecap.asStateFlow()

    val playlistSummaries: StateFlow<List<PlaylistSummary>> =
        dao.getPlaylistSummaries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // -------------------------------------------------------------- NOXA AI

    private val aiRepository by lazy {
        com.example.data.ai.NoxaAiRepository(getApplication())
    }

    /**
     * All background work runs through this scope: same job & main dispatcher
     * as [viewModelScope] (cancelled with the view model), but with a
     * [CoroutineExceptionHandler]. Before it existed, ANY uncaught throwable
     * inside a play-path coroutine — an OOM spike while the home grid decodes
     * artwork during "memuat lagu", an exotic parse error, a codec-adjacent
     * failure — killed the whole app. Now it surfaces as an honest player
     * error and gets logged, so the app survives and the crash log records it.
     */
    private val safeScope = CoroutineScope(
        viewModelScope.coroutineContext + CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Background task failed", throwable)
            player.showError(
                "Terjadi kesalahan saat memuat musik. Coba lagi — jika berulang, " +
                    "laporkan lewat Chat NOXA Support."
            )
        }
    )

    private val _aiMessages = MutableStateFlow<List<com.example.data.ai.AiChatMessage>>(emptyList())
    val aiMessages: StateFlow<List<com.example.data.ai.AiChatMessage>> =
        _aiMessages.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _aiError = MutableStateFlow<String?>(null)
    val aiError: StateFlow<String?> = _aiError.asStateFlow()

    init {
        authManager.attemptSilentAutoSignIn(viewModelScope)
        restoreLastSession()
        // Optional auto-start: when enabled, the restored session plays right
        // away instead of waiting behind the resume card.
        if (settingsStore.autoResumeOnOpen && _resumableSong.value != null) {
            resumeLastSession()
        }
        loadHome()
        // Dengar Bareng: mirror local playback into an open room (null-safe
        // until joinRoom sets roomCode).
        startRoomMirroring()
        // In-app update check: quiet and delayed so it never competes with
        // the first home load; a newer published version forces a dialog.
        safeScope.launch {
            delay(5_000)
            checkForUpdate()
        }
        // The developer never sees the forced-update popup: Sky publishes
        // updates from this very account, and a modal with no dismissal would
        // lock her out of the console that pushes them. Covers the late
        // sign-in case too — the role only becomes known after the silent
        // auto sign-in finishes.
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                if (user?.role == com.example.data.support.AccountRole.DEVELOPER) {
                    _updateInfo.value = null
                }
            }
        }
        observeCurrentSong()
        observeAutoplaySimilar()
        observeWeeklyRecap()
        // Restore the NOXA AI transcript so the assistant keeps continuity.
        _aiMessages.value = aiRepository.loadHistory()
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
        safeScope.launch {
            player.uiState
                .map { it.currentSong }
                .distinctUntilChangedBy { it?.id }
                .collect { song ->
                    resetVideoMode()
                    loadLyrics(song)
                    recordPlay(song)
                }
        }
    }

    /** Counts a play for the weekly recap and prunes records past retention. */
    private fun recordPlay(song: Song?) {
        if (song == null) return
        safeScope.launch {
            try {
                dao.insertPlayRecord(
                    PlayRecordEntity(songId = song.id, title = song.title, artist = song.artist)
                )
                dao.prunePlayRecords(System.currentTimeMillis() - PLAY_RECORD_RETENTION_MS)
            } catch (e: Exception) {
                Log.w(TAG, "Could not record play: ${e.message}")
            }
        }
    }

    /** Recomputes the weekly recap whenever a new play lands. */
    private fun observeWeeklyRecap() {
        safeScope.launch {
            dao.getPlayRecordsSince(System.currentTimeMillis() - WEEK_MS)
                .collect { records ->
                    if (records.isEmpty()) {
                        _weeklyRecap.value = WeeklyRecap.EMPTY
                        return@collect
                    }
                    val topArtist = records.groupBy { it.artist }.maxByOrNull { it.value.size }
                    val topSong = records.groupBy { it.title }.maxByOrNull { it.value.size }
                    _weeklyRecap.value = WeeklyRecap(
                        plays = records.size,
                        topArtist = topArtist?.key.orEmpty(),
                        topSongTitle = topSong?.key.orEmpty(),
                        topSongPlays = topSong?.value?.size ?: 0
                    )
                }
        }
    }

    /**
     * Autoplay of similar music: a few seconds before the last track of a queue
     * ends, the artist's other top tracks are appended — resolved first so they
     * are genuinely playable, not dead preview links.
     */
    private var autoplayAppendedFor: String? = null

    private fun observeAutoplaySimilar() {
        safeScope.launch {
            combine(player.uiState, player.progress) { state, progress -> state to progress }
                .collect { (state, progress) ->
                    if (!settingsStore.autoplaySimilar) return@collect
                    if (state.queue.isEmpty() || state.queueIndex != state.queue.lastIndex) {
                        return@collect
                    }
                    if (!state.isPlaying) return@collect
                    val last = state.queue.last()
                    if (last.id == autoplayAppendedFor) return@collect
                    val duration = last.durationMs
                    if (duration <= 0 || progress.positionMs < duration - AUTOPLAY_NEAR_END_MS) {
                        return@collect
                    }
                    autoplayAppendedFor = last.id
                    appendSimilarTracks(last, state.queue)
                }
        }
    }

    private fun appendSimilarTracks(song: Song, playedQueue: List<Song>) {
        safeScope.launch {
            val artistId = song.artistId.toLongOrNull()
                ?: runCatching { repository.findArtistId(song.artist) }.getOrNull()
            val similar = if (artistId != null) {
                runCatching { repository.artistTopTracks(artistId, 12) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }
            similar
                .filter { candidate ->
                    candidate.id != song.id && playedQueue.none { it.id == candidate.id }
                }
                .take(AUTOPLAY_APPEND_LIMIT)
                .forEach { candidate ->
                    val resolved = if (
                        candidate.source == MusicSource.DEEZER && candidate.isPreview &&
                        usagePolicyStore.isYouTubePolicyAccepted
                    ) {
                        runCatching { youtubeRepository.findFullAudio(candidate) }.getOrNull()
                    } else {
                        candidate
                    }
                    player.addToQueue(resolved ?: candidate)
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
        lyricsJob = safeScope.launch {
            // LRCLIB occasionally answers with non-JSON bodies; a parse hiccup
            // must never kill playback (this runs on every song change).
            val lyrics = try {
                lyricsRepository.lyricsFor(song)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Lyrics failed: ${e.javaClass.simpleName}: ${e.message}")
                null
            }
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
        safeScope.launch {
            _isHomeLoading.value = true
            _homeError.value = null
            try {
                val feed = repository.getHomeFeed()
                _homeSections.value = feed.sections
                _heroSong.value = feed.hero
                _pinnedSongs.value = feed.pinned
                resolveMissingPins(feed.pinned)
                _popularArtists.value = feed.artists
                if (feed.sections.isEmpty() && feed.hero == null) {
                    _homeError.value =
                        "Tidak ada data dari server musik. Periksa koneksi internet lalu coba lagi."
                }
            } catch (e: Exception) {
                // A failed feed must surface as a retryable error, never a crash.
                Log.w(TAG, "Home feed failed: ${e.javaClass.simpleName}: ${e.message}")
                _homeError.value =
                    "Beranda tidak bisa dimuat. Periksa koneksi internet lalu coba lagi."
            } finally {
                _isHomeLoading.value = false
            }
        }
    }

    /**
     * A pin the Deezer catalogue no longer serves (Nidji's "Laskar Pelangi"
     * was delisted) is resolved from the YouTube source instead, so the Sky
     * shelf always shows all five picks.
     */
    private suspend fun resolveMissingPins(existing: List<Song>) {
        val present = existing
            .mapNotNull { song -> com.example.data.model.PinnedTracks.find(song)?.title }
            .toSet()
        val missing = com.example.data.model.PinnedTracks.ALL.filter { it.title !in present }
        if (missing.isEmpty()) return
        val extras = missing.mapNotNull { pin ->
            val candidates = runCatching {
                youtubeRepository.search("${pin.artist} ${pin.title}")
            }.getOrDefault(emptyList())
                .filter {
                    com.example.data.model.PinnedTracks.matchesRelaxed(pin, it.title, it.artist)
                }
            // Prefer the video whose title leads with the pin, so a cover or
            // a lyric video mentioning the words can't win over the real one.
            candidates.minByOrNull { song ->
                val at = song.title.indexOf(pin.title, ignoreCase = true)
                if (at < 0) Int.MAX_VALUE else at
            }
        }
        if (extras.isEmpty()) return
        _pinnedSongs.value = (existing + extras).sortedBy { song ->
            com.example.data.model.PinnedTracks.ALL.indexOfFirst {
                com.example.data.model.PinnedTracks.matches(it, song.title, song.artist)
            }
        }
    }

    fun signInWithGoogle(
        activity: Activity,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        authManager.signInWithGoogle(activity, viewModelScope, onSuccess, onError)
    }

    /** Email/password sign-in — no Google account needed. */
    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        authManager.signInWithEmail(email, password, viewModelScope, onSuccess, onError)
    }

    /** Email/password registration with a unique username claim. */
    fun registerWithEmail(
        email: String,
        password: String,
        username: String,
        onSuccess: (NoxaUser) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        authManager.registerWithEmail(email, password, username, viewModelScope, onSuccess, onError)
    }

    /** Sends an SMS one-time code (OTP) to [phoneNumber] (E.164). */
    fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        authManager.sendPhoneOtp(
            activity = activity,
            phoneNumber = phoneNumber,
            scope = viewModelScope,
            onCodeSent = onCodeSent,
            onError = onError
        )
    }

    /** Verifies the SMS code the user received. */
    fun verifyPhoneOtp(
        verificationId: String,
        code: String,
        onError: (String) -> Unit
    ) {
        authManager.verifyPhoneOtp(
            verificationId = verificationId,
            code = code,
            scope = viewModelScope,
            onError = onError
        )
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

        searchJob = safeScope.launch {
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
        searchJob = safeScope.launch {
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
    /** Plays the favorites list in shuffled order (Library \u2192 Putar Acak). */
    fun playFavoritesShuffled() {
        val shuffled = favoriteSongs.value.shuffled()
        shuffled.firstOrNull()?.let { playSong(it, shuffled, 0) }
    }

    /**
     * Mix Sky \u2014 one shuffled queue from the three personal shelves: pinned
     * picks, favorites and recent history (each song appears once).
     */
    fun playSkyMix() {
        val pool = (pinnedSongs.value + favoriteSongs.value + historySongs.value)
            .distinctBy { it.id }
            .shuffled()
        pool.firstOrNull()?.let { playSong(it, pool, 0) }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        _resumableSong.value = null
        autoplayAppendedFor = null
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

    /**
     * Plays the song behind a shared NOXA link (`deezer:<id>`, `audius:<id>`,
     * `youtube:<id>`), with an honest error when the id no longer resolves.
     */
    fun playSharedSong(songId: String) {
        safeScope.launch {
            val song = if (songId.startsWith("youtube:")) {
                runCatching {
                    youtubeRepository.songByVideoId(songId.removePrefix("youtube:"))
                }.getOrNull()
            } else {
                runCatching { repository.songFromDeepLink(songId) }.getOrNull()
            }
            if (song == null) {
                player.showError(
                    "Lagu dari link tidak bisa ditemukan — mungkin sudah tidak tersedia."
                )
            } else {
                playSong(song)
            }
        }
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
        safeScope.launch {
            val resolved = resolvePlaybackSource(song)
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
     * One shared resolution ladder, used by both a fresh tap and a skip:
     * your own resolver first, then native YouTube audio, then the full-audio
     * matcher for a Deezer preview. Returns null when nothing better than the
     * song's own (preview) URL was found.
     */
    private suspend fun resolvePlaybackSource(song: Song): Song? {
        streamResolver.resolve(song)?.let { return it }
        if (song.source == MusicSource.YOUTUBE && song.streamUrl.isBlank()) {
            runCatching { youtubeRepository.resolveAudio(song) }.getOrNull()?.let { return it }
        }
        if (song.source == MusicSource.DEEZER && song.isPreview &&
            usagePolicyStore.isYouTubePolicyAccepted
        ) {
            runCatching { youtubeRepository.findFullAudio(song) }.getOrNull()?.let { return it }
        }
        return null
    }

    /**
     * YouTube URLs in the rest of the queue resolve lazily in the background
     * and are swapped into ExoPlayer when ready, so pressing "next" later just
     * works without blocking the current song. Deezer previews get the same
     * treatment through the full-audio matcher, capped so a long album queue
     * does not turn into one YouTube search per track.
     */
    private fun warmQueueStreams(queue: List<Song>) {
        safeScope.launch {
            queue
                .filter { it.source == MusicSource.YOUTUBE && it.streamUrl.isBlank() }
                .forEach { queued ->
                    runCatching { youtubeRepository.resolveAudio(queued) }
                        .getOrNull()
                        ?.let { player.updateSongStream(it) }
                }

            if (usagePolicyStore.isYouTubePolicyAccepted) {
                queue
                    .filter { it.source == MusicSource.DEEZER && it.isPreview }
                    // Low-RAM phones warm fewer upcoming tracks: each warm
                    // is a YouTube resolve + buffered stream, the priciest
                    // background work the player does.
                    .take(if (app.isLowRamDevice) 1 else PREVIEW_WARM_LIMIT)
                    .forEach { queued ->
                        runCatching { youtubeRepository.findFullAudio(queued) }
                            .getOrNull()
                            ?.let { player.updateSongStream(it) }
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
        safeScope.launch {
            val resolved = if (needsYoutube) {
                runCatching { youtubeRepository.resolveAudio(song) }.getOrNull()
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
        // Push the seek to the room right away (the heartbeat would lag 10s).
        safeScope.launch {
            kotlinx.coroutines.delay(350)
            publishRoomSnapshot()
        }
    }

    fun next() {
        skipTo(player.pendingNextIndex, plainSkip = { player.next() })
    }

    fun previous() {
        // More than three seconds in, previous restarts the current song —
        // no track change, so nothing to resolve.
        if (player.progress.value.positionMs > 3000L) {
            player.previous()
            return
        }
        skipTo(player.pendingPreviousIndex, plainSkip = { player.previous() })
    }

    /**
     * Skip that keeps full audio. The old skip jumped straight to the raw
     * queue entry, so a queued Deezer track started as its 30 second preview —
     * only a manual tap (which resolves first) played the full song. Now the
     * target is resolved exactly like a tap before the player advances, and
     * the queued entry remains the honest fallback when no full stream exists.
     */
    private fun skipTo(targetIndex: Int, plainSkip: () -> Unit) {
        val target = player.uiState.value.queue.getOrNull(targetIndex)
        val needsFullAudio = target != null &&
            ((target.source == MusicSource.YOUTUBE && target.streamUrl.isBlank()) ||
                (target.source == MusicSource.DEEZER && target.isPreview &&
                    usagePolicyStore.isYouTubePolicyAccepted))
        if (!needsFullAudio) {
            plainSkip()
            return
        }
        player.showResolving()
        safeScope.launch {
            val resolved = resolvePlaybackSource(target)
            if (resolved != null) {
                player.updateSongStream(resolved)
            } else {
                // Nothing better available: the queued (preview) entry plays.
                player.clearError()
            }
            plainSkip()
        }
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
     * YouTube tracks — and Deezer tracks matched to a YouTube video — can have
     * an MV; resolving can take a moment, so the UI shows a spinner until
     * [VideoUiState.Ready] arrives. The stream is picked at the quality chosen
     * in Profile & Settings.
     */
    fun setVideoMode(enabled: Boolean) {
        val song = player.uiState.value.currentSong
        val canHaveMv = song != null &&
            (song.source == MusicSource.YOUTUBE || song.matchedVideoId.isNotBlank())
        if (!enabled || song == null || !canHaveMv) {
            resetVideoMode()
            return
        }
        if (_videoUiState.value is VideoUiState.Loading || _videoUiState.value is VideoUiState.Ready) {
            return
        }
        mvAutoRetries = 0
        _videoUiState.value = VideoUiState.Loading
        safeScope.launch {
            _videoUiState.value = runCatching { resolveMvState(song) }
                .getOrElse { VideoUiState.Unavailable("MV tidak tersedia untuk lagu ini.") }
        }
    }

    /**
     * Resolves the MV stream with graceful quality fallback: the chosen
     * quality first, then 360p and 240p — a lower-resolution MV still beats
     * an error. When every attempt comes back empty (instances flap), one
     * more try runs after a short pause before giving up honestly.
     */
    private suspend fun resolveMvState(song: Song): VideoUiState {
        val requested = appSettings.value.mvQualityHeight
        var url: String? = null
        for (height in (listOf(requested) + MV_FALLBACK_QUALITIES).distinct()) {
            url = youtubeRepository.resolveVideoUrl(song, height)
            if (url != null) break
        }
        if (url == null) {
            delay(750)
            url = youtubeRepository.resolveVideoUrl(song, requested)
        }
        return if (url == null) {
            VideoUiState.Unavailable("MV tidak tersedia untuk lagu ini.")
        } else {
            VideoUiState.Ready(url)
        }
    }

    /** Re-resolves once after a mid-play stream error, then re-attaches. */
    private fun handleMvPlaybackError() {
        if (_videoUiState.value !is VideoUiState.Ready) return
        val surface = lastMvSurface ?: return
        val song = player.uiState.value.currentSong ?: return
        mvPlayer.release()
        if (mvAutoRetries >= 1) {
            _videoUiState.value = VideoUiState.Unavailable("MV gagal diputar. Coba lagi nanti.")
            return
        }
        mvAutoRetries++
        safeScope.launch {
            _videoUiState.value = VideoUiState.Loading
            val state = runCatching { resolveMvState(song) }
                .getOrElse { VideoUiState.Unavailable("MV tidak tersedia untuk lagu ini.") }
            _videoUiState.value = state
            (state as? VideoUiState.Ready)?.let { ready ->
                mvPlayer.playOnSurface(
                    ready.url,
                    surface,
                    player.progress.value.positionMs
                )
            }
        }
    }

    /** Called by the sheet with its SurfaceView when the video view appears. */
    fun attachMvSurface(surfaceView: SurfaceView?) {
        lastMvSurface = surfaceView
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
        safeScope.launch {
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
        safeScope.launch {
            dao.clearHistory()
        }
    }

    // Playlists
    fun createPlaylist(name: String, description: String = "") {
        safeScope.launch {
            dao.insertPlaylist(PlaylistEntity(name = name, description = description))
        }
    }

    fun deletePlaylist(id: Long) {
        safeScope.launch {
            dao.deletePlaylistSongs(id)
            dao.deletePlaylist(id)
        }
    }

    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        dao.getPlaylistSongs(playlistId).map { list -> list.map { it.toSong() } }

    fun playlistSongIds(playlistId: Long): Flow<List<String>> = dao.getPlaylistSongIds(playlistId)

    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = Int.MAX_VALUE) {
        safeScope.launch {
            dao.insertPlaylistSong(PlaylistSongEntity.fromSong(playlistId, song, position))
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        safeScope.launch {
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
        safeScope.launch {
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
        artistJob = safeScope.launch {
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
        albumJob = safeScope.launch {
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

    // ------------------------------------------------- NOXA Support & Admin

    /** Keyless shared backend: accounts, roles, support threads. */
    private val backend = com.example.data.support.NoxaBackend()

    // ------------------------------------------------------------- updates

    /** Latest update published by the developer, when this build is older. */
    private val _updateInfo =
        MutableStateFlow<com.example.data.support.NoxaBackend.UpdateInfo?>(null)
    val updateInfo: StateFlow<com.example.data.support.NoxaBackend.UpdateInfo?> =
        _updateInfo.asStateFlow()

    enum class UpdatePhase { Idle, Resolving, Downloading, ReadyToInstall, Failed }

    private val _updatePhase = MutableStateFlow(UpdatePhase.Idle)
    val updatePhase: StateFlow<UpdatePhase> = _updatePhase.asStateFlow()

    private val _updateError = MutableStateFlow<String?>(null)
    val updateError: StateFlow<String?> = _updateError.asStateFlow()

    /** Whether the update popup is on screen (Settings can reopen it). */
    private val _updateDialogVisible = MutableStateFlow(false)
    val updateDialogVisible: StateFlow<Boolean> = _updateDialogVisible.asStateFlow()

    private fun updatePrefs(): android.content.SharedPreferences =
        getApplication<Application>().getSharedPreferences("noxa_update", 0)

    /**
     * Quiet check on every app open: a newer published version offers the
     * dialog. Mandatory updates always show; optional ones show once per
     * version — "Nanti Saja" sticks until the developer pushes another
     * version or the user opens Pengaturan → Pembaruan. Skipped entirely for
     * the DEVELOPER role — see the collector in [init] for the sign-in race.
     */
    fun checkForUpdate() {
        if (authManager.currentUser.value?.role ==
            com.example.data.support.AccountRole.DEVELOPER
        ) return
        safeScope.launch {
            val info = runCatching { backend.readState()?.update }.getOrNull() ?: return@launch
            if (info.versionCode > com.example.BuildConfig.VERSION_CODE) {
                _updateInfo.value = info
                val skipped = updatePrefs().getLong(KEY_SKIPPED_UPDATE, -1L)
                if (info.mandatory || skipped != info.versionCode) {
                    _updatePhase.value = UpdatePhase.Idle
                    _updateError.value = null
                    _updateDialogVisible.value = true
                }
            }
        }
    }

    /** "Nanti Saja" on an optional update: hide and remember the choice. */
    fun dismissOptionalUpdate() {
        val info = _updateInfo.value ?: return
        _updateDialogVisible.value = false
        runCatching {
            updatePrefs().edit().putLong(KEY_SKIPPED_UPDATE, info.versionCode).apply()
        }
    }

    /** Reopens the popup from Pengaturan → Pembaruan. */
    fun openUpdateFromSettings() {
        if (_updateInfo.value == null) return
        _updatePhase.value = UpdatePhase.Idle
        _updateError.value = null
        _updateDialogVisible.value = true
    }

    /**
     * Downloads the pushed APK in-app (MediaFire links are resolved to their
     * direct file first), then hands it to the system installer — the
     * official Android "update" popup, no file manager needed.
     */
    fun downloadUpdate() {
        val info = _updateInfo.value ?: return
        val phase = _updatePhase.value
        if (phase == UpdatePhase.Resolving || phase == UpdatePhase.Downloading) return
        safeScope.launch {
            _updatePhase.value = UpdatePhase.Resolving
            _updateError.value = null
            // Off the main thread: Android throws NetworkOnMainThreadException
            // for the MediaFire page fetch, which surfaced as the false
            // "Link update tidak bisa diakses" error on top of the UA issue.
            val direct = runCatching {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.example.data.remote.UpdateRepository.resolveDirectApkUrl(info.url)
                }
            }.getOrNull()
            if (direct == null) {
                // Never dead-end: hand the link to the browser so the user
                // can still download and open the APK by hand.
                runCatching {
                    val app = getApplication<Application>()
                    app.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse(info.url)
                        ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                _updatePhase.value = UpdatePhase.Failed
                _updateError.value =
                    "Link dibuka di browser — unduh file APK-nya lalu buka file itu " +
                    "untuk instal. Atau coba lagi nanti."
                return@launch
            }
            val app = getApplication<Application>()
            val dir = java.io.File(app.getExternalFilesDir(null), "updates").apply { mkdirs() }
            val target = java.io.File(dir, "noxa-${info.versionName}.apk")
            if (target.exists()) target.delete()
            val dm = app.getSystemService(android.content.Context.DOWNLOAD_SERVICE)
                as android.app.DownloadManager
            val id = try {
                dm.enqueue(
                    android.app.DownloadManager.Request(android.net.Uri.parse(direct))
                        .setTitle("Update NOXA ${info.versionName}")
                        .setNotificationVisibility(
                            android.app.DownloadManager.Request.VISIBILITY_VISIBLE
                        )
                        .setDestinationUri(android.net.Uri.fromFile(target))
                        .setMimeType("application/vnd.android.package")
                )
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed to start: ${e.message}")
                _updatePhase.value = UpdatePhase.Failed
                _updateError.value = "Unduhan gagal dimulai. Coba lagi."
                return@launch
            }
            _updatePhase.value = UpdatePhase.Downloading
            while (true) {
                delay(700)
                var finished = false
                var success = false
                runCatching {
                    dm.query(android.app.DownloadManager.Query().setFilterById(id))?.use { c ->
                        if (!c.moveToFirst()) {
                            finished = true
                            success = true
                        } else {
                            val status = c.getInt(
                                c.getColumnIndexOrThrow(android.app.DownloadManager.COLUMN_STATUS)
                            )
                            finished = status == android.app.DownloadManager.STATUS_SUCCESSFUL ||
                                status == android.app.DownloadManager.STATUS_FAILED
                            success = status == android.app.DownloadManager.STATUS_SUCCESSFUL
                        }
                    }
                }
                if (finished) {
                    if (success && target.exists()) {
                        _updatePhase.value = UpdatePhase.ReadyToInstall
                        installApk(app, target)
                    } else {
                        _updatePhase.value = UpdatePhase.Failed
                        _updateError.value = "Unduhan update gagal. Coba lagi."
                    }
                    return@launch
                }
            }
        }
    }

    /** Opens the official Android installer for the downloaded APK. */
    private fun installApk(app: Application, file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                app, "${app.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package")
                addFlags(
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                )
            }
            app.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Installer launch failed: ${e.message}")
            _updatePhase.value = UpdatePhase.Failed
            _updateError.value = "Panel instalasi Android tidak bisa dibuka: ${e.message}"
        }
    }

    /**
     * Developer-only: publish an APK link as an update for every user.
     * `mandatory = false` gives users a "Nanti Saja" escape and a Settings
     * entry instead of the undismissable popup.
     */
    fun pushUpdate(
        url: String,
        versionName: String,
        versionCode: Long,
        note: String,
        mandatory: Boolean,
        onResult: (String?) -> Unit
    ) {
        safeScope.launch {
            val cleanUrl = url.trim()
            if (!cleanUrl.startsWith("http")) {
                onResult("Link APK harus diawali http/https (file langsung atau link MediaFire).")
                return@launch
            }
            if (versionCode <= com.example.BuildConfig.VERSION_CODE) {
                onResult(
                    "Version code harus lebih besar dari versi sekarang " +
                        "(${com.example.BuildConfig.VERSION_CODE})."
                )
                return@launch
            }
            val ok = runCatching {
                backend.publishUpdate(
                    com.example.data.support.NoxaBackend.UpdateInfo(
                        versionCode = versionCode,
                        versionName = versionName.trim().ifBlank { "v$versionCode" },
                        url = cleanUrl,
                        note = note.trim(),
                        at = System.currentTimeMillis(),
                        mandatory = mandatory
                    )
                )
            }.getOrDefault(false)
            onResult(if (ok) null else "Publish gagal — server tidak terjangkau. Coba lagi.")
        }
    }

    /** Permanently deletes the signed-in account (email must be re-typed). */
    fun deleteAccount(confirmEmail: String, onResult: (String?) -> Unit) {
        authManager.deleteAccount(
            confirmEmail = confirmEmail,
            scope = safeScope,
            onSuccess = { onResult(null) },
            onError = { onResult(it) }
        )
    }

    // ------------------------------------------- dengar bareng (rooms)

    private val _roomState =
        MutableStateFlow<com.example.ui.screens.RoomUiState>(com.example.ui.screens.RoomUiState.Idle)
    val roomState: StateFlow<com.example.ui.screens.RoomUiState> = _roomState.asStateFlow()

    /** The joined room code, or null. Drives every room hook below. */
    @Volatile
    private var roomCode: String? = null

    /** `updatedAt` of the last remote state applied — heartbeat filter. */
    @Volatile
    private var lastRemoteAt = 0L

    /** True while a remote change is being applied — blocks echo-publish. */
    @Volatile
    private var applyingRemote = false

    private var roomJob: kotlinx.coroutines.Job? = null

    /** Enters a room (creating it when the code is new) as the signed-in user. */
    fun joinRoom(code: String) {
        val normalized = code.trim().uppercase(java.util.Locale.ROOT)
        if (normalized.isBlank()) {
            _roomState.value = com.example.ui.screens.RoomUiState.Failed(
                normalized, "Isi kode room dulu."
            )
            return
        }
        val user = authManager.currentUser.value
        if (user == null) {
            _roomState.value = com.example.ui.screens.RoomUiState.Failed(
                normalized, "Masuk ke akun dulu — room terikat ke akun kamu."
            )
            return
        }
        safeScope.launch {
            _roomState.value = com.example.ui.screens.RoomUiState.Joining(normalized)
            val room = runCatching {
                backend.joinRoom(normalized, user.uid, user.displayName ?: user.email ?: "user")
            }.getOrNull()
            if (room == null) {
                _roomState.value = com.example.ui.screens.RoomUiState.Failed(
                    normalized, "Server tidak terjangkau. Periksa koneksi lalu coba lagi."
                )
                return@launch
            }
            roomCode = normalized
            lastRemoteAt = room.updatedAt
            _roomState.value = room.toUiState(normalized, user.uid)
            // Adopt whatever the room is playing right now (a new joiner
            // follows — they don't hijack the room with their silence).
            if (room.songId.isNotBlank()) applyRemoteRoom(room, force = true)
            startRoomLoop()
        }
    }

    /** Creates a room: a fresh 6-character code, then joins it. */
    fun createRoom() {
        val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789" // no 0/O/1/I confusion
        val code = (1..6).map { alphabet.random() }.joinToString("")
        joinRoom(code)
    }

    /** Leaves the room (the roster drops the last member's room entirely). */
    fun leaveRoom() {
        val code = roomCode ?: run {
            _roomState.value = com.example.ui.screens.RoomUiState.Idle
            return
        }
        roomJob?.cancel()
        roomJob = null
        roomCode = null
        applyingRemote = false
        _roomState.value = com.example.ui.screens.RoomUiState.Idle
        val user = authManager.currentUser.value
        if (user != null) {
            safeScope.launch { runCatching { backend.leaveRoom(code, user.uid) } }
        }
    }

    /** Poll loop: refresh members, apply remote song/playing/position. */
    private fun startRoomLoop() {
        roomJob?.cancel()
        roomJob = safeScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(ROOM_POLL_MS)
                val code = roomCode ?: break
                val user = authManager.currentUser.value ?: break
                val room = runCatching { backend.readRoom(code) }.getOrNull() ?: continue
                applyRemoteRoom(room, force = false)
                val current = _roomState.value
                if (current is com.example.ui.screens.RoomUiState.InRoom) {
                    // Full refresh: song/playing/members may have all moved
                    // when a peer took over — the header must follow.
                    _roomState.value = room.toUiState(code, user.uid)
                        .copy(positionMs = estimatedRoomPosition(room))
                }
            }
        }
    }

    /** Where the room's position *should* be right now. */
    private fun estimatedRoomPosition(
        room: com.example.data.support.NoxaBackend.RoomState
    ): Long {
        val elapsed = if (room.playing) System.currentTimeMillis() - room.updatedAt else 0L
        return (room.positionMs + elapsed).coerceAtLeast(0L)
    }

    /**
     * Applies remote room state to the local player. Skips when nothing
     * changed ([force] overrides for join-time adoption), never echoes back
     * (the mirror is gated by [applyingRemote]).
     */
    private suspend fun applyRemoteRoom(
        room: com.example.data.support.NoxaBackend.RoomState,
        force: Boolean
    ) {
        if (!force && room.updatedAt <= lastRemoteAt) return
        lastRemoteAt = room.updatedAt
        if (room.songId.isBlank()) return
        val currentId = playerState.value.currentSong?.id
        applyingRemote = true
        try {
            if (room.songId != currentId) {
                val song = runCatching { resolveSongId(room.songId) }.getOrNull() ?: return
                playSong(song)
                // The player auto-plays; match the room's actual state.
                kotlinx.coroutines.delay(500)
                if (!room.playing && playerState.value.isPlaying) player.togglePlayPause()
            } else {
                val playing = playerState.value.isPlaying
                if (room.playing != playing) player.togglePlayPause()
                val target = estimatedRoomPosition(room)
                val pos = playbackProgress.value.positionMs
                if (kotlin.math.abs(pos - target) > 3_000L) player.seekTo(target)
            }
            // Brief grace so the collector sees our own state settle before
            // the echo gate re-opens.
            kotlinx.coroutines.delay(700)
        } finally {
            applyingRemote = false
        }
    }

    private suspend fun resolveSongId(songId: String): Song? =
        if (songId.startsWith("youtube:")) {
            youtubeRepository.songByVideoId(songId.removePrefix("youtube:"))
        } else {
            repository.songFromDeepLink(songId)
        }

    /**
     * Local → room mirror. Deduped on (song, playing) so the collector
     * only publishes real changes; seeks and the 10s position heartbeat
     * call [mirrorNow] directly.
     */
    private fun publishRoomSnapshot() {
        val code = roomCode ?: return
        if (applyingRemote) return
        val user = authManager.currentUser.value ?: return
        val song = playerState.value.currentSong ?: return
        val playing = playerState.value.isPlaying
        val positionMs = playbackProgress.value.positionMs
        safeScope.launch {
            runCatching {
                backend.publishRoom(
                    code = code,
                    uid = user.uid,
                    name = user.displayName ?: user.email ?: "user"
                ) { room ->
                    room.copy(
                        songId = song.id,
                        songTitle = song.title,
                        songArtist = song.artist,
                        songArtwork = song.artworkUrl,
                        playing = playing,
                        positionMs = positionMs
                    )
                }
            }.getOrNull()?.let { published -> lastRemoteAt = published.updatedAt }
        }
    }

    /** Mirrors playback into the room whenever the local player changes. */
    private fun startRoomMirroring() {
        safeScope.launch {
            var lastKey: String? = null
            playerState.collect { st ->
                if (roomCode == null) {
                    lastKey = null
                    return@collect
                }
                val key = "${st.currentSong?.id}|${st.isPlaying}"
                if (applyingRemote || key == lastKey) return@collect
                lastKey = key
                publishRoomSnapshot()
            }
        }
        // Position heartbeat: keeps every member's timeline converging.
        safeScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(ROOM_HEARTBEAT_MS)
                if (roomCode != null && !applyingRemote && playerState.value.isPlaying) {
                    publishRoomSnapshot()
                }
            }
        }
    }

    private fun com.example.data.support.NoxaBackend.RoomState.toUiState(
        code: String,
        selfUid: String
    ): com.example.ui.screens.RoomUiState.InRoom =
        com.example.ui.screens.RoomUiState.InRoom(
            code = code,
            songId = songId,
            songTitle = songTitle,
            songArtist = songArtist,
            songArtwork = songArtwork,
            playing = playing,
            positionMs = positionMs,
            members = members.map { member ->
                com.example.ui.screens.RoomMemberUi(
                    uid = member.uid,
                    name = member.name,
                    isSelf = member.uid == selfUid
                )
            }
        )

    private val _supportState =
        MutableStateFlow<com.example.ui.screens.SupportUiState>(com.example.ui.screens.SupportUiState.Loading)
    val supportState: StateFlow<com.example.ui.screens.SupportUiState> =
        _supportState.asStateFlow()

    private val _devState =
        MutableStateFlow<com.example.ui.screens.DevUiState>(com.example.ui.screens.DevUiState.Loading)
    val devState: StateFlow<com.example.ui.screens.DevUiState> = _devState.asStateFlow()

    /**
     * Loads the user's support thread (or explains why chat is unavailable).
     * A refresh never drops an open chat back to a spinner — that would wipe
     * the composer draft and reset scroll mid-conversation.
     */
    fun openSupportChat() {
        safeScope.launch {
            if (_supportState.value !is com.example.ui.screens.SupportUiState.Ready) {
                _supportState.value = com.example.ui.screens.SupportUiState.Loading
            }
            _supportState.value = loadSupportState()
        }
    }

    fun refreshSupportChat() {
        safeScope.launch {
            val previous = _supportState.value
            val fresh = loadSupportState()
            _supportState.value = if (fresh is com.example.ui.screens.SupportUiState.Offline &&
                previous is com.example.ui.screens.SupportUiState.Ready
            ) {
                // Keep the conversation on screen with an inline error.
                previous.copy(error = "Gagal muat ulang. Periksa koneksi lalu coba lagi.")
            } else {
                fresh
            }
        }
    }

    private suspend fun loadSupportState(): com.example.ui.screens.SupportUiState {
        val user = authManager.currentUser.value
            ?: return com.example.ui.screens.SupportUiState.NeedsLogin
        val state = runCatching { backend.readState() }.getOrNull()
            ?: return com.example.ui.screens.SupportUiState.Offline
        return com.example.ui.screens.SupportUiState.Ready(state.threads[user.uid])
    }

    /**
     * Sends one user message. The latest crash trace (captured by
     * NoxaApplication) rides along automatically on the first message of a
     * turn, so bug reports arrive with the real stack trace attached.
     */
    fun sendSupportMessage(text: String) {
        val user = authManager.currentUser.value ?: return
        val ready = _supportState.value as? com.example.ui.screens.SupportUiState.Ready ?: return
        if (ready.sending) return
        safeScope.launch {
            _supportState.value = ready.copy(sending = true, error = null)
            val crash = readLatestCrashTrace()
            val ok = runCatching {
                backend.sendUserMessage(
                    uid = user.uid,
                    username = user.displayName ?: user.email ?: "user",
                    text = text,
                    crashTrace = crash
                )
            }.getOrDefault(false)
            val fresh = loadSupportState()
            _supportState.value = when {
                // A load blip must never replace an open conversation — the
                // composer draft and scroll would be lost.
                fresh !is com.example.ui.screens.SupportUiState.Ready &&
                    ready !is com.example.ui.screens.SupportUiState.Loading -> {
                    ready.copy(
                        sending = false,
                        error = if (ok) {
                            "Terkirim ke server, namun gagal sinkron. Ketuk muat ulang nanti."
                        } else {
                            "Pesan gagal dikirim. Periksa koneksi lalu coba lagi."
                        }
                    )
                }

                fresh is com.example.ui.screens.SupportUiState.Ready -> fresh.copy(
                    error = if (ok) null else "Pesan gagal dikirim. Periksa koneksi lalu coba lagi."
                )

                else -> fresh
            }
        }
    }

    private fun readLatestCrashTrace(): String? = try {
        java.io.File(getApplication<Application>().filesDir, "crash/latest-crash.txt")
            .takeIf { it.exists() && it.length() > 0 }
            ?.readText()
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------ admin only

    /** Loads the NOXA Admin console (developer role only). */
    fun openDevPanel() {
        safeScope.launch {
            _devState.value = com.example.ui.screens.DevUiState.Loading
            _devState.value = loadDevState()
        }
    }

    fun refreshDevPanel() {
        safeScope.launch { _devState.value = loadDevState() }
    }

    private suspend fun loadDevState(): com.example.ui.screens.DevUiState {
        val user = authManager.currentUser.value
        if (user == null || user.role != com.example.data.support.AccountRole.DEVELOPER) {
            return com.example.ui.screens.DevUiState.Offline
        }
        val state = runCatching { backend.readState() }.getOrNull()
            ?: return com.example.ui.screens.DevUiState.Offline
        return com.example.ui.screens.DevUiState.Ready(
            accounts = state.accounts.values
                .map {
                    com.example.data.support.BackendLogin(
                        uid = it.uid,
                        email = it.email,
                        username = it.username,
                        role = it.role,
                        createdAt = it.createdAt
                    )
                }
                .sortedByDescending { it.createdAt },
            threads = state.threads.values
                .sortedByDescending { it.lastUserAt.coerceAtLeast(it.lastSupportAt) }
        )
    }

    /** Staff reply: clears the user's pending flag, unlocking their composer. */
    fun sendSupportReply(threadUid: String, text: String) {
        safeScope.launch {
            (_devState.value as? com.example.ui.screens.DevUiState.Ready)?.let {
                _devState.value = it.copy(busy = true)
            }
            runCatching { backend.sendSupportMessage(threadUid, text) }
            _devState.value = loadDevState()
        }
    }

    /** Dev-console account creation with a role. Result: null = success. */
    fun createAccountAsDev(
        email: String,
        username: String,
        password: String,
        role: com.example.data.support.AccountRole,
        onResult: (String?) -> Unit
    ) {
        safeScope.launch {
            if (!com.example.data.auth.LocalAccountStore.isEmailFormatValid(email)) {
                onResult("Format email tidak valid.")
                return@launch
            }
            com.example.data.auth.UsernameRegistry.validate(username)?.let {
                onResult(it)
                return@launch
            }
            if (password.length < 6) {
                onResult("Password minimal 6 karakter.")
                return@launch
            }
            (_devState.value as? com.example.ui.screens.DevUiState.Ready)?.let {
                _devState.value = it.copy(busy = true)
            }
            val (login, failure) =
                backend.createAccountAsStaff(email, username, password, role)
            _devState.value = loadDevState()
            onResult(if (login != null) null else failure ?: "Akun gagal dibuat. Coba lagi.")
        }
    }

    fun changeAccountRole(uid: String, role: com.example.data.support.AccountRole) {
        safeScope.launch {
            runCatching { backend.updateRole(uid, role) }
            _devState.value = loadDevState()
        }
    }

    /**
     * Sends one message to NOXA AI. Replies can carry action tags: a play tag
     * resolves the song against the catalogue and starts it immediately, a
     * playlist tag builds the playlist — exactly the "AI controls the music"
     * behaviour, executed by the app rather than trusted to the model.
     */
    fun sendAiMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isAiThinking.value) return
        _aiMessages.update { it + com.example.data.ai.AiChatMessage(fromUser = true, text = trimmed) }
        _aiError.value = null
        _isAiThinking.value = true
        safeScope.launch {
            val reply = try {
                aiRepository.chat(trimmed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "NOXA AI failed: ${e.javaClass.simpleName}: ${e.message}")
                _aiError.value =
                    "Layanan NOXA AI sedang sulit dihubungi. Periksa koneksi internet lalu coba lagi."
                _isAiThinking.value = false
                return@launch
            }

            var cardSong: Song? = null
            var note: String? = null
            val notes = mutableListOf<String>()
            for (action in reply.actions) {
                when (action) {
                    is com.example.data.ai.NoxaAiAction.Play -> {
                        val song = resolveAiSong(action)
                        if (song != null) {
                            cardSong = song
                            playSong(song)
                        } else {
                            note = "lagu nggak ketemu di katalog NOXA"
                        }
                    }

                    is com.example.data.ai.NoxaAiAction.CreatePlaylist -> {
                        createPlaylistFromAi(action)?.let { notes.add(it) }
                    }
                }
            }

            val body = if (notes.isEmpty()) reply.text
            else reply.text + "\n" + notes.joinToString("\n")
            _aiMessages.update {
                it + com.example.data.ai.AiChatMessage(
                    fromUser = false,
                    text = body,
                    songId = cardSong?.id,
                    songTitle = cardSong?.title,
                    songArtist = cardSong?.artist,
                    songArtwork = cardSong?.artworkUrl?.takeIf { url -> url.isNotBlank() },
                    songNote = note
                )
            }
            aiRepository.saveHistory(_aiMessages.value)
            _isAiThinking.value = false
        }
    }

    /** Song card tap: replay by id, or toggle pause when it is already on. */
    fun playAiSongMessage(message: com.example.data.ai.AiChatMessage) {
        val id = message.songId ?: return
        if (player.uiState.value.currentSong?.id == id) {
            togglePlayPause()
            return
        }
        safeScope.launch {
            val song = runCatching { repository.songFromDeepLink(id) }.getOrNull()
                ?: runCatching {
                    if (id.startsWith("youtube:")) {
                        youtubeRepository.songByVideoId(id.removePrefix("youtube:"))
                    } else null
                }.getOrNull()
                ?: runCatching {
                    repository.search(
                        listOfNotNull(message.songArtist, message.songTitle).joinToString(" ")
                    ).firstOrNull()
                }.getOrNull()
            if (song != null) playSong(song) else {
                player.showError("Lagu dari NOXA AI tidak bisa dimuat ulang.")
            }
        }
    }

    fun clearAiChat() {
        _aiMessages.value = emptyList()
        _aiError.value = null
        aiRepository.resetSession()
    }

    /** Finds the AI's requested song in the catalogue, best match first. */
    private suspend fun resolveAiSong(action: com.example.data.ai.NoxaAiAction.Play): Song? {
        val query = listOf(action.artist, action.title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
        val results = runCatching { repository.search(query) }.getOrDefault(emptyList())
        return results.firstOrNull { song ->
            val titleMatches = song.title.contains(action.title, ignoreCase = true) ||
                action.title.contains(song.title, ignoreCase = true)
            val artistMatches = action.artist.isBlank() ||
                song.artist.contains(action.artist, ignoreCase = true) ||
                action.artist.contains(song.artist, ignoreCase = true)
            titleMatches && artistMatches
        } ?: results.firstOrNull()
    }

    /** Builds the AI-requested playlist; returns a status line for the chat. */
    private suspend fun createPlaylistFromAi(
        action: com.example.data.ai.NoxaAiAction.CreatePlaylist
    ): String? {
        val name = action.name.ifBlank { "Playlist NOXA AI" }
        var playlistId: Long? = null
        var added = 0
        for (track in action.tracks.take(20)) {
            val song = runCatching { repository.search(track) }
                .getOrDefault(emptyList())
                .firstOrNull() ?: continue
            if (playlistId == null) {
                playlistId = dao.insertPlaylist(
                    PlaylistEntity(name = name, description = "Dibuat oleh NOXA AI")
                )
            }
            dao.insertPlaylistSong(PlaylistSongEntity.fromSong(playlistId, song, added))
            added++
        }
        return if (playlistId != null && added > 0) {
            "Playlist \"$name\" sudah jadi, berisi $added lagu. Cek di Library."
        } else {
            "Playlist gagal dibuat — coba sebut judul lagu yang lebih spesifik."
        }
    }

    // ---------------------------------------------------- pengaturan akun

    /** Ganti username (server + mirror lokal). Result: null = sukses. */
    fun changeUsername(newUsername: String, onResult: (String?) -> Unit) {
        authManager.changeUsername(
            newUsername,
            viewModelScope,
            onSuccess = { onResult(null) },
            onError = { onResult(it) }
        )
    }

    /** Ganti password (verifikasi password lama dulu). Result: null = sukses. */
    fun changePassword(
        currentPassword: String,
        newPassword: String,
        onResult: (String?) -> Unit
    ) {
        authManager.changePassword(
            currentPassword,
            newPassword,
            viewModelScope,
            onSuccess = { onResult(null) },
            onError = { onResult(it) }
        )
    }
}
