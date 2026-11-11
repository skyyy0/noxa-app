package com.example.data.support

import kotlin.random.Random

/**
 * The grammar of **@noxa in a listening room** — pure Kotlin, no Android
 * types, so the whole conversation layer is unit-testable.
 *
 * A member typing `@noxa puterin lagu Dewa 19 Kangen` gets the song on (and a
 * one-line answer). But a mention is not a command: `@noxa halo`, `@noxa lagu
 * ini enak ya`, `@noxa lagu apa yang cocok buat kerja?` are *conversation*, and
 * NOXA answers them without touching the player. That distinction is the whole
 * point of [Intent] — v3.6.3 searched the catalogue for literally every
 * mention, so small talk used to hijack the room's queue.
 *
 * Two play shapes are supported:
 *
 *  - **now** — `@noxa cariin lagu Dewa 19 Kangen` plays it immediately;
 *  - **next** — `@noxa puterin Kangen habis lagu ini` / `… antrian kedua`
 *    queues it behind whatever is playing.
 *
 * Parsing stays local and deterministic: playback must never depend on a model
 * answering. The model only writes casual replies; a song request is fixed by
 * the app and answered instantly with [playLine].
 */
object RoomAi {

    /** The one handle NOXA answers to. */
    const val HANDLE = "@noxa"

    /** Where the song goes. */
    enum class Mode {
        /** Play it now, interrupting the room. */
        NOW,

        /** Queue it behind the current song. */
        NEXT
    }

    /** What a mention actually wanted. */
    enum class Intent {
        /** A song request — search the catalogue and play/queue it. */
        PLAY,

        /** Ordinary conversation — answer in chat, leave the player alone. */
        CHAT
    }

    /**
     * What a mention asked for. [query] is blank when no title was given,
     * [text] is everything after the handle (used for chat replies) and
     * [advance] marks "ganti lagu" / "lanjut" without a named song.
     */
    data class Command(
        val query: String,
        val mode: Mode,
        val intent: Intent = Intent.PLAY,
        val text: String = "",
        val advance: Boolean = false
    )

    /** Phrases that mean "not yet, after this one". */
    private val NEXT_MARKERS = listOf(
        "habis lagu ini", "habis lagu", "abis lagu ini", "abis lagu",
        "setelah lagu ini", "setelah ini", "setelah lagu", "setelah",
        "antrian", "antrean", "antri", "antre", "queue", "next",
        "berikutnya", "nanti", "habis ini", "abis ini", "lanjut"
    )

    /**
     * Words that describe the *action* rather than the song. They are dropped
     * before the catalogue is searched, otherwise "puterin lagu Kangen" never
     * matches the track "Kangen".
     */
    private val FILLERS = setOf(
        "noxa", "tolong", "coba", "mohon", "bantu", "buatkan", "buatin", "bikin",
        "bikinin", "tulis", "tuliskan", "tulisin", "lirik", "liriknya", "lagu",
        "lagunya", "song", "songs", "musik", "music", "track", "putar", "puterin",
        "putarkan", "putarin", "mainkan", "play", "dengerin", "dengar", "carikan",
        "cariin", "carin", "cari", "temukan", "cariinlah", "kasih", "kasihin",
        "minta", "mau", "pengen", "ingin", "dong", "ya", "yah", "kak", "min",
        "please", "bisa", "kah", "itu", "ini", "yang", "untuk", "buat", "dari",
        "oleh", "dengan", "pakai", "versi", "asli", "resmi", "judul", "berjudul",
        "aja", "saja", "deh", "nih", "sih", "sekarang", "langsung", "juga",
        // Queue phrasing is dropped from the *query* too, so "habis lagu ini"
        // never becomes part of the song title being searched.
        "habis", "abis", "setelah", "sesudah", "antrian", "antrean", "antri",
        "antre", "queue", "next", "berikutnya", "nanti", "lanjut", "kedua",
        "pertama", "ketiga", "nomor", "ganti", "skip", "ulang", "ulangin",
        "setel", "setelin", "tambah", "tambahin"
    )

    /** Verbs that can only mean "put something on". */
    private val PLAY_VERBS = setOf(
        "putar", "puterin", "putarkan", "putarin", "mainkan", "play", "setel",
        "setelin", "cariin", "carikan", "cari", "carin", "temukan", "dengerin",
        "dengar", "ganti", "skip", "ulang", "ulangin", "tambahin", "tambah"
    )

    /** Nouns that make a bare title look like a song request. */
    private val SONG_NOUNS = setOf(
        "lagu", "lagunya", "song", "musik", "music", "track", "playlist"
    )

