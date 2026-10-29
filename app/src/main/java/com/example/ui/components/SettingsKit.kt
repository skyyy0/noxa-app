package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NoxaBorder
import com.example.ui.theme.NoxaLime
import com.example.ui.theme.NoxaRadius
import com.example.ui.theme.NoxaSpacing
import com.example.ui.theme.NoxaSurfaceElevated
import com.example.ui.theme.NoxaTextPrimary
import com.example.ui.theme.NoxaTextSecondary
import com.example.ui.theme.NoxaTextTertiary

/**
 * The Profile & Settings building blocks — an iOS grouped-inset list spoken
 * in NOXA's own material:
 *
 *  * **No outlines.** A group is a tonal step up from the canvas, never a
 *    card with a 1dp border. Rows inside are separated by a hairline that is
 *    inset to the text, not drawn edge to edge.
 *  * **Lime is a signal, not wallpaper.** Row icons sit in a small tonal
 *    chip tinted per category (haze for account, ember for journal, lime for
 *    player, purple for support), so the page is not a wall of lime-on-black.
 *  * **One header language.** [SettingsSectionLabel] for the small tracked
 *    group labels, [SectionHeader] for content shelves — the same 19sp shelf
 *    header Home uses, so the profile page reads as part of the same app.
 *
 * Everything here is pure presentation: state and callbacks stay with the
 * caller, so the settings screen's behaviour is untouched by the restyle.
 */

/** Small tracked group label above an inset settings group (iOS style). */
@Composable
fun SettingsSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = NoxaTextTertiary,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.3.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 8.dp)
    )
}

/**
 * Content shelf header — identical proportions to Home's section header
 * (19sp bold, −0.4 tracking, tertiary subtitle), so content sections feel
 * continuous between Home and Profile.
 */
@Composable
fun SectionHeader(
    title: String,
    subtitle: String = "",
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    iconTint: Color = NoxaTextTertiary,
    trailing: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Same rhythm as [SettingsSectionLabel] (20dp above, 3+10 below
            // to its content) so a shelf and a group label sit at the same
            // cadence in one scrolling column.
            .padding(top = 20.dp, start = NoxaSpacing.gutter, end = NoxaSpacing.gutter)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title,
                color = NoxaTextPrimary,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp
            )
            Spacer(modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                color = NoxaTextTertiary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

/** One inset group container: tonal step, list-group radius, no outline. */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    tag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NoxaSpacing.gutter)
            .clip(RoundedCornerShape(NoxaRadius.md))
            .background(NoxaSurfaceElevated)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(vertical = 2.dp),
        content = content
    )
}

/**
 * Hairline between rows of a group, inset to start where the row text starts
 * (row padding + icon chip + gap) so the line never runs under the icon.
 */
@Composable
fun SettingsRowDivider(hasIcon: Boolean = true, modifier: Modifier = Modifier) {
    val indent = if (hasIcon) 60.dp else 16.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = indent)
            .height(1.dp)
            .background(NoxaBorder)
    )
}

/**
 * Tonal icon chip — a category-tinted square behind a row icon. This is what
 * keeps a long settings list from reading as monochrome lime: each group
 * carries its own hue at 14% strength instead of every icon glowing brand.
 */
@Composable
fun IconChip(icon: ImageVector, tint: Color = NoxaLime, size: Int = 30, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size((size * 0.55f).dp)
        )
    }
}

/**
 * One row in an inset group: optional tonal icon chip, title, optional
 * subtitle, optional trailing value, chevron by default. Rows without an
 * [onClick] render static (value rows); pass [trailing] to place a switch or
 * button instead of the chevron.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = NoxaLime,
    /** Custom leading content (e.g. AiAvatar) — replaces the [icon] chip. */
    leading: (@Composable () -> Unit)? = null,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = NoxaTextPrimary,
    showChevron: Boolean = true,
    tag: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    val base = modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.noxaTap { onClick() } else it }
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .let { if (tag != null) it.testTag(tag) else it }
    Row(
        modifier = base,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            leading != null -> {
                leading()
                Spacer(modifier = Modifier.width(12.dp))
            }
            icon != null -> {
                IconChip(icon = icon, tint = iconTint)
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = titleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = NoxaTextSecondary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = value,
                color = NoxaTextTertiary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            trailing != null -> {
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
            showChevron && onClick != null -> {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = NoxaTextTertiary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * A selectable row for single-choice groups (audio quality, MV quality):
 * selected = lime label + filled dot, exactly the old behaviour, restyled
 * into the group language.
 */
@Composable
fun SettingsChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noxaTap { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .let { if (tag != null) it.testTag(tag) else it },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = if (selected) NoxaLime else NoxaTextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(NoxaRadius.full))
                    .background(NoxaLime)
            )
        }
    }
}

/**
 * Toggle row inside a group — title + subtitle left, M3 switch right (lime
 * when on). Heavier haptic on the switch itself so it feels like a physical
 * toggle, same as before the restyle.
 */
@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null
) {
    val haptic = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .noxaTap { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .let { if (tag != null) it.testTag(tag) else it },
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
            onCheckedChange = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onCheckedChange(it)
            },
            colors = SwitchDefaults.colors(
                checkedTrackColor = NoxaLime,
                checkedThumbColor = Color(0xFF08090A),
                uncheckedTrackColor = com.example.ui.theme.NoxaSurfaceSecondary,
                uncheckedThumbColor = NoxaTextSecondary,
                uncheckedBorderColor = NoxaBorder
            )
        )
    }
}