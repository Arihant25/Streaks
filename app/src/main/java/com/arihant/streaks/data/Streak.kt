package com.arihant.streaks.data

import android.os.Parcelable
import java.time.LocalDate
import kotlinx.parcelize.Parcelize

@Parcelize
data class Reminder(
        val time: String,
        val days: List<Int> = emptyList()
) : Parcelable

/** Records when frequency settings changed so past periods are evaluated by the rules in effect then */
@Parcelize
data class FrequencyChange(
        val effectiveFrom: String,   // ISO date — start of period when this rule took effect
        val frequency: FrequencyType,
        val frequencyCount: Int
) : Parcelable

@Parcelize
data class Streak(
        val id: String,
        val name: String,
        val emoji: String,
        val frequency: FrequencyType,
        val frequencyCount: Int,
        val createdDate: String,
        val lastCompletedDate: String?,
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val isCompletedToday: Boolean = false,
        val completions: List<String> = emptyList(),
        val reminder: Reminder? = null,
        val color: String = "#FF9900",
        val position: Int = Int.MAX_VALUE,
        val frequencyHistory: List<FrequencyChange> = emptyList()
) : Parcelable {
        fun getCreatedDate(): LocalDate = LocalDate.parse(createdDate)
        fun getLastCompletedDate(): LocalDate? = lastCompletedDate?.let { LocalDate.parse(it) }
        fun asLocalDateCompletions(): List<LocalDate> = completions.map { LocalDate.parse(it) }
}

@Parcelize
enum class FrequencyType : Parcelable {
        DAILY,
        WEEKLY,
        MONTHLY,
        YEARLY
}

data class StreakExportDto(
        val id: String,
        val name: String,
        val emoji: String,
        val frequency: FrequencyType,
        val frequencyCount: Int,
        val createdDate: String,
        val lastCompletedDate: String?,
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val completions: List<String> = emptyList(),
        val reminder: Reminder? = null,
        val color: String = "#FF9900",
        val position: Int = Int.MAX_VALUE,
        val frequencyHistory: List<FrequencyChange>? = null   // nullable for backwards compat
)

