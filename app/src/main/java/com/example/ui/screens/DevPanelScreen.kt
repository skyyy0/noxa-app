package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.MarkChatUnread
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.support.AccountRole
import com.example.data.support.BackendLogin
import com.example.data.support.SupportThread
import com.example.ui.components.PasswordStrengthBar
import com.example.ui.components.SupportIdentityRow
import com.example.ui.components.VerifiedBadgePurple
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaError
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaSuccess
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** State of the NOXA Admin console. */
sealed interface DevUiState {
    data object Loading : DevUiState

    /** Backend unreachable. */
    data object Offline : DevUiState

    data class Ready(
        val accounts: List<BackendLogin>,
        val threads: List<SupportThread>,
        val busy: Boolean = false
    ) : DevUiState
}

private enum class DevTab(val label: String) {
    THREADS("Laporan Pengguna"),
    ACCOUNTS("Daftar Akun")
}

/**
 * **NOXA Admin** — Sky's developer console, reachable only from an account
 * whose role is [AccountRole.DEVELOPER].
 *
 * Everything the user asked for lives here:
 *  * the registered-account list with a live count,
 *  * every user support thread with instant reply (which unlocks the user's
 *    one-message-at-a-time composer),
 *  * "Buat Akun untuk User" with a role picker (Admin / Partner / Moderator /
 *    User) and a ready-to-share account info card after creation.
 */
