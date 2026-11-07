package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.ai.AiChatMessage
import com.example.data.ai.AiFlavor
import com.example.data.ai.AiModel
import com.example.data.ai.AiModelStatus
import com.example.data.ai.AiQuotaState
import com.example.data.ai.AiTier
import com.example.data.local.PlaylistCover
import com.example.ui.components.PlaylistCoverArt
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaHaze
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaLimeDark
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

private data class AiSuggestion(val title: String, val prompt: String, val hint: String)

private val AiSuggestions = listOf(
    AiSuggestion("Putar lagu", "Putar Dewa 19 Kangen", "langsung diputar"),
    AiSuggestion("Bikin playlist", "Buat playlist fokus kerja", "20 lagu, rapi"),
    AiSuggestion("Antre lagu", "Antrekan lagu galau Indonesia", "masuk antrean"),
    AiSuggestion("Atur suasana", "Set equalizer Bass lalu putar lagu santai", "EQ + musik")
)

/**
 * **NOXA AI** — chat asisten musik, v3.5.5.
 *
 * Layout rebuilt with iOS-style layers:
 *  - a frosted header that owns the status-bar inset (this is the fix for the
 *    old bug where the welcome bubble scrolled into the clock),
 *  - a hero welcome panel with quick-action tiles instead of a plain bubble,
 *  - floating capsules for bubbles, a typing indicator with bouncing dots,
 *  - a model pill in the header (glm-5.3-flash / gpt-6-luna / deepseek),
 *  - a rich result card when the assistant builds a playlist, with its
 *    generated cover and a shortcut into the playlist.
 *
 * Insets are applied per-region — header: status bar, composer: IME + nav bar —
 * so the keyboard can no longer push the whole screen and leave a dead gap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    messages: List<AiChatMessage>,
    isThinking: Boolean,
    error: String?,
    playingSongId: String?,
    selectedModel: AiModel,
    modelStatuses: List<AiModelStatus>,
    quotas: Map<AiModel, AiQuotaState> = emptyMap(),
    activeFlavor: AiFlavor? = null,
    onSelectFlavor: (AiFlavor?) -> Unit = {},
    onSelectModel: (AiModel) -> Unit,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onPlaySongMessage: (AiChatMessage) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }
    var showModels by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size, isThinking) {
        val last = messages.size + (if (isThinking) 1 else 0)
        if (last > 0) listState.animateScrollToItem(last - 1)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // The keyboard inset belongs to the whole screen, not to the
            // composer row: padding the row alone made it taller than the
            // screen and pushed the header off the top (the "layar naik"
            // bug). Here the column simply re-measures in a shorter box.
            .imePadding()
            .background(NoxaBlack)
    ) {
        // Ambient wash behind the header — depth without a flat black wall.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(NoxaLime.copy(alpha = 0.10f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .align(Alignment.Center)
        ) {
            AiHeader(
                selectedModel = selectedModel,
                modelStatuses = modelStatuses,
                quota = quotas[selectedModel],
                onBack = onBack,
                onClear = onClearChat,
                onOpenModels = { showModels = true }
            )

            error?.let { message ->
                Text(
                    text = message,
                    color = Color(0xFFFF6B61),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (messages.isEmpty() && !isThinking) {
                    item(key = "welcome") {
                        AiWelcomeHero(
                            modelLabel = selectedModel.label,
                            onSuggestion = { draft = it }
                        )
                    }
                }
                itemsIndexed(
                    messages,
                    key = { index, message -> "$index-${message.fromUser}" }
                ) { index, message ->
                    AiMessageRow(
                        message = message,
                        isPlaying = message.songId != null && message.songId == playingSongId,
                        typewriter = !message.fromUser && index == messages.lastIndex,
                        onPlaySong = { onPlaySongMessage(message) },
                        onOpenPlaylist = { id -> onOpenPlaylist(id) }
                    )
                }
                if (isThinking) {
                    item(key = "thinking") { AiThinkingRow() }
                }
            }

            AiComposer(
                value = draft,
                onValueChange = { draft = it },
                enabled = !isThinking,
                flavors = AiFlavor.forModel(selectedModel),
                activeFlavor = activeFlavor,
                onToggleFlavor = { flavor ->
                    onSelectFlavor(if (activeFlavor == flavor) null else flavor)
                },
                onSend = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !isThinking) {
                        draft = ""
                        onSend(text)
                    }
                }
            )
        }
    }

    if (showModels) {
        AiModelSheet(
            selected = selectedModel,
            statuses = modelStatuses,
            quotas = quotas,
            onSelect = {
                onSelectModel(it)
                showModels = false
            },
            onDismiss = { showModels = false }
        )
    }
}

/**
 * Frosted header. Owns `statusBarsPadding()` so nothing can ever be drawn on
 * top of the clock/battery — the v3.3.3 overlap bug.
 */
