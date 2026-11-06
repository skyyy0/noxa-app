package com.example.data.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * One track the AI referred to. The wire format is `Artist - Title`, which is
 * the only shape that lets the app verify BOTH fields against the real
 * catalogue — the old title-only format is what let foreign songs slip into a
 * "Dewa 19" playlist.
 */
data class AiTrackRef(
    val artist: String,
    val title: String
) {
    /** `Artist - Title` for display/prompt round-trips. */
    val wire: String
        get() = if (artist.isBlank()) title else "$artist - $title"

    companion object {
        fun parse(raw: String): AiTrackRef {
            val clean = raw.trim().trim('"', '\'', ';')
            val separator = clean.indexOf(" - ")
            return if (separator > 0) {
                AiTrackRef(
                    artist = clean.substring(0, separator).trim(),
                    title = clean.substring(separator + 3).trim()
                )
            } else {
                AiTrackRef(artist = "", title = clean)
            }
        }
    }
}

/**
 * A music action the AI asked the app to execute, carried inside its reply as
 * a machine-readable tag. The assistant cannot touch the player directly — it
 * *declares* intent via tags and the app executes them, which keeps the blast
 * radius of a creative LLM nicely contained.
 */
sealed interface NoxaAiAction {
    /** Play (or offer to play) one song: `[NOXA_PLAY|artist|title]`. */
    data class Play(val artist: String, val title: String) : NoxaAiAction

    /** Create a playlist: `[NOXA_PLAYLIST|name|Artist - Title;...]`. */
    data class CreatePlaylist(val name: String, val tracks: List<AiTrackRef>) : NoxaAiAction

    /** Queue tracks after the current one: `[NOXA_QUEUE|Artist - Title;...]`. */
    data class Queue(val tracks: List<AiTrackRef>) : NoxaAiAction

    /** Swap a stray track out of a playlist: `[NOXA_REPLACE|playlist|old|new]`. */
    data class ReplaceTrack(
        val playlistName: String,
        val old: AiTrackRef,
        val new: AiTrackRef
    ) : NoxaAiAction

    /** Set the equalizer preset: `[NOXA_EQ|preset]`. */
    data class Equalizer(val preset: String) : NoxaAiAction
}

/** Parses/strips NOXA AI action tags. Kept pure so unit tests can pin it. */
object NoxaAiActions {

    private val PLAY = Regex("""\[NOXA_PLAY\|([^|\]\n]+)\|([^\]\n]+)]""")
    private val PLAYLIST = Regex("""\[NOXA_PLAYLIST\|([^|\]\n]+)\|([^\]\n]+)]""")
    private val QUEUE = Regex("""\[NOXA_QUEUE\|([^\]\n]+)]""")
    private val REPLACE = Regex("""\[NOXA_REPLACE\|([^|\]\n]+)\|([^|\]\n]+)\|([^\]\n]+)]""")
    private val EQ = Regex("""\[NOXA_EQ\|([^\]\n]+)]""")

    /**
     * Splits a raw reply into its display text and the actions it requested,
     * in order of appearance. Tags never leak into the chat bubble.
     */
    fun parse(reply: String): Pair<String, List<NoxaAiAction>> {
        val actions = mutableListOf<NoxaAiAction>()
        PLAY.findAll(reply).forEach { match ->
            actions.add(
                NoxaAiAction.Play(
                    artist = match.groupValues[1].trim(),
                    title = match.groupValues[2].trim()
                )
            )
        }
        PLAYLIST.findAll(reply).forEach { match ->
            actions.add(
                NoxaAiAction.CreatePlaylist(
                    name = match.groupValues[1].trim(),
                    tracks = splitTracks(match.groupValues[2])
                )
            )
        }
        QUEUE.findAll(reply).forEach { match ->
            actions.add(NoxaAiAction.Queue(splitTracks(match.groupValues[1])))
        }
        REPLACE.findAll(reply).forEach { match ->
            actions.add(
                NoxaAiAction.ReplaceTrack(
                    playlistName = match.groupValues[1].trim(),
                    old = AiTrackRef.parse(match.groupValues[2]),
                    new = AiTrackRef.parse(match.groupValues[3])
                )
            )
        }
        EQ.findAll(reply).forEach { match ->
            actions.add(NoxaAiAction.Equalizer(match.groupValues[1].trim()))
        }
        val text = reply
            .replace(PLAY, "")
            .replace(PLAYLIST, "")
            .replace(QUEUE, "")
            .replace(REPLACE, "")
            .replace(EQ, "")
        return text.trim() to actions
    }

