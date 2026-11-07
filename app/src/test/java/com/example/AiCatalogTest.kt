package com.example

import com.example.data.ai.AiFlavor
import com.example.data.ai.AiModel
import com.example.data.ai.AiQuotaState
import com.example.data.ai.AiTier
import com.example.data.ai.NoxaAiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v3.6.0 model catalogue: which tier a model belongs to, how big its
 * allowance is, and which prompt addendum a flagship mode injects.
 *
 * These are the promises shown in the picker, so they are pinned here — a typo
 * in a window or limit would silently over- or under-deliver messages.
 */
class AiCatalogTest {

    @Test
    fun `catalogue contains every model promised to the user`() {
        val ids = AiModel.entries.map { it.id }
        listOf(
            "glm-5.3-flash",
            "deepseek-v4.1-flash",
            "mimo-v2.6-flash",
            "claude-haiku-5.5",
            "claude-opus-4.6",
            "claude-opus-5",
            "claude-fable-5-1",
            "gemini-3.1-pro",
            "gpt-6-astra"
        ).forEach { assertTrue("missing $it", ids.contains(it)) }
    }

    @Test
    fun `free models have no quota and never use the flagship key`() {
        AiModel.entries.filter { it.tier == AiTier.FREE }.forEach { model ->
            assertNull("${model.id} should be unmetered", model.quota)
            assertFalse(model.usesFlagshipKey)
        }
    }

    @Test
    fun `haiku is six messages per hour with its own bucket`() {
        val quota = AiModel.HAIKU.quota!!
        assertEquals(6, quota.limit)
        assertEquals(60 * 60 * 1000L, quota.windowMs)
        assertEquals("haiku", quota.bucket)
    }

    @Test
    fun `flagship models share six messages per two hours`() {
        listOf(AiModel.OPUS46, AiModel.OPUS5, AiModel.GEMINI, AiModel.GPT).forEach { model ->
            val quota = model.quota!!
            assertEquals(6, quota.limit)
            assertEquals(2 * 60 * 60 * 1000L, quota.windowMs)
            assertEquals("flagship", quota.bucket)
            assertTrue(model.usesFlagshipKey)
        }
    }

    @Test
    fun `fable has the tightest allowance`() {
        val quota = AiModel.FABLE.quota!!
        assertEquals(3, quota.limit)
        assertEquals(60 * 60 * 1000L, quota.windowMs)
        assertEquals("fable", quota.bucket)
    }

    @Test
    fun `flagship modes are closed to free models`() {
        assertEquals(3, AiFlavor.forModel(AiModel.OPUS5).size)
        assertTrue(AiFlavor.forModel(AiModel.GLM).isEmpty())
        assertTrue(AiFlavor.forModel(AiModel.MIMO).isEmpty())
    }

    @Test
    fun `each flagship mode reaches the system prompt`() {
        AiFlavor.entries.forEach { flavor ->
            val prompt = NoxaAiRepository.systemPrompt(null, flavor)
            assertTrue(
                "${flavor.name} addendum missing",
                prompt.contains("MODE KHUSUS FLAGSHIP")
            )
            assertTrue(prompt.contains(flavor.addendum.take(40)))
        }
    }

    @Test
    fun `exhausted quota only reports remaining messages when metered`() {
        assertEquals(4, AiQuotaState(used = 2, limit = 6, resetAtMs = 0L, unlimited = false).remaining)
        assertTrue(AiQuotaState(used = 6, limit = 6, resetAtMs = 0L, unlimited = false).exhausted)
        assertFalse(AiQuotaState(used = 99, limit = 6, resetAtMs = 0L, unlimited = true).exhausted)
    }
}
