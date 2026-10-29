package com.example

import com.example.media.AutoTuneCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic wireless profile's maths (v3.2.1). The curve is what makes a
 * freshly-connected TWS headset sound "already set up", so its shape and its
 * safety on odd hardware are both pinned down here.
 */
class AutoTuneCurveTest {

    @Test
    fun `five band smile curve has the intended shape`() {
        // Sub, bass body, mid dip, presence, air — on a standard ±1500 mB rig.
        val gains = AutoTuneCurve.smileGains(
            centerFreqsHz = listOf(60, 230, 910, 3600, 14000),
            minLevelMb = -1500,
            maxLevelMb = 1500
        )
        assertEquals(listOf(480, 240, -75, 60, 390), gains)
        // Sub is the biggest lift, mids dip below zero, treble rises again.
        assertTrue(gains[0] > gains[1])
        assertTrue(gains[2] < 0)
        assertTrue(gains[4] > 0)
    }

    @Test
    fun `curve follows frequencies, not band count`() {
        // A 3-band headset gets the same character, not a truncated curve.
        val three = AutoTuneCurve.smileGains(listOf(60, 1000, 10000), -1500, 1500)
        assertEquals(listOf(480, -75, 390), three)

        // A 10-band device gets every step of the same shape.
        val ten = AutoTuneCurve.smileGains(
            listOf(30, 60, 120, 400, 1000, 2500, 4000, 6000, 10000, 16000),
            -1500,
            1500
        )
        assertEquals(10, ten.size)
        assertEquals(480, ten[0])  // 30 Hz sits in the sub bucket
        assertEquals(240, ten[2])  // 120 Hz is the first bass-body band
        assertEquals(-75, ten[3])  // 400 Hz already belongs to the mid dip
        assertEquals(60, ten[5])   // 2.5 kHz presence
        assertEquals(390, ten[9])  // air
    }

    @Test
    fun `dips scale with the negative range of asymmetric hardware`() {
        // Some DACs offer -600..+1200 mB: lifts use the positive headroom,
        // the mid dip uses the negative range — never the wrong sign.
        val gains = AutoTuneCurve.smileGains(listOf(60, 910, 8000), -600, 1200)
        assertEquals(384, gains[0])  // 0.32 × 1200
        assertEquals(-30, gains[1])  // -0.05 × 600
        assertEquals(312, gains[2])  // 0.26 × 1200
    }

    @Test
    fun `every gain stays inside the legal hardware range`() {
        // Odd hardware: a shallow range and twelve bands spread across it.
        val min = -500
        val max = 250
        val gains = AutoTuneCurve.smileGains(
            listOf(20, 45, 80, 150, 300, 700, 1500, 3500, 5000, 9000, 14000, 18000),
            min,
            max
        )
        gains.forEach { gain ->
            assertTrue("gain $gain outside $min..$max", gain in min..max)
        }
        // The sub lift scales with this rig's positive headroom: 0.32 × 250.
        assertEquals(80, gains[0])
        // The mid dip scales with the negative side: -0.05 × 500.
        assertEquals(-25, gains[5])
        // And the air lift: 0.26 × 250.
        assertEquals(65, gains[11])
    }

    @Test
    fun `strength constants are on the platform scale`() {
        assertTrue(AutoTuneCurve.BASS_BOOST_STRENGTH in 0..1000)
        assertTrue(AutoTuneCurve.VIRTUALIZER_STRENGTH in 0..1000)
        // Modest on purpose: the curve does the shaping, these add body.
        assertTrue(AutoTuneCurve.BASS_BOOST_STRENGTH <= 500)
        assertTrue(AutoTuneCurve.VIRTUALIZER_STRENGTH <= 500)
    }
}
