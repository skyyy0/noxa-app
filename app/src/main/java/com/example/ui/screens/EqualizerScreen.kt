package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.media.EqualizerState
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaRadius
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import kotlin.math.roundToInt

/**
 * **Studio Suara** (v3.7.0) — the face of [com.example.media.EqualizerManager].
 *
 * The screen no longer pretends an equalizer is six sliders on a slab. It
 * explains the chain it is running (preamp → bands → compressor → limiter),
 * names the output it tuned for, and shows the headroom it reserved. The
 * controls are the three decisions a listener actually wants:
 *
 *  1. **Profil** — what character (transparent, warm, vocal, bass, detail).
 *  2. **Intensitas** — how far to push that character, one slider.
 *  3. **Loudness / Limiter** — even out quiet tracks, and stay safe at volume.
 */
@Composable
fun EqualizerScreen(
    equalizerState: EqualizerState,
    onSetEnabled: (Boolean) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSetBandLevel: (Int, Int) -> Unit,
    onSetIntensity: (Float) -> Unit,
    onSetLoudness: (Boolean) -> Unit,
    onSetLimiter: (Boolean) -> Unit,
    onRefreshRoute: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val active = equalizerState.isEnabled

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NoxaBlack)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp)
            .testTag("equalizer_screen")
    ) {
        // ------------------------------------------------------------- header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = NoxaLime,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STUDIO SUARA",
                        color = NoxaTextTertiary,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.5.sp
                    )
                }
                Text(
                    text = "Suara",
                    color = NoxaTextPrimary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-1).sp
                )
            }
            Switch(
                checked = active,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSetEnabled(it)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = NoxaBlack,
                    checkedTrackColor = NoxaLime,
                    uncheckedThumbColor = NoxaTextTertiary,
                    uncheckedTrackColor = NoxaSurfaceSecondary
                ),
                modifier = Modifier.testTag("equalizer_master_switch")
            )
        }

        // ------------------------------------------------------ engine status
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(NoxaRadius.lg))
                .background(
                    Brush.linearGradient(
                        listOf(
                            NoxaSurfaceElevated,
                            NoxaLime.copy(alpha = if (active) 0.07f else 0.02f)
                        )
                    )
                )
                .padding(16.dp)
                .testTag("sound_engine_card")
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = equalizerState.routeLabel,
                        color = NoxaTextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = equalizerState.engineName,
                        color = if (equalizerState.dspActive) NoxaLime else NoxaTextTertiary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onRefreshRoute()
                    },
                    modifier = Modifier.testTag("sound_refresh_route")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Deteksi ulang output",
                        tint = NoxaTextSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = equalizerState.routeNote,
                color = NoxaTextSecondary,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChainPill("Preamp ${equalizerState.headroomDb.roundToInt()} dB")
                ChainPill(if (equalizerState.dspActive) "6 band" else "EQ hardware")
                ChainPill(if (equalizerState.loudnessEnabled) "Kompresor" else "Tanpa kompresor")
                ChainPill(if (equalizerState.limiterEnabled) "Limiter" else "Tanpa limiter")
            }

            if (!active) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Engine di-bypass — audio diputar apa adanya tanpa koreksi.",
                    color = NoxaTextTertiary,
                    fontSize = 11.5.sp
                )
            }
        }

        // ------------------------------------------------------------- profil
        Text(
            text = "Profil",
            color = NoxaTextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(equalizerState.presets) { preset ->
                val selected = equalizerState.currentPreset == preset
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (selected) NoxaLime else NoxaSurfaceElevated)
                        .clickable(enabled = active) { onSelectPreset(preset) }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                        .testTag("preset_$preset")
                ) {
                    Text(
                        text = preset,
                        color = if (selected) NoxaBlack else NoxaTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // ---------------------------------------------------------- intensitas
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Intensitas",
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(equalizerState.intensity * 100).roundToInt()}%",
                    color = NoxaLime,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = equalizerState.intensity,
                onValueChange = onSetIntensity,
                valueRange = 0f..1f,
                enabled = active,
                colors = SliderDefaults.colors(
                    thumbColor = NoxaLime,
                    activeTrackColor = NoxaLime,
                    inactiveTrackColor = NoxaSurfaceSecondary
                ),
                modifier = Modifier.testTag("sound_intensity")
            )
            Text(
                text = "Seberapa jauh profil dibentuk. 0% = transparan, 100% = karakter penuh.",
                color = NoxaTextTertiary,
                fontSize = 11.5.sp,
                lineHeight = 16.sp
            )
        }

        // --------------------------------------------------------------- band
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(NoxaRadius.lg))
                .background(NoxaSurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Enam band",
                    color = NoxaTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (active) "AKTIF" else "BYPASS",
                    color = if (active) NoxaLime else NoxaTextTertiary,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (equalizerState.bands.isEmpty()) {
                Text(
                    text = "Engine menyala otomatis saat lagu pertama diputar — " +
                        "sesi audio belum tersedia.",
                    color = NoxaTextTertiary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            } else {
                equalizerState.bands.forEach { band ->
                    val db = band.currentLevelMilliBels / 100f
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatFrequency(band.centerFreqHz),
                            color = NoxaTextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(48.dp)
                        )
                        Slider(
                            value = db.coerceIn(-12f, 12f),
                            onValueChange = { onSetBandLevel(band.index, (it * 100).roundToInt()) },
                            valueRange = -12f..12f,
                            enabled = active,
                            colors = SliderDefaults.colors(
                                thumbColor = NoxaLime,
                                activeTrackColor = NoxaLime.copy(alpha = 0.7f),
                                inactiveTrackColor = NoxaSurfaceSecondary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("eq_band_${band.index}")
                        )
                        Text(
                            text = "${if (db > 0) "+" else ""}${db.roundToInt()} dB",
                            color = if (db == 0f) NoxaTextTertiary else NoxaTextPrimary,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.width(46.dp)
                        )
                    }
                }
            }
        }

        // ------------------------------------------------------------ toggles
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
                .clip(RoundedCornerShape(NoxaRadius.lg))
                .background(NoxaSurfaceElevated)
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            EngineToggleRow(
                title = "Loudness",
                subtitle = "Kompresor 3 band: lagu pelan terdengar, lagu keras tidak melompat",
                checked = equalizerState.loudnessEnabled,
                onCheckedChange = onSetLoudness,
                tag = "sound_loudness"
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.06f))
            )
            EngineToggleRow(
                title = "Limiter",
                subtitle = "Menahan transien supaya bass boost tidak pernah pecah",
                checked = equalizerState.limiterEnabled,
                onCheckedChange = onSetLimiter,
                tag = "sound_limiter"
            )
        }

        Text(
            text = "Profil Otomatis mengikuti output: speaker HP dipangkas sub-bassnya, " +
                "Bluetooth dikompensasi, headphone kabel dan DAC dibiarkan netral. " +
                "Headroom dihitung ulang setiap kali profil berubah.",
            color = NoxaTextTertiary,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            modifier = Modifier.padding(horizontal = 22.dp)
        )
    }
}

@Composable
private fun ChainPill(text: String) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(NoxaSurfaceSecondary)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = NoxaTextSecondary,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun EngineToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
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
                color = NoxaTextTertiary,
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NoxaBlack,
                checkedTrackColor = NoxaLime,
                uncheckedThumbColor = NoxaTextTertiary,
                uncheckedTrackColor = NoxaSurfaceSecondary
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}

/** 60 Hz, 200 Hz, 2.5 kHz … — short enough to fit a slider row. */
private fun formatFrequency(hz: Int): String = when {
    hz >= 1000 -> "${(hz / 100f).roundToInt() / 10f}".trimEnd('0').trimEnd('.') + " kHz"
    else -> "$hz Hz"
}
