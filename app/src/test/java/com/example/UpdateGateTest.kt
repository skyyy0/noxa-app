package com.example

import com.example.data.remote.UpdateGate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The popup rule Sky asked for: when an update is pushed, ONLY installations
 * below that version may see the popup — a user already on the latest version
 * must never be bothered again.
 */
class UpdateGateTest {

    private val current = 17L

    @Test
    fun `user below pushed version gets the pop-up`() {
        assertTrue(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = 18L,
                isDeveloper = false,
                skippedVersionCode = -1L,
                mandatory = true
            )
        )
    }

    @Test
    fun `user already on the pushed version never sees the pop-up`() {
        assertFalse(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = current,
                isDeveloper = false,
                skippedVersionCode = -1L,
                mandatory = true
            )
        )
        assertFalse(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = current,
                isDeveloper = false,
                skippedVersionCode = -1L,
                mandatory = false
            )
        )
    }

    @Test
    fun `user past the pushed version never sees the pop-up`() {
        assertFalse(
            UpdateGate.shouldShow(
                currentVersionCode = 19L,
                publishedVersionCode = 18L,
                isDeveloper = false,
                skippedVersionCode = -1L,
                mandatory = true
            )
        )
    }

    @Test
    fun `developer never sees the pop-up even when behind`() {
        assertFalse(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = 18L,
                isDeveloper = true,
                skippedVersionCode = -1L,
                mandatory = true
            )
        )
    }

    @Test
    fun `optional update stays dismissed once per version`() {
        // Never dismissed → shown.
        assertTrue(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = 18L,
                isDeveloper = false,
                skippedVersionCode = -1L,
                mandatory = false
            )
        )
        // "Nanti Saja" pressed for exactly this version → hidden.
        assertFalse(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = 18L,
                isDeveloper = false,
                skippedVersionCode = 18L,
                mandatory = false
            )
        )
        // A NEWER push invalidates the old dismissal.
        assertTrue(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = 19L,
                isDeveloper = false,
                skippedVersionCode = 18L,
                mandatory = false
            )
        )
    }

    @Test
    fun `mandatory update ignores a previous dismissal`() {
        assertTrue(
            UpdateGate.shouldShow(
                currentVersionCode = current,
                publishedVersionCode = 18L,
                isDeveloper = false,
                skippedVersionCode = 18L,
                mandatory = true
            )
        )
    }

    @Test
    fun `isNewer only for strictly higher published code`() {
        assertTrue(UpdateGate.isNewer(17L, 18L))
        assertFalse(UpdateGate.isNewer(17L, 17L))
        assertFalse(UpdateGate.isNewer(17L, 16L))
    }
}
