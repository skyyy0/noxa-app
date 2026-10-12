package com.example.data.remote

import android.util.Log
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The Deezer-backed half of NOXA's catalogue.
 *
 * Deezer is the only large, keyless source that actually carries mainstream
 * Indonesian music (Dewa 19, Sheila On 7, Iwan Fals, Tulus, …) as well as global
 * pop. Its API returns a directly playable HTTPS MP3 for every track, but Deezer
 * legally caps those clips at 30 seconds, so every [Song] built here is marked
 * [Song.isPreview].
 *
 * Plays are therefore real audio, just short. Full-length streaming is only
 * available from providers that expose it, which is why NOXA keeps Audius as a
 * second source.
 */
class DeezerRepository {

    private val api: DeezerApiService by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(DeezerApiService::class.java)
    }

    /** Artist-id lookups are cached: a shelf needs one, not one per scroll. */
    private val artistIdCache = HashMap<String, Long?>()
    private val artistIdMutex = Mutex()

    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            val response = safeCall { api.searchTracks(trimmed, limit) }
            response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
        }

    suspend fun chart(limit: Int = SEARCH_LIMIT): List<Song> = withContext(Dispatchers.IO) {
        val response = safeCall { api.chartTracks(limit) }
        response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
    }

    /**
     * Top tracks of an artist, resolved by name.
     *
     * Deezer's `search?q=artist:"…"` field syntax does not work reliably, so the
     * artist is resolved once through `search/artist` and then reused from cache.
     */
    suspend fun artistTopTracks(artistName: String, limit: Int = SEARCH_LIMIT): List<Song> =
        withContext(Dispatchers.IO) {
            val artistId = resolveArtistId(artistName) ?: return@withContext emptyList()
            val response = safeCall { api.artistTopTracks(artistId, limit) }
            response?.data.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id }
        }

    private suspend fun resolveArtistId(artistName: String): Long? {
        artistIdMutex.withLock { artistIdCache[artistName] }?.let { return it }
        val found = safeArtistCall { api.searchArtists(artistName, 1) }
            ?.data
            ?.firstOrNull()
            ?.id
        artistIdMutex.withLock { artistIdCache[artistName] = found }
        return found
    }

    private suspend fun safeCall(
        block: suspend () -> Response<DeezerTrackListResponse>
    ): DeezerTrackListResponse? = try {
        val response = block()
        val body = response.body()
        when {
            !response.isSuccessful -> {
                Log.w(TAG, "HTTP ${response.code()}")
                null
            }
            // Deezer reports failures inside a 200 body.
            body == null || body.isFailure -> {
                Log.w(TAG, "API error: ${body?.error?.message ?: "empty body"}")
                null
            }
            else -> body
        }
    } catch (e: Exception) {
        Log.w(TAG, "Request failed: ${e.message}")
        null
    }

    private suspend fun safeArtistCall(
        block: suspend () -> Response<DeezerArtistListResponse>
    ): DeezerArtistListResponse? = try {
        val response = block()
        val body = response.body()
        if (!response.isSuccessful || body == null || body.isFailure) null else body
    } catch (e: Exception) {
        Log.w(TAG, "Artist lookup failed: ${e.message}")
        null
    }

    /**
     * Maps a Deezer track onto the app model.
     *
     * A track is only kept when Deezer actually handed back a playable preview
     * URL; otherwise it would be a silent, unplayable entry in the list.
     * Internal so the mapping can be unit-tested.
     */
    internal fun DeezerTrack.toSong(): Song? {
        val remoteId = id?.takeIf { it > 0 } ?: return null
        val previewUrl = preview?.takeIf { it.startsWith("https://") } ?: return null

        val artwork = album?.coverXl
            ?: album?.coverBig
            ?: album?.coverMedium
            ?: artist?.pictureXl
            ?: artist?.pictureBig
            ?: ""

        return Song(
            id = "$ID_PREFIX$remoteId",
            title = title?.trim().orEmpty().ifEmpty { "Untitled" },
            artist = artist?.name?.trim().orEmpty().ifEmpty { "Unknown Artist" },
            album = album?.title?.trim().orEmpty().ifEmpty { "Single" },
            artworkUrl = artwork,
            streamUrl = previewUrl,
            durationMs = (duration ?: 0).toLong() * 1000L,
            channelId = artist?.id?.toString(),
            viewCount = rank?.toString(),
            source = MusicSource.DEEZER,
            remoteId = remoteId.toString(),
            isPreview = true,
            shareUrl = link ?: SHARE_URL_PREFIX + remoteId
        )
    }

    companion object {
        const val ID_PREFIX = "deezer:"
        const val SHARE_URL_PREFIX = "https://www.deezer.com/track/"
        private const val TAG = "DeezerRepo"
        private const val BASE_URL = "https://api.deezer.com/"
        private const val SEARCH_LIMIT = 25
    }
}
