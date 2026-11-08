package com.example.data.support

/**
 * The grammar of **@noxa in a listening room** — pure Kotlin, no Android
 * types, so the whole conversation layer is unit-testable.
 *
 * A member types `@noxa puterin lagu Dewa 19 Kangen`, NOXA answers in the room
 * chat and puts the song on. Two shapes are supported:
 *
 *  - **now** — `@noxa cariin lagu Dewa 19 Kangen` plays it immediately;
 *  - **next** — `@noxa puterin Kangen habis lagu ini` / `… antrian kedua`
 *    queues it behind whatever is playing.
 *
 * The parsing is deliberately local and deterministic: playback must never
 * depend on a model answering, so the model only writes the chat line. If the
 * request has no usable title, the room asks what to play instead of guessing.
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

    /** What a mention asked for. [query] is blank when no title was given. */
    data class Command(val query: String, val mode: Mode)

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
        "pertama", "ketiga", "nomor"
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
        return Command(query = queryOf(withoutMention), mode = mode)
    }

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
}
