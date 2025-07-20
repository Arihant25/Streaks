import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.*

// Test data from the user's JSON
fun testStreakCalculation() {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val completions = listOf(
        "2025-06-04", "2025-06-07", "2025-06-09", "2025-06-10", "2025-06-11",
        "2025-06-13", "2025-06-14", "2025-06-15", "2025-06-17", "2025-06-18",
        "2025-06-19", "2025-06-20", "2025-06-21", "2025-06-23", "2025-06-24",
        "2025-06-26", "2025-06-27", "2025-06-29", "2025-07-01", "2025-07-03",
        "2025-07-05", "2025-07-08", "2025-07-10", "2025-07-11", "2025-07-12",
        "2025-07-13", "2025-07-14", "2025-07-15", "2025-07-16", "2025-07-17",
        "2025-07-18", "2025-07-19"
    )
    
    val frequencyCount = 3
    val today = LocalDate.of(2025, 7, 20)
    
    println("Testing Exercise streak (Weekly, 3x per week)")
    println("Today: $today")
    println("Completions: ${completions.size} total")
    
    // Test without today
    val resultWithoutToday = calculateWeeklyStreak(completions, frequencyCount, today)
    println("Without today: Current=${resultWithoutToday.first}, Best=${resultWithoutToday.second}")
    
    // Test with today added
    val completionsWithToday = completions + today.format(formatter)
    val resultWithToday = calculateWeeklyStreak(completionsWithToday, frequencyCount, today)
    println("With today: Current=${resultWithToday.first}, Best=${resultWithToday.second}")
}

fun calculateWeeklyStreak(completions: List<String>, frequencyCount: Int, today: LocalDate): Pair<Int, Int> {
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    val completionDates = completions.map { LocalDate.parse(it, formatter) }.sorted()
    
    // Group by weeks
    val periodCompletionCounts = mutableMapOf<LocalDate, Int>()
    completionDates.forEach { date ->
        val weekFields = WeekFields.of(Locale.getDefault())
        val weekStart = date.with(weekFields.dayOfWeek(), 1)
        periodCompletionCounts[weekStart] = (periodCompletionCounts[weekStart] ?: 0) + 1
    }
    
    // Get weeks that meet frequency requirement
    val validPeriods = periodCompletionCounts.filter { (_, count) -> 
        count >= frequencyCount 
    }.keys.sorted()
    
    if (validPeriods.isEmpty()) return Pair(0, 0)
    
    // Calculate best streak
    var bestStreak = 0
    var tempStreak = 0
    var lastPeriod: LocalDate? = null
    
    for (period in validPeriods) {
        if (lastPeriod == null || period == lastPeriod.plusWeeks(1)) {
            tempStreak++
        } else {
            bestStreak = maxOf(bestStreak, tempStreak)
            tempStreak = 1
        }
        lastPeriod = period
    }
    bestStreak = maxOf(bestStreak, tempStreak)
    
    // Calculate current streak (backwards from last valid period)
    var currentStreak = 0
    val lastValidPeriod = validPeriods.last()
    val todayWeek = today.with(WeekFields.of(Locale.getDefault()).dayOfWeek(), 1)
    
    for (i in validPeriods.size - 1 downTo 0) {
        val period = validPeriods[i]
        if (i == validPeriods.size - 1) {
            currentStreak = 1
        } else {
            val nextPeriod = validPeriods[i + 1]
            if (nextPeriod == period.plusWeeks(1)) {
                currentStreak++
            } else {
                break
            }
        }
    }
    
    // Check if current streak is still valid
    if (lastValidPeriod != todayWeek && lastValidPeriod.plusWeeks(1) != todayWeek) {
        currentStreak = 0
    }
    
    println("Valid weeks: ${validPeriods.map { it.format(formatter) }}")
    println("Today's week starts: ${todayWeek.format(formatter)}")
    println("Last valid week: ${lastValidPeriod.format(formatter)}")
    
    return Pair(currentStreak, bestStreak)
}

fun main() {
    testStreakCalculation()
}
