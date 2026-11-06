package com.example

import com.example.data.local.PlaybackSession
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Guards the v3.5.5 startup crash: a persisted [Song] whose title came back null
 * reached Compose's text measurement (SpannableString) and killed the app on
 * launch. Two things must stay true:
 *
 *  1. [Song] serialises through a generated adapter, so R8 renaming its fields
 *     can never strip the payload again.
 *  2. Anything that renders a song uses a value that is never null/blank.
 */
class SongSanitiseTest {

    private fun song(
        id: String = "youtube:dQw4w9WgXcQ",
        title: String = "Kangen",
        artist: String = "Dewa 19",
        artworkUrl: String = "https://example.test/a.jpg"
    ) = Song(id = id, title = title, artist = artist, artworkUrl = artworkUrl)

    @Test
    fun `displayTitle never renders blank`() {
        assertEquals("Kangen", song().displayTitle)
        assertEquals(Song.UNKNOWN_TITLE, song(title = "   ").displayTitle)
    }

    @Test
    fun `displayArtist never renders blank`() {
        assertEquals("Dewa 19", song().displayArtist)
        assertEquals(Song.UNKNOWN_ARTIST, song(artist = "").displayArtist)
    }

    @Test
    fun `sanitised fills blanks and derives provider fields`() {
        val repaired = song(title = "", artist = "").sanitised()

        assertEquals(Song.UNKNOWN_TITLE, repaired.title)
        assertEquals(Song.UNKNOWN_ARTIST, repaired.artist)
        assertEquals("Single", repaired.album)
        assertEquals(MusicSource.YOUTUBE, repaired.source)
        assertEquals("dQw4w9WgXcQ", repaired.remoteId)
        assertEquals("", repaired.streamUrl)
    }

    @Test
    fun `generated adapter keeps every field through a round trip`() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(PlaybackSession::class.java)
        val original = PlaybackSession(songs = listOf(song()), index = 0, positionMs = 1234L)

        val restored = adapter.fromJson(adapter.toJson(original))

        assertEquals(original, restored)
        assertEquals("Kangen", restored?.songs?.first()?.title)
    }

    @Test
    fun `session json with a missing title is rejected instead of becoming null`() {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val adapter = moshi.adapter(Song::class.java)
        val broken = """{"id":"youtube:x","artist":"Dewa 19","artworkUrl":""}"""

        // A required field is missing: the generated adapter throws (caught by
        // PlaybackSessionStore.load), so a null title can never be constructed.
        val song = runCatching { adapter.fromJson(broken) }.getOrNull()
        assertNull(song)
    }
}
