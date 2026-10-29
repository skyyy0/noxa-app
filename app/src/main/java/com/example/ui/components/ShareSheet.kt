package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Song
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaRadius
import com.example.ui.theme.NoxaSurface
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.util.NoxaLinks
import com.example.ui.util.ShareCardRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * NOXA's own share sheet: two options instead of the generic system dialog —
 *
 *  1. **Gambar** — renders the Spotify-Canvas-style card (artwork, title,
 *     artist, NOXA branding), saves it to `Pictures/NOXA` and hands it to the
 *     share sheet.
 *  2. **Salin link NOXA** — copies the deep link that opens straight in the
 *     app on the recipient's device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheetDialog(
    song: Song,
    /**
     * Kirim Lagu (3.3.3): kirim lagu ini ke username teman. `onDone(null)`
     * = sukses, selain itu pesan error dari backend. Null saat fitur
     * tidak tersedia (mis. belum masuk akun) — baris kiriman disembunyikan.
     */
    onSendGift: ((toUsername: String, message: String, onDone: (String?) -> Unit) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var giftFormOpen by remember { mutableStateOf(false) }
    var giftTo by remember { mutableStateOf("") }
    var giftMessage by remember { mutableStateOf("") }
    val link = remember(song.id) { NoxaLinks.songLink(song) }

    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = NoxaSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text(
                text = "Bagikan lagu",
                color = NoxaTextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${song.title} — ${song.artist}",
                color = NoxaTextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(16.dp))

            ShareOptionRow(
                icon = Icons.Default.CardGiftcard,
                title = "Bagikan sebagai gambar",
                subtitle = "Kartu canvas: artwork, judul & NOXA. Otomatis tersimpan di galeri (Pictures/NOXA).",
                tag = "share_as_image",
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            ShareCardRenderer.share(context, song)
                        }
                        busy = false
                        if (!ok) {
                            Toast.makeText(
                                context,
                                "Gagal membuat gambar share. Coba lagi.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            onDismiss()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Spacer(modifier = Modifier.height(10.dp))

            ShareOptionRow(
                icon = Icons.Default.QrCode,
                title = "Bagikan QR",
                subtitle = "Kode QR link lagu ini — pindai pakai kamera, langsung terbuka di NOXA.",
                tag = "share_as_qr",
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            ShareCardRenderer.shareQr(context, song)
                        }
                        busy = false
                        if (!ok) {
                            Toast.makeText(
                                context,
                                "Gagal membuat QR. Coba lagi.",
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            onDismiss()
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            ShareOptionRow(
                icon = Icons.Default.Link,
                title = "Salin link NOXA",
                subtitle = link,
                tag = "copy_noxa_link",
                enabled = !busy,
                onClick = {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    clipboard.setPrimaryClip(
                        android.content.ClipData.newPlainText("NOXA link", link)
                    )
                    Toast.makeText(context, "Link NOXA disalin", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            )

            if (onSendGift != null) {
                Spacer(modifier = Modifier.height(10.dp))

                ShareOptionRow(
                    icon = Icons.Default.Redeem,
                    title = "Kirim ke teman",
                    subtitle = if (giftFormOpen) {
                        "Tutup form kiriman"
                    } else {
                        "Kirim lagu ini ke akun NOXA teman — muncul di profilnya."
                    },
                    tag = "send_gift",
                    enabled = !busy,
                    onClick = { giftFormOpen = !giftFormOpen }
                )

                if (giftFormOpen) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(NoxaSurfaceElevated)
                            .border(1.dp, NoxaBorder, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        OutlinedTextField(
                            value = giftTo,
                            onValueChange = { giftTo = it.trimStart().take(20) },
                            label = { Text("Username teman", fontSize = 12.sp) },
                            singleLine = true,
                            enabled = !busy,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = NoxaTextPrimary,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NoxaLime,
                                unfocusedBorderColor = NoxaBorder,
                                focusedLabelColor = NoxaLime,
                                unfocusedLabelColor = NoxaTextTertiary,
                                cursorColor = NoxaLime
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = giftMessage,
                            onValueChange = { giftMessage = it.take(120) },
                            label = { Text("Pesan (opsional)", fontSize = 12.sp) },
                            enabled = !busy,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = NoxaTextPrimary,
                                fontSize = 14.sp
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NoxaLime,
                                unfocusedBorderColor = NoxaBorder,
                                focusedLabelColor = NoxaLime,
                                unfocusedLabelColor = NoxaTextTertiary,
                                cursorColor = NoxaLime
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(NoxaRadius.full))
                                .background(NoxaLime)
                                .clickable(enabled = !busy) {
                                    val to = giftTo.trim()
                                    if (to.isEmpty()) {
                                        Toast.makeText(
                                            context,
                                            "Isi username penerima dulu.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        return@clickable
                                    }
                                    busy = true
                                    onSendGift(to, giftMessage.trim()) { error ->
                                        busy = false
                                        if (error == null) {
                                            Toast.makeText(
                                                context,
                                                "Lagu dikirim ke @$to 🎁",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            onDismiss()
                                        } else {
                                            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                                .padding(vertical = 11.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (busy) "Mengirim…" else "Kirim lagu",
                                color = NoxaBlack,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (busy) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = NoxaLime,
                        strokeWidth = 2.5.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ShareOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) NoxaSurfaceElevated else NoxaSurface)
            .border(1.dp, NoxaBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(14.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(NoxaLime.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = NoxaLime,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                color = NoxaTextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = NoxaTextSecondary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
