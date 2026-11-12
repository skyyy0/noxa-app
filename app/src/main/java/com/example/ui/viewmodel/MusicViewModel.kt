package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.NoxaApplication
import com.example.data.local.AppSettings
import com.example.data.local.FavoriteSongEntity
import com.example.data.local.HeatmapStats
import com.example.data.local.ListeningHeatmap
import com.example.data.local.LocalMusicStore
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
import com.example.data.support.SharedPlaylistCodes
import com.example.media.MvPlayer
import com.example.media.PlaybackProgress
import com.example.media.PlayerUiState
import com.example.media.SoundProfiles
import com.example.data.auth.NoxaUser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.launch

private const val TAG = "MusicViewModel"

/** The MIME the system package installer matches when opening a downloaded APK. */
private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

/** SharedPreferences key: the versionCode an optional update was skipped on. */
private const val KEY_SKIPPED_UPDATE = "skipped_update_version"

/** How many preview tracks in a queue get warmed for full audio ahead of time. */
private const val PREVIEW_WARM_LIMIT = 3

/** A single silent retry for the audio resolver ladder (v3.7.0). */
private const val RESOLVE_RETRY_DELAY_MS = 350L

/**
 * "Online" window for the dev console: an account whose presence stamp is
 * younger than this counts as actively listening right now. The client
 * heartbeat refreshes the stamp every few minutes while the app is open.
 */
private const val ONLINE_WINDOW_MS = 2L * 60 * 1000

/** Autoplay-similar: trigger window before the end of the queue's last song. */
private const val AUTOPLAY_NEAR_END_MS = 15_000L

/**
 * Cari pakai lirik: one quiet beat after the last keystroke before the
 * network fires. Without this, every letter cost a request and the free
 * lyric APIs answered 503 — the feature looked broken.
 */
private const val SEARCH_DEBOUNCE_MS = 450L

/** Wrapped: how many top artists / songs the mini card & full screen list. */
private const val WRAPPED_ARTIST_LIMIT = 5
private const val WRAPPED_SONG_LIMIT = 5

/** How many similar tracks get appended when autoplay kicks in. */
private const val AUTOPLAY_APPEND_LIMIT = 5

private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Play records now back the 365-day heatmap, so a year of plays is kept
 * (aggregates are computed in SQLite — raw rows never pile up in memory).
 */
private const val PLAY_RECORD_RETENTION_MS = 365L * 24 * 60 * 60 * 1000

/** Home taste engine: how many artists/ids feed stats & recommendations. */
private const val TASTE_ARTIST_LIMIT = 4
private const val QUICK_GRID_LIMIT = 8
private const val FORYOU_ARTIST_LIMIT = 2
private const val FORYOU_RELATED_LIMIT = 4
private const val FORYOU_RELATED_ARTISTS = 2
private const val FORYOU_SONG_LIMIT = 12

/**
 * Dengar Bareng: how often the room is polled. Pulled in from 2 s to 1 s in
 * v2.0.0 so a peer's pause/seek lands within a heartbeat instead of visibly
 * lagging — the payload is a single small JSON doc, cheap enough to poll.
 */
private const val ROOM_POLL_MS = 1_000L
private const val ROOM_HEARTBEAT_MS = 6_000L

/**
 * Minimum spacing between two room page edits. Telegraph accepts roughly one
 * edit per second; sending chat, an AI line and a song change back to back is
 * what made the later writes disappear.
 */
private const val ROOM_WRITE_GAP_MS = 1_100L

/** Radar Rilis: releases younger than this count as "new", and how many
 * followed artists are checked per refresh. */
private const val RADAR_MAX_AGE_DAYS = 90L
private const val RADAR_ARTIST_LIMIT = 20

/**
 * Preemptive preload: this long before a track ends, the next one's full
 * stream is resolved in the background so the transition has zero loading.
 */
private const val PRELOAD_NEAR_END_MS = 30_000L

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

/** One artist counted from the user's own listening history. */
data class TopArtistTaste(val name: String, val plays: Int)

/** One song counted from the user's own listening history (full Wrapped). */
data class TopSongTaste(val title: String, val plays: Int)

/**
 * Profile's **Peta Panas Dengar** + **NOXA Wrapped Mini**: one year of
 * listening, aggregated on-device from Room's `play_records`.
 * `stats == null` means not enough data to draw yet.
 */
data class YearListeningUi(
    val stats: HeatmapStats? = null,
    val topArtists: List<TopArtistTaste> = emptyList(),
    val topSongTitle: String = "",
    val topSongPlays: Int = 0,
    /** Top songs for the full Wrapped screen (mini card uses the first). */
    val topSongs: List<TopSongTaste> = emptyList(),
    val distinctArtists: Int = 0,
    /** Busiest hour of day (0–23), or -1 when unknown. */
    val busiestHour: Int = -1
) {
    companion object {
        val EMPTY = YearListeningUi()
    }
}

/**
 * The home screen's "statistik kamu" card: what the user actually played in
 * the last seven days, derived from Room's `play_records`. Purely on-device —
 * this data never leaves the phone.
 */
