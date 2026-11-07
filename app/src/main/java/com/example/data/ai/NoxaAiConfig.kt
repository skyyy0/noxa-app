package com.example.data.ai

import android.util.Base64
import com.example.data.remote.Secrets

/**
 * **Kredensial NOXA AI (v3.6.0)** — freemodel.in.
 *
 * Ada dua kelas key:
 *
 *  * **Reguler** — dipakai model gratis (GLM, DeepSeek, MiMo). Sama seperti
 *    sebelumnya: potongan base64 yang urutannya diacak supaya `strings`/MT
 *    Manager tidak menemukan `fm_...` utuh di APK.
 *  * **Flagship** — key khusus model premium (Claude/Gemini/GPT). Dua key:
 *    utama dan cadangan. Kode mencoba utama dulu, lalu cadangan, supaya satu
 *    key yang kehabisan kredit tidak mematikan seluruh keluarga model flagship.
 *
 * Build-time `.env` tetap bisa menimpa semuanya lewat Secrets Gradle Plugin,
 * jadi key bisa dirotasi tanpa mengubah kode.
 */
internal object NoxaAiConfig {

    private const val BASE = "https://freemodel.in/v1"

    val chatUrl: String get() = "$BASE/chat/completions"

    val modelsUrl: String get() = "$BASE/models"

    /** Key bawaan untuk model gratis, base64 dan dipecah tiga. */
    private val FREE_CHUNKS = listOf(
        "0NGMzZTA4YjllNzNhOTRjZj", // potongan tengah
        "dkYzc1YjgzMmIxNGQ0MzE5", // potongan akhir
        "Zm1fZDJmZjEzNTA0YWQ0Mjk" // potongan awal
    )

    /**
     * Key flagship utama (v3.6.0), dipecah tiga seperti key gratis.
     * Key lama `fm_8825…` ditolak provider (ditolak/kehabisan kredit), jadi
     * v3.6.0 memakai key baru ini; cadangan tetap di bawah.
     */
    private val FLAGSHIP_CHUNKS = listOf(
        "Zm1fNzUxNzA1NmZhZTYyMD", // awal
        "IyNDY0Zjc2YzBhNDg5MGJi", // tengah
        "OTVmNTQxYmVkMTVkZDBkMmM2" // akhir
    )

    /** Key flagship cadangan — dipakai otomatis saat key utama habis/ditolak. */
    private val BACKUP_CHUNKS = listOf(
        "RlZWY3YzcwOWJhZDk5YzM2", // tengah
        "NGMzODk0ZDBiZWNjMmY2NzFi", // akhir
        "Zm1fN2Q4N2MxMzhhMGY0NG" // awal
    )

    /** Key reguler (model gratis). */
    val apiKey: String by lazy {
        val fromEnv = Secrets.get("FREEMODEL_API_KEY").trim()
        if (fromEnv.isNotBlank()) fromEnv else assemble(FREE_CHUNKS, listOf(2, 0, 1))
    }

    /**
     * Semua key yang boleh dipakai model flagship, **berurutan**: utama lalu
     * cadangan. Nilai kosong dibuang, jadi kalau satu key ditarik dari build,
     * sisanya tetap jalan tanpa perubahan kode.
     */
    val flagshipKeys: List<String> by lazy {
        val primary = Secrets.get("FREEMODEL_FLAGSHIP_KEY").trim()
            .ifBlank { assemble(FLAGSHIP_CHUNKS, listOf(0, 1, 2)) }
        val backup = Secrets.get("FREEMODEL_FLAGSHIP_BACKUP_KEY").trim()
            .ifBlank { assemble(BACKUP_CHUNKS, listOf(2, 0, 1)) }
        listOf(primary, backup).filter { it.isNotBlank() }.distinct()
    }

    private fun assemble(chunks: List<String>, order: List<Int>): String {
        val encoded = order.joinToString("") { chunks[it] }
        return runCatching {
            String(Base64.decode(encoded, Base64.NO_WRAP))
        }.getOrDefault("")
    }
}

/** Kelas model: gratis tanpa batas, atau flagship berkuota. */
enum class AiTier(val label: String) {
    FREE("Gratis"),
    FLAGSHIP("Flagship")
}

