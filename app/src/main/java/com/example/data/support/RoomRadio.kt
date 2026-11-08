package com.example.data.support

import com.example.data.model.Song

/**
 * **Auto-radio for Dengar Bareng** — pure Kotlin so the "keep the music going"
 * rule can be tested without a device.
 *
 * When the current track ends and nobody queued anything, the room's admin
 * device picks one more song in the same lane (same artist first, then an
 * untouched catalogue result), publishes it, and everybody follows. Only the
 * admin device decides, so the room never fights over two different picks.
 */
object RoomRadio {

    /** How many recently played ids are remembered per room session. */
    const val RECENT_LIMIT = 12

    /**
     * True when the room's current track has run out. [durationMs] is 0 until
     * a member has published a real duration, and an unknown duration must
     * never trigger a skip.
     */
    fun hasEnded(playing: Boolean, positionMs: Long, durationMs: Long): Boolean {
        if (!playing || durationMs <= 0) return false
        return positionMs >= durationMs
    }

    /** Appends [songId] to the recent list, keeping it bounded and unique. */
    fun remember(recent: List<String>, songId: String): List<String> {
        if (songId.isBlank()) return recent
        return (recent - songId + songId).takeLast(RECENT_LIMIT)
    }

    /**
     * Picks the next radio song: the artist's own catalogue first, minus
     * anything already heard ([excludeIds], which should include the current
     * song). Returns null when nothing suitable is left — the room then simply
     * stops instead of repeating itself.
     */
    fun pickSimilar(
        anchorArtist: String,
        candidates: List<Song>,
        excludeIds: Collection<String>
    ): Song? {
        val excluded = excludeIds.toSet()
        val fresh = candidates.filter { it.id.isNotBlank() && it.id !in excluded }
        if (fresh.isEmpty()) return null
        return fresh
            .sortedWith(
                compareByDescending<Song> {
                    anchorArtist.isNotBlank() && it.artist.equals(anchorArtist, ignoreCase = true)
                }.thenByDescending { it.durationMs > 0 }
            )
            .firstOrNull()
    }
}
