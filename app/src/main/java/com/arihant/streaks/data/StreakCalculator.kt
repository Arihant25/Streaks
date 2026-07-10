package com.arihant.streaks.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * Pure streak math, free of Android dependencies so it can be unit-tested on the JVM.
 *
 * ## Positive habits (build something)
 *
 * The timeline is split into **eras** by the frequency history: each era carries the rule
 * (frequency, required count) that was active during its date range. Within an era the calendar
 * is divided into grid **periods** (days / weeks / months / years); periods are **clipped** to
 * the era's range, so period boundaries always tile exactly across a frequency change.
 *
 * A full period is valid when it contains at least `frequencyCount` completions. A clipped
 * period (**fragment**) is judged against a prorated quota — `ceil(count * fragmentDays /
 * fullDays)` — because the user never had the whole period available. A fragment that meets its
 * prorated quota counts as a streak unit; a fragment that doesn't is **neutral**: it neither
 * extends nor breaks the chain (a frequency change must never retroactively kill a streak).
 * A failed FULL period, or a fully missed one, breaks the chain as usual. The running period
 * has a grace window: it does not count until its quota is met, but cannot break the chain
 * until it is over.
 *
 * ## Negative habits (quit something)
 *
 * For negative habits, completions record **slip-ups** and `frequencyCount` is the number of
 * allowed slip-ups per period (usually 0). The chain is walked over EVERY period from the
 * habit's start to today — an empty period is a success, so the streak grows by itself while
 * nothing is marked. A period stays valid while `slips <= allowed`; exceeding the allowance
 * breaks the chain at that period. The running period counts immediately (staying clean today
 * is already an achievement), so a fresh habit starts at 1, and marking a slip-up beyond the
 * allowance drops the current streak to 0 on the spot. Fragments created by a frequency change
 * get a proportionally scaled-down allowance (`floor`), and — unlike positive habits — a failed
 * fragment DOES break the chain: the slips really happened.
 *
 * Invariants shared by both:
 * - closed periods are never re-evaluated by later frequency changes (era boundary = change day);
 * - weekly periods follow the configured first day of week, matching the calendar UI.
 */
object StreakCalculator {

    private val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ── Period grid ───────────────────────────────────────────────────────────

    fun periodStart(date: LocalDate, frequency: FrequencyType, firstDayOfWeek: DayOfWeek): LocalDate =
        when (frequency) {
            FrequencyType.DAILY -> date
            FrequencyType.WEEKLY -> date.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
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
        val quota: Int,         // required completions (positive) / allowed slips (negative)
        val isFragment: Boolean
    )

    private fun periodFor(
        date: LocalDate,
        eras: List<Era>,
        firstDayOfWeek: DayOfWeek,
        negative: Boolean
    ): Period {
        val era = eraFor(date, eras)
        val fullStart = periodStart(date, era.frequency, firstDayOfWeek)
        val fullEnd = periodEnd(fullStart, era.frequency)
        val start = if (era.start != null && era.start > fullStart) era.start else fullStart
        val end = if (era.endExclusive != null && era.endExclusive < fullEnd) era.endExclusive else fullEnd
        val isFragment = start != fullStart || end != fullEnd
        val quota = if (!isFragment) {
            era.count
        } else {
            val fullDays = ChronoUnit.DAYS.between(fullStart, fullEnd)
            val fragmentDays = ChronoUnit.DAYS.between(start, end)
            if (negative) {
                // Allowance shrinks with the fragment; rounding DOWN keeps a zero-tolerance
                // habit at zero tolerance
                ((era.count * fragmentDays) / fullDays).toInt()
            } else {
                // ceil(count * fragmentDays / fullDays): you can't do half a session
                ((era.count * fragmentDays + fullDays - 1) / fullDays).toInt().coerceAtLeast(1)
            }
        }
        return Period(start, end, quota, isFragment)
    }

    private enum class Status { VALID, NEUTRAL, FAILED }

