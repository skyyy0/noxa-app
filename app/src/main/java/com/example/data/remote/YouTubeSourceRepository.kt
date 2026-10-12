package com.example.data.remote

import android.util.Log
import com.example.data.model.MusicSource
import com.example.data.model.Song
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * YouTube as a music source, without any server of our own.
 *
 * Search uses the official **YouTube Data API v3** when `YOUTUBE_API_KEY` is
 * configured, and always also the keyless **Piped** / **Invidious** public
 * instance APIs. Playback is the part YouTube's own API can never do: it never
 * returns audio, so the actual stream URLs are resolved through public Piped
 * (preferred — its URLs are proxied and work from any device) or Invidious
 * instances at play time.
 *
 * Honest limitations, by design:
 *  * These are unofficial instance APIs. Instances go down; the app walks a
 *    fallback list and surfaces an error when all of them fail — it never
 *    silently fakes playback.
 *  * Resolved URLs are short-lived (a few hours) and are cached in memory only
 *    for the lifetime of the process.
 *  * NOXA does not implement YouTube's signature cipher / PO-token handling on
 *    the device; that battle is fought (and frequently updated) by the server
 *    side projects (yt-dlp) these instances run. Users who want a private
 *    pipeline can point `STREAM_RESOLVER_URL` at their own yt-dlp backend —
 *    it takes precedence over everything here.
 */
class YouTubeSourceRepository {

    private val dataApiKey: String? = Secrets.get("YOUTUBE_API_KEY").trim()
        .takeIf { it.isNotEmpty() }

    private val pipedInstances: List<String> =
        configuredInstances("PIPED_INSTANCE") + DEFAULT_PIPED_INSTANCES

    private val invidiousInstances: List<String> =
        configuredInstances("INVIDIOUS_INSTANCE") + DEFAULT_INVIDIOUS_INSTANCES

