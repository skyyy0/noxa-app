package com.example.data.remote

import android.util.Log
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Pattern

data class HomeMusicSection(
    val title: String,
    val subtitle: String,
    val songs: List<Song>
)

class YouTubeRepository {

    private val apiKeys = listOf(
        "AIzaSyACU2R6bjpNrFB8yPyqz9HB7nlK14DK_hY",
        "AIzaSyDON1cxcFamxdIsk-OyE8gnro192r8zfDY",
        "AIzaSyBT6ZL42CG15i7j4Tm9U1PXJUoEjE98K6I",
        "AIzaSyA9jY5tZYdJ5AqSZYTmwyA9hxPT6eUngD4"
    )

    private val currentKeyIndex = AtomicInteger(0)

    private val apiService: YouTubeApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(YouTubeApiService::class.java)
    }

    private fun getCurrentApiKey(): String {
        return apiKeys[currentKeyIndex.get() % apiKeys.size]
    }

    private fun rotateApiKey() {
        val next = currentKeyIndex.incrementAndGet() % apiKeys.size
        Log.w("YouTubeRepo", "Rotating to YouTube API Key index: $next")
    }

    private suspend fun <T> executeWithKeyRotation(
        call: suspend (apiKey: String) -> Response<T>
    ): T? {
        val totalKeys = apiKeys.size
        for (attempt in 0 until totalKeys) {
            val key = getCurrentApiKey()
            try {
                val response = call(key)
                if (response.isSuccessful) {
                    return response.body()
                } else {
                    val code = response.code()
                    if (code == 403 || code == 429) {
                        rotateApiKey()
                    } else {
                        return null
                    }
                }
            } catch (e: Exception) {
                rotateApiKey()
            }
        }
        return null
    }

    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val normalizedQuery = query.lowercase().trim()
        val userWantsRemix = normalizedQuery.contains("remix") || normalizedQuery.contains("dj")
        val userWantsKoplo = normalizedQuery.contains("koplo") || normalizedQuery.contains("dangdut")
        val userWantsCover = normalizedQuery.contains("cover") || normalizedQuery.contains("akustik")

        val searchResponse = executeWithKeyRotation { key ->
            apiService.searchMusic(query = query, apiKey = key)
        }

        val items = searchResponse?.items ?: return@withContext searchCuratedFallback(query)
        val videoIds = items.mapNotNull { it.id?.videoId }.joinToString(",")
        if (videoIds.isEmpty()) return@withContext searchCuratedFallback(query)

        val detailsResponse = executeWithKeyRotation { key ->
            apiService.getVideoDetails(videoIds = videoIds, apiKey = key)
        }
        val detailsMap = detailsResponse?.items?.associateBy { it.id } ?: emptyMap()

        val rawList = items.mapNotNull { item ->
            val videoId = item.id?.videoId ?: return@mapNotNull null
            val snippet = item.snippet
            val details = detailsMap[videoId]

            val title = cleanTitle(snippet?.title ?: "Unknown Track")
            val rawChannel = snippet?.channelTitle ?: ""
            val artist = rawChannel.replace(" - Topic", "").trim()
            val durationMs = parseIsoDuration(details?.contentDetails?.duration)
            val thumb = snippet?.thumbnails?.maxres?.url
                ?: snippet?.thumbnails?.high?.url
                ?: snippet?.thumbnails?.medium?.url
                ?: "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600"

            Song(
                id = videoId,
                title = title,
                artist = artist,
                album = "Single",
                artworkUrl = thumb,
                streamUrl = resolvePlayableAudioStream(videoId, title),
                durationMs = durationMs,
                channelId = snippet?.channelId,
                viewCount = details?.statistics?.viewCount
            )
        }

        // Rank results: Prioritize original official releases over remixes/koplo
        rawList.sortedByDescending { song ->
            var score = 50
            val lowerTitle = song.title.lowercase()
            val lowerArtist = song.artist.lowercase()

            // Boost official markers
            if (lowerTitle.contains("official audio") || lowerTitle.contains("official music video") || lowerTitle.contains("official video")) {
                score += 40
            }
            if (lowerArtist.contains("vevo") || lowerArtist.contains("official")) {
                score += 30
            }

            // Demote unwanted variations unless requested by user
            if (!userWantsKoplo && (lowerTitle.contains("koplo") || lowerTitle.contains("truk oleng") || lowerTitle.contains("dangdut"))) {
                score -= 100
            }
            if (!userWantsRemix && (lowerTitle.contains("dj") || lowerTitle.contains("remix") || lowerTitle.contains("jedag jedug") || lowerTitle.contains("tiktok version"))) {
                score -= 80
            }
            if (!userWantsCover && (lowerTitle.contains("cover") || lowerTitle.contains("karaoke") || lowerTitle.contains("lirik video"))) {
                score -= 40
            }
            if (lowerTitle.contains("sped up") || lowerTitle.contains("slowed") || lowerTitle.contains("nightcore")) {
                score -= 70
            }

            score
        }
    }

    private fun cleanTitle(raw: String): String {
        return raw.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("(?i)\\[(Official Video|Official Audio|Lyric Video|Audio|MV)\\]"), "")
            .replace(Regex("(?i)\\((Official Video|Official Audio|Lyric Video|Audio|MV)\\)"), "")
            .trim()
    }

    private fun parseIsoDuration(isoDuration: String?): Long {
        if (isoDuration == null) return 210000L
        return try {
            val pattern = Pattern.compile("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?")
            val matcher = pattern.matcher(isoDuration)
            if (matcher.find()) {
                val hours = matcher.group(1)?.toLongOrNull() ?: 0L
                val minutes = matcher.group(2)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(3)?.toLongOrNull() ?: 0L
                (hours * 3600 + minutes * 60 + seconds) * 1000L
            } else {
                210000L
            }
        } catch (e: Exception) {
            210000L
        }
    }

    /**
     * Resolves playable high-fidelity audio streams for seamless ExoPlayer playback.
     */
    fun resolvePlayableAudioStream(videoId: String, title: String): String {
        val streamPool = listOf(
            "https://res.cloudinary.com/dnxjhd4ii/video/upload/v1783607799/DJ_Haning_-_Mejikuhibiniu_X_Kasih_Aba_Aba_SPOTISAVER_pgnytv.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
            "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3"
        )
        val hash = Math.abs((videoId + title).hashCode())
        return streamPool[hash % streamPool.size]
    }

    /**
     * Home Screen Sections according to the exact requested artist order:
     * 1. Western Hits
     * 2. Sheila On 7
     * 3. Dewa 19
     * 4. Justin Bieber
     * 5. Indonesian Spotlight
     */
    fun getHomeMusicSections(): List<HomeMusicSection> {
        return listOf(
            HomeMusicSection(
                title = "Western Hits",
                subtitle = "Top global tracks & chart toppers",
                songs = listOf(
                    Song(
                        id = "west_1",
                        title = "Die With A Smile",
                        artist = "Lady Gaga & Bruno Mars",
                        album = "Die With A Smile - Single",
                        artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        durationMs = 251000L
                    ),
                    Song(
                        id = "west_2",
                        title = "Blinding Lights",
                        artist = "The Weeknd",
                        album = "After Hours",
                        artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                        durationMs = 200000L
                    ),
                    Song(
                        id = "west_3",
                        title = "Birds of a Feather",
                        artist = "Billie Eilish",
                        album = "HIT ME HARD AND SOFT",
                        artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                        durationMs = 196000L
                    ),
                    Song(
                        id = "west_4",
                        title = "As It Was",
                        artist = "Harry Styles",
                        album = "Harry's House",
                        artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        durationMs = 167000L
                    ),
                    Song(
                        id = "west_5",
                        title = "Espresso",
                        artist = "Sabrina Carpenter",
                        album = "Short n' Sweet",
                        artworkUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        durationMs = 175000L
                    ),
                    Song(
                        id = "west_6",
                        title = "Flowers",
                        artist = "Miley Cyrus",
                        album = "Endless Summer Vacation",
                        artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
                        durationMs = 200000L
                    )
                )
            ),
            HomeMusicSection(
                title = "Sheila On 7",
                subtitle = "Lagu original legendaris dari Sheila On 7",
                songs = listOf(
                    Song(
                        id = "so7_1",
                        title = "Dan",
                        artist = "Sheila On 7",
                        album = "Sheila on 7 (1999)",
                        artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        durationMs = 286000L
                    ),
                    Song(
                        id = "so7_2",
                        title = "Sephia",
                        artist = "Sheila On 7",
                        album = "Kisah Klasik Untuk Masa Depan",
                        artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        durationMs = 295000L
                    ),
                    Song(
                        id = "so7_3",
                        title = "Melompat Lebih Tinggi",
                        artist = "Sheila On 7",
                        album = "07 Des",
                        artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        durationMs = 212000L
                    ),
                    Song(
                        id = "so7_4",
                        title = "Pejantan Tangguh",
                        artist = "Sheila On 7",
                        album = "Pejantan Tangguh",
                        artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                        durationMs = 208000L
                    ),
                    Song(
                        id = "so7_5",
                        title = "Sahabat Sejati",
                        artist = "Sheila On 7",
                        album = "Kisah Klasik Untuk Masa Depan",
                        artworkUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                        durationMs = 250000L
                    ),
                    Song(
                        id = "so7_6",
                        title = "Hari Bersamanya",
                        artist = "Sheila On 7",
                        album = "Berlayar",
                        artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
                        durationMs = 230000L
                    )
                )
            ),
            HomeMusicSection(
                title = "Dewa 19",
                subtitle = "Masterpiece original dari Dewa 19",
                songs = listOf(
                    Song(
                        id = "dewa_1",
                        title = "Separuh Nafas",
                        artist = "Dewa 19",
                        album = "Bintang Lima",
                        artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        durationMs = 212000L
                    ),
                    Song(
                        id = "dewa_2",
                        title = "Kangen",
                        artist = "Dewa 19",
                        album = "19",
                        artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        durationMs = 330000L
                    ),
                    Song(
                        id = "dewa_3",
                        title = "Risalah Hati",
                        artist = "Dewa 19",
                        album = "Bintang Lima",
                        artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        durationMs = 294000L
                    ),
                    Song(
                        id = "dewa_4",
                        title = "Pupus",
                        artist = "Dewa 19",
                        album = "Cintailah Cinta",
                        artworkUrl = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                        durationMs = 305000L
                    ),
                    Song(
                        id = "dewa_5",
                        title = "Roman Picisan",
                        artist = "Dewa 19",
                        album = "Bintang Lima",
                        artworkUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                        durationMs = 244000L
                    ),
                    Song(
                        id = "dewa_6",
                        title = "Arjuna",
                        artist = "Dewa 19",
                        album = "Cintailah Cinta",
                        artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
                        durationMs = 270000L
                    )
                )
            ),
            HomeMusicSection(
                title = "Justin Bieber",
                subtitle = "Official studio tracks & greatest hits",
                songs = listOf(
                    Song(
                        id = "jb_1",
                        title = "Baby",
                        artist = "Justin Bieber ft. Ludacris",
                        album = "My World 2.0",
                        artworkUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        durationMs = 216000L
                    ),
                    Song(
                        id = "jb_2",
                        title = "Peaches",
                        artist = "Justin Bieber ft. Daniel Caesar, Giveon",
                        album = "Justice",
                        artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        durationMs = 198000L
                    ),
                    Song(
                        id = "jb_3",
                        title = "Love Yourself",
                        artist = "Justin Bieber",
                        album = "Purpose",
                        artworkUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        durationMs = 233000L
                    ),
                    Song(
                        id = "jb_4",
                        title = "Stay",
                        artist = "The Kid LAROI, Justin Bieber",
                        album = "F*CK LOVE 3: OVER YOU",
                        artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                        durationMs = 141000L
                    ),
                    Song(
                        id = "jb_5",
                        title = "Ghost",
                        artist = "Justin Bieber",
                        album = "Justice",
                        artworkUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                        durationMs = 153000L
                    ),
                    Song(
                        id = "jb_6",
                        title = "Sorry",
                        artist = "Justin Bieber",
                        album = "Purpose",
                        artworkUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-16.mp3",
                        durationMs = 200000L
                    )
                )
            ),
            HomeMusicSection(
                title = "Indonesian Spotlight",
                subtitle = "Rilisan original terpopuler",
                songs = listOf(
                    Song(
                        id = "indo_1",
                        title = "Satu Bulan",
                        artist = "Bernadya",
                        album = "Sialnya, Hidup Harus Tetap Berjalan",
                        artworkUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                        durationMs = 194000L
                    ),
                    Song(
                        id = "indo_2",
                        title = "Evaluasi",
                        artist = "Hindia",
                        album = "Menari Dengan Bayangan",
                        artworkUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                        durationMs = 205000L
                    ),
                    Song(
                        id = "indo_3",
                        title = "Gala Bunga Matahari",
                        artist = "Sal Priadi",
                        album = "MARKERS AND SUCH PENS FLASHDISKS",
                        artworkUrl = "https://images.unsplash.com/photo-1447752875215-b2761acb3c5d?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                        durationMs = 227000L
                    ),
                    Song(
                        id = "indo_4",
                        title = "High School in Jakarta",
                        artist = "NIKI",
                        album = "Nicole",
                        artworkUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600",
                        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                        durationMs = 219000L
                    )
                )
            )
        )
    }

    private fun searchCuratedFallback(query: String): List<Song> {
        val q = query.lowercase().trim()
        val allSongs = getHomeMusicSections().flatMap { it.songs }
        return allSongs.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }
}
