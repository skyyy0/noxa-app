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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
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
import com.example.ui.util.FormatUtils
import com.example.ui.viewmodel.ArtistUiState

/** Biru = artis terverifikasi biasa, merah = artis besar (listener ≥ 1 jt). */
@Composable
fun VerifiedArtistBadge(
    isBigArtist: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Int = 18
) {
    Icon(
        imageVector = Icons.Default.Verified,
        contentDescription = if (isBigArtist) "Artis besar terverifikasi" else "Terverifikasi",
        tint = if (isBigArtist) Color(0xFFE53935) else Color(0xFF1D9BF0),
        modifier = modifier.size(sizeDp.dp)
    )
}

/**
 * Artist profile page: photo, verified check, listener count, tappable album
 * shelf (each opens the album page) and the artist's top tracks in order.
 */
@Composable
fun ArtistScreen(
    state: ArtistUiState,
    favoriteIds: Set<String>,
    currentPlayingSongId: String?,
    onBack: () -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String, String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
    ) {
        when (state) {
            is ArtistUiState.Loading -> {
                CircularProgressIndicator(
                    color = NoxaLime,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(34.dp)
                )
            }

            is ArtistUiState.Error -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = NoxaTextSecondary,
                        fontSize = 14.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

            is ArtistUiState.Ready -> {
                val profile = state.profile
                val topSongs = state.topSongs
                val albums = state.albums

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
                            text = "Artis",
                            color = NoxaTextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Header: photo + name + verified check + listener count.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(profile.pictureUrl)
                                .size(400, 400)
                                .crossfade(true)
                                .build(),
                            contentDescription = profile.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(132.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .border(1.dp, NoxaBorder, RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = profile.name,
                                    color = NoxaTextPrimary,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                VerifiedArtistBadge(
                                    isBigArtist = profile.isBigArtist,
                                    sizeDp = 20
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = buildString {
                                    append(FormatUtils.formatCount(profile.fanCount))
                                    append(" pendengar")
                                    if (profile.albumCount > 0) {
                                        append(" • ")
                                        append(profile.albumCount)
                                        append(" rilisan")
                                    }
                                },
                                color = NoxaTextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Play everything: the top-track list becomes the queue.
                    if (topSongs.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(NoxaLime)
                                    .clickable { onPlaySong(topSongs[0], topSongs, 0) }
                                    .padding(horizontal = 26.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = NoxaBlack,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Putar semua",
                                    color = NoxaBlack,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Discography shelf — each card opens the album page.
                    if (albums.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        SectionTitle("Album")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 10.dp)
                        ) {
                            items(
                                items = albums,
                                key = { it.id }
                            ) { album ->
                                AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
                            }
                        }
                    }

                    // Top tracks.
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionTitle("Lagu Teratas")
                    if (topSongs.isEmpty()) {
                        Text(
                            text = "Belum ada lagu untuk artis ini.",
                            color = NoxaTextTertiary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    } else {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            topSongs.forEachIndexed { index, song ->
                                SongListItem(
                                    song = song,
                                    rank = index + 1,
                                    isPlaying = song.id == currentPlayingSongId,
                                    isFavorite = favoriteIds.contains(song.id),
                                    onPlay = { onPlaySong(song, topSongs, index) },
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
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = NoxaTextPrimary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun AlbumCard(album: AlbumInfo, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(album.coverUrl)
                .size(320, 320)
                .crossfade(true)
                .build(),
            contentDescription = album.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, NoxaBorder, RoundedCornerShape(10.dp))
                .background(NoxaSurfaceSecondary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = album.title,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = buildString {
                if (album.releaseYear.isNotBlank()) {
                    append(album.releaseYear)
                    if (album.trackCount > 0) append(" • ")
                }
                if (album.trackCount > 0) {
                    append(album.trackCount)
                    append(" lagu")
                }
            }.ifEmpty { "Album" },
            color = NoxaTextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
