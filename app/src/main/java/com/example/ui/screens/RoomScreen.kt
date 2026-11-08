package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.support.RoomAi
import com.example.ui.components.noxaStrongTap
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.FormatUtils

/** One member chip shown in the room roster. */
data class RoomMemberUi(
    val uid: String,
    val name: String,
    val isSelf: Boolean
)

/** One chat line in the room. [fromAi] bubbles are NOXA's own messages. */
data class RoomChatUi(
    val id: String,
    val name: String,
    val text: String,
    val fromAi: Boolean = false,
    val isSelf: Boolean = false,
    val at: Long = 0L
)

/** One song waiting behind the current track. */
data class RoomQueueUi(
    val songId: String,
    val title: String,
    val artist: String,
    val artwork: String = "",
    val addedBy: String = ""
)

/** What the Dengar Bareng screen is showing. */
sealed interface RoomUiState {
    data object Idle : RoomUiState
    data class Joining(val code: String) : RoomUiState

    data class InRoom(
        val code: String,
        val songId: String = "",
        val songTitle: String = "",
        val songArtist: String = "",
        val songArtwork: String = "",
        val playing: Boolean = false,
        val positionMs: Long = 0L,
        val members: List<RoomMemberUi> = emptyList(),
        /** Vote Skip: distinct ballots from current members. */
        val voteCount: Int = 0,
        /** Strict-majority threshold for the current roster size. */
        val votesNeeded: Int = 1,
        /** Whether *this* member currently has a ballot in. */
        val myVote: Boolean = false,
        /** Length of the room's current track; 0 while unknown. */
        val durationMs: Long = 0L,
        /** The room admin (first to join). */
        val hostUid: String = "",
        /** True when this device is the admin. */
        val isHost: Boolean = false,
        /** Admin switch: keep playing similar songs when the queue runs dry. */
        val autoRadio: Boolean = true,
        /** Admin switch: may members summon NOXA with `@noxa …`? */
        val aiEnabled: Boolean = true,
        val chat: List<RoomChatUi> = emptyList(),
        val queue: List<RoomQueueUi> = emptyList(),
        /** NOXA is searching the catalogue for a mention right now. */
        val aiThinking: Boolean = false
    ) : RoomUiState

    data class Failed(val code: String, val message: String) : RoomUiState
}

/**
 * **Dengar Bareng** — one shared room, everyone hears the same song and talks
 * in the same chat.
 *
 * iOS-flavoured: the pre-room screen starts almost empty (one hero card, one
 * code field, two actions); inside a room the top is a compact player with
 * progressive controls, the middle is the conversation, and `@noxa` turns the
 * chat into a DJ booth — suggestions appear the moment you type `@`.
 */
@Composable
fun RoomScreen(
    state: RoomUiState,
    onBack: () -> Unit,
    onJoin: (String) -> Unit,
    onCreate: () -> Unit,
    onLeave: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    /** Vote Skip — casts/withdraws this member's ballot for the current song. */
    onVoteSkip: () -> Unit,
    /** Sends a chat line; `@noxa …` also asks NOXA to play something. */
    onSendMessage: (String) -> Unit,
    /** Admin: allow/deny `@noxa` requests in this room. */
    onAiToggle: (Boolean) -> Unit,
    /** Admin: keep the music going with similar songs. */
    onRadioToggle: (Boolean) -> Unit,
    /** Admin: stop NOXA right now (clears the queue, pauses). */
    onStopDj: () -> Unit,
    /** Admin: drop everything waiting behind the current song. */
    onClearQueue: () -> Unit,
    /** Smooth local progress (ms) — used when the room plays our own song. */
    localPositionMs: Long,
    localDurationMs: Long,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .statusBarsPadding()
                // The activity runs with adjustNothing, so the IME inset is the
                // only thing that moves the layout — the composer must follow it.
                .imePadding()
                .padding(horizontal = 16.dp)
        ) {
            RoomHeader(onBack = onBack)

            when (val s = state) {
                RoomUiState.Idle -> ScrollableRoomBody {
                    RoomIdleBody(onJoin = onJoin, onCreate = onCreate)
                }

                is RoomUiState.Joining -> ScrollableRoomBody {
                    RoomJoiningBody(code = s.code)
                }

                is RoomUiState.Failed -> ScrollableRoomBody {
                    RoomFailedBody(
                        code = s.code,
                        message = s.message,
                        onRetry = { onJoin(s.code) }
                    )
                }

                is RoomUiState.InRoom -> RoomInRoomBody(
                    state = s,
                    onLeave = onLeave,
                    onTogglePlayPause = onTogglePlayPause,
                    onSeekTo = onSeekTo,
                    onPrevious = onPrevious,
                    onNext = onNext,
                    onVoteSkip = onVoteSkip,
                    onSendMessage = onSendMessage,
                    onAiToggle = onAiToggle,
                    onRadioToggle = onRadioToggle,
                    onStopDj = onStopDj,
                    onClearQueue = onClearQueue,
                    localPositionMs = localPositionMs,
                    localDurationMs = localDurationMs
                )
            }
        }
    }
}

