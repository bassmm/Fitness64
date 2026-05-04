package com.fitness64

import com.fitness64.activities.ActivityService
import com.fitness64.activities.ActivityType
import com.fitness64.activities.WorkoutLog
import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.LocalDate
fun Application.configureRouting(userService: UserService, activityService: ActivityService) {
    routing {

        staticResources("/assets", "assets")

        get("/") {
            call.respondRedirect("/home")
        }

        get("/login") {
            call.respondTemplate(
                "login",
                mapOf(
                    "error" to "",
                    "email" to ""
                )
            )
        }

        get("/register") {
            call.respondTemplate(
                "register",
                mapOf(
                    "error" to "",
                    "name" to "",
                    "email" to ""
                )
            )
        }

        authenticate("auth-session") {

            get("/logout") {
                call.sessions.clear<UserSession>()
                call.respondRedirect("/login")
            }

            get("/log") {
                val activityDate = call.request.queryParameters["date"] ?: LocalDate.now().toString()

                call.respondTemplate(
                    "log",
                    mapOf(
                        "error" to "",
                        "activityDate" to activityDate
                    )
                )
            }

            get("/log/redirect") {
                val activityType = call.request.queryParameters["activityType"]?.trim().orEmpty()
                val activityDate = call.request.queryParameters["activityDate"] ?: LocalDate.now().toString()

                if (activityType.isBlank()) {
                    call.respondTemplate(
                        "log",
                        mapOf(
                            "error" to "Please select an activity type.",
                            "activityDate" to activityDate
                        )
                    )
                    return@get
                }

                when (activityType) {
                    "Weightlifting" -> call.respondRedirect("/weightlifting/log")
                    "Running", "Cycling", "Swimming" ->
                        call.respondRedirect("/log/details?type=$activityType&date=$activityDate")

                    else -> call.respondRedirect("/log")
                }
            }

            get("/log/details") {
                val selectedType = call.request.queryParameters["type"]?.trim().orEmpty()
                val activityDate = call.request.queryParameters["date"] ?: LocalDate.now().toString()

                if (selectedType.isBlank()) {
                    call.respondRedirect("/log")
                    return@get
                }

                call.respondTemplate(
                    "log-details",
                    mapOf(
                        "error" to "",
                        "selectedType" to selectedType,
                        "activityDate" to activityDate,
                        "distance" to "",
                        "duration" to "",
                        "notes" to ""
                    )
                )
            }

            post("/log/details") {
                val params = call.receiveParameters()
                val type = params["type"]?.trim().orEmpty()
                val activityDate = params["activityDate"]?.trim().orEmpty()
                val distance = params["distance"]?.trim().orEmpty()
                val duration = params["duration"]?.trim().orEmpty()
                val notes = params["notes"]?.trim().orEmpty()

                val durationMinutes = duration.toIntOrNull()
                if (type.isBlank() || activityDate.isBlank() || durationMinutes == null || durationMinutes <= 0) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Please complete the required fields with valid values.",
                            "selectedType" to type,
                            "activityDate" to activityDate,
                            "distance" to distance,
                            "duration" to duration,
                            "notes" to notes
                        )
                    )
                    return@post
                }

                val distanceKm = if (distance.isBlank()) null else distance.toDoubleOrNull()
                if (distance.isNotBlank() && (distanceKm == null || distanceKm < 0.0)) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Distance must be a valid non-negative number.",
                            "selectedType" to type,
                            "activityDate" to activityDate,
                            "distance" to distance,
                            "duration" to duration,
                            "notes" to notes
                        )
                    )
                    return@post
                }

                loggedActivities.add(
                    LoggedActivity(
                        date = activityDate,
                        type = type,
                        duration = duration,
                        distance = distance,
                        notes = notes
                    )
                )

                val session = call.principal<UserSession>()
                val user = session?.let { userService.findByEmail(it.email) }
                val userId = user?.id

                if (userId != null) {
                    val activityTypeId = activityService.getActivityTypeByName(type)
                        ?: activityService.createActivityType(ActivityType(type))

                    activityService.createWorkoutLog(
                        WorkoutLog(
                            userId = userId,
                            activityTypeId = activityTypeId,
                            logDate = activityDate,
                            duration = durationMinutes,
                            distance = distanceKm,
                            notes = notes.ifBlank { null },
                            calories = null,
                            source = "manual"
                        )
                    )
                }

                call.respondRedirect("/calendar")
            }

            get("/quick-log") {
                call.respondRedirect("/log")
            }

            get("/progress") {
                val achievements = listOf(
                    "Completed 5 workouts this week",
                    "New 5km personal best",
                    "Logged activity for 3 consecutive days"
                )

                call.respondTemplate(
                    "progress",
                    mapOf(
                        "weeklyWorkouts" to "5 workouts completed",
                        "activeDays" to "4 active days",
                        "pbRun" to "5km: 25 min",
                        "pbCycle" to "10km: 32 min",
                        "consistency" to "3-week streak",
                        "nextGoal" to "Complete 6 workouts next week",
                        "achievements" to achievements
                    )
                )
            }

            get("/pebble-index") {
                call.respondTemplate(
                    "pebble-index",
                    mapOf(
                        "user" to mapOf("name" to "John")
                    )
                )
            }

            get("/races/log") {
                call.respondTemplate(
                    "race-log",
                    mapOf(
                        "error" to "",
                        "eventName" to "",
                        "eventDate" to "",
                        "location" to "",
                        "category" to "",
                        "finishTime" to "",
                        "overallRank" to "",
                        "categoryRank" to "",
                        "certificateUrl" to "",
                        "isPersonalBest" to false
                    )
                )
            }

            post("/races/log") {
                val params = call.receiveParameters()
                val eventName = params["eventName"]?.trim().orEmpty()
                val eventDate = params["eventDate"]?.trim().orEmpty()
                val location = params["location"]?.trim().orEmpty()
                val category = params["category"]?.trim().orEmpty()
                val finishTime = params["finishTime"]?.trim().orEmpty()
                val overallRank = params["overallRank"]?.trim()?.toIntOrNull()
                val categoryRank = params["categoryRank"]?.trim()?.toIntOrNull()
                val certificateUrl = params["certificateUrl"]?.trim().orEmpty()
                val isPersonalBest = params["isPersonalBest"] == "true"

                if (eventName.isBlank() || eventDate.isBlank()) {
                    call.respondTemplate(
                        "race-log",
                        mapOf(
                            "error" to "Please enter the race name and date.",
                            "eventName" to eventName,
                            "eventDate" to eventDate,
                            "location" to location,
                            "category" to category,
                            "finishTime" to finishTime,
                            "overallRank" to (overallRank ?: ""),
                            "categoryRank" to (categoryRank ?: ""),
                            "certificateUrl" to certificateUrl,
                            "isPersonalBest" to isPersonalBest
                        )
                    )
                    return@post
                }

                call.respondRedirect("/races")
            }
        }
    }
}

data class LoggedActivity(
    val date: String,
    val type: String,
    val duration: String,
    val distance: String,
    val notes: String
)

val loggedActivities = mutableListOf<LoggedActivity>()

fun getStartOfWeek(date: LocalDate): LocalDate {
    return date.minusDays(date.dayOfWeek.value.toLong() - 1)
}






