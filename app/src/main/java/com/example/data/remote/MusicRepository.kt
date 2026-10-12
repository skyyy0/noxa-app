package com.example.data.remote

import com.example.data.model.AlbumDetail
import com.example.data.model.AlbumInfo
import com.example.data.model.ArtistProfile
import com.example.data.model.Song
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** One horizontally scrolling shelf on the home screen. */
data class HomeMusicSection(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

/** Everything the home screen needs, in one immutable snapshot. */
data class HomeFeed(
    val hero: Song? = null,
    val sections: List<HomeMusicSection> = emptyList()
)

private data class ArtistShelf(
    val artistName: String,
    val title: String,
    val subtitle: String
)

/**
 * The single source of music for NOXA.
 *
 * Two keyless providers are combined, and the UI never needs to know which one a
 * track came from:
 *
 *  * **[DeezerRepository]** — the broad catalogue, now including mainstream
 *    Indonesian music. Audio is a label-approved 30 second preview clip, so those
 *    tracks are flagged [Song.isPreview].
 *  * **[AudiusRepository]** — independent artists, with genuinely full-length
 *    audio.
 *
 * Search merges both; the home feed is built from a handful of curated shelves
 * plus the global chart. No API key, no signup, nothing hardcoded.
 */
class MusicRepository(
    private val deezer: DeezerRepository = DeezerRepository(),
    private val audius: AudiusRepository = AudiusRepository()
) {

    /**
     * Search both providers.
     *
     * Deezer results come first because its catalogue matches what people search
     * for most, then Audius is appended so full-length options are still there.
     */
    suspend fun search(query: String): List<Song> = coroutineScope {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@coroutineScope emptyList()

        val deezerDeferred = async { deezer.search(trimmed) }
        val audiusDeferred = async { audius.search(trimmed, limit = 10) }

        val deezerResults = runCatching { deezerDeferred.await() }.getOrDefault(emptyList())
        val audiusResults = runCatching { audiusDeferred.await() }.getOrDefault(emptyList())

        (deezerResults + audiusResults).distinctBy { it.id }
    }

    // ----------------------------------------------------- artist / album pages

    /** Deezer artist id for a name, cached after the first hit. */
    suspend fun findArtistId(artistName: String): Long? = deezer.artistIdFor(artistName)

    /** Artist page header (picture, listeners, release count). */
    suspend fun artistProfile(artistId: Long): ArtistProfile? = deezer.artistProfile(artistId)

    /** Top tracks of an artist by id — the artist page's song list. */
    suspend fun artistTopTracks(artistId: Long, limit: Int = 25): List<Song> =
        deezer.topTracksForArtist(artistId, limit)

    /** Discography of an artist. */
    suspend fun artistAlbums(artistId: Long): List<AlbumInfo> = deezer.artistAlbums(artistId)

    /** Album header plus full tracklist, in track order. */
    suspend fun albumDetail(albumId: Long): AlbumDetail? = deezer.albumDetail(albumId)

    /** Builds the whole home feed from live API data. */
    suspend fun getHomeFeed(): HomeFeed = coroutineScope {
        // Bounded concurrency keeps Deezer's per-IP query quota happy while the
        // shelves still load in parallel instead of one after another.
        val gate = Semaphore(permits = 3)

        val heroDeferred = async { gate.withPermit { loadHero() } }

        val shelfDeferred = ARTIST_SHELVES.map { shelf ->
            async {
                gate.withPermit {
                    HomeMusicSection(
                        title = shelf.title,
                        subtitle = shelf.subtitle,
                        songs = deezer.artistTopTracks(shelf.artistName, limit = SHELF_SIZE)
                    )
                }
            }
        }

        val chartDeferred = async {
            gate.withPermit {
                HomeMusicSection(
                    title = "Global Top 50",
                    subtitle = "Tangga lagu Deezer hari ini",
                    songs = deezer.chart(limit = SHELF_SIZE)
                )
            }
        }

        val audiusDeferred = async {
            gate.withPermit {
                HomeMusicSection(
                    title = "Komunitas Audius",
                    subtitle = "Lagu penuh dari artis independen",
                    songs = audius.trending(limit = SHELF_SIZE)
                )
            }
        }

        val sections = (shelfDeferred + chartDeferred + audiusDeferred)
            .awaitAll()
            .filter { it.songs.isNotEmpty() }
            .distinctBy { it.title }

        HomeFeed(hero = heroDeferred.await(), sections = sections)
    }

    /**
     * The hero banner always shows "Blinding Lights" by The Weeknd. The track is
     * fetched from Deezer like everything else, so the banner is real, playable
     * audio rather than a hardcoded image.
     */
    private suspend fun loadHero(): Song? {
        val candidates = deezer.search(HERO_QUERY, limit = 10)
        return candidates.firstOrNull { song ->
            song.artist.equals(HERO_ARTIST, ignoreCase = true) &&
                song.title.contains(HERO_TITLE, ignoreCase = true)
        } ?: candidates.firstOrNull()
    }

    companion object {
        const val HERO_TITLE = "Blinding Lights"
        const val HERO_ARTIST = "The Weeknd"
        private const val HERO_QUERY = "Blinding Lights The Weeknd"
        private const val SHELF_SIZE = 12

        private val ARTIST_SHELVES = listOf(
            ArtistShelf("Dewa 19", "Dewa 19", "Balada & rock legendaris Indonesia"),
            ArtistShelf("Sheila On 7", "Sheila On 7", "Pop Indonesia paling dicari"),
            ArtistShelf("Justin Bieber", "Justin Bieber", "Hits pop global"),
            ArtistShelf("Iwan Fals", "Iwan Fals", "Legenda musik Indonesia"),
            ArtistShelf("Tulus", "Tulus", "Pop & jazz Indonesia"),
            ArtistShelf("NOAH", "NOAH & Peterpan", "Dari Peterpan sampai NOAH")
        )
    }
}
