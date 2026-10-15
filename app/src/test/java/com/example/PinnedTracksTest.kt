package com.example

import com.example.data.auth.UsernameRegistry
import com.example.data.model.PinnedTracks
import com.example.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sky's pinned pick and the quote attached to it. */
class PinnedTracksTest {

    private fun song(title: String, artist: String) = Song(
        id = "deezer:1",
        title = title,
        artist = artist,
        artworkUrl = ""
    )

    @Test
    fun kangenByDewa19IsPinnedWithSkyQuote() {
        val pin = PinnedTracks.find(song("Kangen", "Dewa 19"))
        assertNotNull(pin)
        assertEquals(PinnedTracks.SKY_QUOTE, pin!!.quote)
    }

    @Test
    fun matchingIsCaseInsensitiveAndTrimsWhitespace() {
        assertNotNull(PinnedTracks.find(song(" kAnGeN ", "DEWA 19")))
        assertTrue(PinnedTracks.isPinned(song("Kangen", "Dewa 19")))
    }

    @Test
    fun otherTracksAreNotPinned() {
        assertNull(PinnedTracks.find(song("Sepia", "Sheila On 7")))
        assertFalse(PinnedTracks.isPinned(song("Bila Kau Tak Ada", "Sheila On 7")))
        assertNull(PinnedTracks.find(song("Kangen", "NOAH")))
    }

    @Test
    fun everyPinnedPickIsListedWithItsQuote() {
        assertEquals(5, PinnedTracks.ALL.size)
        assertNotNull(PinnedTracks.find(song("Pupus", "Dewa 19")))
        assertNotNull(PinnedTracks.find(song("Dan", "Sheila On 7")))
        assertNotNull(PinnedTracks.find(song("Terlalu Manis", "Slank")))
        assertNotNull(PinnedTracks.find(song("Laskar Pelangi", "Nidji")))
        // Each pin carries a non-blank quote of its own.
        PinnedTracks.ALL.forEach { pin ->
            assertTrue(pin.quote.isNotBlank())
        }
    }

    @Test
    fun resultMatchingToleratesSuffixesButNotWrongArtistsOrTypos() {
        assertTrue(PinnedTracks.matchesResult("Kangen - 2011 Remaster", "Dewa 19"))
        assertTrue(PinnedTracks.matchesResult("Kangen (feat. Ariel)", "Dewa 19"))
        assertTrue(PinnedTracks.matchesResult("Kangen", "Dewa 19"))
        assertFalse(PinnedTracks.matchesResult("Kangen (Live)", "Sheila On 7"))
        assertFalse(PinnedTracks.matchesResult("Kangenen", "Dewa 19"))
    }

    @Test
    fun perPinMatchingKeepsSisterPinsApart() {
        // Regression: the home feed used the any-pin matcher while resolving
        // each pin, so both Dewa 19 pins grabbed the same top track, the shelf
        // rendered one song twice and Home crashed with a duplicate LazyRow
        // key ("Key \"deezer:…\" was already used").
        val kangen = PinnedTracks.ALL.first { it.title == "Kangen" }
        val pupus = PinnedTracks.ALL.first { it.title == "Pupus" }
        assertTrue(PinnedTracks.matches(kangen, "Kangen (2019 Remaster)", "Dewa 19"))
        assertFalse(PinnedTracks.matches(kangen, "Pupus", "Dewa 19"))
        assertTrue(PinnedTracks.matches(pupus, "Pupus", "Dewa 19"))
        assertFalse(PinnedTracks.matches(pupus, "Kangen", "Dewa 19"))
    }

