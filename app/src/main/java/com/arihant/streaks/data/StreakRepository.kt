package com.arihant.streaks.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.arihant.streaks.widgets.StreaksWidgetProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

class StreakRepository {
    private val _streaks = MutableLiveData<List<Streak>>(emptyList())
    val streaks: LiveData<List<Streak>> = _streaks

    private val fileName = "streaks.json"
    private val gson = Gson()
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ── Period helpers ────────────────────────────────────────────────────────

    private fun getPeriodStart(date: LocalDate, frequency: FrequencyType): LocalDate = when (frequency) {
        FrequencyType.DAILY   -> date
        FrequencyType.WEEKLY  -> { val wf = WeekFields.of(Locale.getDefault()); date.with(wf.dayOfWeek(), 1) }
        FrequencyType.MONTHLY -> date.withDayOfMonth(1)
        FrequencyType.YEARLY  -> date.withDayOfYear(1)
    }

    /** Returns the first day of the NEXT period (exclusive end of current period). */
    private fun getPeriodEnd(periodStart: LocalDate, frequency: FrequencyType): LocalDate = when (frequency) {
        FrequencyType.DAILY   -> periodStart.plusDays(1)
        FrequencyType.WEEKLY  -> periodStart.plusWeeks(1)
        FrequencyType.MONTHLY -> periodStart.plusMonths(1)
        FrequencyType.YEARLY  -> periodStart.plusYears(1)
    }

    private fun getPreviousPeriodStart(periodStart: LocalDate, frequency: FrequencyType): LocalDate = when (frequency) {
        FrequencyType.DAILY   -> periodStart.minusDays(1)
        FrequencyType.WEEKLY  -> periodStart.minusWeeks(1)
        FrequencyType.MONTHLY -> periodStart.minusMonths(1)
        FrequencyType.YEARLY  -> periodStart.minusYears(1)
    }

    /**
     * Returns the (FrequencyType, requiredCount) in effect on [date] according to the streak's history.
     * Falls back to current streak.frequency / frequencyCount if no history.
     */
    private fun getEffectiveSettings(date: LocalDate, streak: Streak): Pair<FrequencyType, Int> {
        val history = streak.frequencyHistory
        if (history.isEmpty()) return streak.frequency to streak.frequencyCount

        val applicable = history
            .filter { LocalDate.parse(it.effectiveFrom) <= date }
            .maxByOrNull { LocalDate.parse(it.effectiveFrom) }

        return if (applicable != null) {
            applicable.frequency to applicable.frequencyCount
        } else {
            // date is before all history entries → use the oldest entry
            val oldest = history.minByOrNull { LocalDate.parse(it.effectiveFrom) }!!
            oldest.frequency to oldest.frequencyCount
        }
    }

    // ── File I/O ──────────────────────────────────────────────────────────────

    fun loadStreaksFromFile(context: Context) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return
            FileReader(file).use { reader ->
                val type = object : TypeToken<List<StreakExportDto>>() {}.type
                val exportList: List<StreakExportDto> = gson.fromJson(reader, type)
                val today = LocalDate.now()
                val streaks = exportList.map { dto ->
                    val lastCompleted = dto.lastCompletedDate
                    Streak(
                        id = dto.id,
                        name = dto.name,
                        emoji = dto.emoji,
                        frequency = dto.frequency,
                        frequencyCount = dto.frequencyCount,
                        createdDate = dto.createdDate,
                        lastCompletedDate = lastCompleted,
                        currentStreak = dto.currentStreak,
                        bestStreak = dto.bestStreak,
                        isCompletedToday = lastCompleted == today.format(formatter),
                        completions = dto.completions ?: emptyList(),
                        reminder = dto.reminder,
                        color = dto.color ?: "#FF9900",
                        position = dto.position ?: exportList.indexOf(dto),
                        frequencyHistory = dto.frequencyHistory ?: emptyList()
                    )
                }
                _streaks.value = streaks
            }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun saveStreaksToFile(context: Context) {
        try {
            val file = File(context.filesDir, fileName)
            val exportList = _streaks.value?.map { streak ->
                StreakExportDto(
                    id = streak.id,
                    name = streak.name,
                    emoji = streak.emoji,
                    frequency = streak.frequency,
                    frequencyCount = streak.frequencyCount,
                    createdDate = streak.createdDate,
                    lastCompletedDate = streak.lastCompletedDate,
                    currentStreak = streak.currentStreak,
                    bestStreak = streak.bestStreak,
                    completions = streak.completions,
                    reminder = streak.reminder,
                    color = streak.color,
                    position = streak.position,
                    frequencyHistory = streak.frequencyHistory
                )
            } ?: emptyList()
            FileWriter(file, false).use { writer -> gson.toJson(exportList, writer) }
        } catch (e: Exception) { /* ignore */ }
    }