@Composable
private fun AiHeader(
    selectedModel: AiModel,
    modelStatuses: List<AiModelStatus>,
    quota: AiQuotaState?,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onOpenModels: () -> Unit
) {
    val down = modelStatuses.firstOrNull { it.model == selectedModel && !it.available }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .background(
                Brush.verticalGradient(
                    listOf(NoxaBlack.copy(alpha = 0.96f), NoxaBlack.copy(alpha = 0.78f))
                )
            )
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Kembali",
                    tint = NoxaTextPrimary
                )
            }
            AiAvatar(size = 38.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "NOXA AI",
                    color = NoxaTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Asisten musik pribadi",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Bersihkan chat",
                    tint = NoxaTextSecondary
                )
            }
        }
        Row(
            modifier = Modifier.padding(start = 56.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (down != null) Color(0xFFFF8A3D).copy(alpha = 0.14f)
                        else NoxaLime.copy(alpha = 0.12f)
                    )
                    .border(
                        1.dp,
                        if (down != null) Color(0xFFFF8A3D).copy(alpha = 0.35f)
                        else NoxaLime.copy(alpha = 0.30f),
                        RoundedCornerShape(50)
                    )
                    .noxaTap(onClick = onOpenModels)
                    .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (down != null) Color(0xFFFF8A3D) else NoxaLime)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    if (down != null) "${selectedModel.label} · maintenance" else selectedModel.label,
                    color = if (down != null) Color(0xFFFF8A3D) else NoxaLime,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    Icons.Default.ExpandMore,
                    contentDescription = "Ganti model",
                    tint = if (down != null) Color(0xFFFF8A3D) else NoxaLime,
                    modifier = Modifier.size(16.dp)
                )
            }
            // Flagship allowance, visible without opening the picker: the
            // user can see how many messages are left before typing.
            if (quota != null && !quota.unlimited && quota.limit > 0) {
                val tint = if (quota.exhausted) Color(0xFFFF6B61) else NoxaTextTertiary
                Text(
                    text = if (quota.exhausted) "jatah habis · ${quota.limit}/${quota.limit}"
                    else "sisa ${quota.remaining}/${quota.limit} pesan",
                    color = tint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            } else if (quota?.unlimited == true) {
                Text(
                    text = "unlimited",
                    color = NoxaLime.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
    }
}

/** Lime-gradient robot avatar with the AutoAwesome spark. */
@Composable
fun AiAvatar(
    size: Dp,
    modifier: Modifier = Modifier,
    pulsing: Boolean = false
) {
    val infinite = rememberInfiniteTransition(label = "ai_avatar")
    val pulse by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100), RepeatMode.Reverse),
        label = "ai_pulse"
    )
    val scale = if (pulsing) 1f + 0.08f * pulse else 1f
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .scale(scale)
                .clip(RoundedCornerShape(size * 0.32f))
                .background(Brush.linearGradient(listOf(NoxaLime, NoxaLimeDark))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = NoxaBlack,
                modifier = Modifier.size(size * 0.56f)
            )
        }
    }
}

/**
 * Hero panel shown before the first message: a big greeting card plus a 2×2
 * grid of quick actions. Replaces the old "one bubble + four grey chips" that
 * read as a legacy chat.
 */