data class ListeningStats(
    val playsWeek: Int = 0,
    val topArtists: List<TopArtistTaste> = emptyList(),
    /** Most-played song ids, best first (joined to history for artwork). */
    val topSongIds: List<String> = emptyList()
) {
    companion object {
        val EMPTY = ListeningStats()
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

    /**
     * Direct stream URLs expire and some instances refuse them; the embedded
     * YouTube player is the fallback that always plays, because it streams
     * from YouTube itself instead of a signed googlevideo link.
     */
    data class Embed(val videoId: String) : VideoUiState

    /** Video mode requested but no instance could provide a video stream. */
    data class Unavailable(val message: String) : VideoUiState
}

/** What the artist page should show. */
sealed interface ArtistUiState {
    data object Loading : ArtistUiState
    data class Ready(
        val profile: ArtistProfile,
        val topSongs: List<Song>,
        val albums: List<AlbumInfo>,
        /** Musically adjacent artists — the "penggemar juga mendengar" shelf. */
        val relatedArtists: List<ArtistProfile> = emptyList(),
        /** Short Wikipedia bio; null when unavailable (section hides). */
        val bio: String? = null,
        /** Scraped extras (YouTube subscribers, genre/origin/debut) or null. */
        val insights: com.example.data.remote.ArtistInsights? = null
    ) : ArtistUiState
    data class Error(val message: String) : ArtistUiState
}

/** Result of a "cari pakai lirik" reverse lookup, ready to render. */
data class LyricSearchUi(
    val query: String,
    val songs: List<Song>,
    val matchedLine: String,
    /** Non-null when no lyric source could be reached (honest, not "no match"). */
    val error: String? = null
) {
    companion object {
        val EMPTY = LyricSearchUi("", emptyList(), "")
    }
}

/** What the album page should show. */
sealed interface AlbumUiState {
    data object Loading : AlbumUiState
    data class Ready(val album: AlbumInfo, val tracks: List<Song>) : AlbumUiState
    data class Error(val message: String) : AlbumUiState
}

/**
 * What the collaborative-playlist flow should show. One flow drives both
 * sheets: the join sheet in the Library (Checking / Preview / Importing /
 * Imported / Error) and the share sheet in the playlist detail (Publishing /
 * Shared / Error). [SharedPlaylistUi.Idle] is the resting state.
 */
sealed interface SharedPlaylistUi {
    data object Idle : SharedPlaylistUi
    data class Publishing(val name: String) : SharedPlaylistUi
    data class Shared(val code: String, val name: String) : SharedPlaylistUi
    data object Checking : SharedPlaylistUi
    data class Preview(val playlist: com.example.data.support.SharedPlaylist) : SharedPlaylistUi
    data object Importing : SharedPlaylistUi
    data class Imported(val playlistId: Long, val name: String, val songCount: Int) : SharedPlaylistUi
    data class Error(val message: String) : SharedPlaylistUi
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

    // ------------------------------------------------- timer tidur + ambience

    private val ambiencePlayer = com.example.media.AmbiencePlayer(application)

    private val _ambience = MutableStateFlow<com.example.media.Ambience?>(null)

    /** Soundscape mixed under the music; null = off. */
    val ambience: StateFlow<com.example.media.Ambience?> = _ambience.asStateFlow()

    private val _ambienceVolume = MutableStateFlow(0.45f)
    val ambienceVolume: StateFlow<Float> = _ambienceVolume.asStateFlow()

    init {
        // The ambience is part of "going to sleep": when the countdown reaches
        // zero the music pauses and the rain should not keep playing alone.
        player.onSleepTimerFinished = {
            ambiencePlayer.stop()
            _ambience.value = null
        }
    }

    fun setAmbience(ambience: com.example.media.Ambience?) {
        _ambience.value = ambience
        if (ambience == null) ambiencePlayer.stop()
        else ambiencePlayer.play(ambience, _ambienceVolume.value)
    }

    fun setAmbienceVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _ambienceVolume.value = clamped
        ambiencePlayer.setVolume(clamped)
    }

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
            autoResumeOnOpen = settingsStore.autoResumeOnOpen,
            crossfadeEnabled = settingsStore.crossfadeEnabled,
            wifiOnlyStreaming = settingsStore.wifiOnlyStreaming
        )
    )

    /** MV quality, autoplay-similar, karaoke overlay and auto-resume. */
    val appSettings: StateFlow<AppSettings> = _appSettings.asStateFlow()

    // ------------------------------------------------------- bahasa (i18n)

    private val languageStore = app.languageStore

    /** Pengaturan → Bahasa. Default Indonesia (salinan app hari ini). */
    val appLanguage: StateFlow<com.example.data.local.AppLanguage> =
        languageStore.language

    fun setLanguage(language: com.example.data.local.AppLanguage) {
        languageStore.set(language)
    }

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

    /** Soft volume crossfade between consecutive tracks. */
    fun setCrossfadeEnabled(enabled: Boolean) {
        settingsStore.crossfadeEnabled = enabled
        _appSettings.value = _appSettings.value.copy(crossfadeEnabled = enabled)
        player.crossfadeEnabled = enabled
    }

    /**
     * **Hanya Wi-Fi** (v3.7.0): streaming is refused on mobile data instead of
     * quietly burning a quota. Local imports and downloaded tracks still play —
     * they cost no data, so the guard never touches them.
     */
    fun setWifiOnlyStreaming(enabled: Boolean) {
        settingsStore.wifiOnlyStreaming = enabled
        _appSettings.value = _appSettings.value.copy(wifiOnlyStreaming = enabled)
    }

    private fun isOnCellular(): Boolean = runCatching {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            ?: return@runCatching false
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }.getOrDefault(false)

    private fun blockedByDataSaver(song: Song): Boolean {
        if (!settingsStore.wifiOnlyStreaming) return false
        if (song.source == MusicSource.LOCAL) return false
        if (offlineVersion(song) != null) return false
        return isOnCellular()
    }

    private fun showDataSaverBlock(song: Song) {
        player.showError(
            "\"${song.title}\" butuh internet, dan mode Hanya Wi-Fi sedang aktif. " +
                "Sambungkan ke Wi-Fi atau matikan opsi itu di Pengaturan → Fitur Pemutar."
        )
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

    // Hero banner — "Hate Me" (Ellie Goulding with Juice WRLD), fetched live.
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

    // ---------------------------------------------- Sistem Pintar (v2.0.0)

    /**
     * True when the first-run device scan still has to run (fresh install).
     * The home route gates its content on this so the scan animation plays
     * before the feed hits the network.
     */
    private val _needsPerfScan = MutableStateFlow(!app.perfProfileStore.isReady)
    val needsPerfScan: StateFlow<Boolean> = _needsPerfScan.asStateFlow()

    /**
     * Runs the one-time device scan: measures RAM, cores and chipset, saves
     * the resulting profile and lets the home screen continue. ~1.8 s of
     * staged animation is spent so the scan feels deliberate, not glitchy.
     */
    fun runPerfScan() {
        safeScope.launch {
            _isScanning.value = true
            // Let the scan UI paint its first frame before measuring.
            delay(150)
            val profile = runCatching {
                com.example.data.local.PerfProfileStore.measure(app)
            }.getOrElse {
                com.example.data.local.PerfProfile.from(
                    totalMemBytes = 0L,
                    cores = Runtime.getRuntime().availableProcessors(),
                    isLowRamDevice = app.isLowRamDevice
                )
            }
            runCatching { app.perfProfileStore.save(profile) }
            _devicePerf.value = profile
            // A beat per detected item so the scan list reads naturally.
            delay(500)
            _isScanning.value = false
            delay(350)
            _needsPerfScan.value = false
            // The image pipeline is tuned from the profile right away.
            player.applyPerfProfile(profile)
        }
    }

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _devicePerf = MutableStateFlow(
        app.perfProfileStore.load() ?: com.example.data.local.PerfProfile.from(
            totalMemBytes = 0L,
            cores = Runtime.getRuntime().availableProcessors(),
            isLowRamDevice = app.isLowRamDevice
        )
    )

    /** The measured device profile — shown on the scan screen and Profile. */
    val devicePerf: StateFlow<com.example.data.local.PerfProfile> = _devicePerf.asStateFlow()

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

    // ------------------------------------------- v2.0.0: follow + radar rilis

    /** Ids of artists the user follows — drives the Ikuti button state. */
    val followedArtistIds: StateFlow<Set<Long>> = dao.getFollowedArtists()
        .map { list -> list.mapTo(HashSet()) { it.artistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** New releases from followed artists — the Home "Radar Rilis" shelf. */
    private val _releaseRadar = MutableStateFlow<List<AlbumInfo>>(emptyList())
    val releaseRadar: StateFlow<List<AlbumInfo>> = _releaseRadar.asStateFlow()

    /**
     * Follow/unfollow an artist from the artist page. Followed artists feed
     * the Home release-radar shelf (new albums within the last 90 days).
     */
    fun toggleFollowArtist(profile: ArtistProfile) {
        safeScope.launch {
            try {
                if (followedArtistIds.value.contains(profile.artistIdKey())) {
                    dao.unfollowArtist(profile.artistIdKey())
                } else {
                    dao.insertFollowedArtist(
                        com.example.data.local.FollowedArtistEntity(
                            artistId = profile.artistIdKey(),
                            name = profile.name,
                            pictureUrl = profile.pictureUrl
                        )
                    )
                    loadReleaseRadar()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Follow toggle failed: ${e.message}")
            }
        }
    }

    /** Deezer id as the Room key; profiles always carry it. */
    private fun ArtistProfile.artistIdKey(): Long = id

    /**
     * Radar Rilis: the newest album of each followed artist, when it is
     * younger than [RADAR_MAX_AGE_DAYS]. Fails soft per artist.
     */
    private suspend fun loadReleaseRadar() {
        val ids = runCatching { dao.followedArtistIdsOnce() }.getOrDefault(emptyList())
        if (ids.isEmpty()) {
            _releaseRadar.value = emptyList()
            return
        }
        val cutoff = java.time.LocalDate.now().minusDays(RADAR_MAX_AGE_DAYS)
        val recent = ids.take(RADAR_ARTIST_LIMIT).mapNotNull { id ->
            runCatching { repository.artistAlbums(id) }.getOrDefault(emptyList())
                .filter { album ->
                    runCatching {
                        java.time.LocalDate.parse(album.releaseDate).isAfter(cutoff)
                    }.getOrDefault(false)
                }
                .maxByOrNull { it.releaseDate }
        }
        _releaseRadar.value = recent
            .sortedByDescending { it.releaseDate }
            .distinctBy { it.id }
    }

    // ------------------------------------------- v2.0.0: cari pakai lirik

    private val _lyricSearch = MutableStateFlow(LyricSearchUi.EMPTY)
    val lyricSearch: StateFlow<LyricSearchUi> = _lyricSearch.asStateFlow()

    private var lyricSearchJob: Job? = null

    /**
     * **Cari pakai lirik** — reverse lookup: a line of lyrics becomes the
     * tracks those words belong to. LRCLIB/songsear.ch identify the song; the
     * NOXA catalogue then resolves each hit into something actually playable.
     *
     * Requests are debounced (one quiet beat before the network fires) so
     * typing does not hammer the sources with a request per keystroke — that
     * was what made the free lyric APIs answer 503 and the feature look dead.
     * Catalogue resolution runs in parallel: eight hits no longer wait in a
     * line.
     */
    fun searchByLyrics(fragment: String) {
        lyricSearchJob?.cancel()
        val trimmed = fragment.trim()
        if (trimmed.length < com.example.data.remote.LyricsRepository.MIN_LYRIC_QUERY) {
            _lyricSearch.value = LyricSearchUi.EMPTY
            _isSearching.value = false
            return
        }
        lyricSearchJob = safeScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            _isSearching.value = true
            _lyricSearch.value = LyricSearchUi.EMPTY
            try {
                val result = runCatching { lyricsRepository.searchByLyricText(trimmed) }
                val error = result.exceptionOrNull()
                val hits = result.getOrDefault(emptyList())
                val songs = if (hits.isEmpty()) {
                    emptyList()
                } else {
                    coroutineScope {
                        hits.map { hit ->
                            async { resolveLyricHit(hit) }
                        }.awaitAll().filterNotNull().distinctBy { it.id }
                    }
                }
                _lyricSearch.value = LyricSearchUi(
                    query = trimmed,
                    songs = songs,
                    matchedLine = trimmed,
                    // Reaching this point with no hits AND a thrown source means
                    // the network failed — say that instead of "no match".
                    error = if (songs.isEmpty() && error != null) {
                        "Layanan lirik tidak bisa dijangkau. Coba lagi sebentar lagi."
                    } else {
                        null
                    }
                )
            } finally {
                _isSearching.value = false
            }
        }
    }

    /**
     * Turns one web lyric hit into a playable catalogue song.
     *
     * "Artist Title" is the strongest query, but it returns nothing for a lot
     * of Indonesian tracks (Deezer spells the artist differently, or the hit is
     * a cover). The title alone is therefore tried next before giving up — that
     * single fallback is what made lyric search look broken when the source had
     * clearly found the song.
     */
    private suspend fun resolveLyricHit(
        hit: com.example.data.remote.LyricSearchHit
    ): Song? {
        fun matches(song: Song): Boolean =
            song.title.contains(hit.trackName, ignoreCase = true) ||
                hit.trackName.contains(song.title, ignoreCase = true)

        val byBoth = runCatching {
            repository.search("${hit.artistName} ${hit.trackName}").take(8)
        }.getOrDefault(emptyList())
        byBoth.firstOrNull(::matches)?.let { return it }

        val byTitle = runCatching {
            repository.search(hit.trackName).take(8)
        }.getOrDefault(emptyList())
        byTitle.firstOrNull(::matches)?.let { return it }
        // Last resort: the artist's own catalogue, matched loosely on title.
        return runCatching {
            repository.search(hit.artistName).take(8)
        }.getOrDefault(emptyList()).firstOrNull(::matches)
    }

    /** Clears the lyric-search result (leaving the Search tab). */
    fun clearLyricSearch() {
        lyricSearchJob?.cancel()
        _isSearching.value = false
        _lyricSearch.value = LyricSearchUi.EMPTY
    }

    // --------------------------------------- v2.0.0: rating & jangan putar lagi

    /** Song id → 1..5 stars. */
    val songRatings: StateFlow<Map<String, Int>> = dao.getSongRatings()
        .map { list -> list.associate { it.songId to it.rating } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** Muted songs and artists ("jangan putar lagi"). */
    private val _mutedSongs = MutableStateFlow<Set<String>>(emptySet())
    val mutedSongs: StateFlow<Set<String>> = _mutedSongs.asStateFlow()

    private val _mutedArtists = MutableStateFlow<Set<String>>(emptySet())
    val mutedArtists: StateFlow<Set<String>> = _mutedArtists.asStateFlow()

    /** Loads the mute list once; kept reactive via the same collector. */
    private fun observeMutedEntries() {
        safeScope.launch {
            dao.getMutedEntries().collect { entries ->
                _mutedSongs.value = entries
                    .filter { it.kind == com.example.data.local.MutedEntryEntity.KIND_SONG }
                    .mapTo(HashSet()) { it.value }
                _mutedArtists.value = entries
                    .filter { it.kind == com.example.data.local.MutedEntryEntity.KIND_ARTIST }
                    .mapTo(HashSet()) { it.value.lowercase() }
            }
        }
    }

    /** Stars 1..5, or 0 to clear the rating. */
    fun rateSong(song: Song, rating: Int) {
        safeScope.launch {
            try {
                if (rating <= 0) {
                    dao.clearSongRating(song.id)
                } else {
                    dao.upsertSongRating(
                        com.example.data.local.SongRatingEntity(
                            songId = song.id,
                            rating = rating.coerceIn(1, 5)
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rating failed: ${e.message}")
            }
        }
    }

    /** "Jangan putar lagi" for one song; the current queue entry is dropped. */
    fun muteSong(song: Song) {
        safeScope.launch {
            try {
                dao.insertMuted(
                    com.example.data.local.MutedEntryEntity(
                        kind = com.example.data.local.MutedEntryEntity.KIND_SONG,
                        value = song.id
                    )
                )
                player.removeFromQueue(song)
            } catch (e: Exception) {
                Log.w(TAG, "Mute failed: ${e.message}")
            }
        }
    }

    /** "Jangan putar lagi" for a whole artist. */
    fun muteArtist(song: Song) {
        val artist = song.artist.trim()
        if (artist.isEmpty()) return
        safeScope.launch {
            try {
                dao.insertMuted(
                    com.example.data.local.MutedEntryEntity(
                        kind = com.example.data.local.MutedEntryEntity.KIND_ARTIST,
                        value = artist
                    )
                )
                player.uiState.value.queue
                    .filter { it.artist.equals(artist, ignoreCase = true) && it.id != song.id }
                    .forEach { player.removeFromQueue(it) }
            } catch (e: Exception) {
                Log.w(TAG, "Mute artist failed: ${e.message}")
            }
        }
    }

    /** Undo a mute from the profile settings. */
    fun unmute(kind: String, value: String) {
        safeScope.launch {
            runCatching { dao.deleteMuted(kind, value) }
        }
    }

    /** True when autoplay / mix generation must skip this song. */
    private fun isMuted(song: Song): Boolean =
        song.id in _mutedSongs.value ||
            song.artist.trim().lowercase() in _mutedArtists.value

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

    /** Year-long heatmap + mini-wrapped for the profile screen. */
    private val _yearListening = MutableStateFlow(YearListeningUi.EMPTY)
    val yearListening: StateFlow<YearListeningUi> = _yearListening.asStateFlow()

    // Home "statistik kamu" card + the taste-derived "Buat kamu" shelf.
    private val _listeningStats = MutableStateFlow(ListeningStats.EMPTY)
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

    private val _quickPlaySongs = MutableStateFlow<List<Song>>(emptyList())
    val quickPlaySongs: StateFlow<List<Song>> = _quickPlaySongs.asStateFlow()

    private val _forYouSongs = MutableStateFlow<List<Song>>(emptyList())
    val forYouSongs: StateFlow<List<Song>> = _forYouSongs.asStateFlow()

    private val _forYouLabel = MutableStateFlow("")
    val forYouLabel: StateFlow<String> = _forYouLabel.asStateFlow()

    /** The taste signature the shelf was last built from (network guard). */
    private var lastTasteKey: List<String> = emptyList()

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

    // ------------------------------------- NOXA AI v3.5.5: model + memory + flow

    private val songMemoryStore = com.example.data.local.SongMemoryStore(getApplication())
    private val playlistCoverStore = com.example.data.local.PlaylistCoverStore(getApplication())

    /** Model yang dipilih pengguna di pemilih model NOXA AI. */
    private val _aiModel = MutableStateFlow(aiRepository.selectedModel)
    val aiModel: StateFlow<com.example.data.ai.AiModel> = _aiModel.asStateFlow()

    /** Status tiap model (maintenance / tersedia) dari `/v1/models`. */
    private val _aiModelStatuses =
        MutableStateFlow(aiRepository.cachedModelStatus())
    val aiModelStatuses: StateFlow<List<com.example.data.ai.AiModelStatus>> =
        _aiModelStatuses.asStateFlow()

    /**
     * Kuota tiap model di perangkat ini. Model flagship berbagi satu jatah,
     * Fable 5.1 dan Haiku punya jatah sendiri; akun developer unlimited.
     */
    private val _aiQuotas =
        MutableStateFlow(aiRepository.quotas())
    val aiQuotas: StateFlow<Map<com.example.data.ai.AiModel, com.example.data.ai.AiQuotaState>> =
        _aiQuotas.asStateFlow()

    /** True untuk akun developer: semua batas pesan dilewati. */
    private fun syncAiDeveloperFlag() {
        aiRepository.isDeveloper =
            authManager.currentUser.value?.role == com.example.data.support.AccountRole.DEVELOPER
    }

    /**
     * Mode kerja flagship yang aktif (Tulis Lirik / Bedah Lagu / Setlist).
     * Selalu null untuk model gratis — mode ini bagian dari nilai flagship.
     */
    private val _aiFlavor = MutableStateFlow<com.example.data.ai.AiFlavor?>(null)
    val aiFlavor: StateFlow<com.example.data.ai.AiFlavor?> = _aiFlavor.asStateFlow()

    fun selectAiFlavor(flavor: com.example.data.ai.AiFlavor?) {
        _aiFlavor.value = flavor
    }

    fun selectAiModel(model: com.example.data.ai.AiModel) {
        aiRepository.selectedModel = model
        _aiModel.value = model
        if (model.tier != com.example.data.ai.AiTier.FLAGSHIP) _aiFlavor.value = null
        syncAiDeveloperFlag()
        _aiQuotas.value = aiRepository.quotas()
    }

    /** Refresh daftar model; kegagalan jaringan mempertahankan snapshot lama. */
    fun refreshAiModelStatus() {
        syncAiDeveloperFlag()
        _aiQuotas.value = aiRepository.quotas()
        safeScope.launch {
            _aiModelStatuses.value = aiRepository.refreshModelStatus()
        }
    }

    /** Pesan ramah saat jatah model habis, lengkap dengan sisa waktunya. */
    private fun aiQuotaMessage(
        error: com.example.data.ai.NoxaAiRepository.AiQuotaExceeded
    ): String {
        val minutes = (
            (error.state.resetAtMs - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000L
            ) + 1L
        return "Jatah ${error.model.label} sudah habis (${error.state.limit} pesan). " +
            "Jatah kembali dalam sekitar $minutes menit. Model gratis tetap bisa dipakai sekarang."
    }

    /** Memori lagu untuk lagu yang sedang dibuka di sheet pemutar. */
    private val _songMemory = MutableStateFlow<com.example.data.local.SongMemory?>(null)
    val songMemory: StateFlow<com.example.data.local.SongMemory?> = _songMemory.asStateFlow()

    fun loadSongMemory(songId: String?) {
        _songMemory.value = songId?.let { songMemoryStore.forSong(it) }
    }

    fun saveSongMemory(song: Song, note: String) {
        songMemoryStore.save(song.id, note, song.title, song.artist)
        loadSongMemory(song.id)
    }

    fun clearSongMemory(songId: String) {
        songMemoryStore.remove(songId)
        loadSongMemory(songId)
    }

    fun allSongMemories(): List<com.example.data.local.SongMemory> = songMemoryStore.all()

    /** Flow / AutoMix: weave the upcoming queue instead of shuffling it. */
    private val _flowEnabled = MutableStateFlow(false)
    val flowEnabled: StateFlow<Boolean> = _flowEnabled.asStateFlow()

    fun setFlowEnabled(enabled: Boolean) {
        _flowEnabled.value = enabled
        if (!enabled) return
        val state = player.uiState.value
        val upcoming = state.queue.drop((state.queueIndex + 1).coerceAtLeast(0))
        if (upcoming.size < 2) {
            _flowEnabled.value = false
            player.showError("Flow butuh minimal tiga lagu di antrean.")
            return
        }
        player.setUpcomingQueue(com.example.media.FlowMixer.order(upcoming))
        player.showError("Flow aktif — antrean diurutkan ulang tanpa jeda.")
    }

    fun playlistCoverFor(playlistId: Long, name: String): com.example.data.local.PlaylistCover =
        playlistCoverStore.coverFor(playlistId, name)

    /** Bumped on every cover save so screens recompose with the new cover. */
    private val _playlistCoverVersion = MutableStateFlow(0)
    val playlistCoverVersion: StateFlow<Int> = _playlistCoverVersion.asStateFlow()

    fun savePlaylistCover(playlistId: Long, cover: com.example.data.local.PlaylistCover) {
        playlistCoverStore.save(playlistId, cover)
        _playlistCoverVersion.update { it + 1 }
    }

    // ---- collaborative-playlist state (declared BEFORE init on purpose:
    // initializers run in declaration order, and init reloads the stored links
    // — the same ordering trap that caused the v1.15.7 NPE).

    private val sharedPlaylistPrefs by lazy {
        getApplication<Application>().getSharedPreferences("noxa_shared_playlists", 0)
    }

    private val _sharedPlaylistUi = MutableStateFlow<SharedPlaylistUi>(SharedPlaylistUi.Idle)
    val sharedPlaylistUi: StateFlow<SharedPlaylistUi> = _sharedPlaylistUi.asStateFlow()

    /** Transient banners (sync result, push conflict) shown by the detail page. */
    private val _sharedNotice = MutableStateFlow<String?>(null)
    val sharedNotice: StateFlow<String?> = _sharedNotice.asStateFlow()

    /** playlistId (as string, prefs-friendly) -> share code. */
    private val _sharedLinks = MutableStateFlow<Map<String, String>>(emptyMap())
    val sharedLinks: StateFlow<Map<String, String>> = _sharedLinks.asStateFlow()

    /** Code for a linked playlist, for chips & share sheets. */
    fun sharedCodeFor(playlistId: Long): String? =
        _sharedLinks.value[playlistId.toString()]

    fun consumeSharedPlaylistUi() {
        if (_sharedPlaylistUi.value !is SharedPlaylistUi.Idle) {
            _sharedPlaylistUi.value = SharedPlaylistUi.Idle
        }
    }

    fun dismissSharedNotice() {
        _sharedNotice.value = null
    }

    private fun rememberLink(playlistId: Long, code: String, baseUpdatedAt: Long) {
        sharedPlaylistPrefs.edit()
            .putString(linkKey(playlistId), code)
            .putLong(baseKey(playlistId), baseUpdatedAt)
            .apply()
        _sharedLinks.update { it + (playlistId.toString() to code) }
    }

    private fun loadSharedLinks() {
        val links = mutableMapOf<String, String>()
        sharedPlaylistPrefs.all.forEach { (key, value) ->
            if (key.startsWith("link_") && value is String && value.isNotBlank()) {
                links[key.removePrefix("link_")] = value
            }
        }
        _sharedLinks.value = links
    }

    /**
     * Referensi stabil untuk [com.example.voice.NoxaWakeBus.handler] — properti
     * (bukan `::method` baru tiap akses) supaya bisa dicocokkan saat dilepas
     * di [onCleared] tanpa takut salah melepas handler milik instance lain.
     * Dideklarasikan sebelum `init` karena ikut dipasang di sana.
     */
    private val voiceCommandHandler: suspend (String) -> String = { transcript ->
        handleVoiceCommand(transcript)
    }

    // ------------------------------- Profil suara nirkabel (v3.2.1)

    /**
     * Popup state while NOXA tunes itself for a connected wireless output.
     * Step 0 = detecting, 1 = applying, 2 = done (auto-dismissed after).
     */
    data class TwsTuneState(
        val deviceName: String,
               val kindLabel: String,
        val step: Int,
        /** One-line report of what actually got applied — honest, never fake. */
        val summary: String? = null
    )

    private val _twsTune = MutableStateFlow<TwsTuneState?>(null)
    /** Non-null while the automatic wireless tuning popup is on screen. */
    val twsTune: StateFlow<TwsTuneState?> = _twsTune.asStateFlow()

    private val outputWatcher = com.example.media.OutputDeviceWatcher(application)
    /** Devices already tuned this session — reconnecting must not nag twice. */
    private val twsTunedDevices = HashSet<String>()
    private var twsTuneJob: Job? = null

    /**
     * **Full otomatis**: the moment a TWS/Bluetooth output appears, NOXA says
     * so in a popup and applies the wireless profile itself — the user never
     * opens a single slider. One popup per device per session.
     */
    private fun startWirelessTuning(device: com.example.media.AudioOutputDevice) {
        if (!twsTunedDevices.add(device.key)) return
        twsTuneJob?.cancel()
        twsTuneJob = safeScope.launch {
            _twsTune.value = TwsTuneState(device.name, device.kindLabel, step = 0)
            delay(550)
            _twsTune.value = TwsTuneState(device.name, device.kindLabel, step = 1)
            val result = player.requestAutoTune()
            _twsTune.value = TwsTuneState(
                deviceName = device.name,
                kindLabel = device.kindLabel,
                step = 2,
                summary = autoTuneSummary(result)
            )
            delay(1_800)
            _twsTune.value = null
        }
    }

    /** What really happened, in one line — including "not yet possible". */
    private fun autoTuneSummary(result: com.example.media.AutoTuneResult?): String =
        if (result == null) {
            "Profil disiapkan — aktif otomatis saat musik diputar."
        } else {
            buildString {
                append("${result.routeLabel} · ${result.bandsApplied} band")
                append(if (result.dspActive) " · DSP + limiter" else " · mode aman")
                append(" · headroom ${result.headroomDb.toInt()} dB")
            }
        }

    /** Manual close from the popup. */
    fun dismissTwsTune() {
        twsTuneJob?.cancel()
        _twsTune.value = null
    }

    init {
        // Wireless detection is permission-free: it listens to audio outputs,
        // not to the Bluetooth stack, so it also sees USB dongles and LE audio.
        safeScope.launch {
            outputWatcher.connections.collect { device -> startWirelessTuning(device) }
        }
        outputWatcher.start()
    }

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
        observeCurrentSong()
        observeAutoplaySimilar()
        // Full-audio guarantee when ExoPlayer advances on its own (a song ends):
        // hold the transition until the next track's full stream is resolved.
        player.onAutoTransition = { song -> handleAutoAdvance(song) }
        // A stream that dies mid-song (expired instance URL, network blip) is
        // re-resolved silently and playback continues in place — the user
        // never sees an error for a hiccup the app can fix itself.
        player.onStreamExpired = { song -> refreshExpiredStream(song) }
        observeWeeklyRecap()
        observeListeningTaste()
        observeYearListening()
        observeMutedEntries()
        // Preemptive preload: the next track's stream is resolved ahead of
        // time so the transition has no loading pause (tier-sized window).
        observePreloadNext()
        // Crossfade starts in the player's hands from the very first frame.
        player.crossfadeEnabled = settingsStore.crossfadeEnabled && app.perfProfile.crossfadeAllowed
        // Collaborative-playlist links (playlistId -> share code) survive
        // restarts; reload them so chips and share sheets know immediately.
        loadSharedLinks()
        // Restore the NOXA AI transcript so the assistant keeps continuity.
        _aiMessages.value = aiRepository.loadHistory()
        observeOfflineDownloads()
        // NOXA Wake (v3.0.0): layanan di background mengirim transcript ke
        // sini selama UI hidup, supaya jalur pemutaran penuh (kebijakan
        // YouTube, salinan offline, ladder resolusi audio) yang dipakai —
        // sama persis dengan mengetuk lagu di layar.
        com.example.voice.NoxaWakeBus.handler = voiceCommandHandler
    }

    /**
     * Satu perintah suara dari NOXA Wake → aksi + pesan hasil untuk bubble.
     * Penerjemahan teks ada di [com.example.voice.WakeCommandParser]; sini
     * hanya menjalankannya lewat jalur play yang sudah ada.
     */
    private suspend fun handleVoiceCommand(transcript: String): String {
        return when (val cmd = com.example.voice.WakeCommandParser.parse(transcript)) {
            is com.example.voice.WakeCommand.Play -> {
                val query = cmd.query.trim()
                val results = if (query.isEmpty()) emptyList() else searchAll(query)
                if (results.isEmpty()) {
                    "Lagu \u201c$query\u201d tidak ditemukan."
                } else {
                    val target = results.first()
                    playSong(target, results, 0)
                    if (_pendingYouTubePlay.value != null) {
                        "Butuh sekali persetujuan kebijakan YouTube di dalam NOXA."
                    } else {
                        "Memutar \u201c${target.title}\u201d \u2014 ${target.artist}"
                    }
                }
            }

            com.example.voice.WakeCommand.Pause ->
                if (player.uiState.value.isPlaying) {
                    player.togglePlayPause()
                    "Dijeda."
                } else {
                    "Sudah dalam keadaan jeda."
                }

            com.example.voice.WakeCommand.Resume ->
                if (player.uiState.value.isPlaying) {
                    "Sudah diputar."
                } else {
                    player.togglePlayPause()
                    "Dilanjutkan."
                }

            com.example.voice.WakeCommand.Next -> {
                next()
                "Lagu berikutnya."
            }

            com.example.voice.WakeCommand.Previous -> {
                previous()
                "Lagu sebelumnya."
            }

            is com.example.voice.WakeCommand.Unknown ->
                if (cmd.text.isBlank()) "" else com.example.voice.WakeCommandParser.HINT
        }
    }

    // ------------------------------------------------------- mode offline

    /** songId → stored row; swapped into the queue when a track plays. */
    private var offlineById: Map<String, com.example.data.local.OfflineTrackEntity> = emptyMap()

    /** Downloaded songs, newest first — the Library "Unduhan" tab. */
    private val _offlineSongs = MutableStateFlow<List<Song>>(emptyList())
    val offlineSongs: StateFlow<List<Song>> = _offlineSongs.asStateFlow()

    /** Ids already on disk — the menu switches to "Hapus unduhan". */
    private val _downloadedIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedIds: StateFlow<Set<String>> = _downloadedIds.asStateFlow()

    /** Ids currently being fetched — the menu shows "Mengunduh…". */
    private val _downloadingIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadingIds: StateFlow<Set<String>> = _downloadingIds.asStateFlow()

    /** One-line feedback after a download/remove; consumed by NoxaApp's toast. */
    private val _downloadNotice = MutableStateFlow<String?>(null)
    val downloadNotice: StateFlow<String?> = _downloadNotice.asStateFlow()

    fun consumeDownloadNotice() {
        _downloadNotice.value = null
    }

    private fun observeOfflineDownloads() {
        safeScope.launch {
            dao.observeOfflineTracks().collect { rows ->
                // A row whose file the system cleared (storage purge, clear
                // data) is dropped instead of being listed as playable.
                val alive = rows.filter { it.audioExists() }
                offlineById = alive.associateBy { it.songId }
                _downloadedIds.value = alive.map { it.songId }.toSet()
                _offlineSongs.value = alive.map { it.toSong() }
            }
        }
    }

    // ------------------------------------------------- musik lokal (v3.7.0)

    /** Files the user imported from this device — the Library "Lokal" tab. */
    private val _localSongs = MutableStateFlow(LocalMusicStore.load(app))
    val localSongs: StateFlow<List<Song>> = _localSongs.asStateFlow()

    /**
     * Imports the URIs returned by the system picker. The persisted read grant
     * is taken in the UI layer (it needs the Activity's content resolver); here
     * we only read metadata and store the list.
     */
    fun importLocalSongs(uris: List<Uri>) {
        if (uris.isEmpty()) return
        safeScope.launch {
            val known = _localSongs.value.map { it.streamUrl }.toSet()
            val all = try {
                LocalMusicStore.import(app, uris)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Local import failed: ${e.message}")
                _downloadNotice.value = "Impor musik lokal gagal. Coba pilih file lain."
                return@launch
            }
            _localSongs.value = all
            val added = all.count { it.streamUrl !in known }
            _downloadNotice.value = when {
                added == 0 -> "File itu sudah ada di koleksi lokal."
                added == 1 -> "1 lagu lokal ditambahkan ke Koleksi."
                else -> "$added lagu lokal ditambahkan ke Koleksi."
            }
        }
    }

    /** Drops one imported track from the collection; the file stays untouched. */
    fun removeLocalSong(song: Song) {
        _localSongs.value = LocalMusicStore.remove(app, song.id)
        _downloadNotice.value = "\"${song.title}\" dihapus dari koleksi lokal."
    }

    // ------------------------------------------- cuplikan 30 detik (v3.7.0)

    /**
     * A track whose full audio could not be found. Instead of silently playing
     * a 30 second Deezer clip — which looked like a broken song — NOXA asks.
     */
    data class PreviewFallback(
        val song: Song,
        val queue: List<Song>,
        val index: Int
    )

    private val _previewFallback = MutableStateFlow<PreviewFallback?>(null)
    val previewFallback: StateFlow<PreviewFallback?> = _previewFallback.asStateFlow()

    /** "Putar cuplikan" — explicit consent, never automatic. */
    fun acceptPreviewFallback() {
        val pending = _previewFallback.value ?: return
        _previewFallback.value = null
        val held = player.uiState.value.currentSong?.id == pending.song.id &&
            !player.uiState.value.isPlaying
        if (held) {
            player.swapCurrentStreamAndRestart(pending.song)
        } else {
            player.playSong(pending.song, pending.queue, pending.index)
        }
    }

    /** "Lewati" — moves on (or clears the hold) without playing the clip. */
    fun skipPreviewFallback() {
        val pending = _previewFallback.value ?: return
        _previewFallback.value = null
        val held = player.uiState.value.currentSong?.id == pending.song.id &&
            !player.uiState.value.isPlaying
        if (held) {
            if (player.pendingNextIndex >= 0) {
                // The player is paused while the full stream was being looked
                // for; skipping must also hand playback back to the transport.
                player.next()
                player.resumeAfterHold()
            } else {
                player.endResolving()
            }
        } else {
            player.endResolving()
        }
    }

    /** The local copy of a song (playable from disk), or null when streaming. */
    private fun offlineVersion(song: Song): Song? =
        offlineById[song.id]?.takeIf { it.audioExists() }?.toSong()

    /** Menu action: download a fresh track, or delete the stored one. */
    fun toggleDownload(song: Song) {
        if (_downloadingIds.value.contains(song.id)) return
        if (offlineById[song.id] != null) {
            removeDownload(song)
        } else {
            downloadSong(song)
        }
    }

    private fun removeDownload(song: Song) {
        val row = offlineById[song.id] ?: return
        safeScope.launch {
            com.example.data.offline.OfflineFileStore.delete(
                listOf(row.audioPath, row.artworkPath)
            )
            dao.deleteOfflineTrack(song.id)
            _downloadNotice.value = "Unduhan \"${song.title}\" dihapus."
        }
    }

    /**
     * Downloads the best available audio for [song] (same resolution ladder
     * as playback, so a Deezer preview becomes the full track when the
     * matcher finds it) plus its artwork, then records the row in Room.
     */
    private fun downloadSong(song: Song) {
        if (blockedByDataSaver(song)) {
            _downloadNotice.value =
                "Mode Hanya Wi-Fi aktif — unduhan menunggu koneksi Wi-Fi."
            return
        }
        safeScope.launch {
            _downloadingIds.update { it + song.id }
            try {
                val source = resolvePlaybackSource(song) ?: song
                if (!source.streamUrl.startsWith("http")) {
                    throw IllegalStateException("sumber audio tidak bisa diunduh")
                }
                val base = com.example.data.offline.OfflineFileStore.baseNameFor(song.id)
                val store = com.example.data.offline.OfflineFileStore
                val audio = store.fetch(app, source.streamUrl, base)
                    ?: throw IllegalStateException("unduhan kosong")
                val artwork = runCatching {
                    if (song.artworkUrl.startsWith("http")) {
                        store.fetch(app, song.artworkUrl, "${base}_art")?.path
                    } else {
                        null
                    }
                }.getOrDefault(null).orEmpty()

                dao.upsertOfflineTrack(
                    com.example.data.local.OfflineTrackEntity.fromSong(
                        song = song,
                        audioPath = audio.path,
                        sizeBytes = audio.sizeBytes,
                        artworkPath = artwork
                    )
                )
                _downloadNotice.value = "\"${song.title}\" siap diputar offline."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Download failed: ${e.javaClass.simpleName}: ${e.message}")
                _downloadNotice.value =
                    "Mengunduh \"${song.title}\" gagal — periksa koneksi lalu coba lagi."
            } finally {
                _downloadingIds.update { it - song.id }
            }
        }
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
     * **Peta Panas Dengar + Wrapped Mini**: aggregates a year of plays in
     * SQLite (day, artist, song, hour) and anchors the heatmap on "today" at
     * every emission. Purely on-device — none of this leaves the phone.
     */
    private fun observeYearListening() {
        safeScope.launch {
            val since = System.currentTimeMillis() - PLAY_RECORD_RETENTION_MS
            combine(
                dao.observePlaysPerDay(since),
                dao.observeTopArtists(since, WRAPPED_ARTIST_LIMIT),
                dao.observeTopTitles(since, WRAPPED_SONG_LIMIT),
                dao.observePlaysPerHour(since),
                dao.observeDistinctArtistCount(since)
            ) { days, artists, titles, hours, distinct ->
                val stats = ListeningHeatmap.build(
                    dayPlays = days.associate { it.day to it.plays },
                    todayDayIndex = ListeningHeatmap.dayIndex(System.currentTimeMillis())
                )
                if (stats.totalPlays == 0) {
                    YearListeningUi.EMPTY
                } else {
                    YearListeningUi(
                        stats = stats,
                        topArtists = artists.map { TopArtistTaste(it.artist, it.plays) },
                        topSongTitle = titles.firstOrNull()?.title.orEmpty(),
                        topSongPlays = titles.firstOrNull()?.plays ?: 0,
                        topSongs = titles.map { TopSongTaste(it.title, it.plays) },
                        distinctArtists = distinct,
                        busiestHour = hours.maxByOrNull { it.plays }?.hour ?: -1
                    )
                }
            }.collect { _yearListening.value = it }
        }
    }

    /**
     * Reads the user's own listening statistics (Room `play_records`, last
     * seven days) and keeps the home screen's stats card, quick-play grid and
     * "Buat kamu" recommendations in step with them. Purely on-device.
     */
    private fun observeListeningTaste() {
        safeScope.launch {
            combine(
                dao.getPlayRecordsSince(System.currentTimeMillis() - WEEK_MS),
                historySongs
            ) { records, history -> records to history }
                .collect { (records, history) ->
                    val artists = records
                        .groupBy { it.artist.trim() }
                        .filterKeys { it.isNotBlank() }
                        .map { (name, plays) -> TopArtistTaste(name, plays.size) }
                        .sortedByDescending { it.plays }
                        .take(TASTE_ARTIST_LIMIT)
                    val topSongIds = records.groupingBy { it.songId }.eachCount()
                        .toList()
                        .sortedByDescending { it.second }
                        .take(QUICK_GRID_LIMIT)
                        .map { it.first }
                    _listeningStats.value = ListeningStats(
                        playsWeek = records.size,
                        topArtists = artists,
                        topSongIds = topSongIds
                    )
                    // Artwork lives in the history table, so the quick-play
                    // grid joins the ids onto it here.
                    val historyById = history.associateBy { it.id }
                    _quickPlaySongs.value = topSongIds.mapNotNull { historyById[it] }

                    // Refresh the shelf only when the taste itself changed —
                    // not on every single play.
                    val tasteKey = artists.map { it.name }
                    if (tasteKey != lastTasteKey) {
                        lastTasteKey = tasteKey
                        loadForYou(artists)
                    }
                }
        }
    }

    /**
     * "Buat kamu": top tracks of the user's most-played artists plus the top
     * tracks of Deezer's *related* neighbours (the honest "genre mirip"
     * signal). Fails soft — a dead endpoint only leaves the shelf hidden.
     */
    private suspend fun loadForYou(topArtists: List<TopArtistTaste>) {
        if (topArtists.isEmpty()) {
            _forYouSongs.value = emptyList()
            _forYouLabel.value = ""
            return
        }
        val seen = HashSet<String>()
        val picked = mutableListOf<Song>()
        fun addAll(songs: List<Song>) {
            songs.forEach { song -> if (seen.add(song.id)) picked += song }
        }
        topArtists.take(FORYOU_ARTIST_LIMIT).forEach { taste ->
            val artistId = runCatching { repository.findArtistId(taste.name) }.getOrNull()
                ?: return@forEach
            addAll(
                runCatching { repository.artistTopTracks(artistId, 6) }.getOrDefault(emptyList())
            )
            runCatching { repository.relatedArtists(artistId, FORYOU_RELATED_LIMIT) }
                .getOrDefault(emptyList())
                .take(FORYOU_RELATED_ARTISTS)
                .forEach { related ->
                    addAll(
                        runCatching { repository.artistTopTracks(related.id, 3) }
                            .getOrDefault(emptyList())
                    )
                }
        }
        _forYouSongs.value = picked.take(FORYOU_SONG_LIMIT)
        _forYouLabel.value = "Karena kamu sering dengar ${topArtists.first().name}"
    }

    /**
     * Autoplay of similar music: a few seconds before the last track of a queue
     * ends, the artist's other top tracks are appended — resolved first so they
     * are genuinely playable, not dead preview links.
     */
    private var autoplayAppendedFor: String? = null

    /** Guards against holding the same auto-advanced track twice. */
    private var lastHeldForResolveId: String? = null

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

    /**
     * **Preemptive preload** — the seamless-transition engine. While a track
     * plays and the queue has a next item, once playback enters the tier's
     * preload window (30 s HEMAT → 60 s NGEBUT), the next track's full stream
     * is resolved in the background and swapped into ExoPlayer's timeline.
     * When the song ends the player advances instantly: the URL is already
     * buffered — no "memuat lagu" hold, no gap.
     */
    private var preloadedForId: String? = null

    private fun observePreloadNext() {
        safeScope.launch {
            combine(player.uiState, player.progress) { s, p -> s to p }
                .collect { (state, progress) ->
                    if (!state.isPlaying) return@collect
                    val idx = state.queueIndex
                    if (idx < 0 || idx >= state.queue.lastIndex) return@collect
                    val next = state.queue[idx + 1]
                    if (next.id == preloadedForId || isMuted(next)) return@collect
                    val duration = state.durationMs
                    if (duration <= 0L) return@collect
                    val remaining = duration - progress.positionMs
                    if (remaining > app.perfProfile.preloadWindowMs) return@collect
                    preloadedForId = next.id
                    preloadStream(next)
                }
        }
    }

    /** Resolves the next track's stream and swaps it into the timeline. */
    private fun preloadStream(song: Song) {
        safeScope.launch {
            // Offline tracks are already local — resolving them would only
            // swap the file back onto the network. Imported local files have no
            // network source at all.
            if (song.source == MusicSource.LOCAL) return@launch
            if (offlineById.containsKey(song.id)) return@launch
            val resolved = runCatching { resolvePlaybackSource(song) }.getOrNull()
                ?: return@launch
            if (resolved.streamUrl.isNotBlank() && resolved.streamUrl != song.streamUrl) {
                player.updateSongStream(resolved)
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
                    candidate.id != song.id && playedQueue.none { it.id == candidate.id } &&
                        !isMuted(candidate)
                }
                .take(AUTOPLAY_APPEND_LIMIT)                .forEach { candidate ->
                    val resolved = if (
                        candidate.source == MusicSource.DEEZER && candidate.isPreview &&
                            usagePolicyStore.isYouTubePolicyAccepted
                    ) {
                        runCatching { youtubeRepository.findFullAudio(candidate) }.getOrNull()
                    } else {
                        candidate
                    }
                    // Downloaded tracks keep their local file even when they
                    // come back in an auto-appended batch.
                    player.addToQueue(
                        offlineVersion(candidate) ?: resolved ?: candidate
                    )
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
                // Radar Rilis rides along with the home load — it is cheap
                // (albums of already-followed artists only) and fails soft.
                runCatching { loadReleaseRadar() }
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
     * A pin the Deezer catalogue does not serve (a delisted or region-blocked
     * pick) is resolved from the YouTube source instead, so the Sky shelf
     * always shows every pick.
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
        // Order by the pin list — relaxed-aware, so a YouTube-served pick
        // (strictly unmatched) still lands in its shelf position instead of
        // sorting to the front with index -1.
        _pinnedSongs.value = (existing + extras).sortedBy { song ->
            com.example.data.model.PinnedTracks.ALL.indexOfFirst { pin ->
                com.example.data.model.PinnedTracks.matches(pin, song.title, song.artist) ||
                    com.example.data.model.PinnedTracks.matchesRelaxed(pin, song.title, song.artist)
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

    // ----------------------------------------------------------------- radio

    /** True while a radio queue is being built — spins the buttons. */
    private val _isBuildingRadio = MutableStateFlow(false)
    val isBuildingRadio: StateFlow<Boolean> = _isBuildingRadio.asStateFlow()

    /**
     * **Radio dari lagu** — builds a shuffled queue around the song's artist
     * (their top tracks plus related artists') and starts it at the seed song.
     * The player shows "menyiapkan" while the queue is fetched, and a failed
     * build surfaces as an honest player error instead of a silent no-op.
     */
    fun startSongRadio(song: Song) {
        startRadio { repository.radioFromSong(song) }
    }

    /**
     * **Radio dari artis** — same engine, seeded by the artist page. The id is
     * resolved from the name when the caller only knows the name.
     */
    fun startArtistRadio(artistId: String, artistName: String) {
        startRadio {
            val id = artistId.toLongOrNull()
                ?: repository.findArtistId(artistName)
                ?: return@startRadio emptyList()
            repository.radioFromArtist(id)
        }
    }

    private fun startRadio(build: suspend () -> List<Song>) {
        if (_isBuildingRadio.value) return
        safeScope.launch {
            _isBuildingRadio.value = true
            player.showResolving()
            val queue = try {
                build()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Radio failed: ${e.javaClass.simpleName}: ${e.message}")
                emptyList()
            }
            _isBuildingRadio.value = false
            val playable = queue.filter { it.streamUrl.isNotBlank() && !isMuted(it) }
            if (playable.isEmpty()) {
                player.showError(
                    "Radio belum bisa dibuat dari sumber ini. " +
                        "Periksa koneksi internet lalu coba lagi."
                )
                return@launch
            }
            playSong(playable.first(), playable, 0)
        }
    }

    fun playSong(song: Song, queue: List<Song> = listOf(song), index: Int = 0) {
        _resumableSong.value = null
        autoplayAppendedFor = null
        preloadedForId = null
        // YouTube tracks always need the policy — there is no other audio.
        // Deezer previews only *may* use YouTube for full-length audio, so the
        // dialog is asked once: accepted -> full audio attempted, declined ->
        // the 30s preview plays quietly from then on without nagging again.
        val policyAccepted = usagePolicyStore.isYouTubePolicyAccepted
        // An offline copy plays its own bytes: no stream is resolved, so the
        // YouTube usage policy has nothing to disclose and must not gate it.
        val isOffline = offlineById.containsKey(song.id)
        val needsPolicy = when {
            isOffline -> false
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
        // Musik lokal: the URI is already the audio. No resolution, no network,
        // no policy dialog — it plays even in airplane mode.
        if (song.source == MusicSource.LOCAL) {
            player.playSong(song, queue, index)
            return
        }
        if (blockedByDataSaver(song)) {
            showDataSaverBlock(song)
            return
        }
        // Mode offline: the bytes are on disk, so nothing is resolved and the
        // whole queue is re-pointed at its local copies (order and indices are
        // untouched — a downloaded song simply plays from another URI).
        val localSong = offlineVersion(song)
        if (localSong != null) {
            player.playSong(localSong, queue.map { offlineVersion(it) ?: it }, index)
            return
        }
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
                        "Sumber sedang sibuk atau tidak tersedia — coba lagi nanti."
                )

                // No full audio found. A Deezer preview used to start playing
                // here, which is exactly the "why did my song become 30 s?"
                // bug. Ask first; silence is better than a surprise clip.
                song.isPreview -> {
                    player.endResolving()
                    _previewFallback.value = PreviewFallback(song, queue, index)
                }

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
        // Local files are their own source; asking the network would be wrong.
        if (song.source == MusicSource.LOCAL) return null
        streamResolver.resolve(song)?.let { return it }
        if (song.source == MusicSource.YOUTUBE && song.streamUrl.isBlank()) {
            retryOnce { runCatching { youtubeRepository.resolveAudio(song) }.getOrNull() }
                ?.let { return it }
        }
        if (song.source == MusicSource.DEEZER && song.isPreview &&
            usagePolicyStore.isYouTubePolicyAccepted
        ) {
            retryOnce { runCatching { youtubeRepository.findFullAudio(song) }.getOrNull() }
                ?.let { return it }
        }
        return null
    }

    /**
     * One quiet second attempt. Public instances answer "no match" and
     * "instance overloaded" the same way to the caller, and a single retry is
     * the difference between playing the song and offering a 30 s clip for a
     * hiccup that lasted 300 ms.
     */
    private suspend fun <T> retryOnce(block: suspend () -> T?): T? {
        block()?.let { return it }
        delay(RESOLVE_RETRY_DELAY_MS)
        return block()
    }

    /**
     * Fresh audio for a stream that failed mid-playback. The ladder mirrors
     * [resolvePlaybackSource] but forces a cache-evicting re-resolve for the
     * YouTube-backed sources, since the cached URL is exactly the one that
     * just died. Returns null when nothing better exists — the player then
     * retries the original URL before giving up.
     */
    private suspend fun refreshExpiredStream(song: Song): Song? {
        streamResolver.resolve(song)?.let { return it }
        when (song.source) {
            MusicSource.YOUTUBE ->
                retryOnce { runCatching { youtubeRepository.refreshAudio(song) }.getOrNull() }
                    ?.let { return it }

            MusicSource.DEEZER -> {
                // Full-length Deezer tracks carry their audio on a matched
                // YouTube video; a raw preview re-runs the matcher instead.
                if (song.isPreview && usagePolicyStore.isYouTubePolicyAccepted) {
                    retryOnce { runCatching { youtubeRepository.findFullAudio(song) }.getOrNull() }
                        ?.let { return it }
                } else {
                    retryOnce { runCatching { youtubeRepository.refreshAudio(song) }.getOrNull() }
                        ?.let { return it }
                }
            }

            MusicSource.AUDIUS, MusicSource.LOCAL -> Unit
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
                    // Offline rows already hold their bytes; warming them would
                    // replace the local file with a network URL.
                    .filter { !offlineById.containsKey(it.id) }
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
        // An offline copy plays its own bytes — queue it directly instead of
        // resolving a network stream it will never use.
        offlineVersion(song)?.let { local ->
            play(local)
            return
        }
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
        if (target != null && blockedByDataSaver(target)) {
            showDataSaverBlock(target)
            return
        }
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
            } else if (target != null && target.isPreview) {
                // Don't sneak a 30 s clip in through the skip button either.
                player.endResolving()
                _previewFallback.value = PreviewFallback(
                    song = target,
                    queue = player.uiState.value.queue,
                    index = targetIndex
                )
                return@launch
            } else {
                player.clearError()
            }
            plainSkip()
        }
    }

    /**
     * Full-audio guarantee for the **auto** transition — the case where a song
     * finishes and ExoPlayer advances the queue by itself. (The manual skip
     * button already resolves first via [skipTo]; this covers the native path.)
     *
     * A long album queue is only warmed a few tracks ahead, so once playback
     * moves past that window the next queued Deezer item is still its raw 30 s
     * preview — previously that preview simply played as if it were the song.
     * Now the transition is held: the short clip is paused before it can be
     * heard, the full stream is resolved, and only then does the track restart
     * from the top. When no better stream exists, the honest preview resumes.
     * A rolling warm keeps the next few tracks ready so this hold is rare.
     */
    private fun handleAutoAdvance(song: Song) {
        // Local files need nothing from the network — keep the warm window
        // rolling and let the next track start instantly.
        if (song.source == MusicSource.LOCAL) {
            warmQueueStreams(player.uiState.value.queue)
            return
        }
        if (blockedByDataSaver(song)) {
            player.pauseForResolve()
            showDataSaverBlock(song)
            return
        }
        val policyAccepted = usagePolicyStore.isYouTubePolicyAccepted
        val needsFullAudio = when {
            // Mode offline: the bytes are already local — never hold playback
            // to go look for a network stream.
            offlineById.containsKey(song.id) -> false
            song.source == MusicSource.DEEZER && song.isPreview -> policyAccepted
            song.source == MusicSource.YOUTUBE && song.streamUrl.isBlank() -> true
            else -> false
        }
        if (!needsFullAudio) {
            // Already full (warmed earlier) or needs no resolve: just keep the
            // warm window rolling ahead of playback.
            warmQueueStreams(player.uiState.value.queue)
            return
        }
        if (song.id == lastHeldForResolveId) return
        lastHeldForResolveId = song.id
        player.pauseForResolve()
        safeScope.launch {
            val resolved = resolvePlaybackSource(song)
            if (resolved != null) {
                player.swapCurrentStreamAndRestart(resolved)
            } else if (song.isPreview) {
                // No full audio: the hold stays and the dialog decides. The
                // 30 s clip never starts on its own.
                _previewFallback.value = PreviewFallback(
                    song = song,
                    queue = player.uiState.value.queue,
                    index = player.uiState.value.queueIndex.coerceAtLeast(0)
                )
            } else {
                player.resumeAfterHold()
            }
            warmQueueStreams(player.uiState.value.queue)
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

    /** Arms/disarms the "stop at the end of this song" sleep-timer mode. */
    fun setSleepTimerEndOfTrack(enabled: Boolean = true) {
        player.setSleepTimerEndOfTrack(enabled)
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

    /** Strength of the whole profile (0..1) — the Studio "Intensitas" slider. */
    fun setSoundIntensity(value: Float) {
        player.setSoundIntensity(value)
    }

    /** Loudness: the 3-band compressor that evens out quiet and loud tracks. */
    fun setSoundLoudness(enabled: Boolean) {
        player.setSoundLoudness(enabled)
    }

    /** Limiter: last line of defence against clipping. */
    fun setSoundLimiter(enabled: Boolean) {
        player.setSoundLimiter(enabled)
    }

    /** Re-tunes for the current output (also runs automatically on route change). */
    fun refreshSoundRoute() {
        player.refreshSoundRoute()
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
        if (url != null) return VideoUiState.Ready(url)
        // Direct stream refused (expired signature, dead instance, or a track
        // YouTube will not serve as a bare URL). Every MV-capable song knows
        // its video id, so hand the rest to the embedded player instead of
        // telling the user the MV does not exist.
        val videoId = mvVideoIdOf(song)
        return if (videoId.isNotBlank()) {
            VideoUiState.Embed(videoId)
        } else {
            VideoUiState.Unavailable("MV tidak tersedia untuk lagu ini.")
        }
    }

    /** YouTube video id for [song], from the match or the namespaced track id. */
    private fun mvVideoIdOf(song: Song): String {
        if (song.matchedVideoId.isNotBlank()) return song.matchedVideoId
        return if (song.source == MusicSource.YOUTUBE) {
            song.id.removePrefix("youtube:").takeIf { it.isNotBlank() } ?: ""
        } else {
            ""
        }
    }

    /** Re-resolves once after a mid-play stream error, then re-attaches. */
    private fun handleMvPlaybackError() {
        if (_videoUiState.value !is VideoUiState.Ready) return
        val surface = lastMvSurface ?: return
        val song = player.uiState.value.currentSong ?: return
        mvPlayer.release()
        if (mvAutoRetries >= 1) {
            val videoId = mvVideoIdOf(song)
            _videoUiState.value = if (videoId.isNotBlank()) {
                VideoUiState.Embed(videoId)
            } else {
                VideoUiState.Unavailable("MV gagal diputar. Coba lagi nanti.")
            }
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
            // A deleted local copy breaks its collaboration link; drop the
            // mapping so a stale code never resurfaces on a future playlist.
            if (sharedLinks.value.containsKey(id.toString())) {
                sharedPlaylistPrefs.edit()
                    .remove(linkKey(id))
                    .remove(baseKey(id))
                    .apply()
                _sharedLinks.update { links -> links - id.toString() }
            }
        }
    }

    fun playlistSongs(playlistId: Long): Flow<List<Song>> =
        dao.getPlaylistSongs(playlistId).map { list -> list.map { it.toSong() } }

    fun playlistSongIds(playlistId: Long): Flow<List<String>> = dao.getPlaylistSongIds(playlistId)

    fun addSongToPlaylist(playlistId: Long, song: Song, position: Int = Int.MAX_VALUE) {
        safeScope.launch {
            dao.insertPlaylistSong(PlaylistSongEntity.fromSong(playlistId, song, position))
            pushSharedPlaylist(playlistId)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        safeScope.launch {
            dao.deletePlaylistSong(playlistId, songId)
            pushSharedPlaylist(playlistId)
        }
    }

    // ------------------------------------------- collaborative playlists
    //
    // Playlists shared by 6-character code through the same keyless backend
    // document as accounts & rooms. Links (playlistId -> code + last synced
    // remote updatedAt) live in their own SharedPreferences file; the sync
    // rule in [SharedPlaylistSync] refuses to overwrite a friend's newer edit.
    //
    // NOTE: the state properties for this feature are declared further up,
    // *before* the init block — initializers run in declaration order and the
    // init block loads the links (the v1.15.7 NPE lesson).

    private fun linkKey(playlistId: Long) = "link_$playlistId"
    private fun baseKey(playlistId: Long) = "base_$playlistId"

    /**
     * Publishes a local playlist under a fresh (or existing) share code.
     * Result surfaces as [SharedPlaylistUi.Shared] with the code to hand out.
     */
    fun sharePlaylist(playlistId: Long) {
        val user = authManager.currentUser.value
        if (user == null) {
            _sharedPlaylistUi.value = SharedPlaylistUi.Error(
                "Masuk ke akun dulu — playlist kolaboratif terikat ke akun kamu."
            )
            return
        }
        val name = playlistSummaries.value
            .firstOrNull { it.playlistId == playlistId }?.name ?: "Playlist"
        val code = sharedCodeFor(playlistId) ?: SharedPlaylistCodes.newCode()
        _sharedPlaylistUi.value = SharedPlaylistUi.Publishing(name)
        safeScope.launch {
            val songs = runCatching { dao.getPlaylistSongs(playlistId).first() }
                .getOrNull().orEmpty().map { it.toSong() }
            val now = System.currentTimeMillis()
            val published = runCatching {
                backend.publishSharedPlaylist(
                    com.example.data.support.SharedPlaylist(
                        code = code,
                        name = name,
                        ownerUid = user.uid,
                        ownerName = user.displayName ?: user.email ?: "NOXA User",
                        createdAt = now,
                        updatedAt = now,
                        songs = songs.map { com.example.data.support.SharedPlaylistSong.fromSong(it) }
                    )
                )
            }.getOrNull()
            if (published == null) {
                _sharedPlaylistUi.value = SharedPlaylistUi.Error(
                    "Server tidak terjangkau. Periksa koneksi lalu coba lagi."
                )
                return@launch
            }
            rememberLink(playlistId, published.code, published.updatedAt)
            _sharedPlaylistUi.value = SharedPlaylistUi.Shared(published.code, published.name)
        }
    }

    /**
     * Looks up whatever the user pasted (bare code, link or share message).
     * On success the flow carries a preview card; on failure an honest reason.
     */
    fun previewSharedPlaylist(input: String) {
        val code = com.example.data.support.SharedPlaylistCodes.normalize(input)
        if (!com.example.data.support.SharedPlaylistCodes.isValid(code)) {
            _sharedPlaylistUi.value = SharedPlaylistUi.Error(
                "Kode tidak dikenali. Tempel kode 6 karakter dari temanmu."
            )
            return
        }
        _sharedPlaylistUi.value = SharedPlaylistUi.Checking
        safeScope.launch {
            val remote = runCatching { backend.readSharedPlaylist(code) }.getOrNull()
            if (remote == null) {
                _sharedPlaylistUi.value = SharedPlaylistUi.Error(
                    "Playlist \"$code\" tidak ditemukan atau server tidak terjangkau."
                )
            } else {
                _sharedPlaylistUi.value = SharedPlaylistUi.Preview(remote)
            }
        }
    }

    /** Clones the previewed playlist into the local library, then links it. */
    fun importSharedPlaylist() {
        val preview = (_sharedPlaylistUi.value as? SharedPlaylistUi.Preview)?.playlist ?: return
        _sharedPlaylistUi.value = SharedPlaylistUi.Importing
        safeScope.launch {
            val playlistId = runCatching {
                val id = dao.insertPlaylist(
                    PlaylistEntity(
                        name = preview.name,
                        description = "Kolaboratif • ${preview.code}"
                    )
                )
                preview.songs.forEachIndexed { index, song ->
                    dao.insertPlaylistSong(
                        PlaylistSongEntity.fromSong(id, song.toSong(), index)
                    )
                }
                id
            }.getOrNull()
            if (playlistId == null) {
                _sharedPlaylistUi.value = SharedPlaylistUi.Error(
                    "Gagal menyimpan playlist ke perangkat. Coba lagi."
                )
                return@launch
            }
            rememberLink(playlistId, preview.code, preview.updatedAt)
            _sharedPlaylistUi.value = SharedPlaylistUi.Imported(
                playlistId, preview.name, preview.songs.size
            )
        }
    }

    /** Pulls the remote list into the local playlist (manual sync button). */
    fun syncSharedPlaylist(playlistId: Long) {
        val code = sharedCodeFor(playlistId) ?: run {
            _sharedNotice.value = "Playlist ini belum terhubung ke tautan kolaboratif."
            return
        }
        safeScope.launch {
            val remote = runCatching { backend.readSharedPlaylist(code) }.getOrNull()
            if (remote == null) {
                _sharedNotice.value = "Playlist kolaboratif tidak bisa diambil. Coba lagi nanti."
                return@launch
            }
            val ok = runCatching {
                dao.deletePlaylistSongs(playlistId)
                remote.songs.forEachIndexed { index, song ->
                    dao.insertPlaylistSong(
                        PlaylistSongEntity.fromSong(playlistId, song.toSong(), index)
                    )
                }
            }.isSuccess
            if (!ok) {
                _sharedNotice.value = "Gagal menyimpan hasil sinkron. Coba lagi."
                return@launch
            }
            rememberLink(playlistId, code, remote.updatedAt)
            _sharedNotice.value =
                "Tersinkron • ${remote.songs.size} lagu dari ${remote.ownerName}"
        }
    }

    /**
     * Best-effort push after a local add/remove on a linked playlist. The
     * remote is only overwritten while nobody else wrote since our last sync;
     * otherwise a "pull to sync" notice appears instead of clobbering the
     * friend's edit (see [SharedPlaylistSync]).
     */
    private fun pushSharedPlaylist(playlistId: Long) {
        val code = sharedCodeFor(playlistId) ?: return
        safeScope.launch {
            val base = sharedPlaylistPrefs.getLong(baseKey(playlistId), 0L)
            val remote = runCatching { backend.readSharedPlaylist(code) }.getOrNull()
                ?: return@launch // offline: local edit stands; next sync reconciles
            if (!com.example.data.support.SharedPlaylistSync.canPush(base, remote.updatedAt)) {
                _sharedNotice.value =
                    "Ada perubahan baru dari teman di playlist ini — buka lalu tekan Sinkron."
                return@launch
            }
            val local = runCatching { dao.getPlaylistSongs(playlistId).first() }
                .getOrNull() ?: return@launch
            val published = runCatching {
                backend.publishSharedPlaylist(
                    remote.copy(
                        songs = local.map {
                            com.example.data.support.SharedPlaylistSong.fromSong(it.toSong())
                        }
                    )
                )
            }.getOrNull() ?: return@launch
            rememberLink(playlistId, code, published.updatedAt)
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
            // Enrichment is parallel and fails soft: a dead related-artist or
            // Wikipedia endpoint only loses its own section, never the page.
            val related = async {
                runCatching { repository.relatedArtists(artistId, 12) }
                    .getOrDefault(emptyList())
            }
            val bio = async {
                runCatching { app.artistBioRepository.bioFor(profile.name) }.getOrNull()
            }
            val insights = async {
                runCatching { app.artistInsightsScraper.insightsFor(profile.name) }
                    .getOrNull()
            }
            _artistUiState.value = ArtistUiState.Ready(
                profile = profile,
                topSongs = topSongs,
                albums = albums,
                relatedArtists = related.await().filter { it.id != artistId },
                bio = bio.await(),
                insights = insights.await()?.takeUnless { it.isEmpty }
            )
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
        ambiencePlayer.release()
        // Lepas handler suara: setelah UI mati, NOXA Wake jatuh ke engine
        // lokal (WakeCommandEngine) — perintah tetap jalan saat app ditutup.
        if (com.example.voice.NoxaWakeBus.handler === voiceCommandHandler) {
            com.example.voice.NoxaWakeBus.handler = null
        }
        super.onCleared()
    }

    // ------------------------------------------------- NOXA Support & Admin

    /** Keyless shared backend: accounts, roles, support threads. */
    private val backend = com.example.data.support.NoxaBackend()

    // ------------------------------------------------------ per-song comments

    /** Keyless shared comment threads — one Telegraph document, keyed by song id. */
    private val commentRepo = com.example.data.support.SongCommentsRepository()

    /** Everything the comment sheet renders; `null` = sheet closed. */
    data class SongCommentsUi(
        val song: Song,
        val comments: List<com.example.data.support.SongComment> = emptyList(),
        val loading: Boolean = true,
        val posting: Boolean = false,
        /** Last failure message (post stayed in the composer). */
        val error: String? = null
    )

    private val _songComments = MutableStateFlow<SongCommentsUi?>(null)
    val songComments: StateFlow<SongCommentsUi?> = _songComments.asStateFlow()

    /** Opens the comment sheet for [song] and starts fetching its thread. */
    fun openSongComments(song: Song) {
        _songComments.value = SongCommentsUi(song)
        safeScope.launch {
            val list = runCatching { commentRepo.commentsFor(song.id) }
                .getOrDefault(emptyList())
            _songComments.update { ui ->
                if (ui?.song?.id == song.id) ui.copy(comments = list, loading = false) else ui
            }
        }
    }

    fun closeSongComments() {
        _songComments.value = null
    }

    /**
     * Posts [text] as the signed-in user. `onDone` receives `null` on success
     * or a user-facing Indonesian refusal; on success the sheet refreshes
     * straight from the repository cache, so the new comment appears without
     * a second round-trip.
     */
    fun postSongComment(text: String, onDone: ((String?) -> Unit)? = null) {
        val ui = _songComments.value ?: return
        val user = authManager.currentUser.value
        if (user == null) {
            onDone?.invoke("Masuk dulu untuk berkomentar.")
            return
        }
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Pendengar"
        safeScope.launch {
            _songComments.update { it?.copy(posting = true, error = null) }
            val err = runCatching {
                commentRepo.post(ui.song.id, user.uid, name, text)
            }.getOrDefault(com.example.data.support.SongCommentsRepository.UNREACHABLE)
            if (err == null) {
                val list = runCatching { commentRepo.commentsFor(ui.song.id) }
                    .getOrDefault(ui.comments)
                _songComments.update { it?.copy(comments = list, posting = false, error = null) }
                onDone?.invoke(null)
            } else {
                val friendly = if (
                    err == com.example.data.support.SongCommentsRepository.UNREACHABLE
                ) "Server komentar tidak bisa dijangkau. Coba lagi nanti." else err
                _songComments.update { it?.copy(posting = false, error = friendly) }
                onDone?.invoke(friendly)
            }
        }
    }

    // ---------------------------------------------------- Kirim Lagu (3.3.3)

    /** Kiriman lagu masuk untuk akun yang sedang masuk — terbaru dulu. */
    private val _gifts =
        MutableStateFlow<List<com.example.data.support.SongGift>>(emptyList())
    val gifts: StateFlow<List<com.example.data.support.SongGift>> =
        _gifts.asStateFlow()

    /** Refresh inbox — dipanggil tiap Profil dibuka; diam saat belum masuk. */
    fun refreshGifts() {
        val uid = authManager.currentUser.value?.uid ?: return
        safeScope.launch {
            val list = runCatching { backend.giftsFor(uid) }.getOrNull() ?: return@launch
            _gifts.value = list
        }
    }

    /**
     * Mengirim lagu yang sedang dibagikan ke username teman.
     * `onDone(null)` = sukses; selain itu pesan error berbahasa Indonesia.
     */
    fun sendGift(song: Song, toUsername: String, message: String, onDone: (String?) -> Unit) {
        val user = authManager.currentUser.value
        if (user == null) {
            onDone("Masuk dulu untuk mengirim lagu.")
            return
        }
        val name = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore('@')
            ?: "Pendengar"
        safeScope.launch {
            val err = runCatching {
                backend.sendGift(
                    toUsername = toUsername,
                    fromName = name,
                    songId = song.id,
                    title = song.title,
                    artist = song.artist,
                    artwork = song.artworkUrl,
                    message = message
                )
            }.getOrDefault("Server NOXA tidak bisa dijangkau. Coba lagi nanti.")
            onDone(err)
        }
    }

    /**
     * Memutar kiriman dari Profil — id lagu membawa prefix provider, jadi
     * resolusi stream sama persis seperti lagu biasa (tanpa hardcode sumber).
     */
    fun playGift(gift: com.example.data.support.SongGift) {
        if (gift.songId.isBlank()) return
        val song = Song(
            id = gift.songId,
            title = gift.title,
            artist = gift.artist,
            artworkUrl = gift.artwork
        ).withDerivedProviderFields()
        playSong(song)
    }

    /** Optimistik: centang lime hilang langsung; tulis ke backend di belakang. */
    fun markGiftRead(giftId: String) {
        val uid = authManager.currentUser.value?.uid ?: return
        _gifts.update { list ->
            list.map { if (it.id == giftId) it.copy(read = true) else it }
        }
        safeScope.launch {
            runCatching { backend.markGiftRead(uid, giftId) }
        }
    }

    // ------------------------------------------------------------- updates

    /** Latest update published by the developer, when this build is older. */
    private val _updateInfo =
        MutableStateFlow<com.example.data.support.NoxaBackend.UpdateInfo?>(null)
    val updateInfo: StateFlow<com.example.data.support.NoxaBackend.UpdateInfo?> =
        _updateInfo.asStateFlow()

    // The developer never sees the forced-update popup: Sky publishes updates
    // from this very account, and a modal with no dismissal would lock her out
    // of the console that pushes them.
    //
    // This collector MUST live below the [_updateInfo] declaration: viewModelScope
    // runs on Dispatchers.Main.immediate, so a launch from [init] emits the
    // flow's current value *synchronously* during construction. When the
    // restored session already belongs to a DEVELOPER, the old init-placed
    // collector dereferenced _updateInfo before its initializer had run —
    // a null backing field and the "set value on a null object" force close.
    init {
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                if (user?.role == com.example.data.support.AccountRole.DEVELOPER) {
                    _updateInfo.value = null
                }
            }
        }
        // Presence heartbeat: while a user is signed in, mark this device
        // online (throttled inside repoState — a sparkle no more often than
        // every 60s). Signed-out dev panel keeps stats visible without an
        // identity — heartbeat from there is skipped to avoid ghost "online"
        // listeners with no name.
        viewModelScope.launch {
            authManager.currentUser.collect { user ->
                if (user != null) {
                    backend.heartbeat(user.uid)
                }
            }
        }
    }

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
        val isDeveloper = authManager.currentUser.value?.role ==
            com.example.data.support.AccountRole.DEVELOPER
        if (isDeveloper) return
        safeScope.launch {
            val info = runCatching { backend.readState()?.update }.getOrNull()
            val current = com.example.BuildConfig.VERSION_CODE.toLong()
            // Stale-state hygiene: an update this build no longer needs
            // (already installed, or published at/below our versionCode) is
            // dropped instead of lingering as an "available update" card.
            if (info == null || !com.example.data.remote.UpdateGate.isNewer(current, info.versionCode)) {
                _updateInfo.value = null
                _updateDialogVisible.value = false
                return@launch
            }
            val skipped = updatePrefs().getLong(KEY_SKIPPED_UPDATE, -1L)
            val show = com.example.data.remote.UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = info.versionCode,
                isDeveloper = isDeveloper,
                skippedVersionCode = skipped,
                mandatory = info.mandatory
            )
            _updateInfo.value = info
            if (show) {
                _updatePhase.value = UpdatePhase.Idle
                _updateError.value = null
                _updateDialogVisible.value = true
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

    /**
     * Reopens the popup from Pengaturan → Pembaruan — but only while the
     * published version is genuinely newer than this build. A stale entry
     * (user already on the latest version) is cleared instead of shown.
     */
    fun openUpdateFromSettings() {
        val info = _updateInfo.value
        if (info == null ||
            !com.example.data.remote.UpdateGate.isNewer(
                com.example.BuildConfig.VERSION_CODE.toLong(), info.versionCode
            )
        ) {
            _updateInfo.value = null
            _updateDialogVisible.value = false
            return
        }
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
        // Android 8+: install-from-this-source must be granted for OUR package
        // or the system installer refuses to open. Ask FIRST so the user is
        // never sent through a full download just to hit the settings wall.
        val app = getApplication<Application>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            !app.packageManager.canRequestPackageInstalls()
        ) {
            runCatching {
                app.startActivity(
                    android.content.Intent(
                        android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        android.net.Uri.parse("package:${app.packageName}")
                    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
            _updatePhase.value = UpdatePhase.Failed
            _updateError.value =
                "Izinkan \"Install dari sumber ini\" untuk NOXA, lalu ketuk " +
                "Update Sekarang lagi — panel instalasi Android akan terbuka otomatis."
            return
        }
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
                        .setMimeType(APK_MIME_TYPE)
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
                        // Security: never hand an unverified file to the
                        // system installer — the archive must be signed
                        // with the same key as the installed app.
                        val refusal = verifyUpdateSignature(app, target)
                        if (refusal != null) {
                            _updatePhase.value = UpdatePhase.Failed
                            _updateError.value = refusal
                        } else {
                            _updatePhase.value = UpdatePhase.ReadyToInstall
                            installApk(app, target)
                        }
                    } else {
                        _updatePhase.value = UpdatePhase.Failed
                        _updateError.value = "Unduhan update gagal. Coba lagi."
                    }
                    return@launch
                }
            }
        }
    }

    /**
     * Security: a pushed update must be signed with the *same* key as the
     * installed NOXA. A tampered file (swapped on the host, hostile proxy,
     * wrong link) is then refused honestly here instead of being handed to
     * the system installer. Returns null when the file is safe, or a
     * user-facing reason to refuse.
     */
    private fun verifyUpdateSignature(app: Application, file: java.io.File): String? {
        return try {
            val pm = app.packageManager
            @Suppress("DEPRECATION")
            val archiveFlags = if (android.os.Build.VERSION.SDK_INT >= 28) {
                android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                android.content.pm.PackageManager.GET_SIGNATURES
            }
            val archive = pm.getPackageArchiveInfo(file.absolutePath, archiveFlags)
                ?: return "File update tidak bisa dibaca sebagai APK — unduh ulang."

            fun signaturesOf(info: android.content.pm.PackageInfo): Set<String> =
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    info.signingInfo?.apkContentsSigners.orEmpty()
                        .filterNotNull().map { it.toCharsString() }.toSet()
                } else {
                    @Suppress("DEPRECATION")
                    info.signatures.orEmpty()
                        .filterNotNull().map { it.toCharsString() }.toSet()
                }

            val archiveSigners = signaturesOf(archive)
            @Suppress("DEPRECATION")
            val ownSigners = signaturesOf(
                pm.getPackageInfo(app.packageName, archiveFlags)
            )
            if (archiveSigners.isEmpty() ||
                ownSigners.isEmpty() ||
                archiveSigners.intersect(ownSigners).isEmpty()
            ) {
                "Update ditolak: tanda tangan APK tidak sama dengan NOXA yang " +
                    "terpasang. Unduh hanya dari sumber resmi."
            } else {
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update signature check failed: ${e.message}")
            "Gagal memverifikasi update: ${e.message}"
        }
    }

    /** Opens the official Android installer for the downloaded APK. */
    private fun installApk(app: Application, file: java.io.File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                app, "${app.packageName}.fileprovider", file
            )
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                // The archive MIME (not the obsolete "application/vnd.android"
                // guess) is what the system package installer matches on —
                // with the wrong type Android offered only file-handling apps
                // (Termux, ShareIt, WPS) instead of "Pasang".
                setDataAndType(uri, APK_MIME_TYPE)
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
            if (versionCode <= 0L) {
                onResult("Version code harus angka lebih dari 0.")
                return@launch
            }
            // The baseline is the update already PUBLISHED on the server, never
            // the build installed on this phone (see UpdateGate.publishAllowed).
            // Equal is a legal republish — Sky re-pushes the same versionCode
            // whenever the APK URL/note changes; only a strictly lower code is
            // a downgrade and gets refused.
            val published = runCatching { backend.readState()?.update?.versionCode }
                .getOrNull()
            if (!com.example.data.remote.UpdateGate.publishAllowed(versionCode, published)) {
                onResult(
                    "Version code $versionCode lebih kecil dari update yang sudah " +
                        "dipublish ($published) — itu menurunkan versi. Isi minimal " +
                        "$published (republish) atau lebih besar; yang ditawari update " +
                        "adalah user di bawahnya."
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

    /** One-skip-at-a-time guard for the vote executor (see [RoomVotes]). */
    @Volatile
    private var skipVoteInProgress = false

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

    /** Recently played ids in this room — keeps auto-radio from repeating. */
    private val roomRadioRecent = mutableListOf<String>()

    /**
     * While `now < roomMirrorMutedUntil`, members do not publish their own
     * auto-advance: the room itself is choosing the next song, and letting
     * every phone publish its private queue would make the room jump around.
     */
    @Volatile
    private var roomMirrorMutedUntil = 0L

    /** One queue/radio advance at a time. */
    @Volatile
    private var roomAdvancing = false

    /** Throttles the end-of-song hook so a failed lookup cannot spam search. */
    @Volatile
    private var lastRoomAdvanceAttemptAt = 0L

    private var roomAiJob: kotlinx.coroutines.Job? = null

    private fun roomDisplayName(user: com.example.data.auth.NoxaUser): String =
        user.displayName ?: user.email ?: "user"

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
        roomAiJob?.cancel()
        roomAiJob = null
        roomCode = null
        applyingRemote = false
        roomMirrorMutedUntil = 0L
        roomAdvancing = false
        roomRadioRecent.clear()
        roomPendingChat.clear()
        roomSearchJob?.cancel()
        roomSearchJob = null
        _roomSearch.value = com.example.ui.screens.RoomSearchUi()
        _roomState.value = com.example.ui.screens.RoomUiState.Idle
        val user = authManager.currentUser.value
        if (user != null) {
            safeScope.launch { runCatching { backend.leaveRoom(code, user.uid) } }
        }
    }

    // ------------------------------------- room chat, NOXA DJ, admin controls

    /**
     * Chat lines that are on screen but not yet confirmed by the server.
     * The backend document is one shared page and every write is a full
     * read-modify-write, so a send can take a second or two. Waiting for that
     * round-trip before drawing the bubble is what made the room feel laggy;
     * the bubble now appears immediately and is reconciled when the echo comes
     * back (a failed send is marked instead of vanishing).
     */
    private val roomPendingChat =
        mutableListOf<com.example.ui.screens.RoomChatUi>()

    private var roomPendingSeq = 0

    /**
     * Serializes room writes. Telegraph accepts roughly one page edit per
     * second and v3.6.3 fired chat, AI line and song change back to back — the
     * later writes were dropped, which is exactly the "chat telat" and "tidak
     * bisa ganti lagu" report. Writes now leave one at a time, with a single
     * retry when the page refuses the first attempt.
     */
    private val roomWriteMutex = Mutex()
    private var lastRoomWriteAt = 0L

    private suspend fun <T> roomWrite(block: suspend () -> T?): T? =
        roomWriteMutex.withLock {
            val wait = ROOM_WRITE_GAP_MS - (System.currentTimeMillis() - lastRoomWriteAt)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            var result = attemptRoomWrite(block)
            if (result == null) {
                kotlinx.coroutines.delay(700)
                result = attemptRoomWrite(block)
            }
            lastRoomWriteAt = System.currentTimeMillis()
            result
        }

    private suspend fun <T> attemptRoomWrite(block: suspend () -> T?): T? = try {
        block()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /**
     * Sends one chat line. A message that asks for a song (`@noxa puterin …`)
     * also plays/queues it; every other mention is answered as conversation.
     */
    fun sendRoomMessage(text: String) {
        val code = roomCode ?: return
        val user = authManager.currentUser.value ?: return
        val clean = text.trim()
            .take(com.example.data.support.NoxaBackend.RoomChatMessage.MAX_CHAT_CHARS)
        if (clean.isBlank()) return
        // Optimistic bubble: drawn before the network is touched.
        val pending = com.example.ui.screens.RoomChatUi(
            id = "p${System.currentTimeMillis()}-${++roomPendingSeq}",
            name = roomDisplayName(user),
            text = clean,
            isSelf = true,
            at = System.currentTimeMillis(),
            pending = true
        )
        roomPendingChat.add(pending)
        pushPendingChat()
        val command = com.example.data.support.RoomAi.command(clean)
        safeScope.launch {
            val published = publishRoomChat(code, user, clean)
            if (published != null) {
                roomPendingChat.removeAll { it.id == pending.id }
                applyRoomUi(published, user.uid)
            } else {
                roomPendingChat.replaceAll {
                    if (it.id == pending.id) it.copy(pending = false, failed = true) else it
                }
                pushPendingChat()
            }
            if (command != null) handleRoomMention(code, user, command)
        }
    }

    /** Re-sends a message the backend refused, dropping its failed bubble. */
    fun resendRoomMessage(text: String) {
        roomPendingChat.removeAll { it.failed && it.text == text }
        pushPendingChat()
        sendRoomMessage(text)
    }

    /** Redraws the conversation with the local (unconfirmed) bubbles appended. */
    private fun pushPendingChat() {
        val current = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom ?: return
        val confirmed = current.chat
            .filter { it.isSelf && !it.pending }.map { it.text }.toSet()
        val waiting = roomPendingChat.filter { it.text !in confirmed }
        _roomState.value = current.copy(chat = (current.chat + waiting).distinctBy { it.id })
    }

    /**
     * Server truth + this device's unconfirmed bubbles. A pending line is
     * dropped once the same text is back from the server, so the optimistic
     * copy never renders twice.
     */
    private fun mergePendingChat(
        server: List<com.example.ui.screens.RoomChatUi>
    ): List<com.example.ui.screens.RoomChatUi> {
        val confirmed = server.filter { it.isSelf }.map { it.text }.toSet()
        val waiting = roomPendingChat.filter { it.text !in confirmed }
        return (server + waiting).sortedBy { it.at }
    }

    /** Applies a server room document to the UI, keeping local echoes. */
    private fun applyRoomUi(
        room: com.example.data.support.NoxaBackend.RoomState,
        selfUid: String
    ) {
        val current = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom
        val ui = room.toUiState(
            code = room.code,
            selfUid = selfUid,
            aiThinking = current?.aiThinking ?: false
        )
        _roomState.value = ui.copy(
            positionMs = estimatedRoomPosition(room),
            chat = mergePendingChat(ui.chat)
        )
    }

    /**
     * Chat publish. It carries the sender's live position whenever they are on
     * the room's current song: a chat line bumps `updatedAt`, and everyone else
     * derives the timeline from it, so a stale position would make the whole
     * room seek backwards just because somebody said "mantap".
     */
    private suspend fun publishRoomChat(
        code: String,
        user: com.example.data.auth.NoxaUser,
        text: String,
        fromAi: Boolean = false
    ): com.example.data.support.NoxaBackend.RoomState? {
        val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom
        val song = playerState.value.currentSong
        val progress = playbackProgress.value
        val onRoomSong = room != null && song != null && song.id == room.songId
        val published = roomWrite {
            runCatching {
                backend.sendRoomChat(
                    code = code,
                    uid = user.uid,
                    name = if (fromAi) "NOXA" else roomDisplayName(user),
                    text = text,
                    fromAi = fromAi,
                    positionMs = if (onRoomSong) progress.positionMs else null,
                    playing = if (onRoomSong) playerState.value.isPlaying else null,
                    durationMs = if (onRoomSong) progress.durationMs.takeIf { it > 0 } else null
                )
            }.getOrNull()
        }
        published?.let { lastRemoteAt = it.updatedAt }
        return published
    }

    /** One chat id — unique even for two lines posted in the same millisecond. */
    private fun newRoomChatId(): String =
        "m${System.currentTimeMillis()}-${(1000..9999).random()}"

    /**
     * Executes an `@noxa` mention.
     *
     * The distinction that used to be missing: only an actual song request
     * touches the player. `@noxa halo`, `@noxa lagu ini enak ya`, or a real
     * question (`@noxa lagu apa yang cocok buat kerja?`) get an answer in the
     * chat and nothing else — v3.6.3 searched the catalogue for every single
     * mention and hijacked the room on small talk.
     */
    private fun handleRoomMention(
        code: String,
        user: com.example.data.auth.NoxaUser,
        command: com.example.data.support.RoomAi.Command
    ) {
        val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom ?: return
        if (!room.aiEnabled) {
            if (command.intent == com.example.data.support.RoomAi.Intent.PLAY) {
                safeScope.launch {
                    publishRoomChat(
                        code, user,
                        "Mode NOXA sedang dimatikan admin room, jadi aku cuma bisa ikut dengar.",
                        fromAi = true
                    )
                }
            }
            return
        }
        val ai = com.example.data.support.RoomAi
        if (command.intent == com.example.data.support.RoomAi.Intent.CHAT) {
            answerRoomChat(code, user, command, room)
            return
        }
        if (command.advance) {
            // "@noxa ganti lagu" — no title, just move on.
            roomAiJob?.cancel()
            roomAiJob = safeScope.launch {
                setRoomAiThinking(true)
                try {
                    val next = pickRoomRadioSong(room.songArtist, room.songId)
                    if (next == null) {
                        publishRoomChat(
                            code, user,
                            "Belum ada lagu lain yang pas setelah ini. Sebut judulnya, nanti kuputar.",
                            fromAi = true
                        )
                    } else {
                        playRoomSongNow(
                            next, user,
                            line = ai.playLine(
                                next.title, next.artist,
                                com.example.data.support.RoomAi.Mode.NOW
                            )
                        )
                    }
                } finally {
                    setRoomAiThinking(false)
                }
            }
            return
        }
        if (command.query.isBlank()) {
            safeScope.launch {
                publishRoomChat(
                    code, user,
                    "Mau lagu apa? Ketik misalnya: @noxa puterin lagu Kangen Dewa 19",
                    fromAi = true
                )
            }
            return
        }
        roomAiJob?.cancel()
        roomAiJob = safeScope.launch {
            setRoomAiThinking(true)
            try {
                val outcome = searchCatalogue(command.query)
                val pick = outcome.songs.firstOrNull { it.title.isNotBlank() }
                when {
                    pick != null -> {
                        val line = ai.playLine(pick.title, pick.artist, command.mode)
                        if (command.mode == com.example.data.support.RoomAi.Mode.NEXT) {
                            enqueueRoomSong(pick, user, line)
                        } else {
                            playRoomSongNow(pick, user, line)
                        }
                    }
                    // A network failure is not "the song does not exist".
                    outcome.failed -> publishRoomChat(
                        code, user,
                        "Katalog NOXA belum bisa kuhubungi. Coba lagi sebentar, ya.",
                        fromAi = true
                    )
                    else -> publishRoomChat(
                        code, user,
                        "Aku belum nemu \"${command.query}\" di katalog. Coba judul atau nama artis lain, ya.",
                        fromAi = true
                    )
                }
            } finally {
                setRoomAiThinking(false)
            }
        }
    }

    /** Catalogue search with one retry; distinguishes empty from unreachable. */
    private data class RoomSearchOutcome(val songs: List<Song>, val failed: Boolean)

    private suspend fun searchCatalogue(query: String): RoomSearchOutcome {
        repeat(2) { attempt ->
            val result = runCatching { repository.search(query) }
            if (result.isSuccess) {
                return RoomSearchOutcome(result.getOrDefault(emptyList()), failed = false)
            }
            if (attempt == 0) kotlinx.coroutines.delay(350)
        }
        return RoomSearchOutcome(emptyList(), failed = true)
    }

    /**
     * Conversational answer. The model writes it on the fast room client; if it
     * is down or slow, [com.example.data.support.RoomAi.casualReply] answers
     * instantly so a member is never left talking to a spinner.
     */
    private fun answerRoomChat(
        code: String,
        user: com.example.data.auth.NoxaUser,
        command: com.example.data.support.RoomAi.Command,
        room: com.example.ui.screens.RoomUiState.InRoom
    ) {
        // A bare "@noxa" is still a greeting, not silence.
        val question = command.text.ifBlank { "hai" }
        roomAiJob?.cancel()
        roomAiJob = safeScope.launch {
            setRoomAiThinking(true)
            try {
                val nowPlaying = room.songTitle.takeIf { it.isNotBlank() }?.let { title ->
                    if (room.songArtist.isBlank()) title else "$title — ${room.songArtist}"
                }
                val prompt = buildString {
                    append("Anggota bernama ").append(roomDisplayName(user))
                    append(" menulis di room: \"").append(question.take(200)).append("\".\n")
                    if (nowPlaying != null) {
                        append("Lagu room saat ini: ").append(nowPlaying).append(". ")
                    } else {
                        append("Room belum memutar lagu apa pun. ")
                    }
                    append("Pendengar: ").append(room.members.size)
                    append(". Jawab sebagai NOXA dan jangan mengubah lagu.")
                }
                // Six seconds is the whole patience budget of a chat room. If
                // the model is slower, the instant fallback answers instead.
                val reply = kotlinx.coroutines.withTimeoutOrNull(6_000L) {
                    aiRepository.roomReply(prompt)
                }?.takeIf { it.length in 3..240 }
                    ?: com.example.data.support.RoomAi.casualReply(question, nowPlaying)
                publishRoomChat(code, user, reply, fromAi = true)
            } finally {
                setRoomAiThinking(false)
            }
        }
    }

    private fun setRoomAiThinking(thinking: Boolean) {
        val current = _roomState.value
        if (current is com.example.ui.screens.RoomUiState.InRoom) {
            _roomState.value = current.copy(aiThinking = thinking)
        }
    }

    /**
     * Puts [song] on in the room right now and plays it here too.
     *
     * The optional [line] rides in the *same* page write as the song change.
     * v3.6.3 published the chat line first and the song second: two edits in a
     * row, and when the provider dropped the second one the song silently
     * stayed put (the "sudah disuruh ganti tapi tidak mau" bug).
     */
    private suspend fun playRoomSongNow(
        song: Song,
        user: com.example.data.auth.NoxaUser,
        line: String? = null
    ): Boolean {
        val code = roomCode ?: return false
        // An explicit pick overrides every leftover auto-radio guard.
        roomMirrorMutedUntil = 0L
        lastRoomAdvanceAttemptAt = 0L
        val published = roomWrite {
            runCatching {
                backend.publishRoom(code, user.uid, roomDisplayName(user)) { room ->
                    val withSong = room.copy(
                        songId = song.id,
                        songTitle = song.title,
                        songArtist = song.artist,
                        songArtwork = song.artworkUrl,
                        playing = true,
                        positionMs = 0L,
                        durationMs = song.durationMs,
                        skipVotes = emptyList(),
                        queue = room.queue.filterNot { it.songId == song.id }
                    )
                    if (line == null) withSong else withSong.copy(
                        chat = (withSong.chat + com.example.data.support.NoxaBackend.RoomChatMessage(
                            id = newRoomChatId(),
                            uid = user.uid,
                            name = "NOXA",
                            text = line,
                            at = System.currentTimeMillis(),
                            fromAi = true
                        )).takeLast(com.example.data.support.NoxaBackend.RoomState.MAX_CHAT)
                    )
                }
            }.getOrNull()
        } ?: return false
        lastRemoteAt = published.updatedAt
        roomRadioRecent.clear()
        roomRadioRecent.add(song.id)
        applyingRemote = true
        try {
            playSong(song)
            applyRoomUi(published, user.uid)
            kotlinx.coroutines.delay(400)
        } finally {
            applyingRemote = false
        }
        return true
    }

    /** Queues [song] behind the current track — or starts it when idle. */
    private fun enqueueRoomSong(
        song: Song,
        user: com.example.data.auth.NoxaUser,
        line: String? = null
    ) {
        val code = roomCode ?: return
        safeScope.launch {
            val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom
            // Nothing playing: "after this song" means right now.
            if (room != null && room.songId.isBlank()) {
                playRoomSongNow(song, user, line)
                return@launch
            }
            val published = roomWrite {
                runCatching {
                    backend.publishRoom(code, user.uid, roomDisplayName(user)) { state ->
                        val item = com.example.data.support.NoxaBackend.RoomQueueItem(
                            songId = song.id,
                            title = song.title,
                            artist = song.artist,
                            artwork = song.artworkUrl,
                            addedBy = "NOXA"
                        )
                        val queued = state.copy(
                            queue = (state.queue.filterNot { it.songId == song.id } + item)
                                .takeLast(com.example.data.support.NoxaBackend.RoomState.MAX_QUEUE)
                        )
                        if (line == null) queued else queued.copy(
                            chat = (queued.chat + com.example.data.support.NoxaBackend.RoomChatMessage(
                                id = newRoomChatId(),
                                uid = user.uid,
                                name = "NOXA",
                                text = line,
                                at = System.currentTimeMillis(),
                                fromAi = true
                            )).takeLast(com.example.data.support.NoxaBackend.RoomState.MAX_CHAT)
                        )
                    }
                }.getOrNull()
            } ?: return@launch
            lastRemoteAt = published.updatedAt
            applyRoomUi(published, user.uid)
        }
    }

    // --------------------------------------------- admin: search & queue picks

    /** Local search panel used by the room admin to pick a song for everyone. */
    private val _roomSearch =
        MutableStateFlow(com.example.ui.screens.RoomSearchUi())
    val roomSearch: StateFlow<com.example.ui.screens.RoomSearchUi> =
        _roomSearch.asStateFlow()

    private var roomSearchJob: kotlinx.coroutines.Job? = null

    /** Debounced catalogue search for the room's "putar untuk semua" panel. */
    fun searchRoomSongs(query: String) {
        roomSearchJob?.cancel()
        val clean = query.trim()
        if (clean.isBlank()) {
            _roomSearch.value = com.example.ui.screens.RoomSearchUi()
            return
        }
        _roomSearch.value = _roomSearch.value.copy(query = query, loading = true, error = null)
        roomSearchJob = safeScope.launch {
            kotlinx.coroutines.delay(350)
            val outcome = searchCatalogue(clean)
            _roomSearch.value = com.example.ui.screens.RoomSearchUi(
                query = query,
                results = outcome.songs.filter { it.title.isNotBlank() }.take(12),
                loading = false,
                error = when {
                    outcome.failed -> "Katalog tidak terjangkau. Periksa koneksi."
                    outcome.songs.isEmpty() -> "Tidak ada hasil untuk \"$clean\"."
                    else -> null
                }
            )
        }
    }

    fun clearRoomSearch() {
        roomSearchJob?.cancel()
        _roomSearch.value = com.example.ui.screens.RoomSearchUi()
    }

    /** Admin: play a searched song for the whole room, right now. */
    fun playRoomSearchResult(song: Song) {
        val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom ?: return
        val user = authManager.currentUser.value ?: return
        if (!room.isHost) return
        _roomSearch.value = com.example.ui.screens.RoomSearchUi()
        safeScope.launch {
            playRoomSongNow(
                song, user,
                line = com.example.data.support.RoomAi.playLine(
                    song.title, song.artist, com.example.data.support.RoomAi.Mode.NOW
                )
            )
        }
    }

    /** Admin: queue a searched song behind the current one. */
    fun queueRoomSearchResult(song: Song) {
        val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom ?: return
        val user = authManager.currentUser.value ?: return
        if (!room.isHost) return
        _roomSearch.value = com.example.ui.screens.RoomSearchUi()
        enqueueRoomSong(
            song, user,
            line = com.example.data.support.RoomAi.playLine(
                song.title, song.artist, com.example.data.support.RoomAi.Mode.NEXT
            )
        )
    }

    /** Admin: promote a song from the queue to the room right now. */
    fun playQueuedSong(songId: String) {
        val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom ?: return
        val user = authManager.currentUser.value ?: return
        if (!room.isHost) return
        val item = room.queue.firstOrNull { it.songId == songId } ?: return
        safeScope.launch {
            val song = resolveSongId(item.songId)
            if (song == null) {
                publishRoomChat(
                    room.code, user,
                    "Lagu \"${item.title}\" tidak bisa dibuka dari katalog. Coba lagu lain, ya.",
                    fromAi = true
                )
                return@launch
            }
            playRoomSongNow(song, user)
        }
    }

    /** Admin: drop one queued song (the whole queue can still be cleared). */
    fun removeQueuedSong(songId: String) = publishRoomControls { state ->
        state.copy(queue = state.queue.filterNot { it.songId == songId })
    }

    /**
     * Room-wide switches. Only the host may flip them — the check runs here so
     * a modified client cannot change the room either.
     */
    fun setRoomAiEnabled(enabled: Boolean) =
        publishRoomControls { it.copy(aiEnabled = enabled) }

    fun setRoomAutoRadio(enabled: Boolean) =
        publishRoomControls { it.copy(autoRadio = enabled) }

    fun clearRoomQueue() = publishRoomControls { it.copy(queue = emptyList()) }

    /** Admin stop: drop what NOXA queued and pause the room immediately. */
    fun stopRoomDj() = publishRoomControls(pause = true) { it.copy(queue = emptyList()) }

    private fun publishRoomControls(
        pause: Boolean = false,
        transform: (com.example.data.support.NoxaBackend.RoomState) ->
            com.example.data.support.NoxaBackend.RoomState
    ) {
        val code = roomCode ?: return
        val user = authManager.currentUser.value ?: return
        val room = _roomState.value as? com.example.ui.screens.RoomUiState.InRoom ?: return
        if (!room.isHost) return
        safeScope.launch {
            val song = playerState.value.currentSong
            val progress = playbackProgress.value
            val onRoomSong = song != null && song.id == room.songId
            val published = roomWrite {
                runCatching {
                    backend.publishRoom(code, user.uid, roomDisplayName(user)) { state ->
                        val synced = if (onRoomSong) {
                            state.copy(
                                playing = if (pause) false else playerState.value.isPlaying,
                                positionMs = progress.positionMs,
                                durationMs = progress.durationMs.takeIf { it > 0 } ?: state.durationMs
                            )
                        } else {
                            state
                        }
                        transform(synced)
                    }
                }.getOrNull()
            } ?: return@launch
            lastRemoteAt = published.updatedAt
            applyRoomUi(published, user.uid)
            if (pause && onRoomSong && playerState.value.isPlaying) player.togglePlayPause()
        }
    }

    /**
     * End of a room track: the host promotes the queue (or a similar song when
     * auto-radio is on) and publishes it once. Everyone else mutes their own
     * auto-advance for a moment so the room does not split into two songs.
     */
    private suspend fun advanceRoomIfEnded(
        room: com.example.data.support.NoxaBackend.RoomState,
        selfUid: String
    ) {
        if (room.songId.isBlank()) return
        val localDuration = if (playerState.value.currentSong?.id == room.songId) {
            playbackProgress.value.durationMs
        } else {
            0L
        }
        val duration = room.durationMs.takeIf { it > 0 }
            ?: localDuration.takeIf { it > 0 }
            ?: return
        if (!com.example.data.support.RoomRadio.hasEnded(
                room.playing,
                estimatedRoomPosition(room),
                duration
            )
        ) {
            return
        }
        if (room.queue.isEmpty() && !room.autoRadio) return
        val now = System.currentTimeMillis()
        // The hook runs every second while the track sits at its end; without
        // this it would hit the catalogue once per poll when nothing matches.
        if (now - lastRoomAdvanceAttemptAt < 8_000L) return
        lastRoomAdvanceAttemptAt = now
        roomMirrorMutedUntil = now + 6_000
        val driver = room.hostUid.takeIf { host -> room.members.any { it.uid == host } }
            ?: com.example.data.support.RoomVotes.skipperUid(room.members.map { it.uid })
            ?: return
        if (driver != selfUid || roomAdvancing) return
        roomAdvancing = true
        try {
            val head = room.queue.firstOrNull()
            val queued = head?.let { runCatching { resolveSongId(it.songId) }.getOrNull() }
            val next = queued ?: pickRoomRadioSong(room) ?: return
            val newQueue = if (queued != null) room.queue.drop(1) else room.queue
            val user = authManager.currentUser.value ?: return
            val published = roomWrite {
                runCatching {
                    backend.publishRoom(room.code, user.uid, roomDisplayName(user)) { state ->
                        // Re-check under the write: if the room moved on while
                        // we were searching (a manual pick, an admin stop), the
                        // newer decision wins and this advance is abandoned.
                        // This is the precise guard — a timestamp would also
                        // reject perfectly fresh snapshots.
                        if (state.songId != room.songId) {
                            return@publishRoom state
                        }
                        state.copy(
                            songId = next.id,
                            songTitle = next.title,
                            songArtist = next.artist,
                            songArtwork = next.artworkUrl,
                            playing = true,
                            positionMs = 0L,
                            durationMs = next.durationMs,
                            skipVotes = emptyList(),
                            queue = newQueue
                        )
                    }
                }.getOrNull()
            } ?: return
            if (published.songId != next.id) return
            lastRemoteAt = published.updatedAt
            roomRadioRecent.add(next.id)
            while (roomRadioRecent.size > com.example.data.support.RoomRadio.RECENT_LIMIT) {
                roomRadioRecent.removeAt(0)
            }
            applyingRemote = true
            try {
                playSong(next)
                applyRoomUi(published, selfUid)
                kotlinx.coroutines.delay(400)
            } finally {
                applyingRemote = false
            }
        } finally {
            roomAdvancing = false
        }
    }

    /** A track in the current song's lane, never one already played here. */
    private suspend fun pickRoomRadioSong(
        room: com.example.data.support.NoxaBackend.RoomState
    ): Song? = pickRoomRadioSong(room.songArtist, room.songId)

    private suspend fun pickRoomRadioSong(anchorArtist: String, currentSongId: String): Song? {
        val anchor = anchorArtist
        if (anchor.isBlank()) return null
        val candidates = runCatching {
            val artistId = repository.findArtistId(anchor)
            if (artistId != null) repository.artistTopTracks(artistId, 20)
            else repository.search(anchor)
        }.getOrDefault(emptyList())
        val recent = roomRadioRecent.toSet() + currentSongId
        return com.example.data.support.RoomRadio.pickSimilar(anchor, candidates, recent)
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
                executeVoteSkipIfNeeded(room, user.uid)
                advanceRoomIfEnded(room, user.uid)
                // Full refresh: song/playing/members/chat may all have moved
                // when a peer took over. applyRoomUi keeps the local "NOXA is
                // looking…" flag and this device's unconfirmed chat bubbles.
                applyRoomUi(room, user.uid)
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
     * **Vote Skip** — toggles this member's ballot for the song playing in
     * the room. The song itself is never hijacked: the publish only flips the
     * vote (plus our current position, so the timeline doesn't jump back).
     */
    fun toggleSkipVote() {
        val code = roomCode ?: return
        val user = authManager.currentUser.value ?: return
        val song = playerState.value.currentSong ?: return
        safeScope.launch {
            roomWrite {
                runCatching {
                backend.publishRoom(
                    code = code,
                    uid = user.uid,
                    name = user.displayName ?: user.email ?: "user"
                ) { room ->
                    val votes = room.skipVotes.filterNot { it == user.uid }
                    val withVote = if (room.skipVotes.contains(user.uid)) {
                        votes
                    } else {
                        votes + user.uid
                    }
                    // Only refresh playback fields when we are actually on
                    // the room's song — a freshly joined member must never
                    // clobber the room with their stale local player.
                    val synced = if (song.id == room.songId) {
                        room.copy(
                            playing = playerState.value.isPlaying,
                            positionMs = playbackProgress.value.positionMs
                        )
                    } else {
                        room
                    }
                    synced.copy(skipVotes = withVote)
                }
                }.getOrNull()
            }?.let { published ->
                lastRemoteAt = published.updatedAt
                applyRoomUi(published, user.uid)
            }
        }
    }

    /**
     * Majority reached → the deterministic skipper (first uid in sorted
     * order, computed identically on every device) advances the queue once.
     * The mirror publish clears the ballot; a follow-up publish guarantees
     * the room can't wedge even when the skipper's queue had nothing next.
     */
    private suspend fun executeVoteSkipIfNeeded(
        room: com.example.data.support.NoxaBackend.RoomState,
        selfUid: String
    ) {
        val roster = room.members.map { it.uid }
        val votes = com.example.data.support.RoomVotes.validVotes(room.skipVotes, roster)
        if (!com.example.data.support.RoomVotes.shouldSkip(votes.size, roster.size)) return
        if (com.example.data.support.RoomVotes.skipperUid(roster) != selfUid) return
        if (skipVoteInProgress) return
        skipVoteInProgress = true
        try {
            next()
            // Let the player-state mirror publish the new song first…
            kotlinx.coroutines.delay(700)
            // …then guarantee the ballot is cleared either way. If the
            // skipper's queue was empty, this drops the stale votes so the
            // room can simply vote again on the next song.
            publishRoomSnapshot(clearVotes = true)
        } finally {
            kotlinx.coroutines.delay(1_200)
            skipVoteInProgress = false
        }
    }

    /**
     * Local → room mirror. Deduped on (song, playing) so the collector
     * only publishes real changes; seeks and the 10s position heartbeat
     * call [mirrorNow] directly.
     */
    private fun publishRoomSnapshot(clearVotes: Boolean = false) {
        val code = roomCode ?: return
        if (applyingRemote) return
        val user = authManager.currentUser.value ?: return
        val song = playerState.value.currentSong ?: return
        val playing = playerState.value.isPlaying
        val positionMs = playbackProgress.value.positionMs
        safeScope.launch {
            roomWrite {
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
                        positionMs = positionMs,
                        // Published so every member can tell when the track
                        // ends — that is what triggers queue/auto-radio.
                        durationMs = playbackProgress.value.durationMs
                            .takeIf { it > 0 } ?: room.durationMs,
                        // A song/play change makes any running skip vote
                        // stale; heartbeats and seeks keep it alive.
                        skipVotes = if (clearVotes) emptyList() else room.skipVotes
                    )
                }
                }.getOrNull()
            }?.let { published -> lastRemoteAt = published.updatedAt }
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
                // The room just ended a song and is choosing the next one:
                // this phone's private auto-advance must not hijack it.
                if (System.currentTimeMillis() < roomMirrorMutedUntil) return@collect
                // Real content change: a running skip vote is now moot.
                publishRoomSnapshot(clearVotes = true)
            }
        }
        // Position heartbeat: keeps every member's timeline converging.
        safeScope.launch {
            while (isActive) {
                kotlinx.coroutines.delay(ROOM_HEARTBEAT_MS)
                if (roomCode != null && !applyingRemote &&
                    System.currentTimeMillis() >= roomMirrorMutedUntil &&
                    playerState.value.isPlaying
                ) {
                    publishRoomSnapshot()
                }
            }
        }
    }

    private fun com.example.data.support.NoxaBackend.RoomState.toUiState(
        code: String,
        selfUid: String,
        aiThinking: Boolean = false
    ): com.example.ui.screens.RoomUiState.InRoom {
        val roster = members.map { it.uid }
        val validVotes = com.example.data.support.RoomVotes.validVotes(skipVotes, roster)
        val effectiveHost = hostUid.takeIf { host -> roster.contains(host) } ?: roster.minOrNull()
        return com.example.ui.screens.RoomUiState.InRoom(
            code = code,
            songId = songId,
            songTitle = songTitle,
            songArtist = songArtist,
            songArtwork = songArtwork,
            playing = playing,
            positionMs = positionMs,
            durationMs = durationMs,
            members = members.map { member ->
                com.example.ui.screens.RoomMemberUi(
                    uid = member.uid,
                    name = member.name,
                    isSelf = member.uid == selfUid
                )
            },
            voteCount = validVotes.size,
            votesNeeded = com.example.data.support.RoomVotes.votesNeeded(roster.size),
            myVote = selfUid in skipVotes,
            hostUid = effectiveHost ?: "",
            isHost = effectiveHost == selfUid,
            autoRadio = autoRadio,
            aiEnabled = aiEnabled,
            chat = chat.map { message ->
                com.example.ui.screens.RoomChatUi(
                    id = message.id,
                    name = message.name,
                    text = message.text,
                    fromAi = message.fromAi,
                    isSelf = !message.fromAi && message.uid == selfUid,
                    at = message.at
                )
            },
            queue = queue.map { item ->
                com.example.ui.screens.RoomQueueUi(
                    songId = item.songId,
                    title = item.title,
                    artist = item.artist,
                    artwork = item.artwork,
                    addedBy = item.addedBy
                )
            },
            aiThinking = aiThinking
        )
    }

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
        val now = System.currentTimeMillis()
        // "Aktif hari ini": every account whose presence stamp (written by
        // the throttled client heartbeat) falls on today's calendar date.
        // "Online": stamped within the last 2 minutes. "Baru": registered
        // today. All fall back to 0 when nothing has ever heartbeat — the
        // panel shows an honest 0, never a fabricated number.
        val today = java.time.LocalDate.now()
        fun sameDay(ms: Long): Boolean = ms > 0 && runCatching {
            java.time.Instant.ofEpochMilli(ms)
                .atZone(java.time.ZoneId.systemDefault()).toLocalDate() == today
        }.getOrDefault(false)
        val logins = state.accounts.values
            .map {
                com.example.data.support.BackendLogin(
                    uid = it.uid,
                    email = it.email,
                    username = it.username,
                    role = it.role,
                    createdAt = it.createdAt,
                    lastSeenAt = it.lastSeenAt
                )
            }
            .sortedByDescending { it.createdAt }
        return com.example.ui.screens.DevUiState.Ready(
            accounts = logins,
            activeToday = logins.count { sameDay(it.lastSeenAt) },
            onlineNow = logins.count {
                it.lastSeenAt > 0 && now - it.lastSeenAt < ONLINE_WINDOW_MS
            },
            newToday = logins.count { sameDay(it.createdAt) },
            publishedVersionCode = state.update?.versionCode,
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
     * Sends one message to NOXA AI (v3.5.5 — official freemodel.in API).
     *
     * Replies carry action tags. Every requested track is resolved against the
     * real catalogue with a *strict* artist+title match, so a "Dewa 19" playlist
     * can never silently absorb a foreign song: a track that does not match is
     * skipped and reported, not replaced by the first search hit.
     */
    fun sendAiMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || _isAiThinking.value) return
        syncAiDeveloperFlag()
        _aiMessages.update { it + com.example.data.ai.AiChatMessage(fromUser = true, text = trimmed) }
        _aiError.value = null
        _isAiThinking.value = true
        safeScope.launch {
            // "Tulis Lirik" is grounded on the real web instead of the model's
            // memory: the request is looked up on LRCLIB first, so an existing
            // song is described from its actual words rather than invented ones.
            val reference = if (_aiFlavor.value == com.example.data.ai.AiFlavor.TULIS_LIRIK) {
                runCatching { lyricsRepository.referenceForRequest(trimmed) }
                    .getOrNull()
                    ?.asPromptBlock()
            } else {
                null
            }
            val reply = try {
                aiRepository.chat(
                    trimmed,
                    songMemoryStore.contextFor(),
                    _aiFlavor.value,
                    reference
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.example.data.ai.NoxaAiRepository.AiQuotaExceeded) {
                _isAiThinking.value = false
                _aiQuotas.value = aiRepository.quotas()
                _aiError.value = aiQuotaMessage(e)
                return@launch
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
            var builtPlaylist: AiPlaylistBuild? = null
            for (action in reply.actions) {
                when (action) {
                    is com.example.data.ai.NoxaAiAction.Play -> {
                        val song = resolveAiTrack(
                            com.example.data.ai.AiTrackRef(action.artist, action.title)
                        )
                        if (song != null) {
                            cardSong = song
                            playSong(song)
                        } else {
                            note = "lagu tidak ditemukan di katalog NOXA"
                        }
                    }

                    is com.example.data.ai.NoxaAiAction.CreatePlaylist -> {
                        val build = createPlaylistFromAi(action)
                        if (build != null) {
                            builtPlaylist = build
                            notes.add(
                                "Playlist \"${build.name}\" siap berisi ${build.count} lagu." +
                                    if (build.skipped > 0) {
                                        " ${build.skipped} lagu dilewati karena tidak cocok " +
                                            "dengan artis/judul di katalog."
                                    } else {
                                        ""
                                    }
                            )
                        } else {
                            notes.add(
                                "Playlist gagal dibuat — sebut artis dan judul dengan lebih " +
                                    "spesifik supaya bisa dicocokkan ke katalog."
                            )
                        }
                    }

                    is com.example.data.ai.NoxaAiAction.Queue -> {
                        val songs = action.tracks.mapNotNull { resolveAiTrack(it) }
                        // playNext inserts right after the current item, so the
                        // list is inserted in reverse to keep the AI's order.
                        songs.asReversed().forEach { player.playNext(it) }
                        notes.add(
                            if (songs.isEmpty()) "Lagu yang diminta tidak ditemukan di katalog."
                            else "${songs.size} lagu masuk ke antrean berikutnya."
                        )
                    }

                    is com.example.data.ai.NoxaAiAction.ReplaceTrack -> {
                        notes.add(replacePlaylistTrack(action))
                    }

                    is com.example.data.ai.NoxaAiAction.Equalizer -> {
                        notes.add(applyAiEqualizer(action.preset))
                    }
                }
            }

            val body = buildString {
                append(reply.text)
                reply.fallbackFrom?.let { down ->
                    append("\n\n(")
                    append(down.label)
                    append(" sedang maintenance — dialihkan ke ")
                    append(reply.servedBy.label)
                    append(".)")
                }
                notes.forEach { line -> append("\n").append(line) }
            }
            _aiMessages.update {
                it + com.example.data.ai.AiChatMessage(
                    fromUser = false,
                    text = body,
                    songId = cardSong?.id,
                    songTitle = cardSong?.title,
                    songArtist = cardSong?.artist,
                    songArtwork = cardSong?.artworkUrl?.takeIf { url -> url.isNotBlank() },
                    songNote = note,
                    playlistName = builtPlaylist?.name,
                    playlistId = builtPlaylist?.id,
                    playlistTrackCount = builtPlaylist?.count ?: 0,
                    playlistCoverSeed = builtPlaylist?.coverSeed ?: 0
                )
            }
            aiRepository.saveHistory(_aiMessages.value)
            _aiQuotas.value = aiRepository.quotas()
            // A maintenance fallback is worth re-reading: the badge in the
            // header and the picker should reflect it immediately.
            if (reply.fallbackFrom != null) {
                _aiModelStatuses.value = aiRepository.refreshModelStatus()
            }
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

    /** Result of an AI playlist build, used to render the result card. */
    private data class AiPlaylistBuild(
        val id: Long,
        val name: String,
        val count: Int,
        val skipped: Int,
        val coverSeed: Int
    )

    /**
     * Strict catalogue match for one AI track reference.
     *
     * Title must match (either direction, punctuation-insensitive) AND the
     * artist must match when the AI declared one. There is deliberately **no**
     * "fall back to the first search hit": that fallback is exactly what used
     * to drop an unrelated song into an artist-specific playlist.
     */
    private suspend fun resolveAiTrack(ref: com.example.data.ai.AiTrackRef): Song? {
        val wantedTitle = normaliseForMatch(ref.title)
        if (wantedTitle.isBlank()) return null
        val wantedArtist = normaliseForMatch(ref.artist)
        val query = listOf(ref.artist, ref.title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { ref.title }
        val results = runCatching { repository.search(query) }.getOrDefault(emptyList())
        val pool = if (results.isEmpty() && ref.artist.isNotBlank()) {
            runCatching { repository.search(ref.title) }.getOrDefault(emptyList())
        } else {
            results
        }
        return pool.firstOrNull { song ->
            val songTitle = normaliseForMatch(song.title)
            val titleOk = songTitle == wantedTitle ||
                songTitle.contains(wantedTitle) ||
                wantedTitle.contains(songTitle)
            titleOk && (wantedArtist.isBlank() || artistMatches(song.artist, wantedArtist))
        }
    }

    /**
     * Artist comparison that tolerates "Dewa 19" vs "Dewa 19 feat. ..." and
     * "Sheila On 7" vs "Sheila on 7" without accepting a different artist.
     */
    private fun artistMatches(songArtist: String, wantedArtist: String): Boolean {
        val a = normaliseForMatch(songArtist)
        val b = normaliseForMatch(wantedArtist)
        if (a.isBlank() || b.isBlank()) return true
        if (a.contains(b) || b.contains(a)) return true
        val aTokens = a.split(' ').filter { it.length > 1 }.toSet()
        val bTokens = b.split(' ').filter { it.length > 1 }.toSet()
        if (aTokens.isEmpty() || bTokens.isEmpty()) return false
        return aTokens.intersect(bTokens).isNotEmpty()
    }

    /** Lowercase, ASCII-ish, punctuation-free — the shared comparison form. */
    private fun normaliseForMatch(raw: String): String = raw
        .lowercase()
        .replace(Regex("\\([^)]*\\)"), " ")
        .replace(Regex("\\[[^]]*]"), " ")
        .replace(Regex("(?i)\\b(remaster(ed)?|feat\\.?|ft\\.?|version|versi|official)\\b"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    /** Builds the AI playlist from strictly matched tracks only. */
    private suspend fun createPlaylistFromAi(
        action: com.example.data.ai.NoxaAiAction.CreatePlaylist
    ): AiPlaylistBuild? {
        val name = action.name.ifBlank { "Playlist NOXA AI" }
        val resolved = LinkedHashMap<String, Song>()
        var skipped = 0
        for (ref in action.tracks.take(20)) {
            val song = resolveAiTrack(ref)
            if (song == null) {
                skipped++
            } else {
                resolved.putIfAbsent(song.id, song)
            }
        }
        if (resolved.isEmpty()) return null
        val playlistId = dao.insertPlaylist(
            PlaylistEntity(name = name, description = "Dibuat oleh NOXA AI")
        )
        resolved.values.forEachIndexed { index, song ->
            dao.insertPlaylistSong(PlaylistSongEntity.fromSong(playlistId, song, index))
        }
        return AiPlaylistBuild(
            id = playlistId,
            name = name,
            count = resolved.size,
            skipped = skipped,
            coverSeed = com.example.data.local.PlaylistCoverStore.defaultSeed(name)
        )
    }

    /** Swaps one stray track inside an existing playlist. */
    private suspend fun replacePlaylistTrack(
        action: com.example.data.ai.NoxaAiAction.ReplaceTrack
    ): String {
        val playlists = runCatching { dao.getAllPlaylists().first() }.getOrDefault(emptyList())
        val target = playlists.firstOrNull {
            it.name.equals(action.playlistName, ignoreCase = true)
        } ?: playlists.firstOrNull {
            it.name.contains(action.playlistName, ignoreCase = true)
        } ?: return "Playlist \"${action.playlistName}\" tidak ditemukan."

        val songs = runCatching { dao.getPlaylistSongs(target.playlistId).first() }
            .getOrDefault(emptyList())
        val wantedTitle = normaliseForMatch(action.old.title)
        val wantedArtist = normaliseForMatch(action.old.artist)
        val victim = songs.firstOrNull { entry ->
            val title = normaliseForMatch(entry.title)
            (title.contains(wantedTitle) || wantedTitle.contains(title)) &&
                (wantedArtist.isBlank() || artistMatches(entry.artist, wantedArtist))
        } ?: return "Lagu \"${action.old.title}\" tidak ada di playlist \"${target.name}\"."

        val replacement = resolveAiTrack(action.new)
            ?: return "Pengganti \"${action.new.title}\" tidak ditemukan di katalog."
        dao.deletePlaylistSong(target.playlistId, victim.songId)
        dao.insertPlaylistSong(
            PlaylistSongEntity.fromSong(target.playlistId, replacement, victim.position)
        )
        return "Di \"${target.name}\": ${victim.title} diganti dengan ${replacement.title}."
    }

    /** Maps the AI's preset name onto the equalizer's real preset list. */
    private fun applyAiEqualizer(requested: String): String {
        val presets = player.uiState.value.equalizerState.presets
        // The Studio renamed the presets; keep the AI's older vocabulary
        // working ("bass boost" -> "Bass", "vocal booster" -> "Vokal").
        val alias = when (requested.lowercase()) {
            "bass boost", "bassboost", "extra bass", "bass" -> "Bass"
            "vocal booster", "vocal", "vokal booster" -> "Vokal"
            "flat", "normal", "netral", "neutral" -> "Netral"
            "warm", "hangat" -> "Hangat"
            "detail", "detailed", "treble", "cerah" -> "Detail"
            "auto", "otomatis", "noxa auto" -> SoundProfiles.AUTO
            else -> null
        }
        alias?.let { matched ->
            setEqualizerPreset(matched)
            return "Suara diset ke profil $matched."
        }
        val wanted = normaliseForMatch(requested)
        val match = presets.firstOrNull { it.equals(requested, ignoreCase = true) }
            ?: presets.firstOrNull { preset ->
                val normalised = normaliseForMatch(preset)
                normalised.contains(wanted) || wanted.contains(normalised)
            }
            ?: presets.firstOrNull { preset ->
                val normalised = normaliseForMatch(preset)
                wanted.contains(normalised.substringBefore(' '))
            }
        return if (match != null) {
            setEqualizerPreset(match)
            "Equalizer diset ke $match."
        } else {
            "Preset equalizer \"$requested\" tidak tersedia."
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