    private fun updateWidget(context: Context) {
        val intent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.component = ComponentName(context, StreaksWidgetProvider::class.java)
        val ids = AppWidgetManager.getInstance(context)
            .getAppWidgetIds(ComponentName(context, StreaksWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    // ── Core recalculation ────────────────────────────────────────────────────

    /**
     * Recalculates streak fully from completions, respecting frequencyHistory so that
     * past periods are evaluated against the rules that were in effect at the time.
     *
     * Period keys carry their FrequencyType so that cross-era consecutive checks use
     * actual period boundaries rather than assuming the current frequency.
     */
    private fun recalculateStreakFromCompletions(streak: Streak, completions: List<String>): Streak {
        val today = LocalDate.now()
        val todayStr = today.format(formatter)

        if (completions.isEmpty()) {
            return streak.copy(
                lastCompletedDate = null,
                currentStreak = 0,
                bestStreak = maxOf(streak.bestStreak, 0),
                isCompletedToday = false,
                completions = completions
            )
        }

        val completionDates = completions.map { LocalDate.parse(it, formatter) }.sorted()

        // Key = (periodStart, FrequencyType) so periods from different eras don't collide
        data class PeriodKey(val start: LocalDate, val freq: FrequencyType)

        val periodCounts = mutableMapOf<PeriodKey, Int>()
        completionDates.forEach { date ->
            val (freq, _) = getEffectiveSettings(date, streak)
            val key = PeriodKey(getPeriodStart(date, freq), freq)
            periodCounts[key] = (periodCounts[key] ?: 0) + 1
        }

        // Keep only periods where the count met the required threshold
        val validKeys = periodCounts
            .filter { (key, count) ->
                val (_, required) = getEffectiveSettings(key.start, streak)
                count >= required
            }
            .keys
            .sortedBy { it.start }

        if (validKeys.isEmpty()) {
            return streak.copy(
                lastCompletedDate = completionDates.last().format(formatter),
                currentStreak = 0,
                bestStreak = maxOf(streak.bestStreak, 0),
                isCompletedToday = completions.contains(todayStr),
                completions = completions
            )
        }

        // Find best streak by scanning forward; two adjacent keys are consecutive iff
        // the end of the first period == the start of the second (handles cross-era boundaries)
        var bestStreak = 0
        var tempStreak = 1
        for (i in 1 until validKeys.size) {
            val prevEnd = getPeriodEnd(validKeys[i - 1].start, validKeys[i - 1].freq)
            if (prevEnd == validKeys[i].start) {
                tempStreak++
            } else {
                bestStreak = maxOf(bestStreak, tempStreak)
                tempStreak = 1
            }
        }
        bestStreak = maxOf(bestStreak, tempStreak)

        // Current streak = consecutive chain ending at last valid period
        var currentStreak = 1
        for (i in validKeys.size - 2 downTo 0) {
            val prevEnd = getPeriodEnd(validKeys[i].start, validKeys[i].freq)
            if (prevEnd == validKeys[i + 1].start) currentStreak++ else break
        }

        // Streak is only "live" if the last valid period is the current or previous period
        val lastKey = validKeys.last()
        val (todayFreq, _) = getEffectiveSettings(today, streak)
        val todayPeriodStart = getPeriodStart(today, todayFreq)
        val prevPeriodStart = getPreviousPeriodStart(todayPeriodStart, todayFreq)
        if (lastKey.start < prevPeriodStart) currentStreak = 0

        return streak.copy(
            lastCompletedDate = completionDates.last().format(formatter),
            currentStreak = currentStreak,
            bestStreak = maxOf(streak.bestStreak, bestStreak),
            isCompletedToday = completions.contains(todayStr),
            completions = completions
        )
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun addStreak(
        name: String, emoji: String, frequency: FrequencyType,
        frequencyCount: Int, context: Context? = null, color: String = "#FF9900"
    ) {
        val todayStr = LocalDate.now().format(formatter)
        val newStreak = Streak(
            id = UUID.randomUUID().toString(),
            name = name, emoji = emoji,
            frequency = frequency, frequencyCount = frequencyCount,
            createdDate = todayStr, lastCompletedDate = null,
            currentStreak = 0, bestStreak = 0, isCompletedToday = false,
            completions = emptyList(), reminder = null, color = color,
            frequencyHistory = emptyList()
        )
        val list = _streaks.value?.toMutableList() ?: mutableListOf()
        list.add(newStreak)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun completeStreak(streakId: String, context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        val streak = list[idx]
        val todayStr = LocalDate.now().format(formatter)
        if (streak.completions.contains(todayStr)) return
        val updated = recalculateStreakFromCompletions(streak, streak.completions + todayStr)
        list[idx] = updated
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun uncompleteStreak(streakId: String, context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        val streak = list[idx]
        val todayStr = LocalDate.now().format(formatter)
        val updated = recalculateStreakFromCompletions(streak, streak.completions.filter { it != todayStr })
        list[idx] = updated
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun toggleStreakCompletionForDate(streakId: String, date: LocalDate, context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        val streak = list[idx]
        val dateStr = date.format(formatter)
        val newCompletions = if (streak.completions.contains(dateStr))
            streak.completions.filter { it != dateStr }
        else
            streak.completions + dateStr
        list[idx] = recalculateStreakFromCompletions(streak, newCompletions)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun updateStreakNameEmojiColor(
        streakId: String, name: String, emoji: String, color: String, context: Context? = null
    ) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        list[idx] = list[idx].copy(name = name, emoji = emoji, color = color)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    /**
     * Changes the frequency/count of an existing streak, preserving its history so that
     * past periods continue to be evaluated against the rules that were in effect then.
     *
     * The change takes effect at the START OF THE CURRENT PERIOD, so past periods are
     * unaffected and the streak count is preserved as long as there is no gap.
     */
    fun updateStreakFrequency(
        streakId: String, frequency: FrequencyType, frequencyCount: Int, context: Context? = null
    ) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        val streak = list[idx]

        // No-op if nothing actually changed
        if (streak.frequency == frequency && streak.frequencyCount == frequencyCount) return

        val today = LocalDate.now()
        // Effective from the start of the current period under the NEW frequency
        val effectiveFrom = getPeriodStart(today, frequency).format(formatter)

        val newHistory: List<FrequencyChange> = if (streak.frequencyHistory.isEmpty()) {
            // First change: also record the ORIGINAL setting so we know what to apply pre-change
            listOf(
                FrequencyChange(streak.createdDate, streak.frequency, streak.frequencyCount),
                FrequencyChange(effectiveFrom, frequency, frequencyCount)
            )
        } else {
            streak.frequencyHistory + FrequencyChange(effectiveFrom, frequency, frequencyCount)
        }

        val draft = streak.copy(
            frequency = frequency,
            frequencyCount = frequencyCount,
            frequencyHistory = newHistory
        )
        list[idx] = recalculateStreakFromCompletions(draft, streak.completions)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun recalculateAllStreaks(context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        _streaks.value = list.map { recalculateStreakFromCompletions(it, it.completions) }
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun deleteStreak(streakId: String, context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        list.removeAt(idx)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun setStreakReminder(streakId: String, reminder: Reminder, context: Context? = null): Streak? {
        val list = _streaks.value?.toMutableList() ?: return null
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return null
        list[idx] = list[idx].copy(reminder = reminder)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
        return list[idx]
    }

    fun removeStreakReminder(streakId: String, context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        val idx = list.indexOfFirst { it.id == streakId }
        if (idx == -1) return
        list[idx] = list[idx].copy(reminder = null)
        _streaks.value = list
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun reorderStreaks(newOrder: List<String>, context: Context? = null) {
        val list = _streaks.value?.toMutableList() ?: return
        val map = list.associateBy { it.id }
        _streaks.value = newOrder.mapIndexed { i, id -> map[id]?.copy(position = i) ?: return }
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    fun setStreaksFromImport(streaks: List<Streak>, context: Context? = null) {
        _streaks.value = streaks
        context?.let { saveStreaksToFile(it); updateWidget(it) }
    }

    companion object {
        @Volatile private var INSTANCE: StreakRepository? = null
        fun getInstance(): StreakRepository =
            INSTANCE ?: synchronized(this) { INSTANCE ?: StreakRepository().also { INSTANCE = it } }
    }
}
