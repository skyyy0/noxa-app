package com.example.media

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import android.util.Log

data class EqualizerBand(
    val index: Int,
    val centerFreqHz: Int,
    val minLevelMilliBels: Int,
    val maxLevelMilliBels: Int,
    val currentLevelMilliBels: Int
)

data class EqualizerState(
    val isEnabled: Boolean = true,
    val currentPreset: String = "Normal",
    val presets: List<String> = listOf(
        EqualizerManager.AUTO_PRESET,
        "Flat",
        "Bass Boost",
        "Vocal Booster",
        "Electronic",
        "Rock",
        "Pop",
        "Classical"
    ),
    val bands: List<EqualizerBand> = emptyList(),
    /** 0..1000, mirroring the platform BassBoost scale. */
    val bassBoostStrength: Int = 0,
    /** 0..1000, mirroring the platform Virtualizer scale. */
    val virtualizerStrength: Int = 0,
    val supportsBassBoost: Boolean = false,
    val supportsVirtualizer: Boolean = false
)

/** What the automatic wireless profile actually managed to apply. */
data class AutoTuneResult(
    /** Bands written with the smile curve (the hardware's own band count). */
    val bandsApplied: Int,
    /** Strength written to BassBoost, or null when the hardware lacks it. */
    val bassBoostApplied: Int?,
    /** Strength written to Virtualizer, or null when the hardware lacks it. */
    val virtualizerApplied: Int?
)

class EqualizerManager {

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    var currentState: EqualizerState = EqualizerState()
        private set

    /** The session the live effect instances are bound to (0 = none yet). */
    private var boundSessionId: Int = 0

    /**
     * Levels the app last asked for. ExoPlayer builds a fresh audio session
     * whenever its sink restarts — notably the moment a wireless headset
     * connects — and new effect instances start flat, so these are replayed
     * on every (re)bind instead of letting the user's tuning vanish quietly.
     */
    private val requestedBandLevels = HashMap<Int, Int>()
    private var requestedEnabled: Boolean? = null

