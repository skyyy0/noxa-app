package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Corner-radius scale for NOXA.
 *
 * The whole app draws from these four steps plus `full`. Anything that needs
 * a corner picks the step that matches its *role*, not its size:
 *
 *  - [xs]  8dp  — tags, inline controls, thumbnail corner inside a dense row.
 *  - [sm]  12dp — chips, small artwork, inset controls.
 *  - [md]  18dp — standard cards, artwork tiles, inset list groups, dialogs.
 *  - [lg]  26dp — sheets, hero panels, anything that owns a whole region.
 *  - [xl]  30dp — full-bleed feature cards and the top edge of a sheet.
 *  - [full]      — pills, avatars, circular play buttons (clamped to a round end).
 *
 * The scale deliberately sits larger than a generic Material app: iOS-style
 * surfaces have a soft, continuous silhouette, and anything under ~14dp reads
 * as a hard little box rather than a sheet of material.
 *
 * Having one scale is what stops the app drifting into "every screen rounds
 * corners differently". New surfaces must pick from here before inventing a
 * radius, and no radius outside this scale should be introduced without a
 * new role.
 */
object NoxaRadius {

    /** Tags, inline controls, the corner of a thumbnail inside a dense row. */
    val xs = 8.dp

    /** Chips, small artwork, inset controls. */
    val sm = 12.dp

    /** Standard cards, artwork tiles and inset list groups. */
    val md = 18.dp

    /** Sheets, hero panels and other region-owning surfaces. */
    val lg = 26.dp

    /** Full-bleed feature cards and the top edge of a large sheet. */
    val xl = 30.dp

    /**
     * Perfect pill / circle. Deliberately larger than half of any element we
     * build, so the corner clamps to a round end regardless of the element's
     * height — same token works for a 32dp chip and a 56dp avatar.
     */
    val full = 999.dp
}

/**
 * Material3 shape roles bound to the same scale, so any Material component
 * dropped into the app (Card, Dialog, Snackbar, TextField…) inherits NOXA's
 * corners without per-call overrides.
 *
 * The M3 scale has five roles while NOXA defines four steps + full; `full`
 * is intentionally not exposed as a shape role — pills are always built
 * explicitly with [NoxaRadius.full] so nothing becomes round by accident.
 */
val NoxaShapes = Shapes(
    extraSmall = RoundedCornerShape(NoxaRadius.xs),
    small = RoundedCornerShape(NoxaRadius.sm),
    medium = RoundedCornerShape(NoxaRadius.md),
    large = RoundedCornerShape(NoxaRadius.lg),
    extraLarge = RoundedCornerShape(NoxaRadius.xl)
)