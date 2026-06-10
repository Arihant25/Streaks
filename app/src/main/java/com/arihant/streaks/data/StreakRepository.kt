package com.arihant.streaks.data

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Looper
import android.util.Log
import androidx.core.util.AtomicFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.arihant.streaks.widgets.StreaksWidgetProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileNotFoundException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Single source of truth for streaks.
 *
 * Data lives in an in-memory [cache] mirrored into [streaks] LiveData; persistence is a JSON file
 * written atomically ([AtomicFile]) on a single-threaded executor so the UI thread never touches
 * disk and a crash mid-write can't corrupt existing data.
 */
class StreakRepository private constructor(private val appContext: Context) {

    private val _streaks = MutableLiveData<List<Streak>>(emptyList())
    val streaks: LiveData<List<Streak>> = _streaks

    @Volatile private var cache: List<Streak> = emptyList()
    @Volatile private var loaded = false
    @Volatile var lastLoadFailed = false
        private set
    @Volatile private var lastComputedDate: LocalDate = LocalDate.now()

    private val lock = Any()
    private val ioExecutor = Executors.newSingleThreadExecutor()
    private val gson = Gson()
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun storageFile() = AtomicFile(File(appContext.filesDir, FILE_NAME))

    private fun weekStartsMonday() = SettingsStore.weekStartsMonday(appContext)

    // ── Loading ───────────────────────────────────────────────────────────────

