package com.example

import com.example.data.local.QuestEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** NOXA Quest: tiga misi mingguan dari angka rekap + streak heatmap. */
class QuestEngineTest {

    @Test
    fun `weekly always returns three missions with stable ids`() {
        val quests = QuestEngine.weekly(playsWeek = 0, topSongPlays = 0, currentStreak = 0L)
        assertEquals(listOf("plays", "repeat", "streak"), quests.map { it.id })
        assertEquals(QuestEngine.PLAYS_TARGET, quests[0].target)
        assertEquals(QuestEngine.REPEAT_TARGET, quests[1].target)
        assertEquals(QuestEngine.STREAK_TARGET, quests[2].target)
    }

    @Test
    fun `missions start incomplete with zero progress`() {
        val quests = QuestEngine.weekly(0, 0, 0L)
        assertTrue(quests.none { it.completed })
        assertTrue(quests.all { it.progress == 0 })
        assertTrue(quests.all { it.fraction == 0f })
    }

    @Test
    fun `reaching each target marks the mission complete`() {
        val quests = QuestEngine.weekly(
            playsWeek = QuestEngine.PLAYS_TARGET,
            topSongPlays = QuestEngine.REPEAT_TARGET,
            currentStreak = QuestEngine.STREAK_TARGET.toLong()
        )
        assertTrue(quests.all { it.completed })
        assertTrue(quests.all { it.fraction == 1f })
    }

    @Test
    fun `progress overshoots honestly instead of clamping`() {
        // Streak 10 hari tetap menampilkan 10/3 — angka jujur di UI.
        val quests = QuestEngine.weekly(playsWeek = 45, topSongPlays = 9, currentStreak = 10L)
        assertEquals(45, quests[0].progress)
        assertEquals(10, quests[2].progress)
        assertTrue(quests[2].completed)
    }

    @Test
    fun `fraction is clamped so a progress bar can never overflow`() {
        val quests = QuestEngine.weekly(playsWeek = 10_000, topSongPlays = 0, currentStreak = -4L)
        assertEquals(1f, quests[0].fraction)
        // Negatif (data korup) tidak membuat fraction di luar 0..1.
        assertEquals(0f, quests[2].fraction)
        assertEquals(0, quests[2].progress)
        assertFalse(quests[1].completed)
    }
}
