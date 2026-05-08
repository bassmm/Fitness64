package com.fitness64.routes.activities

import com.fitness64.core.isHtmxRequest
import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.core.respondHx
import com.fitness64.core.respondHxRedirect
import com.fitness64.schema.ActivityService
import com.fitness64.schema.Trackpoint
import com.fitness64.schema.RaceService
import com.fitness64.schema.UserService
import com.fitness64.schema.WeightliftingLoggedExercise
import com.fitness64.schema.WeightliftingService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.net.URI
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private data class ActivityFeedItem(
    val id: String,
    val type: String,
    val date: String,
    val category: String,
    val title: String,
    val summary: String,
    val metric: String,
    val notes: String,
    val location: String = "",
    val raceCategory: String = "",
    val finishTime: String = "",
    val overallRank: Int? = null,
    val categoryRank: Int? = null,
    val isPersonalBest: Boolean = false,
    val certificateUrl: String? = null
)

private data class LapDetail(
    val lapNumber: Int,
    val color: String,
    val points: List<PointCoord>
)

private data class PointCoord(
    val lat: Double,
    val lng: Double
)

private data class HeartRatePoint(
    val label: String,
    val bpm: Int
)

private val LAP_COLORS = listOf(
    "#3388ff", "#ff6600", "#33cc33", "#cc33cc", "#ffcc00", "#00cccc"
)

private data class ActivityDetail(
    val id: String,
    val type: String,
    val category: String,
    val title: String,
    val date: String,
    val duration: Int,
    val distance: Double? = null,
    val calories: Int? = null,
    val notes: String? = null,
    val source: String? = null,
    val exercises: List<com.fitness64.schema.WeightliftingWorkoutEntry>? = null,
    val totalSets: Int? = null,
    val location: String? = null,
    val categoryName: String? = null,
    val finishTime: String? = null,
    val overallRank: Int? = null,
    val categoryRank: Int? = null,
    val isPersonalBest: Boolean = false,
    val certificateUrl: String? = null,
    val activityTypeId: Int? = null,
    val availableActivityTypes: List<String> = emptyList(),
    val laps: List<LapDetail>? = null,
    val heartRateData: List<HeartRatePoint>? = null
)

private suspend fun resolveActivity(
    type: String,
    resourceId: Int,
    userId: Int,
    activityService: ActivityService,
    weightliftingService: WeightliftingService,
    raceService: RaceService
): ActivityDetail? = when (type) {
    "cardio" -> {
        val workout = activityService.getWorkoutLog(resourceId)
            ?: return null

        if (workout.userId != userId) return null

        val activityType = activityService.getActivityTypeName(workout.activityTypeId)
            ?: workout.source
            ?: "Unknown"

        val cardioTypes = listOf("Running", "Cycling", "Swimming")
        val displayName = workout.name?.takeIf { it.isNotBlank() } ?: "$activityType Session"

        val laps = buildLapDetails(workout.id!!, activityService)
        val heartRateData = buildHeartRateData(workout.id, activityService)

        ActivityDetail(
            id = "cardio-${workout.id}",
            type = "cardio",
            category = activityType.takeIf { it in cardioTypes } ?: "Cardio",
            title = displayName,
            date = workout.logDate,
            duration = workout.duration,
            distance = workout.distance,
            calories = workout.calories,
            notes = workout.notes,
            source = workout.source,
            activityTypeId = workout.activityTypeId,
            availableActivityTypes = cardioTypes,
            laps = laps,
            heartRateData = heartRateData
        )
    }

    "weightlifting" -> {
        val history = weightliftingService.getWeightliftingHistory(userId)
        val workout = history.find { it.id == resourceId } ?: return null
        val displayName = workout.name?.takeIf { it.isNotBlank() } ?: "Weightlifting Session"

        ActivityDetail(
            id = "weightlifting-${workout.id}",
            type = "weightlifting",
            category = "Weightlifting",
            title = displayName,
            date = workout.logDate,
            duration = workout.duration,
            notes = workout.notes,
            exercises = workout.exercises,
            totalSets = workout.totalSets
        )
    }

    "race" -> {
        val race = raceService.getRacesForUser(userId).find { it.id == resourceId }
            ?: return null

        ActivityDetail(
            id = "race-${race.id}",
            type = "race",
            category = "Race",
            title = race.eventName,
            date = race.eventDate,
            duration = 0,
            location = race.location,
            categoryName = race.category,
            finishTime = race.finishTime,
            overallRank = race.overallRank,
            categoryRank = race.categoryRank,
            isPersonalBest = race.isPersonalBest,
            certificateUrl = safeExternalUrl(race.certificateUrl).ifBlank { null }
        )
    }

    else -> null
}

