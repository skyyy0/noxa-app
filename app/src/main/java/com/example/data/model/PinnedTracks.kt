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
        pin.title.equals(song.title.trim(), ignoreCase = true) &&
            pin.artist.equals(song.artist.trim(), ignoreCase = true)
    }

    fun isPinned(song: Song): Boolean = find(song) != null

    /**
     * Whether a catalogue result is the pinned recording: titles travel with
     * remaster/bracket suffixes across providers, so the pinned title must
     * still match the *start* of the result's title.
     */
    fun matchesResult(title: String, artist: String): Boolean = ALL.any { pin ->
        val t = title.trim()
        (t.equals(pin.title, ignoreCase = true) ||
            t.startsWith("${pin.title} ", ignoreCase = true) ||
            t.startsWith("${pin.title}(", ignoreCase = true) ||
            t.startsWith("${pin.title} -", ignoreCase = true)) &&
            pin.artist.equals(artist.trim(), ignoreCase = true)
    }
}
