package com.example

import com.example.data.model.Song
import com.example.data.support.RoomAi
import com.example.data.support.RoomRadio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomAiTest {

    @Test
    fun `a mention is recognised anywhere in the line`() {
        assertTrue(RoomAi.mentionsNoxa("@noxa puterin lagu Kangen"))
        assertTrue(RoomAi.mentionsNoxa("eh @noxa cariin lagu Dewa 19"))
        assertFalse(RoomAi.mentionsNoxa("lagu ini enak ya"))
        // An email that merely contains the word is not a mention.
        assertFalse(RoomAi.mentionsNoxa("kirim ke sky@noxa.id"))
    }

    @Test
    fun `plain chat is not a command`() {
        assertNull(RoomAi.command("halo semua, lagu ini enak"))
    }

    @Test
    fun `play now strips the command words from the song title`() {
        val command = RoomAi.command("@noxa cariin lagu Dewa 19 Kangen")
        assertEquals(RoomAi.Mode.NOW, command?.mode)
        assertEquals("dewa 19 kangen", command?.query)
    }

    @Test
    fun `after this song becomes a queue request`() {
        val command = RoomAi.command("@noxa puterin lagu Kangen habis lagu ini")
        assertEquals(RoomAi.Mode.NEXT, command?.mode)
        assertEquals("kangen", command?.query)
    }

    @Test
    fun `queue position words are not part of the title`() {
        val command = RoomAi.command("@noxa antrian kedua: Sheila On 7 - Dan")
        assertEquals(RoomAi.Mode.NEXT, command?.mode)
        assertEquals("sheila on 7 dan", command?.query)
    }

    @Test
    fun `calling NOXA without a title yields a blank query instead of a guess`() {
        val command = RoomAi.command("@noxa")
        assertEquals(RoomAi.Mode.NOW, command?.mode)
        assertEquals("", command?.query)
    }

    @Test
    fun `small talk is chat, not a song request`() {
        val command = RoomAi.command("@noxa halo semuanya")
        assertEquals(RoomAi.Intent.CHAT, command?.intent)
        assertEquals("", command?.query)
        assertEquals(RoomAi.Intent.CHAT, RoomAi.command("@noxa")?.intent)
    }

    @Test
    fun `a music question is answered instead of played`() {
        val command = RoomAi.command("@noxa lagu apa yang cocok buat kerja?")
        assertEquals(RoomAi.Intent.CHAT, command?.intent)
        assertEquals(RoomAi.Intent.CHAT, RoomAi.command("@noxa siapa kamu?")?.intent)
    }

    @Test
    fun `an opinion about the current song is not a request`() {
        assertEquals(RoomAi.Intent.CHAT, RoomAi.command("@noxa lagu ini enak ya")?.intent)
    }

    @Test
    fun `a bare title still plays`() {
        val command = RoomAi.command("@noxa lagu Kangen")
        assertEquals(RoomAi.Intent.PLAY, command?.intent)
        assertEquals("kangen", command?.query)
    }

    @Test
    fun `asking to change the song without a title advances`() {
        val command = RoomAi.command("@noxa ganti lagu")
        assertEquals(RoomAi.Intent.PLAY, command?.intent)
        assertEquals(true, command?.advance)
        assertEquals("", command?.query)
    }

    @Test
    fun `play lines always name the track`() {
        val now = RoomAi.playLine("Kangen", "Dewa 19", RoomAi.Mode.NOW)
        assertTrue(now.contains("Kangen"))
        assertTrue(now.contains("Dewa 19"))
        val next = RoomAi.playLine("Dan", "Sheila On 7", RoomAi.Mode.NEXT)
        assertTrue(next.contains("Dan"))
    }

    @Test
    fun `casual replies stay personal instead of guessing a song`() {
        assertTrue(RoomAi.casualReply("halo noxa").contains("NOXA"))
        assertTrue(RoomAi.casualReply("lagu apa ini", "Kangen — Dewa 19").contains("Kangen"))
    }

    @Test
    fun `the suggestion appears while the handle is half typed`() {
        assertTrue(RoomAi.suggestsHandle("@"))
        assertTrue(RoomAi.suggestsHandle("@n"))
        assertTrue(RoomAi.suggestsHandle("halo @no"))
        assertTrue(RoomAi.suggestsHandle("@noxa"))
        // Completed (trailing space) or unrelated text: no chip.
        assertFalse(RoomAi.suggestsHandle("@noxa "))
        assertFalse(RoomAi.suggestsHandle("lagu ini enak"))
        assertFalse(RoomAi.suggestsHandle("kirim ke @sky"))
    }

    @Test
    fun `completing the handle keeps what was already typed`() {
        assertEquals("@noxa ", RoomAi.completeHandle("@n"))
        assertEquals("halo @noxa ", RoomAi.completeHandle("halo @"))
        assertEquals("@noxa ", RoomAi.completeHandle(""))
    }
}

class RoomRadioTest {

    private fun song(id: String, artist: String, duration: Long = 180_000L) = Song(
        id = id,
        title = "Track $id",
        artist = artist,
        artworkUrl = "",
        durationMs = duration
    )

    @Test
    fun `an unknown duration never counts as ended`() {
        assertFalse(RoomRadio.hasEnded(playing = true, positionMs = 999_999L, durationMs = 0L))
        assertFalse(RoomRadio.hasEnded(playing = false, positionMs = 200_000L, durationMs = 180_000L))
        assertTrue(RoomRadio.hasEnded(playing = true, positionMs = 180_000L, durationMs = 180_000L))
    }

    @Test
    fun `recently played ids stay unique and bounded`() {
        val recent = (1..20).fold(emptyList<String>()) { acc, i ->
            RoomRadio.remember(acc, "s$i")
        }
        assertEquals(RoomRadio.RECENT_LIMIT, recent.size)
        assertEquals("s20", recent.last())
        assertEquals(recent.size, recent.distinct().size)
    }

    @Test
    fun `the radio prefers the same artist and avoids what was heard`() {
        val candidates = listOf(
            song("a", "Dewa 19"),
            song("b", "Dewa 19"),
            song("c", "Sheila On 7")
        )
        // "a" is playing; the other Dewa 19 track wins over the different artist.
        assertEquals("b", RoomRadio.pickSimilar("Dewa 19", candidates, setOf("a"))?.id)
        // Everything used up: no repeat, the room just stops.
        assertNull(RoomRadio.pickSimilar("Dewa 19", candidates, setOf("a", "b", "c")))
    }
}
