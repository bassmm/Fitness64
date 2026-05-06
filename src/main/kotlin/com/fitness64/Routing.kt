package com.fitness64

import com.fitness64.activities.ActivityService
import com.fitness64.activities.ActivityType
import com.fitness64.activities.WorkoutLog
import com.fitness64.plans.PlanService
import com.fitness64.races.RaceRecord
import com.fitness64.races.RaceService
import com.fitness64.users.UserService
import com.fitness64.weightlifting.WeightliftingService
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
import kotlin.math.roundToInt

fun Application.configureRouting(
    userService: UserService,
    activityService: ActivityService,
    planService: PlanService,
    raceService: RaceService,
    weightliftingService: WeightliftingService
) {
    routing {

        staticResources("/assets", "assets")

        // Public routes
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

                val parsedDate = parseActivityDate(activityDate)
                val durationMinutes = parseDurationMinutes(durationText)
                val distanceKm = parseDistanceKm(distanceText)

                if (
                    type.isBlank() ||
                    parsedDate == null ||
                    durationMinutes == null
                ) {
                    call.respondTemplate(
                        "log-details",
                        mapOf(
                            "error" to "Please enter a valid activity type, date, and duration.",
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
                        logDate = parsedDate.toString(),
                        duration = durationMinutes,
                        distance = distanceKm,
                        notes = notes.ifBlank { null },
                        calories = null,
                        source = type
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
                val endOfWeek = startOfWeek.plusDays(6)

                val workouts = activityService.getWorkoutsForUser(userId)
                val activityTypeNames = loadActivityTypeNames(activityService, workouts)
                val workoutsByDate = workouts.groupBy { it.logDate }

                val weightliftingSessions = weightliftingService.getWeightliftingHistory(userId)
                val weightliftingByDate = weightliftingSessions.groupBy { it.logDate }

                val races = raceService.getRacesForUser(userId)
                val racesByDate = races.groupBy { it.eventDate }

                val planFromDatabase = planService.getPlan(userId)
                val planByDay = planFromDatabase.associateBy { it.day }

                val earliestWorkoutDate = workouts
                    .mapNotNull { runCatching { LocalDate.parse(it.logDate) }.getOrNull() }
                    .minOrNull()

                val earliestWeightliftingDate = weightliftingSessions
                    .mapNotNull { runCatching { LocalDate.parse(it.logDate) }.getOrNull() }
                    .minOrNull()

                val earliestRaceDate = races
                    .mapNotNull { runCatching { LocalDate.parse(it.eventDate) }.getOrNull() }
                    .minOrNull()

                val earliestDate = listOfNotNull(
                    earliestWorkoutDate,
                    earliestWeightliftingDate,
                    earliestRaceDate,
                    startOfWeek
                ).minOrNull() ?: startOfWeek

                val latestDate = endOfWeek

                val calendarItems = generateSequence(latestDate) { date ->
                    if (date.isAfter(earliestDate)) {
                        date.minusDays(1)
                    } else {
                        null
                    }
                }.map { date ->
                    val dateString = date.toString()
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val workoutsForDate = workoutsByDate[dateString] ?: emptyList()
                    val weightliftingForDate = weightliftingByDate[dateString] ?: emptyList()
                    val racesForDate = racesByDate[dateString] ?: emptyList()

                    val plannedSession = if (!date.isBefore(startOfWeek) && !date.isAfter(endOfWeek)) {
                        planByDay[dayName]?.session ?: ""
                    } else {
                        ""
                    }

                    mapOf(
                        "day" to dayName,
                        "date" to dateString,
                        "planned" to plannedSession,
                        "isToday" to (date == today),
                        "logged" to (
                            workoutsForDate.map {
                                mapOf(
                                    "type" to (activityTypeNames[it.activityTypeId] ?: it.source ?: "Workout"),
                                    "duration" to "${it.duration} min",
                                    "distance" to (it.distance?.let { distance -> "${formatDistance(distance)} km" } ?: ""),
                                    "notes" to (it.notes ?: "")
                                )
                            } +
                                weightliftingForDate.map {
                                    mapOf(
                                        "type" to "Weightlifting",
                                        "duration" to "${it.duration} min",
                                        "distance" to "${it.totalSets} total sets",
                                        "notes" to (it.notes ?: "")
                                    )
                                } +
                                racesForDate.map { race ->
                                    val raceDetails = listOfNotNull(
                                        race.location,
                                        race.category
                                    ).joinToString(" • ")

                                    val raceNotes = buildString {
                                        if (race.isPersonalBest) append("Personal best")
                                        if (raceDetails.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(raceDetails)
                                        }
                                    }

                                    mapOf(
                                        "type" to "Race: ${race.eventName}",
                                        "duration" to (race.finishTime ?: "Completed"),
                                        "distance" to "",
                                        "notes" to raceNotes
                                    )
                                }
                        )
                    )
                }.toList()

                call.respondTemplate(
                    "calendar",
                    mapOf("calendarItems" to calendarItems)
                )
            }

            get("/progress") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val userId = user.id
                    ?: return@get call.respondRedirect("/login")

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
                        activityTypeNames[workout.activityTypeId] ?: workout.source ?: "Activity"
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

            get("/races") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                if (user == null) {
                    call.respondRedirect("/login")
                    return@get
                }

                val races = user.id?.let { raceService.getRacesForUser(it) } ?: emptyList<Any>()

                call.respondTemplate(
                    "races",
                    mapOf("races" to races)
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

                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                if (user == null || user.id == null) {
                    call.respondRedirect("/login")
                    return@post
                }

                raceService.createRace(
                    RaceRecord(
                        userId = user.id,
                        eventName = eventName,
                        eventDate = eventDate,
                        location = location.ifBlank { null },
                        category = category.ifBlank { null },
                        finishTime = finishTime.ifBlank { null },
                        overallRank = overallRank,
                        categoryRank = categoryRank,
                        isPersonalBest = isPersonalBest,
                        certificateUrl = certificateUrl.ifBlank { null }
                    )
                )

                call.respondRedirect("/races")
            }
        }
    }
}

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
data class PlanFollowStatus(
    val day: String,
    val date: String,
    val planned: String,
    val status: String,
    val plannedWorkout: Boolean,
    val completedPlannedWorkout: Boolean
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

fun formatSpeed(speedKmh: Double): String {
    val formattedSpeed = if (speedKmh % 1.0 == 0.0) {
        speedKmh.toInt().toString()
    } else {
        "%.1f".format(Locale.UK, speedKmh)
    }

    return "$formattedSpeed km/h"
}

fun splitPreferredActivities(preferredActivities: String?): List<String> {
    return preferredActivities
        ?.split(",")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?: emptyList()
}
