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
 * ## Streak freezes (positive habits only)
 *
 * A frozen period bridges the chain like a neutral fragment: the streak survives a missed
 * period but doesn't grow. Freezes are earned by consistency — one per [FREEZE_EARN_EVERY]
 * honestly completed periods over the habit's lifetime — and spent only when they actually
 * save a missed period (freezing a period that gets completed anyway costs nothing). At most
 * [MAX_CONSECUTIVE_FROZEN] periods in a row can be saved, so a streak can't idle forever.
 * The available count is fully derived from history, which keeps recalculation idempotent.
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

    private enum class Status { VALID, NEUTRAL, FROZEN, FAILED }

    /** One freeze is earned for every this many honestly completed periods. */
    const val FREEZE_EARN_EVERY = 10

    /** At most this many periods in a row can be saved by freezes. */
    const val MAX_CONSECUTIVE_FROZEN = 2

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

        // Freezes on future dates can't exist through the UI but could arrive via import;
        // they must not participate (a key after the running period breaks the anchor scan).
        val freezeDates =
            streak.freezes
                .mapNotNull { runCatching { LocalDate.parse(it, formatter) }.getOrNull() }
                .filter { !it.isAfter(today) }

        if (normalized.isEmpty() && freezeDates.isEmpty()) {
            return streak.copy(
                lastCompletedDate = null,
                currentStreak = 0,
                bestStreak = 0,
                isCompletedToday = false,
                completions = normalized,
                freezesAvailable = 0
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

        // Frozen periods must exist as keys too — an empty frozen period is the whole point.
        val frozenStarts = mutableSetOf<LocalDate>()
        freezeDates.forEach { date ->
            val period = periodFor(date, eras, firstDayOfWeek, negative = false)
            frozenStarts.add(period.start)
            if (period.start !in periods) {
                periods[period.start] = period
                counts[period.start] = 0
            }
        }

        val ordered = periods.values.toList()
        val runningPeriod = periodFor(today, eras, firstDayOfWeek, negative = false)

        // Single forward pass resolves every period's status, including the freeze rules:
        // a freeze turns a missed period into a bridge (FROZEN), but only while at most
        // MAX_CONSECUTIVE_FROZEN periods in a row are being saved — an endless vacation
        // still breaks the chain. A freeze on a period that was completed anyway, or one
        // wasted past the consecutive limit, is NOT spent.
        val statuses = HashMap<LocalDate, Status>(ordered.size)
        var totalValid = 0
        var frozenSpent = 0
        var consecutiveFrozen = 0
        var chainEnd: LocalDate? = null
        for (period in ordered) {
            if (chainEnd != null && period.start != chainEnd) {
                consecutiveFrozen = 0 // calendar gap — nothing left to save
            }
            val count = counts[period.start] ?: 0
            val base = when {
                count >= period.quota -> Status.VALID
                period.isFragment -> Status.NEUTRAL
                else -> Status.FAILED
            }
            val status =
                if (base == Status.FAILED &&
                    period.start in frozenStarts &&
                    consecutiveFrozen < MAX_CONSECUTIVE_FROZEN &&
                    // Budget check in chronological order: only freezes already earned by
                    // this point can be spent — freezing on credit is not a thing
                    frozenSpent < totalValid / FREEZE_EARN_EVERY
                ) {
                    frozenSpent++
                    consecutiveFrozen++
                    Status.FROZEN
                } else {
                    // A completed or honestly failed period ends the frozen run;
                    // neutral fragments neither extend nor interrupt it
                    if (base != Status.NEUTRAL) consecutiveFrozen = 0
                    if (base == Status.VALID) totalValid++
                    base
                }
            statuses[period.start] = status
            chainEnd = period.end
        }

        // Best streak: forward scan. Neutral fragments and frozen periods bridge; failed or
        // missed full periods reset; the running period gets grace until it is over.
        var best = 0
        var run = 0
        var previousEnd: LocalDate? = null
        for (period in ordered) {
            if (previousEnd != null && period.start != previousEnd) run = 0
            when (statuses.getValue(period.start)) {
                Status.VALID -> {
                    run++
                    best = maxOf(best, run)
                }
                Status.NEUTRAL, Status.FROZEN -> Unit
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
            when (statuses.getValue(period.start)) {
                Status.VALID -> current++
                Status.NEUTRAL, Status.FROZEN -> Unit
                Status.FAILED -> if (!isRunning) break // running period still has grace
            }
            expectEnd = period.start
        }

        // Freezes are earned by consistency — one for every FREEZE_EARN_EVERY honestly
        // completed periods over the habit's lifetime — and spent only when they actually
        // save a missed period. Everything is derived, so recalculation stays idempotent.
        val available = (totalValid / FREEZE_EARN_EVERY - frozenSpent).coerceAtLeast(0)

        return streak.copy(
            lastCompletedDate = completionDates.lastOrNull()?.format(formatter),
            currentStreak = current,
            bestStreak = maxOf(best, current),
            isCompletedToday = normalized.contains(todayStr),
            completions = normalized,
            freezesAvailable = available
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
            completions = normalized,
            // Freezes are a positive-habit concept: a quit-habit's streak already grows by
            // itself, so "pausing" it would mean licensing extra slip-ups
            freezesAvailable = 0
        )
    }
}
