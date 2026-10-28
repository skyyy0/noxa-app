package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NoxaBlack
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaSurfaceSecondary
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary
import com.example.ui.viewmodel.MusicViewModel.TwsTuneState

/**
 * The wireless tuning popup (v3.2.1).
 *
 * Shown the moment NOXA detects a TWS/Bluetooth output: it announces what it
 * is doing while the automatic profile is applied, then reports what actually
 * landed on the hardware. The copy is deliberately honest — when the audio
 * session does not exist yet (nothing has been played), it says the profile
 * becomes active on first playback instead of claiming success.
 */
@Composable
fun TwsTuneDialog(
    state: TwsTuneState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NoxaSurfaceSecondary,
        title = {
            Text(
                text = "Profil Suara Nirkabel",
                color = NoxaTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // The device that triggered it — real name from the platform.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NoxaBlack)
                        .border(1.dp, NoxaBorder, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${state.kindLabel} · ${state.deviceName}",
                        color = NoxaLime,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                TuneStep(
                    label = "Mendeteksi perangkat",
                    state = stepState(state.step, 0)
                )
                TuneStep(
                    label = "Menerapkan profil otomatis",
                    state = stepState(state.step, 1)
                )
                TuneStep(
                    label = "Suara disetel untuk perangkat ini",
                    state = stepState(state.step, 2)
                )

                if (state.summary != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = state.summary,
                        color = if (state.step >= 2) NoxaLime else NoxaTextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Oke",
                    color = NoxaLime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    )
}

private enum class StepState { Pending, Running, Done }

private fun stepState(currentStep: Int, index: Int): StepState = when {
    currentStep > index -> StepState.Done
    currentStep == index -> StepState.Running
    else -> StepState.Pending
}

@Composable
private fun TuneStep(label: String, state: StepState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    when (state) {
                        StepState.Done -> NoxaLime
                        StepState.Running -> NoxaLime.copy(alpha = 0.14f)
                        StepState.Pending -> NoxaSurfaceSecondary
                    }
                )
                .then(
                    if (state == StepState.Pending) {
                        Modifier.border(1.dp, NoxaBorder, CircleShape)
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                StepState.Done -> Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = NoxaBlack,
                    modifier = Modifier.size(14.dp)
                )
                StepState.Running -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = NoxaLime,
                    strokeWidth = 2.dp
                )
                StepState.Pending -> Unit
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Text(
            text = label,
            color = when (state) {
                StepState.Done -> NoxaTextPrimary
                StepState.Running -> NoxaTextPrimary
                StepState.Pending -> NoxaTextTertiary
            },
            fontSize = 13.sp,
            fontWeight = if (state == StepState.Pending) FontWeight.Normal else FontWeight.Medium
        )
    }
}