    private fun splitTracks(raw: String): List<AiTrackRef> =
        raw.split(';')
            .map { AiTrackRef.parse(it) }
            .filter { it.title.isNotBlank() }

    /** Removes emoji glyphs — the persona forbids them, belt and braces. */
    fun stripEmoji(text: String): String = text
        .replace(Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{1F1E6}-\\x{1F1FF}]"), "")
        .replace(Regex("[ \\t]+\\n"), "\\n")
        .trim()
}

/** One chat message rendered in the NOXA AI screen. */
data class AiChatMessage(
    val fromUser: Boolean,
    val text: String,
    /** The song the AI picked, shown as a "Dipilih oleh NOXA AI" card. */
    val songId: String? = null,
    val songTitle: String? = null,
    val songArtist: String? = null,
    val songArtwork: String? = null,
    val songNote: String? = null,
    val playlistName: String? = null,
    /** Set when this reply is the result of an AI-built playlist. */
    val playlistId: Long? = null,
    val playlistTrackCount: Int = 0,
    val playlistCoverSeed: Int = 0
)

/** Result of one AI round-trip. */
data class AiReply(
    val text: String,
    val actions: List<NoxaAiAction>,
    /** Model that actually answered (may differ after a fallback). */
    val servedBy: AiModel = AiModel.DEFAULT,
    /** Set when the requested model was down and the default took over. */
    val fallbackFrom: AiModel? = null
)

/**
 * The NOXA AI chat backend — **official API** (v3.5.5).
 *
 * Replaces the old scraped/keyless Omegatech endpoint with freemodel.in's
 * OpenAI-compatible API:
 *
 * ```
 * POST https://freemodel.in/v1/chat/completions
 * Authorization: Bearer <key>
 * { "model": "glm-5.3-flash", "messages": [...] }
 * ```
 *
 * A real system prompt travels in `messages[0]`, and the last turns of the
 * transcript are replayed as `assistant`/`user` turns, so the conversation
 * survives app restarts with proper context (the old provider only kept a
 * session id).
 *
 * Model availability is read from `GET /v1/models` and cached for ten minutes:
 * a model that disappears from that list is shown as *maintenance* in the
 * picker instead of failing silently when the user taps it.
 */
class NoxaAiRepository(context: Context) {

    private val prefs = context.getSharedPreferences("noxa_ai", Context.MODE_PRIVATE)

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    // --------------------------------------------------------------- model

    /** Model currently selected by the user (persisted). */
    var selectedModel: AiModel
        get() = AiModel.fromId(prefs.getString(KEY_MODEL, AiModel.DEFAULT.id))
        set(value) = prefs.edit().putString(KEY_MODEL, value.id).apply()

    /** Last known availability snapshot, persisted so the picker has data offline. */
    fun cachedModelStatus(): List<AiModelStatus> {
        val raw = prefs.getString(KEY_MODEL_STATUS, null) ?: return allAvailable()
        return try {
            val obj = JSONObject(raw)
            AiModel.entries.map { model ->
                AiModelStatus(model, obj.optBoolean(model.id, true))
            }
        } catch (e: Exception) {
            allAvailable()
        }
    }

    private fun allAvailable(): List<AiModelStatus> =
        AiModel.entries.map { AiModelStatus(it, true) }

    /**
     * Refreshes model availability from `/v1/models`. A network failure keeps
     * the previous snapshot (never blocks the chat), because the important
     * signal is "absent from a successful list", not "we could not ask".
     */
    suspend fun refreshModelStatus(): List<AiModelStatus> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(NoxaAiConfig.modelsUrl)
            .header("Authorization", "Bearer ${NoxaAiConfig.apiKey}")
            .header("Accept", "application/json")
            .get()
            .build()
        val body = runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull() ?: return@withContext cachedModelStatus()

        val ids = runCatching {
            val root = JSONObject(body)
            val data = root.optJSONArray("data") ?: JSONArray()
            (0 until data.length()).mapNotNull { index ->
                data.optJSONObject(index)?.optString("id")
            }
        }.getOrNull()

        if (ids.isNullOrEmpty()) return@withContext cachedModelStatus()

