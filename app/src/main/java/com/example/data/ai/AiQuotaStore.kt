package com.example.data.ai

import android.content.SharedPreferences

/**
 * Penghitung kuota NOXA AI per perangkat.
 *
 * Kuota dihitung dari *jendela bergulir*: setiap pesan dicatat dengan
 * timestamp, dan yang dihitung hanya pesan di dalam jendela model tersebut
 * (1 jam untuk Haiku & Fable, 2 jam untuk flagship). Jadi jatah tidak "reset
 * tengah malam", ia benar-benar kembali satu pesan setiap satu pesan lama
 * keluar dari jendela — persis seperti yang dijanjikan di UI.
 *
 * Model flagship berbagi satu ember (`bucket = "flagship"`), sehingga memakai
 * Opus 4.6 lalu Gemini 3.1 Pro memotong jatah yang sama. Fable 5.1 punya ember
 * sendiri yang lebih ketat, Haiku punya embernya sendiri.
 *
 * Akun developer ([isDeveloper]) tidak pernah dibatasi: [AiQuotaState.unlimited].
 */
internal class AiQuotaStore(private val prefs: SharedPreferences) {

    /** Kuota untuk [model] sekarang, tanpa mencatat pemakaian. */
    fun state(spec: AiQuotaSpec?, isDeveloper: Boolean): AiQuotaState {
        if (isDeveloper) return AiQuotaState(0, 0, 0L, unlimited = true)
        if (spec == null) return AiQuotaState(0, 0, 0L, unlimited = false)
        val stamps = liveStamps(spec)
        return AiQuotaState(
            used = stamps.size,
            limit = spec.limit,
            resetAtMs = stamps.firstOrNull()?.plus(spec.windowMs) ?: 0L,
            unlimited = false
        )
    }

    /** Mencatat satu pemakaian untuk model ini (dipanggil hanya saat sukses). */
    fun consume(spec: AiQuotaSpec?) {
        if (spec == null) return
        val now = System.currentTimeMillis()
        val stamps = liveStamps(spec) + now
        prefs.edit().putString(key(spec.bucket), stamps.joinToString(",")).apply()
    }

    /** Membuang seluruh catatan — dipakai Dev Panel untuk reset manual. */
    fun resetAll() {
        prefs.edit()
            .remove(key("flagship"))
            .remove(key("fable"))
            .remove(key("haiku"))
            .apply()
    }

    private fun liveStamps(spec: AiQuotaSpec): List<Long> {
        val now = System.currentTimeMillis()
        val cutoff = now - spec.windowMs
        return prefs.getString(key(spec.bucket), null)
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toLongOrNull() }
            .filter { it > cutoff }
            .sorted()
    }

    private fun key(bucket: String) = "quota_$bucket"
}