    /**
     * POSITIVE habits only: true when the requirement for the period containing [today] is
     * already met, so a reminder would be noise. Negative habits deliberately have no
     * equivalent — their check-in reminder stays useful while the user is clean; callers skip
     * it only when a slip-up is already marked today ([Streak.isCompletedToday]).
     */
    fun isCurrentPeriodSatisfied(
        streak: Streak,
        firstDayOfWeek: DayOfWeek,
        today: LocalDate = LocalDate.now()
    ): Boolean {
        if (streak.isNegative) return false
        val period = periodFor(today, erasOf(streak), firstDayOfWeek, negative = false)
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
        firstDayOfWeek: DayOfWeek,
        today: LocalDate = LocalDate.now()
    ): Streak {
        val normalized = completions.distinct().sorted()
        return if (streak.isNegative) {
            recalculateNegative(streak, normalized, firstDayOfWeek, today)
        } else {
            recalculatePositive(streak, normalized, firstDayOfWeek, today)
        }
    }

    // ── Positive habits ───────────────────────────────────────────────────────

    private fun recalculatePositive(
        streak: Streak,
        normalized: List<String>,
        firstDayOfWeek: DayOfWeek,
        today: LocalDate
    ): Streak {
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
            val period = periodFor(date, eras, firstDayOfWeek, negative = false)
            periods[period.start] = period
            counts[period.start] = (counts[period.start] ?: 0) + 1
        }

        // Synthesize the fragments around each era boundary even when they hold no completions,
        // so an empty cut-short period bridges the chain instead of leaving a calendar gap.
        eras.asSequence().drop(1).mapNotNull { it.start }.forEach { boundary ->
            val tail = periodFor(boundary.minusDays(1), eras, firstDayOfWeek, negative = false)
            if (tail.isFragment && tail.start !in periods) {
                periods[tail.start] = tail
                counts[tail.start] = 0
            }
            val head = periodFor(boundary, eras, firstDayOfWeek, negative = false)
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
        val runningPeriod = periodFor(today, eras, firstDayOfWeek, negative = false)

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

    // ── Negative habits ───────────────────────────────────────────────────────

    private fun recalculateNegative(
        streak: Streak,
        normalized: List<String>,
        firstDayOfWeek: DayOfWeek,
        today: LocalDate
    ): Streak {
        val todayStr = today.format(formatter)
        val eras = erasOf(streak)

        val slipDates = normalized.map { LocalDate.parse(it, formatter) }
        val created = runCatching { LocalDate.parse(streak.createdDate) }.getOrDefault(today)
        val anchor = minOf(created, slipDates.firstOrNull() ?: created)

        // Count slips per era-clipped period start
        val slipCounts = mutableMapOf<LocalDate, Int>()
        slipDates.forEach { date ->
            val period = periodFor(date, eras, firstDayOfWeek, negative = true)
            slipCounts[period.start] = (slipCounts[period.start] ?: 0) + 1
        }

        // Walk EVERY period from the anchor to today: empty periods are successes here,
        // so the whole timeline participates in the chain.
        var current = 0
        var best = 0
        var run = 0
        var cursor = periodFor(anchor, eras, firstDayOfWeek, negative = true)
        while (cursor.start <= today) {
            val slips = slipCounts[cursor.start] ?: 0
            if (slips <= cursor.quota) {
                run++
                best = maxOf(best, run)
            } else {
                run = 0
            }
            if (cursor.end > today) break // that was the running period
            cursor = periodFor(cursor.end, eras, firstDayOfWeek, negative = true)
        }
        // The running period counts immediately — staying clean today is the achievement —
        // so `run` already includes it (or is 0 when today's allowance is blown).
        current = run

        return streak.copy(
            lastCompletedDate = slipDates.lastOrNull()?.format(formatter),
            currentStreak = current,
            bestStreak = maxOf(best, current),
            isCompletedToday = normalized.contains(todayStr), // = slipped today
            completions = normalized
        )
    }
}
