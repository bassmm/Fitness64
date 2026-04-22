package com.fitness64

import com.fitness64.users.User
import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

fun Application.configureRouting(userService: UserService) {
    routing {

        // Public routes
        get("/") {
            call.respondRedirect("/login")
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

        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                call.respondTemplate(
                    "login",
                    mapOf(
                        "error" to "Please enter both email and password.",
                        "email" to email
                    )
                )
                return@post
            }

            val user = userService.findByEmail(email)

            if (user == null || !BCrypt.checkpw(password, user.passwordHash)) {
                call.respondTemplate(
                    "login",
                    mapOf(
                        "error" to "Invalid email or password.",
                        "email" to email
                    )
                )
                return@post
            }

            call.sessions.set(UserSession(email = email))
            call.respondRedirect("/home")
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

        post("/register") {
            val params = call.receiveParameters()
            val name = params["name"]?.trim().orEmpty()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()
            val fitnessLevel = params["fitnessLevel"]?.trim().orEmpty()

            if (name.isBlank() || email.isBlank() || password.isBlank() || fitnessLevel.isBlank()) {
                call.respondTemplate(
                    "register",
                    mapOf(
                        "error" to "Please complete all required fields.",
                        "name" to name,
                        "email" to email
                    )
                )
                return@post
            }

            if (userService.findByEmail(email) != null) {
                call.respondTemplate(
                    "register",
                    mapOf(
                        "error" to "An account with this email already exists.",
                        "name" to name,
                        "email" to email
                    )
                )
                return@post
            }

            val newUser = com.fitness64.users.User(
                name = name,
                email = email,
                passwordHash = password,
                fitnessLevel = fitnessLevel
            )

            try {
                userService.create(newUser)
                call.respondRedirect("/login")
            } catch (e: Exception) {
                call.respondTemplate(
                    "register",
                    mapOf(
                        "error" to "An error occurred during registration. Please try again.",
                        "name" to name,
                        "email" to email
                    )
                )
            }
        }

        // Protected routes
        authenticate("auth-session") {
            get("/logout") {
                call.sessions.clear<UserSession>()
                call.respondRedirect("/login")
            }

            get("/home") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                val today = LocalDate.now()
                val todayDate = today.toString()
                val todayDayName = today.dayOfWeek.getDisplayName(
                    TextStyle.FULL,
                    Locale.ENGLISH
                )
                val todayTraining = "Rest"

                call.respondTemplate(
                    "home",
                    mapOf(
                        "user" to (user?.name ?: session.email),
                        "today" to todayTraining,
                        "todayDate" to todayDate,
                        "streak" to "3-week streak",
                        "nextGoal" to "Complete 6 workouts next week",
                        "achievement" to "New 5km personal best"
                    )
                )
            }

            get("/profile") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                call.respondTemplate(
                    "profile",
                    mapOf(
                        "name" to (user?.name ?: session.email),
                        "email" to session.email,
                        "fitnessLevel" to (user?.fitnessLevel ?: "Not set"),
                        "goals" to (user?.goal ?: "No goals set"),
                        "activities" to listOf("Running", "Cycling", "Gym"),
                        "community" to "Training group: Leeds Runners"
                    )
                )
            }

            get("/log") {
                val selectedType = call.request.queryParameters["type"] ?: ""
                val activityDate = call.request.queryParameters["date"] ?: LocalDate.now().toString()

                call.respondTemplate(
                    "log",
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

            post("/log") {
                val session = call.principal<UserSession>()!!
                val params = call.receiveParameters()
                val type = params["type"]?.trim().orEmpty()
                val activityDate = params["activityDate"]?.trim().orEmpty()
                val distance = params["distance"]?.trim().orEmpty()
                val duration = params["duration"]?.trim().orEmpty()
                val notes = params["notes"]?.trim().orEmpty()

                if (type.isBlank() || activityDate.isBlank() || duration.isBlank()) {
                    call.respondTemplate(
                        "log",
                        mapOf(
                            "error" to "Please complete the required fields.",
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

                call.respondRedirect("/calendar")
            }

            get("/quick-log") {
                call.respondTemplate(
                    "quick-log",
                    mapOf("error" to "")
                )
            }

            post("/quick-log") {
                val params = call.receiveParameters()
                val type = params["type"]?.trim().orEmpty()

                if (type.isBlank()) {
                    call.respondTemplate(
                        "quick-log",
                        mapOf("error" to "Please select an activity type.")
                    )
                    return@post
                }

                val todayDate = LocalDate.now().toString()

                when (type.lowercase()) {
                    "running" -> call.respondRedirect("/log?type=Running&date=$todayDate")
                    "cycling" -> call.respondRedirect("/log?type=Cycling&date=$todayDate")
                    "gym" -> call.respondRedirect("/log?type=Gym&date=$todayDate")
                    "swimming" -> call.respondRedirect("/log?type=Swimming&date=$todayDate")
                    else -> call.respondRedirect("/log?date=$todayDate")
                }
            }

            get("/plan") {
                val startOfWeek = getStartOfWeek(LocalDate.now())
                val weeklyPlan = weeklyPlanData.entries.mapIndexed { index, entry ->
                    val date = startOfWeek.plusDays(index.toLong())
                    mapOf(
                        "day" to entry.key,
                        "date" to date.toString(),
                        "session" to entry.value
                    )
                }

                call.respondTemplate(
                    "plan",
                    mapOf("weeklyPlan" to weeklyPlan)
                )
            }

            get("/plan/replace") {
                val day = call.request.queryParameters["day"]?.trim().orEmpty()

                if (day.isBlank() || !weeklyPlanData.containsKey(day)) {
                    call.respondRedirect("/plan")
                    return@get
                }

                call.respondTemplate(
                    "replace-session",
                    mapOf(
                        "error" to "",
                        "day" to day,
                        "currentSession" to weeklyPlanData[day].orEmpty()
                    )
                )
            }

            post("/plan/replace") {
                val params = call.receiveParameters()
                val day = params["day"]?.trim().orEmpty()
                val newSession = params["newSession"]?.trim().orEmpty()

                if (day.isBlank() || !weeklyPlanData.containsKey(day)) {
                    call.respondRedirect("/plan")
                    return@post
                }

                if (newSession.isBlank()) {
                    call.respondTemplate(
                        "replace-session",
                        mapOf(
                            "error" to "Please select a replacement session.",
                            "day" to day,
                            "currentSession" to weeklyPlanData[day].orEmpty()
                        )
                    )
                    return@post
                }

                weeklyPlanData[day] = newSession
                call.respondRedirect("/calendar")
            }

            get("/calendar") {
                val today = LocalDate.now()
                val startOfWeek = getStartOfWeek(today)

                val calendarItems = (0..6).map { index ->
                    val date = startOfWeek.plusDays(index.toLong())
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val plannedSession = weeklyPlanData[dayName].orEmpty()
                    val loggedForDate = loggedActivities.filter { it.date == date.toString() }

                    mapOf(
                        "day" to dayName,
                        "date" to date.toString(),
                        "planned" to plannedSession,
                        "isToday" to (date == today),
                        "logged" to loggedForDate.map {
                            mapOf(
                                "type" to it.type,
                                "duration" to it.duration,
                                "distance" to it.distance,
                                "notes" to it.notes
                            )
                        }
                    )
                }

                call.respondTemplate(
                    "calendar",
                    mapOf("calendarItems" to calendarItems)
                )
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
        }
    }
}

// Demo data structures and helpers for UI development
data class LoggedActivity(
    val date: String,
    val type: String,
    val duration: String,
    val distance: String,
    val notes: String
)

val loggedActivities = mutableListOf<LoggedActivity>()

val weeklyPlanData = mutableMapOf(
    "Monday" to "Upper Body Strength",
    "Tuesday" to "Cardio & Core",
    "Wednesday" to "Lower Body Strength",
    "Thursday" to "Rest",
    "Friday" to "Full Body",
    "Saturday" to "Long Run",
    "Sunday" to "Yoga & Recovery"
)

fun getStartOfWeek(date: LocalDate): LocalDate {
    return date.minusDays(date.dayOfWeek.value.toLong() - 1)
}
