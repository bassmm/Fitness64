package com.comp2850

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val registeredUsers = mutableMapOf(
    "sarah@example.com" to DemoUser(
        name = "Sarah",
        email = "sarah@example.com",
        password = "123456",
        fitnessLevel = "Intermediate"
    )
)

private val weeklyPlanData = linkedMapOf(
    "Monday" to "Run 5km",
    "Tuesday" to "Gym",
    "Wednesday" to "Rest",
    "Thursday" to "Cycling 10km",
    "Friday" to "Gym",
    "Saturday" to "Long Run",
    "Sunday" to "Rest"
)

private val loggedActivities = mutableListOf<LoggedActivity>()

data class DemoUser(
    val name: String,
    val email: String,
    val password: String,
    val fitnessLevel: String
)

data class LoggedActivity(
    val date: String,
    val type: String,
    val duration: String,
    val distance: String,
    val notes: String
)

fun getStartOfWeek(today: LocalDate): LocalDate {
    return today.with(DayOfWeek.MONDAY)
}

fun Application.configureRouting() {

    routing {

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

            val user = registeredUsers[email]

            if (user == null || user.password != password) {
                call.respondTemplate(
                    "login",
                    mapOf(
                        "error" to "Invalid email or password.",
                        "email" to email
                    )
                )
                return@post
            }

            call.sessions.set(
                UserSession(
                    name = user.name,
                    email = user.email,
                    fitnessLevel = user.fitnessLevel
                )
            )

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

            if (registeredUsers.containsKey(email)) {
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

            registeredUsers[email] = DemoUser(
                name = name,
                email = email,
                password = password,
                fitnessLevel = fitnessLevel
            )

            call.respondRedirect("/login")
        }

        get("/logout") {
            call.sessions.clear<UserSession>()
            call.respondRedirect("/login")
        }

        get("/home") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

            val today = LocalDate.now()
            val todayDate = today.toString()
            val todayDayName = today.dayOfWeek.getDisplayName(
                TextStyle.FULL,
                Locale.ENGLISH
            )
            val todayTraining = weeklyPlanData[todayDayName] ?: "Rest"

            call.respondTemplate(
                "home",
                mapOf(
                    "user" to session.name,
                    "today" to todayTraining,
                    "todayDate" to todayDate,
                    "streak" to "3-week streak",
                    "nextGoal" to "Complete 6 workouts next week",
                    "achievement" to "New 5km personal best"
                )
            )
        }

        get("/profile") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

            call.respondTemplate(
                "profile",
                mapOf(
                    "name" to session.name,
                    "email" to session.email,
                    "fitnessLevel" to session.fitnessLevel,
                    "goals" to "Complete a 10k race",
                    "activities" to listOf("Running", "Cycling", "Gym"),
                    "community" to "Training group: Leeds Runners"
                )
            )
        }

        get("/log") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@post
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

            call.respondTemplate(
                "quick-log",
                mapOf("error" to "")
            )
        }

        post("/quick-log") {
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@post
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@post
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

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
            val session = call.sessions.get<UserSession>()
            if (session == null) {
                call.respondRedirect("/login")
                return@get
            }

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