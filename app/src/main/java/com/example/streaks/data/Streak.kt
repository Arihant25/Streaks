package com.example.streaks.data

import java.time.LocalDate

data class Streak(
    val id: String,
    val name: String,
    val emoji: String,
    val frequency: FrequencyType,
    val frequencyCount: Int,
    val createdDate: LocalDate,
    val lastCompletedDate: LocalDate?,
    val currentStreak: Int = 0,
    val isCompletedToday: Boolean = false
)

data class StreakExportDto(
    val id: String,
    val name: String,
    val emoji: String,
    val frequency: FrequencyType,
    val frequencyCount: Int,
    val createdDate: String, // formatted as ISO string
    val lastCompletedDate: String?, // formatted as ISO string or null
    val currentStreak: Int = 0
)

enum class FrequencyType {
    DAILY, WEEKLY, MONTHLY, YEARLY
}