@Composable
private fun AiWelcomeHero(modelLabel: String, onSuggestion: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            NoxaLime.copy(alpha = 0.16f),
                            NoxaHaze.copy(alpha = 0.10f),
                            NoxaSurfaceElevated
                        )
                    )
                )
                .border(1.dp, NoxaBorder, RoundedCornerShape(26.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AiAvatar(size = 46.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "Selamat datang di NOXA AI",
                            color = NoxaTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Model aktif: $modelLabel",
                            color = NoxaTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Ceritakan suasana hati Anda, minta satu lagu, atau minta playlist " +
                        "lengkap dengan artis yang tepat. Saya juga bisa mengatur antrean, " +
                        "mengganti lagu yang tidak cocok, dan menyetel equalizer.",
                    color = NoxaTextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "MULAI CEPAT",
            color = NoxaTextTertiary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        AiSuggestions.chunked(2).forEach { row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { suggestion ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(NoxaSurfaceSecondary)
                            .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
                            .noxaTap { onSuggestion(suggestion.prompt) }
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Text(
                            suggestion.title,
                            color = NoxaTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            suggestion.hint,
                            color = NoxaTextTertiary,
                            fontSize = 10.5.sp
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AiMessageRow(
    message: AiChatMessage,
    isPlaying: Boolean,
    typewriter: Boolean = false,
    onPlaySong: () -> Unit,
    onOpenPlaylist: (Long) -> Unit
) {
    if (message.fromUser) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(NoxaLime.copy(alpha = 0.22f), NoxaLime.copy(alpha = 0.10f))
                        )
                    )
                    .border(
                        1.dp,
                        NoxaLime.copy(alpha = 0.28f),
                        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                    )
                    .padding(horizontal = 15.dp, vertical = 11.dp)
            ) {
                Text(message.text, color = NoxaTextPrimary, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        return
    }

    val fullText = message.text
    var revealedLength by rememberSaveable(message.text, typewriter) {
        mutableStateOf(if (typewriter) 0 else fullText.length)
    }
    val typing = revealedLength < fullText.length
    if (typing) {
        LaunchedEffect(fullText) {
            while (revealedLength < fullText.length) {
                kotlinx.coroutines.delay(16L)
                revealedLength = minOf(fullText.length, revealedLength + 2)
            }
        }
    }
    val hasAction = message.songTitle != null || message.playlistId != null

    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                if (hasAction) ActionHalo()
                AiAvatar(size = 30.dp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .widthIn(max = 310.dp)
                    .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                    .background(NoxaSurfaceElevated)
                    .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                    .padding(horizontal = 15.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        fullText.take(revealedLength),
                        color = NoxaTextPrimary,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    if (typing) {
                        val caretAlpha by rememberInfiniteTransition(label = "ai_caret")
                            .animateFloat(
                                initialValue = 0.15f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(tween(560), RepeatMode.Reverse),
                                label = "ai_caret_alpha"
                            )
                        Text(
                            "\u258c",
                            color = NoxaLime.copy(alpha = caretAlpha),
                            fontSize = 14.sp,
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }

        message.songTitle?.let { title ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.padding(start = 38.dp)) {
                        AiSongCard(
                            title = title,
                            artist = message.songArtist.orEmpty(),
                            artwork = message.songArtwork,
                            note = message.songNote,
                            isPlaying = isPlaying,
                            onPlay = onPlaySong
                        )
                    }
                }
            }
        }

        message.playlistId?.let { playlistId ->
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.padding(start = 38.dp)) {
                AiPlaylistCard(
                    name = message.playlistName.orEmpty().ifBlank { "Playlist NOXA AI" },
                    trackCount = message.playlistTrackCount,
                    coverSeed = message.playlistCoverSeed,
                    onOpen = { onOpenPlaylist(playlistId) }
                )
            }
        }
    }
}

/** One-shot lime ring that expands and fades out of the avatar on an action. */
@Composable
private fun ActionHalo() {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    Box(
        modifier = Modifier
            .size(30.dp * (1f + 1.1f * p))
            .graphicsLayer { alpha = (1f - p) * 0.8f }
            .border(1.5.dp, NoxaLime, CircleShape)
    )
}

/** The "Dipilih oleh NOXA AI" card — artwork, title, play control, note. */
@Composable
private fun AiSongCard(
    title: String,
    artist: String,
    artwork: String?,
    note: String?,
    isPlaying: Boolean,
    onPlay: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaLime.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaSurfaceSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = NoxaLime,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "DIPILIH OLEH NOXA AI",
                    color = NoxaLime,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    title,
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    artist,
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(NoxaLime)
                    .noxaTap(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Sedang diputar" else "Putar",
                    tint = NoxaBlack,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(NoxaSurfaceSecondary)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(if (isPlaying) 1f else 0.35f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NoxaLime)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            if (isPlaying) "Sedang diputar" else "Ketuk untuk putar",
            color = NoxaTextTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        note?.let {
            Text(it, color = NoxaTextTertiary, fontSize = 10.5.sp)
        }
    }
}

/**
 * Result card for an AI-built playlist: generated cover, name, track count and
 * a direct "Buka playlist" shortcut. This is the answer to "setelah NOXA bikin
 * playlist, tampilannya terlalu standar".
 */
@Composable
private fun AiPlaylistCard(
    name: String,
    trackCount: Int,
    coverSeed: Int,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 310.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaLime.copy(alpha = 0.32f), RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaylistCoverArt(
                name = name,
                cover = PlaylistCover(style = 0, seed = coverSeed),
                modifier = Modifier.size(60.dp),
                cornerRadius = 16.dp,
                showInitials = true
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "PLAYLIST SIAP",
                    color = NoxaLime,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    name,
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                )
                Text(
                    "$trackCount lagu · semua cocok katalog",
                    color = NoxaTextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(NoxaLime.copy(alpha = 0.14f))
                .border(1.dp, NoxaLime.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                .noxaTap(onClick = onOpen)
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Buka playlist",
                color = NoxaLime,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** Thinking state: pulsing avatar + three bouncing dots. */
@Composable
private fun AiThinkingRow() {
    Row(verticalAlignment = Alignment.Bottom) {
        AiAvatar(size = 30.dp, pulsing = true)
        Spacer(modifier = Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(3) { index ->
                val transition = rememberInfiniteTransition(label = "dot_$index")
                val lift by transition.animateFloat(
                    initialValue = 0f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(620, delayMillis = index * 130),
                        RepeatMode.Reverse
                    ),
                    label = "dot_lift_$index"
                )
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .graphicsLayer {
                            translationY = -lift * 5f
                            alpha = 0.45f + lift * 0.55f
                        }
                        .clip(CircleShape)
                        .background(NoxaLime)
                )
            }
        }
    }
}

@Composable
private fun AiComposer(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    flavors: List<AiFlavor> = emptyList(),
    activeFlavor: AiFlavor? = null,
    onToggleFlavor: (AiFlavor) -> Unit = {},
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
    if (flavors.isNotEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "KHUSUS FLAGSHIP",
                color = NoxaLime.copy(alpha = 0.7f),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp
            )
            flavors.forEach { flavor ->
                val on = flavor == activeFlavor
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) NoxaLime.copy(alpha = 0.18f) else NoxaSurfaceSecondary)
                        .border(
                            1.dp,
                            if (on) NoxaLime.copy(alpha = 0.55f) else NoxaBorder,
                            RoundedCornerShape(50)
                        )
                        .noxaTap { onToggleFlavor(flavor) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        flavor.label,
                        color = if (on) NoxaLime else NoxaTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { if (enabled) onValueChange(it) },
            enabled = enabled,
            placeholder = {
                Text("Tulis pesan ke NOXA...", color = NoxaTextTertiary, fontSize = 13.sp)
            },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = NoxaLime,
                unfocusedBorderColor = NoxaBorder,
                focusedTextColor = NoxaTextPrimary,
                unfocusedTextColor = NoxaTextPrimary,
                disabledTextColor = NoxaTextTertiary,
                disabledBorderColor = NoxaBorder,
                cursorColor = NoxaLime,
                focusedContainerColor = NoxaSurfaceSecondary,
                unfocusedContainerColor = NoxaSurfaceSecondary,
                disabledContainerColor = NoxaSurfaceSecondary
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        val active = enabled && value.isNotBlank()
        val sendScale by animateFloatAsState(if (active) 1f else 0.94f, label = "send_scale")
        IconButton(
            onClick = onSend,
            enabled = active,
            modifier = Modifier
                .size(46.dp)
                .scale(sendScale)
                .clip(CircleShape)
                .background(
                    if (active) Brush.linearGradient(listOf(NoxaLime, NoxaLimeDark))
                    else Brush.linearGradient(listOf(NoxaSurfaceSecondary, NoxaSurfaceSecondary))
                )
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Kirim",
                tint = if (active) NoxaBlack else NoxaTextTertiary
            )
        }
    }
    }
}

