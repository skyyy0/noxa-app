package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.PlaylistCover
import com.example.data.local.PlaylistCoverStore
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaHaze
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * **Studio Cover Playlist** — cover art generated on the device.
 *
 * No server, no upload: the palette and the shapes come from the playlist name
 * and a seed, so the same playlist looks identical on every launch and every
 * device. Four styles, all in NOXA's lime/haze/purple family:
 *
 *  0 Aurora — soft overlapping gradients
 *  1 Tape   — retro horizontal bands
 *  2 Prism  — a rotated light beam
 *  3 Pulse  — concentric rings off-centre
 */
@Composable
fun PlaylistCoverArt(
    name: String,
    cover: PlaylistCover,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    showInitials: Boolean = true
) {
    val random = remember(cover.seed, cover.style) { Random(cover.seed) }
    val palette = remember(cover.seed) { paletteFor(cover.seed) }
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .clip(shape)
            .background(NoxaBlack)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            when (cover.style) {
                1 -> drawTape(palette, random)
                2 -> drawPrism(palette, random)
                3 -> drawPulse(palette, random)
                else -> drawAurora(palette, random)
            }
        }
        if (showInitials) {
            Text(
                text = initials(name),
                color = NoxaTextPrimary.copy(alpha = 0.92f),
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
            )
        }
    }
}

/** Two-letter monogram from the playlist name (max two words). */
internal fun initials(name: String): String {
    val words = name.trim().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return "N"
    return words.take(2).joinToString("") { it.first().uppercase() }
}

private fun paletteFor(seed: Int): List<Color> {
    val random = Random(seed)
    val extras = listOf(NoxaHaze, NoxaPurple, Color(0xFF35E0C0), Color(0xFFFF8A3D))
    val first = extras[random.nextInt(extras.size)]
    val second = extras[random.nextInt(extras.size)]
    return listOf(NoxaLime, first, second)
}

private fun DrawScope.drawAurora(palette: List<Color>, random: Random) {
    drawRect(Color(0xFF0C0D10))
    repeat(3) { index ->
        val color = palette[index % palette.size]
        val cx = size.width * (0.2f + random.nextFloat() * 0.6f)
        val cy = size.height * (0.2f + random.nextFloat() * 0.6f)
        drawCircle(
            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(cx, cy),
                radius = size.minDimension * (0.55f + random.nextFloat() * 0.35f)
            ),
            radius = size.minDimension,
            center = Offset(cx, cy)
        )
    }
}

private fun DrawScope.drawTape(palette: List<Color>, random: Random) {
    drawRect(Color(0xFF101114))
    val bands = 7
    val bandHeight = size.height / bands
    repeat(bands) { index ->
        val color = palette[index % palette.size]
        val offset = (random.nextFloat() - 0.5f) * size.width * 0.12f
        drawRect(
            color = color.copy(alpha = 0.30f + random.nextFloat() * 0.25f),
            topLeft = Offset(offset, index * bandHeight),
            size = Size(size.width, bandHeight * 0.82f)
        )
    }
    repeat(3) {
        drawLine(
            color = Color.White.copy(alpha = 0.18f),
            start = Offset(0f, size.height * random.nextFloat()),
            end = Offset(size.width, size.height * random.nextFloat()),
            strokeWidth = 2f
        )
    }
}

private fun DrawScope.drawPrism(palette: List<Color>, random: Random) {
    drawRect(Color(0xFF0A0B0D))
    rotate(degrees = 18f + random.nextFloat() * 26f) {
        val beam = (size.width / 3f).coerceAtLeast(24f)
        palette.forEachIndexed { index, color ->
            drawRect(
                color = color.copy(alpha = 0.5f),
                topLeft = Offset(index * beam * 0.9f - size.width * 0.2f, -size.height),
                size = Size(beam, size.height * 3f)
            )
        }
    }
    drawCircle(
        color = Color.Black.copy(alpha = 0.25f),
        radius = size.minDimension * 0.42f,
        center = Offset(size.width * 0.7f, size.height * 0.32f),
        style = Stroke(width = size.minDimension * 0.06f)
    )
}

private fun DrawScope.drawPulse(palette: List<Color>, random: Random) {
    drawRect(Color(0xFF0B0C0F))
    val center = Offset(
        size.width * (0.25f + random.nextFloat() * 0.5f),
        size.height * (0.3f + random.nextFloat() * 0.4f)
    )
    repeat(6) { index ->
        val color = palette[index % palette.size]
        val radius = size.minDimension * (0.12f + index * 0.11f)
        drawCircle(
            color = color.copy(alpha = 0.42f - index * 0.05f),
            radius = radius,
            center = center,
            style = Stroke(width = size.minDimension * 0.035f)
        )
    }
    repeat(24) { index ->
        val angle = index / 24f * 6.283f
        val r = size.minDimension * 0.45f
        drawCircle(
            color = Color.White.copy(alpha = 0.20f),
            radius = 1.6f,
            center = Offset(center.x + cos(angle) * r, center.y + sin(angle) * r)
        )
    }
}

/**
 * The studio sheet: pick a style, roll a new palette, done. Changes are applied
 * live to the preview above so the user sees exactly what they will get.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistCoverStudioSheet(
    playlistName: String,
    initial: PlaylistCover,
    onDismiss: () -> Unit,
    onSave: (PlaylistCover) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var style by remember { mutableIntStateOf(initial.style) }
    var seed by remember { mutableIntStateOf(initial.seed) }
    val preview = PlaylistCover(style, seed)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NoxaBlack,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp)
        ) {
            Text(
                "Studio Cover",
                color = NoxaTextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Cover dibuat di perangkat ini — tanpa unggah, tanpa server.",
                color = NoxaTextTertiary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            PlaylistCoverArt(
                name = playlistName,
                cover = preview,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlaylistCoverStore.STYLE_LABELS.forEachIndexed { index, label ->
                    CoverStyleChip(
                        label = label,
                        selected = style == index,
                        onClick = { style = index },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NoxaSurfaceSecondary)
                    .border(1.dp, NoxaBorder, RoundedCornerShape(16.dp))
                    .noxaTap { seed = Random.nextInt(1, Int.MAX_VALUE) }
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Casino,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.size(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Acak palet", color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Warna baru dari nama playlist", color = NoxaTextTertiary, fontSize = 11.sp)
                }
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(NoxaLime)
                    .noxaTap { onSave(preview) }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Simpan cover",
                    color = NoxaBlack,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun CoverStyleChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) NoxaLime.copy(alpha = 0.16f) else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (selected) NoxaLime.copy(alpha = 0.5f) else NoxaBorder,
                RoundedCornerShape(12.dp)
            )
            .noxaTap(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (selected) NoxaLime else NoxaTextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
