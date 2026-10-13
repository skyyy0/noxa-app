package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AppSettings
import com.example.data.local.AppSettingsStore
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaPurple
import com.example.ui.theme.NoxaPurpleDeep
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.WeeklyRecap

@Composable
fun ProfileScreen(
    currentUser: com.example.data.auth.NoxaUser?,
    isSigningIn: Boolean,
    weeklyRecap: WeeklyRecap,
    appSettings: AppSettings,
    onSignInWithGoogle: (android.app.Activity) -> Unit,
    onSignInWithEmail: (String, String, (String) -> Unit) -> Unit,
    onRegisterWithEmail: (String, String, String, (String) -> Unit) -> Unit,
    onSetMvQuality: (Int) -> Unit,
    onSetAutoplaySimilar: (Boolean) -> Unit,
    onSetKaraokeOverlay: (Boolean) -> Unit,
    onSetAutoResumeOnOpen: (Boolean) -> Unit,
    onSignOut: () -> Unit,
    onNavigateToAboutSky: () -> Unit,
    onOpenAiChat: () -> Unit,
    onOpenSupportChat: () -> Unit,
    onOpenDevPanel: () -> Unit,
    /** null = sukses, String = pesan error. */
    onChangeUsername: (String, (String?) -> Unit) -> Unit,
    /** null = sukses, String = pesan error. */
    onChangePassword: (String, String, (String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var selectedQuality by remember { mutableStateOf("Hi-Res Lossless (24-bit/96kHz)") }
    var cacheCleared by remember { mutableStateOf(false) }

    // Email / password auth state (the Google-free path).
    var registerMode by remember { mutableStateOf(false) }
    var authEmail by remember { mutableStateOf("") }
    var authPassword by remember { mutableStateOf("") }
    var authUsername by remember { mutableStateOf("") }
    var authError by remember { mutableStateOf<String?>(null) }

    // NOXA Bot eye-cover state: while the password field is focused and has
    // content, the mascot covers its eyes ("I'm not looking!").
    var passwordFocused by remember { mutableStateOf(false) }

    // Account settings dialogs.
    var showUsernameDialog by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(bottom = 120.dp)
            .testTag("profile_screen")
    ) {
        // Profile Header
        Text(
            text = "Profile & Settings",
            color = NoxaTextPrimary,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 16.dp)
        )

        // User Info Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentUser?.photoUrl != null) {
                        coil.compose.AsyncImage(
                            model = currentUser.photoUrl,
                            contentDescription = "Avatar",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, NoxaLime, CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(NoxaLime),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (currentUser?.displayName != null) currentUser.displayName!!.take(1).uppercase() else "G",
                                color = NoxaBlack,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentUser?.displayName ?: "Guest User",
                            color = NoxaTextPrimary,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = currentUser?.email ?: "Mode Tamu (Login Disarankan)",
                            color = if (currentUser != null) NoxaLime else NoxaTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        // Unique Firestore username (registered accounts only):
                        // only shown when it is handle-shaped, so Google names
                        // with spaces do not look odd here.
                        val handle = currentUser?.displayName
                        if (handle != null && USERNAME_HANDLE.matches(handle)) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "@$handle",
                                color = NoxaTextTertiary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("profile_username")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (currentUser != null) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = onSignOut,
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sign Out", color = NoxaTextSecondary, fontSize = 13.sp)
                    }
                } else {
                    androidx.compose.material3.Button(
                        onClick = {
                            (context as? android.app.Activity)?.let { onSignInWithGoogle(it) }
                        },
                        enabled = !isSigningIn,
                        shape = RoundedCornerShape(20.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = NoxaLime,
                            contentColor = NoxaBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSigningIn) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = NoxaBlack,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("Sign in with Google", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // ---- Email / username path: accounts without Google. ----
                    Text(
                        text = if (registerMode) "Daftar akun NOXA" else "Masuk dengan email",
                        color = NoxaTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (registerMode) {
                        OutlinedTextField(
                            value = authUsername,
                            onValueChange = { authUsername = it; authError = null },
                            label = { Text("Username unik") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_username_field"),
                            colors = authFieldColors()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    OutlinedTextField(
                        value = authEmail,
                        onValueChange = { authEmail = it; authError = null },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_email_field"),
                        colors = authFieldColors()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (registerMode) {
                        // Password with the NOXA Bot mascot + strength meter.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.example.ui.components.PasswordGuardRobot(
                                covering = passwordFocused && authPassword.isNotEmpty()
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = authPassword,
                                onValueChange = { authPassword = it; authError = null },
                                label = { Text("Password") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier
                                    .weight(1f)
                                    .onFocusChanged { passwordFocused = it.isFocused }
                                    .testTag("auth_password_field"),
                                colors = authFieldColors()
                            )
                        }
                        if (authPassword.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            com.example.ui.components.PasswordStrengthBar(
                                password = authPassword,
                                modifier = Modifier.padding(start = 64.dp)
                            )
                        }
                    } else {
                        OutlinedTextField(
                            value = authPassword,
                            onValueChange = { authPassword = it; authError = null },
                            label = { Text("Password") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("auth_password_field"),
                            colors = authFieldColors()
                        )
                    }

                    authError?.let { message ->
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = message,
                            color = NoxaTextPrimary.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("auth_error_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    androidx.compose.material3.Button(
                        onClick = {
                            val onError: (String) -> Unit = { authError = it }
                            if (registerMode) {
                                onRegisterWithEmail(
                                    authEmail.trim(),
                                    authPassword,
                                    authUsername.trim(),
                                    onError
                                )
                            } else {
                                onSignInWithEmail(authEmail.trim(), authPassword, onError)
                            }
                        },
                        enabled = !isSigningIn && authEmail.isNotBlank() && authPassword.isNotBlank() &&
                            (!registerMode || authUsername.isNotBlank()),
                        shape = RoundedCornerShape(20.dp),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            containerColor = NoxaLime,
                            contentColor = NoxaBlack
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("auth_submit_btn")
                    ) {
                        if (isSigningIn) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = NoxaBlack,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (registerMode) "Daftar" else "Masuk",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (registerMode) {
                            "Sudah punya akun? Masuk"
                        } else {
                            "Belum punya akun? Daftar"
                        },
                        color = NoxaLime,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                registerMode = !registerMode
                                authError = null
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                            .testTag("auth_mode_toggle")
                    )
                }
            }
        }

        // PENGATURAN AKUN — ganti username & password dari aplikasi.
        if (currentUser != null && currentUser.email != null) {
            Text(
                text = "Pengaturan Akun",
                color = NoxaTextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
                border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AccountSettingRow(
                        icon = Icons.Default.Person,
                        title = "Ganti Username",
                        subtitle = "Username tampil di profil & chat support",
                        onClick = { showUsernameDialog = true },
                        tag = "settings_change_username"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(NoxaBorder)
                    )
                    AccountSettingRow(
                        icon = Icons.Default.Security,
                        title = "Ganti Password",
                        subtitle = "Verifikasi password lama dulu",
                        onClick = { showPasswordDialog = true },
                        tag = "settings_change_password"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // MINI WEEKLY RECAP
        Text(
            text = "Rekap Minggu Ini",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("weekly_recap_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
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

        Spacer(modifier = Modifier.height(24.dp))

        // DEVELOPER SECTION — ABOUT SKY PROMINENT CARD
        Text(
            text = "Developer & Vision",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { onNavigateToAboutSky() }
                .testTag("about_sky_nav_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceSecondary),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NoxaLime)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NoxaLime),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "About Sky",
                            tint = NoxaBlack,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "About Sky",
                                color = NoxaTextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NoxaLime.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "CREATOR",
                                    color = NoxaLime,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }
                        Text(
                            text = "Meet the architect behind NOXA and Sky Vibes",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Navigate to About Sky",
                    tint = NoxaLime,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // NOXA AI — asisten musik yang bisa putar lagu & bikin playlist.
        Text(
            text = "Asisten",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { onOpenAiChat() }
                .testTag("ai_chat_nav_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NoxaLime.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                com.example.ui.screens.AiAvatar(size = 46.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Chat dengan NOXA AI",
                        color = NoxaTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Minta putar lagu, bikin playlist, atau curhat — asisten musik kamu",
                        color = NoxaTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NoxaLime,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // CHAT DENGAN NOXA SUPPORT — lapor bug & keluhan langsung dari app.
        Text(
            text = "Bantuan",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clickable { onOpenSupportChat() }
                .testTag("support_chat_nav_card"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, NoxaPurple.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NoxaPurple),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.SupportAgent,
                        contentDescription = "NOXA Support",
                        tint = NoxaTextPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    com.example.ui.components.SupportIdentityRow(name = "Chat dengan NOXA Support")
                    Text(
                        text = "Lapor bug atau keluhan — dibalas langsung di aplikasi",
                        color = NoxaTextSecondary,
                        fontSize = 12.sp
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NoxaPurple,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // NOXA ADMIN — hanya untuk akun developer.
        if (currentUser?.role == com.example.data.support.AccountRole.DEVELOPER) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable { onOpenDevPanel() }
                    .testTag("dev_panel_nav_card"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NoxaSurfaceSecondary),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, NoxaPurple)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NoxaPurpleDeep),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.VerifiedBadgePurple(size = 26.dp)
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        com.example.ui.components.SupportIdentityRow(name = "NOXA Admin")
                        Text(
                            text = "Kelola akun, buat akun admin/partner, balas laporan user",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = NoxaPurple,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Direct Links Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                // Sky Vibes Official Website
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://skyvibes.my.id"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = "Website", tint = NoxaLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Official Website (skyvibes.my.id)", color = NoxaTextPrimary, fontSize = 14.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NoxaTextTertiary, modifier = Modifier.size(16.dp))
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))

                // GitHub
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/skyyy0"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Code, contentDescription = "GitHub", tint = NoxaLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("GitHub Profile (skyyy0)", color = NoxaTextPrimary, fontSize = 14.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NoxaTextTertiary, modifier = Modifier.size(16.dp))
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NoxaBorder))

                // Report Bug WhatsApp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/6289510146933?text=Halo%20Sky,%20saya%20ingin%20lapor%20bug%20di%20NOXA%20Android"))
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Phone, contentDescription = "WhatsApp", tint = NoxaLime, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Report Bug / Contact Sky (+6289510146933)", color = NoxaTextPrimary, fontSize = 14.sp)
                    }
                    Icon(Icons.Default.OpenInNew, contentDescription = "Open", tint = NoxaTextTertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Audio & Playback Settings
        Text(
            text = "Audio Quality & Streaming",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                listOf(
                    "Hi-Res Lossless (24-bit/96kHz)",
                    "High Quality (320 kbps)",
                    "Standard (192 kbps)"
                ).forEach { quality ->
                    val isSelected = selectedQuality == quality
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedQuality = quality }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = quality,
                            color = if (isSelected) NoxaLime else NoxaTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NoxaLime)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // PLAYER FEATURES — MV quality + the extra toggles.
        Text(
            text = "Fitur Pemutar",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                Text(
                    text = "Kualitas video MV",
                    color = NoxaTextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                AppSettingsStore.MV_QUALITY_CHOICES.forEach { height ->
                    val isSelected = appSettings.mvQualityHeight == height
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSetMvQuality(height) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${height}p",
                            color = if (isSelected) NoxaLime else NoxaTextPrimary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(NoxaLime)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(NoxaBorder)
                )

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
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Storage & Cache
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = NoxaSurfaceElevated),
            border = androidx.compose.foundation.BorderStroke(1.dp, NoxaBorder)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        cacheCleared = true
                        Toast.makeText(context, "Storage cache cleared successfully", Toast.LENGTH_SHORT).show()
                    }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Clear Cache",
                        tint = NoxaTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Clear Cache", color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = if (cacheCleared) "0 MB cached" else "42.8 MB cached",
                            color = NoxaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = "Clear",
                    color = NoxaLime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
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
}

/** One navigation row in the "Pengaturan Akun" card. */
@Composable
private fun AccountSettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = NoxaLime, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(title, color = NoxaTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = NoxaTextSecondary, fontSize = 11.sp)
            }
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = NoxaTextTertiary,
            modifier = Modifier.size(16.dp)
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

/** Handle shape for usernames registered through Firestore. */
private val USERNAME_HANDLE = Regex("^[A-Za-z0-9_.]{3,20}$")

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

/** A settings row with a switch — used by the player feature toggles. */
@Composable
private fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = NoxaTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                color = NoxaTextSecondary,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = NoxaLime,
                checkedThumbColor = NoxaBlack,
                uncheckedTrackColor = NoxaSurfaceSecondary,
                uncheckedThumbColor = NoxaTextSecondary,
                uncheckedBorderColor = NoxaBorder
            )
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
