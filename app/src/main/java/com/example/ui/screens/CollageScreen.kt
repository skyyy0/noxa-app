package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Song
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaRadius
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.CollageRenderer
import com.example.ui.util.ShareCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * **Noxa Kolase** (v3.7.5).
 *
 * Pick four songs, pick a photo, get a story poster with four floating player
 * cards and a NOXA credit. Everything is rendered on-device by
 * [CollageRenderer]; the preview here uses the same arrangement so there is no
 * surprise between what you compose and what lands in the gallery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollageScreen(
    songs: List<Song>,
    onBack: () -> Unit,
    onNotice: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val slots = remember(songs) {
        mutableStateOf(
            listOf(
                songs.getOrNull(0),
                songs.getOrNull(1),
                songs.getOrNull(2),
                songs.getOrNull(3)
            )
        )
    }
    var background by remember { mutableStateOf<String?>(null) }
    var pickingSlot by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            background = uri.toString()
        }
    }

    fun export(share: Boolean) {
        if (busy) return
        val chosen = slots.value.filterNotNull()
        if (chosen.isEmpty()) {
            onNotice("Pilih minimal satu lagu dulu.")
            return
        }
        busy = true
        scope.launch {
            val bitmap = CollageRenderer.render(
                context = context,
                songs = slots.value,
                backgroundUri = background
            )
            val result = if (bitmap == null) {
                ShareCardRenderer.ShareResult.FAILED
            } else if (share) {
                withContext(Dispatchers.IO) {
                    CollageRenderer.saveAndShare(
                        context = context,
                        bitmap = bitmap,
                        baseName = "kolase",
                        text = "Kolase lagu dari NOXA Music",
                        title = "Bagikan Noxa Kolase"
                    )
                }
            } else {
                withContext(Dispatchers.IO) {
                    val saved = ShareCardRenderer.saveToGallery(context, bitmap, "Kolase")
                    if (saved != null) {
                        ShareCardRenderer.ShareResult.SAVED_ONLY
                    } else {
                        ShareCardRenderer.ShareResult.FAILED
                    }
                }
            }
            busy = false
            onNotice(
                when (result) {
                    ShareCardRenderer.ShareResult.SHARED -> "Kolase siap dibagikan."
                    ShareCardRenderer.ShareResult.SAVED_ONLY ->
                        "Kolase tersimpan di galeri (Pictures/NOXA)."
                    ShareCardRenderer.ShareResult.FAILED ->
                        "Kolase gagal dibuat. Coba pilih foto lain atau kurangi ukuran fotonya."
                }
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .testTag("collage_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ------------------------------------------------------------ header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 20.dp, top = 8.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(NoxaSurfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Kembali",
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Noxa Kolase",
                        color = NoxaTextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.6).sp
                    )
                    Text(
                        text = "Empat lagu + fotomu, jadi satu poster",
                        color = NoxaTextTertiary,
                        fontSize = 11.5.sp
                    )
                }
            }

            // ------------------------------------------------------------ poster
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .aspectRatio(0.72f)
                    .clip(RoundedCornerShape(NoxaRadius.xl))
                    .background(Color(0xFF0B0C0E))
                    .testTag("collage_preview")
            ) {
                val posterWidth = maxWidth
                val posterHeight = maxHeight

                if (background != null) {
                    AsyncImage(
                        model = background,
                        contentDescription = "Foto latar kolase",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.55f))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF1A1F0E),
                                        Color(0xFF0A0B0C),
                                        Color(0xFF12161C)
                                    )
                                )
                            )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    NoxaLime.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                val cardWidth = posterWidth * 0.43f
                val positions = listOf(
                    Triple(maxWidth * 0.02f, maxHeight * 0.10f, -10f),
                    Triple(maxWidth * 0.55f, maxHeight * 0.08f, 9f),
                    Triple(maxWidth * 0.03f, maxHeight * 0.55f, 8f),
                    Triple(maxWidth * 0.53f, maxHeight * 0.54f, -8f)
                )

                slots.value.forEachIndexed { index, song ->
                    val (x, y, rotation) = positions[index]
                    CollageCard(
                        song = song,
                        width = cardWidth,
                        rotation = rotation,
                        onClick = { pickingSlot = index },
                        modifier = Modifier.offset(x = x, y = y)
                    )
                }

                // Footer credit — the same mark the exported poster carries.
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(NoxaLime),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "N",
                            color = NoxaBlack,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Dibuat dengan NOXA",
                        color = NoxaTextPrimary.copy(alpha = 0.85f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("collage_pick_photo")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Image,
                            contentDescription = null,
                            tint = NoxaTextPrimary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (background == null) "Pilih foto" else "Ganti foto",
                            color = NoxaTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Text(
                text = "Ketuk kartu untuk memilih lagu. Urutannya bebas — yang kosong " +
                    "tetap tampil sebagai slot abu-abu.",
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // ----------------------------------------------------------- actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(NoxaLime)
                        .clickable(enabled = !busy) { export(share = false) }
                        .testTag("collage_save"),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = NoxaBlack,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = "Simpan ke galeri",
                            color = NoxaBlack,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(NoxaSurfaceSecondary)
                        .clickable(enabled = !busy) { export(share = true) }
                        .testTag("collage_share"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Bagikan kolase",
                        tint = NoxaTextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // ---------------------------------------------------------- song picker
        if (pickingSlot != null) {
            ModalBottomSheet(
                onDismissRequest = { pickingSlot = null },
                containerColor = NoxaSurfaceElevated,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Pilih lagu untuk slot ${(pickingSlot ?: 0) + 1}",
                            color = NoxaTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val index = pickingSlot ?: return@IconButton
                            slots.value = slots.value.toMutableList().apply { set(index, null) }
                            pickingSlot = null
                        }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Kosongkan slot",
                                tint = NoxaTextTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (songs.isEmpty()) {
                        Text(
                            text = "Belum ada lagu di koleksi. Favoritkan beberapa lagu dulu.",
                            color = NoxaTextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 18.dp)
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            items(songs.take(80), key = { it.id }) { song ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            val index = pickingSlot ?: return@clickable
                                            slots.value = slots.value.toMutableList().apply {
                                                set(index, song)
                                            }
                                            pickingSlot = null
                                        }
                                        .padding(vertical = 7.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = song.artworkUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = song.displayTitle,
                                            color = NoxaTextPrimary,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = song.displayArtist,
                                            color = NoxaTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * One mini player card in the composition. Scaled down from the exported
 * geometry, but the same parts: artwork, tag, title, artist, heart, playhead.
 */
@Composable
private fun CollageCard(
    song: Song?,
    width: androidx.compose.ui.unit.Dp,
    rotation: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(width)
            .graphicsLayer { rotationZ = rotation }
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0xF0141518))
            .clickable { onClick() }
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(NoxaSurfaceSecondary),
            contentAlignment = Alignment.Center
        ) {
            if (song != null) {
                AsyncImage(
                    model = song.artworkUrl,
                    contentDescription = song.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Slot kosong",
                    tint = NoxaTextTertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "NOXA",
            color = NoxaLime,
            fontSize = 7.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            text = song?.displayTitle ?: "Pilih lagu",
            color = NoxaTextPrimary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = song?.displayArtist ?: "Ketuk untuk memilih",
            color = NoxaTextSecondary,
            fontSize = 8.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.White.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.38f)
                    .height(2.dp)
                    .background(NoxaTextPrimary.copy(alpha = 0.7f))
            )
        }
    }
}
