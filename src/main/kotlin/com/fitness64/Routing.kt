package com.fitness64

import com.fitness64.activities.ActivityService
import com.fitness64.activities.WorkoutLog
import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*
import kotlin.math.roundToInt

fun Application.configureRouting(
    userService: UserService,
    activityService: ActivityService
) {
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
                val todayDayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                val todayTraining = weeklyPlanData[todayDayName] ?: "Rest"

                call.respondTemplate(
                    "home",
                    mapOf(
                        "user" to (user?.name ?: session.email),
                        "today" to todayTraining,
                        "todayDate" to todayDate,
                        "streak" to "View your progress dashboard for consistency stats",
                        "nextGoal" to "Log a workout this week",
                        "achievement" to "Check Progress for your latest records"
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

            // Step 1: activity type selection page
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

            // Step 2: redirect by chosen activity type
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

            // Step 3: generic details page for non-gym activities
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
                val params = call.receiveParameters()

                val type = params["type"]?.trim().orEmpty()
                val activityDate = params["activityDate"]?.trim().orEmpty()
                val distance = params["distance"]?.trim().orEmpty()
                val duration = params["duration"]?.trim().orEmpty()
                val notes = params["notes"]?.trim().orEmpty()

                val parsedDate = parseActivityDate(activityDate)
                val durationMinutes = parseDurationMinutes(duration)

                if (
                    type.isBlank() ||
                    activityDate.isBlank() ||
                    parsedDate == null ||
                    durationMinutes == null
                ) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Please enter a valid activity type, date, and duration.",
                            "selectedType" to type,
                            "activityDate" to activityDate,
                            "distance" to distance,
                            "duration" to duration,
                            "notes" to notes
                        )
                    )
                    return@post
                }

                val user = userService.findByEmail(session.email)
                val userId = user?.id

                if (userId == null) {
                    call.sessions.clear<UserSession>()
                    call.respondRedirect("/login")
                    return@post
                }

                try {
                    val activityTypeId = activityService.getOrCreateActivityType(type)

                    activityService.createWorkoutLog(
                        WorkoutLog(
                            userId = userId,
                            activityTypeId = activityTypeId,
                            logDate = parsedDate.toString(),
                            duration = durationMinutes,
                            distance = parseDistanceKm(distance),
                            notes = notes.ifBlank { null },
                            calories = null,
                            source = "manual"
                        )
                    )

                    call.respondRedirect("/calendar")
                } catch (e: Exception) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Could not save activity. Please try again.",
                            "selectedType" to type,
                            "activityDate" to activityDate,
                            "distance" to distance,
                            "duration" to duration,
                            "notes" to notes
                        )
                    )
                }
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
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                val userId = user?.id

                if (userId == null) {
                    call.sessions.clear<UserSession>()
                    call.respondRedirect("/login")
                    return@get
                }

                val workouts = activityService.getWorkoutsForUser(userId)
                val activityTypeNames = loadActivityTypeNames(activityService, workouts)

                val today = LocalDate.now()
                val startOfWeek = getStartOfWeek(today)

                val calendarItems = (0..6).map { index ->
                    val date = startOfWeek.plusDays(index.toLong())
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val plannedSession = weeklyPlanData[dayName].orEmpty()
                    val loggedForDate = workouts.filter { it.logDate == date.toString() }

                    mapOf(
                        "day" to dayName,
                        "date" to date.toString(),
                        "planned" to plannedSession,
                        "isToday" to (date == today),
                        "logged" to loggedForDate.map {
                            mapOf(
                                "type" to (activityTypeNames[it.activityTypeId] ?: "Activity"),
                                "duration" to formatDuration(it.duration),
                                "distance" to formatWorkoutDistance(it.distance),
                                "notes" to it.notes.orEmpty()
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
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                val userId = user?.id

                if (userId == null) {
                    call.sessions.clear<UserSession>()
                    call.respondRedirect("/login")
                    return@get
                }

                val workouts = activityService.getWorkoutsForUser(userId)
                val activityTypeNames = loadActivityTypeNames(activityService, workouts)

                val today = LocalDate.now()
                val startOfWeek = getStartOfWeek(today)

                val weeklyWorkouts = workouts.filter { workout ->
                    val workoutDate = parseActivityDate(workout.logDate)

                    workoutDate != null &&
                            !workoutDate.isBefore(startOfWeek) &&
                            !workoutDate.isAfter(today)
                }

                val totalWorkouts = weeklyWorkouts.size

                val activeDays = weeklyWorkouts
                    .map { it.logDate }
                    .distinct()
                    .size

                val mostLoggedActivity = weeklyWorkouts
                    .groupingBy { workout ->
                        activityTypeNames[workout.activityTypeId] ?: "Activity"
                    }
                    .eachCount()
                    .maxByOrNull { it.value }
                    ?.let { "${it.key} (${it.value})" }
                    ?: "No activities logged yet"

                val runningRecords = buildRunningRecords(workouts, activityTypeNames)
                val cyclingRecords = buildCyclingRecords(workouts, activityTypeNames)

                val achievements = if (weeklyWorkouts.isEmpty()) {
                    listOf("No workouts logged this week yet")
                } else {
                    listOf(
                        "Logged $totalWorkouts workout${pluralSuffix(totalWorkouts)} this week",
                        "Active on $activeDays day${pluralSuffix(activeDays)} this week",
                        "Most logged activity: $mostLoggedActivity"
                    )
                }

                call.respondTemplate(
                    "progress",
                    mapOf(
                        "weeklyWorkouts" to "$totalWorkouts workout${pluralSuffix(totalWorkouts)} completed this week",
                        "activeDays" to "$activeDays active day${pluralSuffix(activeDays)}",
                        "runningRecords" to runningRecords,
                        "cyclingRecords" to cyclingRecords,
                        "consistency" to "$activeDays / 7 days active",
                        "nextGoal" to if (totalWorkouts == 0) {
                            "Log your first workout this week"
                        } else {
                            "Reach ${totalWorkouts + 1} workouts this week"
                        },
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
val weeklyPlanData = mutableMapOf(
    "Monday" to "Upper Body Strength",
    "Tuesday" to "Cardio & Core",
    "Wednesday" to "Lower Body Strength",
    "Thursday" to "Rest",
    "Friday" to "Full Body",
    "Saturday" to "Long Run",
    "Sunday" to "Yoga & Recovery"
)

const val KM_PER_MILE = 1.60934
const val HALF_MARATHON_KM = 21.0975
const val MARATHON_KM = 42.195

data class ProgressRecord(
    val label: String,
    val value: String
)

data class DistanceBenchmark(
    val label: String,
    val distanceKm: Double
)

fun getStartOfWeek(date: LocalDate): LocalDate {
    return date.minusDays(date.dayOfWeek.value.toLong() - 1)
}

fun parseActivityDate(value: String): LocalDate? {
    return runCatching {
        LocalDate.parse(value)
    }.getOrNull()
}

fun parseDistanceKm(value: String): Double? {
    if (value.isBlank()) {
        return null
    }

    val number = Regex("""\d+(\.\d+)?""")
        .find(value)
        ?.value
        ?.toDoubleOrNull()
        ?: return null

    val lowerValue = value.lowercase()

    return if (
        lowerValue.contains("mile") ||
        Regex("""\bmi\b""").containsMatchIn(lowerValue)
    ) {
        number * KM_PER_MILE
    } else {
        number
    }
}

fun parseDurationMinutes(value: String): Int? {
    val trimmed = value.trim().lowercase()

    if (trimmed.isBlank()) {
        return null
    }

    if (trimmed.contains(":")) {
        val parts = trimmed.split(":")

        if (parts.size != 3 || parts.any { part -> part.toIntOrNull() == null }) {
            return null
        }

        val hours = parts[0].toInt()
        val minutes = parts[1].toInt()
        val seconds = parts[2].toInt()

        if (minutes !in 0..59 || seconds !in 0..59) {
            return null
        }

        val totalMinutes = hours * 60 + minutes + if (seconds >= 30) 1 else 0
        return totalMinutes.takeIf { it > 0 }
    }

    val hours = Regex("""(\d+)\s*(h|hr|hrs|hour|hours)\b""")
        .find(trimmed)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: 0

    val minutes = Regex("""(\d+)\s*(m|min|mins|minute|minutes)\b""")
        .find(trimmed)
        ?.groupValues
        ?.get(1)
        ?.toIntOrNull()
        ?: 0

    if (hours > 0 || minutes > 0) {
        val totalMinutes = hours * 60 + minutes
        return totalMinutes.takeIf { it > 0 }
    }

    val plainNumber = Regex("""\d+""")
        .find(trimmed)
        ?.value
        ?.toIntOrNull()

    return plainNumber?.takeIf { it > 0 }
}

fun formatDuration(durationMinutes: Int): String {
    return "$durationMinutes min"
}

fun formatWorkoutDistance(distance: Double?): String {
    return distance?.let { "${formatDistance(it)} km" }.orEmpty()
}

fun formatDistance(distance: Double): String {
    return if (distance % 1.0 == 0.0) {
        distance.toInt().toString()
    } else {
        "%.2f"
            .format(Locale.UK, distance)
            .trimEnd('0')
            .trimEnd('.')
    }
}

fun formatClockDuration(minutes: Double): String {
    val totalSeconds = (minutes * 60).roundToInt()

    val hours = totalSeconds / 3600
    val remainingSecondsAfterHours = totalSeconds % 3600
    val mins = remainingSecondsAfterHours / 60
    val seconds = remainingSecondsAfterHours % 60

    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, mins, seconds)
    } else {
        "%d:%02d".format(mins, seconds)
    }
}

fun pluralSuffix(count: Int): String {
    return if (count == 1) "" else "s"
}

suspend fun loadActivityTypeNames(
    activityService: ActivityService,
    workouts: List<WorkoutLog>
): Map<Int, String> {
    val names = mutableMapOf<Int, String>()

    for (activityTypeId in workouts.map { it.activityTypeId }.distinct()) {
        names[activityTypeId] = activityService.getActivityTypeName(activityTypeId) ?: "Activity"
    }

    return names
}

fun buildRunningRecords(
    workouts: List<WorkoutLog>,
    activityTypeNames: Map<Int, String>
): List<ProgressRecord> {
    val runs = workoutsForActivityType(workouts, activityTypeNames, "Running")

    val benchmarkRecords = listOf(
        DistanceBenchmark("Fastest 1 km", 1.0),
        DistanceBenchmark("Fastest 1 mile", KM_PER_MILE),
        DistanceBenchmark("Fastest 5 km", 5.0),
        DistanceBenchmark("Fastest 10 km", 10.0),
        DistanceBenchmark("Fastest half marathon", HALF_MARATHON_KM),
        DistanceBenchmark("Fastest marathon", MARATHON_KM)
    ).map { benchmark ->
        fastestBenchmarkRecord(runs, benchmark)
    }

    return benchmarkRecords + listOf(
        longestDistanceRecord(
            workouts = runs,
            label = "Longest run",
            emptyMessage = "No running distance logged yet"
        ),
        bestPaceRecord(runs)
    )
}

fun buildCyclingRecords(
    workouts: List<WorkoutLog>,
    activityTypeNames: Map<Int, String>
): List<ProgressRecord> {
    val rides = workoutsForActivityType(workouts, activityTypeNames, "Cycling")

    return listOf(
        longestDistanceRecord(
            workouts = rides,
            label = "Longest ride",
            emptyMessage = "No cycling distance logged yet"
        ),
        fastestAverageSpeedRecord(rides)
    )
}

fun workoutsForActivityType(
    workouts: List<WorkoutLog>,
    activityTypeNames: Map<Int, String>,
    activityTypeName: String
): List<WorkoutLog> {
    return workouts.filter { workout ->
        val distance = workout.distance

        activityTypeNames[workout.activityTypeId].equals(activityTypeName, ignoreCase = true) &&
                distance != null &&
                distance > 0.0 &&
                workout.duration > 0
    }
}

fun fastestBenchmarkRecord(
    runs: List<WorkoutLog>,
    benchmark: DistanceBenchmark
): ProgressRecord {
    val best = runs
        .mapNotNull { run ->
            val estimatedMinutes = estimatedMinutesForDistance(run, benchmark.distanceKm)
            if (estimatedMinutes == null) {
                null
            } else {
                run to estimatedMinutes
            }
        }
        .minByOrNull { it.second }

    val value = if (best == null) {
        "No eligible run yet"
    } else {
        val run = best.first
        val estimatedMinutes = best.second

        "${formatClockDuration(estimatedMinutes)} from ${formatDistance(run.distance!!)} km run"
    }

    return ProgressRecord(
        label = benchmark.label,
        value = value
    )
}

fun estimatedMinutesForDistance(
    workout: WorkoutLog,
    targetDistanceKm: Double
): Double? {
    val workoutDistance = workout.distance ?: return null

    if (workoutDistance < targetDistanceKm) {
        return null
    }

    val minutesPerKm = workout.duration / workoutDistance
    return minutesPerKm * targetDistanceKm
}

fun longestDistanceRecord(
    workouts: List<WorkoutLog>,
    label: String,
    emptyMessage: String
): ProgressRecord {
    val best = workouts.maxByOrNull { it.distance ?: 0.0 }

    val value = if (best == null || best.distance == null) {
        emptyMessage
    } else {
        "${formatDistance(best.distance)} km in ${formatClockDuration(best.duration.toDouble())}"
    }

    return ProgressRecord(label, value)
}

fun bestPaceRecord(runs: List<WorkoutLog>): ProgressRecord {
    val best = runs.minByOrNull { run ->
        run.duration / (run.distance ?: 1.0)
    }

    val value = if (best == null || best.distance == null) {
        "No running pace available yet"
    } else {
        val minutesPerKm = best.duration / best.distance
        "${formatClockDuration(minutesPerKm)} / km from ${formatDistance(best.distance)} km run"
    }

    return ProgressRecord("Best pace", value)
}

fun fastestAverageSpeedRecord(rides: List<WorkoutLog>): ProgressRecord {
    val best = rides.maxByOrNull { ride ->
        val distance = ride.distance ?: 0.0
        val hours = ride.duration / 60.0

        if (hours <= 0.0) {
            0.0
        } else {
            distance / hours
        }
    }

    val value = if (best == null || best.distance == null) {
        "No cycling speed available yet"
    } else {
        val speedKmh = best.distance / (best.duration / 60.0)

        "${formatSpeed(speedKmh)} from ${formatDistance(best.distance)} km ride"
    }

    return ProgressRecord("Fastest average speed", value)
}

fun formatSpeed(speedKmh: Double): String {
    val formattedSpeed = if (speedKmh % 1.0 == 0.0) {
        speedKmh.toInt().toString()
    } else {
        "%.1f".format(Locale.UK, speedKmh)
    }

    return "$formattedSpeed km/h"
}