/**
 * Batas pemakaian satu keluarga model.
 *
 * [limit] pesan per [windowMs] milidetik, dihitung dari perangkat ini.
 * Model flagship berbagi satu kuota (satu "jatah flagship" untuk semuanya),
 * kecuali model yang punya [AiModel.quotaBucket] sendiri — Claude Fable 5.1
 * sengaja lebih ketat karena paling istimewa.
 */
data class AiQuotaSpec(
    val limit: Int,
    val windowMs: Long,
    val bucket: String
)

/**
 * Model yang boleh dipilih pengguna. Label + keterangan tampil di pemilih
 * model; [id] adalah nama model di API.
 *
 * Urutan enum = urutan tampil di pemilih: model gratis dulu, lalu flagship.
 */
enum class AiModel(
    val id: String,
    val label: String,
    val blurb: String,
    val tier: AiTier,
    val quota: AiQuotaSpec? = null
) {
    GLM(
        "glm-5.3-flash",
        "GLM 5.3 Flash",
        "Cepat, seimbang, tanpa batas pesan",
        AiTier.FREE
    ),
    DEEPSEEK(
        "deepseek-v4.1-flash",
        "DeepSeek V4.1",
        "Kuat menyusun playlist panjang",
        AiTier.FREE
    ),
    MIMO(
        "mimo-v2.6-flash",
        "MiMo V2.6 Flash",
        "Ringan dan bebas dipakai — tanpa kuota",
        AiTier.FREE
    ),
    HAIKU(
        "claude-haiku-5.5",
        "Claude Haiku 5.5",
        "Balasan cepat dan rapi, 6 pesan per jam",
        AiTier.FLAGSHIP,
        AiQuotaSpec(limit = 6, windowMs = 60 * 60 * 1000L, bucket = "haiku")
    ),
    // Catatan: `gpt-6-luna` tidak ada di daftar model resmi freemodel.in
    // (dicek 7 Nov 2026); keluarga GPT-6 yang tersedia adalah `gpt-6-astra`.
    GPT(
        "gpt-6-astra",
        "GPT-6 Astra",
        "Penalaran paling dalam untuk permintaan rumit",
        AiTier.FLAGSHIP,
        AiQuotaSpec(limit = 6, windowMs = 2 * 60 * 60 * 1000L, bucket = "flagship")
    ),
    OPUS46(
        "claude-opus-4.6",
        "Claude Opus 4.6",
        "Penulisan lirik dan narasi paling halus",
        AiTier.FLAGSHIP,
        AiQuotaSpec(limit = 6, windowMs = 2 * 60 * 60 * 1000L, bucket = "flagship")
    ),
    OPUS5(
        "claude-opus-5",
        "Claude Opus 5",
        "Model flagship terkuat untuk analisis musik",
        AiTier.FLAGSHIP,
        AiQuotaSpec(limit = 6, windowMs = 2 * 60 * 60 * 1000L, bucket = "flagship")
    ),
    GEMINI(
        "gemini-3.1-pro",
        "Gemini 3.1 Pro",
        "Cepat memahami konteks panjang dan suasana",
        AiTier.FLAGSHIP,
        AiQuotaSpec(limit = 6, windowMs = 2 * 60 * 60 * 1000L, bucket = "flagship")
    ),
    FABLE(
        "claude-fable-5-1",
        "Claude Fable 5.1",
        "Edisi paling istimewa — 3 pesan per jam",
        AiTier.FLAGSHIP,
        AiQuotaSpec(limit = 3, windowMs = 60 * 60 * 1000L, bucket = "fable")
    );

    /** True bila model ini memakai key flagship (bukan key reguler). */
    val usesFlagshipKey: Boolean get() = tier == AiTier.FLAGSHIP

    companion object {
        val DEFAULT = GLM

        fun fromId(id: String?): AiModel =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
    }
}

/**
 * Tiga kemampuan khusus yang hanya terbuka untuk model flagship.
 *
 * Ini yang membuat langganan flagship terasa berbeda: model gratis tetap bisa
 * mengobrol dan memutar lagu, sedangkan flagship mendapat tiga mode kerja yang
 * lebih dalam — menulis lirik, membedah lagu/album, dan menyusun setlist
 * panjang dengan alur energi.
 *
 * [addendum] ditempel ke system prompt, jadi tidak ada request tambahan.
 */