        val statuses = AiModel.entries.map { model ->
            AiModelStatus(model, ids.any { it.equals(model.id, ignoreCase = true) })
        }
        val cache = JSONObject()
        statuses.forEach { cache.put(it.model.id, it.available) }
        prefs.edit().putString(KEY_MODEL_STATUS, cache.toString()).apply()
        statuses
    }

    // ---------------------------------------------------------------- chat

    val sessionId: String? get() = prefs.getString(KEY_SESSION, null)?.takeIf { it.isNotBlank() }

    /**
     * Sends one user message and returns the cleaned reply with its actions.
     *
     * If the selected model is down (missing from `/v1/models`, or the request
     * fails with 404/429/5xx) the message is retried once on the default model,
     * so a maintenance window never costs the user their message.
     */
    suspend fun chat(
        userMessage: String,
        memoryContext: String? = null
    ): AiReply = withContext(Dispatchers.IO) {
        val requested = selectedModel
        try {
            requestCompletion(requested, userMessage, memoryContext)?.let { raw ->
                return@withContext parseReply(raw, requested, fallbackFrom = null)
            }
            throw IllegalStateException("empty reply")
        } catch (e: Exception) {
            if (requested == AiModel.DEFAULT) throw e
            val fallback = requestCompletion(AiModel.DEFAULT, userMessage, memoryContext)
                ?: throw e
            parseReply(fallback, AiModel.DEFAULT, fallbackFrom = requested)
        }
    }

    /** One POST; returns the assistant content or null. Throws on hard failure. */
    private fun requestCompletion(
        model: AiModel,
        userMessage: String,
        memoryContext: String?
    ): String? {
        val messages = JSONArray()
        messages.put(JSONObject().put("role", "system").put("content", systemPrompt(memoryContext)))
        // Replay a short window of the transcript for continuity.
        loadHistory().takeLast(HISTORY_TURNS)
            .filter { it.text.isNotBlank() }
            .forEach { message ->
                messages.put(
                    JSONObject()
                        .put("role", if (message.fromUser) "user" else "assistant")
                        .put("content", message.text)
                )
            }
        messages.put(JSONObject().put("role", "user").put("content", userMessage))

        val payload = JSONObject()
            .put("model", model.id)
            .put("messages", messages)
            .put("temperature", 0.7)
            .put("max_tokens", 900)
            .toString()

        val request = Request.Builder()
            .url(NoxaAiConfig.chatUrl)
            .header("Authorization", "Bearer ${NoxaAiConfig.apiKey}")
            .header("Content-Type", "application/json")
            .header("User-Agent", "NOXA-Music/3.5.5")
            .post(payload.toRequestBody(json))
            .build()

        val raw = client.newCall(request).execute().use { response ->
            val text = response.body?.string()
            if (!response.isSuccessful) {
                // Surface the HTTP code so the caller can decide about fallback.
                throw HttpFailure(response.code, text.orEmpty().take(200))
            }
            text
        } ?: return null

        val content = runCatching {
            val root = JSONObject(raw)
            root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
                .ifBlank { root.optString("response") }
        }.getOrDefault("")

        if (content.isBlank()) return null
        // New model token is stored so a future provider change has a hook.
        runCatching {
            JSONObject(raw).optString("id").takeIf { it.isNotBlank() }?.let {
                prefs.edit().putString(KEY_SESSION, it).apply()
            }
        }
        return content
    }

    private fun parseReply(raw: String, servedBy: AiModel, fallbackFrom: AiModel?): AiReply {
        val cleaned = NoxaAiActions.stripEmoji(raw)
        val (text, actions) = NoxaAiActions.parse(cleaned)
        return AiReply(
            text = text.ifBlank { "Siap." },
            actions = actions,
            servedBy = servedBy,
            fallbackFrom = fallbackFrom
        )
    }

    /** Starts a fresh conversation (called from the chat header menu). */
    fun resetSession() {
        prefs.edit().remove(KEY_SESSION).remove(KEY_HISTORY).apply()
    }

    /** Restores the locally persisted chat transcript (newest last). */
    fun loadHistory(): List<AiChatMessage> {
        val raw = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                AiChatMessage(
                    fromUser = obj.optBoolean("fromUser"),
                    text = obj.optString("text"),
                    songId = obj.optString("songId").takeIf { it.isNotBlank() },
                    songTitle = obj.optString("songTitle").takeIf { it.isNotBlank() },
                    songArtist = obj.optString("songArtist").takeIf { it.isNotBlank() },
                    songArtwork = obj.optString("songArtwork").takeIf { it.isNotBlank() },
                    songNote = obj.optString("songNote").takeIf { it.isNotBlank() },
                    playlistName = obj.optString("playlistName").takeIf { it.isNotBlank() },
                    playlistId = obj.optLong("playlistId", 0L).takeIf { it > 0L },
                    playlistTrackCount = obj.optInt("playlistTrackCount", 0),
                    playlistCoverSeed = obj.optInt("playlistCoverSeed", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Persists the transcript so reopening the chat keeps the conversation. */
    fun saveHistory(messages: List<AiChatMessage>) {
        val array = JSONArray()
        messages.takeLast(HISTORY_LIMIT).forEach { message ->
            array.put(
                JSONObject()
                    .put("fromUser", message.fromUser)
                    .put("text", message.text)
                    .put("songId", message.songId ?: "")
                    .put("songTitle", message.songTitle ?: "")
                    .put("songArtist", message.songArtist ?: "")
                    .put("songArtwork", message.songArtwork ?: "")
                    .put("songNote", message.songNote ?: "")
                    .put("playlistName", message.playlistName ?: "")
                    .put("playlistId", message.playlistId ?: 0L)
                    .put("playlistTrackCount", message.playlistTrackCount)
                    .put("playlistCoverSeed", message.playlistCoverSeed)
            )
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    /** HTTP failure with the status code kept for fallback decisions. */
    class HttpFailure(val code: Int, val body: String) :
        Exception("HTTP $code ${body.take(120)}")

    companion object {
        private const val KEY_SESSION = "session_id"
        private const val KEY_HISTORY = "history_json"
        private const val KEY_MODEL = "ai_model"
        private const val KEY_MODEL_STATUS = "model_status_json"
        private const val HISTORY_LIMIT = 120
        private const val HISTORY_TURNS = 12

        /**
         * The persona, sent as the system message on every request.
         *
         * Deliberately strict: identity, the tag protocol for player control,
         * Indonesian-only, a hard no-emoji rule, and the *catalogue honesty*
         * rule that fixes the "album Dewa 19 but a random foreign song slipped
         * in" bug — every track must be written as `Artist - Title` so the app
         * can reject a mismatch instead of accepting the first search hit.
         */
        internal val SYSTEM_PROMPT = """
Kamu adalah "NOXA AI", asisten musik pribadi di dalam aplikasi NOXA Music. Panggil pengguna dengan namanya kalau dia sudah menyebutkannya.

KARAKTER: hangat, profesional, ceria secukupnya — seperti asisten resmi yang sangat paham musik. Bahasa Indonesia baku yang rapi: ejaan benar, kalimat runtut, tanpa bahasa gaul atau singkatan seperti "gue", "nih", "dong". Sapa pengguna dengan "Anda". Humor ringan boleh, norak tidak.

KEMAMPUAN KHUSUS: kamu TERHUBUNG ke pemutar musik NOXA. Semua perintah ditulis sebagai tag di baris TERAKHIR balasan:

1. Putar satu lagu:
[NOXA_PLAY|Artis|Judul]
2. Buat playlist baru:
[NOXA_PLAYLIST|Nama Playlist|Artis - Judul;Artis - Judul;Artis - Judul]
3. Tambahkan ke antrean setelah lagu yang sedang diputar:
[NOXA_QUEUE|Artis - Judul;Artis - Judul]
4. Ganti satu lagu yang tidak cocok di dalam playlist:
[NOXA_REPLACE|Nama Playlist|Artis Lama - Judul Lama|Artis Baru - Judul Baru]
5. Atur equalizer (pilihan: Flat, Bass, Vocal, Rock, Pop, Jazz, Elektronik, Akustik):
[NOXA_EQ|Bass]

ATURAN LAGU (SANGAT PENTING):
- Setiap lagu WAJIB ditulis "Artis - Judul" lengkap, karena aplikasi mencocokkannya ke katalog nyata dan akan MENOLAK lagu yang artisnya tidak cocok.
- Kalau pengguna meminta album atau lagu dari satu artis (contoh: "album Dewa 19"), SEMUA lagu dalam daftar harus dari artis itu. Jangan menyisipkan lagu artis lain.
- Jangan mengarang judul. Kalau ragu apakah lagu ada, pilih lagu yang paling terkenal dari artis tersebut dan sebutkan pilihanmu.
- Maksimal 20 lagu per playlist.

ATURAN GAYA:
- JANGAN PERNAH memakai emoji, karakter dekoratif, atau ASCII art.
- 1-4 kalimat singkat, kecuali pengguna meminta penjelasan panjang.
- Jangan menyebut dirimu model bahasa atau AI dari perusahaan lain. Kamu NOXA AI, bagian dari NOXA Music.
""".trimIndent()

        /** System prompt plus optional song-memory context. */
        internal fun systemPrompt(memoryContext: String?): String =
            if (memoryContext.isNullOrBlank()) SYSTEM_PROMPT
            else SYSTEM_PROMPT + "\n\n" + memoryContext
    }
}
