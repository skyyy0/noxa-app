package com.example.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.data.model.Song
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.components.YouTubePolicyDialog
import com.example.ui.screens.AboutSkyScreen
import com.example.ui.screens.AlbumScreen
import com.example.ui.screens.ArtistScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.ShareUtils
import com.example.ui.viewmodel.MusicViewModel
import com.example.ui.viewmodel.VideoUiState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Equalizer : Screen("equalizer", "Equalizer", Icons.Default.Tune)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object AboutSky : Screen("about_sky", "About Sky", Icons.Default.Person)

    /** Playlist detail: `playlist/{id}`. */
    object Playlist : Screen("playlist", "Playlist", Icons.Default.LibraryMusic) {
        const val ARG_ID = "playlistId"
        const val ROUTE = "playlist/{$ARG_ID}"
        fun routeFor(id: Long) = "playlist/$id"
    }

    /** Artist profile: `artist/{artistId}` (Deezer artist id). */
    object Artist : Screen("artist", "Artist", Icons.Default.Person) {
        const val ARG_ID = "artistId"
        const val ROUTE = "artist/{$ARG_ID}"
        fun routeFor(id: Long) = "artist/$id"
    }

    /** Album detail: `album/{albumId}`. */
    object Album : Screen("album", "Album", Icons.Default.LibraryMusic) {
        const val ARG_ID = "albumId"
        const val ROUTE = "album/{$ARG_ID}"
        fun routeFor(id: Long) = "album/$id"
    }
}

