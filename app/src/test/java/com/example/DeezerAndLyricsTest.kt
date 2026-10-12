package com.example

import com.example.data.model.MusicSource
import com.example.data.remote.DeezerRepository
import com.example.data.remote.DeezerTrackListResponse
import com.example.data.remote.LrcParser
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the two pieces of new logic that do not need a network call: mapping a
 * Deezer response onto the app model, and turning LRCLIB's LRC text into timed
 * lines that the lyrics panel can highlight against the real playback position.
 */
class DeezerAndLyricsTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val deezerJson = """
        {
          "data": [
            {
              "id": 1333046382,
              "readable": true,
              "title": "Kangen",
              "duration": 300,
              "link": "https://www.deezer.com/track/1333046382",
              "preview": "https://cdnt-preview.dzcdn.net/api/1/1/4/0/0/0/abc.mp3?hdnea=exp=1791776011",
              "rank": 181890,
              "artist": { "id": 12799, "name": "Dewa 19", "picture_xl": "https://cdn-images.dzcdn.net/dewa19/1000x1000.jpg" },
              "album": {
                "id": 1303082,
                "title": "Kerajaan Cinta",
                "cover_xl": "https://cdn-images.dzcdn.net/cover/1000x1000.jpg"
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses the deezer track list response`() {
        val response = moshi.adapter(DeezerTrackListResponse::class.java).fromJson(deezerJson)

        assertNotNull(response)
        assertFalse(response!!.isFailure)
        val track = response.data!!.first()
        assertEquals("Kangen", track.title)
        assertEquals("Dewa 19", track.artist?.name)
        assertEquals(300, track.duration)
    }

    @Test
    fun `maps a deezer track with a real playable preview url`() {
        val track = moshi.adapter(DeezerTrackListResponse::class.java).fromJson(deezerJson)!!
            .data!!.first()

        val repository = DeezerRepository()
        val song = with(repository) { track.toSong() }

        assertNotNull(song)
        assertEquals("deezer:1333046382", song!!.id)
        assertEquals(MusicSource.DEEZER, song.source)
        assertEquals("1333046382", song.remoteId)
        assertEquals("Kangen", song.title)
        assertEquals("Dewa 19", song.artist)
        assertEquals("Kerajaan Cinta", song.album)
        assertEquals("https://cdn-images.dzcdn.net/cover/1000x1000.jpg", song.artworkUrl)
        assertEquals(300_000L, song.durationMs)
        assertEquals("https://www.deezer.com/track/1333046382", song.shareUrl)
        // Deezer is legally limited to a 30 second clip, so the UI must say so.
        assertTrue(song.isPreview)
        assertTrue(song.streamUrl.startsWith("https://"))
    }

    @Test
    fun `a deezer track without a preview is dropped instead of being listed as unplayable`() {
        val json = """{"data":[{"id":42,"title":"No preview","artist":{"name":"Someone"}}]}"""
        val track = moshi.adapter(DeezerTrackListResponse::class.java).fromJson(json)!!
            .data!!.first()

        val repository = DeezerRepository()
        assertNull(with(repository) { track.toSong() })
    }

    @Test
    fun `deezer reports errors inside an http 200 body`() {
        val json = """{"error":{"type":"Exception","message":"Quota exceeded","code":4}}"""
        val response = moshi.adapter(DeezerTrackListResponse::class.java).fromJson(json)
        assertTrue(response!!.isFailure)
        assertEquals("Quota exceeded", response.error?.message)
    }

    private val lrc = """
        [ar:Dewa 19]
        [ti:Kangen]
        [00:11.67] Kuterima suratmu, telah kubaca dan aku mengerti
        [00:19.63]
        [00:22.22] Betapa merindunya dirimu akan hadirnya diriku
        [01:02.5] Baris dengan satu digit pecahan
        [01:05.123] Baris dengan tiga digit pecahan
    """.trimIndent()

    @Test
    fun `parses lrc timestamps and skips metadata and blank lines`() {
        val lines = LrcParser.parse(lrc)

        assertEquals(4, lines.size)
        assertEquals(11_670L, lines[0].timeMs)
        assertEquals("Kuterima suratmu, telah kubaca dan aku mengerti", lines[0].text)
        assertEquals(22_220L, lines[1].timeMs)
        // `[01:02.5]` is 1 minute 2 seconds and 500 ms, not 5 ms.
        assertEquals(62_500L, lines[2].timeMs)
        assertEquals(65_123L, lines[3].timeMs)
        // Output is sorted, which indexAt() relies on.
        assertEquals(lines.sortedBy { it.timeMs }, lines)
    }

    @Test
    fun `indexAt finds the line that is being sung`() {
        val lyrics = com.example.data.model.TrackLyrics(lines = LrcParser.parse(lrc))

        assertEquals(-1, lyrics.indexAt(0))
        assertEquals(0, lyrics.indexAt(11_670))
        assertEquals(0, lyrics.indexAt(20_000))
        assertEquals(1, lyrics.indexAt(22_220))
        assertEquals(3, lyrics.indexAt(600_000))
    }

    @Test
    fun `a line with several timestamps is emitted for each of them`() {
        val lines = LrcParser.parse("[00:10.00][00:20.00] Reff yang sama")
        assertEquals(2, lines.size)
        assertEquals(10_000L, lines[0].timeMs)
        assertEquals(20_000L, lines[1].timeMs)
        assertEquals("Reff yang sama", lines[1].text)
    }
}
