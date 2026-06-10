package com.arihant.streaks.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.arihant.streaks.data.FrequencyType
import com.arihant.streaks.data.Reminder
import com.arihant.streaks.data.Streak
import com.arihant.streaks.data.StreakRepository
import java.time.LocalDate

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = StreakRepository.getInstance(application)

    val streaks: LiveData<List<Streak>> = repository.streaks

    fun addStreak(
        name: String,
        emoji: String,
        frequency: FrequencyType,
        frequencyCount: Int,
        color: String
    ) = repository.addStreak(name, emoji, frequency, frequencyCount, color)

    fun completeStreak(streakId: String) = repository.completeStreak(streakId)

    fun uncompleteStreak(streakId: String) = repository.uncompleteStreak(streakId)

    fun deleteStreak(streakId: String): Streak? = repository.deleteStreak(streakId)

    fun restoreStreak(streak: Streak) = repository.restoreStreak(streak)

    fun setStreakReminder(streakId: String, reminder: Reminder): Streak? =
        repository.setStreakReminder(streakId, reminder)

    fun removeStreakReminder(streakId: String) = repository.removeStreakReminder(streakId)

    fun updateStreakNameEmojiColor(streakId: String, name: String, emoji: String, color: String) =
        repository.updateStreakNameEmojiColor(streakId, name, emoji, color)

    fun updateStreakFrequency(streakId: String, frequency: FrequencyType, frequencyCount: Int) =
        repository.updateStreakFrequency(streakId, frequency, frequencyCount)

    fun reorderStreaks(newOrder: List<String>) = repository.reorderStreaks(newOrder)

    fun toggleStreakCompletion(streakId: String, date: LocalDate) =
        repository.toggleStreakCompletionForDate(streakId, date)
}