    /** Opening words of a genuine question — those are chat, not commands. */
    private val QUESTION_STARTS = setOf(
        "apa", "apakah", "gimana", "bagaimana", "gmna", "kenapa", "mengapa",
        "napa", "siapa", "kapan", "berapa", "mana", "menurut", "rekomendasi",
        "saran", "saranin", "rekomendasiin", "menurutmu", "boleh", "bisakah",
        "kenal", "tau", "tahu"
    )

    /** "lagu ini", "lagunya tadi" — a demonstrative is chat, not a title. */
    private val DEMONSTRATIVES = setOf(
        "ini", "itu", "apa", "yang", "tadi", "barusan", "sekarang", "aja",
        "saja", "kita", "kamu", "aku", "nya"
    )

    /** Opinions about the song that is already playing: never a request. */
    private val OPINIONS = setOf(
        "enak", "bagus", "keren", "mantap", "asik", "asyik", "jelek", "norak",
        "bosen", "bosan", "sedih", "seneng", "suka", "cinta", "galau", "rapi",
        "lucu", "boring", "garing"
    )

    private val MENTION_REGEX = Regex("(^|\\s)@noxa\\b", RegexOption.IGNORE_CASE)
    private val MENTION_STRIP = Regex("@noxa\\b", RegexOption.IGNORE_CASE)
    private val NON_WORD = Regex("[^\\p{L}\\p{N} ]")

    /** True when the message addresses NOXA. */
    fun mentionsNoxa(text: String): Boolean = MENTION_REGEX.containsMatchIn(text.trim())

    /**
     * Reads a mention, or null when the message is ordinary room chat.
     * A blank [Command.query] means "they called me but named no song".
     */
    fun command(text: String): Command? {
        if (!mentionsNoxa(text)) return null
        val withoutMention = text.replace(MENTION_STRIP, " ")
        val lower = withoutMention.lowercase(java.util.Locale.ROOT)
        val mode = if (NEXT_MARKERS.any { lower.contains(it) }) Mode.NEXT else Mode.NOW
        val intent = intentOf(lower)
        // A chat mention never carries a search query — that is the whole point
        // of the intent split, and keeping it blank makes a stray search
        // impossible even if a future caller forgets to check the intent.
        val query = if (intent == Intent.PLAY) queryOf(withoutMention) else ""
        return Command(
            query = query,
            mode = mode,
            intent = intent,
            text = withoutMention.trim(),
            advance = intent == Intent.PLAY && query.isBlank() && asksToAdvance(lower)
        )
    }

    /**
     * Play request vs. small talk.
     *
     * Explicit play verbs win outright ("cariin", "puterin", "dengerin"), so
     * "bisa puterin lagu galau?" still plays — a polite question is still a
     * request. Without one, a question mark, a question opener or an opinion
     * about the current song means the member is just talking.
     */
    fun intentOf(clean: String): Intent {
        val words = clean.lowercase(java.util.Locale.ROOT)
            .replace(NON_WORD, " ")
            .split(' ')
            .filter { it.isNotBlank() }
        if (words.isEmpty()) return Intent.CHAT

        if (words.any { it in PLAY_VERBS }) return Intent.PLAY

        val sentence = clean.lowercase(java.util.Locale.ROOT)
        if (NEXT_MARKERS.any { sentence.contains(it) }) return Intent.PLAY

        val asksQuestion = sentence.contains("?") || words.first() in QUESTION_STARTS
        val hasOpinion = words.any { it in OPINIONS }
        if (asksQuestion || hasOpinion) return Intent.CHAT

        // "lagu Kangen" / "Kangen Dewa 19" — a noun plus a real title.
        val nounIndex = words.indexOfFirst { it in SONG_NOUNS }
        if (nounIndex >= 0) {
            val after = words.drop(nounIndex + 1).filter { it !in FILLERS }
            val before = words.take(nounIndex).filter { it !in FILLERS }
            if (after.any { it !in DEMONSTRATIVES } ||
                before.any { it !in DEMONSTRATIVES }
            ) {
                return Intent.PLAY
            }
        }
        return Intent.CHAT
    }

    /** "ganti lagu" / "lanjut" with no title: skip, don't search. */
    private fun asksToAdvance(clean: String): Boolean =
        listOf("ganti", "lanjut", "next", "skip", "lain", "acak").any { clean.contains(it) }

