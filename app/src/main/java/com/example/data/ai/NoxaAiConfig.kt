package com.example.data.ai

import android.util.Base64
import com.example.data.remote.Secrets

/**
 * **Kredensial NOXA AI (v3.5.5)** — freemodel.in.
 *
 * API key disimpan dalam bentuk terpecah + ter-encode, bukan sebagai string
 * literal utuh, supaya `strings`/MT Manager tidak menemukan `fm_...` langsung
 * di APK. R8 full mode menambah obfuscation; ini lapisan keduanya, bukan satu-
 * satunya. Kalau build-time `.env` menyediakan `FREEMODEL_API_KEY` (lewat
 * Secrets Gradle Plugin), nilai itu yang dipakai — jadi key bisa dirotasi tanpa
 * mengubah kode.
 */
internal object NoxaAiConfig {

    private const val BASE = "https://freemodel.in/v1"

    val chatUrl: String get() = "$BASE/chat/completions"

    val modelsUrl: String get() = "$BASE/models"

    /**
     * Key bawaan aplikasi, base64 dan dipecah tiga. Potongan disimpan dalam
     * urutan acak supaya pencarian string mentah tidak menemukan key utuh.
     */
    private val CHUNKS = listOf(
        "0NGMzZTA4YjllNzNhOTRjZj", // potongan tengah
        "dkYzc1YjgzMmIxNGQ0MzE5", // potongan akhir
        "Zm1fZDJmZjEzNTA0YWQ0Mjk" // potongan awal
    )

    /** Key yang benar-benar dipakai untuk request. */
    val apiKey: String by lazy {
        val fromEnv = Secrets.get("FREEMODEL_API_KEY").trim()
        if (fromEnv.isNotBlank()) fromEnv else assemble()
    }

    private fun assemble(): String {
        val order = listOf(2, 0, 1)
        val encoded = order.joinToString("") { CHUNKS[it] }
        return runCatching {
            String(Base64.decode(encoded, Base64.NO_WRAP))
        }.getOrDefault("")
    }
}

/**
 * Model yang boleh dipilih pengguna. Label + keterangan tampil di pemilih
 * model; [id] adalah nama model di API.
 */
enum class AiModel(val id: String, val label: String, val blurb: String) {
    GLM("glm-5.3-flash", "GLM 5.3 Flash", "Cepat, seimbang, paling hemat kuota"),
    // Catatan: `gpt-6-luna` tidak ada di daftar model resmi freemodel.in
    // (dicek 6 Nov 2026); yang tersedia dari keluarga GPT-6 adalah
    // `gpt-6-astra`. Memakai id yang tidak ada akan membuat model ini selalu
    // tampil "maintenance", jadi slot GPT memakai model yang benar-benar ada.
    GPT("gpt-6-astra", "GPT-6 Astra", "Paling pintar untuk permintaan rumit"),
    DEEPSEEK("deepseek-v4.1-flash", "DeepSeek V4.1", "Kuat untuk playlist panjang");

    companion object {
        val DEFAULT = GLM

        fun fromId(id: String?): AiModel =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

/** Status satu model: tersedia, atau sedang maintenance menurut /v1/models. */
data class AiModelStatus(
    val model: AiModel,
    val available: Boolean
)
