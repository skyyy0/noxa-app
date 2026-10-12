package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import android.widget.Toast
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
import com.example.ui.components.FullPlayerSheet
import com.example.ui.components.MiniPlayer
import com.example.ui.screens.AboutSkyScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.MusicViewModel

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Search : Screen("search", "Search", Icons.Default.Search)
    object Library : Screen("library", "Library", Icons.Default.LibraryMusic)
    object Equalizer : Screen("equalizer", "Equalizer", Icons.Default.Tune)
    object Profile : Screen("profile", "Profile", Icons.Default.Person)
    object AboutSky : Screen("about_sky", "About Sky", Icons.Default.Person)
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
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val isGuestMode by viewModel.isGuestMode.collectAsStateWithLifecycle()
    val isSigningIn by viewModel.isSigningIn.collectAsStateWithLifecycle()

    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()

    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val historySongs by viewModel.historySongs.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val isFullPlayerVisible by viewModel.isFullPlayerVisible.collectAsStateWithLifecycle()

    // Surface real playback errors (e.g. no audio source available) instead of
    // silently pretending something is playing.
    val context = LocalContext.current
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

    val isCurrentFavorite = playerState.currentSong?.let { viewModel.isFavorite(it.id) } ?: false

    val progress = if (playerState.durationMs > 0) {
        playerState.currentPositionMs.toFloat() / playerState.durationMs.toFloat()
    } else 0f

    Box(modifier = Modifier.fillMaxSize().background(NoxaBlack)) {
        Scaffold(
            bottomBar = {
                // Persistent bottom container with MiniPlayer + BottomNavigation
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    // Floating MiniPlayer directly above BottomNavigation
                    MiniPlayer(
                        currentSong = playerState.currentSong,
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        progress = progress,
                        isFavorite = isCurrentFavorite,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onNext = { viewModel.next() },
                        onToggleFavorite = {
                            playerState.currentSong?.let { viewModel.toggleFavorite(it) }
                        },
                        onClick = { viewModel.openFullPlayer() }
                    )

                    // Bottom Navigation Bar
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
                                // Active indicator pill/icon
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
                        isLoading = isHomeLoading,
                        errorMessage = homeError,
                        currentUser = currentUser,
                        isGuestMode = isGuestMode,
                        isSigningIn = isSigningIn,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
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
                        onQueryChange = { viewModel.onSearchQueryChanged(it) },
                        onPerformSearch = { viewModel.performSearch(it) },
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        isFavorite = { viewModel.isFavorite(it) }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        favorites = favoriteSongs,
                        history = historySongs,
                        playlists = playlists,
                        currentPlayingSongId = playerState.currentSong?.id,
                        onPlaySong = { song, queue, idx -> viewModel.playSong(song, queue, idx) },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onCreatePlaylist = { name, desc -> viewModel.createPlaylist(name, desc) },
                        onDeletePlaylist = { viewModel.deletePlaylist(it) },
                        onClearHistory = { viewModel.clearHistory() }
                    )
                }

                composable(Screen.Equalizer.route) {
                    EqualizerScreen(
                        equalizerState = playerState.equalizerState,
                        onSetEnabled = { viewModel.setEqualizerEnabled(it) },
                        onSelectPreset = { viewModel.setEqualizerPreset(it) },
                        onSetBandLevel = { band, level -> viewModel.setEqualizerBand(band, level) }
                    )
                }

                composable(Screen.Profile.route) {
                    ProfileScreen(
                        currentUser = currentUser,
                        isSigningIn = isSigningIn,
                        onSignInWithGoogle = { activity -> viewModel.signInWithGoogle(activity) },
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
            }
        )
    }
}
