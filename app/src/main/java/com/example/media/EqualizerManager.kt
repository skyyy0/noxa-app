package com.example.media

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import kotlin.math.roundToInt

/** One band as the Studio UI sees it (levels are hundredths of a dB). */
data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMilliBels: Int,
    val maxLevelMilliBels: Int,
    val currentLevelMilliBels: Int
)

/**
 * The full picture of NOXA's sound engine: what it is running on, which output
 * it tuned itself for, and how much headroom it reserved.
 */
data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPreset: String = SoundProfiles.AUTO,
    val presets: List<String> = SoundProfiles.PRESETS,
    val bands: List<EqualizerBand> = emptyList(),
    /** 0..1000 compatibility value; the DSP path maps it onto low-band trim. */
    val bassBoostStrength: Int = 0,
    /** 0..1000 compatibility value, only meaningful on the legacy backend. */
    val virtualizerStrength: Int = 0,
    val supportsBassBoost: Boolean = false,
    val supportsVirtualizer: Boolean = false,
    /** "DSP 6-band" when DynamicsProcessing is running, otherwise the fallback. */
    val engineName: String = "Menunggu sesi audio",
    val dspActive: Boolean = false,
    val routeLabel: String = SoundRoute.UNKNOWN.label,
    val routeNote: String = SoundRoute.UNKNOWN.note,
    val headroomDb: Float = 0f,
    val intensity: Float = 0.7f,
    val loudnessEnabled: Boolean = true,
    val limiterEnabled: Boolean = true
)

/** What the automatic tuning actually managed to apply. */
data class AutoTuneResult(
    val bandsApplied: Int,
    val dspActive: Boolean,
    val headroomDb: Float,
    val routeLabel: String,
    val preset: String
)

/**
 * NOXA's sound engine (v3.7.0).
 *
 * ── Why this is not "an equalizer" ────────────────────────────────────────
 * A bare [Equalizer] writes gains straight into the hardware stage. Boost one
 * band on a phone speaker and the mix clips *before* anyone can hear the
 * shape; that distortion is why "no EQ" sounded better than the old presets.
 *
 * This engine runs a **chain**, not a curve:
 *
 * ```
 *   input gain (headroom) → 6-band post EQ → 3-band compressor → limiter
 * ```
 *
 *  * **Headroom** is computed from the loudest boosted band (plus the
 *    compressor's own lift), so a +6 dB bass shelf can never clip the DAC.
 *  * **The compressor** is what makes quiet tracks audible in noisy places and
 *    keeps the loud ones from jumping out — the "loudness" switch.
 *  * **The limiter** catches transients the compressor's attack lets through.
 *  * **The curve is route-aware.** Speaker, wired, Bluetooth and USB get
 *    different automatic shapes ([SoundProfiles.curve]), because a TWS bud and
 *    a phone speaker do not need the same correction.
 *
 * On API < 28 (or when a device rejects DynamicsProcessing) the same headroom
 * math is applied to the platform [Equalizer] plus a [LoudnessEnhancer], so the
 * result degrades to "safe and flat" instead of "loud and broken".
 */
class EqualizerManager(private val context: Context? = null) {

    private var dsp: DynamicsProcessing? = null
    private var legacyEq: Equalizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null

    private var boundSessionId: Int = 0
    private var backend: Backend = Backend.NONE

    private var route: SoundRoute = SoundRoute.UNKNOWN
    private var presetName: String = SoundProfiles.AUTO
    private var intensity: Float = 0.7f
    private var loudnessEnabled: Boolean = true
    private var limiterEnabled: Boolean = true
    private var enabled: Boolean = true

    /** Per-band user offset in dB, applied after the (scaled) preset curve. */
    private val trimDb = FloatArray(SoundProfiles.BANDS.size)
    private var legacyBass: Int = 0
    private var legacyVirtual: Int = 0
    private var autoTunePending = false

    var currentState: EqualizerState = EqualizerState()
        private set

    private enum class Backend { NONE, DSP, LEGACY }

