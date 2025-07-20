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

    fun loadStreaksFromFile(context: Context) {
        try {
            val file = File(context.filesDir, fileName)
            if (!file.exists()) return
            FileReader(file).use { reader ->
                val type = object : TypeToken<List<StreakExportDto>>() {}.type
                val exportList: List<StreakExportDto> = gson.fromJson(reader, type)
                val today = LocalDate.now()
                val streaks =
                        exportList.map { dto ->
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
                                    position = dto.position ?: exportList.indexOf(dto)
                            )
                        }
                _streaks.value = streaks
            }
        } catch (e: Exception) {
            // Optionally log error
        }
    }

    private fun saveStreaksToFile(context: Context) {
        try {
            val file = File(context.filesDir, fileName)
            val exportList =
                    _streaks.value?.map { streak ->
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
                                position = streak.position
                        )
                    }
                            ?: emptyList()
            FileWriter(file, false).use { writer -> gson.toJson(exportList, writer) }
        } catch (e: Exception) {
            // Optionally log error
        }
    }

    private fun updateWidget(context: Context) {
        val intent = android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        intent.component = ComponentName(context, StreaksWidgetProvider::class.java)
        val ids =
                AppWidgetManager.getInstance(context)
                        .getAppWidgetIds(ComponentName(context, StreaksWidgetProvider::class.java))
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        context.sendBroadcast(intent)
    }

    fun addStreak(
            name: String,
            emoji: String,
            frequency: FrequencyType,
            frequencyCount: Int,
            context: Context? = null,
            color: String = "#FF9900"
    ) {
        val todayStr = LocalDate.now().format(formatter)
        val newStreak =
                Streak(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        emoji = emoji,
                        frequency = frequency,
                        frequencyCount = frequencyCount,
                        createdDate = todayStr,
                        lastCompletedDate = null,
                        currentStreak = 0,
                        bestStreak = 0,
                        isCompletedToday = false,
                        completions = emptyList(),
                        reminder = null,
                        color = color
                )
        val currentStreaks = _streaks.value?.toMutableList() ?: mutableListOf()
        currentStreaks.add(newStreak)
        _streaks.value = currentStreaks
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    fun completeStreak(streakId: String, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val today = LocalDate.now()
            val todayStr = today.format(formatter)
            // Double check - prevent duplicate completions
            if (streak.completions.contains(todayStr)) return
            
            val updatedCompletions = streak.completions + todayStr
            val recalculatedStreak = recalculateStreakFromCompletions(streak, updatedCompletions)
            
            currentStreaks[index] = recalculatedStreak
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    fun uncompleteStreak(streakId: String, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val today = LocalDate.now()
            val todayStr = today.format(formatter)
            val updatedCompletions = streak.completions.filter { it != todayStr }
            val recalculatedStreak = recalculateStreakFromCompletions(streak, updatedCompletions)
            
            currentStreaks[index] = recalculatedStreak
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    fun toggleStreakCompletionForDate(streakId: String, date: LocalDate, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val dateStr = date.format(formatter)
            
            val updatedCompletions = if (streak.completions.contains(dateStr)) {
                // Remove the completion
                streak.completions.filter { it != dateStr }
            } else {
                // Add the completion
                streak.completions + dateStr
            }
            
            val recalculatedStreak = recalculateStreakFromCompletions(streak, updatedCompletions)
            
            currentStreaks[index] = recalculatedStreak
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    /**
     * Recalculates the entire streak from scratch based on completion dates.
     * This is more reliable than incremental updates and eliminates edge cases.
     */
    private fun recalculateStreakFromCompletions(streak: Streak, completions: List<String>): Streak {
        if (completions.isEmpty()) {
            return streak.copy(
                lastCompletedDate = null,
                currentStreak = 0,
                bestStreak = maxOf(streak.bestStreak, 0),
                isCompletedToday = false,
                completions = completions
            )
        }

        val today = LocalDate.now()
        val todayStr = today.format(formatter)
        val completionDates = completions.map { LocalDate.parse(it, formatter) }.sorted()
        
        // Group completions by periods and count them
        val periodCompletionCounts = mutableMapOf<LocalDate, Int>()
        completionDates.forEach { date ->
            val period = when (streak.frequency) {
                FrequencyType.DAILY -> date
                FrequencyType.WEEKLY -> {
                    val weekFields = WeekFields.of(Locale.getDefault())
                    date.with(weekFields.dayOfWeek(), 1)
                }
                FrequencyType.MONTHLY -> date.withDayOfMonth(1)
                FrequencyType.YEARLY -> date.withDayOfYear(1)
            }
            periodCompletionCounts[period] = (periodCompletionCounts[period] ?: 0) + 1
        }
        
        // Get all periods that meet the frequency requirement, in chronological order
        val validPeriods = periodCompletionCounts.filter { (_, count) -> 
            count >= streak.frequencyCount 
        }.keys.sorted()
        
        if (validPeriods.isEmpty()) {
            return streak.copy(
                lastCompletedDate = completionDates.lastOrNull()?.format(formatter),
                currentStreak = 0,
                bestStreak = maxOf(streak.bestStreak, 0),
                isCompletedToday = completions.contains(todayStr),
                completions = completions
            )
        }
        
        // Calculate streaks by finding consecutive periods
        var currentStreak = 0
        var bestStreak = 0
        var tempStreak = 0
        var lastPeriod: LocalDate? = null
        
        for (period in validPeriods) {
            if (lastPeriod == null || isConsecutivePeriod(lastPeriod, period, streak.frequency)) {
                tempStreak++
            } else {
                // Streak broken, start new streak
                bestStreak = maxOf(bestStreak, tempStreak)
                tempStreak = 1
            }
            lastPeriod = period
        }
        bestStreak = maxOf(bestStreak, tempStreak)
        
        // Current streak is the length of consecutive periods ending at the most recent valid period
        val lastValidPeriod = validPeriods.last()
        val todayPeriod = when (streak.frequency) {
            FrequencyType.DAILY -> today
            FrequencyType.WEEKLY -> {
                val weekFields = WeekFields.of(Locale.getDefault())
                today.with(weekFields.dayOfWeek(), 1)
            }
            FrequencyType.MONTHLY -> today.withDayOfMonth(1)
            FrequencyType.YEARLY -> today.withDayOfYear(1)
        }
        
        // Current streak extends from the end backwards to find consecutive periods
        currentStreak = 0
        var checkPeriod = lastValidPeriod
        
        // Count backwards from the last valid period to find the length of the current streak
        for (i in validPeriods.size - 1 downTo 0) {
            val period = validPeriods[i]
            if (i == validPeriods.size - 1) {
                currentStreak = 1
            } else {
                val nextPeriod = validPeriods[i + 1]
                if (isConsecutivePeriod(period, nextPeriod, streak.frequency)) {
                    currentStreak++
                } else {
                    break
                }
            }
        }
        
        // If the last valid period is not current/recent enough, reset current streak
        // For weekly/monthly/yearly: streak is valid if last period is current or previous consecutive period
        // For daily: streak is valid only if it includes today or yesterday
        val isCurrentPeriodValid = when (streak.frequency) {
            FrequencyType.DAILY -> {
                lastValidPeriod == todayPeriod || lastValidPeriod == todayPeriod.minusDays(1)
            }
            FrequencyType.WEEKLY -> {
                lastValidPeriod == todayPeriod || lastValidPeriod == todayPeriod.minusWeeks(1)
            }
            FrequencyType.MONTHLY -> {
                lastValidPeriod == todayPeriod || lastValidPeriod == todayPeriod.minusMonths(1)
            }
            FrequencyType.YEARLY -> {
                lastValidPeriod == todayPeriod || lastValidPeriod == todayPeriod.minusYears(1)
            }
        }
        
        if (!isCurrentPeriodValid) {
            currentStreak = 0
        }
        
        return streak.copy(
            lastCompletedDate = completionDates.lastOrNull()?.format(formatter),
            currentStreak = currentStreak,
            bestStreak = maxOf(streak.bestStreak, bestStreak),
            isCompletedToday = completions.contains(todayStr),
            completions = completions
        )
    }
    
    /**
     * Checks if two periods are consecutive based on the frequency type
     */
    private fun isConsecutivePeriod(period1: LocalDate, period2: LocalDate, frequency: FrequencyType): Boolean {
        return when (frequency) {
            FrequencyType.DAILY -> period1.plusDays(1) == period2
            FrequencyType.WEEKLY -> period1.plusWeeks(1) == period2
            FrequencyType.MONTHLY -> period1.plusMonths(1) == period2
            FrequencyType.YEARLY -> period1.plusYears(1) == period2
        }
    }

    private fun checkAndUpdateStreak(
            streak: Streak,
            completions: List<String>,
            today: LocalDate,
            isUndo: Boolean = false
    ): Pair<Boolean, List<String>> {
        val completionsAsDate = completions.map { LocalDate.parse(it, formatter) }
        val filteredCompletions =
                when (streak.frequency) {
                    FrequencyType.DAILY -> completionsAsDate.filter { it == today }
                    FrequencyType.WEEKLY -> {
                        val weekFields = WeekFields.of(Locale.getDefault())
                        val weekOfYear = today.get(weekFields.weekOfWeekBasedYear())
                        completionsAsDate.filter {
                            it.get(weekFields.weekOfWeekBasedYear()) == weekOfYear &&
                                    it.year == today.year
                        }
                    }
                    FrequencyType.MONTHLY ->
                            completionsAsDate.filter {
                                it.month == today.month && it.year == today.year
                            }
                    FrequencyType.YEARLY -> completionsAsDate.filter { it.year == today.year }
                }
        val count = filteredCompletions.size
        val filteredCompletionsStr = filteredCompletions.map { it.format(formatter) }

        // Get the last completed date for the streak
        val lastCompletedDate = streak.getLastCompletedDate()

        // Check if we're in a new period compared to the last completion
        val isNewPeriod =
                if (lastCompletedDate != null) {
                    when (streak.frequency) {
                        FrequencyType.DAILY -> today != lastCompletedDate
                        FrequencyType.WEEKLY -> {
                            val weekFields = WeekFields.of(Locale.getDefault())
                            val currentWeek = today.get(weekFields.weekOfWeekBasedYear())
                            val lastWeek = lastCompletedDate.get(weekFields.weekOfWeekBasedYear())
                            currentWeek != lastWeek || today.year != lastCompletedDate.year
                        }
                        FrequencyType.MONTHLY ->
                                today.month != lastCompletedDate.month ||
                                        today.year != lastCompletedDate.year
                        FrequencyType.YEARLY -> today.year != lastCompletedDate.year
                    }
                } else true // If no last completion, it's always a new period

        // For non-undo operations:
        // - If it's a new period and we've met the frequency count, increment the streak
        // - If it's the same period, don't increment even if we exceed the frequency count
        // For undo operations:
        // - If we drop below frequency count in the current period, decrement the streak
        return if (!isUndo) {
            Pair(isNewPeriod && count >= streak.frequencyCount, filteredCompletionsStr)
        } else {
            Pair(count < streak.frequencyCount, filteredCompletionsStr)
        }
    }

    /**
     * Recalculates all streaks from their completion data. 
     * Useful for fixing data inconsistencies or after app updates.
     */
    fun recalculateAllStreaks(context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val recalculatedStreaks = currentStreaks.map { streak ->
            recalculateStreakFromCompletions(streak, streak.completions)
        }
        _streaks.value = recalculatedStreaks
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    fun setStreaksFromImport(streaks: List<Streak>, context: Context? = null) {
        _streaks.value = streaks
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    fun updateStreakNameEmojiColor(
            streakId: String,
            name: String,
            emoji: String,
            color: String,
            context: Context? = null
    ) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val updatedStreak = streak.copy(name = name, emoji = emoji, color = color)
            currentStreaks[index] = updatedStreak
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    fun deleteStreak(streakId: String, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            currentStreaks.removeAt(index)
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    fun setStreakReminder(streakId: String, reminder: Reminder, context: Context? = null): Streak? {
        val currentStreaks = _streaks.value?.toMutableList() ?: return null
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val updatedStreak = streak.copy(reminder = reminder)
            currentStreaks[index] = updatedStreak
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
            return updatedStreak
        }
        return null
    }

    fun removeStreakReminder(streakId: String, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val updatedStreak = streak.copy(reminder = null)
            currentStreaks[index] = updatedStreak
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    fun reorderStreaks(newOrder: List<String>, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val streakMap = currentStreaks.associateBy { it.id }
        val reordered =
                newOrder.mapIndexed { idx, id -> streakMap[id]?.copy(position = idx) ?: return }
        _streaks.value = reordered
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    companion object {
        @Volatile private var INSTANCE: StreakRepository? = null

        fun getInstance(): StreakRepository {
            return INSTANCE
                    ?: synchronized(this) { INSTANCE ?: StreakRepository().also { INSTANCE = it } }
        }
    }
}
