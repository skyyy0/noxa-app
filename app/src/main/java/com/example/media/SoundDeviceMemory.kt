package com.example.media

import android.content.Context
import org.json.JSONObject

/** One remembered tuning for one physical output. */
data class SoundDeviceProfile(
    val preset: String,
    val intensity: Float,
    val loudness: Boolean,
    val limiter: Boolean,
    val trimDb: List<Float>
)

/**
 * **Memori per perangkat** (v3.7.5).
 *
 * A pair of earbuds and a car head unit do not want the same curve, yet an EQ
 * that forgets everything on disconnect forces the user to redo it every time
 * they switch. This store keeps one small tuning per output (identified by
 * route + product name), so plugging the same TWS back in restores exactly the
 * sound that was dialled in for it — including the manual band trims.
 */
class SoundDeviceMemory(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profileFor(key: String): SoundDeviceProfile? = runCatching {
        val root = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        val json = root.optJSONObject(key) ?: return null
        val trim = json.optJSONArray("trim") ?: return null
        SoundDeviceProfile(
            preset = json.optString("preset", SoundProfiles.AUTO),
            intensity = json.optDouble("intensity", 0.7).toFloat().coerceIn(0f, 1f),
            loudness = json.optBoolean("loudness", true),
            limiter = json.optBoolean("limiter", true),
            trimDb = (0 until trim.length()).map { trim.optDouble(it, 0.0).toFloat() }
        )
    }.getOrNull()

    fun save(key: String, profile: SoundDeviceProfile) {
        if (key.isBlank()) return
        runCatching {
            val root = JSONObject(prefs.getString(KEY, "{}") ?: "{}")
            root.put(
                key,
                JSONObject().apply {
                    put("preset", profile.preset)
                    put("intensity", profile.intensity.toDouble())
                    put("loudness", profile.loudness)
                    put("limiter", profile.limiter)
                    put("trim", org.json.JSONArray(profile.trimDb))
                    put("savedAt", System.currentTimeMillis())
                }
            )
            prefs.edit().putString(KEY, root.toString()).apply()
        }
    }

    private companion object {
        const val PREFS = "noxa_sound_devices"
        const val KEY = "profiles"
    }
}
