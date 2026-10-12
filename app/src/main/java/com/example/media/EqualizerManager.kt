package com.example.media

import android.media.audiofx.Equalizer
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
    val presets: List<String> = listOf("Flat", "Bass Boost", "Vocal Booster", "Electronic", "Rock", "Pop", "Classical"),
    val bands: List<EqualizerBand> = emptyList()
)

class EqualizerManager {

    private var equalizer: Equalizer? = null
    var currentState: EqualizerState = EqualizerState()
        private set

    fun init(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        try {
            equalizer?.release()
            val eq = Equalizer(0, audioSessionId).apply {
                enabled = true
            }
            equalizer = eq
            readCurrentBands()
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Failed to initialize Equalizer with session ID: $audioSessionId", e)
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
                else -> {
                    for (i in 0 until (eq.numberOfBands)) {
                        eq.setBandLevel(i.toShort(), 0)
                    }
                }
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
            currentState = currentState.copy(isEnabled = enabled)
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error setting enabled state", e)
        }
    }

    fun release() {
        try {
            equalizer?.release()
            equalizer = null
        } catch (e: Exception) {
            Log.e("EqualizerManager", "Error releasing equalizer", e)
        }
    }
}
