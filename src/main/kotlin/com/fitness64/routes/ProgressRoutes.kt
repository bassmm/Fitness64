package com.fitness64.routes

import com.fitness64.core.buildCyclingRecords
import com.fitness64.core.buildRunningRecords
import com.fitness64.core.formatDistance
import com.fitness64.core.getStartOfWeek
import com.fitness64.core.loadActivityTypeNames
import com.fitness64.core.pluralSuffix
import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.schema.ActivityService
import com.fitness64.schema.UserService
import com.fitness64.schema.WeightliftingService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Application.configureProgressRoutes(
    userService: UserService,
    activityService: ActivityService,
    weightliftingService: WeightliftingService
) {
    routing {
        authenticate("auth-session") {
            get("/progress") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val (user, userId) = auth

                val workouts = activityService.getWorkoutsForUser(userId)
                val activityTypeNames = loadActivityTypeNames(activityService, workouts)
                val weightliftingHistory = weightliftingService.getWeightliftingHistory(userId)

                val today = LocalDate.now()
                val startOfWeek = getStartOfWeek(today)

                fun String.isInCurrentWeek(): Boolean {
                    val d = runCatching { LocalDate.parse(this) }.getOrNull()
                    return d != null && d >= startOfWeek && d <= today
                }

                val cardioWorkoutsThisWeek = workouts.count { it.logDate.isInCurrentWeek() }
                val weightliftingWorkoutsThisWeek = weightliftingHistory.count { it.logDate.isInCurrentWeek() }
                val workoutsThisWeek = cardioWorkoutsThisWeek + weightliftingWorkoutsThisWeek

                val cardioDays = workouts.map { it.logDate }.toSet()
                val weightliftingDays = weightliftingHistory.map { it.logDate }.toSet()
                val activeDays = (cardioDays + weightliftingDays).size

                val weeklySummary = "$workoutsThisWeek workout${pluralSuffix(workoutsThisWeek)} this week"
                val activeDaysText = "$activeDays active day${pluralSuffix(activeDays)} total"

                val consistency = if (workoutsThisWeek >= 3) "On fire! $workoutsThisWeek workouts"
                else if (workoutsThisWeek > 0) "Getting there: $workoutsThisWeek this week"
                else "Start your weekly streak"

                val nextGoal = when {
                    !user.goal.isNullOrBlank() -> user.goal
                    user.trainingDaysPerWeek != null -> "${user.trainingDaysPerWeek}x/week target"
                    else -> "Set your next goal"
                }

                val runningRecords = buildRunningRecords(workouts, activityTypeNames)
                val cyclingRecords = buildCyclingRecords(workouts, activityTypeNames)

                val workoutsByDay = buildWorkoutsByDay(workouts, weightliftingHistory, startOfWeek)
                val activityBreakdown = buildActivityBreakdown(workouts, weightliftingHistory, activityTypeNames, startOfWeek)
                val distanceByWeek = buildDistanceByWeek(workouts, activityTypeNames, today)

                val achievements = buildList {
                    if (activeDays >= 10) add("Logged workouts on $activeDays different days")
                    if (workoutsThisWeek >= 3) add("Completed $workoutsThisWeek workouts this week")
                    val longestRun = workouts.filter { activityTypeNames[it.activityTypeId] == "Running" }
                        .maxByOrNull { it.distance ?: 0.0 }
                    if (longestRun?.distance != null && longestRun.distance >= 10.0) {
                        add("Ran ${formatDistance(longestRun.distance)} km in a single session")
                    }
                    val longestRide = workouts.filter { activityTypeNames[it.activityTypeId] == "Cycling" }
                        .maxByOrNull { it.distance ?: 0.0 }
                    if (longestRide?.distance != null && longestRide.distance >= 50.0) {
                        add("Cycled ${formatDistance(longestRide.distance)} km in a single session")
                    }
                    if (isEmpty()) add("Complete more workouts to unlock achievements")
                }

                val volumeBySession = weightliftingHistory
                    .sortedBy { it.logDate }
                    .map { item ->
                        mapOf(
                            "date" to item.logDate,
                            "totalSets" to item.totalSets
                        )
                    }

                call.respondTemplate("progress", mapOf(
                    "weeklyWorkouts" to weeklySummary,
                    "activeDays" to activeDaysText,
                    "consistency" to consistency,
                    "nextGoal" to nextGoal,
                    "runningRecords" to runningRecords,
                    "cyclingRecords" to cyclingRecords,
                    "achievements" to achievements,
                    "workoutsByDay" to workoutsByDay,
                    "activityBreakdown" to activityBreakdown,
                    "distanceByWeek" to distanceByWeek,
                    "volumeBySession" to volumeBySession
                ))
            }
        }
    }
}