    /** Idempotent, thread-safe. Receivers and widgets call this before reading. */
    fun ensureLoaded() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            val file = storageFile()
            val list: List<Streak> = try {
                val json = String(file.readFully(), Charsets.UTF_8)
                val type = object : TypeToken<List<StreakExportDto>>() {}.type
                val dtos: List<StreakExportDto> = gson.fromJson(json, type) ?: emptyList()
                dtos.map { it.toStreak() }
            } catch (e: FileNotFoundException) {
                emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load streaks, keeping a copy of the corrupt file", e)
                lastLoadFailed = true
                backupCorruptFile(file.baseFile)
                emptyList()
            }
            val weekMonday = weekStartsMonday()
            val today = LocalDate.now()
            cache = list
                .map { StreakCalculator.recalculate(it, it.completions, weekMonday, today) }
                .sortedBy { it.position }
            lastComputedDate = today
            loaded = true
            postToLiveData(cache)
        }
    }

    /** Warm-up for app start: load off the main thread, then run [onLoaded] on the same thread. */
    fun ensureLoadedAsync(onLoaded: (() -> Unit)? = null) {
        ioExecutor.execute {
            ensureLoaded()
            onLoaded?.invoke()
        }
    }

    private fun backupCorruptFile(base: File) {
        try {
            if (base.exists()) base.copyTo(File(base.parentFile, "$FILE_NAME.corrupt"), overwrite = true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to back up corrupt file", e)
        }
    }

    private fun postToLiveData(list: List<Streak>) {
        if (Looper.myLooper() == Looper.getMainLooper()) _streaks.value = list
        else _streaks.postValue(list)
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    private fun persistAsync(snapshot: List<Streak>) {
        ioExecutor.execute {
            val file = storageFile()
            var stream: java.io.FileOutputStream? = null
            try {
                stream = file.startWrite()
                stream.write(gson.toJson(snapshot.map { it.toDto() }).toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save streaks", e)
                stream?.let { file.failWrite(it) }
            }
        }
    }

    private fun updateWidget() {
        val ids = AppWidgetManager.getInstance(appContext)
            .getAppWidgetIds(ComponentName(appContext, StreaksWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
            .setComponent(ComponentName(appContext, StreaksWidgetProvider::class.java))
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        appContext.sendBroadcast(intent)
    }

    /** Applies [transform] to the current list, publishes, saves and refreshes widgets. */
    private fun mutate(transform: (List<Streak>) -> List<Streak>?) {
        val newList: List<Streak>
        synchronized(lock) {
            newList = transform(cache) ?: return
            cache = newList
        }
        postToLiveData(newList)
        persistAsync(newList)
        updateWidget()
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    fun getStreaks(): List<Streak> = cache

    fun findStreak(streakId: String): Streak? = cache.find { it.id == streakId }

    fun isCurrentPeriodSatisfied(streak: Streak): Boolean =
        StreakCalculator.isCurrentPeriodSatisfied(streak, weekStartsMonday())

    // ── Mutations ─────────────────────────────────────────────────────────────

    fun addStreak(
        name: String,
        emoji: String,
        frequency: FrequencyType,
        frequencyCount: Int,
        color: String = Streak.DEFAULT_COLOR
    ) {
        val todayStr = LocalDate.now().format(formatter)
        mutate { list ->
            val newStreak = Streak(
                id = UUID.randomUUID().toString(),
                name = name,
                emoji = emoji,
                frequency = frequency,
                frequencyCount = frequencyCount,
                createdDate = todayStr,
                lastCompletedDate = null,
                color = color,
                position = (list.maxOfOrNull { it.position } ?: -1) + 1
            )
            list + newStreak
        }
    }

    fun completeStreak(streakId: String) {
        val todayStr = LocalDate.now().format(formatter)
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            val streak = list[idx]
            if (streak.completions.contains(todayStr)) return@mutate null
            list.toMutableList().also {
                it[idx] = recalculated(streak, streak.completions + todayStr)
            }
        }
    }

    fun uncompleteStreak(streakId: String) {
        val todayStr = LocalDate.now().format(formatter)
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            val streak = list[idx]
            list.toMutableList().also {
                it[idx] = recalculated(streak, streak.completions.filter { c -> c != todayStr })
            }
        }
    }

    fun toggleStreakCompletionForDate(streakId: String, date: LocalDate) {
        val dateStr = date.format(formatter)
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            val streak = list[idx]
            val newCompletions =
                if (streak.completions.contains(dateStr)) streak.completions.filter { it != dateStr }
                else streak.completions + dateStr
            list.toMutableList().also { it[idx] = recalculated(streak, newCompletions) }
        }
    }

    fun updateStreakNameEmojiColor(streakId: String, name: String, emoji: String, color: String) {
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            list.toMutableList().also {
                it[idx] = it[idx].copy(name = name, emoji = emoji, color = color)
            }
        }
    }

    /**
     * Changes the frequency/count of an existing streak. The new rule takes effect TODAY:
     * closed periods stay evaluated by the rules in effect back then, the cut-short remainder
     * of the old period is judged against a prorated quota (see [StreakCalculator]), so the
     * change never rewrites history and never breaks a live chain by itself.
     */
    fun updateStreakFrequency(streakId: String, frequency: FrequencyType, frequencyCount: Int) {
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            val streak = list[idx]
            if (streak.frequency == frequency && streak.frequencyCount == frequencyCount) {
                return@mutate null
            }

            val today = LocalDate.now()
            val todayStr = today.format(formatter)

            // Keep only entries that already took effect. Dropping today's (and any future)
            // entries makes the latest change always win — appending blindly used to let an
            // earlier-dated entry shadow a later change forever.
            val applied = streak.frequencyHistory.filter {
                runCatching { LocalDate.parse(it.effectiveFrom) }.getOrNull()?.isBefore(today) == true
            }
            val base = if (applied.isEmpty() && LocalDate.parse(streak.createdDate).isBefore(today)) {
                // First change: record the ORIGINAL rule so earlier periods keep it
                listOf(FrequencyChange(streak.createdDate, streak.frequency, streak.frequencyCount))
            } else {
                applied
            }
            val newHistory = base + FrequencyChange(todayStr, frequency, frequencyCount)

            val draft = streak.copy(
                frequency = frequency,
                frequencyCount = frequencyCount,
                frequencyHistory = newHistory
            )
            list.toMutableList().also { it[idx] = recalculated(draft, streak.completions) }
        }
    }

    fun recalculateAll() {
        lastComputedDate = LocalDate.now()
        mutate { list -> list.map { recalculated(it, it.completions) } }
    }

    /** Cheap onResume hook: recalculates only when the calendar date has changed. */
    fun refreshIfDayChanged() {
        if (LocalDate.now() != lastComputedDate) recalculateAll()
    }

    /** Removes the streak and returns it so the caller can offer undo. */
    fun deleteStreak(streakId: String): Streak? {
        var removed: Streak? = null
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            removed = list[idx]
            list.toMutableList().also { it.removeAt(idx) }
        }
        return removed
    }

    /** Re-inserts a previously deleted streak at its old position. */
    fun restoreStreak(streak: Streak) {
        mutate { list ->
            if (list.any { it.id == streak.id }) return@mutate null
            (list + recalculated(streak, streak.completions)).sortedBy { it.position }
        }
    }

    fun setStreakReminder(streakId: String, reminder: Reminder): Streak? {
        var updated: Streak? = null
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            list.toMutableList().also {
                it[idx] = it[idx].copy(reminder = reminder)
                updated = it[idx]
            }
        }
        return updated
    }

    fun removeStreakReminder(streakId: String) {
        mutate { list ->
            val idx = list.indexOfFirst { it.id == streakId }
            if (idx == -1) return@mutate null
            list.toMutableList().also { it[idx] = it[idx].copy(reminder = null) }
        }
    }

    fun reorderStreaks(newOrder: List<String>) {
        mutate { list ->
            val byId = list.associateBy { it.id }
            if (newOrder.size != list.size || !newOrder.all { byId.containsKey(it) }) {
                return@mutate null
            }
            newOrder.mapIndexed { i, id -> byId.getValue(id).copy(position = i) }
        }
    }

    /** Replaces everything (import). Recalculates so imported numbers are trustworthy. */
    fun replaceAll(streaks: List<Streak>) {
        mutate {
            streaks
                .sortedBy { it.position }
                .mapIndexed { i, s -> recalculated(s.copy(position = i), s.completions) }
        }
    }

    private fun recalculated(streak: Streak, completions: List<String>): Streak =
        StreakCalculator.recalculate(streak, completions, weekStartsMonday())

    companion object {
        private const val TAG = "StreakRepository"
        private const val FILE_NAME = "streaks.json"

        @Volatile private var INSTANCE: StreakRepository? = null

        fun getInstance(context: Context): StreakRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: StreakRepository(context.applicationContext).also { INSTANCE = it }
            }
    }
}
