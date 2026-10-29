package com.example.data.local

/**
 * **NOXA Quest** (v3.3.3) — misi mingguan yang dihitung murni dari data
 * dengar yang sudah ada di perangkat (rekap 7 hari + streak heatmap).
 *
 * Tidak ada server dan tidak ada tombol klaim: misi selesai begitu angkanya
 * penuh, dan angkanya sendiri diperbarui otomatis oleh data yang sama dengan
 * yang dipakai kartu Rekap & Peta Panas. Pure Kotlin tanpa dependensi Android
 * supaya seluruh aturannya bisa diuji di JVM.
 */
data class Quest(
    val id: String,
    val title: String,
    val hint: String,
    val progress: Int,
    val target: Int
) {
    val completed: Boolean get() = target > 0 && progress >= target

    /** Progress 0f..1f untuk bar — selalu terbatas, aman dipanggil saat render. */
    val fraction: Float
        get() = if (target <= 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
}

object QuestEngine {

    /** Minggu berjalan: semua misi memakai jendela 7 hari yang sama rekap. */
    const val PLAYS_TARGET = 30
    const val REPEAT_TARGET = 5
    const val STREAK_TARGET = 3

    /**
     * Tiga misi minggu ini dari angka mentah rekap + heatmap:
     *
     *  1. **Putar 30 lagu** — jumlah play 7 hari terakhir.
     *  2. **Ulangi lagu favorit 5×** — play judul paling sering minggu ini.
     *  3. **Streak 3 hari** — hari beruntun dari peta panas.
     *
     * Progress boleh melewati target (mis. streak 10 hari tetap menampilkan
     * 10/3 — angka jujur, tidak di-clamp disembunyikan).
     */
    fun weekly(playsWeek: Int, topSongPlays: Int, currentStreak: Long): List<Quest> = listOf(
        Quest(
            id = "plays",
            title = "Putar $PLAYS_TARGET lagu",
            hint = "Rekap 7 hari terakhir",
            progress = playsWeek.coerceAtLeast(0),
            target = PLAYS_TARGET
        ),
        Quest(
            id = "repeat",
            title = "Ulangi lagu favorit ${REPEAT_TARGET}×",
            hint = "Lagu paling sering minggu ini",
            progress = topSongPlays.coerceAtLeast(0),
            target = REPEAT_TARGET
        ),
        Quest(
            id = "streak",
            title = "Streak $STREAK_TARGET hari",
            hint = "Dengar tiap hari tanpa putus",
            progress = currentStreak.coerceAtLeast(0L).toInt(),
            target = STREAK_TARGET
        )
    )
}