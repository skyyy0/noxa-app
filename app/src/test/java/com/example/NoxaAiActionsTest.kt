package com.example

import com.example.data.ai.NoxaAiAction
import com.example.data.ai.NoxaAiActions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The AI controls the player through tags embedded in its reply. These tests
 * pin the protocol: tags are extracted in order, never leak into the visible
 * chat text, and emoji are stripped even when the model disobeys the prompt.
 */
class NoxaAiActionsTest {

    @Test
    fun `play tag is extracted and removed from text`() {
        val reply = "Siap, gue putarin Kangen.\n[NOXA_PLAY|Dewa 19|Kangen]"
        val (text, actions) = NoxaAiActions.parse(reply)

        assertFalse(text.contains("NOXA_PLAY"))
        assertEquals(1, actions.size)
        val play = actions[0] as NoxaAiAction.Play
        assertEquals("Dewa 19", play.artist)
        assertEquals("Kangen", play.title)
        assertTrue(text.startsWith("Siap"))
    }

    @Test
    fun `playlist tag carries name and artist-aware tracks`() {
        val reply = "Playlist Dewa 19 jadi ya.\n[NOXA_PLAYLIST|Dewa 19|Dewa 19 - Kangen;Dewa 19 - Separuh Nafas]"
        val (text, actions) = NoxaAiActions.parse(reply)

        assertFalse(text.contains("NOXA_PLAYLIST"))
        val playlist = actions[0] as NoxaAiAction.CreatePlaylist
        assertEquals("Dewa 19", playlist.name)
        assertEquals(2, playlist.tracks.size)
        // v3.5.5: setiap entri membawa artis, supaya app bisa menolak lagu
        // yang artisnya tidak cocok (bug "album Dewa 19 tapi ada lagu nyasar").
        assertTrue(playlist.tracks.all { it.artist == "Dewa 19" })
        assertEquals("Kangen", playlist.tracks[0].title)
        assertEquals("Dewa 19 - Kangen", playlist.tracks[0].wire)
    }

    @Test
    fun `title-only track keeps a blank artist`() {
        val reply = "[NOXA_PLAYLIST|Galau|The One That Got Away]"
        val (_, actions) = NoxaAiActions.parse(reply)
        val playlist = actions[0] as NoxaAiAction.CreatePlaylist
        assertEquals(1, playlist.tracks.size)
        assertEquals("", playlist.tracks[0].artist)
        assertEquals("The One That Got Away", playlist.tracks[0].title)
    }

    @Test
    fun `queue replace and equalizer tags are parsed`() {
        val reply = "Siap.\n" +
            "[NOXA_QUEUE|Dewa 19 - Kangen;Sheila On 7 - Dan]\n" +
            "[NOXA_REPLACE|Galau|A - Lama|Dewa 19 - Kangen]\n" +
            "[NOXA_EQ|Bass Boost]"
        val (text, actions) = NoxaAiActions.parse(reply)

        assertEquals("Siap.", text)
        assertEquals(3, actions.size)
        val queue = actions[0] as NoxaAiAction.Queue
        assertEquals(listOf("Kangen", "Dan"), queue.tracks.map { it.title })
        val replace = actions[1] as NoxaAiAction.ReplaceTrack
        assertEquals("Galau", replace.playlistName)
        assertEquals("Lama", replace.old.title)
        assertEquals("Dewa 19", replace.new.artist)
        assertEquals("Bass Boost", (actions[2] as NoxaAiAction.Equalizer).preset)
    }

    @Test
    fun `reply without tags yields plain text and no actions`() {
        val (text, actions) = NoxaAiActions.parse("Halo! Mau dengar apa hari ini?")
        assertEquals("Halo! Mau dengar apa hari ini?", text)
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `emoji are stripped from replies`() {
        val cleaned = NoxaAiActions.stripEmoji("Hai, Sky! 👋😊 Senang berkenalan 💙")
        assertFalse(cleaned.any { it.code in 0x1F000..0x1FAFF })
        assertTrue(cleaned.contains("Hai, Sky!"))
    }
}
