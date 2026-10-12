package com.example.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.AlbumUiState

/**
 * Album page: cover, title, artist (tapping the name returns to the artist
 * page), year/track count, and the full tracklist in track order. Tapping any
 * track plays it through the normal chain (full audio for previews included).
 */
@Composable
fun AlbumScreen(
    state: AlbumUiState,
    favoriteIds: Set<String>,
    currentPlayingSongId: String?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
    ) {
        when (val s = state) {
            is AlbumUiState.Loading -> {
                CircularProgressIndicator(
                    color = NoxaLime,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
            }

            is AlbumUiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = s.message,
                        color = NoxaTextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Coba lagi",
                        color = NoxaLime,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .border(1.dp, NoxaLime, RoundedCornerShape(20.dp))
                            .clickable { onRetry() }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    )
                }
            }

            is AlbumUiState.Ready -> {
                val album = s.album
                val tracks = s.tracks

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Kembali",
                                tint = NoxaTextPrimary
                            )
                        }
                        Text(
                            text = "Album",
                            color = NoxaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Header: cover + title + artist line (→ artist profile).
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(album.coverUrl)
                                .size(440, 440)
                                .crossfade(true)
                                .build(),
                            contentDescription = album.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(168.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .border(1.dp, NoxaBorder, RoundedCornerShape(14.dp))
                                .background(NoxaSurfaceSecondary)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = album.title,
                                color = NoxaTextPrimary,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = album.artistName,
                                    color = NoxaTextSecondary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onOpenArtist(
                                                album.artistId.toString(),
                                                album.artistName
                                            )
                                        }
                                        .padding(vertical = 2.dp, horizontal = 2.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                VerifiedArtistBadge(
                                    isBigArtist = false,
                                    sizeDp = 15
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = buildString {
                                    if (album.releaseYear.isNotBlank()) append(album.releaseYear)
                                    if (album.trackCount > 0) {
                                        if (isNotEmpty()) append(" • ")
                                        append(album.trackCount)
                                        append(" lagu")
                                    }
                                }.ifEmpty { "Album" },
                                color = NoxaTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    if (tracks.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AlbumAction(
                                label = "Putar album",
                                primary = true,
                                onClick = { onPlaySong(tracks[0], tracks, 0) }
                            )
                            AlbumAction(
                                label = "Acak",
                                primary = false,
                                onClick = {
                                    val shuffled = tracks.shuffled()
                                    onPlaySong(shuffled[0], shuffled, 0)
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (tracks.isEmpty()) {
                        Text(
                            text = "Album ini tidak memiliki lagu yang bisa diputar.",
                            color = NoxaTextTertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            tracks.forEachIndexed { index, song ->
                                SongListItem(
                                    song = song,
                                    rank = index + 1,
                                    isPlaying = song.id == currentPlayingSongId,
                                    isFavorite = favoriteIds.contains(song.id),
                                    onPlay = { onPlaySong(song, tracks, index) },
                                    onToggleFavorite = { onToggleFavorite(song) },
                                    onPlayNext = { onPlayNext(song) },
                                    onAddToQueue = { onAddToQueue(song) },
                                    onAddToPlaylist = { onAddToPlaylist(song) },
                                    onShare = { onShare(song) },
                                    onOpenArtist = onOpenArtist
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumAction(label: String, primary: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (primary) NoxaLime else NoxaSurfaceSecondary)
            .border(1.dp, if (primary) NoxaLime else NoxaBorder, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(horizontal = 22.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (primary) Icons.Default.PlayArrow else Icons.Default.Shuffle,
            contentDescription = null,
            tint = if (primary) NoxaBlack else NoxaLime,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = if (primary) NoxaBlack else NoxaTextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
