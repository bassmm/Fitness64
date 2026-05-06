package com.fitness64.routes

import com.fitness64.core.formatClockDuration
import com.fitness64.core.formatDistance
import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.schema.ActivityService
import com.fitness64.schema.PlanService
import com.fitness64.schema.RaceService
import com.fitness64.schema.UserService
import com.fitness64.schema.WeightliftingService
import com.fitness64.schema.WeightliftingHistoryItem
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

data class CalendarItem(val date: String, val day: String, val isToday: Boolean, val planned: String?, val logged: List<CalendarActivity>)
data class CalendarActivity(val type: String, val duration: String, val distance: String?, val notes: String?)

fun Application.configureCalendarRoutes(
    userService: UserService,
    activityService: ActivityService,
    weightliftingService: WeightliftingService,
    raceService: RaceService,
    planService: PlanService
) {
    routing {
        authenticate("auth-session") {
            get("/calendar") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@get

                val workouts = activityService.getWorkoutsForUser(userId)
                val weightliftingHistory = weightliftingService.getWeightliftingHistory(userId)
                val plan = planService.getPlan(userId)

                val allDates = mutableSetOf<String>()
                allDates.addAll(workouts.map { it.logDate })
                allDates.addAll(weightliftingHistory.map { it.logDate })

                val calendarItems = allDates
                    .sortedDescending()
                    .take(30)
                    .map { dateStr ->
                        val localDate = runCatching { LocalDate.parse(dateStr) }.getOrNull()
                        val dayName = localDate?.dayOfWeek?.getDisplayName(TextStyle.FULL, Locale.ENGLISH) ?: dateStr
                        val isToday = dateStr == LocalDate.now().toString()

                        val planned = plan.find { it.day.equals(dayName, ignoreCase = true) }?.session

                        val cardioActivities = workouts
                            .filter { it.logDate == dateStr }
                            .map { workout ->
                                val typeName = activityService.getActivityTypeName(workout.activityTypeId) ?: workout.source ?: "Activity"
                                val displayName = workout.name?.takeIf { it.isNotBlank() } ?: "$typeName Session"
                                CalendarActivity(
                                    type = displayName,
                                    duration = formatClockDuration(workout.duration.toDouble()),
                                    distance = workout.distance?.let { formatDistance(it) + " km" },
                                    notes = workout.notes
                                )
                            }

                        val weightliftingActivities = weightliftingHistory
                            .filter { it.logDate == dateStr }
                            .map { session ->
                                val displayName = session.name?.takeIf { it.isNotBlank() } ?: "Weightlifting Session"
                                CalendarActivity(
                                    type = displayName,
                                    duration = formatClockDuration(session.duration.toDouble()),
                                    distance = null,
                                    notes = "${session.totalSets} sets${if (session.notes.isNullOrBlank()) "" else " - ${session.notes}"}"
                                )
                            }

                        val logged = cardioActivities + weightliftingActivities

                        CalendarItem(dateStr, dayName, isToday, planned, logged)
                    }

                call.respondTemplate("calendar", mapOf("calendarItems" to calendarItems))
            }
        }
    }
}