private fun activityDetailMap(activity: ActivityDetail): Map<String, Any> = mapOf<String, Any>(
    "activity" to mapOf<String, Any?>(
        "id" to activity.id,
        "type" to activity.type,
        "category" to activity.category,
        "title" to activity.title,
        "date" to activity.date,
        "duration" to activity.duration,
        "distance" to activity.distance,
        "calories" to activity.calories,
        "notes" to activity.notes,
        "source" to activity.source,
        "exercises" to activity.exercises,
        "totalSets" to activity.totalSets,
        "location" to activity.location,
        "category_name" to activity.categoryName,
        "finishTime" to activity.finishTime,
        "overallRank" to activity.overallRank,
        "categoryRank" to activity.categoryRank,
        "isPersonalBest" to activity.isPersonalBest,
        "certificateUrl" to activity.certificateUrl,
        "activityTypeId" to activity.activityTypeId,
        "availableActivityTypes" to activity.availableActivityTypes,
        "laps" to (activity.laps?.map { lap ->
            mapOf<String, Any?>(
                "lapNumber" to lap.lapNumber,
                "color" to lap.color,
                "points" to lap.points.map { pt -> listOf(pt.lat, pt.lng) }
            )
        }),
        "heartRateData" to (activity.heartRateData?.map { hr ->
            mapOf<String, Any?>("label" to hr.label, "bpm" to hr.bpm)
        })
    )
)

private suspend fun buildLapDetails(workoutLogId: Int, activityService: ActivityService): List<LapDetail>? {
    val laps = activityService.getLapsForWorkoutLog(workoutLogId)
    if (laps.isEmpty()) return null

    val result = mutableListOf<LapDetail>()
    for ((index, lap) in laps.withIndex()) {
        val trackpoints = activityService.getTrackpointsForLap(lap.id)
            .filter { it.latitude != null && it.longitude != null }
            .map { PointCoord(it.latitude!!, it.longitude!!) }

        if (trackpoints.isNotEmpty()) {
            val color = LAP_COLORS[index % LAP_COLORS.size]
            result.add(LapDetail(lapNumber = index + 1, color = color, points = trackpoints))
        }
    }

    return result.ifEmpty { null }
}

private fun parseTimestamp(timeStr: String): LocalDateTime? {
    return try {
        LocalDateTime.parse(timeStr.trimEnd('Z'))
    } catch (e: DateTimeParseException) {
        null
    }
}

private fun formatElapsed(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "${minutes}:${secs.toString().padStart(2, '0')}"
}

private suspend fun buildHeartRateData(workoutLogId: Int, activityService: ActivityService): List<HeartRatePoint>? {
    val laps = activityService.getLapsForWorkoutLog(workoutLogId)
    if (laps.isEmpty()) return null

    val allTrackpoints = mutableListOf<Trackpoint>()
    for (lap in laps) {
        allTrackpoints.addAll(activityService.getTrackpointsForLap(lap.id))
    }

    val hrPoints = allTrackpoints.filter { it.heartRate != null && it.heartRate > 0 }
    if (hrPoints.isEmpty()) return null

    val baseTime = parseTimestamp(hrPoints.first().time)
    if (baseTime == null) return null

    return hrPoints.map { tp ->
        val tpTime = parseTimestamp(tp.time)
        val elapsedSeconds = if (tpTime != null) ChronoUnit.SECONDS.between(baseTime, tpTime) else 0L
        HeartRatePoint(label = formatElapsed(elapsedSeconds), bpm = tp.heartRate!!)
    }
}