/** The pre-room states scroll; the in-room state is a chat and fills the screen. */
@Composable
private fun ColumnScope.ScrollableRoomBody(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
    ) {
        content()
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun RoomHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Dengar Bareng",
                color = NoxaTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Satu room, satu lagu, satu obrolan",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(NoxaBorder)
    )
}

// ------------------------------------------------------------------ idle

@Composable
private fun RoomIdleBody(onJoin: (String) -> Unit, onCreate: () -> Unit) {
    var code by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(24.dp))

    // Hero card — the one thing on screen before you act.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(NoxaLime.copy(alpha = 0.12f), NoxaPurple.copy(alpha = 0.12f))
                )
            )
            .border(1.dp, NoxaBorder, RoundedCornerShape(24.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(NoxaLime.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = NoxaLime,
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Putar musik bareng teman,\nngobrol di ruang yang sama",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Buat room dan bagikan kodenya. Semua mendengar lagu yang sama, bisa chatan, dan bisa minta lagu ke @noxa.",
            color = NoxaTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Code entry: one big capsule, uppercase, monospaced — iOS input style.
    OutlinedRoomCodeField(
        value = code,
        onValueChange = { input ->
            code = input.filter { it.isLetterOrDigit() }.take(6).uppercase(java.util.Locale.ROOT)
        }
    )

    Spacer(modifier = Modifier.height(16.dp))

    // Primary: create. Secondary: join with the typed code.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(NoxaLime)
            .noxaStrongTap { onCreate() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Buat Room Baru", color = NoxaBlack, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(10.dp))

    val canJoin = code.length in 4..6
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (canJoin) NoxaSurfaceElevated else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (canJoin) NoxaLime.copy(alpha = 0.6f) else NoxaBorder,
                RoundedCornerShape(26.dp)
            )
            .noxaTap(enabled = canJoin) { onJoin(code) }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Gabung dengan Kode",
            color = if (canJoin) NoxaLime else NoxaTextTertiary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Spacer(modifier = Modifier.height(10.dp))
    Text(
        "Kode terdiri dari 6 karakter, contoh: K7M2QX",
        color = NoxaTextTertiary,
        fontSize = 11.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun OutlinedRoomCodeField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = {
            Text(
                "KODE ROOM",
                color = NoxaTextTertiary,
                fontSize = 16.sp,
                letterSpacing = 4.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Characters
        ),
        textStyle = TextStyle(
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 7.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            color = NoxaTextPrimary
        ),
        shape = RoundedCornerShape(18.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NoxaLime,
            unfocusedBorderColor = NoxaBorder,
            cursorColor = NoxaLime,
            focusedContainerColor = NoxaSurfaceElevated,
            unfocusedContainerColor = NoxaSurfaceElevated
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ------------------------------------------------------------- joining / failed

@Composable
private fun RoomJoiningBody(code: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(70.dp))
        CircularProgressIndicator(color = NoxaLime, strokeWidth = 2.5.dp)
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            "Menyambung ke room $code…",
            color = NoxaTextSecondary,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun RoomFailedBody(code: String, message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        Text(
            "Room $code tidak bisa dimasuki",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            message,
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(NoxaLime)
                .clickable { onRetry() }
                .padding(horizontal = 26.dp, vertical = 11.dp)
        ) {
            Text("Coba Lagi", color = NoxaBlack, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------ in room

@Composable
private fun ColumnScope.RoomInRoomBody(
    state: RoomUiState.InRoom,
    onLeave: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVoteSkip: () -> Unit,
    onSendMessage: (String) -> Unit,
    onAiToggle: (Boolean) -> Unit,
    onRadioToggle: (Boolean) -> Unit,
    onStopDj: () -> Unit,
    onClearQueue: () -> Unit,
    localPositionMs: Long,
    localDurationMs: Long
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }

    // Newest message always in view — including NOXA's "looking…" bubble.
    LaunchedEffect(state.chat.size, state.aiThinking) {
        if (state.chat.isNotEmpty()) {
            listState.animateScrollToItem(state.chat.lastIndex)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // --- code chip + roster + leave, in one compact row.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaLime.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                .noxaTap {
                    clipboard.setText(AnnotatedString(state.code))
                    Toast.makeText(context, "Kode room disalin: ${state.code}", Toast.LENGTH_SHORT)
                        .show()
                }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = "Salin kode room",
                tint = NoxaLime,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                state.code,
                color = NoxaLime,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Avatar stack: first three members, then "+N".
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            state.members.take(3).forEach { member ->
                RoomAvatar(name = member.name, isSelf = member.isSelf)
            }
            if (state.members.size > 3) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(NoxaPurpleDeep)
                        .border(2.dp, NoxaBlack, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "+${state.members.size - 3}",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            "Keluar",
            color = NoxaTextTertiary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .noxaStrongTap { onLeave() }
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )
    }

    Spacer(modifier = Modifier.height(6.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {
        LiveDot()
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "${state.members.size} pendengar · obrolan terbuka",
            color = NoxaTextSecondary,
            fontSize = 11.sp
        )
        if (state.isHost) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NoxaLime.copy(alpha = 0.16f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    "ADMIN",
                    color = NoxaLime,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    if (state.songId.isBlank()) {
        // Empty room: a slim notice, not a dead player. The chat stays usable.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorder, RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = NoxaTextTertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Belum ada lagu. Putar dari pemutar, atau tag @noxa di obrolan.",
                color = NoxaTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
    } else {
        RoomNowPlaying(
            state = state,
            onSeekTo = onSeekTo,
            onTogglePlayPause = onTogglePlayPause,
            onPrevious = onPrevious,
            onNext = onNext,
            onVoteSkip = onVoteSkip,
            localPositionMs = localPositionMs,
            localDurationMs = localDurationMs
        )
    }

    if (state.queue.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        RoomQueueStrip(
            queue = state.queue,
            isHost = state.isHost,
            onClearQueue = onClearQueue
        )
    }

    if (state.isHost) {
        Spacer(modifier = Modifier.height(8.dp))
        RoomAdminBar(
            state = state,
            onAiToggle = onAiToggle,
            onRadioToggle = onRadioToggle,
            onStopDj = onStopDj
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // --- the conversation (and NOXA's stage).
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("room_chat"),
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (state.chat.isEmpty()) {
            item(key = "room_chat_empty") { RoomChatEmpty() }
        }
        items(state.chat, key = { it.id }) { message -> RoomChatBubble(message) }
        if (state.aiThinking) {
            item(key = "room_chat_ai_typing") { NoxaTypingBubble() }
        }
    }

    // --- suggestion the moment the handle is half typed.
    AnimatedVisibility(
        visible = RoomAi.suggestsHandle(draft),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NoxaLime.copy(alpha = 0.12f))
                    .border(1.dp, NoxaLime.copy(alpha = 0.5f), RoundedCornerShape(50))
                    .noxaTap { draft = RoomAi.completeHandle(draft) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(7.dp))
                Text(
                    "noxa",
                    color = NoxaLime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "DJ room — minta lagu",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }

    RoomComposer(
        draft = draft,
        onDraftChange = { draft = it.take(240) },
        onSend = {
            val text = draft.trim()
            if (text.isNotEmpty()) {
                onSendMessage(text)
                draft = ""
            }
        }
    )

    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun RoomNowPlaying(
    state: RoomUiState.InRoom,
    onSeekTo: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onVoteSkip: () -> Unit,
    localPositionMs: Long,
    localDurationMs: Long
) {
    // The caller zeroes local progress when the room plays another song,
    // so "has local duration" == "the room plays our song".
    val duration = if (localDurationMs > 0) localDurationMs else state.durationMs
    val position = if (localDurationMs > 0) localPositionMs else state.positionMs
    var seeking by remember { mutableStateOf<Float?>(null) }
    val shownPosition = seeking ?: position

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (state.songArtwork.isNotBlank()) {
                AsyncImage(
                    model = state.songArtwork,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NoxaSurfaceSecondary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = NoxaLime,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "SEDANG DIPUTAR BERSAMA",
                    color = NoxaLime,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    state.songTitle,
                    color = NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    state.songArtist,
                    color = NoxaTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NoxaLime)
                    .clickable { onTogglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (state.playing) "Jeda" else "Putar",
                    tint = NoxaBlack,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Progress line (tap-free, iOS-style): whole bar is the seek surface.
        Slider(
            value = shownPosition.toFloat().coerceAtMost((duration.coerceAtLeast(1L)).toFloat()),
            onValueChange = { seeking = it },
            onValueChangeFinished = {
                seeking?.let { onSeekTo(it.toLong()) }
                seeking = null
            },
            enabled = duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = NoxaLime,
                activeTrackColor = NoxaLime,
                inactiveTrackColor = NoxaSurfaceSecondary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                FormatUtils.formatDuration(shownPosition.toLong()),
                color = NoxaTextTertiary,
                fontSize = 10.sp
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.SkipPrevious,
                        contentDescription = "Sebelumnya",
                        tint = NoxaTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                VoteSkipChip(state = state, onVoteSkip = onVoteSkip)
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.SkipNext,
                        contentDescription = "Berikutnya",
                        tint = NoxaTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Text(
                if (duration > 0) FormatUtils.formatDuration(duration) else "--:--",
                color = NoxaTextTertiary,
                fontSize = 10.sp
            )
        }
    }
}

/** Compact vote-skip pill: one ballot each, a strict majority skips. */
@Composable
private fun VoteSkipChip(state: RoomUiState.InRoom, onVoteSkip: () -> Unit) {
    val needed = state.votesNeeded.coerceAtLeast(1)
    val count = state.voteCount.coerceIn(0, needed)
    val reached = state.voteCount >= needed
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(
                if (state.myVote || reached) NoxaLime.copy(alpha = 0.16f)
                else NoxaSurfaceSecondary
            )
            .border(
                1.dp,
                if (state.myVote || reached) NoxaLime.copy(alpha = 0.6f) else NoxaBorder,
                RoundedCornerShape(50)
            )
            .noxaTap { onVoteSkip() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
            .testTag("room_vote_skip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (state.myVote) "Skip ✓" else "Skip",
            color = if (state.myVote || reached) NoxaLime else NoxaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(5.dp))
        repeat(needed.coerceAtMost(5)) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i < count) NoxaLime else NoxaTextTertiary.copy(alpha = 0.4f))
            )
        }
        if (needed > 5) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                "$count/$needed",
                color = NoxaTextTertiary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
private fun RoomQueueStrip(
    queue: List<RoomQueueUi>,
    isHost: Boolean,
    onClearQueue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "ANTRIAN · ${queue.size}",
                color = NoxaTextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            if (isHost) {
                Text(
                    "Kosongkan",
                    color = NoxaTextTertiary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .noxaTap { onClearQueue() }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(queue, key = { it.songId }) { item ->
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(NoxaSurfaceSecondary)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        item.title,
                        color = NoxaTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        item.artist,
                        color = NoxaTextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.widthIn(max = 70.dp),
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** Admin corner: NOXA allowance, auto-radio, and a hard stop. */
@Composable
private fun RoomAdminBar(
    state: RoomUiState.InRoom,
    onAiToggle: (Boolean) -> Unit,
    onRadioToggle: (Boolean) -> Unit,
    onStopDj: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AdminChip(
            label = if (state.aiEnabled) "NOXA ON" else "NOXA OFF",
            active = state.aiEnabled,
            onClick = { onAiToggle(!state.aiEnabled) }
        )
        AdminChip(
            label = if (state.autoRadio) "Radio auto" else "Radio mati",
            active = state.autoRadio,
            onClick = { onRadioToggle(!state.autoRadio) }
        )
        AdminChip(
            label = "Stop NOXA",
            active = false,
            onClick = onStopDj
        )
    }
}

@Composable
private fun AdminChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) NoxaLime.copy(alpha = 0.14f) else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (active) NoxaLime.copy(alpha = 0.55f) else NoxaBorder,
                RoundedCornerShape(50)
            )
            .noxaTap { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (active) NoxaLime else NoxaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RoomChatEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Belum ada obrolan",
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Sapa temanmu, atau ketik @ untuk minta lagu ke NOXA.",
            color = NoxaTextTertiary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun RoomChatBubble(message: RoomChatUi) {
    val alignment =
        if (message.isSelf && !message.fromAi) Alignment.End else Alignment.Start
    val bubbleColor = when {
        message.fromAi -> NoxaLime.copy(alpha = 0.10f)
        message.isSelf -> NoxaLime.copy(alpha = 0.16f)
        else -> NoxaSurfaceElevated
    }
    val borderColor = when {
        message.fromAi -> NoxaLime.copy(alpha = 0.55f)
        message.isSelf -> NoxaLime.copy(alpha = 0.35f)
        else -> NoxaBorder
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (message.fromAi) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
            Text(
                if (message.fromAi) "NOXA" else message.name,
                color = if (message.fromAi) NoxaLime else NoxaTextTertiary,
                fontSize = 9.sp,
                fontWeight = if (message.fromAi) FontWeight.Black else FontWeight.SemiBold,
                letterSpacing = if (message.fromAi) 0.8.sp else 0.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 180.dp)
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (message.isSelf && !message.fromAi) 14.dp else 4.dp,
                        bottomEnd = if (message.isSelf && !message.fromAi) 4.dp else 14.dp
                    )
                )
                .background(bubbleColor)
                .border(
                    1.dp,
                    borderColor,
                    RoundedCornerShape(
                        topStart = 14.dp,
                        topEnd = 14.dp,
                        bottomStart = if (message.isSelf && !message.fromAi) 14.dp else 4.dp,
                        bottomEnd = if (message.isSelf && !message.fromAi) 4.dp else 14.dp
                    )
                )
                .padding(horizontal = 11.dp, vertical = 8.dp)
        ) {
            Text(
                message.text,
                color = NoxaTextPrimary,
                fontSize = 12.5.sp,
                lineHeight = 17.sp
            )
        }
    }
}

@Composable
private fun NoxaTypingBubble() {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NoxaLime.copy(alpha = 0.08f))
            .border(1.dp, NoxaLime.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(
            color = NoxaLime,
            strokeWidth = 1.5.dp,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            "NOXA sedang mencari lagunya…",
            color = NoxaTextSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun RoomComposer(
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val canSend = draft.isNotBlank()
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    "Ketik pesan… atau @noxa",
                    color = NoxaTextTertiary,
                    fontSize = 12.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            textStyle = TextStyle(fontSize = 13.sp, color = NoxaTextPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = NoxaSurfaceElevated,
                unfocusedContainerColor = NoxaSurfaceElevated,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = NoxaLime
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("room_chat_input")
        )
        Spacer(modifier = Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(if (canSend) NoxaLime else NoxaSurfaceSecondary)
                .noxaTap(enabled = canSend) { onSend() }
                .testTag("room_chat_send"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Kirim",
                tint = if (canSend) NoxaBlack else NoxaTextTertiary,
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
private fun RoomAvatar(name: String, isSelf: Boolean) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isSelf) NoxaLime else NoxaPurple)
            .border(2.dp, NoxaBlack, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(java.util.Locale.ROOT).ifBlank { "?" },
            color = if (isSelf) NoxaBlack else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** Small pulsing lime dot — "this room is live". */
@Composable
private fun LiveDot() {
    val transition = rememberInfiniteTransition(label = "live_dot")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "live_dot_alpha"
    )
    Box(
        modifier = Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(NoxaLime.copy(alpha = alpha))
    )
}
