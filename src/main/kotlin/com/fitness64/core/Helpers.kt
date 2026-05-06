package com.fitness64.core

import com.fitness64.schema.ActivityService
import com.fitness64.schema.WorkoutLog
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*
import kotlin.math.roundToInt

const val KM_PER_MILE = 1.60934
const val HALF_MARATHON_KM = 21.0975
const val MARATHON_KM = 42.195

data class ProgressRecord(val label: String, val value: String)
data class DistanceBenchmark(val label: String, val distanceKm: Double)

fun getStartOfWeek(date: LocalDate): LocalDate = date.minusDays(date.dayOfWeek.value.toLong() - 1)

fun parseActivityDate(value: String): LocalDate? = runCatching { LocalDate.parse(value) }.getOrNull()

fun parseDistanceKm(value: String): Double? {
    if (value.isBlank()) return null
    val number = Regex("""\d+(\.\d+)?""").find(value)?.value?.toDoubleOrNull() ?: return null
    val lowerValue = value.lowercase()
    return if (lowerValue.contains("mile") || Regex("""\bmi\b""").containsMatchIn(lowerValue)) {
        number * KM_PER_MILE
    } else {
        number
    }
}

fun parseDurationMinutes(value: String): Int? {
    val trimmed = value.trim().lowercase()
    if (trimmed.isBlank()) return null

    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")
        if (parts.size != 3 || parts.any { it.toIntOrNull() == null }) return null
        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val seconds = parts[2].toInt()
        if (minutes !in 0..59 || seconds !in 0..59) return null
        val totalMinutes = hours * 60 + minutes + if (seconds >= 30) 1 else 0
        return totalMinutes.takeIf { it > 0 }
    }

    val hours = Regex("""(\d+)\s*(h|hr|hrs|hour|hours)\b""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    val minutes = Regex("""(\d+)\s*(m|min|mins|minute|minutes)\b""").find(trimmed)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    if (hours > 0 || minutes > 0) return (hours * 60 + minutes).takeIf { it > 0 }

    return Regex("""\d+""").find(trimmed)?.value?.toIntOrNull()?.takeIf { it > 0 }
}

fun pluralSuffix(count: Int): String = if (count == 1) "" else "s"

fun splitPreferredActivities(preferredActivities: String?): List<String> =
    preferredActivities?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

suspend fun loadActivityTypeNames(activityService: ActivityService, workouts: List<WorkoutLog>): Map<Int, String> {
    val names = mutableMapOf<Int, String>()
    for (id in workouts.map { it.activityTypeId }.distinct()) {
        names[id] = activityService.getActivityTypeName(id) ?: "Activity"
    }
    return names
}

fun buildRunningRecords(workouts: List<WorkoutLog>, activityTypeNames: Map<Int, String>): List<ProgressRecord> {
    val runs = workoutsForActivityType(workouts, activityTypeNames, "Running")
    val benchmarks = listOf(
        DistanceBenchmark("Fastest 1 km", 1.0), DistanceBenchmark("Fastest 1 mile", KM_PER_MILE),
        DistanceBenchmark("Fastest 5 km", 5.0), DistanceBenchmark("Fastest 10 km", 10.0),
        DistanceBenchmark("Fastest half marathon", HALF_MARATHON_KM), DistanceBenchmark("Fastest marathon", MARATHON_KM)
    ).map { fastestBenchmarkRecord(runs, it) }
    return benchmarks + listOf(
        longestDistanceRecord(runs, "Longest run", "No running distance logged yet"),
        bestPaceRecord(runs)
    )
}

fun buildCyclingRecords(workouts: List<WorkoutLog>, activityTypeNames: Map<Int, String>): List<ProgressRecord> {
    val rides = workoutsForActivityType(workouts, activityTypeNames, "Cycling")
    return listOf(
        longestDistanceRecord(rides, "Longest ride", "No cycling distance logged yet"),
        fastestAverageSpeedRecord(rides)
    )
}

fun workoutsForActivityType(workouts: List<WorkoutLog>, activityTypeNames: Map<Int, String>, typeName: String): List<WorkoutLog> =
    workouts.filter {
        activityTypeNames[it.activityTypeId].equals(typeName, ignoreCase = true) &&
            (it.distance ?: 0.0) > 0.0 && it.duration > 0
    }

fun fastestBenchmarkRecord(runs: List<WorkoutLog>, benchmark: DistanceBenchmark): ProgressRecord {
    val best = runs.mapNotNull { run ->
        estimatedMinutesForDistance(run, benchmark.distanceKm)?.let { run to it }
    }.minByOrNull { it.second }

    val value = if (best == null) "No eligible run yet"
    else "${formatClockDuration(best.second)} from ${formatDistance(best.first.distance!!)} km run"

    return ProgressRecord(benchmark.label, value)
}

fun estimatedMinutesForDistance(workout: WorkoutLog, targetDistanceKm: Double): Double? {
    val d = workout.distance ?: return null
    if (d < targetDistanceKm) return null
    return (workout.duration / d) * targetDistanceKm
}

fun longestDistanceRecord(workouts: List<WorkoutLog>, label: String, emptyMessage: String): ProgressRecord {
    val best = workouts.maxByOrNull { it.distance ?: 0.0 }
    val value = if (best?.distance == null) emptyMessage
    else "${formatDistance(best.distance)} km in ${formatClockDuration(best.duration.toDouble())}"
    return ProgressRecord(label, value)
}

fun bestPaceRecord(runs: List<WorkoutLog>): ProgressRecord {
    val best = runs.minByOrNull { it.duration / (it.distance ?: 1.0) }
    val value = if (best?.distance == null) "No running pace available yet"
    else "${formatClockDuration(best.duration / best.distance)} / km from ${formatDistance(best.distance)} km run"
    return ProgressRecord("Best pace", value)
}

fun fastestAverageSpeedRecord(rides: List<WorkoutLog>): ProgressRecord {
    val best = rides.maxByOrNull {
        val d = it.distance ?: 0.0
        val h = it.duration / 60.0
        if (h <= 0.0) 0.0 else d / h
    }
    val value = if (best?.distance == null) "No cycling speed available yet"
    else "${formatSpeed(best.distance / (best.duration / 60.0))} from ${formatDistance(best.distance)} km ride"
    return ProgressRecord("Fastest average speed", value)
}

fun formatDistance(distance: Double): String =
    if (distance % 1.0 == 0.0) distance.toInt().toString()
    else "%.2f".format(Locale.UK, distance).trimEnd('0').trimEnd('.')

fun formatClockDuration(minutes: Double): String {
    val totalSeconds = (minutes * 60).roundToInt()
    val hours = totalSeconds / 3600
    val mins = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, mins, seconds) else "%d:%02d".format(mins, seconds)
}

fun formatSpeed(speedKmh: Double): String {
    val formatted = if (speedKmh % 1.0 == 0.0) speedKmh.toInt().toString() else "%.1f".format(Locale.UK, speedKmh)
    return "$formatted km/h"
}