    // ---------------------------------------------------------------- lifecycle

    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == boundSessionId && backend != Backend.NONE) {
            // Same sink: re-applying is cheap and keeps a route change honest.
            refreshRoute()
            return
        }
        releaseEffects()
        boundSessionId = audioSessionId
        route = detectRoute()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            backend = if (buildDsp(audioSessionId)) Backend.DSP else Backend.LEGACY
        } else {
            backend = Backend.LEGACY
        }
        if (backend == Backend.LEGACY) buildLegacy(audioSessionId)

        apply()
        if (autoTunePending) {
            autoTunePending = false
            applyPreset(SoundProfiles.AUTO)
        }
    }

    private fun buildDsp(sessionId: Int): Boolean = runCatching {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,   // stereo
            true, 2,  // pre-EQ: tone tilt
            true, 3,  // multiband compressor
            true, SoundProfiles.BANDS.size,
            true      // limiter
        ).build()
        val effect = DynamicsProcessing(0, sessionId, config)
        effect.enabled = enabled
        dsp = effect
        true
    }.getOrElse { error ->
        Log.w(TAG, "DynamicsProcessing unavailable, using the guarded fallback: ${error.message}")
        false
    }

    private fun buildLegacy(sessionId: Int): Boolean = runCatching {
        val eq = Equalizer(0, sessionId).apply { enabled = enabled }
        legacyEq = eq
        loudness = runCatching { LoudnessEnhancer(sessionId) }.getOrNull()
            ?.apply { enabled = enabled }
        virtualizer = runCatching { Virtualizer(0, sessionId) }.getOrNull()
            ?.apply { enabled = enabled }
        true
    }.getOrElse { error ->
        Log.w(TAG, "Legacy equalizer unavailable: ${error.message}")
        false
    }

    /** Re-detects the physical output — call when headphones come or go. */
    fun refreshRoute() {
        val detected = detectRoute()
        if (detected != route) {
            // A new route means a different correction, not a louder one; the
            // per-band user trim is deliberately kept so a manual bass cut
            // does not silently come back when a headset reconnects.
            route = detected
        }
        apply()
    }

    private fun detectRoute(): SoundRoute {
        val manager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return route
        val devices = runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull().orEmpty()

        fun has(vararg types: Int) = devices.any { device ->
            device.isSink && types.contains(device.type)
        }
        return when {
            has(
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER
            ) -> SoundRoute.BLUETOOTH

            has(AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE) ->
                SoundRoute.USB

            has(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET) ->
                SoundRoute.WIRED

            else -> SoundRoute.SPEAKER
        }
    }

    // ------------------------------------------------------------------ controls

    fun applyPreset(presetName: String) {
        this.presetName = presetName
        // A new profile resets manual trims: the user asked to hear *this*
        // shape, not this shape plus whatever was dragged before.
        trimDb.fill(0f)
        apply()
    }

    /** Absolute band gain from the UI, in hundredths of a dB. */
    fun setBandLevel(bandIndex: Int, levelMilliBels: Int) {
        if (bandIndex !in SoundProfiles.BANDS.indices) return
        val target = (levelMilliBels / 100f).coerceIn(-12f, 12f)
        trimDb[bandIndex] = target - scaledCurve()[bandIndex]
        apply()
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        apply()
    }

    fun setLoudnessEnabled(value: Boolean) {
        loudnessEnabled = value
        apply()
    }

    fun setLimiterEnabled(value: Boolean) {
        limiterEnabled = value
        apply()
    }

    fun setEnabled(isEnabled: Boolean) {
        enabled = isEnabled
        runCatching { dsp?.enabled = isEnabled }
        runCatching { legacyEq?.enabled = isEnabled }
        runCatching { loudness?.enabled = isEnabled && loudnessEnabled }
        runCatching { virtualizer?.enabled = isEnabled }
        currentState = currentState.copy(isEnabled = isEnabled)
    }

    /** Compatibility hook for older callers / the AI equalizer command. */
    fun setBassBoost(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        legacyBass = clamped
        trimDb[0] = (clamped / 1000f) * 6f
        apply()
    }

    /** Spatializer is hardware-only; on the DSP path it is reported honestly. */
    fun setVirtualizer(strength: Int) {
        val clamped = strength.coerceIn(0, 1000)
        legacyVirtual = clamped
        runCatching { virtualizer?.setStrength(clamped.toShort()) }
        currentState = currentState.copy(virtualizerStrength = clamped)
    }

    /**
     * Applies the automatic profile for the current output. Null means there is
     * no audio session yet; the request lands as soon as playback starts.
     */
    fun applyAutoTune(): AutoTuneResult? {
        if (backend == Backend.NONE) {
            autoTunePending = true
            return null
        }
        applyPreset(SoundProfiles.AUTO)
        val state = currentState
        return AutoTuneResult(
            bandsApplied = state.bands.size,
            dspActive = state.dspActive,
            headroomDb = state.headroomDb,
            routeLabel = state.routeLabel,
            preset = state.currentPreset
        )
    }

    // ------------------------------------------------------------------- engine

    /** Preset curve scaled by intensity — the part trims are added on top of. */
    private fun scaledCurve(): FloatArray =
        SoundProfiles.curve(presetName, route).map { it * intensity }.toFloatArray()

    private fun effectiveGains(): FloatArray {
        val scaled = scaledCurve()
        return FloatArray(scaled.size) { index ->
            (scaled[index] + trimDb[index]).coerceIn(-12f, 12f)
        }
    }

    private fun apply() {
        val gains = effectiveGains()
        val headroom = SoundProfiles.headroomDb(gains, loudnessEnabled)
        when (backend) {
            Backend.DSP -> applyDsp(gains, headroom)
            Backend.LEGACY -> applyLegacy(gains)
            Backend.NONE -> Unit
        }
        publishState(gains, headroom)
    }

    private fun applyDsp(gains: FloatArray, headroom: Float) {
        val effect = dsp ?: return
        runCatching {
            effect.setEnabled(enabled)
            effect.setInputGainAllChannelsTo(headroom)

            // pre-EQ: a gentle tilt only. The heavy lifting belongs to the
            // post-EQ, where the six real bands live.
            effect.setPreEqBandAllChannelsTo(
                0,
                DynamicsProcessing.EqBand(true, 120f, if (gains[0] > 3f) -1f else 0f)
            )
            effect.setPreEqBandAllChannelsTo(
                1,
                DynamicsProcessing.EqBand(true, 8000f, if (gains[4] + gains[5] > 5f) -1f else 0f)
            )

            gains.forEachIndexed { index, gain ->
                effect.setPostEqBandAllChannelsTo(
                    index,
                    DynamicsProcessing.EqBand(true, SoundProfiles.BANDS[index].toFloat(), gain)
                )
            }

            // Compressor: slow enough to keep punch, fast enough to hold a
            // chorus together. Its +2 dB make-up is already inside `headroom`.
            val mbc = DynamicsProcessing.Mbc(true, loudnessEnabled, 3)
            val postGain = if (loudnessEnabled) 2f else 0f
            mbc.setBand(
                0,
                DynamicsProcessing.MbcBand(
                    true, 200f, 15f, 250f, 2f, -20f, 6f, -60f, 1f, 0f, postGain
                )
            )
            mbc.setBand(
                1,
                DynamicsProcessing.MbcBand(
                    true, 2000f, 12f, 220f, 1.8f, -22f, 6f, -60f, 1f, 0f, postGain
                )
            )
            mbc.setBand(
                2,
                DynamicsProcessing.MbcBand(
                    true, 8000f, 10f, 180f, 1.6f, -24f, 6f, -60f, 1f, 0f, postGain
                )
            )
            effect.setMbcAllChannelsTo(mbc)

            effect.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    true,
                    limiterEnabled,
                    0,
                    1f,
                    70f,
                    10f,
                    -1.5f,
                    0f
                )
            )
        }.onFailure { error ->
            Log.w(TAG, "DSP apply failed, falling back for this session: ${error.message}")
            // The effect object is unusable — degrade to the hardware path so
            // music keeps playing with a safe, flat curve.
            runCatching { dsp?.release() }
            dsp = null
            backend = if (boundSessionId > 0 && buildLegacy(boundSessionId)) {
                Backend.LEGACY
            } else {
                Backend.NONE
            }
            applyLegacy(gains)
        }
    }

    private fun applyLegacy(gains: FloatArray) {
        val eq = legacyEq ?: return
        // A hardware EQ has no preamp. Shift the whole curve down so its
        // loudest band is 0 dB — the same anti-clipping rule as the DSP path,
        // just enforced by the curve instead of by an input gain.
        val shift = -(gains.maxOrNull() ?: 0f).coerceAtLeast(0f)
        runCatching {
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            for (band in 0 until eq.numberOfBands.toInt()) {
                val freq = eq.getCenterFreq(band.toShort()) / 1000
                val db = interpolate(gains, freq) + shift
                eq.setBandLevel(band.toShort(), (db * 100).roundToInt().coerceIn(min, max).toShort())
            }
            eq.enabled = enabled
            loudness?.let { enhancer ->
                enhancer.setTargetGain(if (loudnessEnabled) 250 else 0)
                enhancer.enabled = enabled && loudnessEnabled
            }
        }.onFailure { Log.w(TAG, "Legacy apply failed: ${it.message}") }
    }

    /** Linear interpolation of the 6-band curve at an arbitrary frequency. */
    private fun interpolate(gains: FloatArray, frequencyHz: Int): Float {
        val freqs = SoundProfiles.BANDS
        if (frequencyHz <= freqs.first()) return gains.first()
        if (frequencyHz >= freqs.last()) return gains.last()
        for (i in 0 until freqs.size - 1) {
            if (frequencyHz in freqs[i]..freqs[i + 1]) {
                val span = (freqs[i + 1] - freqs[i]).toFloat()
                val t = (frequencyHz - freqs[i]) / span
                return gains[i] + (gains[i + 1] - gains[i]) * t
            }
        }
        return 0f
    }

    private fun publishState(gains: FloatArray, headroom: Float) {
        val bands = SoundProfiles.BANDS.mapIndexed { index, freq ->
            EqualizerBand(
                index = index,
                centerFreqHz = freq,
                minLevelMilliBels = -1200,
                maxLevelMilliBels = 1200,
                currentLevelMilliBels = (gains[index] * 100).roundToInt()
            )
        }
        currentState = EqualizerState(
            isEnabled = enabled,
            currentPreset = presetName,
            presets = SoundProfiles.PRESETS,
            bands = bands,
            bassBoostStrength = legacyBass,
            virtualizerStrength = legacyVirtual,
            supportsBassBoost = true,
            supportsVirtualizer = virtualizer != null,
            engineName = when (backend) {
                Backend.DSP -> "NOXA DSP · 6 band + kompresor"
                Backend.LEGACY -> "Hardware EQ · mode aman"
                Backend.NONE -> "Menunggu sesi audio"
            },
            dspActive = backend == Backend.DSP,
            routeLabel = route.label,
            routeNote = route.note,
            headroomDb = headroom,
            intensity = intensity,
            loudnessEnabled = loudnessEnabled,
            limiterEnabled = limiterEnabled
        )
    }

    fun release() {
        releaseEffects()
        boundSessionId = 0
        backend = Backend.NONE
    }

    private fun releaseEffects() {
        runCatching { dsp?.release() }
        runCatching { legacyEq?.release() }
        runCatching { loudness?.release() }
        runCatching { virtualizer?.release() }
        dsp = null
        legacyEq = null
        loudness = null
        virtualizer = null
    }

    companion object {
        private const val TAG = "NoxaSoundEngine"

        /** Preset chip label for the automatic, route-aware profile. */
        const val AUTO_PRESET = SoundProfiles.AUTO
    }
}