private data class DayCount(val day: String, val count: Int)
private data class TypeCount(val type: String, val count: Int)
private data class WeekDistance(val week: String, val distance: Double)

private fun buildWorkoutsByDay(
    workouts: List<com.fitness64.schema.WorkoutLog>,
    weightliftingHistory: List<com.fitness64.schema.WeightliftingHistoryItem>,
    startOfWeek: LocalDate
): List<DayCount> {
    val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    val cardioDates = workouts.mapNotNull { runCatching { LocalDate.parse(it.logDate) }.getOrNull() }
    val weightliftingDates = weightliftingHistory.mapNotNull { runCatching { LocalDate.parse(it.logDate) }.getOrNull() }
    val allDates = cardioDates + weightliftingDates

    return dayNames.mapIndexed { index, day ->
        val date = startOfWeek.plusDays(index.toLong())
        val count = allDates.count { it == date }
        DayCount(day = day, count = count)
    }
}

private fun buildActivityBreakdown(
    workouts: List<com.fitness64.schema.WorkoutLog>,
    weightliftingHistory: List<com.fitness64.schema.WeightliftingHistoryItem>,
    activityTypeNames: Map<Int, String>,
    startOfWeek: LocalDate
): List<TypeCount> {
    val today = LocalDate.now()
    val cardioThisWeek = workouts.filter {
        val d = runCatching { LocalDate.parse(it.logDate) }.getOrNull()
        d != null && d >= startOfWeek && d <= today
    }.mapNotNull { activityTypeNames[it.activityTypeId] }

    val weightliftingThisWeek = weightliftingHistory.count {
        val d = runCatching { LocalDate.parse(it.logDate) }.getOrNull()
        d != null && d >= startOfWeek && d <= today
    }

    val counts = cardioThisWeek.groupingBy { it }.eachCount().toMutableMap()
    if (weightliftingThisWeek > 0) {
        counts["Weightlifting"] = (counts["Weightlifting"] ?: 0) + weightliftingThisWeek
    }

    return counts.entries
        .map { TypeCount(type = it.key, count = it.value) }
        .sortedByDescending { it.count }
}

private fun buildDistanceByWeek(
    workouts: List<com.fitness64.schema.WorkoutLog>,
    activityTypeNames: Map<Int, String>,
    today: LocalDate
): List<WeekDistance> {
    val runningOrCycling = workouts.filter {
        val name = activityTypeNames[it.activityTypeId]
        name == "Running" || name == "Cycling"
    }

    val mondayOfCurrentWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)

    val weekRanges = (0 until 4).map { i ->
        val weekStart = mondayOfCurrentWeek.minusWeeks((3 - i).toLong())
        val weekEnd = weekStart.plusDays(6)
        weekStart to weekEnd
    }

    return weekRanges.mapIndexed { i, (start, end) ->
        val distance = runningOrCycling
            .filter {
                val d = runCatching { LocalDate.parse(it.logDate) }.getOrNull()
                d != null && d >= start && d <= end
            }
            .sumOf { it.distance ?: 0.0 }
        val label = when (i) {
            0 -> "3 weeks ago"
            1 -> "2 weeks ago"
            2 -> "Last week"
            3 -> "This week"
            else -> "Week ${i + 1}"
        }
        WeekDistance(week = label, distance = distance)
    }
}
