package com.arihant.streaks.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure streak math, free of Android dependencies so it can be unit-tested on the JVM.
 *
 * ## Model
 *
 * The timeline is split into **eras** by the frequency history: each era carries the rule
 * (frequency, required count) that was active during its date range. Within an era the calendar
 * is divided into grid **periods** (days / weeks / months / years); periods are **clipped** to
 * the era's range, so period boundaries always tile exactly across a frequency change.
 *
 * A full period is valid when it contains at least `count` completions. A clipped period
 * (**fragment**) is judged against a prorated quota — `ceil(count * fragmentDays / fullDays)` —
 * because the user never had the whole period available. A fragment that meets its prorated
 * quota counts as a streak unit; a fragment that doesn't is **neutral**: it neither extends nor
 * breaks the chain (a frequency change must never retroactively kill a streak). A failed FULL
 * period, or a fully missed one, breaks the chain as usual.
 *
 * Invariants:
 * - closed periods are never re-evaluated by later frequency changes (era boundary = change day);
 * - a frequency change by itself can never break an otherwise live chain;
 * - weekly periods follow the user's week-start preference, matching the calendar UI.
 *
 * The current streak is the chain ending at the running period, which has a grace window:
 * it does not count until its quota is met, but it cannot break the chain until it is over.
 */
object StreakCalculator {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ── Period grid ───────────────────────────────────────────────────────────

    fun periodStart(date: LocalDate, frequency: FrequencyType, weekStartsMonday: Boolean): LocalDate =
        when (frequency) {
            FrequencyType.DAILY -> date
            FrequencyType.WEEKLY -> {
                val firstDay = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY
                date.with(TemporalAdjusters.previousOrSame(firstDay))
            }
            FrequencyType.MONTHLY -> date.withDayOfMonth(1)
            FrequencyType.YEARLY -> date.withDayOfYear(1)
        }

    /** First day of the NEXT period (exclusive end of the period starting at [periodStart]). */
    fun periodEnd(periodStart: LocalDate, frequency: FrequencyType): LocalDate = when (frequency) {
        FrequencyType.DAILY -> periodStart.plusDays(1)
        FrequencyType.WEEKLY -> periodStart.plusWeeks(1)
        FrequencyType.MONTHLY -> periodStart.plusMonths(1)
        FrequencyType.YEARLY -> periodStart.plusYears(1)
    }

    // ── Eras ──────────────────────────────────────────────────────────────────

    private data class Era(
        val start: LocalDate?,        // null = open beginning (covers backfilled completions)
        val endExclusive: LocalDate?, // null = open end
        val frequency: FrequencyType,
        val count: Int
    )

    private fun erasOf(streak: Streak): List<Era> {
        // Sorted by date; for duplicate dates the LAST entry wins, so a re-change on the same
        // day can never be shadowed by an earlier one.
        val entries = streak.frequencyHistory
            .mapNotNull { change ->
                runCatching { LocalDate.parse(change.effectiveFrom) }.getOrNull()?.let { it to change }
            }
            .sortedBy { it.first }
            .associate { it }
            .toList()

        if (entries.isEmpty()) {
            return listOf(Era(null, null, streak.frequency, streak.frequencyCount))
        }
        return entries.mapIndexed { i, (from, change) ->
            Era(
                start = if (i == 0) null else from,
                endExclusive = entries.getOrNull(i + 1)?.first,
                frequency = change.frequency,
                count = change.frequencyCount
            )
        }
    }

    private fun eraFor(date: LocalDate, eras: List<Era>): Era =
        eras.last { it.start == null || it.start <= date }

    // ── Clipped periods ───────────────────────────────────────────────────────

    private data class Period(
        val start: LocalDate,   // clipped to the era
        val end: LocalDate,     // clipped to the era, exclusive
        val quota: Int,
        val isFragment: Boolean
    )

    private fun periodFor(date: LocalDate, eras: List<Era>, weekStartsMonday: Boolean): Period {
        val era = eraFor(date, eras)
        val fullStart = periodStart(date, era.frequency, weekStartsMonday)
        val fullEnd = periodEnd(fullStart, era.frequency)
        val start = if (era.start != null && era.start > fullStart) era.start else fullStart
        val end = if (era.endExclusive != null && era.endExclusive < fullEnd) era.endExclusive else fullEnd
        val isFragment = start != fullStart || end != fullEnd
        val quota = if (!isFragment) {
            era.count
        } else {
            // ceil(count * fragmentDays / fullDays): you can't do half a session
            val fullDays = ChronoUnit.DAYS.between(fullStart, fullEnd)
            val fragmentDays = ChronoUnit.DAYS.between(start, end)
            ((era.count * fragmentDays + fullDays - 1) / fullDays).toInt().coerceAtLeast(1)
        }
        return Period(start, end, quota, isFragment)
    }