@Composable
fun NoxaApp(
    viewModel: MusicViewModel = viewModel(),
    navController: NavHostController = rememberNavController()
) {
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val heroSong by viewModel.heroSong.collectAsStateWithLifecycle()
    val homeSections by viewModel.homeSections.collectAsStateWithLifecycle()
    val isHomeLoading by viewModel.isHomeLoading.collectAsStateWithLifecycle()
    val homeError by viewModel.homeError.collectAsStateWithLifecycle()
    val resumableSong by viewModel.resumableSong.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    // Collecting the favourite *set* once here is what makes the heart icons
    // update immediately everywhere after a tap.
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val historySongs by viewModel.historySongs.collectAsStateWithLifecycle()
    val playlistSummaries by viewModel.playlistSummaries.collectAsStateWithLifecycle()

    val lyricsState by viewModel.lyricsState.collectAsStateWithLifecycle()
    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsStateWithLifecycle()
    val videoUiState by viewModel.videoUiState.collectAsStateWithLifecycle()
    val pendingYouTubePlay by viewModel.pendingYouTubePlay.collectAsStateWithLifecycle()
    val artistUiState by viewModel.artistUiState.collectAsStateWithLifecycle()
    val albumUiState by viewModel.albumUiState.collectAsStateWithLifecycle()

    val context = LocalContext.current

    // Song awaiting "which playlist?".
    var songPendingPlaylist by remember { mutableStateOf<Song?>(null) }

    LaunchedEffect(playerState.errorMessage) {
        val message = playerState.errorMessage
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.clearPlayerError()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: Screen.Home.route

    // Tapping an artist name anywhere: resolve id (or name) -> open the page.
    val openArtistProfile: (String, String) -> Unit = { artistId, artistName ->
        viewModel.openArtistProfile(artistId, artistName) { resolvedId ->
            navController.navigate(Screen.Artist.routeFor(resolvedId)) {
                launchSingleTop = true
            }
        }
    }

    val bottomNavItems = listOf(
        Screen.Home,
        Screen.Search,
        Screen.Library,
        Screen.Equalizer,
        Screen.Profile
    )

    val isCurrentFavorite = playerState.currentSong?.id?.let { favoriteIds.contains(it) } ?: false

    Box(modifier = Modifier.fillMaxSize().background(NoxaBlack)) {
        Scaffold(
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    MiniPlayer(
                        currentSong = playerState.currentSong,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        progressFlow = viewModel.playbackProgress,
                        isFavorite = isCurrentFavorite,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onToggleFavorite = {
                            playerState.currentSong?.let { viewModel.toggleFavorite(it) }
                        },
                        onClick = { viewModel.openFullPlayer() }
                    )

                    // Floating pill navigation: the bar itself is a rounded card
                    // instead of a full-width box, and the selected tab gets an
                    // animated lime pill with its label — the modern iOS/Android
                    // hybrid look rather than the old squared-off row.
                    val navigate: (Screen) -> Unit = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(NoxaSurfaceElevated)
                            .border(1.dp, NoxaBorder.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            val pillColor by animateColorAsState(
                                targetValue = if (isSelected) NoxaLime else Color.Transparent,
                                label = "nav_pill"
                            )
                            Row(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(pillColor)
                                    .clickable { navigate(screen) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                                    .testTag("nav_item_${screen.route}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title,
                                    tint = if (isSelected) NoxaBlack else NoxaTextTertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                                AnimatedVisibility(
                                    visible = isSelected,
                                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                                ) {
                                    Text(
                                        text = screen.title,
                                        color = NoxaBlack,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        modifier = Modifier.padding(start = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            containerColor = NoxaBlack
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        heroSong = heroSong,
                        sections = homeSections,
                        resumeSong = resumableSong,
                        isLoading = isHomeLoading,
                        errorMessage = homeError,
                        currentUser = currentUser,
                        isGuestMode = isGuestMode,
                        isSigningIn = isSigningIn,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onResume = { viewModel.resumeLastSession() },
                        onDismissResume = { viewModel.dismissResume() },
                        onSignInWithGoogle = { activity -> viewModel.signInWithGoogle(activity) },
                        onRetry = { viewModel.loadHome() },
                        onOpenArtist = openArtistProfile
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        searchQuery = searchQuery,
                        searchResults = searchResults,
                        isSearching = isSearching,
                        currentPlayingSongId = playerState.currentSong?.id,
                        favoriteIds = favoriteIds,
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onPerformSearch = { viewModel.performSearch(it) },
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { songPendingPlaylist = it },
                        onShare = { ShareUtils.shareSong(context, it) },
                        onOpenArtist = openArtistProfile
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        favorites = favoriteSongs,
                        history = historySongs,
                        playlistSummaries = playlistSummaries,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        favoriteIds = favoriteIds,
                        onPlayNext = { viewModel.playNext(it) },
                        onAddToQueue = { viewModel.addToQueue(it) },
                        onAddToPlaylist = { songPendingPlaylist = it },
                        onShare = { ShareUtils.shareSong(context, it) },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onOpenPlaylist = { navController.navigate(Screen.Playlist.routeFor(it)) },
                        onClearHistory = { viewModel.clearHistory() },
                        onOpenArtist = openArtistProfile
                    )
                }

                composable(Screen.Playlist.ROUTE) { entry ->
                    val playlistId = entry.arguments?.getString(Screen.Playlist.ARG_ID)?.toLongOrNull()
                    if (playlistId == null) {
                        navController.popBackStack()
                    } else {
                        val songsFlow = remember(playlistId) { viewModel.playlistSongs(playlistId) }
                        PlaylistDetailScreen(
                            playlistId = playlistId,
                            playlistName = playlistSummaries
                                .firstOrNull { it.playlistId == playlistId }?.name
                                ?: "Playlist",
                            songsFlow = songsFlow,
                            favoriteIds = favoriteIds,
                            currentPlayingSongId = playerState.currentSong?.id,
                            onBack = { navController.popBackStack() },
                            onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onRemoveSong = { songId -> viewModel.removeSongFromPlaylist(playlistId, songId) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onShare = { ShareUtils.shareSong(context, it) },
                            onOpenArtist = openArtistProfile
                        )
                    }
                }

                composable(Screen.Artist.ROUTE) { entry ->
                    val artistId = entry.arguments?.getString(Screen.Artist.ARG_ID)?.toLongOrNull()
                    if (artistId == null) {
                        navController.popBackStack()
                    } else {
                        LaunchedEffect(artistId) { viewModel.loadArtist(artistId) }
                        ArtistScreen(
                            state = artistUiState,
                            favoriteIds = favoriteIds,
                            currentPlayingSongId = playerState.currentSong?.id,
                            onBack = { navController.popBackStack() },
                            onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songPendingPlaylist = it },
                            onShare = { ShareUtils.shareSong(context, it) },
                            onOpenAlbum = { albumId ->
                                navController.navigate(Screen.Album.routeFor(albumId))
                            },
                            onOpenArtist = openArtistProfile,
                            onRetry = { viewModel.loadArtist(artistId) }
                        )
                    }
                }

                composable(Screen.Album.ROUTE) { entry ->
                    val albumId = entry.arguments?.getString(Screen.Album.ARG_ID)?.toLongOrNull()
                    if (albumId == null) {
                        navController.popBackStack()
                    } else {
                        LaunchedEffect(albumId) { viewModel.loadAlbum(albumId) }
                        AlbumScreen(
                            state = albumUiState,
                            favoriteIds = favoriteIds,
                            currentPlayingSongId = playerState.currentSong?.id,
                            onBack = { navController.popBackStack() },
                            onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onPlayNext = { viewModel.playNext(it) },
                            onAddToQueue = { viewModel.addToQueue(it) },
                            onAddToPlaylist = { songPendingPlaylist = it },
                            onShare = { ShareUtils.shareSong(context, it) },
                            onOpenArtist = openArtistProfile,
                            onRetry = { viewModel.loadAlbum(albumId) }
                        )
                    }
                }

                composable(Screen.Equalizer.route) {
                    EqualizerScreen(
                        equalizerState = playerState.equalizerState,
                        onSetEnabled = { viewModel.setEqualizerEnabled(it) },
                        onSelectPreset = { viewModel.setEqualizerPreset(it) },
                        onSetBandLevel = { band, level -> viewModel.setEqualizerBand(band, level) },
                        onSetBassBoost = { viewModel.setBassBoost(it) },
                        onSetVirtualizer = { viewModel.setVirtualizer(it) }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        currentUser = currentUser,
                        isSigningIn = isSigningIn,
                        onSignInWithGoogle = { activity: Activity -> viewModel.signInWithGoogle(activity) },
                        onSignOut = { viewModel.signOut() },
                        onNavigateToAboutSky = {
                            navController.navigate(Screen.AboutSky.route)
                        }
                    )
                }

                composable(Screen.AboutSky.route) {
                    AboutSkyScreen(
                        playerState = playerState,
                        onPlaySong = { song -> viewModel.playSong(song) },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }

        // Full Screen Player Overlay Sheet
        FullPlayerSheet(
            visible = isFullPlayerVisible,
            playerState = playerState,
            progressFlow = viewModel.playbackProgress,
            lyricsState = lyricsState,
            videoUiState = videoUiState,
            isFavorite = isCurrentFavorite,
            onClose = { viewModel.closeFullPlayer() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekTo = { viewModel.seekTo(it) },
            onNext = { viewModel.next() },
            onPrevious = { viewModel.previous() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleRepeat = { viewModel.toggleRepeat() },
            onToggleFavorite = {
                playerState.currentSong?.let { viewModel.toggleFavorite(it) }
            },
            onSetSleepTimer = { viewModel.setSleepTimer(it) },
            onOpenEqualizer = {
                viewModel.closeFullPlayer()
                navController.navigate(Screen.Equalizer.route)
            },
            onSelectQueueItem = { song, idx ->
                viewModel.playSong(song, playerState.queue, idx)
            },
            onShare = {
                playerState.currentSong?.let { ShareUtils.shareSong(context, it) }
            },
            onRetryLyrics = { viewModel.retryLyrics() },
            onSetVideoMode = { enabled -> viewModel.setVideoMode(enabled) },
            onAttachVideoSurface = { surface -> viewModel.attachMvSurface(surface) },
            onOpenArtist = { artistId, artistName ->
                viewModel.openArtistProfile(artistId, artistName) { resolvedId ->
                    viewModel.closeFullPlayer()
                    navController.navigate(Screen.Artist.routeFor(resolvedId)) {
                        launchSingleTop = true
                    }
                }
            }
        )

        // One-time YouTube usage policy, shown before the first YouTube play.
        if (pendingYouTubePlay != null) {
            YouTubePolicyDialog(
                onAccept = { viewModel.acceptYouTubePolicy() },
                onDecline = { viewModel.declineYouTubePolicy() }
            )
        }

        // "Which playlist?" picker, triggered from any song's overflow menu.
        songPendingPlaylist?.let { song ->
            AddToPlaylistDialog(
                song = song,
                playlists = playlistSummaries,
                onDismiss = { songPendingPlaylist = null },
                onSelect = { playlistId ->
                    viewModel.addSongToPlaylist(playlistId, song)
                    songPendingPlaylist = null
                    Toast.makeText(context, "Ditambahkan ke playlist", Toast.LENGTH_SHORT).show()
                },
                onCreatePlaylist = { name ->
                    viewModel.createPlaylist(name)
                    Toast.makeText(context, "Playlist \"$name\" dibuat", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}
