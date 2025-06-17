package com.example.streaks.data

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import java.time.LocalDate
import java.util.UUID
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

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
                val streaks = exportList.map { dto ->
                    val lastCompleted = dto.lastCompletedDate?.let { LocalDate.parse(it, formatter) }
                    Streak(
                        id = dto.id,
                        name = dto.name,
                        emoji = dto.emoji,
                        frequency = dto.frequency,
                        frequencyCount = dto.frequencyCount,
                        createdDate = LocalDate.parse(dto.createdDate, formatter),
                        lastCompletedDate = lastCompleted,
                        currentStreak = dto.currentStreak,
                        isCompletedToday = lastCompleted == today,
                        completions = dto.completions?.map { LocalDate.parse(it, formatter) } ?: emptyList()
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
            val exportList = _streaks.value?.map { streak ->
                StreakExportDto(
                    id = streak.id,
                    name = streak.name,
                    emoji = streak.emoji,
                    frequency = streak.frequency,
                    frequencyCount = streak.frequencyCount,
                    createdDate = streak.createdDate.format(formatter),
                    lastCompletedDate = streak.lastCompletedDate?.format(formatter),
                    currentStreak = streak.currentStreak,
                    completions = streak.completions.map { it.format(formatter) }
                )
            } ?: emptyList()
            FileWriter(file, false).use { writer ->
                gson.toJson(exportList, writer)
            }
        } catch (e: Exception) {
            // Optionally log error
        }
    }

    fun addStreak(name: String, emoji: String, frequency: FrequencyType, frequencyCount: Int, context: Context? = null) {
        val newStreak = Streak(
            id = UUID.randomUUID().toString(),
            name = name,
            emoji = emoji,
            frequency = frequency,
            frequencyCount = frequencyCount,
            createdDate = LocalDate.now(),
            lastCompletedDate = null,
            currentStreak = 0,
            isCompletedToday = false,
            completions = emptyList()
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
            if (streak.isCompletedToday) return // Already completed today
            val updatedCompletions = streak.completions + today
            val (shouldIncrement, filteredCompletions) = checkAndUpdateStreak(streak, updatedCompletions, today)
            val updatedStreak = streak.copy(
                lastCompletedDate = today,
                currentStreak = if (shouldIncrement) streak.currentStreak + 1 else streak.currentStreak,
                isCompletedToday = true,
                completions = filteredCompletions
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
            val updatedCompletions = streak.completions.filter { it != today }
            val (shouldDecrement, filteredCompletions) = checkAndUpdateStreak(streak, updatedCompletions, today, isUndo = true)
            val updatedStreak = streak.copy(
                lastCompletedDate = null,
                currentStreak = if (shouldDecrement && streak.currentStreak > 0) streak.currentStreak - 1 else streak.currentStreak,
                isCompletedToday = false,
                completions = filteredCompletions
            )
            currentStreaks[index] = updatedStreak
            _streaks.value = currentStreaks
            context?.let { saveStreaksToFile(it) }
        }
    }

    private fun checkAndUpdateStreak(streak: Streak, completions: List<LocalDate>, today: LocalDate, isUndo: Boolean = false): Pair<Boolean, List<LocalDate>> {
        val filteredCompletions = when (streak.frequency) {
            FrequencyType.DAILY -> completions.filter { it == today }
            FrequencyType.WEEKLY -> {
                val weekFields = WeekFields.of(Locale.getDefault())
                val weekOfYear = today.get(weekFields.weekOfWeekBasedYear())
                completions.filter { it.get(weekFields.weekOfWeekBasedYear()) == weekOfYear && it.year == today.year }
            }
            FrequencyType.MONTHLY -> completions.filter { it.month == today.month && it.year == today.year }
            FrequencyType.YEARLY -> completions.filter { it.year == today.year }
        }
        val count = filteredCompletions.size
        return if (!isUndo) {
            Pair(count >= streak.frequencyCount, filteredCompletions)
        } else {
            Pair(count < streak.frequencyCount, filteredCompletions)
        }
    }

    fun setStreaksFromImport(streaks: List<Streak>, context: Context? = null) {
        _streaks.value = streaks
        context?.let { saveStreaksToFile(it) }
    }

    companion object {
        @Volatile
        private var INSTANCE: StreakRepository? = null

        fun getInstance(): StreakRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: StreakRepository().also { INSTANCE = it }
            }
        }
    }
}
