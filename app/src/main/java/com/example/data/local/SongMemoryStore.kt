package com.example.data.local

import android.content.Context
import org.json.JSONObject

/** One saved memory on a song — the "diary" entry for that track. */
data class SongMemory(
    val songId: String,
    val note: String,
    val savedAt: Long,
    val title: String = "",
    val artist: String = ""
)

/**
 * **Memori Lagu** (v3.5.5) — catatan pribadi per lagu ("ini yang saya dengar
 * waktu nunggu hasil"). Disimpan lokal, kunci = songId, jadi catatan menempel
 * pada lagu yang sama dari sumber mana pun.
 *
 * Dipakai dua tempat:
 *  - sheet pemutar: pengguna menulis/menghapus catatan untuk lagu aktif;
 *  - NOXA AI: [contextFor] mengirim ringkasan memori terbaru sebagai konteks,
 *    supaya asisten bisa menyebutkan momen itu tanpa diingatkan.
 */
class SongMemoryStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun forSong(songId: String): SongMemory? {
        val raw = prefs.getString(key(songId), null) ?: return null
        return parse(songId, raw)
    }

    /** Saves (or replaces) the memory for [songId]; a blank note deletes it. */
    fun save(songId: String, note: String, title: String, artist: String) {
        if (songId.isBlank()) return
        val trimmed = note.trim()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(key(songId)).apply()
            return
        }
        val obj = JSONObject()
            .put("note", trimmed.take(MAX_NOTE))
            .put("at", System.currentTimeMillis())
            .put("title", title.take(120))
            .put("artist", artist.take(120))
        prefs.edit().putString(key(songId), obj.toString()).apply()
    }

    fun remove(songId: String) = prefs.edit().remove(key(songId)).apply()

    /** All memories, newest first. */
    fun all(): List<SongMemory> {
        val out = mutableListOf<SongMemory>()
        prefs.all.forEach { (storedKey, value) ->
            if (!storedKey.startsWith(PREFIX)) return@forEach
            val songId = storedKey.removePrefix(PREFIX)
            val memory = parse(songId, value as? String ?: return@forEach)
            if (memory != null) out.add(memory)
        }
        return out.sortedByDescending { it.savedAt }
    }

    /**
     * Compact context for the system prompt: the newest memories, one line
     * each. Empty when the user never wrote one — the prompt then stays lean.
     */
    fun contextFor(limit: Int = 5): String? {
        val memories = all().take(limit)
        if (memories.isEmpty()) return null
        val lines = memories.joinToString("\n") { memory ->
            val who = listOf(memory.artist, memory.title)
                .filter { it.isNotBlank() }
                .joinToString(" - ")
            "- " + (if (who.isBlank()) "Lagu" else who) + ": " + memory.note
        }
        return "MEMORI LAGU PENGGUNA (diary pribadi, boleh disebut natural kalau relevan):\n" + lines
    }

    /** Memories that mention a query string, for the "cari memori" field. */
    fun search(query: String): List<SongMemory> {
        val q = query.trim()
        if (q.isEmpty()) return all()
        return all().filter { memory ->
            memory.note.contains(q, ignoreCase = true) ||
                memory.title.contains(q, ignoreCase = true) ||
                memory.artist.contains(q, ignoreCase = true)
        }
    }

    private fun parse(songId: String, raw: String): SongMemory? = try {
        val obj = JSONObject(raw)
        val note = obj.optString("note")
        if (note.isBlank()) {
            null
        } else {
            SongMemory(
                songId = songId,
                note = note,
                savedAt = obj.optLong("at", 0L),
                title = obj.optString("title"),
                artist = obj.optString("artist")
            )
        }
    } catch (e: Exception) {
        null
    }

    private fun key(songId: String) = PREFIX + songId

    private companion object {
        const val NAME = "noxa_song_memories"
        const val PREFIX = "m:"
        const val MAX_NOTE = 400
    }
}
