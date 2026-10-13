package com.example.data.model

/**
 * Tracks Sky pinned for this release.
 *
 * A pinned track gets a badge everywhere it appears (home shelf, search,
 * library, mini player) and its own shelf at the top of Home. In the full
 * player, the pin also surfaces the quote [SKY_QUOTE] beneath the transport
 * controls — the words that explain *why* the song is pinned.
 */
object PinnedTracks {

    /** Sky's words for this drop, shown in the full player under the controls. */
    const val SKY_QUOTE =
        "ada lagu yang cuma berdurasi beberapa menit, " +
            "tapi bisa membawa kita kembali ke masa yang udah ga mungkin diulang."

    /** A pinned pick: matched against songs by title + artist (case-insensitive). */
    data class PinnedTrack(val title: String, val artist: String, val quote: String)

    val ALL: List<PinnedTrack> = listOf(
        PinnedTrack(title = "Kangen", artist = "Dewa 19", quote = SKY_QUOTE),
        PinnedTrack(
            title = "Pupus",
            artist = "Dewa 19",
            quote = "tidak semua yang kita perjuangkan ditakdirkan untuk kita miliki."
        ),
        PinnedTrack(
            title = "Dan",
            artist = "Sheila On 7",
            quote = "kadang, merelakan seseorang berarti menerima bahwa kebahagiaannya " +
                "mungkin tidak lagi bersama kita."
        ),
        PinnedTrack(
            title = "Terlalu Manis",
            artist = "Slank",
            quote = "beberapa kenangan memang lebih indah dibiarkan menjadi bagian dari masa lalu."
        ),
        PinnedTrack(
            title = "Laskar Pelangi",
            artist = "Nidji",
            quote = "jalan kita mungkin ga selalu mudah, tapi masih ada banyak hal " +
                "yang layak diperjuangkan."
        )
    )

    /** The pinned entry for this song, or null when it is not pinned. */
    fun find(song: Song): PinnedTrack? = ALL.firstOrNull { pin ->
        matches(pin, song.title, song.artist)
    }

    fun isPinned(song: Song): Boolean = find(song) != null

    /**
     * Whether a catalogue result is the pinned recording: titles travel with
     * remaster/bracket/live suffixes across providers, so the pinned title must
     * still match the *start* of the result's title. One rule, used both when
     * matching live catalogue results and when looking up the quote for a song
     * that is already in the UI — so a track that gets the badge always gets
     * its quote too.
     */
    fun matchesResult(title: String, artist: String): Boolean = ALL.any { pin ->
        matches(pin, title, artist)
    }

    /**
     * Whether a catalogue result is THIS specific pinned recording (not just
     * any pin's). The home feed uses it while resolving each pin so two pins
     * of the same artist (Kangen & Pupus are both Dewa 19) can never grab
     * the same track — that duplicated song ids and crashed the shelf with
     * "Key \"deezer:…\" was already used".
     */
    fun matches(pin: PinnedTrack, title: String, artist: String): Boolean {
        val t = title.trim()
        val titleMatches = t.equals(pin.title, ignoreCase = true) ||
            t.startsWith("${pin.title} ", ignoreCase = true) ||
            t.startsWith("${pin.title}(", ignoreCase = true) ||
            t.startsWith("${pin.title} -", ignoreCase = true)
        return titleMatches && pin.artist.equals(artist.trim(), ignoreCase = true)
    }
}
