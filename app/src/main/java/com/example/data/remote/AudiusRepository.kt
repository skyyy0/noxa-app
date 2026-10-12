package com.example.data.remote

import android.util.Log
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

data class HomeMusicSection(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

private data class HomeSpec(
    val title: String,
    val subtitle: String,
    /** Audius genre used for curated trending, or null for the global chart. */
    val genre: String?,
    /** Search fallback when the genre chart returns nothing. */
    val query: String
)

/**
 * The single source of music data for NOXA.
 *
 * It is backed by the **Audius** public API, which returns real track metadata
 * *and* a real, directly playable audio stream for every track. That means:
 *
 * - search and home always return genuine API data (never a hardcoded list),
 * - every [Song] carries a real [Song.streamUrl] that Media3/ExoPlayer can play,
 * - no API key is required, so the app works with zero configuration.
 *
 * Audius content is uploaded by artists and streamed through their own API,
 * which is why it can be played legitimately (unlike the YouTube Data API, which
 * only exposes metadata and no audio).
 *
 * If the API cannot be reached the result is an empty list — the UI then shows an
 * honest loading/error state instead of simulated songs.
 */
class AudiusRepository {

    private val developerKey: String? =
        Secrets.get("AUDIUS_API_KEY").trim().takeIf { it.isNotEmpty() && !it.startsWith("REPLACE_WITH") }

    private val apiService: AudiusApiService by lazy {
        val logging = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(AudiusApiService::class.java)
    }

    /**
     * Direct, playable audio URL for a track.
     *
     * The `/stream` endpoint resolves (via redirect) to a freshly signed audio
     * file on Audius' content nodes, so it stays valid even though the signed
     * URLs embedded in metadata expire.
     */
    fun streamUrlFor(trackId: String): String =
        BASE_URL + "v1/tracks/" + trackId + "/stream?app_name=" + APP_NAME

    /** Real search results. Returns an empty list when the API fails. */
    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val response = safeCall {
            apiService.searchTracks(
                query = trimmed,
                limit = SEARCH_LIMIT,
                appName = APP_NAME,
                apiKey = developerKey
            )
        }
        response?.data.orEmpty().mapNotNull { it.toSong() }
    }

    /** Real global trending chart. */
    suspend fun trending(): List<Song> = withContext(Dispatchers.IO) {
        val response = safeCall {
            apiService.trendingTracks(limit = SEARCH_LIMIT, appName = APP_NAME, apiKey = developerKey)
        }
        response?.data.orEmpty().mapNotNull { it.toSong() }
    }

    /** Real trending chart for a single genre. */
    suspend fun trendingByGenre(genre: String): List<Song> = withContext(Dispatchers.IO) {
        val response = safeCall {
            apiService.trendingTracksByGenre(
                genre = genre,
                limit = SEARCH_LIMIT,
                appName = APP_NAME,
                apiKey = developerKey
            )
        }
        response?.data.orEmpty().mapNotNull { it.toSong() }
    }

    /**
     * Home sections built from live API data. Section titles are design choices,
     * but every track inside them is fetched from Audius — nothing is hardcoded.
     * Empty sections are dropped so the UI never shows an empty carousel.
     */
    suspend fun getHomeMusicSections(): List<HomeMusicSection> = coroutineScope {
        HOME_SPECS.map { spec ->
            async {
                val songs = if (spec.genre == null) {
                    trending()
                } else {
                    trendingByGenre(spec.genre).ifEmpty { search(spec.query) }
                }
                HomeMusicSection(spec.title, spec.subtitle, songs.take(MAX_SECTION_ITEMS))
            }
        }.awaitAll().filter { it.songs.isNotEmpty() }
    }

    private suspend fun safeCall(block: suspend () -> Response<AudiusTrackListResponse>): AudiusTrackListResponse? {
        return try {
            val response = block()
            if (response.isSuccessful) {
                response.body()
            } else {
                Log.w("AudiusRepo", "Request failed with HTTP ${response.code()}")
                null
            }
        } catch (e: Exception) {
            Log.w("AudiusRepo", "Request failed: ${e.message}")
            null
        }
    }

    /** Maps a raw Audius track onto the app model. Internal so it can be unit-tested. */
    internal fun AudiusTrack.toSong(): Song? {
        val trackId = id?.takeIf { it.isNotBlank() } ?: return null
        val artwork = artwork?.x480
            ?: artwork?.x1000
            ?: artwork?.x150
            ?: artwork?.mirrors?.firstOrNull()
            ?: ""
        return Song(
            id = trackId,
            title = title?.trim().orEmpty().ifEmpty { "Untitled" },
            artist = user?.name?.trim().orEmpty().ifEmpty { "Unknown Artist" },
            album = genre?.trim().orEmpty().ifEmpty { "Single" },
            artworkUrl = artwork,
            // Real, playable audio URL resolved by Audius.
            streamUrl = streamUrlFor(trackId),
            durationMs = (duration ?: 0).toLong() * 1000L,
            channelId = user?.handle,
            viewCount = playCount?.toString()
        )
    }

    private companion object {
        const val BASE_URL = "https://api.audius.co/"
        const val APP_NAME = "NOXA"
        const val SEARCH_LIMIT = 25
        const val MAX_SECTION_ITEMS = 12

        val HOME_SPECS = listOf(
            HomeSpec("Trending Now", "Sedang populer di Audius hari ini", null, ""),
            HomeSpec("Electronic", "House, techno & electronic picks", "Electronic", "electronic"),
            HomeSpec("Hip-Hop & Trap", "Beats dan rap dari komunitas Audius", "Hip-Hop/Rap", "hip hop"),
            HomeSpec("Lo-Fi & Chill", "Santai untuk fokus atau bersantai", "Lo-Fi", "lofi"),
            HomeSpec("R&B & Soul", "Groove hangat dengan vokal lembut", "R&B/Soul", "r&b soul")
        )
    }
}
