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
                    "volumeBySession" to volumeBySession
                ))
            }
        }
    }
}
