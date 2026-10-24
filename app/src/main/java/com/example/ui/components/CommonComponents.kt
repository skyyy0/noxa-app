package com.example.ui.components

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VolumeOff
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
import com.example.ui.theme.NoxaError
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
    /** v2.0.0 — current star rating (0..5) shown in the quick menu. */
    rating: Int = 0,
    /** v2.0.0 — "Beri bintang" star row; null hides it. */
    onRate: ((Song, Int) -> Unit)? = null,
    /** v2.0.0 — "Jangan putar lagi (lagu)"; null hides it. */
    onMuteSong: (() -> Unit)? = null,
    /** v2.0.0 — "Jangan putar lagi (artis)"; null hides it. */
    onMuteArtist: (() -> Unit)? = null,
    /**
     * Optional third line beneath artist • duration (e.g. the artist's total
     * audience on the artist page). When [metaHighlight] is true the number is
     * drawn in lime, otherwise as quiet secondary text. Empty/blank hides it.
     */
    metaText: String? = null,
    metaHighlight: Boolean = false,
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

        Box {
            AsyncImage(
                model = song.artworkUrl,
                contentDescription = "Track Artwork",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(0.5.dp, NoxaBorder, RoundedCornerShape(8.dp))
            )
            // Spotify-style "now playing" bars over the artwork corner — the
            // glanceable sign that THIS row is what you are hearing.
            if (isPlaying) {
                NowPlayingBars(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(3.dp)
                )
            }
        }

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
            if (!metaText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metaText,
                    color = if (metaHighlight) NoxaLime.copy(alpha = 0.85f) else NoxaTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = if (metaHighlight) FontWeight.SemiBold else FontWeight.Normal,
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
        if (onPlayNext != null || onAddToQueue != null || onAddToPlaylist != null ||
            onShare != null || onRate != null || onMuteSong != null || onMuteArtist != null
        ) {
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
                    // --------------------------------------- v2.0.0: bintang & mute
                    if (onRate != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Beri bintang",
                                color = NoxaTextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            (1..5).forEach { star ->
                                IconButton(
                                    onClick = {
                                        // Tap the active star again to clear.
                                        onRate(song, if (rating == star) 0 else star)
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "$star bintang",
                                        tint = if (star <= rating) NoxaLime else NoxaTextTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    if (onMuteSong != null) {
                        DropdownMenuItem(
                            text = { Text("Jangan putar lagu ini", color = NoxaTextPrimary, fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.VolumeOff, contentDescription = null, tint = NoxaError)
                            },
                            onClick = {
                                menuOpen = false
                                onMuteSong()
                            }
                        )
                    }
                    if (onMuteArtist != null) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "Jangan putar ${song.artist}",
                                    color = NoxaTextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = {
                                Icon(Icons.Default.VolumeOff, contentDescription = null, tint = NoxaError)
                            },
                            onClick = {
                                menuOpen = false
                                onMuteArtist()
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * The **NOXA verified badge** — a purple rosette (scalloped seal, the same
 * silhouette language as platform verification badges) with a crisp white
 * tick. Replaces the earlier plain disc + loud pulsing halo, which read as a
 * blurry blob at 15 dp. The glow now breathes gently instead of blinking.
 * Worn by NOXA Support and developer identities.
 */
@Composable
fun VerifiedBadgePurple(
    size: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "badge_glow")
    val glow by infinite.animateFloat(
        initialValue = 0.30f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badge_glow_alpha"
    )
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Soft, slow-breathing halo — present but never flashy.
        Box(
            modifier = Modifier
                .size(size * 1.5f)
                .background(
                    Brush.radialGradient(
                        listOf(NoxaPurple.copy(alpha = glow), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Canvas(modifier = Modifier.size(size)) {
            val r = this.size.minDimension / 2f
            val center = this.size / 2f

            // Scalloped rosette: 12 gentle bumps around the base circle.
            val scallops = 12
            val base = r * 0.94f
            val bulge = r * 0.10f
            val path = Path()
            for (i in 0 until scallops) {
                val a0 = (2.0 * Math.PI * i / scallops)
                val a1 = (2.0 * Math.PI * (i + 1) / scallops)
                val p0x = center.width + (Math.cos(a0) * base).toFloat()
                val p0y = center.height + (Math.sin(a0) * base).toFloat()
                val p1x = center.width + (Math.cos(a1) * base).toFloat()
                val p1y = center.height + (Math.sin(a1) * base).toFloat()
                val am = (a0 + a1) / 2.0
                val cpx = center.width + (Math.cos(am) * (base + bulge * 2f)).toFloat()
                val cpy = center.height + (Math.sin(am) * (base + bulge * 2f)).toFloat()
                if (i == 0) path.moveTo(p0x, p0y) else path.lineTo(p0x, p0y)
                path.quadraticBezierTo(cpx, cpy, p1x, p1y)
            }
            path.close()

            // Diagonal gradient fill — bright purple top-left to deep purple.
            drawPath(
                path,
                brush = Brush.linearGradient(
                    listOf(NoxaPurple, NoxaPurpleDeep),
                    start = androidx.compose.ui.geometry.Offset(0f, 0f),
                    end = androidx.compose.ui.geometry.Offset(this.size.width, this.size.height)
                )
            )
            // Hairline inner ring for a finished, jewel-like edge.
            drawCircle(
                color = Color.White.copy(alpha = 0.22f),
                radius = base * 0.78f,
                style = Stroke(width = r * 0.055f)
            )
            // Crisp white tick, optically centred.
            val s = this.size.width
            val check = Path().apply {
                moveTo(s * 0.31f, s * 0.52f)
                lineTo(s * 0.44f, s * 0.65f)
                lineTo(s * 0.70f, s * 0.37f)
            }
            drawPath(
                check,
                color = Color.White,
                style = Stroke(
                    width = s * 0.115f,
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

/**
 * The three little dancing lime bars Spotify paints on whatever is currently
 * playing — a glanceable "this is what you are hearing" mark for song rows
 * and shelves. Each bar breathes at its own pace; heights are animated once
 * and only rendered while [isPlaying] is true upstream.
 */
@Composable
fun NowPlayingBars(
    modifier: Modifier = Modifier,
    barColor: Color = NoxaLime,
    barWidth: Dp = 2.5.dp,
    maxHeight: Dp = 12.dp
) {
    val transition = rememberInfiniteTransition(label = "now_playing_bars")
    val height1 by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val height2 by transition.animateFloat(
        initialValue = 0.75f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(420, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val height3 by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(NoxaBlack.copy(alpha = 0.72f))
            .padding(horizontal = 3.dp, vertical = 2.5.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        listOf(height1, height2, height3).forEach { fraction ->
            Box(
                modifier = Modifier
                    .width(barWidth)
                    .height(maxHeight * fraction)
                    .background(barColor, RoundedCornerShape(1.dp))
            )
        }
    }
}
