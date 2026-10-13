package com.example.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.Song
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.FormatUtils

@Composable
fun CategoryChipsRow(
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories) { category ->
            val isSelected = category == selectedCategory
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (isSelected) NoxaLime else NoxaSurfaceSecondary)
                    .border(
                        1.dp,
                        if (isSelected) NoxaLime else NoxaBorder,
                        CircleShape
                    )
                    .clickable { onSelectCategory(category) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("category_chip_$category")
            ) {
                Text(
                    text = category,
                    color = if (isSelected) NoxaBlack else NoxaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun HeroSpotlightCard(
    song: Song,
    isPlaying: Boolean = false,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, if (isPlaying) NoxaLime else NoxaBorder, RoundedCornerShape(20.dp))
            .clickable { onPlay() }
            .testTag("hero_spotlight_card")
    ) {
        val context = LocalContext.current
        AsyncImage(
            // Capped decode size: the card is 200dp tall, so decoding a
            // 1000x1000 cover for it was wasted work on every home load.
            model = ImageRequest.Builder(context)
                .data(song.artworkUrl)
                .size(512, 512)
                .crossfade(true)
                .build(),
            contentDescription = "Spotlight Artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f),
                            Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // Spotlight Content
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(NoxaLime)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "FEATURED SPOTLIGHT",
                    color = NoxaBlack,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = song.title,
                color = NoxaTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song.artist,
                color = NoxaTextSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Play Button FAB on bottom right
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(NoxaLime)
                .clickable { onPlay() }
                .testTag("hero_play_btn"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause Spotlight" else "Play Spotlight",
                tint = NoxaBlack,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SongListItem(
    song: Song,
    rank: Int? = null,
    isPlaying: Boolean = false,
    isFavorite: Boolean = false,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onAddToPlaylist: (() -> Unit)? = null,
    onShare: (() -> Unit)? = null,
    /** Opens the artist profile; the artist name becomes tappable when set. */
    onOpenArtist: ((String, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            // Tap plays; long-press opens the same quick menu as the ⋮ button —
            // the gesture Spotify users expect on every song row.
            .combinedClickable(
                onLongClick = { menuOpen = true },
                onClick = { onPlay() }
            )
            .padding(vertical = 8.dp, horizontal = 12.dp)
            .testTag("song_item_${song.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                text = "$rank",
                color = if (isPlaying) NoxaLime else NoxaTextTertiary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(28.dp)
            )
        }

        AsyncImage(
            model = song.artworkUrl,
            contentDescription = "Track Artwork",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(0.5.dp, NoxaBorder, RoundedCornerShape(8.dp))
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                color = if (isPlaying) NoxaLime else NoxaTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Artist name doubles as the entry point to the artist profile.
                Text(
                    text = song.artist,
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (onOpenArtist != null) {
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable { onOpenArtist(song.artistId, song.artist) }
                            .padding(vertical = 1.dp, horizontal = 2.dp)
                    } else {
                        Modifier
                    }
                )
                Text(
                    // Deezer tracks resolve to full-length audio at play time
                    // (YouTube matcher + on-device extractor), so the old
                    // "PREVIEW 30s" suffix would now be misleading.
                    text = " • " + FormatUtils.formatDuration(song.durationMs),
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp).testTag("fav_btn_${song.id}")
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = "Toggle Favorite",
                tint = if (isFavorite) NoxaLime else NoxaTextTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        // Overflow actions: queue management, playlists and sharing.
        if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null || onShare != null) {
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("more_btn_${song.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Menu lain",
                        tint = NoxaTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = NoxaSurfaceElevated
                ) {
                    if (onPlayNext != null) {
                        DropdownMenuItem(
                            text = { Text("Putar berikutnya", color = NoxaTextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.QueuePlayNext, contentDescription = null, tint = NoxaLime)
                            },
                            onClick = {
                                menuOpen = false
                                onPlayNext()
                            }
                        )
                    }
                    if (onAddToQueue != null) {
                        DropdownMenuItem(
                            text = { Text("Tambah ke antrian", color = NoxaTextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = NoxaLime)
                            },
                            onClick = {
                                menuOpen = false
                                onAddToQueue()
                            }
                        )
                    }
                    if (onAddToPlaylist != null) {
                        DropdownMenuItem(
                            text = { Text("Tambah ke playlist", color = NoxaTextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = NoxaLime)
                            },
                            onClick = {
                                menuOpen = false
                                onAddToPlaylist()
                            }
                        )
                    }
                    if (onShare != null) {
                        DropdownMenuItem(
                            text = { Text("Bagikan", color = NoxaTextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = NoxaLime)
                            },
                            onClick = {
                                menuOpen = false
                                onShare()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The **NOXA Support verified badge** — a purple, hand-drawn seal with a soft
 * glow, deliberately different from every blue artist tick and from the lime
 * brand accents. Only NOXA Support / staff accounts wear it.
 */
@Composable
fun VerifiedBadgePurple(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "badge_glow")
    val glow by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_glow_alpha"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Halo behind the seal.
        Box(
            modifier = Modifier
                .size(size * 1.45f)
                .background(
                    Brush.radialGradient(
                        listOf(NoxaPurple.copy(alpha = 0.55f * glow), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val center = this.size / 2f
            // Rounded seal body with a gradient from bright to deep purple.
            drawCircle(
                brush = Brush.linearGradient(
                    listOf(NoxaPurple, NoxaPurpleDeep)
                ),
                radius = r
            )
            // Inner rim for a jewel-like finish.
            drawCircle(
                color = Color.White.copy(alpha = 0.35f),
                radius = r * 0.86f,
                style = Stroke(width = r * 0.10f)
            )
            // White check mark.
            val s = this.size.width
            val check = Path().apply {
                moveTo(s * 0.30f, s * 0.52f)
                lineTo(s * 0.44f, s * 0.66f)
                lineTo(s * 0.72f, s * 0.36f)
            }
            drawPath(
                check,
                color = Color.White,
                style = Stroke(
                    width = s * 0.11f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

/**
 * Inline "name + purple badge" header used by the NOXA Support chat and the
 * staff replies inside the admin console.
 */
@Composable
fun SupportIdentityRow(
    name: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    nameColor: Color = NoxaTextPrimary,
    badgeSize: Dp = 15.dp
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = name,
            color = nameColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(5.dp))
        VerifiedBadgePurple(size = badgeSize)
        if (subtitle != null) {
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = subtitle,
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