private fun safeExternalUrl(value: String?): String {
    val trimmed = value?.trim().orEmpty()

    if (trimmed.isBlank()) {
        return ""
    }

    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return ""
    val scheme = uri.scheme?.lowercase() ?: return ""

    return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
        trimmed
    } else {
        ""
    }
}

internal fun activityMatchesSearch(
    query: String,
    title: String,
    type: String,
    category: String,
    date: String
): Boolean {
    val trimmedQuery = query.trim()

    if (trimmedQuery.isBlank()) {
        return true
    }

    return title.contains(trimmedQuery, ignoreCase = true) ||
            type.contains(trimmedQuery, ignoreCase = true) ||
            category.contains(trimmedQuery, ignoreCase = true) ||
            date.contains(trimmedQuery, ignoreCase = true)
}

fun Application.configureActivityRoutes(
    activityService: ActivityService,
    userService: UserService,
    weightliftingService: WeightliftingService,
    raceService: RaceService
) {
    routing {
        authenticate("auth-session") {
            get("/activities") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@get

                val selectedFilter = call.request.queryParameters["filter"]
                    ?.lowercase()
                    ?.takeIf { it in setOf("all", "workouts", "races") }
                    ?: "all"

                val searchQuery = call.request.queryParameters["search"]?.trim().orEmpty()

                val selectedDateParam = call.request.queryParameters["date"]?.trim().orEmpty()
                val monthParam = call.request.queryParameters["month"]?.trim().orEmpty()
                val yearParam = call.request.queryParameters["year"]?.trim().orEmpty()

                val selectedDate = selectedDateParam
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                val today = LocalDate.now()

                val navMonth = monthParam.toIntOrNull() ?: today.monthValue
                val navYear = yearParam.toIntOrNull() ?: today.year
                val yearMonth = runCatching {
                    YearMonth.of(navYear, navMonth)
                }.getOrNull() ?: YearMonth.from(today)

                val cardioHistory = activityService.getCardioHistory(userId)
                val weightliftingHistory = weightliftingService.getWeightliftingHistory(userId)
                val raceHistory = raceService.getRacesForUser(userId)

                val activityDates = (
                        cardioHistory.map { it.logDate } +
                                weightliftingHistory.map { it.logDate } +
                                raceHistory.map { it.eventDate }
                        ).toSet()

                val firstOfMonth = yearMonth.atDay(1)
                val startDay = firstOfMonth.dayOfWeek.value % 7
                val daysInMonth = yearMonth.lengthOfMonth()
                val totalCells = (startDay + daysInMonth + 6) / 7 * 7

                val calendarDays = buildList {
                    for (i in 0 until startDay) {
                        add(
                            mapOf(
                                "day" to "",
                                "date" to "",
                                "month" to "",
                                "year" to "",
                                "hasActivity" to false,
                                "isToday" to false,
                                "isSelected" to false,
                                "isCurrentMonth" to false
                            )
                        )
                    }

                    for (day in 1..daysInMonth) {
                        val date = yearMonth.atDay(day)
                        val dateStr = date.toString()

                        add(
                            mapOf(
                                "day" to day,
                                "date" to dateStr,
                                "month" to date.monthValue,
                                "year" to date.year,
                                "hasActivity" to (dateStr in activityDates),
                                "isToday" to (date == today),
                                "isSelected" to (selectedDate != null && date == selectedDate),
                                "isCurrentMonth" to true
                            )
                        )
                    }

                    while (size < totalCells) {
                        add(
                            mapOf(
                                "day" to "",
                                "date" to "",
                                "month" to "",
                                "year" to "",
                                "hasActivity" to false,
                                "isToday" to false,
                                "isSelected" to false,
                                "isCurrentMonth" to false
                            )
                        )
                    }
                }

                val prevMonth = yearMonth.minusMonths(1)
                val nextMonth = yearMonth.plusMonths(1)

                val cardioItems = cardioHistory.map { item ->
                    val cardioCategory = if (item.activityType in listOf("Running", "Cycling", "Swimming")) {
                        item.activityType
                    } else {
                        "Cardio"
                    }

                    val displayName = item.name?.takeIf { it.isNotBlank() }
                        ?: "${item.activityType} Session"

                    ActivityFeedItem(
                        id = "cardio-${item.id}",
                        type = "cardio",
                        date = item.logDate,
                        category = cardioCategory,
                        title = displayName,
                        summary = item.distance?.let { "$it km" } ?: "Distance not recorded",
                        metric = "${item.duration} min",
                        notes = item.notes ?: ""
                    )
                }

                val weightliftingItems = weightliftingHistory.map { item ->
                    val exerciseSummary = item.exercises
                        .joinToString(", ") { "${it.exerciseName} ${it.sets}x${it.reps}" }
                        .ifBlank { "${item.totalSets} total sets" }

                    val displayName = item.name?.takeIf { it.isNotBlank() }
                        ?: "Weightlifting Session"

                    ActivityFeedItem(
                        id = "weightlifting-${item.id}",
                        type = "weightlifting",
                        date = item.logDate,
                        category = "Weightlifting",
                        title = displayName,
                        summary = exerciseSummary,
                        metric = "${item.duration} min",
                        notes = item.notes ?: ""
                    )
                }

                val raceItems = raceHistory.map { race ->
                    val metric = race.finishTime
                        ?: race.overallRank?.let { "Overall #$it" }
                        ?: "Completed"

                    ActivityFeedItem(
                        id = "race-${race.id}",
                        type = "race",
                        date = race.eventDate,
                        category = "Race",
                        title = race.eventName,
                        summary = "Race result",
                        metric = metric,
                        notes = if (race.isPersonalBest) "Personal best" else "",
                        location = race.location ?: "",
                        raceCategory = race.category ?: "",
                        finishTime = race.finishTime ?: "",
                        overallRank = race.overallRank,
                        categoryRank = race.categoryRank,
                        isPersonalBest = race.isPersonalBest,
                        certificateUrl = safeExternalUrl(race.certificateUrl).ifBlank { null }
                    )
                }

                val allActivities = cardioItems + weightliftingItems + raceItems

                val activities = allActivities
                    .filter { item ->
                        when (selectedFilter) {
                            "workouts" -> item.type != "race"
                            "races" -> item.type == "race"
                            else -> true
                        }
                    }
                    .let { list ->
                        if (selectedDate != null) {
                            list.filter { it.date == selectedDate.toString() }
                        } else {
                            list
                        }
                    }
                    .filter { activity ->
                        activityMatchesSearch(
                            query = searchQuery,
                            title = activity.title,
                            type = activity.type,
                            category = activity.category,
                            date = activity.date
                        )
                    }
                    .sortedByDescending { it.date }

                val volumeBySession = weightliftingHistory
                    .sortedBy { it.logDate }
                    .map { item ->
                        mapOf(
                            "date" to item.logDate,
                            "totalSets" to item.totalSets
                        )
                    }

                call.respond(
                    PebbleContent(
                        "activity-history",
                        mapOf(
                            "activities" to activities,
                            "calendarMonth" to yearMonth.month.getDisplayName(
                                TextStyle.FULL,
                                Locale.ENGLISH
                            ) + " " + yearMonth.year,
                            "calendarDays" to calendarDays,
                            "prevMonth" to prevMonth.monthValue,
                            "prevYear" to prevMonth.year,
                            "nextMonth" to nextMonth.monthValue,
                            "nextYear" to nextMonth.year,
                            "selectedDate" to (selectedDate?.toString() ?: ""),
                            "hasDateFilter" to (selectedDate != null),
                            "selectedFilter" to selectedFilter,
                            "searchQuery" to searchQuery,
                            "resultCount" to activities.size,
                            "allCount" to allActivities.size,
                            "workoutCount" to allActivities.count { it.type != "race" },
                            "raceCount" to allActivities.count { it.type == "race" },
                            "workoutsCount" to allActivities.count { it.type != "race" },
                            "racesCount" to allActivities.count { it.type == "race" },
                            "volumeBySession" to volumeBySession
                        )
                    )
                )
            }

            get("/activities/{id}") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@get

                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing activity ID")

                val parts = id.split("-", limit = 2)
                if (parts.size != 2) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid activity ID format")
                }

                val type = parts[0]
                val resourceId = parts[1].toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid resource ID")

                val activity = resolveActivity(
                    type,
                    resourceId,
                    userId,
                    activityService,
                    weightliftingService,
                    raceService
                ) ?: return@get call.respond(HttpStatusCode.NotFound, "Activity not found")

                call.respond(PebbleContent("activity-detail", activityDetailMap(activity)))
            }

            get("/activities/{id}/view") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@get

                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing activity ID")

                val parts = id.split("-", limit = 2)
                if (parts.size != 2) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid activity ID format")
                }

                val type = parts[0]
                val resourceId = parts[1].toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid resource ID")

                val activity = resolveActivity(
                    type,
                    resourceId,
                    userId,
                    activityService,
                    weightliftingService,
                    raceService
                ) ?: return@get call.respond(HttpStatusCode.NotFound, "Activity not found")

                call.respondHx(
                    templateName = "_partials/_activity-detail-view",
                    model = activityDetailMap(activity),
                    target = "#activity-edit-area",
                    swap = "innerHTML"
                )
            }

            get("/activities/{id}/edit") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@get

                val id = call.parameters["id"]
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Missing activity ID")

                val parts = id.split("-", limit = 2)
                if (parts.size != 2) {
                    return@get call.respond(HttpStatusCode.BadRequest, "Invalid activity ID format")
                }

                val type = parts[0]
                val resourceId = parts[1].toIntOrNull()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid resource ID")

                val activity = resolveActivity(
                    type,
                    resourceId,
                    userId,
                    activityService,
                    weightliftingService,
                    raceService
                ) ?: return@get call.respond(HttpStatusCode.NotFound, "Activity not found")

                val templateName = when (type) {
                    "cardio" -> "_partials/_cardio-edit-form"
                    "weightlifting" -> "_partials/_weightlifting-edit-form"
                    "race" -> "_partials/_race-edit-form"
                    else -> return@get call.respond(HttpStatusCode.BadRequest, "Invalid activity type")
                }

                call.respondHx(
                    templateName = templateName,
                    model = activityDetailMap(activity) + mapOf("error" to ""),
                    target = "#activity-edit-area",
                    swap = "innerHTML"
                )
            }

            post("/activities/{id}/edit") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@post

                val id = call.parameters["id"]
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Missing activity ID")

                val parts = id.split("-", limit = 2)
                if (parts.size != 2) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Invalid activity ID format")
                }

                val type = parts[0]
                val resourceId = parts[1].toIntOrNull()
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Invalid resource ID")

                val params = call.receiveParameters()
                val duration = params["duration"]?.toIntOrNull() ?: 0
                val notes = params["notes"] ?: ""
                val date = params["date"]?.trim().orEmpty()

                if (duration <= 0) {
                    val editTemplate = when (type) {
                        "cardio" -> "_partials/_cardio-edit-form"
                        "weightlifting" -> "_partials/_weightlifting-edit-form"
                        "race" -> "_partials/_race-edit-form"
                        else -> null
                    }

                    if (editTemplate != null) {
                        val activity = resolveActivity(
                            type,
                            resourceId,
                            userId,
                            activityService,
                            weightliftingService,
                            raceService
                        )

                        if (activity != null) {
                            call.respondHx(
                                templateName = editTemplate,
                                model = activityDetailMap(activity) + mapOf(
                                    "error" to "Duration must be a positive number."
                                ),
                                target = "#activity-edit-area",
                                swap = "innerHTML"
                            )
                            return@post
                        }
                    }

                    return@post call.respondRedirect("/activities")
                }

                when (type) {
                    "cardio" -> {
                        val distance = params["distance"]?.toDoubleOrNull()
                        val calories = params["calories"]?.toIntOrNull()
                        val newActivityType = params["activityType"]?.trim()
                        val newName = params["name"]?.trim()

                        val newActivityTypeId = if (!newActivityType.isNullOrEmpty()) {
                            activityService.getOrCreateActivityType(newActivityType)
                        } else {
                            null
                        }

                        val workoutName = newName?.takeIf { it.isNotBlank() }

                        activityService.updateWorkoutLog(
                            resourceId,
                            duration,
                            distance,
                            notes,
                            calories,
                            newActivityTypeId,
                            workoutName,
                            date
                        )
                    }

                    "weightlifting" -> {
                        val exerciseRows = parseWeightliftingRows(params)

                        if (exerciseRows.isEmpty()) {
                            val activity = resolveActivity(
                                type,
                                resourceId,
                                userId,
                                activityService,
                                weightliftingService,
                                raceService
                            )

                            if (activity != null) {
                                call.respondHx(
                                    templateName = "_partials/_weightlifting-edit-form",
                                    model = activityDetailMap(activity) + mapOf(
                                        "error" to "At least one valid exercise is required."
                                    ),
                                    target = "#activity-edit-area",
                                    swap = "innerHTML"
                                )
                                return@post
                            }
                        }

                        val newName = params["name"]?.trim()
                        val workoutName = newName?.takeIf { it.isNotBlank() }

                        weightliftingService.updateWorkoutSession(
                            resourceId,
                            duration,
                            notes,
                            workoutName,
                            date
                        )
                        weightliftingService.updateWorkoutSessionExercises(resourceId, exerciseRows)
                    }

                    "race" -> {
                        val finishTime = params["finishTime"]
                        val overallRank = params["overallRank"]?.toIntOrNull()
                        val isPersonalBest = params["isPersonalBest"] == "on"

                        raceService.updateRace(
                            resourceId,
                            finishTime,
                            overallRank,
                            isPersonalBest,
                            notes,
                            date
                        )
                    }

                    else -> return@post call.respond(HttpStatusCode.BadRequest, "Invalid activity type")
                }

                call.respondHxRedirect("/activities/$id")
            }
        }
    }
}

