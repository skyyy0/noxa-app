package com.example.data.local

import android.content.Context

/**
 * **Studio Cover Playlist** (v3.5.5) — keeps the cover a user picked for a
 * playlist.
 *
 * Stored in SharedPreferences keyed by `playlistId` instead of adding a column
 * to `PlaylistEntity`: no Room migration, no schema version bump, and old
 * installs keep working untouched. A missing entry means "auto" — the cover is
 * derived deterministically from the playlist name, so every playlist already
 * has a cover before the user opens the studio.
 */
/**
 * A playlist cover: palette style, deterministic seed, and (v3.6.0) an optional
 * printed texture plus a typographic voice for the monogram.
 *
 * Texture/font default to 0 = "none", so covers saved by older versions keep
 * looking exactly the same after an update.
 */
data class PlaylistCover(
    val style: Int,
    val seed: Int,
    val texture: Int = 0,
    val font: Int = 0
)

class PlaylistCoverStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The saved cover, or the deterministic default for this name. */
    fun coverFor(playlistId: Long, name: String): PlaylistCover {
        val style = prefs.getInt(key(playlistId, "style"), STYLE_AUTO)
        val seed = prefs.getInt(key(playlistId, "seed"), defaultSeed(name))
        val texture = prefs.getInt(key(playlistId, "texture"), 0)
        val font = prefs.getInt(key(playlistId, "font"), 0)
        return PlaylistCover(
            style = style.coerceIn(0, STYLE_COUNT - 1),
            seed = seed,
            texture = texture.coerceIn(0, TEXTURE_COUNT - 1),
            font = font.coerceIn(0, FONT_COUNT - 1)
        )
    }

    fun save(playlistId: Long, cover: PlaylistCover) {
        prefs.edit()
            .putInt(key(playlistId, "style"), cover.style.coerceIn(0, STYLE_COUNT - 1))
            .putInt(key(playlistId, "seed"), cover.seed)
            .putInt(key(playlistId, "texture"), cover.texture.coerceIn(0, TEXTURE_COUNT - 1))
            .putInt(key(playlistId, "font"), cover.font.coerceIn(0, FONT_COUNT - 1))
            .apply()
    }

    fun clear(playlistId: Long) {
        prefs.edit()
            .remove(key(playlistId, "style"))
            .remove(key(playlistId, "seed"))
            .remove(key(playlistId, "texture"))
            .remove(key(playlistId, "font"))
            .apply()
    }

    private fun key(playlistId: Long, field: String) = "p$playlistId:$field"

    companion object {
        private const val NAME = "noxa_playlist_covers"
        private const val STYLE_AUTO = 0
        const val STYLE_COUNT = 4
        const val TEXTURE_COUNT = 5
        const val FONT_COUNT = 4

        val STYLE_LABELS = listOf("Aurora", "Tape", "Prism", "Pulse")

        /** Texture overlays: index 0 is "clean" (no texture). */
        val TEXTURE_LABELS = listOf("Bersih", "Grain", "Halftone", "Gelombang", "Scanline")

        /** Typographic voices for the monogram. */
        val FONT_LABELS = listOf("Grotesk", "Playfair", "Sans", "Mono")

        /** Stable seed from the name — the same playlist always looks the same. */
        fun defaultSeed(name: String): Int = name.fold(17) { acc, ch -> acc * 31 + ch.code } and 0x7FFFFFFF
    }
}
