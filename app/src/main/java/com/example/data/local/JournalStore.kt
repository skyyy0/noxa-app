package com.example.data.local

import android.content.Context

/** Satu pilihan mood di Jurnal Dengar. */
data class JournalMood(val id: String, val label: String)

/**
 * Pilihan mood Jurnal Dengar — chip nada berwarna, bukan emoji (emoji tidak
 * konsisten di launcher OEM dan tidak pernah render di kartu NOXA).
 */
object JournalMoods {
    val ALL = listOf(
        JournalMood("santai", "Santai"),
        JournalMood("fokus", "Fokus"),
        JournalMood("semangat", "Semangat"),
        JournalMood("nostalgia", "Nostalgia")
    )

    fun of(id: String): JournalMood? = ALL.firstOrNull { it.id == id }
}

/**
 * **Jurnal Dengar** (v3.3.3) — mood per hari, disimpan lokal.
 *
 * Kuncinya adalah epoch day yang sama dengan [ListeningHeatmap] (`playedAt /
 * 86_400_000`), jadi catatan jurnal dan kotak peta panas menunjuk hari yang
 * identik tanpa perlu konversi zona waktu. SharedPreferences biasa: kecil,
 * sinkron, dan bisa dibaca sebelum Compose state apa pun ada.
 *
 * Menulis `""` menghapus catatan hari itu (mood dilepas lagi), bukan menyimpan
 * string kosong — peta hasil selalu berisi id mood yang valid saja.
 */
class JournalStore(context: Context) {

    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    /** Mood untuk satu hari, atau null bila hari itu tidak dicatat. */
    fun moodFor(dayIndex: Long): String? =
        prefs.getString(key(dayIndex), null)?.takeIf { it.isNotBlank() }

    /** Simpan (id tidak kosong) atau hapus ("" / null) catatan hari ini. */
    fun setMood(dayIndex: Long, moodId: String?) {
        val k = key(dayIndex)
        if (moodId.isNullOrBlank()) {
            prefs.edit().remove(k).apply()
        } else {
            prefs.edit().putString(k, moodId).apply()
        }
    }

    /**
     * Sebaran mood untuk daftar hari — dipakai strip 7 hari terakhir. Satu
     * pembacaan, hasil sebagai map; hari tanpa catatan tidak muncul di map.
     */
    fun moodsFor(days: List<Long>): Map<Long, String> {
        if (days.isEmpty()) return emptyMap()
        val out = HashMap<Long, String>(days.size)
        for (day in days) {
            moodFor(day)?.let { out[day] = it }
        }
        return out
    }

    private fun key(dayIndex: Long): String = "mood_$dayIndex"

    private companion object {
        const val NAME = "noxa_journal"
    }
}