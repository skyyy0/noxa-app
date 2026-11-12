package com.example

import com.example.media.SoundProfiles
import com.example.media.SoundRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine's math, tested without an audio device: these are the rules that
 * keep a boosted profile from clipping, and the ones a refactor must not
 * quietly break.
 */
class SoundProfilesTest {

    @Test
    fun `every preset has one gain per band`() {
        SoundProfiles.PRESETS.forEach { preset ->
            assertEquals(
                "Preset $preset must fill all ${SoundProfiles.BANDS.size} bands",
                SoundProfiles.BANDS.size,
                SoundProfiles.curve(preset, SoundRoute.SPEAKER).size
            )
        }
    }

    @Test
    fun `automatic profile depends on the output`() {
        val speaker = SoundProfiles.curve(SoundProfiles.AUTO, SoundRoute.SPEAKER)
        val bluetooth = SoundProfiles.curve(SoundProfiles.AUTO, SoundRoute.BLUETOOTH)
        val wired = SoundProfiles.curve(SoundProfiles.AUTO, SoundRoute.WIRED)

        // The phone speaker cannot reproduce 60 Hz: its sub band must be cut,
        // not boosted like a generic smile curve would.
        assertTrue("Speaker sub-bass should be trimmed", speaker[0] < 0f)
        // Bluetooth needs compensation, wired must stay out of the way.
        assertTrue("Bluetooth should add some warmth", bluetooth[0] > 0f)
        assertTrue(wired.all { it == 0f })
    }

    @Test
    fun `neutral preset is truly flat`() {
        assertTrue(SoundProfiles.curve("Netral", SoundRoute.BLUETOOTH).all { it == 0f })
    }

    @Test
    fun `headroom is always a cut when a band is boosted`() {
        val bass = SoundProfiles.curve("Bass", SoundRoute.SPEAKER)
        val headroom = SoundProfiles.headroomDb(bass, loudnessOn = true)
        assertTrue("Headroom must be negative", headroom < 0f)
        // It has to cover the loudest boost plus the compressor's make-up gain.
        assertTrue(headroom <= -bass.max())
    }

    @Test
    fun `headroom with loudness off leaves more room`() {
        val detail = SoundProfiles.curve("Detail", SoundRoute.WIRED)
        val withLoudness = SoundProfiles.headroomDb(detail, loudnessOn = true)
        val withoutLoudness = SoundProfiles.headroomDb(detail, loudnessOn = false)
        assertTrue(withoutLoudness > withLoudness)
    }

    @Test
    fun `headroom never cuts more than twelve dB`() {
        val extreme = FloatArray(SoundProfiles.BANDS.size) { 12f }
        assertEquals(-12f, SoundProfiles.headroomDb(extreme, loudnessOn = true), 0.001f)
    }
}
