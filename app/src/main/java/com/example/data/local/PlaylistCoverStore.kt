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
data class PlaylistCover(
    val style: Int,
    val seed: Int
)

class PlaylistCoverStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** The saved cover, or the deterministic default for this name. */
    fun coverFor(playlistId: Long, name: String): PlaylistCover {
        val style = prefs.getInt(key(playlistId, "style"), STYLE_AUTO)
        val seed = prefs.getInt(key(playlistId, "seed"), defaultSeed(name))
        return PlaylistCover(style = style.coerceIn(0, STYLE_COUNT - 1), seed = seed)
    }

    fun save(playlistId: Long, cover: PlaylistCover) {
        prefs.edit()
            .putInt(key(playlistId, "style"), cover.style.coerceIn(0, STYLE_COUNT - 1))
            .putInt(key(playlistId, "seed"), cover.seed)
            .apply()
    }

    fun clear(playlistId: Long) {
        prefs.edit().remove(key(playlistId, "style")).remove(key(playlistId, "seed")).apply()
    }

    private fun key(playlistId: Long, field: String) = "p$playlistId:$field"

    companion object {
        private const val NAME = "noxa_playlist_covers"
        private const val STYLE_AUTO = 0
        const val STYLE_COUNT = 4

        val STYLE_LABELS = listOf("Aurora", "Tape", "Prism", "Pulse")

        /** Stable seed from the name — the same playlist always looks the same. */
        fun defaultSeed(name: String): Int = name.fold(17) { acc, ch -> acc * 31 + ch.code } and 0x7FFFFFFF
    }
}
