package com.arihant.streaks.data

import android.os.Parcelable
import java.time.LocalDate
import kotlinx.parcelize.Parcelize

@Parcelize
data class Reminder(
        val time: String, // ISO_LOCAL_TIME string
        val days: List<Int> = emptyList() // 0=Mon, 6=Sun
) : Parcelable

/**
 * Records when frequency settings changed so past periods are evaluated by the rules that were
 * in effect at the time (see [StreakCalculator] for the era model).
 */
@Parcelize
data class FrequencyChange(
        val effectiveFrom: String, // ISO date — the day this rule took effect
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
        val createdDate: String, // Store as ISO string for Parcelable
        val lastCompletedDate: String?, // Store as ISO string or null
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val isCompletedToday: Boolean = false,
        val completions: List<String> = emptyList(), // Store as ISO strings
        val reminder: Reminder? = null,
        val color: String = DEFAULT_COLOR,
        val position: Int = Int.MAX_VALUE, // New field for ordering
        val frequencyHistory: List<FrequencyChange> = emptyList(),
        /**
         * A negative habit is something the user is quitting (smoking, junk food …).
         * For these, [completions] records SLIP-UPS: the streak grows by itself while nothing
         * is marked, and [frequencyCount] is the number of allowed slip-ups per period
         * (usually 0). [isCompletedToday] then means "slipped today".
         */
        val isNegative: Boolean = false
) : Parcelable {
        fun getCreatedDate(): LocalDate = LocalDate.parse(createdDate)
        fun getLastCompletedDate(): LocalDate? = lastCompletedDate?.let { LocalDate.parse(it) }
        fun asLocalDateCompletions(): List<LocalDate> = completions.map { LocalDate.parse(it) }

        fun toDto(): StreakExportDto =
                StreakExportDto(
                        id = id,
                        name = name,
                        emoji = emoji,
                        frequency = frequency,
                        frequencyCount = frequencyCount,
                        createdDate = createdDate,
                        lastCompletedDate = lastCompletedDate,
                        currentStreak = currentStreak,
                        bestStreak = bestStreak,
                        completions = completions,
                        reminder = reminder,
                        color = color,
                        position = position,
                        frequencyHistory = frequencyHistory,
                        isNegative = isNegative
                )

        companion object {
                const val DEFAULT_COLOR = "#FF9900" // neon orange
        }
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
        val createdDate: String, // formatted as ISO string
        val lastCompletedDate: String?, // formatted as ISO string or null
        val currentStreak: Int = 0,
        val bestStreak: Int = 0,
        val completions: List<String> = emptyList(),
        val reminder: Reminder? = null,
        val color: String = Streak.DEFAULT_COLOR,
        val position: Int = Int.MAX_VALUE,
        val frequencyHistory: List<FrequencyChange>? = null, // nullable for backwards compat
        val isNegative: Boolean = false
) {
        /** Gson can leave non-null fields null when the JSON omits them, hence the fallbacks. */
        @Suppress("USELESS_ELVIS", "SENSELESS_COMPARISON")
        fun toStreak(): Streak =
                Streak(
                        id = id,
                        name = name,
                        emoji = emoji ?: "🔥",
                        frequency = frequency,
                        frequencyCount = frequencyCount,
                        createdDate = createdDate,
                        lastCompletedDate = lastCompletedDate,
                        currentStreak = currentStreak,
                        bestStreak = bestStreak,
                        isCompletedToday = false, // derived by recalculation
                        completions = completions ?: emptyList(),
                        reminder = reminder,
                        color = color ?: Streak.DEFAULT_COLOR,
                        position = position,
                        frequencyHistory = frequencyHistory ?: emptyList(),
                        isNegative = isNegative
                )
}
