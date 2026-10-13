package com.example.ui.screens

import android.app.Activity
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.ArtistProfile
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import com.example.data.remote.HomeMusicSection
import com.example.ui.components.HeroSpotlightCard
import com.example.ui.components.noxaAppear
import com.example.ui.components.noxaClickable
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.data.auth.NoxaUser
import java.util.Calendar

@Composable
fun HomeScreen(
    heroSong: Song?,
    sections: List<HomeMusicSection>,
    pinnedSongs: List<Song> = emptyList(),
    /** Recently played songs — rendered as their own shelf. */
    recentSongs: List<Song> = emptyList(),
    popularArtists: List<ArtistProfile> = emptyList(),
    resumeSong: Song?,
    isLoading: Boolean,
    errorMessage: String?,
    currentUser: NoxaUser?,
    isGuestMode: Boolean,
    isSigningIn: Boolean,
    currentPlayingSongId: String?,
    onPlaySong: (Song, List<Song>, Int) -> Unit,
    onResume: () -> Unit,
    onDismissResume: () -> Unit,
    onSignInWithGoogle: (Activity) -> Unit,
    /** Opens the "Get Started" sign-in hub (Google / Email / Phone OTP). */
    onOpenGetStarted: () -> Unit = {},
    onRetry: () -> Unit,
    /** Plays the shuffled Mix Sky queue (pinned + favorites + recent). */
    onPlaySkyMix: () -> Unit = {},
    onOpenArtist: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Selamat pagi"
            in 12..16 -> "Selamat siang"
            else -> "Selamat malam"
        }
    }

    // Flattened once per feed load instead of on every hero tap.
    val heroQueue = remember(sections) { sections.firstOrNull()?.songs.orEmpty() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            // The screens below the status bar start here; without this the header
            // slid under the clock and the guest card looked cut off.
            .statusBarsPadding()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Top Header
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = greeting,
                        color = NoxaTextTertiary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (currentUser != null) (currentUser.displayName ?: "NOXA Music") else "NOXA Music",
                        color = NoxaTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (currentUser?.photoUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentUser.photoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Profile Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, NoxaLime, CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NoxaSurfaceSecondary)
                            .border(1.dp, NoxaLime, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            color = NoxaLime,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        // Continue listening — the queue restored after the app (or device) restarted.
        if (resumeSong != null) {
            item(key = "resume") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NoxaSurfaceElevated)
                        .border(1.dp, NoxaLime.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                        .clickable { onResume() }
                        .padding(12.dp)
                        .testTag("resume_card"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(resumeSong.artworkUrl)
                            .size(128, 128)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Lanjutkan mendengarkan",
                            color = NoxaLime,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = resumeSong.title,
                            color = NoxaTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = resumeSong.artist,
                            color = NoxaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NoxaLime),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Lanjutkan",
                            tint = NoxaBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onDismissResume) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Tutup",
                            tint = NoxaTextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Recommended Google Sign-in Banner for Guests
        if (currentUser == null) {
            item(key = "guest_banner") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NoxaSurfaceElevated)
                        .border(1.dp, NoxaBorder, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Guest",
                                tint = NoxaLime,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Mode Tamu Aktif",
                                    color = NoxaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Sign in to sync your library everywhere",
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }

                        // One entry point: the hub offers Google, Email and
                        // Phone (OTP) instead of firing Google directly.
                        Button(
                            onClick = onOpenGetStarted,
                            enabled = !isSigningIn,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NoxaLime,
                                contentColor = NoxaBlack
                            ),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("get_started_btn")
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = NoxaBlack,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Real loading / error state for the API-backed home feed.
        if (isLoading || errorMessage != null) {
            item(key = "state") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = NoxaLime,
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Memuat musik...",
                                color = NoxaTextSecondary,
                                fontSize = 14.sp
                            )
                        } else {
                            Text(
                                text = errorMessage ?: "",
                                color = NoxaTextSecondary,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onRetry,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = NoxaLime,
                                    contentColor = NoxaBlack
                                ),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(
                                    text = "Coba lagi",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // A. Hero Spotlight Card — "Blinding Lights" by The Weeknd.
        if (heroSong != null) {
            item(key = "hero") {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                    Text(
                        text = "Sorotan hari ini",
                        color = NoxaTextTertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    HeroSpotlightCard(
                        song = heroSong,
                        onPlay = {
                            val queue = if (heroQueue.isEmpty()) listOf(heroSong) else heroQueue
                            val index = queue.indexOfFirst { it.id == heroSong.id }.coerceAtLeast(0)
                            onPlaySong(heroSong, queue, index)
                        }
                    )
                }
            }
        }

        // B. Artis Populer — avatar shelf; each avatar opens the artist page.
        if (popularArtists.isNotEmpty()) {
            item(key = "popular_artists") {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Artis Populer",
                            color = NoxaTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ketuk untuk membuka profil artis",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        itemsIndexed(
                            items = popularArtists,
                            key = { _, artist -> artist.id },
                            contentType = { _, _ -> "artist_avatar" }
                        ) { _, artist ->
                            Column(
                                modifier = Modifier
                                    .width(72.dp)
                                    .clickable {
                                        onOpenArtist(artist.id.toString(), artist.name)
                                    }
                                    .testTag("popular_artist_${artist.id}"),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(artist.pictureUrl)
                                        .size(144, 144)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = artist.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = 1.5.dp,
                                            color = NoxaLime.copy(alpha = 0.65f),
                                            shape = CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artist.name,
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }

        // C. Pinned by Sky — the dedicated pin shelf with the quote.
        if (pinnedSongs.isNotEmpty()) {
            item(key = "pinned_shelf") {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = NoxaLime,
                                modifier = Modifier.size(19.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "Pinned by Sky",
                                color = NoxaTextPrimary,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            // Mix Sky: one shuffled queue from Sky's pins, the
                            // user's favorites and their recent listens.
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(NoxaLime.copy(alpha = 0.14f))
                                    .border(1.dp, NoxaLime.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .noxaClickable { onPlaySkyMix() }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                                    .testTag("mix_sky_btn"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shuffle,
                                    contentDescription = null,
                                    tint = NoxaLime,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "Mix Sky",
                                    color = NoxaLime,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Text(
                            text = "Rak khusus pilihan Sky",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        itemsIndexed(
                            items = pinnedSongs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "pinned_card" }
                        ) { index, song ->
                            PinnedShelfCard(
                                song = song,
                                quote = PinnedTracks.find(song)?.quote.orEmpty(),
                                isPlaying = song.id == currentPlayingSongId,
                                onClick = { onPlaySong(song, pinnedSongs, index) },
                                // Cards materialise one after another instead of
                                // popping in together — the iOS shelf feel.
                                modifier = Modifier.noxaAppear(delayMillis = index * 70)
                            )
                        }
                    }
                }
            }
        }

        // C2. Dengar Terakhir — quick jumps back into recent listens.
        if (recentSongs.isNotEmpty()) {
            item(key = "recent_shelf") {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "Dengar Terakhir",
                            color = NoxaTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Lanjutkan dari mana kamu berhenti",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(
                            items = recentSongs,
                            key = { _, song -> "recent_${song.id}" },
                            contentType = { _, _ -> "recent_card" }
                        ) { index, song ->
                            Column(
                                modifier = Modifier
                                    .width(96.dp)
                                    .noxaAppear(delayMillis = index * 40)
                                    .noxaClickable {
                                        onPlaySong(song, recentSongs, index)
                                    }
                                    .testTag("recent_card_${song.id}"),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(NoxaSurfaceElevated)
                                        .border(1.dp, NoxaBorder, RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.artworkUrl)
                                            .size(192, 192)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                                Text(
                                    text = song.title,
                                    color = NoxaTextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // D. Curated shelves: Dewa 19, Sheila On 7, Justin Bieber, and more.
        sections.forEach { section ->
            item(key = "section_${section.title}") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = section.title,
                            color = NoxaTextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = section.subtitle,
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        itemsIndexed(
                            items = section.songs,
                            key = { _, song -> song.id },
                            contentType = { _, _ -> "track_card" }
                        ) { index, song ->
                            val isPlaying = song.id == currentPlayingSongId
                            Column(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .noxaAppear(delayMillis = index * 50)
                                    .noxaClickable {
                                        onPlaySong(song, section.songs, index)
                                    }
                                    .testTag("section_song_${song.id}")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(NoxaSurfaceElevated)
                                        .border(
                                            width = if (isPlaying) 1.5.dp else 1.dp,
                                            color = if (isPlaying) NoxaLime else NoxaBorder,
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(song.artworkUrl)
                                            .size(280, 280)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = song.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    if (isPlaying) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(NoxaLime),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Playing",
                                                    tint = NoxaBlack,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }
                                        }
                                    }

                                    if (PinnedTracks.isPinned(song)) {
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .clip(RoundedCornerShape(5.dp))
                                                .background(NoxaLime)
                                                .padding(horizontal = 5.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = NoxaBlack,
                                                modifier = Modifier.size(9.dp)
                                            )
                                            Spacer(modifier = Modifier.width(3.dp))
                                            Text(
                                                text = "Pinned",
                                                color = NoxaBlack,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = song.title,
                                    color = if (isPlaying) NoxaLime else NoxaTextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                Text(
                                    text = song.artist,
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    // Shelf cards are narrow, so the artist name
                                    // itself is the entry point to their profile.
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable { onOpenArtist(song.artistId, song.artist) }
                                        .padding(vertical = 1.dp, horizontal = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A card on Sky's pin shelf: artwork with a vector star badge, the track — and the quote
 * that explains why it was pinned. Fixed width so several pins scroll in a row.
 */
@Composable
private fun PinnedShelfCard(
    song: Song,
    quote: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .width(280.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaSurfaceElevated)
            .border(
                width = if (isPlaying) 1.5.dp else 1.dp,
                color = NoxaLime.copy(alpha = if (isPlaying) 0.9f else 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .noxaClickable { onClick() }
            .padding(12.dp)
            .testTag("pinned_card_${song.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(song.artworkUrl)
                        .size(144, 144)
                        .crossfade(true)
                        .build(),
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(58.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, NoxaBorder, RoundedCornerShape(10.dp))
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(NoxaLime)
                        .padding(3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Pinned by Sky",
                        tint = NoxaBlack,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    color = if (isPlaying) NoxaLime else NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    color = NoxaTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NoxaLime),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Putar lagu pinned",
                    tint = NoxaBlack,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        if (quote.isNotBlank()) {
            Text(
                text = "\u201C$quote\u201D",
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
        }
    }
}
