package com.example.media

/**
 * The frequency skeleton every NOXA sound profile is written on.
 *
 * Six bands is deliberate: enough to shape bass, body, presence and air
 * separately, few enough that each slider has an audible job. The first and
 * last bands sit at the edges of what a phone speaker or a TWS driver can
 * actually reproduce, so they double as "don't ask for more than physics
 * allows" limits.
 */
object SoundProfiles {

    /** Low → high, in Hz. Kept in one place so the UI and the DSP agree. */
    val BANDS = intArrayOf(60, 200, 700, 2500, 8000, 16000)

    const val AUTO = "Otomatis"
    const val NEUTRAL = "Netral"

    /** Profile order in the UI: transparent first, then colour. */
    val PRESETS = listOf(AUTO, NEUTRAL, "Hangat", "Vokal", "Bass", "Detail")

    /**
     * Gain offsets in dB for [preset] on [route].
     *
     * The automatic profile is the only route-dependent one: it compensates for
     * what each output physically does instead of applying the same smile curve
     * everywhere (the old behaviour, which is why Bluetooth sounded muddy and
     * the phone speaker just distorted).
     */
    fun curve(preset: String, route: SoundRoute): FloatArray = when (preset) {
        AUTO -> when (route) {
            // A phone speaker cannot produce 60 Hz; boosting it only eats
            // headroom and makes the whole mix rattle. Trim the sub, lift the
            // presence region where speech and vocals live.
            SoundRoute.SPEAKER -> floatArrayOf(-4f, -1f, 0f, 1.5f, 1f, -0.5f)
            // A2DP usually arrives bass-heavy and slightly dull; pull the sub
            // back, keep a little warmth, add air for clarity.
            SoundRoute.BLUETOOTH -> floatArrayOf(1.5f, 1f, 0f, 1f, 1.5f, 1f)
            // Wired and USB carry what the file has; stay out of the way.
            SoundRoute.WIRED, SoundRoute.USB -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
            SoundRoute.UNKNOWN -> floatArrayOf(0f, 0f, 0f, 0.5f, 0.5f, 0f)
        }

        NEUTRAL -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
        "Hangat" -> floatArrayOf(2.5f, 1.5f, 0f, -0.5f, -1f, -1.5f)
        "Vokal" -> floatArrayOf(-1f, -0.5f, 1f, 2.5f, 1.5f, 0f)
        "Bass" -> floatArrayOf(6f, 4f, 1f, 0f, 0.5f, 0f)
        "Detail" -> floatArrayOf(-1f, -1f, 0f, 1f, 3f, 4f)
        else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f)
    }

    /**
     * Headroom the chain needs so a boosted band cannot clip before the
     * limiter sees it. Always negative: this is the preamp cut, not a gain.
     */
    fun headroomDb(gains: FloatArray, loudnessOn: Boolean): Float {
        val peak = gains.maxOrNull() ?: 0f
        val loudnessLift = if (loudnessOn) 2f else 0f
        return (-(peak.coerceAtLeast(0f) + loudnessLift + 0.5f)).coerceIn(-12f, 0f)
    }

    /** Label shown next to the device in the Studio screen. */
    fun label(route: SoundRoute): String = route.label

    /** One honest sentence about what the route profile is doing. */
    fun note(route: SoundRoute): String = route.note
}

/**
 * What the engine is currently connected to.
 *
 * These are the four paths that actually behave differently; SCO (a call) is
 * treated as Bluetooth so a headset never jumps back to a phone-speaker curve
 * mid-conversation.
 */
enum class SoundRoute(val label: String, val note: String) {
    SPEAKER(
        "Speaker HP",
        "Sub dipangkas supaya tidak pecah, vokal dan presence dinaikkan"
    ),
    WIRED(
        "Headphone kabel",
        "Respons netral — tuning bawaan headphone dibiarkan bekerja"
    ),
    BLUETOOTH(
        "Bluetooth",
        "Kompensasi bass A2DP, presence dinaikkan, limiter menjaga agar tidak pecah"
    ),
    USB(
        "USB / DAC",
        "Flat dan transparan untuk DAC eksternal"
    ),
    UNKNOWN(
        "Output audio",
        "Profil aman diterapkan sampai output dikenali"
    )
}
