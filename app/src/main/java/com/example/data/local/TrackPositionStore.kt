package com.example.data.local

import android.content.Context

/**
 * **Ingat posisi tiap lagu** (v3.7.0).
 *
 * The playback session already remembers where the *queue* stopped, but not
 * where each individual song was left. That is the difference between "NOXA
 * knows what I was playing" and "NOXA puts me back at 2:41 of this song", which
 * is what makes resuming a long mix or a podcast feel effortless.
 *
 * Positions are only stored when they are worth resuming (past 15 s and before
 * the last 15 s), so a song that merely finished does not restart near its own
 * end, and a track that was skipped after two seconds does not reopen at 0:02.
 * Old entries are pruned so the file stays a few kilobytes.
 */
class TrackPositionStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Saved position in ms, or 0 when the song should start from the top. */
    @Synchronized
    fun positionFor(songId: String, durationMs: Long): Long {
        if (songId.isBlank() || durationMs <= 0) return 0L
        val stored = runCatching {
            val root = org.json.JSONObject(prefs.getString(KEY, "{}") ?: "{}")
            root.optJSONObject(songId)?.optLong("ms") ?: 0L
        }.getOrDefault(0L)
        return if (stored in MIN_RESUME_MS until (durationMs - MIN_RESUME_MS)) stored else 0L
    }

    @Synchronized
    fun remember(songId: String, positionMs: Long, durationMs: Long) {
        if (songId.isBlank() || durationMs <= 0) return
        if (positionMs < MIN_RESUME_MS || positionMs > durationMs - MIN_RESUME_MS) {
            forget(songId)
            return
        }
        runCatching {
            val root = org.json.JSONObject(prefs.getString(KEY, "{}") ?: "{}")
            root.put(
                songId,
                org.json.JSONObject().apply {
                    put("ms", positionMs)
                    put("at", System.currentTimeMillis())
                }
            )
            // Keep the map bounded: drop the oldest quarter when it grows past
            // the cap instead of rewriting the whole thing on every save.
            if (root.length() > MAX_ENTRIES) {
                val keys = root.keys().asSequence().toList()
                    .sortedBy { key -> root.optJSONObject(key)?.optLong("at") ?: 0L }
                keys.take(root.length() - MAX_ENTRIES + PRUNE_COUNT).forEach(root::remove)
            }
            prefs.edit().putString(KEY, root.toString()).apply()
        }
    }

    @Synchronized
    fun forget(songId: String) {
        runCatching {
            val root = org.json.JSONObject(prefs.getString(KEY, "{}") ?: "{}")
            if (root.has(songId)) {
                root.remove(songId)
                prefs.edit().putString(KEY, root.toString()).apply()
            }
        }
    }

    private companion object {
        const val PREFS = "noxa_track_positions"
        const val KEY = "positions"
        const val MIN_RESUME_MS = 15_000L
        const val MAX_ENTRIES = 300
        const val PRUNE_COUNT = 60
    }
}