    @Test
    fun findUsesTheSameTolerantMatchingSoEveryShownPinGetsItsQuote() {
        // A catalogue title that travelled with a suffix still resolves to its
        // pin — this is exactly the case that used to show a card without quote.
        val pin = PinnedTracks.find(song("Terlalu Manis - Live", "Slank"))
        assertNotNull(pin)
        assertTrue(pin!!.quote.contains("kenangan"))
        assertNotNull(PinnedTracks.find(song("Dan (Akustik)", "sheila on 7")))
        assertNotNull(PinnedTracks.find(song("Laskar Pelangi - 2019", "Nidji")))
        // …but wrong artists and typos stay unpinned.
        assertNull(PinnedTracks.find(song("Kangen (Live)", "NOAH")))
        assertNull(PinnedTracks.find(song("Kangenen", "Dewa 19")))
    }

    @Test
    fun deezerEllipsisTitlesStillMatchThePin() {
        // Deezer serves Sheila On 7's pick as "Dan…" — the ellipsis used to
        // make every matcher fail, so the shelf only showed 4 of 5 pins.
        assertNotNull(PinnedTracks.find(song("Dan...", "Sheila On 7")))
        assertNotNull(PinnedTracks.find(song("Dan…", "Sheila On 7")))
        val dan = PinnedTracks.ALL.first { it.title == "Dan" }
        assertTrue(PinnedTracks.matches(dan, "Dan...", "Sheila On 7"))
        assertFalse(PinnedTracks.matches(dan, "Dan...", "NOAH"))
    }

    @Test
    fun findAlsoRecognisesPinsServedFromYoutube() {
        // "Laskar Pelangi" is delisted in the catalogue and resolved from
        // YouTube for the shelf: artist sits inside the title, the uploader is
        // the label channel. The quote/star lookup must still find the pin —
        // this was exactly the card that used to render quote-less.
        val pin = PinnedTracks.find(
            song("NIDJI - Laskar Pelangi (Official Music Video)", "Musica Studios")
        )
        assertNotNull(pin)
        assertTrue(pin!!.quote.contains("diperjuangkan"))
        // …while a cover by an unrelated artist stays unpinned even relaxed.
        assertNull(PinnedTracks.find(song("Laskar Pelangi", "Farel Prayoga")))
    }

    @Test
    fun relaxedMatchingAcceptsRealYoutubeHitsButRejectsNoise() {
        val laskar = PinnedTracks.ALL.first { it.title == "Laskar Pelangi" }
        val dan = PinnedTracks.ALL.first { it.title == "Dan" }

        // The actual top hit: artist inside the title, uploader = label channel.
        assertTrue(
            PinnedTracks.matchesRelaxed(
                laskar, "NIDJI - Laskar Pelangi (Official Music Video)", "Musica Studios"
            )
        )
        assertTrue(PinnedTracks.matchesRelaxed(laskar, "Laskar Pelangi", "Nidji"))
        // Wrong artist (no Nidji anywhere) stays out.
        assertFalse(PinnedTracks.matchesRelaxed(laskar, "Laskar Pelangi", "Farel Prayoga"))

        // Short pins are case-sensitive so Indonesian "dan" never matches.
        assertTrue(
            PinnedTracks.matchesRelaxed(dan, "Sheila On 7 - Dan... (Official Audio)", "Random Upload")
        )
        assertFalse(
            PinnedTracks.matchesRelaxed(dan, "Kita dan Cinta - Sheila On 7", "Random Upload")
        )
    }
}

/** Username rules used by email/password registration. */
class UsernameValidationTest {

    @Test
    fun validUsernamesPass() {
        assertNull(UsernameRegistry.validate("sky_007"))
        assertNull(UsernameRegistry.validate("sky.vibes"))
        assertNull(UsernameRegistry.validate("SKY"))
        assertNull(UsernameRegistry.validate("a".repeat(20)))
    }

    @Test
    fun invalidUsernamesAreRejectedWithAMessage() {
        assertNotNull(UsernameRegistry.validate(""))
        assertNotNull(UsernameRegistry.validate("ab"))
        assertNotNull(UsernameRegistry.validate("a".repeat(21)))
        assertNotNull(UsernameRegistry.validate("has space"))
        assertNotNull(UsernameRegistry.validate("bad!char"))
    }
}