    private enum class Status { VALID, NEUTRAL, FAILED }

    /** True when the requirement for the period containing [today] is already met. */
    fun isCurrentPeriodSatisfied(
        streak: Streak,
        weekStartsMonday: Boolean,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        val period = periodFor(today, erasOf(streak), weekStartsMonday)
        val count = streak.completions.count {
            val date = LocalDate.parse(it, formatter)
            !date.isBefore(period.start) && date.isBefore(period.end)
        }
        return count >= period.quota
    }

    // ── Recalculation ─────────────────────────────────────────────────────────

    fun recalculate(
        streak: Streak,
        completions: List<String>,
        weekStartsMonday: Boolean,
        today: LocalDate = LocalDate.now()
    ): Streak {
        val normalized = completions.distinct().sorted()
        val todayStr = today.format(formatter)

        if (normalized.isEmpty()) {
            return streak.copy(
                lastCompletedDate = null,
                currentStreak = 0,
                bestStreak = 0,
                isCompletedToday = false,
                completions = normalized
            )
        }

        val eras = erasOf(streak)
        val completionDates = normalized.map { LocalDate.parse(it, formatter) }

        // Group completions into era-clipped periods
        val periods = sortedMapOf<LocalDate, Period>()
        val counts = mutableMapOf<LocalDate, Int>()
        completionDates.forEach { date ->
            val period = periodFor(date, eras, weekStartsMonday)
            periods[period.start] = period
            counts[period.start] = (counts[period.start] ?: 0) + 1
        }

        // Synthesize the fragments around each era boundary even when they hold no completions,
        // so an empty cut-short period bridges the chain instead of leaving a calendar gap.
        eras.asSequence().drop(1).mapNotNull { it.start }.forEach { boundary ->
            val tail = periodFor(boundary.minusDays(1), eras, weekStartsMonday)
            if (tail.isFragment && tail.start !in periods) {
                periods[tail.start] = tail
                counts[tail.start] = 0
            }
            val head = periodFor(boundary, eras, weekStartsMonday)
            if (head.isFragment && head.start !in periods) {
                periods[head.start] = head
                counts[head.start] = 0
            }
        }

        fun statusOf(period: Period): Status {
            val count = counts[period.start] ?: 0
            return when {
                count >= period.quota -> Status.VALID
                period.isFragment -> Status.NEUTRAL
                else -> Status.FAILED
            }
        }

        val ordered = periods.values.toList()
        val runningPeriod = periodFor(today, eras, weekStartsMonday)

        // Best streak: forward scan. Neutral fragments bridge; failed or missed full periods
        // reset; the running period gets grace until it is over.
        var best = 0
        var run = 0
        var previousEnd: LocalDate? = null
        for (period in ordered) {
            if (previousEnd != null && period.start != previousEnd) run = 0
            when (statusOf(period)) {
                Status.VALID -> {
                    run++
                    best = maxOf(best, run)
                }
                Status.NEUTRAL -> Unit
                Status.FAILED -> if (period.start != runningPeriod.start) run = 0
            }
            previousEnd = period.end
        }

        // Current streak: backward scan from the present
        var current = 0
        var expectEnd: LocalDate? = null
        for (period in ordered.asReversed()) {
            if (expectEnd == null) {
                // Anchor: the latest key must be the running period itself or end exactly
                // where the running period starts — anything older means the chain is dead.
                if (period.start != runningPeriod.start && period.end != runningPeriod.start) break
            } else if (period.end != expectEnd) {
                break // calendar gap — at least one fully missed period in between
            }
            val isRunning = period.start == runningPeriod.start
            when (statusOf(period)) {
                Status.VALID -> current++
                Status.NEUTRAL -> Unit
                Status.FAILED -> if (!isRunning) break // running period still has grace
            }
            expectEnd = period.start
        }

        return streak.copy(
            lastCompletedDate = completionDates.last().format(formatter),
            currentStreak = current,
            bestStreak = maxOf(best, current),
            isCompletedToday = normalized.contains(todayStr),
            completions = normalized
        )
    }
}
