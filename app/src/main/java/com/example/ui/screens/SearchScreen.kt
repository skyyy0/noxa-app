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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.example.ui.components.SongListItem
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

data class BrowseGenre(
    val name: String,
    val color: Color
)

/** Source filter chips above the results. */
private enum class SourceFilter(val label: String, val source: MusicSource?) {
    ALL("Semua", null),
    YOUTUBE("YouTube", MusicSource.YOUTUBE),
    DEEZER("Deezer", MusicSource.DEEZER),
    AUDIUS("Audius", MusicSource.AUDIUS)
}

@Composable
fun SearchScreen(
    searchQuery: String,
    searchResults: List<Song>,
    isSearching: Boolean,
    currentPlayingSongId: String?,
    onQueryChange: (String) -> Unit,
    onPerformSearch: (String) -> Unit,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    favoriteIds: Set<String>,
    onPlayNext: (Song) -> Unit,
    onAddToQueue: (Song) -> Unit,
    onAddToPlaylist: (Song) -> Unit,
    onShare: (Song) -> Unit,
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val genres = remember {
        listOf(
            BrowseGenre("Dewa 19", Color(0xFFE50914)),
            BrowseGenre("Sheila On 7", Color(0xFF1DB954)),
            BrowseGenre("Iwan Fals", Color(0xFFF59E0B)),
            BrowseGenre("NOAH", Color(0xFF8B5CF6)),
            BrowseGenre("Tulus", Color(0xFF06B6D4)),
            BrowseGenre("Justin Bieber", Color(0xFFEC4899)),
            BrowseGenre("Global Top 50", Color(0xFFEF4444)),
            BrowseGenre("Rock & Metal", Color(0xFF10B981))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .testTag("search_screen")
    ) {
        // Search Header
        Text(
            text = "Search",
            color = NoxaTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // Search Input Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            TextField(
                value = searchQuery,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        text = "Songs, artists, podcasts...",
                        color = NoxaTextTertiary,
                        fontSize = 14.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search icon",
                        tint = NoxaTextSecondary
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = NoxaTextSecondary
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = NoxaSurfaceElevated,
                    unfocusedContainerColor = NoxaSurfaceSecondary,
                    focusedIndicatorColor = NoxaLime,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = NoxaLime,
                    focusedTextColor = NoxaTextPrimary,
                    unfocusedTextColor = NoxaTextPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("search_input")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Source filter — the merged list spans several providers.
        var sourceFilter by remember { mutableStateOf(SourceFilter.ALL) }
        val filteredResults = remember(searchResults, sourceFilter) {
            sourceFilter.source?.let { source -> searchResults.filter { it.source == source } }
                ?: searchResults
        }

        if (searchResults.isNotEmpty() && !isSearching) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SourceFilter.entries) { filter ->
                    val isSelected = filter == sourceFilter
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isSelected) NoxaLime else NoxaSurfaceSecondary)
                            .border(
                                1.dp,
                                if (isSelected) NoxaLime else NoxaBorder,
                                CircleShape
                            )
                            .clickable { sourceFilter = filter }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                            .testTag("source_chip_${filter.label}")
                    ) {
                        Text(
                            text = filter.label,
                            color = if (isSelected) NoxaBlack else NoxaTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }

        // Body: Results or Browse Genres
        if (isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = NoxaLime,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "Mencari musik...",
                        color = NoxaTextSecondary,
                        fontSize = 14.sp
                    )
                }
            }
        } else if (searchQuery.isNotEmpty() && searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = "No results",
                        tint = NoxaTextTertiary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No results found for \"$searchQuery\"",
                        color = NoxaTextSecondary,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Check spelling or search for popular artists",
                        color = NoxaTextTertiary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (searchResults.isNotEmpty() && filteredResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Tidak ada hasil dari ${sourceFilter.label} untuk \"$searchQuery\"",
                    color = NoxaTextTertiary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else if (filteredResults.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp, start = 8.dp, end = 8.dp)
            ) {
                itemsIndexed(filteredResults, key = { _, song -> song.id }) { index, song ->
                    SongListItem(
                        song = song,
                        isPlaying = song.id == currentPlayingSongId,
                        isFavorite = favoriteIds.contains(song.id),
                        onPlay = { onPlaySong(song, filteredResults, index) },
                        onToggleFavorite = { onToggleFavorite(song) },
                        onPlayNext = { onPlayNext(song) },
                        onOpenArtist = onOpenArtist,
                        onAddToQueue = { onAddToQueue(song) },
                        onAddToPlaylist = { onAddToPlaylist(song) },
                        onShare = { onShare(song) },
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        } else {
            // Browse Genres Grid
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    text = "Explore Genres",
                    color = NoxaTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    items(genres) { genre ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(90.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(genre.color.copy(alpha = 0.25f))
                                .border(1.dp, genre.color.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                .clickable {
                                    onPerformSearch(genre.name)
                                }
                                .padding(12.dp)
                                .testTag("genre_card_${genre.name}"),
                            contentAlignment = Alignment.BottomStart
                        ) {
                            Text(
                                text = genre.name,
                                color = NoxaTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
