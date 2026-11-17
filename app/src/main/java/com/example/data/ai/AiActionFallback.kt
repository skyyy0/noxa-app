package com.example.data.ai

/**
 * Last line of defence for NOXA AI's player control (v3.7.6).
 *
 * The assistant is *supposed* to append a machine tag (`[NOXA_PLAY|…]`) to its
 * reply. When a model drops the tag — a bad completion, a very short answer, a
 * provider swap mid-conversation — the user used to get a nice sentence and no
 * music, which reads as "NOXA AI rusak".
 *
 * This parser reads the *user's own request* and the assistant's plain text and
 * reconstructs the obvious intent locally. It is deliberately conservative:
 * only explicit play verbs and explicit playlist requests produce an action,
 * and nothing is invented that the reply did not name.
 */
object AiActionFallback {

    private val PLAY_VERB = Regex(
        """\b(?:putarkan|putar|setelkan|setel|mainkan|play)\b\s+(.+)""",
        RegexOption.IGNORE_CASE
    )

    private val PLAYLIST_WORD = Regex("""\bplaylist\b""", RegexOption.IGNORE_CASE)

    /** A quoted song title, e.g. "Kangen" or 'Kangen'. */
    private val QUOTED = Regex("""["“']([^"”']{2,80})["”']""")

    /** A bullet/numbered `Artist - Title` line inside the reply. */
    private val TRACK_LINE = Regex(
        """^\s*(?:[-*•]|\d+[.)])?\s*(?:\[[^\]]*]\s*)?(.{1,80}?)\s+[-–—]\s+(.{1,120})$"""
    )

    /**
     * Returns actions only when the reply itself carried none. [userText] is
     * the message that triggered the reply.
     */
    fun parse(userText: String, replyText: String): List<NoxaAiAction> {
        val wantsPlaylist = PLAYLIST_WORD.containsMatchIn(userText) &&
            Regex("""\b(buat|buatkan|bikin|susun|simpan)\b""", RegexOption.IGNORE_CASE)
                .containsMatchIn(userText)

        if (wantsPlaylist) {
            val tracks = replyText.lineSequence()
                .mapNotNull { line -> TRACK_LINE.find(line.trim()) }
                .map { match -> AiTrackRef(artist = match.groupValues[1], title = match.groupValues[2]) }
                .filter { it.title.isNotBlank() && it.artist.isNotBlank() }
                .distinctBy { "${it.artist.lowercase()}|${it.title.lowercase()}" }
                .take(20)
                .toList()
            if (tracks.isNotEmpty()) {
                return listOf(
                    NoxaAiAction.CreatePlaylist(
                        name = playlistNameFrom(userText, replyText),
                        tracks = tracks
                    )
                )
            }
        }

        if (!PLAY_VERB.containsMatchIn(userText)) return emptyList()

        // Prefer something the reply quoted — that is what it actually chose.
        val quoted = QUOTED.find(replyText)?.groupValues?.get(1)?.trim()
        val fromUser = PLAY_VERB.find(userText)?.groupValues?.get(1)
            ?.substringBefore('.')
            ?.substringBefore(',')
            ?.replace(Regex("""(?i)\b(dong|ya|aja|saja|ke noxa|lagunya|lagu)\b"""), " ")
            ?.trim()
        val title = quoted?.takeIf { it.length in 2..80 } ?: fromUser?.takeIf { it.length in 2..80 }
            ?: return emptyList()

        // "Artist - Title" in the user's request is kept intact; otherwise the
        // catalogue search gets the phrase and the app picks the best match.
        val artist = title.substringBefore(" - ", "").trim()
        val songTitle = if (artist.isNotBlank()) title.substringAfter(" - ").trim() else title
        if (songTitle.isBlank()) return emptyList()
        return listOf(NoxaAiAction.Play(artist = artist, title = songTitle))
    }

    private fun playlistNameFrom(userText: String, replyText: String): String {
        val named = Regex(
            """(?i)playlist\s+(?:baru\s+)?(?:bernama|dengan nama|judul)?\s*["“]([^"”]{2,60})["”]"""
        ).find(userText)?.groupValues?.get(1)
        if (!named.isNullOrBlank()) return named.trim()
        val heading = replyText.lineSequence()
            .firstOrNull { it.contains("playlist", ignoreCase = true) }
            ?.replace(Regex("""(?i).*playlist\s*"""), "")
            ?.trim(' ', ':', '-', '*', '"')
        return heading?.takeIf { it.length in 2..60 } ?: "Playlist NOXA AI"
    }
}