    /** Set when auto-tune was requested before the session existed. */
    private var autoTunePending = false

    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == boundSessionId && equalizer != null) {
            // Same session as before: releasing and rebuilding the effects
            // here would silently wipe the band levels the user just set
            // (this used to run on every song).
            readCurrentBands()
            return
        }
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()

            val eq = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            equalizer = eq
            boundSessionId = audioSessionId

            // BassBoost and Virtualizer are separate platform effects and are not
            // available on every device, so each is probed independently.
            val bb = try {
                BassBoost(0, audioSessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w("EqualizerManager", "BassBoost unavailable: ${e.message}")
                null
            }
            bassBoost = bb

            val vc = try {
                Virtualizer(0, audioSessionId).apply { enabled = true }
            } catch (e: Exception) {
                Log.w("EqualizerManager", "Virtualizer unavailable: ${e.message}")
                null
            }
            virtualizer = vc

            // `getStrengthSupported()` is the platform's own answer for whether the
            // effect can do anything on this device; a constructor succeeding is
            // not enough.
            val bassSupported = bb != null && runCatching { bb.getStrengthSupported() }.getOrDefault(false)
            val virtualSupported = vc != null && runCatching { vc.getStrengthSupported() }.getOrDefault(false)

            currentState = currentState.copy(
                supportsBassBoost = bassSupported,
                supportsVirtualizer = virtualSupported,
                bassBoostStrength = if (bassSupported) {
                    runCatching { bb!!.getRoundedStrength().toInt() }.getOrDefault(0)
                } else {
                    0
                },
                virtualizerStrength = if (virtualSupported) {
                    runCatching { vc!!.getRoundedStrength().toInt() }.getOrDefault(0)
                } else {
                    0
                }
            )

            // Replay the tuning from the previous session so a sink restart
            // (new song, headset connected) keeps the sound the user chose.
            requestedEnabled?.let { wantEnabled ->
                runCatching { eq.enabled = wantEnabled }
            }
            val levelRange = runCatching { eq.bandLevelRange }.getOrNull()
            if (levelRange != null && requestedBandLevels.isNotEmpty()) {
                val lo = levelRange[0].toInt()
                val hi = levelRange[1].toInt()
                requestedBandLevels.forEach { (band, level) ->
                    runCatching {
                        eq.setBandLevel(band.toShort(), level.coerceIn(lo, hi).toShort())
                    }
                }
            }

            readCurrentBands()
            // Auto-tune asked for before playback started: apply it the moment
            // a real session exists, with no second prompt for the user.
            if (autoTunePending) {
                autoTunePending = false
                runCatching { applyAutoTuneNow() }
            }
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Failed to initialize Equalizer with session ID: $audioSessionId", e)
        }
    }

    fun setBassBoost(strength: Int) {
        val bb = bassBoost ?: return
        val clamped = strength.coerceIn(0, 1000)
        try {
            bb.setStrength(clamped.toShort())
            currentState = currentState.copy(bassBoostStrength = clamped)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting bass boost", e)
        }
    }

    fun setVirtualizer(strength: Int) {
        val vc = virtualizer ?: return
        val clamped = strength.coerceIn(0, 1000)
        try {
            vc.setStrength(clamped.toShort())
            currentState = currentState.copy(virtualizerStrength = clamped)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting virtualizer", e)
        }
    }

    private fun readCurrentBands() {
        val eq = equalizer ?: return
        try {
            val numBands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()

            val bandList = mutableListOf<EqualizerBand>()
            for (i in 0 until numBands) {
                val centerFreq = eq.getCenterFreq(i.toShort()) / 1000 // Convert mHz to Hz
                val level = eq.getBandLevel(i.toShort()).toInt()
                bandList.add(
                    EqualizerBand(
                        index = i,
                        centerFreqHz = centerFreq,
                        minLevelMilliBels = minLevel,
                        maxLevelMilliBels = maxLevel,
                        currentLevelMilliBels = level
                    )
                )
            }
            currentState = currentState.copy(bands = bandList)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error reading bands", e)
        }
    }

    fun setBandLevel(bandIndex: Int, levelMilliBels: Int) {
        val eq = equalizer ?: return
        try {
            eq.setBandLevel(bandIndex.toShort(), levelMilliBels.toShort())
            requestedBandLevels[bandIndex] = levelMilliBels
            val updatedBands = currentState.bands.map {
                if (it.index == bandIndex) it.copy(currentLevelMilliBels = levelMilliBels) else it
            }
            currentState = currentState.copy(bands = updatedBands, currentPreset = "Custom")
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting band level", e)
        }
    }

    fun applyPreset(presetName: String) {
        val eq = equalizer ?: return
        try {
            when (presetName) {
                "Flat" -> {
                    for (i in 0 until (eq.numberOfBands)) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
                "Bass Boost" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, (max * 0.8).toInt().toShort())
                    if (eq.numberOfBands > 1) eq.setBandLevel(1, (max * 0.4).toInt().toShort())
                }
                "Vocal Booster" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, (max * 0.6).toInt().toShort())
                    if (eq.numberOfBands > 3) eq.setBandLevel(3, (max * 0.5).toInt().toShort())
                }
                "Electronic" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, (max * 0.6).toInt().toShort())
                    if (eq.numberOfBands > 4) eq.setBandLevel(4, (max * 0.7).toInt().toShort())
                }
                "Rock" -> {
                    val range = eq.bandLevelRange
                    val max = range[1].toInt()
                    if (eq.numberOfBands > 0) eq.setBandLevel(0, (max * 0.7).toInt().toShort())
                    if (eq.numberOfBands > 2) eq.setBandLevel(2, (-max * 0.2).toInt().toShort())
                    if (eq.numberOfBands > 4) eq.setBandLevel(4, (max * 0.8).toInt().toShort())
                }
                "NOXA Auto" -> {
                    // The wireless profile itself — same maths, re-applied on
                    // demand from the preset row.
                    applyAutoTuneNow()
                }
                else -> {
                    for (i in 0 until (eq.numberOfBands)) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
            }
            // Remember exactly what the preset put on the hardware, so a later
            // session change replays it instead of dropping back to flat.
            for (i in 0 until eq.numberOfBands.toInt()) {
                runCatching { requestedBandLevels[i] = eq.getBandLevel(i.toShort()).toInt() }
            }
            readCurrentBands()
            currentState = currentState.copy(currentPreset = presetName)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error applying preset: $presetName", e)
        }
    }

    fun setEnabled(enabled: Boolean) {
        try {
            equalizer?.enabled = enabled
            requestedEnabled = enabled
            currentState = currentState.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting enabled state", e)
        }
    }

    /**
     * Applies NOXA's automatic wireless profile — a smile curve scaled to this
     * hardware's own band count and range, plus bass boost / spatializer where
     * the device reports support (see [AutoTuneCurve]).
     *
     * Returns null when the audio session does not exist yet (nothing has
     * been played since launch). The request is remembered and lands the
     * moment the session appears, so the caller can honestly say "aktif saat
     * musik diputar" instead of pretending.
     */
    fun applyAutoTune(): AutoTuneResult? {
        if (equalizer == null) {
            autoTunePending = true
            return null
        }
        return applyAutoTuneNow()
    }

    private fun applyAutoTuneNow(): AutoTuneResult? {
        val eq = equalizer ?: return null
        return try {
            val range = eq.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val freqs = (0 until eq.numberOfBands.toInt()).map { i ->
                // getCenterFreq returns milliHz.
                eq.getCenterFreq(i.toShort()).toInt() / 1000
            }
            AutoTuneCurve.smileGains(freqs, min, max).forEachIndexed { i, gain ->
                eq.setBandLevel(i.toShort(), gain.toShort())
                requestedBandLevels[i] = gain
            }
            val bass = if (currentState.supportsBassBoost) {
                setBassBoost(AutoTuneCurve.BASS_BOOST_STRENGTH)
                AutoTuneCurve.BASS_BOOST_STRENGTH
            } else {
                null
            }
            val virtual = if (currentState.supportsVirtualizer) {
                setVirtualizer(AutoTuneCurve.VIRTUALIZER_STRENGTH)
                AutoTuneCurve.VIRTUALIZER_STRENGTH
            } else {
                null
            }
            readCurrentBands()
            currentState = currentState.copy(currentPreset = AUTO_PRESET)
            AutoTuneResult(
                bandsApplied = freqs.size,
                bassBoostApplied = bass,
                virtualizerApplied = virtual
            )
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Auto-tune failed", e)
            null
        }
    }

    fun release() {
        try {
            equalizer?.release()
            bassBoost?.release()
            virtualizer?.release()
            equalizer = null
            bassBoost = null
            virtualizer = null
            boundSessionId = 0
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error releasing equalizer", e)
        }
    }

    companion object {
        /** Preset chip label for the automatic wireless profile. */
        const val AUTO_PRESET = "NOXA Auto"
    }
}
