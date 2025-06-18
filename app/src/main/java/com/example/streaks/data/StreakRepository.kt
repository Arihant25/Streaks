package com.example.streaks.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
        context?.let { saveStreaksToFile(it) }
    }

    fun completeStreak(streakId: String, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val today = LocalDate.now()
            val todayStr = today.format(formatter)
            if (streak.isCompletedToday) return // Already completed today
            val updatedCompletions = streak.completions + todayStr
            val (shouldIncrement, filteredCompletions) =
                    checkAndUpdateStreak(streak, updatedCompletions, today)
            val newCurrentStreak =
                    if (shouldIncrement) streak.currentStreak + 1 else streak.currentStreak
            val newBestStreak = maxOf(streak.bestStreak, newCurrentStreak)
            val updatedStreak =
                    streak.copy(
                            lastCompletedDate = todayStr,
                            currentStreak = newCurrentStreak,
                            bestStreak = newBestStreak,
                            isCompletedToday = true,
                            completions = filteredCompletions,
                            reminder = null
                    )
            currentStreaks[index] = updatedStreak
            _streaks.value = currentStreaks
            context?.let { saveStreaksToFile(it) }
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
            val (shouldDecrement, filteredCompletions) =
                    checkAndUpdateStreak(streak, updatedCompletions, today, isUndo = true)
            val updatedStreak =
                    streak.copy(
                            lastCompletedDate = null,
                            currentStreak =
                                    if (shouldDecrement && streak.currentStreak > 0)
                                            streak.currentStreak - 1
                                    else streak.currentStreak,
                            isCompletedToday = false,
                            completions = filteredCompletions,
                            reminder = null
                    )
            currentStreaks[index] = updatedStreak
            _streaks.value = currentStreaks
            context?.let { saveStreaksToFile(it) }
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
        return if (!isUndo) {
            Pair(count >= streak.frequencyCount, filteredCompletionsStr)
        } else {
            Pair(count < streak.frequencyCount, filteredCompletionsStr)
        }
    }

    fun setStreaksFromImport(streaks: List<Streak>, context: Context? = null) {
        _streaks.value = streaks
        context?.let { saveStreaksToFile(it) }
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
            context?.let { saveStreaksToFile(it) }
        }
    }

    fun deleteStreak(streakId: String, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            currentStreaks.removeAt(index)
            _streaks.value = currentStreaks
            context?.let { saveStreaksToFile(it) }
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
            context?.let { saveStreaksToFile(it) }
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
            context?.let { saveStreaksToFile(it) }
        }
    }

    fun reorderStreaks(newOrder: List<String>, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val streakMap = currentStreaks.associateBy { it.id }
        val reordered =
                newOrder.mapIndexed { idx, id -> streakMap[id]?.copy(position = idx) ?: return }
        _streaks.value = reordered
        context?.let { saveStreaksToFile(it) }
    }

    companion object {
        @Volatile private var INSTANCE: StreakRepository? = null

        fun getInstance(): StreakRepository {
            return INSTANCE
                    ?: synchronized(this) { INSTANCE ?: StreakRepository().also { INSTANCE = it } }
        }
    }
}