@Composable
fun DevPanelScreen(
    state: DevUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onReply: (threadUid: String, text: String) -> Unit,
    onCreateAccount: (email: String, username: String, password: String, role: AccountRole, (String?) -> Unit) -> Unit,
    onChangeRole: (uid: String, role: AccountRole) -> Unit,
    modifier: Modifier = Modifier
) {
    var tab by remember { mutableStateOf(DevTab.THREADS) }
    var selectedThreadUid by remember { mutableStateOf<String?>(null) }
    var showCreateForm by remember { mutableStateOf(false) }

    // Centered + width-capped so the admin console reads well on tablets
    // and never hides its header under the status bar on any device.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack),
        contentAlignment = Alignment.Center
    ) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .widthIn(max = 680.dp)
            .fillMaxWidth()
            .background(NoxaBlack)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        DevHeader(
            showCreateForm = showCreateForm || selectedThreadUid != null,
            onBack = {
                if (selectedThreadUid != null) selectedThreadUid = null
                else if (showCreateForm) showCreateForm = false
                else onBack()
            },
            onRefresh = onRefresh
        )

        when (val s = state) {
            DevUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = NoxaPurple, strokeWidth = 2.5.dp) }

            DevUiState.Offline -> Column(
                modifier = Modifier.fillMaxSize().padding(28.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VerifiedBadgePurple(size = 48.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    "Server NOXA tidak terjangkau",
                    color = NoxaTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Admin console butuh koneksi internet untuk membaca daftar akun & laporan.",
                    color = NoxaTextSecondary,
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
                        .clickable { onRefresh() }
                        .padding(horizontal = 24.dp, vertical = 11.dp)
                ) {
                    Text("Coba lagi", color = NoxaPurple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            is DevUiState.Ready -> {
                val thread = selectedThreadUid?.let { uid -> s.threads.firstOrNull { it.uid == uid } }

                when {
                    showCreateForm -> CreateAccountForm(
                        busy = s.busy,
                        onCreate = onCreateAccount,
                        onDone = { showCreateForm = false },
                        onRefresh = onRefresh
                    )

                    selectedThreadUid != null && thread != null -> ThreadConversation(
                        thread = thread,
                        busy = s.busy,
                        onReply = onReply,
                        onRefresh = onRefresh
                    )

                    else -> {
                        DevStatsRow(accounts = s.accounts, threads = s.threads)
                        DevTabRow(
                            current = tab,
                            onSelect = { tab = it },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        when (tab) {
                            DevTab.THREADS -> ThreadsList(
                                threads = s.threads,
                                onSelect = { selectedThreadUid = it }
                            )

                            DevTab.ACCOUNTS -> AccountsList(
                                accounts = s.accounts,
                                onCreateClick = { showCreateForm = true },
                                onChangeRole = onChangeRole
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
private fun DevHeader(showCreateForm: Boolean, onBack: () -> Unit, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Kembali",
                tint = NoxaTextPrimary
            )
        }
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(NoxaPurple),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MarkChatUnread,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            SupportIdentityRow(name = "NOXA Admin")
            Text("Console developer", color = NoxaTextTertiary, fontSize = 11.sp)
        }
        IconButton(onClick = onRefresh) {
            Icon(Icons.Default.Refresh, contentDescription = "Muat ulang", tint = NoxaTextSecondary)
        }
    }
    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))
}

@Composable
private fun DevStatsRow(accounts: List<BackendLogin>, threads: List<SupportThread>) {
    val unread = threads.count { it.unreadForSupport }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatCard("Akun terdaftar", accounts.size.toString(), NoxaLime, Modifier.weight(1f))
        StatCard("Total laporan", threads.size.toString(), NoxaTextPrimary, Modifier.weight(1f))
        StatCard("Menunggu balasan", unread.toString(), NoxaPurple, Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(label, color = NoxaTextTertiary, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DevTabRow(current: DevTab, onSelect: (DevTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NoxaSurfaceElevated)
            .border(1.dp, NoxaBorder, RoundedCornerShape(12.dp))
            .padding(4.dp)
    ) {
        DevTab.entries.forEach { entry ->
            val selected = entry == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) NoxaPurple else Color.Transparent)
                    .clickable { onSelect(entry) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    entry.label,
                    color = if (selected) Color.White else NoxaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ----------------------------------------------------------------- threads

@Composable
private fun ThreadsList(threads: List<SupportThread>, onSelect: (String) -> Unit) {
    if (threads.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Belum ada laporan dari pengguna.\nChat support dari aplikasi pengguna akan muncul di sini.",
                color = NoxaTextTertiary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 19.sp
            )
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        items(threads, key = { it.uid }) { thread ->
            val last = thread.lastMessage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (thread.unreadForSupport) NoxaSurfaceSecondary else NoxaSurfaceElevated
                    )
                    .border(
                        1.dp,
                        if (thread.unreadForSupport) NoxaPurple.copy(alpha = 0.6f) else NoxaBorder,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onSelect(thread.uid) }
                    .padding(13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(NoxaPurpleDeep.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        (thread.username.take(1) ?: "?").uppercase(Locale.ROOT),
                        color = NoxaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Spacer(modifier = Modifier.width(11.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            thread.username.ifBlank { thread.uid },
                            color = NoxaTextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (thread.unreadForSupport) {
                            Spacer(modifier = Modifier.width(7.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NoxaPurple)
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text("BARU", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    }
                    Text(
                        last?.text ?: "Thread dibuat",
                        color = NoxaTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    THREAD_TIME.format(Date(thread.lastUserAt.coerceAtLeast(thread.lastSupportAt))),
                    color = NoxaTextTertiary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ThreadConversation(
    thread: SupportThread,
    busy: Boolean,
    onReply: (String, String) -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    var draft by remember { mutableStateOf("") }

    LaunchedEffect(thread.messages.size) {
        if (thread.messages.isNotEmpty()) listState.animateScrollToItem(thread.messages.size - 1)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Thread identity strip.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Laporan dari @${thread.username.ifBlank { thread.uid }}",
                    color = NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (thread.canUserSend) "Menunggu balasan kamu" else "User menunggu balasan kamu",
                    color = if (thread.unreadForSupport) NoxaPurple else NoxaTextTertiary,
                    fontSize = 11.sp
                )
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (thread.messages.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "Belum ada pesan di thread ini.",
                        color = NoxaTextTertiary,
                        fontSize = 13.sp
                    )
                }
            }
            items(thread.messages, key = { "${it.at}-${it.from}" }) { message ->
                SupportBubble(message = message, isSupport = message.from == "support")
            }
        }

        // Staff reply composer — always available (Support never gets locked).
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = {
                    Text("Balas pengguna...", color = NoxaTextTertiary, fontSize = 13.sp)
                },
                maxLines = 4,
                shape = RoundedCornerShape(22.dp),
                colors = devFieldColors(),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty() && !busy) {
                        draft = ""
                        onReply(thread.uid, text)
                        onRefresh()
                    }
                },
                enabled = draft.isNotBlank() && !busy,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (draft.isNotBlank() && !busy) NoxaPurple else NoxaSurfaceSecondary)
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Kirim balasan",
                        tint = if (draft.isNotBlank()) Color.White else NoxaTextTertiary
                    )
                }
            }
        }
    }
}

// ----------------------------------------------------------------- accounts

@Composable
private fun AccountsList(
    accounts: List<BackendLogin>,
    onCreateClick: () -> Unit,
    onChangeRole: (String, AccountRole) -> Unit
) {
    var roleTarget by remember { mutableStateOf<BackendLogin?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            items(accounts, key = { it.uid }) { account ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaSurfaceElevated)
                        .border(1.dp, NoxaBorder, RoundedCornerShape(14.dp))
                        .clickable { roleTarget = account }
                        .padding(13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(roleColor(account.role).copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            account.username.take(1).uppercase(Locale.ROOT),
                            color = roleColor(account.role),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "@${account.username}",
                                color = NoxaTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (account.role == AccountRole.DEVELOPER) {
                                Spacer(modifier = Modifier.width(5.dp))
                                VerifiedBadgePurple(size = 13.dp)
                            }
                        }
                        Text(
                            account.email,
                            color = NoxaTextSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    RoleChip(account.role)
                }
            }
        }

        // Floating create button.
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(NoxaPurple)
                .clickable { onCreateClick() }
                .padding(horizontal = 18.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Buat Akun", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    roleTarget?.let { account ->
        RolePickerDialog(
            title = "Role untuk @${account.username}",
            selected = account.role,
            onSelect = { role ->
                onChangeRole(account.uid, role)
                roleTarget = null
            },
            onDismiss = { roleTarget = null }
        )
    }
}

@Composable
private fun RoleChip(role: AccountRole) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(roleColor(role).copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            role.label,
            color = roleColor(role),
            fontSize = 10.sp,
            fontWeight = FontWeight.Black
        )
    }
}

private fun roleColor(role: AccountRole): Color = when (role) {
    AccountRole.DEVELOPER -> NoxaPurple
    AccountRole.ADMIN -> NoxaLime
    AccountRole.PARTNER -> NoxaPurpleDeep
    AccountRole.MODERATOR -> Color(0xFFFFB340)
    AccountRole.USER -> NoxaTextSecondary
}

@Composable
private fun RolePickerDialog(
    title: String,
    selected: AccountRole,
    onSelect: (AccountRole) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoxaSurfaceElevated,
        title = { Text(title, color = NoxaTextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                AccountRole.entries.filter { it != AccountRole.DEVELOPER }.forEach { role ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (role == selected) NoxaPurple.copy(alpha = 0.15f) else Color.Transparent
                            )
                            .clickable { onSelect(role) }
                            .padding(vertical = 10.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(roleColor(role))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(role.label, color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        if (role == selected) {
                            Spacer(modifier = Modifier.weight(1f))
                            Text("dipilih", color = NoxaPurple, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Text(
                "Tutup",
                color = NoxaTextSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    )
}

// ----------------------------------------------------------- create account

@Composable
private fun CreateAccountForm(
    busy: Boolean,
    onCreate: (email: String, username: String, password: String, role: AccountRole, (String?) -> Unit) -> Unit,
    onDone: () -> Unit,
    onRefresh: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(AccountRole.PARTNER) }
    var error by remember { mutableStateOf<String?>(null) }
    var created by remember { mutableStateOf<BackendLogin?>(null) }

    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Buat akun untuk user",
                color = NoxaTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            VerifiedBadgePurple(size = 16.dp)
        }
        Text(
            "Akun langsung terdaftar di server NOXA dan siap dikirim ke pengguna.",
            color = NoxaTextSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 14.dp)
        )

        if (created != null) {
            CreatedAccountCard(created = created!!, clipboard = clipboard, context = context)
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                androidx.compose.material3.TextButton(onClick = { created = null; email = ""; username = ""; password = "" }) {
                    Text("Buat lagi", color = NoxaLime, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.TextButton(onClick = { onDone(); onRefresh() }) {
                    Text("Selesai", color = NoxaTextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            return
        }

        Text("Email", color = NoxaTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(5.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it; error = null },
            singleLine = true,
            placeholder = { Text("contoh: partner@noxa.com", color = NoxaTextTertiary, fontSize = 13.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            colors = devFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Username", color = NoxaTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(5.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; error = null },
            singleLine = true,
            placeholder = { Text("unik, 3-20 karakter", color = NoxaTextTertiary, fontSize = 13.sp) },
            colors = devFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))
        Text("Password", color = NoxaTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(5.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; error = null },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = devFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (password.isNotEmpty()) {
            Spacer(modifier = Modifier.height(7.dp))
            PasswordStrengthBar(password = password)
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text("Role", color = NoxaTextSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(AccountRole.ADMIN, AccountRole.PARTNER, AccountRole.MODERATOR, AccountRole.USER)
                .forEach { option ->
                    val selected = option == role
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (selected) roleColor(option).copy(alpha = 0.22f) else NoxaSurfaceElevated
                            )
                            .border(
                                1.dp,
                                if (selected) roleColor(option) else NoxaBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { role = option }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            option.label,
                            color = if (selected) roleColor(option) else NoxaTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
        }

        error?.let {
            Spacer(modifier = Modifier.height(10.dp))
            Text(it, color = NoxaError, fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(18.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (busy) NoxaSurfaceSecondary else NoxaPurple)
                .clickable(enabled = !busy) {
                    error = null
                    onCreate(email.trim(), username.trim(), password, role) { result ->
                        if (result == null) {
                            created = BackendLogin(
                                uid = "",
                                email = email.trim(),
                                username = username.trim(),
                                role = role,
                                createdAt = System.currentTimeMillis()
                            )
                        } else if (result == "server_noxa_tidak_tersedia") {
                            error = "Server NOXA tidak terjangkau. Coba lagi."
                        } else {
                            error = result
                        }
                    }
                }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Buat Akun & Info", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun CreatedAccountCard(
    created: BackendLogin,
    clipboard: androidx.compose.ui.platform.ClipboardManager,
    context: Context
) {
    val info = "Akun NOXA\nEmail: ${created.email}\nUsername: ${created.username}\nRole: ${created.role.label}\nBuka aplikasi NOXA → Profile → Masuk dengan email"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NoxaSurfaceElevated)
            .border(1.5.dp, NoxaSuccess.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(NoxaSuccess.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = NoxaSuccess,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                "Akun dibuat — siap dikirim",
                color = NoxaTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Email", color = NoxaTextTertiary, fontSize = 11.sp)
        Text(created.email, color = NoxaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Username", color = NoxaTextTertiary, fontSize = 11.sp)
        Text("@${created.username}", color = NoxaTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Role", color = NoxaTextTertiary, fontSize = 11.sp)
        Text(created.role.label, color = created.role.let { roleColor(it) }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(NoxaPurple.copy(alpha = 0.16f))
                .clickable {
                    clipboard.setText(AnnotatedString(info))
                    android.widget.Toast.makeText(context, "Info akun disalin", android.widget.Toast.LENGTH_SHORT).show()
                }
                .padding(vertical = 11.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.ContentCopy,
                contentDescription = null,
                tint = NoxaPurple,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text("Salin info akun", color = NoxaPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// -------------------------------------------------------------------- misc

@Composable
internal fun devFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NoxaPurple,
    unfocusedBorderColor = NoxaBorder,
    focusedTextColor = NoxaTextPrimary,
    unfocusedTextColor = NoxaTextPrimary,
    cursorColor = NoxaPurple,
    focusedContainerColor = NoxaSurfaceSecondary,
    unfocusedContainerColor = NoxaSurfaceSecondary
)

private val THREAD_TIME = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