    private val moshi: Moshi = Moshi.Builder().build()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    /** Resolved stream URLs keyed by video id; URLs live ~6h, we keep them 4. */
    private val cacheLock = Any()
    private val streamCache = object : LinkedHashMap<String, CachedStreams>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, CachedStreams>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    /**
     * Deezer track id -> the YouTube video id that matched it. Searching once
     * per track per process is cheap; searching again on every tap is not.
     */
    private val matchCache = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, String>): Boolean =
            size > MAX_CACHE_ENTRIES
    }

    private data class CachedStreams(
        val audioUrl: String?,
        val videoUrl: String?,
        val fetchedAtMs: Long
    )

    // ----------------------------------------------------------------- search

    /**
     * Searches YouTube. Results are merged from every reachable provider and
     * de-duplicated by video id. Returned songs carry no [Song.streamUrl]; the
     * URL is resolved at play time by [resolveAudio].
     */
    suspend fun search(query: String): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()

        val fromDataApi = runCatching { dataApiSearch(trimmed) }.getOrDefault(emptyList())
        val fromPiped = runCatching { pipedSearch(trimmed) }.getOrDefault(emptyList())
        val fromInvidious = runCatching { invidiousSearch(trimmed) }.getOrDefault(emptyList())

        (fromDataApi + fromPiped + fromInvidious)
            .distinctBy { it.id }
            .take(MAX_SEARCH_RESULTS)
    }

    private fun dataApiSearch(query: String): List<Song> {
        val key = dataApiKey ?: return emptyList()
        val url = "https://www.googleapis.com/youtube/v3/search" +
            "?part=snippet&type=video&maxResults=25" +
            "&q=${encode(query)}&key=${encode(key)}"
        val body = getBody(url) ?: return emptyList()
        val items = moshi.adapter(YouTubeSearchListResponse::class.java)
            .fromJson(body)?.items ?: return emptyList()

        return items.mapNotNull { item ->
            val videoId = item.id?.videoId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val snippet = item.snippet
            Song(
                id = Song.idFor(MusicSource.YOUTUBE, videoId),
                title = snippet?.title.orEmpty().ifBlank { "YouTube video" },
                artist = snippet?.channelTitle.orEmpty().ifBlank { "YouTube" },
                album = ALBUM_LABEL,
                artworkUrl = snippet?.thumbnails?.high?.url
                    ?: snippet?.thumbnails?.medium?.url
                    ?: thumbnailFor(videoId),
                durationMs = 0L,
                source = MusicSource.YOUTUBE,
                remoteId = videoId
            )
        }
    }

    private suspend fun pipedSearch(query: String): List<Song> =
        firstInstanceResult(pipedInstances) { base ->
            val body = getBody("$base/search?q=${encode(query)}&filter=videos")
                ?: return@firstInstanceResult null
            val items = moshi.adapter(PipedSearchResponse::class.java)
                .fromJson(body)?.items ?: return@firstInstanceResult null
            items.mapNotNull { it.toSong() }
                .takeIf { it.isNotEmpty() }
        }.orEmpty()

    private suspend fun invidiousSearch(query: String): List<Song> =
        firstInstanceResult(invidiousInstances) { base ->
            val body = getBody("$base/api/v1/search?q=${encode(query)}&type=video")
                ?: return@firstInstanceResult null
            val listType = Types.newParameterizedType(
                List::class.java,
                InvidiousSearchItem::class.java
            )
            val items = moshi.adapter<List<InvidiousSearchItem>>(listType)
                .fromJson(body) ?: return@firstInstanceResult null
            items.mapNotNull { it.toSong() }
                .takeIf { it.isNotEmpty() }
        }.orEmpty()

    /**
     * Finds **full-length audio for a 30 second Deezer preview**.
     *
     * The track is searched on YouTube, every hit is scored by
     * [scoreMatch] (title coverage, artist, duration, junk penalties) and the
     * winner's audio is resolved through the same Piped/Invidious chain used
     * for native YouTube songs. Returns a copy of [song] with a playable
     * [Song.streamUrl] and `isPreview = false`, or `null` when nothing matches
     * confidently — the caller then falls back to the honest 30s preview.
     *
     * Only call this once the usage policy has been accepted.
     */
    suspend fun findFullAudio(song: Song): Song? {
        synchronized(cacheLock) { matchCache[song.id] }?.let { videoId ->
            val cachedAudio = streamsFor(videoId)?.audioUrl
            if (cachedAudio != null) {
                return song.copy(streamUrl = cachedAudio, isPreview = false)
            }
            synchronized(cacheLock) { matchCache.remove(song.id) }
        }

        val query = listOf(song.artist, song.title)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        if (query.isBlank()) return null

        val best = runCatching { search(query) }
            .getOrDefault(emptyList())
            .filter { it.remoteId.isNotBlank() }
            .map { candidate ->
                candidate to scoreMatch(
                    candidateTitle = candidate.title,
                    candidateChannel = candidate.artist,
                    candidateDurationMs = candidate.durationMs,
                    targetTitle = song.title,
                    targetArtist = song.artist,
                    targetDurationMs = song.durationMs
                )
            }
            .filter { (_, score) -> score >= MIN_MATCH_SCORE }
            .maxByOrNull { (_, score) -> score }
            ?.first
            ?: return null

        val audioUrl = streamsFor(best.remoteId)?.audioUrl ?: return null
        synchronized(cacheLock) { matchCache[song.id] = best.remoteId }
        return song.copy(streamUrl = audioUrl, isPreview = false)
    }

    // -------------------------------------------------------------- resolution

    /**
     * Resolves a playable audio URL for a YouTube song. Returns a copy of
     * [song] pointing at the resolved stream, or `null` when no instance could
     * provide one (the caller shows an honest error).
     */
    suspend fun resolveAudio(song: Song): Song? {
        val streams = streamsFor(song.remoteId.ifBlank { song.id.removePrefix(ID_PREFIX) })
            ?: return null
        val audioUrl = streams.audioUrl ?: return null
        return song.copy(streamUrl = audioUrl, isPreview = false)
    }

    /**
     * Resolves a video-only (or muxed) stream for the in-player "Music Video"
     * view. Played on a separate muted player so the main audio — and the
     * notification controls — keep running untouched.
     */
    suspend fun resolveVideoUrl(song: Song): String? {
        val videoId = song.remoteId.ifBlank { song.id.removePrefix(ID_PREFIX) }
        return streamsFor(videoId)?.videoUrl
    }

    /** Fetches (or returns cached) audio+video URLs for one video id. */
    private fun streamsFor(videoId: String): CachedStreams? {
        if (videoId.isBlank()) return null

        synchronized(cacheLock) {
            streamCache[videoId]?.let { cached ->
                if (System.currentTimeMillis() - cached.fetchedAtMs < CACHE_TTL_MS) return cached
            }
        }

        val fresh = pipedStreams(videoId) ?: invidiousStreams(videoId) ?: return null
        synchronized(cacheLock) { streamCache[videoId] = fresh }
        return fresh
    }

    private fun pipedStreams(videoId: String): CachedStreams? =
        firstInstanceResultSync(pipedInstances) { base ->
            val body = getBody("$base/streams/${encode(videoId)}")
                ?: return@firstInstanceResultSync null
            val response = moshi.adapter(PipedStreamsResponse::class.java)
                .fromJson(body) ?: return@firstInstanceResultSync null
            val audio = pickPipedAudioStream(response.audioStreams)?.url
            val video = pickPipedVideoStream(response.videoStreams)?.url
            if (audio == null && video == null) return@firstInstanceResultSync null
            CachedStreams(audioUrl = audio, videoUrl = video, fetchedAtMs = System.currentTimeMillis())
        }

    private fun invidiousStreams(videoId: String): CachedStreams? =
        firstInstanceResultSync(invidiousInstances) { base ->
            val body = getBody("$base/api/v1/videos/${encode(videoId)}")
                ?: return@firstInstanceResultSync null
            val response = moshi.adapter(InvidiousVideoResponse::class.java)
                .fromJson(body) ?: return@firstInstanceResultSync null
            val audio = pickInvidiousAudioFormat(response.adaptiveFormats)?.url
            val video = pickInvidiousVideoFormat(response.adaptiveFormats, response.formatStreams)
            if (audio == null && video == null) return@firstInstanceResultSync null
            CachedStreams(audioUrl = audio, videoUrl = video, fetchedAtMs = System.currentTimeMillis())
        }

    // ------------------------------------------------------------------ HTTP

    private fun getBody(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .header("User-Agent", USER_AGENT)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        } catch (e: IOException) {
            Log.w(TAG, "Request failed for $url: ${e.message}")
            null
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error for $url: ${e.message}")
            null
        }
    }

    /** Walks the instance list and returns the first non-null result. */
    private suspend fun <T> firstInstanceResult(
        instances: List<String>,
        call: suspend (String) -> T?
    ): T? {
        for (base in instances) {
            val result = runCatching { call(base) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun <T> firstInstanceResultSync(instances: List<String>, call: (String) -> T?): T? {
        for (base in instances) {
            val result = runCatching { call(base) }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun configuredInstances(name: String): List<String> =
        Secrets.get(name)
            .split(',')
            .map { it.trim().trimEnd('/') }
            .filter { it.startsWith("http") }

    // ------------------------------------------------------------- mappers

    private fun PipedSearchItem.toSong(): Song? {
        val videoId = (id?.takeIf { it.isNotBlank() } ?: extractVideoId(url)) ?: return null
        if (type != null && type != "stream") return null
        val seconds = duration ?: return null
        return Song(
            id = Song.idFor(MusicSource.YOUTUBE, videoId),
            title = title.orEmpty().ifBlank { "YouTube video" },
            artist = uploaderName.orEmpty().ifBlank { "YouTube" },
            album = ALBUM_LABEL,
            artworkUrl = thumbnail?.takeIf { it.startsWith("http") } ?: thumbnailFor(videoId),
            durationMs = seconds.coerceAtLeast(0L) * 1000L,
            source = MusicSource.YOUTUBE,
            remoteId = videoId
        )
    }

    private fun InvidiousSearchItem.toSong(): Song? {
        val videoId = videoId?.takeIf { it.isNotBlank() } ?: return null
        if (type != null && type != "video") return null
        return Song(
            id = Song.idFor(MusicSource.YOUTUBE, videoId),
            title = title.orEmpty().ifBlank { "YouTube video" },
            artist = author.orEmpty().ifBlank { "YouTube" },
            album = ALBUM_LABEL,
            artworkUrl = videoThumbnails
                ?.firstOrNull { it.url?.startsWith("http") == true }
                ?.url
                ?: thumbnailFor(videoId),
            durationMs = (lengthSeconds ?: 0L).coerceAtLeast(0L) * 1000L,
            source = MusicSource.YOUTUBE,
            remoteId = videoId
        )
    }

    // ------------------------------------------------- selection (pure, tested)

    /**
     * Best audio-only Piped stream: real audio entries only, preferring AAC/m4a
     * (decodes on every device) and then the highest bitrate.
     */
    fun pickPipedAudioStream(streams: List<PipedAudioStream>?): PipedAudioStream? =
        streams
            .orEmpty()
            .filter { it.videoOnly != true && it.url?.startsWith("http") == true }
            .maxWithOrNull(
                compareBy<PipedAudioStream> { it.isM4a() }
                    .thenBy { it.bitrate ?: 0L }
            )

    /**
     * Best video-only stream for the muted MV player: prefers ~360p (fast on
     * mobile data) and MP4/AVC over VP9.
     */
    fun pickPipedVideoStream(streams: List<PipedVideoStream>?): PipedVideoStream? =
        streams
            .orEmpty()
            .filter { it.videoOnly == true && it.url?.startsWith("http") == true }
            .minWithOrNull(
                compareBy<PipedVideoStream> { kotlin.math.abs((it.height ?: 9999) - MV_TARGET_HEIGHT) }
                    .thenBy { if (it.isMp4()) 0 else 1 }
                    .thenBy { -(it.bitrate ?: 0L) }
            )

    /** Best Invidious audio-only adaptive format (highest audio quality). */
    fun pickInvidiousAudioFormat(
        formats: List<InvidiousAdaptiveFormat>?
    ): InvidiousAdaptiveFormat? =
        formats
            .orEmpty()
            .filter {
                it.url?.startsWith("http") == true &&
                    it.type?.startsWith("audio/", ignoreCase = true) == true
            }
            .maxWithOrNull(
                compareBy<InvidiousAdaptiveFormat> { AUDIO_QUALITY_RANK[it.audioQuality] ?: 0 }
                    .thenBy { it.bitrate ?: 0L }
            )

    /**
     * Best Invidious video stream: video-only adaptive formats first (less
     * bandwidth for the muted MV player), muxed progressive streams as the
     * fallback, closest to 360p either way.
     */
    fun pickInvidiousVideoFormat(
        adaptive: List<InvidiousAdaptiveFormat>?,
        muxed: List<InvidiousFormatStream>?
    ): String? {
        val adaptiveVideo = adaptive
            .orEmpty()
            .filter {
                it.url?.startsWith("http") == true &&
                    it.type?.startsWith("video/", ignoreCase = true) == true &&
                    !it.type.contains("audio", ignoreCase = true)
            }
            .minWithOrNull(
                compareBy<InvidiousAdaptiveFormat> {
                    kotlin.math.abs((it.qualityLabel?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 9999) - MV_TARGET_HEIGHT)
                }.thenBy { if (it.type?.contains("mp4") == true) 0 else 1 }
            )
        if (adaptiveVideo?.url != null) return adaptiveVideo.url

        val muxedVideo = muxed
            .orEmpty()
            .filter {
                it.url?.startsWith("http") == true &&
                    (it.resolution?.contains("p") == true || it.qualityLabel?.contains("p") == true)
            }
            .minByOrNull {
                kotlin.math.abs(
                    ((it.qualityLabel ?: it.resolution)?.filter { c -> c.isDigit() }?.toIntOrNull() ?: 9999) - MV_TARGET_HEIGHT
                )
            }
        return muxedVideo?.url
    }

    private fun PipedAudioStream.isM4a(): Boolean =
        format.equals("m4a", ignoreCase = true) ||
            mimeType?.startsWith("audio/mp4", ignoreCase = true) == true

    private fun PipedVideoStream.isMp4(): Boolean =
        format.equals("m4a", ignoreCase = true) ||
            mimeType?.startsWith("video/mp4", ignoreCase = true) == true

    companion object {
        private const val TAG = "YouTubeSource"
        private const val USER_AGENT = "NOXA/1.9"
        private const val ID_PREFIX = "youtube:"
        private const val ALBUM_LABEL = "YouTube"
        private const val MAX_SEARCH_RESULTS = 30
        private const val MAX_CACHE_ENTRIES = 48
        private const val CACHE_TTL_MS = 4L * 60L * 60L * 1000L
        private const val MV_TARGET_HEIGHT = 360

        /**
         * Instances that answered live checks during development. Public
         * instance lists churn constantly, so this list is only a sensible
         * default — `PIPED_INSTANCE` / `INVIDIOUS_INSTANCE` in `.env` prepend
         * your own.
         */
        private val DEFAULT_PIPED_INSTANCES = listOf(
            "https://pipedapi.ducks.party",
            "https://api.piped.private.coffee"
        )

        private val DEFAULT_INVIDIOUS_INSTANCES = listOf(
            "https://invidious.f5.si"
        )

        private val AUDIO_QUALITY_RANK = mapOf(
            "high" to 3,
            "medium" to 2,
            "low" to 1,
            "tiny" to 0
        )

        /** Minimum [scoreMatch] result before a search hit is trusted. */
        private const val MIN_MATCH_SCORE = 40

        private const val MIN_TITLE_COVERAGE = 0.6
        private const val TITLE_MAX_SCORE = 60
        private const val ARTIST_EXACT_BONUS = 30
        private const val ARTIST_PARTIAL_BONUS = 15

        /** Words that never help identify a track (features, promo fluff). */
        private val NOISE_WORDS = setOf(
            "feat", "ft", "featuring", "official", "video", "audio", "clip",
            "music", "hd", "hq", "vevo", "mv", "lyrics", "lyric", "lirik",
            "remaster", "remastered"
        )

        /** Uploads that are never the track being searched for. */
        private val HARD_REJECT = setOf(
            "karaoke", "playlist", "mixtape", "reaction", "slowed", "nightcore",
            "hour", "hours", "compilation", "tutorial", "gameplay"
        )

        /**
         * Scores how likely a YouTube search hit is the same recording as the
         * target track. Pure and deterministic (unit-tested): title coverage
         * dominates, artist presence and duration proximity add, live/covers/
         * mixes subtract, karaoke/compilations are rejected outright.
         */
        internal fun scoreMatch(
            candidateTitle: String,
            candidateChannel: String,
            candidateDurationMs: Long,
            targetTitle: String,
            targetArtist: String,
            targetDurationMs: Long
        ): Int {
            val candidateTokens = tokensOf(candidateTitle, stripNoise = false)
            val targetTokens = tokensOf(targetTitle, stripNoise = true)
            if (targetTokens.isEmpty()) return 0

            if (candidateTokens.any { it in HARD_REJECT }) return 0
            if ("album" in candidateTokens && "album" !in targetTokens) return 0

            // Years and catalogue numbers in titles ("Song - 2011 Remaster")
            // rarely appear in the video title; only they are dropped from the
            // coverage basis, and never when they *are* the whole title.
            val identityTokens = targetTokens
                .filterNot { token -> token.all { it.isDigit() } && targetTokens.size > 1 }
                .ifEmpty { targetTokens.toList() }
                .toSet()
            val coverage =
                identityTokens.count { it in candidateTokens }.toDouble() / identityTokens.size
            if (coverage < MIN_TITLE_COVERAGE) return 0

            var score = (coverage * TITLE_MAX_SCORE).toInt()

            val artistTokens = tokensOf(targetArtist, stripNoise = true)
            val artistHits = artistTokens.count { it in candidateTokens }
            score += when {
                artistTokens.isNotEmpty() && artistHits == artistTokens.size -> ARTIST_EXACT_BONUS
                artistHits > 0 -> ARTIST_PARTIAL_BONUS
                else -> 0
            }

            if (targetDurationMs > 0 && candidateDurationMs > 0) {
                val diffSeconds = kotlin.math.abs(candidateDurationMs - targetDurationMs) / 1000
                score += when {
                    diffSeconds <= 3 -> 50
                    diffSeconds <= 15 -> 40
                    diffSeconds <= 30 -> 25
                    diffSeconds <= 90 -> 5
                    else -> -40
                }
            }

            val lowerTitle = candidateTitle.lowercase()
            if (lowerTitle.contains("official audio")) score += 15
            if ("live" in candidateTokens && "live" !in targetTokens) score -= 35
            if ("cover" in candidateTokens && "cover" !in targetTokens) score -= 60
            if ("remix" in candidateTokens && "remix" !in targetTokens) score -= 20
            if ("acoustic" in candidateTokens && "acoustic" !in targetTokens) score -= 25
            if ("mix" in candidateTokens && "mix" !in targetTokens) score -= 50
            if (tokensOf(candidateChannel, stripNoise = false).any { it == "topic" }) score += 20

            return score
        }

        /**
         * Lowercased word set. For targets (matching *against* a candidate) the
         * bracketed credits are dropped — "Kangen (feat. Ariel)" must still
         * match an upload titled just "KANGEN" — and so are noise words.
         * Candidates keep their full token set: extra words never hurt, but
         * junk words must stay visible to the hard-reject and penalty rules.
         */
        private fun tokensOf(raw: String, stripNoise: Boolean): Set<String> {
            val cleaned = if (stripNoise) {
                raw.replace(Regex("\\[[^\\[\\]]*]|\\([^()]*\\)"), " ")
            } else {
                raw
            }
            return cleaned.lowercase()
                .replace(Regex("[^a-z0-9]+"), " ")
                .split(' ')
                .filter { token ->
                    token.isNotEmpty() && (!stripNoise || token !in NOISE_WORDS)
                }
                .toSet()
        }

        /**
         * Extracts an 11-character video id from the shapes the instance APIs
         * hand out: `/watch?v=<id>`, a full watch/youtu.be URL, or a bare id.
         */
        fun extractVideoId(url: String?): String? {
            if (url.isNullOrBlank()) return null
            val fromQuery = if (url.contains("v=")) {
                url.substringAfter("v=").substringBefore('&').substringBefore('#')
            } else {
                url.substringAfterLast('/')
            }
            val id = fromQuery.substringBefore('?').substringBefore('&')
            return id.takeIf { Regex("[A-Za-z0-9_-]{11}").matches(it) }
        }

        fun thumbnailFor(videoId: String): String = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        private fun encode(value: String): String =
            java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
