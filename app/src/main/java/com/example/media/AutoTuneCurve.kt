package com.example.media

import kotlin.math.roundToInt

/**
 * The musical intent behind NOXA's automatic wireless profile (v3.2.1).
 *
 * When a TWS / Bluetooth output connects, NOXA tunes itself — the user never
 * opens a single slider. The *shape* is fixed (a gentle smile: sub lift, a
 * shallow mid dip so vocals stay clear, a little air on top), but the
 * *numbers* are always scaled to what the connected hardware actually
 * exposes: its band count, its center frequencies and its own level range.
 * A 5-band chip headset and a 10-band DAC get the same character, and no
 * device is ever handed a value outside its legal range.
 *
 * Pure Kotlin on purpose: the maths is unit-tested without an audio device.
 */
object AutoTuneCurve {

    /** BassBoost strength (0..1000, the platform's own scale) — if supported. */
    const val BASS_BOOST_STRENGTH = 300

    /** Virtualizer strength (0..1000) — if supported. */
    const val VIRTUALIZER_STRENGTH = 250

    /**
     * Target gain per band as a fraction of the range *above* zero (for
     * lifts) or *below* zero (for dips). Keyed by center frequency so any
     * band count maps onto the same character.
     */
    private fun fractionFor(centerFreqHz: Int): Float = when {
        centerFreqHz < 120 -> 0.32f    // sub: warmth without mud
        centerFreqHz < 400 -> 0.16f    // bass body
        centerFreqHz < 1200 -> -0.05f  // shallow mid dip — vocals stay in front
        centerFreqHz < 4000 -> 0.04f   // presence, almost flat
        else -> 0.26f                  // air / treble lift
    }

    /**
     * The gains to request for [centerFreqsHz], each already clamped inside
     * `minLevelMb..maxLevelMb`, so hardware never sees an illegal value.
     *
     * @param minLevelMb lower bound from `Equalizer.bandLevelRange` (mB, ≤ 0)
     * @param maxLevelMb upper bound from `Equalizer.bandLevelRange` (mB, ≥ 0)
     */
    fun smileGains(centerFreqsHz: List<Int>, minLevelMb: Int, maxLevelMb: Int): List<Int> {
        val lo = minOf(minLevelMb, maxLevelMb)
        val hi = maxOf(minLevelMb, maxLevelMb)
        return centerFreqsHz.map { freq ->
            val fraction = fractionFor(freq)
            // Lifts scale with the headroom above zero, dips with the range
            // below it — an asymmetric range still comes out musical.
            val magnitude = if (fraction >= 0f) hi else -lo
            (fraction * magnitude).roundToInt().coerceIn(lo, hi)
        }
    }
}
