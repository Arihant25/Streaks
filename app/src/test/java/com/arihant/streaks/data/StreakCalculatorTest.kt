package com.arihant.streaks.data

import java.time.DayOfWeek
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
        frequencyHistory: List<FrequencyChange> = emptyList(),
        isNegative: Boolean = false
    ) = Streak(
        id = "test",
        name = "Test",
        emoji = "🔥",
        frequency = frequency,
        frequencyCount = frequencyCount,
        createdDate = createdDate,
        lastCompletedDate = null,
        completions = completions,
        frequencyHistory = frequencyHistory,
        isNegative = isNegative
    )

    private fun recalc(
        s: Streak,
        firstDay: DayOfWeek = DayOfWeek.MONDAY,
        today: String
    ) = StreakCalculator.recalculate(s, s.completions, firstDay, LocalDate.parse(today))

    // ── Daily (positive) ──────────────────────────────────────────────────────

    @Test
    fun `daily chain counts consecutive days`() {
        val result = recalc(
            streak(completions = listOf("2025-06-08", "2025-06-09", "2025-06-10")),
            today = "2025-06-10"
        )
        assertEquals(3, result.currentStreak)
        assertEquals(3, result.bestStreak)
        assertTrue(result.isCompletedToday)
    }

    @Test
    fun `daily gap resets current but keeps best`() {
        val result = recalc(
            streak(completions = listOf(
                "2025-06-01", "2025-06-02", "2025-06-03", "2025-06-04", // best = 4
                "2025-06-09", "2025-06-10"
            )),
            today = "2025-06-10"
        )
        assertEquals(2, result.currentStreak)
        assertEquals(4, result.bestStreak)
    }

    @Test
    fun `daily streak stays live during grace period (yesterday done, today not yet)`() {
        val result = recalc(
            streak(completions = listOf("2025-06-08", "2025-06-09")),
            today = "2025-06-10"
        )
        assertEquals(2, result.currentStreak)
        assertFalse(result.isCompletedToday)
    }

    @Test
    fun `daily streak dies after a full missed day`() {
        val result = recalc(
            streak(completions = listOf("2025-06-07", "2025-06-08")),
            today = "2025-06-10"
        )
        assertEquals(0, result.currentStreak)
        assertEquals(2, result.bestStreak)
    }

    @Test
    fun `empty completions produce zeroed streak`() {
        val result = recalc(streak(), today = "2025-06-10")
        assertEquals(0, result.currentStreak)
        assertEquals(0, result.bestStreak)
        assertEquals(null, result.lastCompletedDate)
    }

    @Test
    fun `duplicate completion dates are normalized`() {
        val result = recalc(
            streak(completions = listOf("2025-06-10", "2025-06-10", "2025-06-09")),
            today = "2025-06-10"
        )
        assertEquals(listOf("2025-06-09", "2025-06-10"), result.completions)
        assertEquals(2, result.currentStreak)
    }

    // ── Weekly (positive) ─────────────────────────────────────────────────────

    @Test
    fun `weekly 3x counts weeks meeting the quota`() {
        // Monday-start weeks: Jun 2-8, Jun 9-15 (2025). Both have 3 completions.
        val result = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 3,
                completions = listOf(
                    "2025-06-02", "2025-06-04", "2025-06-06",
                    "2025-06-09", "2025-06-11", "2025-06-13"
                )
            ),
            today = "2025-06-14"
        )
        assertEquals(2, result.currentStreak)
        assertEquals(2, result.bestStreak)
    }

    @Test
    fun `weekly quota not met does not count the week`() {
        val result = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 3,
                completions = listOf(
                    "2025-06-02", "2025-06-04", "2025-06-06", // week 1: 3 ✓
                    "2025-06-09", "2025-06-11"                 // week 2: only 2 ✗
                )
            ),
            today = "2025-06-14"
        )
        // Week 1 is the previous period → still inside the grace window
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.bestStreak)
    }

    @Test
    fun `week start setting changes period boundaries`() {
        // 2025-06-08 is a Sunday. Sunday-start: Jun 8 begins a new week containing Jun 9.
        // Monday-start: Jun 8 ends the week of Jun 2; Jun 9 begins the next.
        val base = streak(
            frequency = FrequencyType.WEEKLY, frequencyCount = 2,
            completions = listOf("2025-06-08", "2025-06-09")
        )
        val sundayStart = recalc(base, firstDay = DayOfWeek.SUNDAY, today = "2025-06-09")
        val mondayStart = recalc(base, firstDay = DayOfWeek.MONDAY, today = "2025-06-09")
        assertEquals(1, sundayStart.currentStreak) // both fall into the same Sunday-start week
        assertEquals(0, mondayStart.currentStreak) // split across two Monday-start weeks
    }

    // ── Monthly / yearly (positive) ───────────────────────────────────────────

    @Test
    fun `monthly chain spans months`() {
        val result = recalc(
            streak(
                frequency = FrequencyType.MONTHLY, frequencyCount = 1,
                completions = listOf("2025-04-15", "2025-05-20", "2025-06-01")
            ),
            today = "2025-06-10"
        )
        assertEquals(3, result.currentStreak)
    }

    // ── Frequency history (positive) ──────────────────────────────────────────

    @Test
    fun `frequency change is evaluated era by era`() {
        val history = listOf(
            FrequencyChange("2025-01-01", FrequencyType.DAILY, 1),
            FrequencyChange("2025-06-09", FrequencyType.WEEKLY, 1)
        )
        val result = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                completions = listOf("2025-06-06", "2025-06-07", "2025-06-08", "2025-06-11"),
                frequencyHistory = history
            ),
            today = "2025-06-11"
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
        val result = recalc(
            streak(
                frequency = FrequencyType.DAILY, frequencyCount = 1,
                completions = listOf(
                    "2025-06-02", "2025-06-04", "2025-06-06", // full week: 3/3 ✓
                    "2025-06-09",                             // fragment: 1/1 ✓
                    "2025-06-11", "2025-06-12"                // daily era
                ),
                frequencyHistory = history
            ),
            today = "2025-06-12"
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
        val result = recalc(
            streak(
                frequency = FrequencyType.DAILY, frequencyCount = 1,
                completions = listOf(
                    "2025-06-02", "2025-06-04", "2025-06-06", // full week ✓
                    // nothing on Jun 9-10 — the fragment must not break the chain
                    "2025-06-11", "2025-06-12"
                ),
                frequencyHistory = history
            ),
            today = "2025-06-12"
        )
        assertEquals(3, result.currentStreak) // week + 2 days, fragment bridged
        assertEquals(3, result.bestStreak)
    }

    @Test
    fun `monthly to weekly - completion last week counts as one week, this week makes two`() {
        // A once-a-month streak switched to once-a-week today, with the monthly task done
        // last week. Pre-era math returned 0 here because the monthly period's full end
        // didn't touch the new weekly period's start.
        val today = "2026-06-23" // Tuesday; Sunday-week = [Jun 21, Jun 28)
        val history = listOf(
            FrequencyChange("2026-06-01", FrequencyType.MONTHLY, 1),
            FrequencyChange("2026-06-23", FrequencyType.WEEKLY, 1)
        )
        val before = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                completions = listOf("2026-06-17"), frequencyHistory = history
            ),
            firstDay = DayOfWeek.SUNDAY, today = today
        )
        assertEquals(1, before.currentStreak)

        val after = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                completions = listOf("2026-06-17", "2026-06-23"), frequencyHistory = history
            ),
            firstDay = DayOfWeek.SUNDAY, today = today
        )
        assertEquals(2, after.currentStreak)
    }

    @Test
    fun `switch to yearly preserves accumulated weekly history`() {
        val history = listOf(
            FrequencyChange("2025-01-06", FrequencyType.WEEKLY, 1),
            FrequencyChange("2025-06-11", FrequencyType.YEARLY, 100)
        )
        val result = recalc(
            streak(
                frequency = FrequencyType.YEARLY, frequencyCount = 100,
                completions = listOf("2025-05-12", "2025-05-19", "2025-05-26", "2025-06-02"),
                frequencyHistory = history
            ),
            today = "2025-06-12"
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
        val result = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                completions = listOf("2025-06-07", "2025-06-08", "2025-06-11"),
                frequencyHistory = history
            ),
            today = "2025-06-12"
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
        val result = recalc(
            streak(
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                completions = listOf("2025-06-09"), frequencyHistory = history
            ),
            today = "2025-06-10"
        )
        assertEquals(1, result.currentStreak)
    }

    // ── Current period satisfaction (used to silence reminders) ──────────────

    @Test
    fun `current period satisfied for completed daily habit`() {
        val s = streak(completions = listOf("2025-06-10"))
        assertTrue(
            StreakCalculator.isCurrentPeriodSatisfied(
                s, DayOfWeek.MONDAY, LocalDate.parse("2025-06-10")
            )
        )
        assertFalse(
            StreakCalculator.isCurrentPeriodSatisfied(
                s, DayOfWeek.MONDAY, LocalDate.parse("2025-06-11")
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
                base, DayOfWeek.MONDAY, LocalDate.parse("2025-06-12")
            )
        )
        val enough = base.copy(completions = listOf("2025-06-11", "2025-06-12"))
        assertTrue(
            StreakCalculator.isCurrentPeriodSatisfied(
                enough, DayOfWeek.MONDAY, LocalDate.parse("2025-06-12")
            )
        )
    }

    // ── Negative habits (anti-streaks) ────────────────────────────────────────

    @Test
    fun `fresh negative habit starts at one - staying clean today counts`() {
        val result = recalc(
            streak(createdDate = "2025-06-10", isNegative = true),
            today = "2025-06-10"
        )
        assertEquals(1, result.currentStreak)
        assertEquals(1, result.bestStreak)
        assertFalse(result.isCompletedToday)
    }

    @Test
    fun `negative habit grows by itself while nothing is marked`() {
        val result = recalc(
            streak(createdDate = "2025-06-08", isNegative = true),
            today = "2025-06-12"
        )
        assertEquals(5, result.currentStreak) // Jun 8, 9, 10, 11, 12 — all clean
        assertEquals(5, result.bestStreak)
    }

    @Test
    fun `slip-up today drops a zero-tolerance streak to zero`() {
        val result = recalc(
            streak(
                createdDate = "2025-06-08", isNegative = true,
                frequencyCount = 0,
                completions = listOf("2025-06-12")
            ),
            today = "2025-06-12"
        )
        assertEquals(0, result.currentStreak)
        assertEquals(4, result.bestStreak) // Jun 8-11 were clean
        assertTrue(result.isCompletedToday) // = slipped today
        assertEquals("2025-06-12", result.lastCompletedDate)
    }

    @Test
    fun `streak restarts the day after a slip-up`() {
        val result = recalc(
            streak(
                createdDate = "2025-06-01", isNegative = true,
                frequencyCount = 0,
                completions = listOf("2025-06-10")
            ),
            today = "2025-06-12"
        )
        assertEquals(2, result.currentStreak) // Jun 11, 12
        assertEquals(9, result.bestStreak)    // Jun 1-9
    }

    @Test
    fun `weekly allowance tolerates slips up to the limit`() {
        // "Junk food at most once a week": weeks [Jun 2, Jun 9) and [Jun 9, Jun 16)
        val base = streak(
            createdDate = "2025-06-02", isNegative = true,
            frequency = FrequencyType.WEEKLY, frequencyCount = 1,
            completions = listOf("2025-06-04", "2025-06-11")
        )
        val ok = recalc(base, today = "2025-06-12")
        assertEquals(2, ok.currentStreak) // one slip per week is within the allowance

        val over = recalc(
            base.copy(completions = listOf("2025-06-04", "2025-06-10", "2025-06-11")),
            today = "2025-06-12"
        )
        assertEquals(0, over.currentStreak) // two slips this week → broken now
        assertEquals(1, over.bestStreak)
    }

    @Test
    fun `undoing a slip-up restores the negative streak`() {
        val slipped = streak(
            createdDate = "2025-06-08", isNegative = true,
            completions = listOf("2025-06-12")
        )
        val undone = StreakCalculator.recalculate(
            slipped, emptyList(), DayOfWeek.MONDAY, LocalDate.parse("2025-06-12")
        )
        assertEquals(5, undone.currentStreak)
        assertFalse(undone.isCompletedToday)
    }

    @Test
    fun `negative habit frequency change does not break a clean chain`() {
        // Clean daily quit-habit switched to weekly allowance mid-week: the chain of
        // era-clipped periods stays gapless, so the streak survives the switch.
        val history = listOf(
            FrequencyChange("2025-06-02", FrequencyType.DAILY, 0),
            FrequencyChange("2025-06-11", FrequencyType.WEEKLY, 1)
        )
        val result = recalc(
            streak(
                createdDate = "2025-06-02", isNegative = true,
                frequency = FrequencyType.WEEKLY, frequencyCount = 1,
                frequencyHistory = history
            ),
            today = "2025-06-12"
        )
        // 9 clean daily periods (Jun 2-10) + clean weekly fragment [Jun 11, Jun 16) = 10
        assertEquals(10, result.currentStreak)
    }

    @Test
    fun `negative habits report unsatisfied so check-in reminders keep firing`() {
        val s = streak(createdDate = "2025-06-08", isNegative = true)
        assertFalse(
            StreakCalculator.isCurrentPeriodSatisfied(
                s, DayOfWeek.MONDAY, LocalDate.parse("2025-06-10")
            )
        )
    }
}