    /**
     * Strips commands and filler from [text], leaving something a catalogue can
     * match ("kangen dewa 19"). Keeps the original casing — Deezer search does
     * better with "Dewa 19" than with an all-lowercase string.
     */
    fun queryOf(text: String): String =
        text.lowercase(java.util.Locale.ROOT)
            .replace(NON_WORD, " ")
            .split(' ')
            .filter { it.isNotBlank() && it !in FILLERS }
            .take(10)
            .joinToString(" ")
            .trim()

    /**
     * True while the draft's last token is a prefix of `@noxa` (`@`, `@n`,
     * `@noxa`), which is when the room shows its one-tap suggestion.
     */
    fun suggestsHandle(draft: String): Boolean {
        // A completed "@noxa " must not keep the suggestion on screen.
        if (draft.isBlank() || draft.last().isWhitespace()) return false
        val token = draft.trimEnd().substringAfterLast(' ').lowercase(java.util.Locale.ROOT)
        if (!token.startsWith("@")) return false
        return token.length <= HANDLE.length && HANDLE.startsWith(token)
    }

    /**
     * Completes the half-typed mention with a trailing space so the member can
     * keep typing the song: `"@n"` -> `"@noxa "`.
     */
    fun completeHandle(draft: String): String {
        val trimmed = draft.trimEnd()
        val head = trimmed.substringBeforeLast(' ', missingDelimiterValue = "")
        val token = trimmed.substringAfterLast(' ')
        if (!token.startsWith("@") || !HANDLE.startsWith(token.lowercase(java.util.Locale.ROOT))) {
            return if (trimmed.isEmpty()) "$HANDLE " else "$trimmed $HANDLE "
        }
        val prefix = if (head.isBlank()) "" else "$head "
        return "$prefix$HANDLE "
    }

    /**
     * NOXA's instant one-liner after a song request. Written locally so the
     * room never waits on a model to hear the song start: v3.6.3 published the
     * reply first and only then the publish that changes the track, which cost
     * seconds (and could fail outright on a slow write).
     */
    fun playLine(title: String, artist: String, mode: Mode): String {
        val by = if (artist.isBlank()) "\"$title\"" else "\"$title\" dari $artist"
        val lines = if (mode == Mode.NEXT) listOf(
            "Oke, $by masuk antrean berikutnya.",
            "Sudah kucatat: $by diputar setelah lagu ini.",
            "$by menunggu di antrean, siap setelah lagu ini."
        ) else listOf(
            "Siap, $by langsung kuputar.",
            "$by sekarang diputar, nikmati.",
            "Oke, ganti ke $by — volume enak juga nih."
        )
        return lines[Random.nextInt(lines.size)]
    }

    /**
     * Instant, always-correct line for casual chat, used when the model is in
     * maintenance or slow. Keeps NOXA present instead of silent.
     */
    fun casualReply(text: String, nowPlaying: String? = null): String {
        val clean = text.lowercase(java.util.Locale.ROOT)
        val song = nowPlaying?.takeIf { it.isNotBlank() }
        val greeting = listOf("halo", "hai", "hi", "hey", "pagi", "siang", "sore", "malam", "p")
        fun startsWithAny(words: List<String>) = words.any { w ->
            clean == w || clean.startsWith("$w ") || clean.contains(" $w ") ||
                clean.contains("$w,") || clean.contains("$w!")
        }
        return when {
            startsWithAny(greeting) && clean.length < 40 ->
                "Halo juga. Aku NOXA, DJ room ini — kalau mau lagu, bilang saja judulnya."

            startsWithAny(listOf("makasih", "terima kasih", "thanks", "thx", "tq")) ->
                "Sama-sama. Aku di sini kalau butuh lagu berikutnya."

            startsWithAny(listOf("siapa kamu", "kamu siapa", "noxa siapa")) ->
                "Aku NOXA, DJ yang jaga musik di room ini. Admin room tetap yang pegang kendali."

            clean.contains("lagu apa") || clean.contains("judul") || clean.contains("lagu siapa") ->
                if (song != null) "Sekarang sedang diputar $song, semua dengar lagu yang sama."
                else "Belum ada lagu di room ini. Sebut judulnya, nanti kuputar untuk semua."

            clean.contains("rekomendasi") || clean.contains("saran") ->
                if (song != null) "Lagi di $song. Kalau mau nuansa lain, sebut saja artisnya, kupilihkan."
                else "Sebut satu artis atau suasana, nanti kupilihkan lagunya untuk room ini."

            else -> if (song != null) {
                "Aku ikut dengar $song. Kalau mau ganti, sebut judulnya — admin tetap bisa menghentikanku."
            } else {
                "Aku di sini. Sebut judul lagu kalau mau kuputar, atau ngobrol saja dulu."
            }
        }
    }
}
