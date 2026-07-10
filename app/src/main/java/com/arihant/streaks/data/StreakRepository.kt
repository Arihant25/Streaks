package com.arihant.streaks.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.util.AtomicFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.arihant.streaks.utils.WeekConfig
import com.arihant.streaks.widgets.StreaksWidgetProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

class StreakRepository {
    private val _streaks = MutableLiveData<List<Streak>>(emptyList())
    val streaks: LiveData<List<Streak>> = _streaks

    private val fileName = "streaks.json"
    private val gson = Gson()
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    // Persistence runs on a single background thread so the UI never blocks on disk I/O
    // and writes can't interleave.
    private val ioExecutor = Executors.newSingleThreadExecutor()

    @Volatile private var lastComputedDate: LocalDate = LocalDate.now()
    @Volatile var lastLoadFailed = false
        private set

    fun loadStreaksFromFile(context: Context) {
        // Week boundaries in the recalculation below depend on the configured start day
        WeekConfig.init(context)
        val file = AtomicFile(File(context.filesDir, fileName))
        val loaded: List<Streak> =
                try {
                    val json = String(file.readFully(), Charsets.UTF_8)
                    val type = object : TypeToken<List<StreakExportDto>>() {}.type
                    val exportList: List<StreakExportDto> = gson.fromJson(json, type) ?: emptyList()
                    exportList.map { it.toStreak() }
                } catch (e: FileNotFoundException) {
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load streaks, keeping a copy of the corrupt file", e)
                    lastLoadFailed = true
                    backupCorruptFile(File(context.filesDir, fileName))
                    return
                }
        // Recalculate so streaks broken since the last launch don't show stale counts
        lastComputedDate = LocalDate.now()
        _streaks.value =
                loaded.map { recalculated(it, it.completions) }.sortedBy { it.position }
    }

