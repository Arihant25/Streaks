package com.arihant.streaks.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreakCalculatorTest {

    private fun streak(
        frequency: FrequencyType = FrequencyType.DAILY,
        frequencyCount: Int = 1,
        createdDate: String = "2025-01-01",
        completions: List<String> = emptyList(),
        frequencyHistory: List<FrequencyChange> = emptyList()
    ) = Streak(
        id = "test",
        name = "Test",
        emoji = "🔥",
        frequency = frequency,
        frequencyCount = frequencyCount,
        createdDate = createdDate,
        lastCompletedDate = null,
        completions = completions,
        frequencyHistory = frequencyHistory
    )

    // ── Daily ─────────────────────────────────────────────────────────────────

    @Test
    fun `daily chain counts consecutive days`() {
        val completions = listOf("2025-06-08", "2025-06-09", "2025-06-10")
        val result = StreakCalculator.recalculate(
            streak(completions = completions), completions,
            weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.bestStreak)
        assertTrue(result.isCompletedToday)
    }

    @Test
    fun `daily gap resets current but keeps best`() {
        val completions = listOf(
            "2025-06-01", "2025-06-02", "2025-06-03", "2025-06-04", // best = 4
            "2025-06-09", "2025-06-10"
        )
        val result = StreakCalculator.recalculate(
            streak(completions = completions), completions,
            weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(2, result.currentStreak)
        assertEquals(4, result.bestStreak)
    }

    @Test
    fun `daily streak stays live during grace period (yesterday done, today not yet)`() {
        val completions = listOf("2025-06-08", "2025-06-09")
        val result = StreakCalculator.recalculate(
            streak(completions = completions), completions,
            weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(2, result.currentStreak)
        assertFalse(result.isCompletedToday)
    }

    @Test
    fun `daily streak dies after a full missed day`() {
        val completions = listOf("2025-06-07", "2025-06-08")
        val result = StreakCalculator.recalculate(
            streak(completions = completions), completions,
            weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(0, result.currentStreak)
        assertEquals(2, result.bestStreak)
    }

    @Test
    fun `empty completions produce zeroed streak`() {
        val result = StreakCalculator.recalculate(
            streak(), emptyList(), weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.bestStreak)
        assertEquals(null, result.lastCompletedDate)
    }

    @Test
    fun `duplicate completion dates are normalized`() {
        val completions = listOf("2025-06-10", "2025-06-10", "2025-06-09")
        val result = StreakCalculator.recalculate(
            streak(completions = completions), completions,
            weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(listOf("2025-06-09", "2025-06-10"), result.completions)
        assertEquals(2, result.currentStreak)
    }

    // ── Weekly ────────────────────────────────────────────────────────────────

    @Test
    fun `weekly 3x counts weeks meeting the quota`() {
        // Monday-start weeks: Jun 2-8, Jun 9-15 (2025). Both have 3 completions.
        val completions = listOf(
            "2025-06-02", "2025-06-04", "2025-06-06",
            "2025-06-09", "2025-06-11", "2025-06-13"
        )
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.WEEKLY, frequencyCount = 3, completions = completions),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-14")
        )
        assertEquals(2, result.currentStreak)
        assertEquals(2, result.bestStreak)
    }

    @Test
    fun `weekly quota not met does not count the week`() {
        val completions = listOf(
            "2025-06-02", "2025-06-04", "2025-06-06", // week 1: 3 ✓
            "2025-06-09", "2025-06-11"                 // week 2: only 2 ✗
        )
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.WEEKLY, frequencyCount = 3, completions = completions),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-14")
        )
        // Week 1 is the previous period → still inside the grace window
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.bestStreak)
    }

    @Test
    fun `week start setting changes period boundaries`() {
        // 2025-06-08 is a Sunday. Sunday-start: Jun 8 begins a new week containing Jun 9.
        // Monday-start: Jun 8 ends the week of Jun 2; Jun 9 begins the next.
        val completions = listOf("2025-06-08", "2025-06-09")
        val base = streak(
            frequency = FrequencyType.WEEKLY, frequencyCount = 2, completions = completions
        )
        val sundayStart = StreakCalculator.recalculate(
            base, completions, weekStartsMonday = false, today = LocalDate.parse("2025-06-09")
        )
        val mondayStart = StreakCalculator.recalculate(
            base, completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-09")
        )
        assertEquals(1, sundayStart.currentStreak) // both fall into the same Sunday-start week
        assertEquals(0, mondayStart.currentStreak) // split across two Monday-start weeks
    }

    // ── Monthly / yearly ──────────────────────────────────────────────────────

    @Test
    fun `monthly chain spans months`() {
        val completions = listOf("2025-04-15", "2025-05-20", "2025-06-01")
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.MONTHLY, frequencyCount = 1, completions = completions),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(3, result.currentStreak)
    }

    // ── Frequency history ─────────────────────────────────────────────────────

    @Test
    fun `frequency change is evaluated era by era`() {
        // Daily era through June 7, weekly(1) era from June 9 (Monday-start week).
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.DAILY, 1),
            FrequencyChange("2025-06-09", FrequencyType.WEEKLY, 1)
        )
        val completions = listOf("2025-06-06", "2025-06-07", "2025-06-08", "2025-06-11")
        val result = StreakCalculator.recalculate(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                completions = completions, frequencyHistory = history
            ),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-11")
        )
        // Jun 6, 7, 8 are daily periods; Jun 8 (Sunday) ends exactly where the weekly era
        // beginning Jun 9 starts, so the chain is 3 daily + 1 weekly = 4.
        assertEquals(4, result.currentStreak)
        assertEquals(4, result.bestStreak)
    }

    @Test
    fun `weekly to daily mid-week bridges via a valid prorated fragment`() {
        // Weekly(3) era, switched to daily on Wed 2025-06-11. The cut-short week
        // [Mon Jun 9, Wed Jun 11) has a prorated quota of ceil(3*2/7) = 1.
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.WEEKLY, 3),
            FrequencyChange("2025-06-11", FrequencyType.DAILY, 1)
        )
        val completions = listOf(
            "2025-06-02", "2025-06-04", "2025-06-06", // full week: 3/3 ✓
            "2025-06-09",                             // fragment: 1/1 ✓
            "2025-06-11", "2025-06-12"                // daily era
        )
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.DAILY, frequencyCount = 1,
                    completions = completions, frequencyHistory = history),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
        )
        assertEquals(4, result.currentStreak) // week + fragment + 2 days
        assertEquals(4, result.bestStreak)
    }

    @Test
    fun `empty cut-short period is neutral - bridges without counting`() {
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.WEEKLY, 3),
            FrequencyChange("2025-06-11", FrequencyType.DAILY, 1)
        )
        val completions = listOf(
            "2025-06-02", "2025-06-04", "2025-06-06", // full week ✓
            // nothing on Jun 9-10 — the fragment is empty but must not break the chain
            "2025-06-11", "2025-06-12"
        )
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.DAILY, frequencyCount = 1,
                    completions = completions, frequencyHistory = history),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
        )
        assertEquals(3, result.currentStreak) // week + 2 days, fragment bridged
        assertEquals(3, result.bestStreak)
    }

    @Test
    fun `under-quota fragment is neutral - bridges without counting`() {
        // Weekly(5) era → daily on Thu Jun 12. Fragment [Jun 9, Jun 12) quota = ceil(15/7) = 3,
        // only 2 done → neutral: not a unit, but not a break either.
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.WEEKLY, 5),
            FrequencyChange("2025-06-12", FrequencyType.DAILY, 1)
        )
        val completions = listOf(
            "2025-06-02", "2025-06-03", "2025-06-04", "2025-06-05", "2025-06-06", // 5/5 ✓
            "2025-06-09", "2025-06-10",                                            // 2/3 fragment
            "2025-06-12"
        )
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.DAILY, frequencyCount = 1,
                    completions = completions, frequencyHistory = history),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
        )
        assertEquals(2, result.currentStreak) // full week + today, fragment bridged
    }

    @Test
    fun `switch to yearly preserves accumulated weekly history`() {
        // The old math backdated the yearly era to Jan 1, collapsing months of weekly
        // streak into a single period. Now the era starts on the change day.
        val history = listOf(
            FrequencyChange("2025-01-06", FrequencyType.WEEKLY, 1),
            FrequencyChange("2025-06-11", FrequencyType.YEARLY, 100)
        )
        val completions = listOf("2025-05-12", "2025-05-19", "2025-05-26", "2025-06-02")
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.YEARLY, frequencyCount = 100,
                    completions = completions, frequencyHistory = history),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
        )
        assertEquals(4, result.currentStreak) // four weekly units survive the switch
        assertEquals(4, result.bestStreak)
    }

    @Test
    fun `fully missed periods still break the chain across a change`() {
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.DAILY, 1),
            FrequencyChange("2025-06-11", FrequencyType.WEEKLY, 1)
        )
        // Jun 9 and Jun 10 are fully missed daily periods — an honest break.
        val completions = listOf("2025-06-07", "2025-06-08", "2025-06-11")
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                    completions = completions, frequencyHistory = history),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
        )
        assertEquals(1, result.currentStreak)
        assertEquals(2, result.bestStreak)
    }

    @Test
    fun `duplicate history dates - the last entry wins`() {
        val history = listOf(
            FrequencyChange("2025-06-09", FrequencyType.WEEKLY, 5),
            FrequencyChange("2025-06-09", FrequencyType.WEEKLY, 1) // re-changed the same day
        )
        val completions = listOf("2025-06-09")
        val result = StreakCalculator.recalculate(
            streak(frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                    completions = completions, frequencyHistory = history),
            completions, weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
        )
        assertEquals(1, result.currentStreak) // judged by count=1, not the shadowed count=5
    }

    // ── Current period satisfaction (used to silence reminders) ──────────────

    @Test
    fun `current period satisfied for completed daily habit`() {
        val s = streak(completions = listOf("2025-06-10"))
        assertTrue(
            StreakCalculator.isCurrentPeriodSatisfied(
                s, weekStartsMonday = true, today = LocalDate.parse("2025-06-10")
            )
        )
        assertFalse(
            StreakCalculator.isCurrentPeriodSatisfied(
                s, weekStartsMonday = true, today = LocalDate.parse("2025-06-11")
            )
        )
    }

    @Test
    fun `current period satisfaction prorates a head fragment`() {
        // Switched to weekly(2) on Wed Jun 11: the first weekly period is the fragment
        // [Jun 11, Jun 16), 5 of 7 days → quota ceil(2*5/7) = 2.
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.DAILY, 1),
            FrequencyChange("2025-06-11", FrequencyType.WEEKLY, 2)
        )
        val base = streak(
            frequency = FrequencyType.WEEKLY, frequencyCount = 2,
            completions = listOf("2025-06-11"), frequencyHistory = history
        )
        assertFalse(
            StreakCalculator.isCurrentPeriodSatisfied(
                base, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
            )
        )
        val enough = base.copy(completions = listOf("2025-06-11", "2025-06-12"))
        assertTrue(
            StreakCalculator.isCurrentPeriodSatisfied(
                enough, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
            )
        )
    }

    @Test
    fun `current period satisfaction respects weekly quota`() {
        val s = streak(
            frequency = FrequencyType.WEEKLY, frequencyCount = 2,
            completions = listOf("2025-06-09", "2025-06-10")
        )
        assertTrue(
            StreakCalculator.isCurrentPeriodSatisfied(
                s, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
            )
        )
        val onlyOne = s.copy(completions = listOf("2025-06-09"))
        assertFalse(
            StreakCalculator.isCurrentPeriodSatisfied(
                onlyOne, weekStartsMonday = true, today = LocalDate.parse("2025-06-12")
            )
        )
    }
}
