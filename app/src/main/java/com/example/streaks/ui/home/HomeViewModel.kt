package com.example.streaks.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.streaks.data.Streak
import com.example.streaks.data.StreakRepository

class HomeViewModel : ViewModel() {

    private val repository = StreakRepository.getInstance()

    val streaks: LiveData<List<Streak>> = repository.streaks

    fun completeStreak(streakId: String, context: android.content.Context) {
        repository.completeStreak(streakId, context)
    }

    fun uncompleteStreak(streakId: String, context: android.content.Context) {
        repository.uncompleteStreak(streakId, context)
    }

    fun deleteStreak(streakId: String, context: android.content.Context) {
        repository.deleteStreak(streakId, context)
    }

    fun setStreakReminder(
            streakId: String,
            reminder: com.example.streaks.data.Reminder,
            context: android.content.Context
    ): com.example.streaks.data.Streak? {
        return repository.setStreakReminder(streakId, reminder, context)
    }

    fun removeStreakReminder(streakId: String, context: android.content.Context) {
        repository.removeStreakReminder(streakId, context)
    }

    fun updateStreakNameEmojiColor(
            streakId: String,
            name: String,
            emoji: String,
            color: String,
            context: android.content.Context
    ) {
        repository.updateStreakNameEmojiColor(streakId, name, emoji, color, context)
    }

    fun reorderStreaks(newOrder: List<String>, context: android.content.Context) {
        repository.reorderStreaks(newOrder, context)
    }
}
