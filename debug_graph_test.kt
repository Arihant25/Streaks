import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun main() {
    // Test the date parsing and comparison logic
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    // Simulate some completion dates
    val completionStrings = listOf(
        "2025-01-01",
        "2025-01-15", 
        "2025-02-10",
        "2025-07-20" // Today
    )
    
    // Parse them like the app does
    val completions = completionStrings.map { LocalDate.parse(it) }.toSet()
    
    println("Completions parsed: $completions")
    
    // Test a few dates to see if they match
    val testDates = listOf(
        LocalDate.of(2025, 1, 1),
        LocalDate.of(2025, 1, 15),
        LocalDate.of(2025, 2, 10),
        LocalDate.of(2025, 7, 20),
        LocalDate.of(2025, 1, 2) // Should not match
    )
    
    testDates.forEach { date ->
        val isCompleted = completions.contains(date)
        println("Date $date: isCompleted = $isCompleted")
    }
}
