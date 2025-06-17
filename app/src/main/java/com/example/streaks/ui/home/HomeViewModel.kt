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
}