private fun parseWeightliftingRows(params: Parameters): List<WeightliftingLoggedExercise> {
    val exerciseNames = params.getAll("exerciseName").orEmpty()
    val sets = params.getAll("sets").orEmpty()
    val reps = params.getAll("reps").orEmpty()
    val weights = params.getAll("weight").orEmpty()
    val rowCount = listOf(exerciseNames.size, sets.size, reps.size, weights.size).maxOrNull() ?: 0

    if (rowCount == 0) return emptyList()

    val parsedRows = mutableListOf<WeightliftingLoggedExercise>()

    for (index in 0 until rowCount) {
        val name = exerciseNames.getOrElse(index) { "" }.trim()
        val s = sets.getOrElse(index) { "" }.trim()
        val r = reps.getOrElse(index) { "" }.trim()
        val w = weights.getOrElse(index) { "" }.trim()

        if (name.isBlank() && s.isBlank() && r.isBlank() && w.isBlank()) continue

        val setCount = s.toIntOrNull() ?: continue
        val repCount = r.toIntOrNull() ?: continue
        val weight = if (w.isBlank()) null else w.toDoubleOrNull()

        if (name.isNotBlank() && setCount > 0 && repCount > 0 && weight != null && weight >= 0.0) {
            parsedRows.add(WeightliftingLoggedExercise(name, setCount, repCount, weight))
        } else if (name.isNotBlank() && setCount > 0 && repCount > 0 && weight == null) {
            parsedRows.add(WeightliftingLoggedExercise(name, setCount, repCount, null))
        }
    }

    return parsedRows
}