    private fun backupCorruptFile(base: File) {
        try {
            if (base.exists()) base.copyTo(File(base.parentFile, "$fileName.corrupt"), overwrite = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to back up corrupt file", e)
        }
    }

    private fun saveStreaksToFile(context: Context) {
        val snapshot = _streaks.value?.map { it.toDto() } ?: emptyList()
        val appContext = context.applicationContext
        ioExecutor.execute {
            val file = AtomicFile(File(appContext.filesDir, fileName))
            var stream: java.io.FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(gson.toJson(snapshot).toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save streaks", e)
                stream?.let { file.failWrite(it) }
            }
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

    private fun recalculated(streak: Streak, completions: List<String>): Streak =
            StreakCalculator.recalculate(streak, completions, WeekConfig.firstDayOfWeek)

    /** For reminder logic: whether this period needs no further action. */
    fun isCurrentPeriodSatisfied(streak: Streak): Boolean =
            StreakCalculator.isCurrentPeriodSatisfied(streak, WeekConfig.firstDayOfWeek)

    /** Cheap onResume hook: recalculates only when the calendar date has changed. */
    fun refreshIfDayChanged(context: Context) {
        if (LocalDate.now() != lastComputedDate) recalculateAllStreaks(context)
    }

    fun addStreak(
            name: String,
            emoji: String,
            frequency: FrequencyType,
            frequencyCount: Int,
            context: Context? = null,
            color: String = Streak.DEFAULT_COLOR,
            isNegative: Boolean = false
    ) {
        val todayStr = LocalDate.now().format(formatter)
        val currentStreaks = _streaks.value?.toMutableList() ?: mutableListOf()
        val newStreak =
                Streak(
                        id = UUID.randomUUID().toString(),
                        name = name,
                        emoji = emoji,
                        frequency = frequency,
                        frequencyCount = frequencyCount,
                        createdDate = todayStr,
                        lastCompletedDate = null,
                        color = color,
                        position = (currentStreaks.maxOfOrNull { it.position } ?: -1) + 1,
                        isNegative = isNegative
                )
        // A fresh negative habit already shows "1 day clean", so run the math once
        currentStreaks.add(recalculated(newStreak, newStreak.completions))
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
            val todayStr = LocalDate.now().format(formatter)
            // Double check - prevent duplicate completions
            if (streak.completions.contains(todayStr)) return

            currentStreaks[index] = recalculated(streak, streak.completions + todayStr)
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
            val todayStr = LocalDate.now().format(formatter)
            currentStreaks[index] =
                    recalculated(streak, streak.completions.filter { it != todayStr })
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
            val updatedCompletions =
                    if (streak.completions.contains(dateStr)) {
                        streak.completions.filter { it != dateStr }
                    } else {
                        streak.completions + dateStr
                    }
            currentStreaks[index] = recalculated(streak, updatedCompletions)
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    /**
     * Recalculates all streaks from their completion data.
     * Useful for fixing data inconsistencies or after app updates.
     */
    fun recalculateAllStreaks(context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        lastComputedDate = LocalDate.now()
        _streaks.value = currentStreaks.map { recalculated(it, it.completions) }
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    fun setStreaksFromImport(streaks: List<Streak>, context: Context? = null) {
        _streaks.value =
                streaks
                        .sortedBy { it.position }
                        .mapIndexed { i, s -> recalculated(s.copy(position = i), s.completions) }
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    fun updateStreakDetails(
            streakId: String,
            name: String,
            emoji: String,
            color: String,
            frequency: FrequencyType,
            frequencyCount: Int,
            context: Context? = null
    ) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val streak = currentStreaks[index]
            val frequencyChanged =
                    streak.frequency != frequency || streak.frequencyCount != frequencyCount

            // A frequency change starts a new era TODAY: closed periods stay evaluated by the
            // rules in effect back then (see StreakCalculator), so the change never rewrites
            // history and never breaks a live chain by itself.
            val newHistory =
                    if (frequencyChanged) {
                        val today = LocalDate.now()
                        val todayStr = today.format(formatter)
                        // Drop entries that haven't taken effect yet (same-day re-changes) so
                        // the latest change always wins and history stays strictly increasing.
                        val applied =
                                streak.frequencyHistory.filter {
                                    runCatching { LocalDate.parse(it.effectiveFrom) }
                                            .getOrNull()
                                            ?.isBefore(today) == true
                                }
                        val base =
                                if (applied.isEmpty() &&
                                                runCatching { LocalDate.parse(streak.createdDate) }
                                                        .getOrNull()
                                                        ?.isBefore(today) == true
                                ) {
                                    // First change: record the ORIGINAL rule for earlier periods
                                    listOf(
                                            FrequencyChange(
                                                    streak.createdDate,
                                                    streak.frequency,
                                                    streak.frequencyCount
                                            )
                                    )
                                } else {
                                    applied
                                }
                        base + FrequencyChange(todayStr, frequency, frequencyCount)
                    } else {
                        streak.frequencyHistory
                    }

            val updatedStreak =
                    streak.copy(
                            name = name,
                            emoji = emoji,
                            color = color,
                            frequency = frequency,
                            frequencyCount = frequencyCount,
                            frequencyHistory = newHistory
                    )
            currentStreaks[index] = recalculated(updatedStreak, updatedStreak.completions)
            _streaks.value = currentStreaks
            context?.let {
                saveStreaksToFile(it)
                updateWidget(it)
            }
        }
    }

    /** Removes the streak and returns it so the caller can offer undo. */
    fun deleteStreak(streakId: String, context: Context? = null): Streak? {
        val currentStreaks = _streaks.value?.toMutableList() ?: return null
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index == -1) return null
        val removed = currentStreaks.removeAt(index)
        _streaks.value = currentStreaks
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
        return removed
    }

    /** Re-inserts a previously deleted streak at its old position (undo). */
    fun restoreStreak(streak: Streak, context: Context? = null) {
        val currentStreaks = _streaks.value?.toMutableList() ?: return
        if (currentStreaks.any { it.id == streak.id }) return
        currentStreaks.add(recalculated(streak, streak.completions))
        _streaks.value = currentStreaks.sortedBy { it.position }
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    fun setStreakReminder(streakId: String, reminder: Reminder, context: Context? = null): Streak? {
        val currentStreaks = _streaks.value?.toMutableList() ?: return null
        val index = currentStreaks.indexOfFirst { it.id == streakId }
        if (index != -1) {
            val updatedStreak = currentStreaks[index].copy(reminder = reminder)
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
            currentStreaks[index] = currentStreaks[index].copy(reminder = null)
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
        if (reordered.size != currentStreaks.size) return
        _streaks.value = reordered
        context?.let {
            saveStreaksToFile(it)
            updateWidget(it)
        }
    }

    companion object {
        private const val TAG = "StreakRepository"

        @Volatile private var INSTANCE: StreakRepository? = null

        fun getInstance(): StreakRepository {
            return INSTANCE
                    ?: synchronized(this) { INSTANCE ?: StreakRepository().also { INSTANCE = it } }
        }
    }
}
