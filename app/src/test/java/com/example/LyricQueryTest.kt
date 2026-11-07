package com.example

import com.example.data.remote.LyricsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The web lookup that grounds "Tulis Lirik" only works if the request is
 * reduced to a title/artist query — LRCLIB returns nothing for
 * "tolong tulis lirik lagu Kangen Dewa 19".
 */
class LyricQueryTest {

    @Test
    fun `command words are stripped from a real song request`() {
        assertEquals(
            "kangen dewa 19",
            LyricsRepository.webQueryFor("tolong tulis lirik lagu Kangen Dewa 19")
        )
    }

    @Test
    fun `artist and title survive in either order`() {
        assertEquals(
            "bertaut nadin amizah",
            LyricsRepository.webQueryFor("Lirik lagu Bertaut Nadin Amizah dong")
        )
        // "dan" is also a song title (Dewa 19 / Sheila On 7), so it is NOT in
        // the filler list.
        assertEquals(
            "dan sheila on 7",
            LyricsRepository.webQueryFor("tuliskan lirik lagu Dan oleh Sheila On 7")
        )
    }

    @Test
    fun `a creative brief is not looked up on the web`() {
        assertNull(LyricsRepository.webQueryFor("buatkan lirik tentang hujan di malam hari"))
        assertNull(LyricsRepository.webQueryFor("tulis lirik bertema perpisahan"))
    }

    @Test
    fun `a request without a usable pair of words is not looked up`() {
        assertNull(LyricsRepository.webQueryFor("tolong tulis lirik lagu"))
        assertNull(LyricsRepository.webQueryFor("lirik"))
    }
}
