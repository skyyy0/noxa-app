package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
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
import kotlinx.coroutines.launch
import coil.compose.AsyncImage
import com.example.data.model.Song
import com.example.data.support.RoomAi
import com.example.ui.components.noxaStrongTap
import com.example.ui.components.noxaTap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaBorderSubtle
import com.example.ui.theme.NoxaEmber
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaSurfaceTertiary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.FormatUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** One member chip shown in the room roster. */
data class RoomMemberUi(
    val uid: String,
    val name: String,
    val isSelf: Boolean
)

/**
 * One chat line in the room. [fromAi] bubbles are NOXA's own messages.
 * [pending] lines are drawn before the server confirms them; [failed] ones can
 * be tapped to send again — a message is never silently swallowed.
 */
data class RoomChatUi(
    val id: String,
    val name: String,
    val text: String,
    val fromAi: Boolean = false,
    val isSelf: Boolean = false,
    val at: Long = 0L,
    val pending: Boolean = false,
    val failed: Boolean = false
)

/** One song waiting behind the current track. */
data class RoomQueueUi(
    val songId: String,
    val title: String,
    val artist: String,
    val artwork: String = "",
    val addedBy: String = ""
)

/** Catalogue search inside the room (admin picks for everyone). */
data class RoomSearchUi(
    val query: String = "",
    val results: List<Song> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
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
 * iOS-flavoured, matching the home tab: near-black canvas, tonal cards (no
 * outlines shouting), generous radii, and lime used only for what is happening
 * right now. A compact player sits on top, the conversation fills the middle,
 * and `@noxa` turns the composer into a DJ booth.
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
    /** Re-sends a line the backend refused. */
    onRetryMessage: (String) -> Unit,
    /** Admin: allow/deny `@noxa` requests in this room. */
    onAiToggle: (Boolean) -> Unit,
    /** Admin: keep the music going with similar songs. */
    onRadioToggle: (Boolean) -> Unit,
    /** Admin: stop NOXA right now (clears the queue, pauses). */
    onStopDj: () -> Unit,
    /** Admin: drop everything waiting behind the current song. */
    onClearQueue: () -> Unit,
    /** Admin catalogue search state for the "putar untuk semua" panel. */
    search: RoomSearchUi = RoomSearchUi(),
    onSearchSongs: (String) -> Unit = {},
    onPlaySearchResult: (Song) -> Unit = {},
    onQueueSearchResult: (Song) -> Unit = {},
    /** Admin: promote a queued song to the room now. */
    onPlayQueued: (String) -> Unit = {},
    /** Admin: drop one queued song. */
    onRemoveQueued: (String) -> Unit = {},
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
        // Ambient wash: the same quiet light the home tab uses behind its
        // shelves, so the room belongs to the app instead of floating in it.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            NoxaLime.copy(alpha = 0.05f),
                            Color.Transparent,
                            Color.Transparent
                        )
                    )
                )
        )
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
            RoomHeader(onBack = onBack, code = (state as? RoomUiState.InRoom)?.code)

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
                    onRetryMessage = onRetryMessage,
                    onAiToggle = onAiToggle,
                    onRadioToggle = onRadioToggle,
                    onStopDj = onStopDj,
                    onClearQueue = onClearQueue,
                    search = search,
                    onSearchSongs = onSearchSongs,
                    onPlaySearchResult = onPlaySearchResult,
                    onQueueSearchResult = onQueueSearchResult,
                    onPlayQueued = onPlayQueued,
                    onRemoveQueued = onRemoveQueued,
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

/** iOS navigation bar: round glass back button, centred title, room code. */
@Composable
private fun RoomHeader(onBack: () -> Unit, code: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorderSubtle, CircleShape)
                .noxaTap(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Dengar Bareng",
                color = NoxaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Text(
                if (code != null) "Room $code · satu lagu untuk semua"
                else "Satu room, satu lagu, satu obrolan",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
    }
}

// ------------------------------------------------------------------ idle