/**
 * Model picker. A model that is missing from `/v1/models` is listed but marked
 * **Maintenance** and cannot be selected — the user sees which one is down
 * instead of tapping into a silent failure.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiModelSheet(
    selected: AiModel,
    statuses: List<AiModelStatus>,
    quotas: Map<AiModel, AiQuotaState> = emptyMap(),
    onSelect: (AiModel) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                AiAvatar(size = 34.dp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Pilih model",
                        color = NoxaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Ganti bila satu model sedang maintenance",
                        color = NoxaTextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            // Plain-language explanation of the two tiers. No fake paywall: the
            // flagship models are included with these allowances, and the free
            // models stay unlimited forever.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(NoxaLime.copy(alpha = 0.12f), NoxaSurfaceSecondary)
                        )
                    )
                    .border(1.dp, NoxaLime.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Gratis tanpa batas, flagship dengan jatah",
                        color = NoxaTextPrimary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        "Model gratis boleh dipakai sepuasnya. Model flagship " +
                            "memberi jawaban lebih dalam dengan jatah pesan yang " +
                            "pulih sendiri, plus tiga mode kerja khusus di bawah kotak tulis.",
                        color = NoxaTextTertiary,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            var lastTier: AiTier? = null
            AiModel.entries.forEach { model ->
                if (model.tier != lastTier) {
                    lastTier = model.tier
                    Text(
                        if (model.tier == AiTier.FREE) "TANPA BATAS" else "FLAGSHIP",
                        color = if (model.tier == AiTier.FREE) NoxaTextTertiary
                        else NoxaLime.copy(alpha = 0.85f),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 2.dp)
                    )
                }
                val quota = quotas[model]
                val available = statuses.firstOrNull { it.model == model }?.available ?: true
                val isSelected = model == selected
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (isSelected) NoxaLime.copy(alpha = 0.12f) else NoxaSurfaceSecondary)
                        .border(
                            1.dp,
                            if (isSelected) NoxaLime.copy(alpha = 0.42f) else NoxaBorder,
                            RoundedCornerShape(18.dp)
                        )
                        .noxaTap { if (available) onSelect(model) }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                model.label,
                                color = if (available) NoxaTextPrimary else NoxaTextTertiary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (model == AiModel.DEFAULT) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Pill("DEFAULT", NoxaLime)
                            }
                            if (!available) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Pill("MAINTENANCE", Color(0xFFFF8A3D))
                            }
                            if (model == AiModel.GPT) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Pill("PRO", NoxaHaze)
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            if (available) model.blurb else "Sedang tidak tersedia — pakai model lain",
                            color = NoxaTextTertiary,
                            fontSize = 11.sp
                        )
                        if (quota != null && quota.limit > 0) {
                            Spacer(modifier = Modifier.height(2.dp))
                            val minutes = (
                                (quota.resetAtMs - System.currentTimeMillis())
                                    .coerceAtLeast(0L) / 60_000L
                                ) + 1L
                            Text(
                                when {
                                    quota.unlimited -> "Akun developer — tanpa batas"
                                    quota.exhausted ->
                                        "Jatah habis · pulih dalam ~$minutes menit"
                                    else -> "Sisa ${quota.remaining} dari ${quota.limit} pesan · " +
                                        "jatah pulih bertahap"
                                },
                                color = if (quota.exhausted && !quota.unlimited)
                                    Color(0xFFFF6B61) else NoxaLime.copy(alpha = 0.8f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Dipilih",
                            tint = NoxaLime,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Text(
                "Status model dibaca langsung dari daftar resmi API dan diperbarui " +
                    "setiap kali layar ini dibuka.",
                color = NoxaTextTertiary,
                fontSize = 10.5.sp,
                lineHeight = 15.sp
            )
        }
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = tint,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp
        )
    }
}
