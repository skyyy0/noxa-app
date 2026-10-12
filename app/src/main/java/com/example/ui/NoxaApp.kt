package com.example.ui

import android.app.Activity
import android.widget.Toast
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
import com.example.ui.screens.AboutSkyScreen
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .background(NoxaBlack)
                            .border(width = 0.8.dp, color = NoxaBorder)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        bottomNavItems.forEach { screen ->
                            val isSelected = currentRoute == screen.route
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(vertical = 6.dp)
                                    .testTag("nav_item_${screen.route}"),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) NoxaLime.copy(alpha = 0.15f) else Color.Transparent)
                                        .padding(horizontal = 14.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title,
                                        tint = if (isSelected) NoxaLime else NoxaTextTertiary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = screen.title,
                                    color = if (isSelected) NoxaLime else NoxaTextTertiary,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
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
                        onRetry = { viewModel.loadHome() }
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
                        onShare = { ShareUtils.shareSong(context, it) }
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
                        onClearHistory = { viewModel.clearHistory() }
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
                            onShare = { ShareUtils.shareSong(context, it) }
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
            onRetryLyrics = { viewModel.retryLyrics() }
        )

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