@Composable
private fun RoomIdleBody(onJoin: (String) -> Unit, onCreate: () -> Unit) {
    var code by remember { mutableStateOf("") }

    Spacer(modifier = Modifier.height(20.dp))

    // Hero card — one soft panel, no hard outlines.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(NoxaLime.copy(alpha = 0.14f), NoxaPurple.copy(alpha = 0.10f))
                )
            )
            .border(1.dp, NoxaBorderSubtle, RoundedCornerShape(28.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
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
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp,
            letterSpacing = (-0.2).sp
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            "Buat room dan bagikan kodenya. Semua mendengar lagu yang sama, " +
                "bisa chatan, dan bisa minta lagu ke @noxa.",
            color = NoxaTextSecondary,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 17.sp
        )
    }

    Spacer(modifier = Modifier.height(22.dp))

    OutlinedRoomCodeField(
        value = code,
        onValueChange = { input ->
            code = input.filter { it.isLetterOrDigit() }.take(6).uppercase(Locale.ROOT)
        }
    )

    Spacer(modifier = Modifier.height(14.dp))

    RoomPrimaryButton(label = "Buat Room Baru", onClick = onCreate)

    Spacer(modifier = Modifier.height(10.dp))

    val canJoin = code.length in 4..6
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(if (canJoin) NoxaSurfaceElevated else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (canJoin) NoxaLime.copy(alpha = 0.5f) else NoxaBorderSubtle,
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
            fontWeight = FontWeight.SemiBold
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
private fun RoomPrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(NoxaLime)
            .noxaStrongTap { onClick() }
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = NoxaBlack, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OutlinedRoomCodeField(value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
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
        shape = RoundedCornerShape(20.dp),
        colors = OutlinedTextFieldDefaults.colors(
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
                .noxaStrongTap { onRetry() }
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
    onRetryMessage: (String) -> Unit,
    onAiToggle: (Boolean) -> Unit,
    onRadioToggle: (Boolean) -> Unit,
    onStopDj: () -> Unit,
    onClearQueue: () -> Unit,
    search: RoomSearchUi,
    onSearchSongs: (String) -> Unit,
    onPlaySearchResult: (Song) -> Unit,
    onQueueSearchResult: (Song) -> Unit,
    onPlayQueued: (String) -> Unit,
    onRemoveQueued: (String) -> Unit,
    localPositionMs: Long,
    localDurationMs: Long
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var draft by rememberSaveable { mutableStateOf("") }
    var showMembers by rememberSaveable { mutableStateOf(false) }
    var showSearch by rememberSaveable { mutableStateOf(false) }

    // Newest message always in view — including NOXA's "looking…" bubble.
    LaunchedEffect(state.chat.size, state.aiThinking) {
        if (state.chat.isNotEmpty()) {
            listState.animateScrollToItem(state.chat.lastIndex)
        }
    }

    fun copyCode() {
        clipboard.setText(AnnotatedString(state.code))
        Toast.makeText(context, "Kode room disalin: ${state.code}", Toast.LENGTH_SHORT).show()
    }

    fun shareCode() {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(
                Intent.EXTRA_TEXT,
                "Gabung Dengar Bareng di NOXA Music — kode room ${state.code}. " +
                    "Buka tab Room, ketik kodenya, kita dengar lagu yang sama."
            )
        }
        runCatching {
            context.startActivity(Intent.createChooser(send, "Bagikan kode room"))
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // --- code chip + roster + leave, in one compact row.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaLime.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .noxaTap { copyCode() }
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

        Spacer(modifier = Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(NoxaSurfaceElevated)
                .border(1.dp, NoxaBorderSubtle, CircleShape)
                .noxaTap { shareCode() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Share,
                contentDescription = "Bagikan kode room",
                tint = NoxaTextSecondary,
                modifier = Modifier.size(13.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Avatar stack: first three members, then "+N". Tap opens the roster.
        Row(
            modifier = Modifier.noxaTap { showMembers = true },
            horizontalArrangement = Arrangement.spacedBy((-8).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.members.take(3).forEach { member ->
                RoomAvatar(
                    name = member.name,
                    isSelf = member.isSelf,
                    isHost = member.uid == state.hostUid
                )
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
            Badge(text = "ADMIN", color = NoxaLime)
        }
        if (!state.aiEnabled) {
            Spacer(modifier = Modifier.width(6.dp))
            Badge(text = "NOXA OFF", color = NoxaTextTertiary)
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // --- player / empty notice, easing in once when the room opens.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    AnimatedVisibility(
        visible = appeared,
        enter = fadeIn(tween(260)) +
            slideInVertically(animationSpec = spring(dampingRatio = 0.85f)) { it / 5 }
    ) {
        if (state.songId.isBlank()) {
            RoomEmptyPlayer(
                isHost = state.isHost,
                onFindSong = { showSearch = true }
            )
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
    }

    AnimatedVisibility(
        visible = state.queue.isNotEmpty(),
        enter = fadeIn(tween(200)) + expandVertically(tween(220)),
        exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
    ) {
        Column {
            Spacer(modifier = Modifier.height(8.dp))
            RoomQueueStrip(
                queue = state.queue,
                isHost = state.isHost,
                onClearQueue = onClearQueue,
                onPlay = onPlayQueued,
                onRemove = onRemoveQueued
            )
        }
    }

    if (state.isHost) {
        Spacer(modifier = Modifier.height(8.dp))
        RoomHostBar(
            state = state,
            onAiToggle = onAiToggle,
            onRadioToggle = onRadioToggle,
            onStopDj = onStopDj,
            onFindSong = { showSearch = true }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    // --- the conversation (and NOXA's stage).
    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        val scrolledUp by remember {
            derivedStateOf {
                listState.firstVisibleItemIndex < state.chat.lastIndex - 1 ||
                    (listState.firstVisibleItemIndex == state.chat.lastIndex &&
                        listState.firstVisibleItemScrollOffset > 40)
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .testTag("room_chat"),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (state.chat.isEmpty()) {
                item(key = "room_chat_empty") { RoomChatEmpty(onAsk = onSendMessage) }
            }
            itemsIndexed(state.chat, key = { _, item -> item.id }) { index, message ->
                val previous = state.chat.getOrNull(index - 1)
                RoomChatBubble(
                    message = message,
                    previous = previous,
                    onRetry = { onRetryMessage(message.text) }
                )
            }
            if (state.aiThinking) {
                item(key = "room_chat_ai_typing") { NoxaTypingBubble() }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = scrolledUp && state.chat.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f) +
                slideInVertically { it / 2 },
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NoxaSurfaceElevated)
                    .border(1.dp, NoxaBorder, RoundedCornerShape(50))
                    .noxaTap {
                        if (state.chat.isNotEmpty()) {
                            scope.launch {
                                listState.animateScrollToItem(state.chat.lastIndex)
                            }
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.ArrowDownward,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text("Pesan terbaru", color = NoxaTextSecondary, fontSize = 10.sp)
            }
        }
    }

    // --- suggestion the moment the handle is half typed.
    AnimatedVisibility(
        visible = RoomAi.suggestsHandle(draft),
        enter = fadeIn(tween(140)) + slideInVertically { it / 2 },
        exit = fadeOut(tween(100))
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
                    .border(1.dp, NoxaLime.copy(alpha = 0.45f), RoundedCornerShape(50))
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
                Text("noxa", color = NoxaLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    "DJ room — minta lagu atau ngobrol",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
        }
    }

    RoomComposer(
        draft = draft,
        aiEnabled = state.aiEnabled,
        onDraftChange = { draft = it.take(240) },
        onInsertHandle = { draft = RoomAi.completeHandle(draft) },
        onSend = {
            val text = draft.trim()
            if (text.isNotEmpty()) {
                onSendMessage(text)
                draft = ""
            }
        }
    )

    Spacer(modifier = Modifier.height(10.dp))

    if (showMembers) {
        RoomMembersSheet(
            state = state,
            onDismiss = { showMembers = false },
            onCopyCode = { copyCode() },
            onShare = { shareCode() }
        )
    }

    if (showSearch && state.isHost) {
        RoomSearchSheet(
            search = search,
            onQuery = onSearchSongs,
            onPlay = { song ->
                onPlaySearchResult(song)
                showSearch = false
            },
            onQueue = { song ->
                onQueueSearchResult(song)
                showSearch = false
            },
            onDismiss = {
                showSearch = false
                onSearchSongs("")
            }
        )
    }
}

// ----------------------------------------------------------- now playing

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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.verticalGradient(
                    listOf(NoxaSurfaceElevated, NoxaSurface)
                )
            )
            .border(1.dp, NoxaBorderSubtle, RoundedCornerShape(26.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BreathingArtwork(
                url = state.songArtwork,
                playing = state.playing,
                size = 64
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SEKARANG BERSAMA",
                        color = NoxaLime,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.1.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    RoomEqualizer(active = state.playing, tint = NoxaLime)
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    state.songTitle,
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (-0.2).sp,
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
            PlayPauseOrb(
                playing = state.playing,
                onClick = onTogglePlayPause,
                size = 46
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                FormatUtils.formatDuration(position.coerceAtLeast(0L)),
                color = NoxaTextTertiary,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            RoomProgressBar(
                positionMs = position,
                durationMs = duration,
                onSeek = onSeekTo,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (duration > 0) FormatUtils.formatDuration(duration) else "--:--",
                color = NoxaTextTertiary,
                fontSize = 10.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.SkipPrevious,
                    contentDescription = "Sebelumnya",
                    tint = NoxaTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            VoteSkipChip(state = state, onVoteSkip = onVoteSkip)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.SkipNext,
                    contentDescription = "Berikutnya",
                    tint = NoxaTextPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Artwork that breathes very slightly while the room is playing — the same
 * trick the home tab's "now playing" shelf uses: motion you feel, not notice.
 */
@Composable
private fun BreathingArtwork(url: String, playing: Boolean, size: Int) {
    val transition = rememberInfiniteTransition(label = "room_art")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (playing) 1.035f else 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Reverse),
        label = "room_art_scale"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaSurfaceSecondary),
        contentAlignment = Alignment.Center
    ) {
        if (url.isNotBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(scale)
                    .clip(RoundedCornerShape(18.dp)),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        } else {
            Icon(
                Icons.Default.GraphicEq,
                contentDescription = null,
                tint = NoxaLime,
                modifier = Modifier.size(24.dp)
            )
        }
        // A soft lime tag in the corner says "this is the live room track".
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(16.dp)
                .clip(CircleShape)
                .background(NoxaBlack.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            RoomEqualizer(active = playing, tint = NoxaLime, barWidth = 1.5f, height = 7)
        }
    }
}

/** Three slim bars — signal, not decoration. Idle bars stay nearly still. */
@Composable
private fun RoomEqualizer(
    active: Boolean,
    tint: Color,
    barWidth: Float = 2f,
    height: Int = 10
) {
    val transition = rememberInfiniteTransition(label = "room_eq")
    val b1 by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (active) 1f else 0.4f,
        animationSpec = infiniteRepeatable(tween(620), RepeatMode.Reverse),
        label = "eq1"
    )
    val b2 by transition.animateFloat(
        initialValue = 0.9f,
        targetValue = if (active) 0.35f else 0.55f,
        animationSpec = infiniteRepeatable(tween(480), RepeatMode.Reverse),
        label = "eq2"
    )
    val b3 by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = if (active) 0.85f else 0.45f,
        animationSpec = infiniteRepeatable(tween(740), RepeatMode.Reverse),
        label = "eq3"
    )
    Row(
        modifier = Modifier.height(height.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        listOf(b1, b2, b3).forEach { level ->
            Box(
                modifier = Modifier
                    .width(barWidth.dp)
                    .height((height * level).dp.coerceAtLeast(2.dp))
                    .clip(RoundedCornerShape(1.dp))
                    .background(tint.copy(alpha = 0.9f))
            )
        }
    }
}

@Composable
private fun PlayPauseOrb(playing: Boolean, onClick: () -> Unit, size: Int = 46) {
    val scale by animateFloatAsState(
        targetValue = if (playing) 1f else 0.94f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "play_orb"
    )
    Box(
        modifier = Modifier
            .size(size.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(NoxaLime)
            .noxaStrongTap { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (playing) "Jeda" else "Putar",
            tint = NoxaBlack,
            modifier = Modifier.size((size * 0.52f).dp)
        )
    }
}

/**
 * Thin iOS progress line: the whole strip is the seek surface, the fill glides
 * between polls instead of stepping once per second.
 */
@Composable
private fun RoomProgressBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val enabled = durationMs > 0
    val target = if (enabled) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    val animated by animateFloatAsState(
        targetValue = dragFraction ?: target,
        animationSpec = if (dragFraction != null) tween(0) else tween(750, easing = FastOutSlowInEasing),
        label = "room_progress"
    )
    Canvas(
        modifier = modifier
            .height(24.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek(((offset.x / widthPx).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / widthPx).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragFraction?.let { f -> onSeek((f * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null }
                )
            }
    ) {
        val cy = size.height / 2f
        val radius = 2.dp.toPx()
        drawRoundRect(
            color = NoxaSurfaceSecondary,
            topLeft = Offset(0f, cy - radius),
            size = Size(size.width, radius * 2f),
            cornerRadius = CornerRadius(radius)
        )
        drawRoundRect(
            color = NoxaLime,
            topLeft = Offset(0f, cy - radius),
            size = Size(size.width * animated, radius * 2f),
            cornerRadius = CornerRadius(radius)
        )
        if (durationMs > 0) {
            drawCircle(
                color = NoxaLime,
                radius = (if (dragFraction != null) 6.dp else 4.dp).toPx(),
                center = Offset(size.width * animated, cy)
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
    val active = state.myVote || reached
    val background by animateColorAsState(
        targetValue = if (active) NoxaLime.copy(alpha = 0.15f) else NoxaSurfaceSecondary,
        animationSpec = tween(220),
        label = "vote_bg"
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .border(
                1.dp,
                if (active) NoxaLime.copy(alpha = 0.55f) else NoxaBorder,
                RoundedCornerShape(50)
            )
            .noxaTap { onVoteSkip() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("room_vote_skip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (state.myVote) Icons.Default.Check else Icons.Default.SkipNext,
            contentDescription = null,
            tint = if (active) NoxaLime else NoxaTextSecondary,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            if (state.myVote) "Skip" else "Vote skip",
            color = if (active) NoxaLime else NoxaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.width(5.dp))
        repeat(needed.coerceAtMost(5)) { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(if (i < count) NoxaLime else NoxaTextTertiary.copy(alpha = 0.35f))
            )
        }
        if (needed > 5) {
            Spacer(modifier = Modifier.width(4.dp))
            Text("$count/$needed", color = NoxaTextTertiary, fontSize = 9.sp)
        }
    }
}

// ---------------------------------------------------------------- queue

@Composable
private fun RoomQueueStrip(
    queue: List<RoomQueueUi>,
    isHost: Boolean,
    onClearQueue: () -> Unit,
    onPlay: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = NoxaTextTertiary,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
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
                        .background(NoxaSurfaceElevated)
                        .border(1.dp, NoxaBorderSubtle, RoundedCornerShape(50))
                        .noxaTap(enabled = isHost) { onPlay(item.songId) }
                        .padding(start = 10.dp, end = if (isHost) 4.dp else 10.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            item.title,
                            color = NoxaTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 120.dp)
                        )
                        Text(
                            "${item.artist} · ${item.addedBy.ifBlank { "anggota" }}",
                            color = NoxaTextTertiary,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 130.dp)
                        )
                    }
                    if (isHost) {
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .noxaTap { onRemove(item.songId) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Hapus dari antrian",
                                tint = NoxaTextTertiary,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- host bar

/** Admin corner: NOXA allowance, auto-radio, search, and a hard stop. */
@Composable
private fun RoomHostBar(
    state: RoomUiState.InRoom,
    onAiToggle: (Boolean) -> Unit,
    onRadioToggle: (Boolean) -> Unit,
    onStopDj: () -> Unit,
    onFindSong: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "KONTROL ADMIN",
                color = NoxaTextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .noxaTap { expanded = !expanded },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Sembunyikan" else "Tampilkan",
                    tint = NoxaTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                AdminChip(
                    label = "Cari lagu",
                    active = true,
                    icon = Icons.Default.Search,
                    onClick = onFindSong
                )
            }
            item {
                AdminChip(
                    label = if (state.aiEnabled) "NOXA ON" else "NOXA OFF",
                    active = state.aiEnabled,
                    onClick = { onAiToggle(!state.aiEnabled) }
                )
            }
            item {
                AdminChip(
                    label = if (state.autoRadio) "Radio auto" else "Radio mati",
                    active = state.autoRadio,
                    onClick = { onRadioToggle(!state.autoRadio) }
                )
            }
            item {
                AdminChip(
                    label = "Stop NOXA",
                    active = false,
                    onClick = onStopDj
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(160)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(160))
        ) {
            Text(
                "Cari lagu memutar untuk semua anggota. Radio auto memilih lagu " +
                    "serupa saat antrian kosong. Stop NOXA menghentikan lagu dan " +
                    "mengosongkan antrian.",
                color = NoxaTextTertiary,
                fontSize = 10.sp,
                lineHeight = 14.sp,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp)
            )
        }
    }
}

@Composable
private fun AdminChip(
    label: String,
    active: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (active) NoxaLime.copy(alpha = 0.13f) else NoxaSurfaceSecondary)
            .border(
                1.dp,
                if (active) NoxaLime.copy(alpha = 0.45f) else NoxaBorderSubtle,
                RoundedCornerShape(50)
            )
            .noxaTap { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (active) NoxaLime else NoxaTextSecondary,
                modifier = Modifier.size(11.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            label,
            color = if (active) NoxaLime else NoxaTextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

// ------------------------------------------------------------------ chat

@Composable
private fun RoomEmptyPlayer(isHost: Boolean, onFindSong: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorderSubtle, RoundedCornerShape(22.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.GraphicEq,
            contentDescription = null,
            tint = NoxaTextTertiary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Room masih sunyi",
                color = NoxaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                if (isHost) "Cari lagu untuk semua, atau tag @noxa di obrolan."
                else "Tunggu admin memutar lagu, atau tag @noxa di obrolan.",
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        if (isHost) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(NoxaLime)
                    .noxaStrongTap { onFindSong() }
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text("Cari lagu", color = NoxaBlack, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RoomChatEmpty(onAsk: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Belum ada obrolan",
            color = NoxaTextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            "Sapa temanmu, atau ketik @ untuk minta lagu ke NOXA.",
            color = NoxaTextTertiary,
            fontSize = 11.sp,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                QuickAskChip("Minta lagu galau") { onAsk("@noxa puterin lagu galau") }
            }
            item {
                QuickAskChip("Siapa NOXA?") { onAsk("@noxa siapa kamu?") }
            }
            item {
                QuickAskChip("Lagu apa ini?") { onAsk("@noxa lagu apa ini?") }
            }
        }
    }
}

@Composable
private fun QuickAskChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorderSubtle, RoundedCornerShape(50))
            .noxaTap { onClick() }
            .padding(horizontal = 11.dp, vertical = 7.dp)
    ) {
        Text(label, color = NoxaTextSecondary, fontSize = 10.5.sp)
    }
}

@Composable
private fun RoomChatBubble(
    message: RoomChatUi,
    previous: RoomChatUi?,
    onRetry: () -> Unit
) {
    // Grouping: same sender, minutes apart? Keep the name once, tighten the
    // gap — the small detail that makes a chat feel like a chat.
    val sameSender = previous != null &&
        previous.fromAi == message.fromAi &&
        (message.fromAi || previous.name == message.name) &&
        message.at - previous.at in 0..180_000L
    val alignment = if (message.isSelf && !message.fromAi) Alignment.End else Alignment.Start
    val bubbleColor = when {
        message.fromAi -> NoxaLime.copy(alpha = 0.09f)
        message.isSelf -> NoxaLime.copy(alpha = 0.15f)
        else -> NoxaSurfaceElevated
    }
    val borderColor = when {
        message.failed -> NoxaEmber.copy(alpha = 0.5f)
        message.fromAi -> NoxaLime.copy(alpha = 0.4f)
        message.isSelf -> NoxaLime.copy(alpha = 0.25f)
        else -> NoxaBorderSubtle
    }
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (message.isSelf && !message.fromAi) 18.dp else 5.dp,
        bottomEnd = if (message.isSelf && !message.fromAi) 5.dp else 18.dp
    )

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) +
            slideInVertically(tween(200, easing = FastOutSlowInEasing)) { it / 3 }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (sameSender) 1.dp else 6.dp),
            horizontalAlignment = alignment
        ) {
            if (!sameSender) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (message.fromAi) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(NoxaLime.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = NoxaLime,
                                modifier = Modifier.size(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(5.dp))
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
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        clockOf(message.at),
                        color = NoxaTextTertiary.copy(alpha = 0.75f),
                        fontSize = 8.sp
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
            }
            Box(
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .clip(shape)
                    .background(bubbleColor)
                    .border(1.dp, borderColor, shape)
                    .then(if (message.failed) Modifier.noxaTap { onRetry() } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column {
                    Text(
                        message.text,
                        color = if (message.failed) NoxaTextSecondary else NoxaTextPrimary,
                        fontSize = 12.5.sp,
                        lineHeight = 17.sp
                    )
                    when {
                        message.failed -> {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "Gagal terkirim · ketuk untuk kirim ulang",            color = NoxaEmber,
                                    fontSize = 9.sp
                            )
                        }
                        message.pending -> {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "mengirim…",
                                color = NoxaTextTertiary,
                                fontSize = 9.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoxaTypingBubble() {
    Row(
        modifier = Modifier
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(NoxaLime.copy(alpha = 0.08f))
            .border(1.dp, NoxaLime.copy(alpha = 0.3f), RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypingDots()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "NOXA sedang mengetik…",
            color = NoxaTextSecondary,
            fontSize = 11.sp
        )
    }
}

/** Three dots that lift in sequence — calmer than a spinner. */
@Composable
private fun TypingDots() {
    val transition = rememberInfiniteTransition(label = "typing")
    val offsets = (0..2).map { index ->
        transition.animateFloat(
            initialValue = -1.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                tween(durationMillis = 460, delayMillis = index * 120),
                RepeatMode.Reverse
            ),
            label = "dot$index"
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
        offsets.forEach { dot ->
            Box(
                modifier = Modifier
                    .offset(y = dot.value.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(NoxaLime)
            )
        }
    }
}

@Composable
private fun RoomComposer(
    draft: String,
    aiEnabled: Boolean,
    onDraftChange: (String) -> Unit,
    onInsertHandle: () -> Unit,
    onSend: () -> Unit
) {
    val canSend = draft.isNotBlank()
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium),
        label = "send_scale"
    )
    val sendColor by animateColorAsState(
        targetValue = if (canSend) NoxaLime else NoxaSurfaceSecondary,
        animationSpec = tween(180),
        label = "send_color"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorderSubtle, RoundedCornerShape(50))
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .noxaTap { onInsertHandle() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Tag NOXA",
                tint = if (draft.startsWith("@")) NoxaLime else NoxaTextTertiary,
                modifier = Modifier.size(16.dp)
            )
        }
        TextField(
            value = draft,
            onValueChange = onDraftChange,
            placeholder = {
                Text(
                    if (aiEnabled) "Ketik pesan… atau @noxa"
                    else "Ketik pesan… (NOXA dimatikan)",
                    color = NoxaTextTertiary,
                    fontSize = 12.sp
                )
            },
            singleLine = true,
            shape = RoundedCornerShape(50),
            textStyle = TextStyle(fontSize = 13.sp, color = NoxaTextPrimary),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = NoxaLime
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("room_chat_input")
        )
        if (draft.length > 180) {
            Text(
                "${draft.length}/240",
                color = if (draft.length >= 240) NoxaEmber else NoxaTextTertiary,
                fontSize = 9.sp,
                modifier = Modifier.padding(end = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .scale(sendScale)
                .clip(CircleShape)
                .background(sendColor)
                .noxaTap(enabled = canSend) { onSend() }
                .testTag("room_chat_send"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Kirim",
                tint = if (canSend) NoxaBlack else NoxaTextTertiary,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun RoomAvatar(name: String, isSelf: Boolean, isHost: Boolean = false) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (isSelf) NoxaLime else NoxaPurple)
            .border(
                2.dp,
                if (isHost) NoxaLime.copy(alpha = 0.9f) else NoxaBlack,
                CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.trim().take(1).uppercase(Locale.ROOT).ifBlank { "?" },
            color = if (isSelf) NoxaBlack else Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text,
            color = color,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp
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

private fun clockOf(at: Long): String {
    if (at <= 0L) return ""
    return runCatching {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(at))
    }.getOrDefault("")
}

// ------------------------------------------------------------------ sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetGrabber() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp, bottom = 6.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(NoxaTextTertiary.copy(alpha = 0.5f))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomMembersSheet(
    state: RoomUiState.InRoom,
    onDismiss: () -> Unit,
    onCopyCode: () -> Unit,
    onShare: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NoxaBlack,
        dragHandle = { SheetGrabber() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp)
        ) {
            Text(
                "Pendengar · ${state.members.size}",
                color = NoxaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Admin room memegang kendali musik dan NOXA.",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            state.members.forEach { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NoxaSurfaceSecondary)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RoomAvatar(
                        name = member.name,
                        isSelf = member.isSelf,
                        isHost = member.uid == state.hostUid
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        member.name,
                        color = NoxaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (member.uid == state.hostUid) Badge("ADMIN", NoxaLime)
                    if (member.isSelf) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Badge("KAMU", NoxaTextSecondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetAction(
                    label = "Salin kode",
                    icon = Icons.Default.ContentCopy,
                    onClick = onCopyCode,
                    modifier = Modifier.weight(1f)
                )
                SheetAction(
                    label = "Bagikan",
                    icon = Icons.Default.Share,
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaLime.copy(alpha = 0.13f))
            .border(1.dp, NoxaLime.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .noxaTap { onClick() }
            .padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = NoxaLime,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(label, color = NoxaLime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Admin "putar untuk semua": a real catalogue search with a play button per
 * result. Before this the only way to change the room's song was `@noxa`, and
 * an admin who just wanted to pick a track had no door to knock on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoomSearchSheet(
    search: RoomSearchUi,
    onQuery: (String) -> Unit,
    onPlay: (Song) -> Unit,
    onQueue: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(220)
        runCatching { focus.requestFocus() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = NoxaBlack,
        dragHandle = { SheetGrabber() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 18.dp)
        ) {
            Text(
                "Putar untuk semua",
                color = NoxaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.2).sp
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                "Pilih lagu, semua anggota langsung berpindah bersama.",
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = search.query,
                onValueChange = onQuery,
                placeholder = {
                    Text("Cari judul atau artis", color = NoxaTextTertiary, fontSize = 13.sp)
                },
                singleLine = true,
                shape = RoundedCornerShape(50),
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = NoxaTextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                },
                trailingIcon = {
                    if (search.query.isNotEmpty()) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Hapus",
                            tint = NoxaTextTertiary,
                            modifier = Modifier
                                .size(16.dp)
                                .noxaTap { onQuery("") }
                        )
                    }
                },
                textStyle = TextStyle(fontSize = 13.sp, color = NoxaTextPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NoxaLime.copy(alpha = 0.6f),
                    unfocusedBorderColor = NoxaBorder,
                    focusedContainerColor = NoxaSurfaceElevated,
                    unfocusedContainerColor = NoxaSurfaceElevated,
                    cursorColor = NoxaLime
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
            )
            Spacer(modifier = Modifier.height(10.dp))
            when {
                search.loading -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        color = NoxaLime,
                        strokeWidth = 1.8.dp,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mencari di katalog…", color = NoxaTextSecondary, fontSize = 12.sp)
                }

                search.error != null -> Text(
                    search.error,                        color = NoxaEmber,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 14.dp)
                )

                search.results.isEmpty() -> Text(
                    if (search.query.isBlank()) {
                        "Ketik judul lagu atau nama artis untuk mulai mencari."
                    } else {
                        "Belum ada hasil. Coba kata kunci lain."
                    },
                    color = NoxaTextTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 14.dp)
                )

                else -> LazyColumn(
                    modifier = Modifier.heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(search.results, key = { it.id }) { song ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(NoxaSurfaceSecondary)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.artworkUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    color = NoxaTextPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    color = NoxaTextTertiary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NoxaSurfaceTertiary)
                                    .noxaTap { onQueue(song) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "Antrikan",
                                    tint = NoxaTextSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(NoxaLime)
                                    .noxaStrongTap { onPlay(song) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Putar sekarang",
                                    tint = NoxaBlack,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

