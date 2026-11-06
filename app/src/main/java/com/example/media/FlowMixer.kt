package com.example.media

import com.example.data.model.Song

/**
 * **Flow / AutoMix** (v3.5.5) — queue weaving.
 *
 * A plain shuffle happily puts three slow ballads back to back and jumps from
 * a 2009 rock anthem into a lo-fi loop. Flow keeps the opening track (the one
 * the user actually chose) and then weaves the rest so that:
 *
 *  1. no two consecutive tracks share the same artist, and
 *  2. fast and slow tracks alternate when the queue has enough of both,
 *
 * which — with the existing crossfade — is what makes a queue feel mixed
 * instead of shuffled. Pure and deterministic, so the order is unit-testable
 * and stable for the same input.
 */
object FlowMixer {

    /**
     * Returns [songs] in flow order. A queue shorter than three tracks is
     * returned untouched: there is nothing to weave.
     */
    fun order(songs: List<Song>): List<Song> {
        if (songs.size < 3) return songs
        val anchor = songs.first()
        val rest = songs.drop(1)
        if (rest.isEmpty()) return songs

        val buckets = rest.groupBy { it.artist.trim().lowercase() }
            .mapValues { it.value.toMutableList() }
            .toMutableMap()

        val fast = mutableListOf<Song>()
        val slow = mutableListOf<Song>()
        // Energy proxy: duration is the only signal available offline, and
        // short tracks skew energetic on real playlists. 3:30 splits them.
        rest.sortedBy { it.durationMs }.forEach { song ->
            if (song.durationMs in 1 until ENERGY_SPLIT_MS) fast.add(song) else slow.add(song)
        }
        val useAlternation = fast.isNotEmpty() && slow.isNotEmpty()

        val ordered = mutableListOf(anchor)
        var lastArtist = anchor.artist.trim().lowercase()
        var takeFast = anchor.durationMs in 1 until ENERGY_SPLIT_MS

        while (buckets.values.any { it.isNotEmpty() }) {
            val pool = if (useAlternation) {
                // Alternate the energy band, but never starve one side.
                val preferred = if (takeFast) fast else slow
                val fallback = if (takeFast) slow else fast
                pickBand(preferred, lastArtist, buckets)
                    ?: pickBand(fallback, lastArtist, buckets)
            } else {
                null
            }
            val chosen = pool ?: pickAny(buckets, lastArtist) ?: break
            buckets.values.forEach { it.remove(chosen) }
            ordered.add(chosen)
            lastArtist = chosen.artist.trim().lowercase()
            takeFast = !takeFast
        }
        return ordered
    }

    /** Picks the first song of [band] that is still queued and not a repeat artist. */
    private fun pickBand(
        band: List<Song>,
        lastArtist: String,
        buckets: Map<String, MutableList<Song>>
    ): Song? {
        val remaining = buckets.values.flatten().toMutableSet()
        return band.firstOrNull { song ->
            remaining.remove(song) && song.artist.trim().lowercase() != lastArtist
        }
    }

    /** Any remaining track, preferring a different artist than the last one. */
    private fun pickAny(
        buckets: Map<String, MutableList<Song>>,
        lastArtist: String
    ): Song? {
        val artistKeys = buckets.keys.sortedByDescending { key ->
            buckets[key]?.size ?: 0
        }
        val otherArtist = artistKeys.firstOrNull { it != lastArtist }
        if (otherArtist != null) {
            val list = buckets[otherArtist]
            if (list != null && list.isNotEmpty()) return list.first()
        }
        return artistKeys.firstNotNullOfOrNull { key ->
            val list = buckets[key]
            if (list != null && list.isNotEmpty()) list.first() else null
        }
    }

    /** Tracks shorter than this are treated as the energetic half. */
    const val ENERGY_SPLIT_MS = 210_000L
}
