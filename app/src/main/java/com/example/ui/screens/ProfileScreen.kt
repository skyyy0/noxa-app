package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.local.AppSettings
import com.example.data.local.AppSettingsStore
import com.example.data.local.HeatmapStats
import com.example.data.local.ListeningHeatmap
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaEmber
import com.example.ui.theme.NoxaError
import com.example.ui.theme.NoxaHaze
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaRadius
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaSurfaceTertiary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.components.noxaAppear
import com.example.ui.components.noxaTap
import com.example.ui.components.SectionHeader
import com.example.ui.components.SettingsChoiceRow
import com.example.ui.components.SettingsGroup
import com.example.ui.components.SettingsRow
import com.example.ui.components.SettingsRowDivider
import com.example.ui.components.SettingsSectionLabel
import com.example.ui.components.SettingsToggleRow
import com.example.data.local.JournalMoods
import com.example.data.local.JournalStore
import com.example.data.local.QuestEngine
import com.example.data.support.SongGift
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Redeem
import com.example.ui.viewmodel.WeeklyRecap
import com.example.ui.viewmodel.YearListeningUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ProfileScreen(
    currentUser: com.example.data.auth.NoxaUser?,
    isSigningIn: Boolean,
    weeklyRecap: WeeklyRecap,
    /** Year-long heatmap + mini-wrapped (null stats = belum ada data). */
    yearListening: YearListeningUi = YearListeningUi.EMPTY,
    appSettings: AppSettings,
    onSignInWithGoogle: (android.app.Activity, (String) -> Unit) -> Unit,
    onSignInWithEmail: (String, String, (String) -> Unit) -> Unit,
    onRegisterWithEmail: (String, String, String, (String) -> Unit) -> Unit,
    onSetMvQuality: (Int) -> Unit,
    onSetAutoplaySimilar: (Boolean) -> Unit,
    onSetKaraokeOverlay: (Boolean) -> Unit,
    onSetAutoResumeOnOpen: (Boolean) -> Unit,
    /** v2.0.0 — crossfade lembut antar lagu (Sistem Pintar mematikan di HP kelas Hemat). */
    onSetCrossfade: (Boolean) -> Unit = {},
    /** v2.0.0 — profil perangkat hasil pemindaian sekali di awal. */
    perfProfile: com.example.data.local.PerfProfile? = null,
    /** v2.0.0 — daftar senyap "Jangan putar lagi" untuk dibatalkan di sini. */
    mutedArtists: Set<String> = emptySet(),
    mutedSongs: Set<String> = emptySet(),
    onUnmute: (String, String) -> Unit = { _, _ -> },
    onSignOut: () -> Unit,
    onNavigateToAboutSky: () -> Unit,
    onOpenAiChat: () -> Unit,
    /** Dengar Bareng — shared listening room. */
    onOpenRoom: () -> Unit,
    onOpenSupportChat: () -> Unit,
    onOpenDevPanel: () -> Unit,
    /** Opens the "Get Started" sign-in hub (Google / Email / Phone OTP). */
    onOpenGetStarted: () -> Unit,
    /** Opens the full NOXA Wrapped tahunan screen. */
    onOpenWrapped: () -> Unit = {},
    /** null = sukses, String = pesan error. */
    onChangeUsername: (String, (String?) -> Unit) -> Unit,
    /** null = sukses, String = pesan error. */
    onChangePassword: (String, String, (String?) -> Unit) -> Unit,
    /** null = sukses, String = pesan error. */
    onDeleteAccount: (String, (String?) -> Unit) -> Unit,
    /** Latest published update newer than this build (null = up to date). */
    availableUpdate: com.example.data.support.NoxaBackend.UpdateInfo? = null,
    /** Reopens the update popup from Pengaturan → Pembaruan. */
    onOpenUpdate: () -> Unit = {},
    /** Pengaturan → Bahasa. Default Indonesia (salinan app hari ini). */
    language: com.example.data.local.AppLanguage = com.example.data.local.AppLanguage.ID,
    onSetLanguage: (com.example.data.local.AppLanguage) -> Unit = {},
    /** v3.3.3 — Kirim Lagu: kotak masuk kiriman lagu dari teman. */
    gifts: List<SongGift> = emptyList(),
    /** Refresh inbox dibuka sekali tiap profil dibuka (jaringan). */
    onRefreshGifts: () -> Unit = {},
    /** Putar lagu yang dikirim teman (dipanggil saat baris kiriman ditekan). */
    onPlayGift: (SongGift) -> Unit = {},
    /** Tandai kiriman sudah dibaca (optimistik di UI + tulis ke backend). */
    onMarkGiftRead: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // ------------------------------------------------- v3.0.0: NOXA Wake
    // Status layanan dibaca langsung dari state global service — mati karena
    // apa pun (sistem, tombol Berhenti, swipe) switch-nya ikut balik sendiri.
    val wakeRunning by com.example.voice.NoxaWakeService.isRunning.collectAsState()
    var wakeOverlayAttempted by remember { mutableStateOf(false) }

    // Var kosong yang diisi ulang di bawah: callback izin (yang dibuat
    // sebelum baris ini dievaluasi ulang) memanggil lewat referensi ini,
    // jadi selalu menunjuk versi terbaru — urutan deklarasi lokal tidak saling
    // mengunci.
    var startNoxaWake: () -> Unit = {}

    val wakeMicLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startNoxaWake()
        } else {
            Toast.makeText(
                context,
                "Izin mikrofon dibutuhkan NOXA Wake untuk mendengarkan \u201cHi NOXA\u201d.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    val wakeOverlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // Kembali dari Pengaturan sistem: cek ulang. wakeOverlayAttempted
        // mencegah loop kalau izinnya tidak juga diberikan.
        startNoxaWake()
    }

    startNoxaWake = {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED -> {
                wakeMicLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }

            !Settings.canDrawOverlays(context) -> {
                if (wakeOverlayAttempted) {
                    Toast.makeText(
                        context,
                        "Izin \u201cGambar di atas aplikasi lain\u201d belum diberikan — " +
                            "bubble NOXA Wake butuh izin itu.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    wakeOverlayAttempted = true
                    wakeOverlayLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                    )
                }
            }

            else -> {
                try {
                    ContextCompat.startForegroundService(
                        context,
                        Intent(context, com.example.voice.NoxaWakeService::class.java)
                    )
                    wakeOverlayAttempted = false
                    Toast.makeText(
                        context,
                        "NOXA Wake menyala — bilang \u201cHi NOXA\u201d kapan saja.",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        "NOXA Wake gagal menyala: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    var selectedQuality by remember { mutableStateOf("Hi-Res Lossless (24-bit/96kHz)") }
    var cacheCleared by remember { mutableStateOf(false) }

    // Account settings dialogs.
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showLanguagePicker by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    // Sign-out confirmation & permanent account deletion.
    var showSignOutDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    // Floating QRIS popup for supporting the developer.
    var showQrisDialog by remember { mutableStateOf(false) }

    // Ambient wash hidup di belakang layar ini (dibungkus pemanggil di
    // NoxaApp), sehingga konten profil duduk di atas cahaya yang sama dengan
    // Home — bukan di atas hitam datar. Layar ini sendiri transparan.
    LaunchedEffect(currentUser?.uid) { onRefreshGifts() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(bottom = 120.dp)
            .testTag("profile_screen")
    ) {        // ---------------------------------------------------- Hero header
        // Same header language as Home: a tracked greeting eyebrow, the name
        // at display size, avatar at the trailing edge. Opening the profile
        // the way Home opens makes both pages read as one product.
        val greeting = remember {
            when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
                in 4..11 -> "Selamat pagi"
                in 12..16 -> "Selamat siang"
                else -> "Selamat malam"
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .noxaAppear()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = greeting,
                    color = NoxaTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.4.sp
                )
                Text(
                    text = currentUser?.displayName ?: "Mode Tamu",
                    color = NoxaTextPrimary,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Handle hanya ditampilkan saat bentuknya username unik;
                    // nama Google berspasi tetap tampil sebagai email/hint.
                    val handle = currentUser?.displayName
                    if (handle != null && USERNAME_HANDLE.matches(handle)) {
                        Text(
                            text = "@$handle",
                            color = NoxaTextTertiary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("profile_username")
                        )
                    } else {
                        Text(
                            text = currentUser?.email ?: "Google · Email · Phone (OTP)",
                            color = NoxaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (currentUser?.role == com.example.data.support.AccountRole.DEVELOPER) {
                        Spacer(modifier = Modifier.width(6.dp))
                        com.example.ui.components.VerifiedBadgePurple(size = 14.dp)
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Developer",
                            color = NoxaPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            if (currentUser?.photoUrl != null) {
                coil.compose.AsyncImage(
                    model = currentUser.photoUrl,
                    contentDescription = "Avatar",
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        // Neutral edge, not a brand ring: same rule as Home.
                        .border(1.dp, NoxaBorder, CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(NoxaSurfaceSecondary)
                        .border(1.dp, NoxaBorder, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = (currentUser?.displayName ?: "G").take(1).uppercase(),
                        color = NoxaTextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ------------------------------------------------- Glance statistics
        // Tiga angka ringkas dari data yang sama dengan heatmap: sekilas
        // status dengar tanpa harus scroll ke kartu statistik di bawah.
        val statsNow = yearListening.stats
        if (statsNow != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    "Streak" to "${statsNow.currentStreak} hari",
                    "Lagu tahun ini" to "${statsNow.totalPlays}",
                    "Hari aktif" to "${statsNow.activeDays}"
                ).forEach { (label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(NoxaRadius.md))
                            .background(NoxaSurfaceElevated)
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = value,
                            color = NoxaLime,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                        Text(
                            text = label,
                            color = NoxaTextTertiary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Guest CTA — semua metode masuk tetap lewat satu hub.
        if (currentUser == null) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                androidx.compose.material3.Button(
                    onClick = onOpenGetStarted,
                    enabled = !isSigningIn,
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = NoxaLime,
                        contentColor = NoxaBlack
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("get_started_btn")
                ) {
                    Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Google · Email · Phone (OTP)",
                    color = NoxaTextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ------------------------------------------------------- Grup AKUN
        // Satu grup inset berisi semua tindakan akun — termasuk Sign Out yang
        // dulu berdiri sendiri di kartu identitas.
        if (currentUser != null) {
            SettingsSectionLabel("Akun")
            SettingsGroup(tag = "account_group") {
                SettingsRow(
                    icon = Icons.Default.Person,
                    iconTint = NoxaHaze,
                    title = "Ganti Username",
                    subtitle = "Username tampil di profil & chat support",
                    onClick = { showUsernameDialog = true },
                    tag = "settings_change_username"
                )
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Security,
                    iconTint = NoxaHaze,
                    title = "Ganti Password",
                    subtitle = "Verifikasi password lama dulu",
                    onClick = { showPasswordDialog = true },
                    tag = "settings_change_password"
                )
                if (currentUser.email != null) {
                    SettingsRowDivider()
                    SettingsRow(
                        icon = Icons.Default.Delete,
                        iconTint = NoxaError,
                        title = "Hapus Akun",
                        subtitle = "Permanen — data akun & chat support ikut terhapus",
                        titleColor = NoxaError,
                        onClick = { showDeleteAccountDialog = true },
                        tag = "settings_delete_account"
                    )
                }
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.ExitToApp,
                    iconTint = NoxaError,
                    title = "Keluar",
                    subtitle = "Akhiri sesi di perangkat ini",
                    titleColor = NoxaError,
                    onClick = { showSignOutDialog = true },
                    tag = "settings_sign_out"
                )
            }
        }

        // ---------------------------------------------- NOXA Quest (3.3.3)
        // Misi mingguan yang dihitung murni dari data dengar di perangkat —
        // tidak ada server, tidak ada klaim: selesai = progress penuh.
        val quests = remember(weeklyRecap, yearListening) {
            QuestEngine.weekly(
                playsWeek = weeklyRecap.plays,
                topSongPlays = weeklyRecap.topSongPlays,
                currentStreak = yearListening.stats?.currentStreak ?: 0L
            )
        }
        SettingsSectionLabel("NOXA Quest")
        SettingsGroup(tag = "quest_card") {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Misi minggu ini",
                    color = NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${quests.count { it.completed }}/${quests.size} selesai",
                    color = NoxaLime,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            SettingsRowDivider(hasIcon = false)
            quests.forEachIndexed { index, quest ->
                if (index > 0) SettingsRowDivider(hasIcon = false)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 11.dp)
                        .testTag("quest_${quest.id}"),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = questIcon(quest.id),
                            contentDescription = null,
                            tint = if (quest.completed) NoxaLime else NoxaTextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(9.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quest.title,
                                color = NoxaTextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = quest.hint,
                                color = NoxaTextTertiary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        if (quest.completed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selesai",
                                tint = NoxaLime,
                                modifier = Modifier.size(17.dp)
                            )
                        } else {
                            Text(
                                text = "${quest.progress}/${quest.target}",
                                color = NoxaTextSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // Progress bar: tonal track + lime fill — lime hanya
                    // saat sedang bergerak, sesuai aturan sinyal merek.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(NoxaRadius.full))
                            .background(NoxaSurfaceSecondary)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(quest.fraction)
                                .height(5.dp)
                                .clip(RoundedCornerShape(NoxaRadius.full))
                                .background(NoxaLime)
                        )
                    }
                }
            }
        }

        // ------------------------------------------- Jurnal Dengar (3.3.3)
        // Mood hari ini + tujuh hari terakhir. Mood disimpan lokal per hari
        // (epoch day yang sama dengan heatmap), jadi sejarah kecil tersimpan
        // tanpa akun dan tanpa jaringan.
        val journal = remember(context) { JournalStore(context) }
        val todayIndex = remember { ListeningHeatmap.dayIndex(System.currentTimeMillis()) }
        var todayMood by remember { mutableStateOf(journal.moodFor(todayIndex)) }
        val lastSevenDays = remember(yearListening) {
            yearListening.stats?.cells?.takeLast(7).orEmpty()
        }
        val weekMoods = remember(todayMood) {
            journal.moodsFor(lastSevenDays.map { it.dayIndex })
        }
        SettingsSectionLabel("Jurnal Dengar")
        SettingsGroup(tag = "journal_card") {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MenuBook,
                    contentDescription = null,
                    tint = NoxaEmber,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hari ini",
                    color = NoxaTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${lastSevenDays.lastOrNull()?.plays ?: 0} lagu",
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            SettingsRowDivider(hasIcon = false)

            // Pilih mood hari ini — chip nada, bukan emoji: emoji tidak
            // konsisten di launcher OEM, chip warna selalu terbaca.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                JournalMoods.ALL.forEach { mood ->
                    val selected = todayMood == mood.id
                    val moodColor = journalColor(mood.id)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(NoxaRadius.full))
                            .background(
                                if (selected) moodColor.copy(alpha = 0.22f) else NoxaSurfaceSecondary
                            )
                            .noxaTap {
                                todayMood = if (selected) null else mood.id
                                journal.setMood(todayIndex, if (selected) "" else mood.id)
                            }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                            .testTag("journal_mood_${mood.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mood.label,
                            color = if (selected) moodColor else NoxaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Tujuh hari terakhir: bar kecil per hari (intensitas lime) +
            // titik mood bila hari itu diberi catatan.
            if (lastSevenDays.isNotEmpty()) {
                SettingsRowDivider(hasIcon = false)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val best = (lastSevenDays.maxOfOrNull { it.plays } ?: 0).coerceAtLeast(1)
                    lastSevenDays.forEach { cell ->
                        val mood = weekMoods[cell.dayIndex]
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((8 + 26 * cell.plays / best).dp)
                                    .clip(RoundedCornerShape(NoxaRadius.xs))
                                    .background(
                                        if (cell.plays > 0) NoxaLime.copy(
                                            alpha = 0.35f + 0.65f * cell.plays / best
                                        ) else NoxaSurfaceSecondary
                                    )
                            )
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .clip(CircleShape)
                                    .background(mood?.let { journalColor(it) } ?: NoxaSurfaceTertiary)
                            )
                        }
                    }
                }
            }
        }

        // --------------------------------------------- Kirim Lagu (3.3.3)
        // Inbox kiriman teman — hanya untuk akun masuk. Kirim lagunya lewat
        // menu Bagikan di pemutar/list; halaman ini menerima & memutar.
        if (currentUser != null) {
            val unread = gifts.count { !it.read }
            SettingsSectionLabel("Lagu untukmu")
            SettingsGroup(tag = "gifts_inbox") {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Redeem,
                        contentDescription = null,
                        tint = NoxaPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Kiriman teman",
                        color = NoxaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (unread > 0) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(NoxaRadius.full))
                                .background(NoxaLime)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "$unread baru",
                                color = NoxaBlack,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
                if (gifts.isEmpty()) {
                    Text(
                        text = "Belum ada lagu masuk. Minta teman kirim lewat menu " +
                            "Bagikan di lagu — nanti muncul di sini.",
                        color = NoxaTextTertiary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                    )
                } else {
                    gifts.take(8).forEachIndexed { index, gift ->
                        if (index > 0) SettingsRowDivider(hasIcon = false)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .noxaTap {
                                    onPlayGift(gift)
                                    if (!gift.read) onMarkGiftRead(gift.id)
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .testTag("gift_row_${gift.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            coil.compose.AsyncImage(
                                model = gift.artwork,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(NoxaRadius.xs))
                                    .background(NoxaSurfaceSecondary)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (!gift.read) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(NoxaLime)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    Text(
                                        text = gift.title,
                                        color = NoxaTextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "dari ${gift.fromName} · ${gift.artist}",
                                    color = NoxaTextSecondary,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (gift.message.isNotBlank()) {
                                    Text(
                                        text = "\u201c${gift.message}\u201d",
                                        color = NoxaTextTertiary,
                                        fontSize = 11.sp,
                                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Putar",
                                tint = NoxaLime,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // MINI WEEKLY RECAP — shelf header + grup inset (tanpa border).
        SectionHeader(title = "Rekap minggu ini", subtitle = "Dari 7 hari terakhir")
        Spacer(modifier = Modifier.height(10.dp))

        SettingsGroup(tag = "weekly_recap_card") {
            if (weeklyRecap.plays == 0) {
                Text(
                    text = "Belum ada lagu yang diputar minggu ini. Putar lagu dulu ya.",
                    color = NoxaTextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp)
                )
            } else {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RecapRow(Icons.Default.PlayArrow, "${weeklyRecap.plays} lagu diputar 7 hari terakhir")
                    if (weeklyRecap.topArtist.isNotBlank()) {
                        RecapRow(Icons.Default.Star, "Artis teratas: ${weeklyRecap.topArtist}")
                    }
                    if (weeklyRecap.topSongTitle.isNotBlank()) {
                        RecapRow(
                            Icons.Default.MusicNote,
                            "Lagu paling sering: ${weeklyRecap.topSongTitle} " +
                                "(${weeklyRecap.topSongPlays}×)"
                        )
                    }
                }
            }
        }

        // PETA PANAS DENGAR — a year of listening, day by day.
        ListeningHeatmapCard(yearListening = yearListening)

        // NOXA WRAPPED MINI — the deep recap.
        WrappedMiniCard(yearListening = yearListening, onOpenWrapped = onOpenWrapped)

        // KARTU PROFIL NOXA — the shareable identity card.
        SectionHeader(
            title = "Kartu Profil",
            subtitle = "Avatar, nama, dan statistik dalam satu gambar"
        )
        Spacer(modifier = Modifier.height(10.dp))
        NoxaProfileCard(currentUser = currentUser, yearListening = yearListening)

        // JELAJAHI — About Sky, NOXA AI, dan Dengar Bareng dalam satu grup
        // inset: baris biasa dengan chevron, bukan tiga kartu berbingkai.
        SettingsSectionLabel("Jelajahi")
        SettingsGroup(tag = "explore_group") {
            SettingsRow(
                icon = Icons.Default.Code,
                iconTint = NoxaLime,
                title = "About Sky",
                subtitle = "Meet the architect behind NOXA and Sky Vibes",
                onClick = onNavigateToAboutSky,
                tag = "about_sky_nav_card",
                trailing = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(NoxaRadius.xs))
                            .background(NoxaLime.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "CREATOR",
                            color = NoxaLime,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            )
            SettingsRowDivider()
            SettingsRow(
                title = "Chat dengan NOXA AI",
                subtitle = "Minta putar lagu, bikin playlist, atau curhat",
                leading = { com.example.ui.screens.AiAvatar(size = 30.dp) },
                onClick = onOpenAiChat,
                tag = "ai_chat_nav_card"
            )
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Default.Group,
                iconTint = NoxaHaze,
                title = "Dengar Bareng",
                subtitle = "Bikin room, kode — lagu & posisi sinkron bareng teman",
                onClick = onOpenRoom,
                tag = "room_nav_card"
            )
        }

        // BANTUAN — support + admin (admin hanya untuk akun developer).
        SettingsSectionLabel("Bantuan")
        SettingsGroup(tag = "help_group") {
            SettingsRow(
                icon = Icons.Default.SupportAgent,
                iconTint = NoxaPurple,
                title = "Chat dengan NOXA Support",
                subtitle = "Lapor bug atau keluhan — dibalas langsung di aplikasi",
                onClick = onOpenSupportChat,
                tag = "support_chat_nav_card"
            )
            if (currentUser?.role == com.example.data.support.AccountRole.DEVELOPER) {
                SettingsRowDivider()
                SettingsRow(
                    icon = Icons.Default.Star,
                    iconTint = NoxaPurple,
                    title = "NOXA Admin",
                    subtitle = "Kelola akun, buat admin/partner, balas laporan user",
                    onClick = onOpenDevPanel,
                    tag = "dev_panel_nav_card"
                )
            }
        }

        if (showQrisDialog) {
            QrisSupportDialog(onDismiss = { showQrisDialog = false })
        }

        // TAUTAN — situs, GitHub, Telegram, QRIS dalam satu grup.
        SettingsSectionLabel("Tautan")
        SettingsGroup(tag = "links_group") {
            SettingsRow(
                icon = Icons.Default.Language,
                iconTint = NoxaLime,
                title = "Official Website",
                value = "skyvibes.my.id",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://skyvibes.my.id"))
                    context.startActivity(intent)
                },
                tag = "link_website"
            )
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Default.Code,
                iconTint = NoxaTextSecondary,
                title = "GitHub Profile",
                value = "skyyy0",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/skyyy0"))
                    context.startActivity(intent)
                },
                tag = "link_github"
            )
            SettingsRowDivider()
            SettingsRow(
                // Telegram paper-plane digambar sebagai vektor: Material tidak
                // punya glif Telegram dan emoji tidak konsisten di kartu.
                icon = TelegramIcons.Plane,
                iconTint = Color(0xFF229ED9),
                title = "Telegram Sky",
                value = "@sekaynotdev",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/sekaynotdev"))
                    context.startActivity(intent)
                },
                tag = "link_telegram"
            )
            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Default.QrCode,
                iconTint = NoxaPurple,
                title = "Support Developer NOXA",
                subtitle = "Scan QRIS — dukung pembangunan & pengembangan NOXA",
                onClick = { showQrisDialog = true },
                tag = "link_qris"
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Audio & Playback Settings — pilihan kualitas dalam satu grup inset.
        SettingsSectionLabel("Kualitas Audio")
        SettingsGroup(tag = "audio_quality_group") {
            listOf(
                "Hi-Res Lossless (24-bit/96kHz)",
                "High Quality (320 kbps)",
                "Standard (192 kbps)"
            ).forEachIndexed { index, quality ->
                if (index > 0) SettingsRowDivider(hasIcon = false)
                SettingsChoiceRow(
                    label = quality,
                    selected = selectedQuality == quality,
                    onClick = { selectedQuality = quality },
                    tag = "quality_${quality.hashCode()}"
                )
            }
        }

        // PLAYER FEATURES — MV quality + toggles dalam satu grup.
        SettingsSectionLabel("Fitur Pemutar")
        SettingsGroup(tag = "player_features_group") {
            Text(
                text = "Kualitas video MV",
                color = NoxaTextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            AppSettingsStore.MV_QUALITY_CHOICES.forEachIndexed { index, height ->
                if (index > 0) SettingsRowDivider(hasIcon = false)
                SettingsChoiceRow(
                    label = "${height}p",
                    selected = appSettings.mvQualityHeight == height,
                    onClick = { onSetMvQuality(height) },
                    tag = "mv_quality_${height}"
                )
            }

            SettingsRowDivider(hasIcon = false)

            SettingsToggleRow(
                title = "Autoplay lagu serupa",
                subtitle = "Lanjutkan dengan lagu serupa saat antrian habis",
                checked = appSettings.autoplaySimilar,
                onCheckedChange = onSetAutoplaySimilar,
                tag = "setting_autoplay_similar"
            )
            SettingsToggleRow(
                title = "Lirik karaoke",
                subtitle = "Tampilkan baris lirik sinkron di atas artwork",
                checked = appSettings.karaokeOverlay,
                onCheckedChange = onSetKaraokeOverlay,
                tag = "setting_karaoke_overlay"
            )
            SettingsToggleRow(
                title = "Mulai otomatis saat dibuka",
                subtitle = "Lanjutkan sesi terakhir begitu aplikasi dibuka",
                checked = appSettings.autoResumeOnOpen,
                onCheckedChange = onSetAutoResumeOnOpen,
                tag = "setting_auto_resume"
            )
            SettingsToggleRow(
                title = "Crossfade lembut",
                subtitle = if (perfProfile?.crossfadeAllowed == false) {
                    "Nonaktif di perangkat kelas Hemat (keputusan Sistem Pintar)"
                } else {
                    "Pelankan volume di akhir lagu biar transisi mulus"
                },
                checked = appSettings.crossfadeEnabled && perfProfile?.crossfadeAllowed != false,
                onCheckedChange = onSetCrossfade,
                tag = "setting_crossfade"
            )
        }

        // NOXA WAKE — layanan voice terpisah, grupnya sendiri supaya baris
        // izin yang panjang tidak bercampur dengan toggle pemutar.
        SettingsSectionLabel("NOXA Wake")
        SettingsGroup(tag = "wake_group") {
            SettingsToggleRow(
                title = "\u201cHi NOXA\u201d",
                subtitle =
                    "Dengarkan perintah suara (mis. \u201cputarkan Dewa 19\u201d) walau NOXA " +
                    "sudah ditutup — layar menyala, izin mikrofon & overlay",
                checked = wakeRunning,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        startNoxaWake()
                    } else {
                        context.stopService(
                            Intent(context, com.example.voice.NoxaWakeService::class.java)
                        )
                    }
                },
                tag = "setting_noxa_wake"
            )
        }

        // SISTEM PINTAR — hasil pemindaian perangkat; info dalam grup inset.
        SettingsSectionLabel("Sistem Pintar")
        SettingsGroup(tag = "perf_profile_card") {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = perfProfile?.tierLabel ?: "Seimbang",
                        color = NoxaLime,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Mode pemutaran sesuai kemampuan HP kamu",
                        color = NoxaTextTertiary,
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                PerfInfoRow("RAM", "${perfProfile?.totalMemGb ?: 0.0} GB")
                PerfInfoRow("Inti prosesor", "${perfProfile?.cores ?: 1}")
                PerfInfoRow(
                    "Chipset",
                    perfProfile?.chipName?.takeIf { it.isNotBlank() } ?: "Tidak diketahui"
                )
                PerfInfoRow(
                    "Jendela pra-muat",
                    "${((perfProfile?.preloadWindowMs ?: 45_000L) / 1000)} detik sebelum lagu habis"
                )
            }
        }

        // Senyap — daftar "Jangan putar lagi" yang bisa dibatalkan per item.
        if (mutedArtists.isNotEmpty() || mutedSongs.isNotEmpty()) {
            SettingsSectionLabel("Jangan putar lagi")
            SettingsGroup(tag = "muted_entries_card") {
                mutedArtists.sorted().forEachIndexed { index, artist ->
                    if (index > 0) SettingsRowDivider()
                    MutedEntryRow(
                        label = artist,
                        kindLabel = "Artis",
                        onUndo = {
                            onUnmute(
                                com.example.data.local.MutedEntryEntity.KIND_ARTIST,
                                artist
                            )
                        }
                    )
                }
                mutedSongs.sorted().forEachIndexed { index, songId ->
                    if (index > 0) SettingsRowDivider()
                    MutedEntryRow(
                        label = songId,
                        kindLabel = "Lagu",
                        onUndo = {
                            onUnmute(
                                com.example.data.local.MutedEntryEntity.KIND_SONG,
                                songId
                            )
                        }
                    )
                }
            }
        }

        // PEMBARUAN + CACHE + BAHASA — satu grup "Aplikasi" (gaya iOS
        // Settings): versi & status update, clear cache, pemilih bahasa.
        SettingsSectionLabel("Aplikasi")
        SettingsGroup(tag = "app_group") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "NOXA ${com.example.BuildConfig.VERSION_NAME}",
                        color = NoxaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (availableUpdate != null) {
                            "Versi ${availableUpdate.versionName} sudah tersedia" +
                                if (availableUpdate.mandatory) " (wajib)" else " (opsional)"
                        } else {
                            "Kamu sudah memakai versi terbaru."
                        },
                        color = if (availableUpdate != null) NoxaLime else NoxaTextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.testTag("update_status_text")
                    )
                }
                if (availableUpdate != null) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(NoxaLime)
                            .clickable { onOpenUpdate() }
                            .padding(horizontal = 18.dp, vertical = 9.dp)
                            .testTag("settings_update_btn")
                    ) {
                        Text(
                            text = "Update",
                            color = NoxaBlack,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Default.Delete,
                iconTint = NoxaTextSecondary,
                title = "Clear Cache",
                value = if (cacheCleared) "0 MB" else "42.8 MB",
                showChevron = false,
                onClick = {
                    cacheCleared = true
                    Toast.makeText(context, "Storage cache cleared successfully", Toast.LENGTH_SHORT).show()
                },
                tag = "clear_cache_row"
            )

            SettingsRowDivider()
            SettingsRow(
                icon = Icons.Default.Language,
                iconTint = NoxaHaze,
                title = "Bahasa",
                value = language.endonym,
                onClick = { showLanguagePicker = true },
                tag = "settings_language_card"
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            selected = language,
            onSelect = {
                onSetLanguage(it)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false }
        )
    }

    if (showUsernameDialog && currentUser != null) {
        ChangeUsernameDialog(
            currentUsername = currentUser.displayName.orEmpty(),
            onChange = onChangeUsername,
            onDismiss = { showUsernameDialog = false }
        )
    }
    if (showPasswordDialog) {
        ChangePasswordDialog(
            onChange = onChangePassword,
            onDismiss = { showPasswordDialog = false }
        )
    }
    if (showSignOutDialog) {
        ConfirmSignOutDialog(
            onConfirm = {
                showSignOutDialog = false
                onSignOut()
            },
            onDismiss = { showSignOutDialog = false }
        )
    }
    if (showDeleteAccountDialog && currentUser?.email != null) {
        val accountEmail = currentUser.email!!
        DeleteAccountDialog(
            accountEmail = accountEmail,
            onDelete = { email, onError ->
                onDeleteAccount(email) { error ->
                    if (error == null) showDeleteAccountDialog = false else onError(error)
                }
            },
            onDismiss = { showDeleteAccountDialog = false }
        )
    }
}

/** Dialog for changing the signed-in account's username. */
@Composable
private fun ChangeUsernameDialog(
    currentUsername: String,
    onChange: (String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(currentUsername) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NoxaSurfaceElevated,
        title = {
            Text("Ganti Username", color = NoxaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it; error = null },
                    label = { Text("Username baru") },
                    singleLine = true,
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = NoxaErrorText, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Text(
                text = if (busy) "Menyimpan..." else "Simpan",
                color = if (busy) NoxaTextTertiary else NoxaLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) {
                        busy = true
                        onChange(value.trim()) { result ->
                            busy = false
                            if (result == null) onDismiss() else error = result
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Batal",
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

/** Dialog for changing the password (old + new + strength meter). */
@Composable
private fun ChangePasswordDialog(
    onChange: (String, String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var current by remember { mutableStateOf("") }
    var fresh by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NoxaSurfaceElevated,
        title = {
            Text("Ganti Password", color = NoxaTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it; error = null },
                    label = { Text("Password lama") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = fresh,
                    onValueChange = { fresh = it; error = null },
                    label = { Text("Password baru") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (fresh.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(7.dp))
                    com.example.ui.components.PasswordStrengthBar(password = fresh)
                }
                error?.let {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(it, color = NoxaErrorText, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Text(
                text = if (busy) "Menyimpan..." else "Simpan",
                color = if (busy) NoxaTextTertiary else NoxaLime,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !busy) {
                        busy = true
                        onChange(current, fresh) { result ->
                            busy = false
                            if (result == null) onDismiss() else error = result
                        }
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "Batal",
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

private val NoxaErrorText = androidx.compose.ui.graphics.Color(0xFFFF6B61)

/** One "RAM / cores / chipset" line inside the Sistem Pintar card. */
@Composable
private fun PerfInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = NoxaTextSecondary, fontSize = 13.sp)
        Text(
            text = value,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp)
        )
    }
}

/** One muted song/artist with a "Batalkan" undo action. */
@Composable
private fun MutedEntryRow(label: String, kindLabel: String, onUndo: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = NoxaTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = kindLabel,
                color = NoxaTextTertiary,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Batalkan",
            color = NoxaLime,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onUndo() }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("unmute_${kindLabel}_$label")
        )
    }
}

/** Handle shape for usernames registered through Firestore. */
private val USERNAME_HANDLE = Regex("^[A-Za-z0-9_.]{3,20}$")

/**
 * Ikon per misi NOXA Quest — PlayArrow untuk jumlah play, MusicNote untuk
 * pengulangan lagu, api untuk streak. Dipisah dari [QuestEngine] supaya data
 * murni tetap bebas dependensi Compose.
 */
private fun questIcon(id: String): ImageVector = when (id) {
    "plays" -> Icons.Default.PlayArrow
    "repeat" -> Icons.Default.MusicNote
    else -> Icons.Default.LocalFireDepartment
}

/**
 * Warna mood Jurnal Dengar — hue per mood (haze untuk santai, lime untuk
 * fokus, ember untuk semangat, purple untuk nostalgia) supaya strip 7 hari
 * terbaca tanpa label dan halaman tidak monokrom.
 */
private fun journalColor(moodId: String): Color = when (moodId) {
    "santai" -> NoxaHaze
    "fokus" -> NoxaLime
    "semangat" -> NoxaEmber
    else -> NoxaPurple
}

/** One stat line inside the weekly recap card. */
@Composable
private fun RecapRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = NoxaLime, modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// ------------------------------------------------- Peta Panas Dengar & Wrapped

/** Lime intensity for one heatmap square, 0f (silent) → 1f (best day). */
private fun heatColor(level: Float): Color = when {
    level <= 0f -> NoxaSurfaceSecondary
    level < 0.25f -> NoxaLime.copy(alpha = 0.25f)
    level < 0.5f -> NoxaLime.copy(alpha = 0.5f)
    level < 0.85f -> NoxaLime.copy(alpha = 0.78f)
    else -> NoxaLime
}

/**
 * **Peta Panas Dengar** — 365 lime squares, one per day, GitHub-style.
 * Rows are weekdays (Sunday on top), columns are weeks; a brighter square
 * means more plays that day. Purely decorative data from Room — nothing
 * leaves the phone.
 */

/**
 * KARTU PROFIL NOXA — a preview of the shareable profile card (avatar, name,
 * role and the year's stats in one image). Tapping the button renders it via
 * [com.example.ui.util.ShareCardRenderer] and opens the system share sheet.
 */
@Composable
private fun NoxaProfileCard(
    currentUser: com.example.data.auth.NoxaUser?,
    yearListening: YearListeningUi
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    val stats = yearListening.stats
    val cardData = remember(currentUser, yearListening) {
        com.example.ui.util.ShareCardRenderer.ProfileCardData(
            displayName = currentUser?.displayName ?: "Pendengar NOXA",
            subtitle = currentUser?.email ?: "Mode tamu",
            photoUrl = currentUser?.photoUrl,
            isDeveloper = currentUser?.role == com.example.data.support.AccountRole.DEVELOPER,
            totalPlays = stats?.totalPlays ?: 0,
            distinctArtists = yearListening.distinctArtists,
            topArtist = yearListening.topArtists.firstOrNull()?.name,
            topSong = yearListening.topSongTitle
                .takeIf { it.isNotBlank() }
                ?.let { title ->
                    if (yearListening.topSongPlays > 0) "$title (${yearListening.topSongPlays}×)" else title
                },
            longestStreakDays = stats?.longestStreak ?: 0L
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("profile_card_section"),
        shape = RoundedCornerShape(NoxaRadius.md),
        colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(NoxaLime.copy(alpha = 0.15f))
                        .border(1.5.dp, NoxaLime, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = cardData.displayName.take(1).uppercase(),
                        color = NoxaLime,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                Column {
                    Text(
                        text = cardData.displayName,
                        color = NoxaTextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (cardData.totalPlays > 0) {
                            "${cardData.totalPlays} lagu tahun ini"
                        } else {
                            "belum ada stat"
                        },
                        color = NoxaTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "Kartu siap dibagikan ke story atau chat — avatar, nama, dan statistik " +
                    "dengarmu dalam satu gambar. Tersimpan juga di galeri (Pictures/NOXA).",
                color = NoxaTextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (busy) NoxaSurfaceSecondary else NoxaLime)
                    .clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                com.example.ui.util.ShareCardRenderer.shareProfileCard(context, cardData)
                            }
                            busy = false
                            if (!ok) {
                                Toast.makeText(
                                    context,
                                    "Gagal membuat kartu profil. Coba lagi.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (busy) "Membuat gambar..." else "Bagikan Kartu Profil",
                    color = if (busy) NoxaTextSecondary else NoxaBlack,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ListeningHeatmapCard(yearListening: YearListeningUi) {
    val stats = yearListening.stats

    SectionHeader(title = "Peta Panas Dengar", subtitle = "Satu tahun dengar, hari demi hari")
    Spacer(modifier = Modifier.height(10.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("listening_heatmap_card"),
        shape = RoundedCornerShape(NoxaRadius.md),
        colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated)
    ) {
        if (stats == null || stats.totalPlays == 0) {
            Text(
                text = "Belum ada data setahun ini. Putar musik dulu — petanya terisi sendiri, tiap hari.",
                color = NoxaTextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(16.dp)
            )
            return@Card
        }
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "365 hari terakhir · ${stats.totalPlays}× lagu diputar",
                color = NoxaTextSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            HeatmapGrid(stats = stats)
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sedikit", color = NoxaTextTertiary, fontSize = 10.sp)
                Spacer(modifier = Modifier.width(5.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    listOf(0f, 0.3f, 0.6f, 1f).forEach { level ->
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(heatColor(level))
                        )
                    }
                }
                Spacer(modifier = Modifier.width(5.dp))
                Text("Banyak", color = NoxaTextTertiary, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HeatStat("Hari aktif", "${stats.activeDays}", Modifier.weight(1f))
                HeatStat("Streak sekarang", "${stats.currentStreak} hari", Modifier.weight(1f))
                HeatStat("Streak terbaik", "${stats.longestStreak} hari", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeatStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = NoxaLime,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black
        )
        Text(text = label, color = NoxaTextTertiary, fontSize = 10.sp)
    }
}

/** The 7×53 grid itself — drawn on a Canvas so a year costs zero layouts. */
@Composable
private fun HeatmapGrid(stats: HeatmapStats) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(112.dp)
    ) {
        val columns = 53
        val gap = 2.dp.toPx()
        val cellSize = ((size.width - gap * (columns - 1)) / columns)
            .coerceAtMost(14.dp.toPx())
        val gridWidth = cellSize * columns + gap * (columns - 1)
        val offsetX = ((size.width - gridWidth) / 2f).coerceAtLeast(0f)
        val firstWeekday = stats.cells.firstOrNull()?.weekday ?: 0
        stats.cells.forEachIndexed { index, cell ->
            val col = (index + firstWeekday) / 7
            val row = cell.weekday
            val level = if (stats.bestDayPlays <= 0) {
                0f
            } else {
                cell.plays.toFloat() / stats.bestDayPlays
            }
            drawRect(
                color = heatColor(level),
                topLeft = Offset(
                    x = offsetX + col * (cellSize + gap),
                    y = row * (cellSize + gap)
                ),
                size = Size(cellSize, cellSize)
            )
        }
    }
}

/**
 * **NOXA Wrapped Mini** — the year in five honest numbers: total plays,
 * three top artists, the most-played song, distinct artists, and the hour
 * you listen most. Same on-device data as the heatmap.
 */
@Composable
private fun WrappedMiniCard(
    yearListening: YearListeningUi,
    onOpenWrapped: () -> Unit
) {
    val stats = yearListening.stats ?: return

    SectionHeader(title = "NOXA Wrapped Mini", subtitle = "Lima angka jujur dari setahun dengar")
    Spacer(modifier = Modifier.height(10.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("wrapped_mini_card"),
        shape = RoundedCornerShape(NoxaRadius.md),
        colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(NoxaPurpleDeep.copy(alpha = 0.55f), NoxaBlack)
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                text = "SETAHUN TERAKHIR",
                color = NoxaLime,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${stats.totalPlays}× lagu kamu diputar",
                color = NoxaTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "dari ${yearListening.distinctArtists} artis berbeda",
                color = NoxaTextSecondary,
                fontSize = 12.sp
            )

            if (yearListening.topArtists.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "ARTIS TERATAS",
                    color = NoxaTextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                yearListening.topArtists.take(3).forEachIndexed { index, taste ->
                    WrappedRow(rank = "#${index + 1}", title = taste.name, meta = "${taste.plays}×")
                }
            }

            if (yearListening.topSongTitle.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "LAGU PALING SERING",
                    color = NoxaTextTertiary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                WrappedIconRow(
                    icon = Icons.Default.MusicNote,
                    title = yearListening.topSongTitle,
                    meta = "${yearListening.topSongPlays}×"
                )
            }

            if (yearListening.busiestHour in 0..23) {
                val hour = yearListening.busiestHour
                Spacer(modifier = Modifier.height(8.dp))
                WrappedIconRow(
                    icon = Icons.Default.Schedule,
                    title = "Jam paling rajin dengar",
                    meta = String.format("%02d:00–%02d:00", hour, (hour + 1) % 24)
                )
            }

            // Gateway to the full tahunan Wrapped (story-style, all stats).
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(NoxaLime.copy(alpha = 0.14f))
                    .noxaTap { onOpenWrapped() }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Buka NOXA Wrapped tahunan",
                    color = NoxaLime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun WrappedRow(rank: String, title: String, meta: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank,
            color = NoxaLime,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.width(30.dp)
        )
        Text(
            text = title,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = meta,
            color = NoxaTextSecondary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun WrappedIconRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    meta: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NoxaLime,
            modifier = Modifier
                .width(30.dp)
                .size(15.dp)
        )
        Text(
            text = title,
            color = NoxaTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = meta,
            color = NoxaTextSecondary,
            fontSize = 12.sp
        )
    }
}

/** Themed field colors for the email / username / password inputs. */
@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NoxaLime,
    unfocusedBorderColor = NoxaBorder,
    focusedLabelColor = NoxaLime,
    unfocusedLabelColor = NoxaTextSecondary,
    focusedTextColor = NoxaTextPrimary,
    unfocusedTextColor = NoxaTextPrimary,
    cursorColor = NoxaLime
)

/** Yes/No confirmation — signing out now asks before ending the session. */
@Composable
private fun ConfirmSignOutDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoxaSurfaceElevated,
        title = {
            Text("Yakin ingin keluar?", color = NoxaTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                "Sesi akun kamu akan diakhiri di perangkat ini. Masuk kembali kapan saja.",
                color = NoxaTextSecondary,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(NoxaLime)
                    .clickable { onConfirm() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("sign_out_confirm_btn")
            ) {
                Text("Yes, Sign Out", color = NoxaBlack, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .testTag("sign_out_cancel_btn")
            ) {
                Text("No", color = NoxaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    )
}

/**
 * Permanent account deletion: the email must be re-typed exactly, so this
 * irreversible step can never happen through a stray tap.
 */
@Composable
private fun DeleteAccountDialog(
    accountEmail: String,
    onDelete: (String, (String) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var typed by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoxaSurfaceElevated,
        title = {
            Text("Hapus Akun Permanen", color = NoxaLime, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    text = "Semua data akun kamu — termasuk chat support — akan dihapus " +
                        "permanen dan tidak bisa dikembalikan. Ketik email akun untuk " +
                        "mengonfirmasi:",
                    color = NoxaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    accountEmail,
                    color = NoxaTextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it; error = null },
                    label = { Text("Konfirmasi email") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = authFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("delete_account_email_field")
                )
                error?.let { message ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        message,
                        color = NoxaLime,
                        fontSize = 11.sp,
                        modifier = Modifier.testTag("delete_account_error")
                    )
                }
            }
        },
        confirmButton = {
            val ready = typed.isNotBlank() && !busy
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (ready) NoxaLime else NoxaBorder)
                    .clickable(enabled = ready) {
                        busy = true
                        onDelete(typed.trim()) { message ->
                            busy = false
                            error = message
                        }
                    }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("delete_account_confirm_btn")
            ) {
                Text(
                    "Hapus Permanen",
                    color = if (ready) NoxaBlack else NoxaTextTertiary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, NoxaBorder, RoundedCornerShape(20.dp))
                    .clickable { onDismiss() }
                    .padding(horizontal = 18.dp, vertical = 10.dp)
                    .testTag("delete_account_cancel_btn")
            ) {
                Text("Batal", color = NoxaTextSecondary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    )
}

/**
 * Floating QRIS popup: scan to support NOXA's construction & development.
 * The image is Sky's own QRIS code (hosted on Cloudinary) — no backend or
 * payment key involved, purely a picture in a dialog.
 */
@Composable
private fun QrisSupportDialog(onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoxaSurfaceElevated,
        title = {
            Text("Support Developer NOXA", color = NoxaTextPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Scan QRIS ini untuk mendukung pembangunan dan pengembangan NOXA. Terima kasih!",
                    color = NoxaTextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                coil.compose.AsyncImage(
                    model = QRIS_IMAGE_URL,
                    contentDescription = "QRIS Support Developer NOXA",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Tutup", color = NoxaLime, fontWeight = FontWeight.Bold)
            }
        }
    )
}

/** Sky's QRIS image, hosted publicly on Cloudinary. */
private const val QRIS_IMAGE_URL =
    "https://res.cloudinary.com/dnxjhd4ii/image/upload/v1791893298/qr_ID1025429530401_12.10.26_1791782626_1791782626188_voxeei.jpg"

/**
 * Vector icons that Material does not ship — drawn here so cards never rely
 * on emoji glyphs (which render inconsistently on OEM launchers).
 */
/**
 * Pengaturan → Bahasa. Indonesia is the default and changes nothing (the
 * app's copy ships in Indonesian on purpose); the other languages are served
 * by the in-app dictionary and fall back to Indonesian for untranslated keys.
 */
@Composable
private fun LanguagePickerDialog(
    selected: com.example.data.local.AppLanguage,
    onSelect: (com.example.data.local.AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoxaSurfaceElevated,
        title = {
            Text(
                "Bahasa",
                color = NoxaTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                com.example.data.local.AppLanguage.entries.forEach { lang ->
                    val isSelected = lang == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (isSelected) NoxaLime.copy(alpha = 0.14f) else Color.Transparent
                            )
                            .clickable { onSelect(lang) }
                            .padding(vertical = 11.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                lang.endonym,
                                color = NoxaTextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                lang.labelId,
                                color = NoxaTextTertiary,
                                fontSize = 11.sp
                            )
                        }
                        if (isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Dipilih",
                                tint = NoxaLime,
                                modifier = Modifier.size(18.dp)
                            )
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

private object TelegramIcons {
    /** The Telegram paper plane. */
    val Plane: ImageVector by lazy {
        ImageVector.Builder(
            name = "TelegramPlane",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(fill = SolidColor(Color(0xFF229ED9))) {
                moveTo(9.78f, 18.65f)
                lineTo(10.06f, 14.42f)
                lineTo(17.74f, 7.5f)
                curveTo(18.08f, 7.19f, 17.67f, 7.04f, 17.22f, 7.31f)
                lineTo(7.74f, 13.3f)
                lineTo(3.64f, 12.0f)
                curveTo(2.76f, 11.75f, 2.75f, 11.14f, 3.84f, 10.7f)
                lineTo(19.81f, 4.54f)
                curveTo(20.54f, 4.21f, 21.24f, 4.72f, 20.96f, 5.84f)
                lineTo(18.24f, 18.65f)
                curveTo(18.05f, 19.56f, 17.5f, 19.78f, 16.74f, 19.36f)
                lineTo(12.6f, 16.3f)
                lineTo(10.61f, 18.23f)
                curveTo(10.38f, 18.46f, 10.19f, 18.65f, 9.78f, 18.65f)
                close()
            }
        }.build()
    }
}