enum class AiFlavor(
    val label: String,
    val hint: String,
    val icon: String,
    val addendum: String
) {
    TULIS_LIRIK(
        "Tulis Lirik",
        "Draf lirik orisinal, lengkap dengan bait dan reff",
        "✍",
        """
MODE KHUSUS FLAGSHIP — TULIS LIRIK.
Pengguna meminta lirik. Bila ada blok REFERENSI WEB di bawah, lirik di situ adalah data nyata dari internet: pakai untuk memastikan judul, artis, tema, dan susunan bait benar. Jangan pernah mengarang lirik lalu menyebutnya lirik resmi lagu yang sudah ada. Kutip paling banyak empat baris referensi; sisanya jelaskan maknanya per bagian (bait, reff, bridge) dan sebutkan sumbernya. Bila pengguna ingin lirik BARU atau tidak ada referensi, tulis lirik orisinal dengan struktur jelas: [Verse 1], [Chorus], [Verse 2], [Bridge], [Chorus] dan beri tahu bahwa itu karya baru. Bahasa mengikuti permintaan pengguna. Boleh sebutkan genre dan tempo di baris pertama. JANGAN memakai tag pemutar musik apa pun. Setelah lirik, beri dua kalimat tentang suasana dan saran aransemen.""".trimIndent()
    ),
    BEDAH_LAGU(
        "Bedah Lagu",
        "Analisis lagu atau album: suasana, aransemen, kemiripan",
        "🔍",
        """
MODE KHUSUS FLAGSHIP — BEDAH LAGU.
Jelaskan lagu/album yang ditanyakan secara mendalam: suasana dan progresi emosinya, ciri aransemen, kekuatan lirik, posisi dalam karier artisnya, lalu 3 lagu lain yang mirip dan alasannya. Pakai paragraf pendek yang rapi, maksimal 180 kata. Boleh menutup dengan satu tag [NOXA_QUEUE|Artis - Judul;Artis - Judul] berisi rekomendasi kemiripan itu.""".trimIndent()
    ),
    SETLIST(
        "Setlist",
        "Setlist panjang dengan alur energi dari hangat ke puncak",
        "🎚",
        """
MODE KHUSUS FLAGSHIP — SETLIST.
Susun setlist 18 sampai 24 lagu dengan alur energi yang disengaja: pembuka hangat, naik bertahap, puncak di dua pertiga, lalu penutup yang menenangkan. Wajib diakhiri tag [NOXA_PLAYLIST|Nama Setlist|Artis - Judul;...] berisi SEMUA lagu dengan format "Artis - Judul". Setiap lagu harus benar-benar ada di katalog nyata dan cocok dengan tema yang diminta. Sebelum tag, jelaskan alurnya dalam 3 kalimat.""".trimIndent()
    );

    companion object {
        /** Mode flagship yang boleh dipakai [model]. */
        fun forModel(model: AiModel): List<AiFlavor> =
            if (model.tier == AiTier.FLAGSHIP) entries.toList() else emptyList()
    }
}

/** Status satu model: tersedia, atau sedang maintenance menurut API. */
data class AiModelStatus(
    val model: AiModel,
    val available: Boolean,
    /** Alasan singkat saat tidak tersedia, mis. "maintenance terjadwal". */
    val note: String? = null
)

/** Kuota satu keluarga model untuk perangkat ini. */
data class AiQuotaState(
    /** Berapa model ini memakai jatahnya sendiri (0 saat unlimited). */
    val used: Int,
    /** Batasnya; 0 berarti tanpa batas. */
    val limit: Int,
    /** Saat jatah mulai terisi lagi (epoch ms); 0 saat unlimited. */
    val resetAtMs: Long,
    /** True untuk akun developer — semua batas dilewati. */
    val unlimited: Boolean
) {
    val remaining: Int get() = if (unlimited || limit == 0) Int.MAX_VALUE else (limit - used).coerceAtLeast(0)
    val exhausted: Boolean get() = !unlimited && limit > 0 && remaining == 0
}
