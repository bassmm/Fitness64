package com.comp2850

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

private val weeklyPlanData = linkedMapOf(
    "Monday" to "Run 5km",
    "Tuesday" to "Gym",
    "Wednesday" to "Rest",
    "Thursday" to "Cycling 10km",
    "Friday" to "Gym",
    "Saturday" to "Long Run",
    "Sunday" to "Rest"
)

fun Application.configureRouting() {

    routing {

        // Redirect the root path to the login page
        get("/") {
            call.respondRedirect("/login")
        }

        // Render the login page
        get("/login") {
            call.respondTemplate(
                "login",
                mapOf(
                    "error" to ""
                )
            )
        }

        // Process login form submission
        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                call.respondTemplate(
                    "login",
                    mapOf("error" to "Please enter both email and password.")
                )
                return@post
            }

            // Placeholder authentication logic
            call.respondRedirect("/home")
        }

        // Render the register page
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

        // Process register form submission
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

            // Placeholder registration logic
            call.respondRedirect("/login")
        }

        // Render the home dashboard
        get("/home") {
            call.respondTemplate(
                "home",
                mapOf(
                    "user" to "Sarah",
                    "today" to "Run 5km",
                    "streak" to "3-week streak",
                    "nextGoal" to "Complete 6 workouts next week",
                    "achievement" to "New 5km personal best"
                )
            )
        }

        // Render the profile page
        get("/profile") {
            call.respondTemplate(
                "profile",
                mapOf(
                    "name" to "Sarah",
                    "email" to "sarah@example.com",
                    "fitnessLevel" to "Intermediate",
                    "goals" to "Complete a 10k race",
                    "activities" to listOf("Running", "Cycling", "Gym"),
                    "community" to "Training group: Leeds Runners"
                )
            )
        }

        // Render the full activity logging page
        get("/log") {
            val selectedType = call.request.queryParameters["type"] ?: ""

            call.respondTemplate(
                "log",
                mapOf(
                    "error" to "",
                    "selectedType" to selectedType,
                    "distance" to "",
                    "duration" to "",
                    "notes" to ""
                )
            )
        }

        // Process full activity log submission
        post("/log") {
            val params = call.receiveParameters()
            val type = params["type"]?.trim().orEmpty()
            val distance = params["distance"]?.trim().orEmpty()
            val duration = params["duration"]?.trim().orEmpty()
            val notes = params["notes"]?.trim().orEmpty()

            if (type.isBlank() || duration.isBlank()) {
                call.respondTemplate(
                    "log",
                    mapOf(
                        "error" to "Please complete the required fields.",
                        "selectedType" to type,
                        "distance" to distance,
                        "duration" to duration,
                        "notes" to notes
                    )
                )
                return@post
            }

            // Placeholder save logic
            println("Activity saved: type=$type, distance=$distance, duration=$duration, notes=$notes")

            call.respondRedirect("/home")
        }

        // Render the quick log selection page
        get("/quick-log") {
            call.respondTemplate(
                "quick-log",
                mapOf("error" to "")
            )
        }

        // Process quick log selection and redirect accordingly
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

            when (type.lowercase()) {
                "running" -> call.respondRedirect("/log?type=Running")
                "cycling" -> call.respondRedirect("/log?type=Cycling")
                "gym" -> call.respondRedirect("/log?type=Gym")
                "swimming" -> call.respondRedirect("/log?type=Swimming")
                else -> call.respondRedirect("/log")
            }
        }

        // Render the weekly training plan page
        get("/plan") {
            val weeklyPlan = weeklyPlanData.map { (day, session) ->
                mapOf("day" to day, "session" to session)
            }

            call.respondTemplate(
                "plan",
                mapOf("weeklyPlan" to weeklyPlan)
            )
        }

        // Render the replace session page
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

        // Process replace session submission
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
            call.respondRedirect("/plan")
        }

        // Render the progress page
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

        // Optional Pebble test page
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