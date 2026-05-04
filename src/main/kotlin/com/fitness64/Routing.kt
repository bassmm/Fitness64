package com.fitness64

import com.fitness64.activities.ActivityService
import com.fitness64.activities.ActivityType
import com.fitness64.activities.WorkoutLog
import com.fitness64.plans.PlanService
import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

fun Application.configureRouting(
    userService: UserService,
    activityService: ActivityService,
    planService: PlanService
) {
    routing {

        staticResources("/assets", "assets")

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

            val userId = user.id
            if (userId != null && !planService.hasPlan(userId)) {
                call.respondRedirect("/onboarding")
            } else {
                call.respondRedirect("/home")
            }
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

        authenticate("auth-session") {

            get("/logout") {
                call.sessions.clear<UserSession>()
                call.respondRedirect("/login")
            }

            get("/home") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val userId = user.id
                    ?: return@get call.respondRedirect("/login")

                val today = LocalDate.now()
                val todayDate = today.toString()
                val todayDayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

                val todayTraining = planService.getPlanSessionByDay(userId, todayDayName)?.session
                    ?: "No training planned"

                val startOfWeek = getStartOfWeek(today)

                val workoutsThisWeek = activityService.countWorkoutsForUserBetween(
                    userIdValue = userId,
                    startDate = startOfWeek.toString(),
                    endDate = today.toString()
                )

                val consistency = if (workoutsThisWeek > 0) {
                    "$workoutsThisWeek workout${if (workoutsThisWeek == 1) "" else "s"} this week"
                } else {
                    "Start your weekly streak"
                }

                val nextGoal = when {
                    !user.goal.isNullOrBlank() -> user.goal
                    user.trainingDaysPerWeek != null -> "Complete ${user.trainingDaysPerWeek} workouts this week"
                    else -> "Set your next fitness goal"
                }

                val latestWorkout = activityService.getLatestWorkoutSummaryForUser(userId)

                val latestAchievement = latestWorkout?.let {
                    "Latest ${it.activityType} session logged"
                } ?: "Complete your next workout"

                call.respondTemplate(
                    "home",
                    mapOf(
                        "user" to user.name,
                        "today" to todayTraining,
                        "todayDate" to todayDate,
                        "streak" to consistency,
                        "nextGoal" to nextGoal,
                        "achievement" to latestAchievement
                    )
                )
            }

            get("/profile") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val activities = splitPreferredActivities(user.preferredActivities)

                call.respondTemplate(
                    "profile",
                    mapOf(
                        "name" to user.name,
                        "email" to user.email,
                        "fitnessLevel" to (user.fitnessLevel ?: "Not set"),
                        "goals" to (user.goal ?: "No goals set"),
                        "activities" to activities,
                        "community" to if (user.community.isNullOrBlank()) {
                            "No community set"
                        } else {
                            "Training group: ${user.community}"
                        }
                    )
                )
            }

            get("/profile/edit") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                call.respondTemplate(
                    "edit-profile",
                    mapOf(
                        "user" to user,
                        "error" to ""
                    )
                )
            }

            post("/profile/edit") {
                val session = call.principal<UserSession>()!!
                val currentUser = userService.findByEmail(session.email)
                    ?: return@post call.respondRedirect("/login")

                val currentUserId = currentUser.id
                    ?: return@post call.respondRedirect("/login")

                val params = call.receiveParameters()

                val name = params["name"]?.trim().orEmpty()
                val email = params["email"]?.trim().orEmpty()
                val fitnessLevel = params["fitnessLevel"]?.trim()?.ifBlank { null }
                val goal = params["goal"]?.trim()?.ifBlank { null }
                val trainingDaysPerWeek = params["trainingDaysPerWeek"]?.trim()?.toIntOrNull()
                val preferredActivities = params["preferredActivities"]?.trim()?.ifBlank { null }
                val community = params["community"]?.trim()?.ifBlank { null }

                if (name.isBlank() || email.isBlank()) {
                    call.respondTemplate(
                        "edit-profile",
                        mapOf(
                            "user" to currentUser,
                            "error" to "Name and email cannot be empty."
                        )
                    )
                    return@post
                }

                val existingUser = userService.findByEmail(email)

                if (existingUser != null && existingUser.id != currentUserId) {
                    call.respondTemplate(
                        "edit-profile",
                        mapOf(
                            "user" to currentUser,
                            "error" to "This email is already in use."
                        )
                    )
                    return@post
                }

                userService.updateProfile(
                    userId = currentUserId,
                    name = name,
                    email = email,
                    fitnessLevel = fitnessLevel,
                    goal = goal,
                    trainingDaysPerWeek = trainingDaysPerWeek,
                    preferredActivities = preferredActivities,
                    community = community
                )

                call.sessions.set(UserSession(email = email))
                call.respondRedirect("/profile")
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
                    "Gym" -> call.respondRedirect("/weightlifting/log")
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
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@post call.respondRedirect("/login")

                val userId = user.id
                    ?: return@post call.respondRedirect("/login")

                val params = call.receiveParameters()
                val type = params["type"]?.trim().orEmpty()
                val activityDate = params["activityDate"]?.trim().orEmpty()
                val distanceText = params["distance"]?.trim().orEmpty()
                val durationText = params["duration"]?.trim().orEmpty()
                val notes = params["notes"]?.trim().orEmpty()

                val duration = durationText.toIntOrNull()
                val distance = distanceText.toDoubleOrNull()

                if (type.isBlank() || activityDate.isBlank() || duration == null) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Please complete the required fields with a valid duration.",
                            "selectedType" to type,
                            "activityDate" to activityDate,
                            "distance" to distanceText,
                            "duration" to durationText,
                            "notes" to notes
                        )
                    )
                    return@post
                }

                if (duration <= 0) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Duration must be greater than 0.",
                            "selectedType" to type,
                            "activityDate" to activityDate,
                            "distance" to distanceText,
                            "duration" to durationText,
                            "notes" to notes
                        )
                    )
                    return@post
                }

                val activityTypeId = activityService.getActivityTypeByName(type)
                    ?: activityService.createActivityType(ActivityType(type))

                activityService.createWorkoutLog(
                    WorkoutLog(
                        userId = userId,
                        activityTypeId = activityTypeId,
                        logDate = activityDate,
                        duration = duration,
                        distance = distance,
                        notes = notes.ifBlank { null },
                        calories = null,
                        source = "manual"
                    )
                )

                loggedActivities.add(
                    LoggedActivity(
                        date = activityDate,
                        type = type,
                        duration = durationText,
                        distance = distanceText,
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
                    "gym" -> call.respondRedirect("/weightlifting/log")
                    "running" -> call.respondRedirect("/log/details?type=Running&date=$todayDate")
                    "cycling" -> call.respondRedirect("/log/details?type=Cycling&date=$todayDate")
                    "swimming" -> call.respondRedirect("/log/details?type=Swimming&date=$todayDate")
                    else -> call.respondRedirect("/log")
                }
            }

            get("/calendar") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val userId = user.id
                    ?: return@get call.respondRedirect("/login")

                val today = LocalDate.now()
                val startOfWeek = getStartOfWeek(today)
                val planFromDatabase = planService.getPlan(userId)

                val planByDay = planFromDatabase.associateBy { it.day }

                val calendarItems = (0..6).map { index ->
                    val date = startOfWeek.plusDays(index.toLong())
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val plannedSession = planByDay[dayName]?.session ?: ""
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

            // --- Race routes ---

            get("/races") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                if (user == null) {
                    call.respondRedirect("/login")
                    return@get
                }

                call.respondTemplate(
                    "races",
                    mapOf("races" to emptyList<Any>())
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

fun splitPreferredActivities(preferredActivities: String?): List<String> {
    return preferredActivities
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
}