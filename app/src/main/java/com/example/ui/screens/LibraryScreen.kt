package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PlaylistSummary
import com.example.data.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

@Composable
fun LibraryScreen(
    favorites: List<Song>,
    history: List<Song>,
    playlistSummaries: List<PlaylistSummary>,
    currentPlayingSongId: String?,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    favoriteIds: Set<String>,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var playlistIdPendingDelete by remember { mutableStateOf<Long?>(null) }

    val tabs = listOf("Favorit (${favorites.size})", "Playlist (${playlistSummaries.size})", "Riwayat")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .testTag("library_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Koleksi Saya",
                color = NoxaTextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )

            if (selectedTab == 1) {
                IconButton(
                    onClick = { showCreatePlaylistDialog = true },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(NoxaSurfaceSecondary)
                        .testTag("add_playlist_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Playlist baru",
                        tint = NoxaLime,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else if (selectedTab == 2 && history.isNotEmpty()) {
                TextButton(
                    onClick = { onClearHistory() },
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Text("Bersihkan", color = NoxaTextTertiary, fontSize = 13.sp)
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = NoxaBlack,
            contentColor = NoxaLime,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = NoxaLime,
                    height = 2.5.dp
                )
            },
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NoxaBorder)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            color = if (selectedTab == index) NoxaLime else NoxaTextSecondary
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                if (favorites.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.Favorite,
                        title = "Belum ada lagu favorit",
                        subtitle = "Tekan ikon hati pada lagu mana pun untuk menyimpannya di sini"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 12.dp, start = 8.dp, end = 8.dp)
                    ) {
                        item(key = "fav_actions") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = { onPlaySong(favorites[0], favorites, 0) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NoxaLime,
                                        contentColor = NoxaBlack
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Putar semua", fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val shuffled = favorites.shuffled()
                                        onPlaySong(shuffled[0], shuffled, 0)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = NoxaSurfaceSecondary,
                                        contentColor = NoxaTextPrimary
                                    ),
                                    shape = RoundedCornerShape(20.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = null,
                                        tint = NoxaLime,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Acak", fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        itemsIndexed(favorites, key = { _, song -> song.id }) { index, song ->
                            SongListItem(
                                song = song,
                                isPlaying = song.id == currentPlayingSongId,
                                isFavorite = true,
                                onPlay = { onPlaySong(song, favorites, index) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onAddToPlaylist = { onAddToPlaylist(song) },
                                onShare = { onShare(song) },
                                onOpenArtist = onOpenArtist,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }

            1 -> {
                if (playlistSummaries.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.PlaylistPlay,
                        title = "Buat playlist pertama Anda",
                        subtitle = "Kumpulkan lagu favorit ke dalam satu daftar putar",
                        actionLabel = "Playlist baru",
                        onAction = { showCreatePlaylistDialog = true }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 12.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(playlistSummaries, key = { it.playlistId }) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NoxaSurfaceElevated)
                                    .border(1.dp, NoxaBorder, RoundedCornerShape(12.dp))
                                    .clickable { onOpenPlaylist(playlist.playlistId) }
                                    .padding(14.dp)
                                    .testTag("playlist_row_${playlist.playlistId}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NoxaSurfaceSecondary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlaylistPlay,
                                            contentDescription = null,
                                            tint = NoxaLime,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = playlist.name,
                                            color = NoxaTextPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${playlist.songCount} lagu",
                                            color = NoxaTextSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                IconButton(onClick = { playlistIdPendingDelete = playlist.playlistId }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Hapus playlist",
                                        tint = NoxaTextTertiary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            2 -> {
                if (history.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = "Belum ada riwayat",
                        subtitle = "Lagu yang Anda putar akan otomatis muncul di sini"
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp, top = 8.dp, start = 8.dp, end = 8.dp)
                    ) {
                        itemsIndexed(history, key = { _, song -> song.id }) { index, song ->
                            SongListItem(
                                song = song,
                                isPlaying = song.id == currentPlayingSongId,
                                isFavorite = favoriteIds.contains(song.id),
                                onPlay = { onPlaySong(song, history, index) },
                                onToggleFavorite = { onToggleFavorite(song) },
                                onPlayNext = { onPlayNext(song) },
                                onAddToQueue = { onAddToQueue(song) },
                                onAddToPlaylist = { onAddToPlaylist(song) },
                                onShare = { onShare(song) },
                                onOpenArtist = onOpenArtist,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            containerColor = NoxaSurfaceSecondary,
            title = {
                Text("Playlist baru", color = NoxaTextPrimary, fontWeight = FontWeight.Bold)
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    singleLine = true,
                    label = { Text("Nama playlist", color = NoxaTextTertiary, fontSize = 13.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NoxaLime,
                        unfocusedBorderColor = NoxaBorder,
                        focusedTextColor = NoxaTextPrimary,
                        unfocusedTextColor = NoxaTextPrimary,
                        cursorColor = NoxaLime
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty()) onCreatePlaylist(name, "")
                        showCreatePlaylistDialog = false
                        newPlaylistName = ""
                    },
                    enabled = newPlaylistName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoxaLime,
                        contentColor = NoxaBlack
                    )
                ) {
                    Text("Buat", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreatePlaylistDialog = false
                    newPlaylistName = ""
                }) {
                    Text("Batal", color = NoxaTextSecondary)
                }
            }
        )
    }

    playlistIdPendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { playlistIdPendingDelete = null },
            containerColor = NoxaSurfaceSecondary,
            title = { Text("Hapus playlist?", color = NoxaTextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Semua lagu di dalamnya juga akan dihapus dari playlist ini.",
                    color = NoxaTextSecondary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePlaylist(id)
                        playlistIdPendingDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoxaLime,
                        contentColor = NoxaBlack
                    )
                ) {
                    Text("Hapus", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistIdPendingDelete = null }) {
                    Text("Batal", color = NoxaTextSecondary)
                }
            }
        )
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = NoxaTextTertiary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                color = NoxaTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                color = NoxaTextTertiary,
                fontSize = 13.sp
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NoxaLime,
                        contentColor = NoxaBlack
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(actionLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
