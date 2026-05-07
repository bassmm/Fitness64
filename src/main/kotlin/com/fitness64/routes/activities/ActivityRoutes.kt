package com.fitness64.routes.activities

import com.fitness64.core.isHtmxRequest
import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.core.respondHx
import com.fitness64.schema.ActivityService
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
import java.net.URI
import java.time.LocalDate

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
    val availableActivityTypes: List<String> = emptyList()
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
            availableActivityTypes = cardioTypes
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
            id = "race-${race.id}", type = "race", category = "Race",
            title = race.eventName, date = race.eventDate, duration = 0,
            location = race.location, categoryName = race.category,
            finishTime = race.finishTime, overallRank = race.overallRank,
            categoryRank = race.categoryRank, isPersonalBest = race.isPersonalBest,
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
        "availableActivityTypes" to activity.availableActivityTypes
    )
)


private fun safeExternalUrl(value: String?): String {
    val trimmed = value?.trim().orEmpty()
    if (trimmed.isBlank()) return ""

    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return ""
    val scheme = uri.scheme?.lowercase() ?: return ""

    return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
        trimmed
    } else {
        ""
    }
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

                val fromDateParam = call.request.queryParameters["fromDate"]?.trim().orEmpty()
                val toDateParam = call.request.queryParameters["toDate"]?.trim().orEmpty()

                val fromDate = fromDateParam
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                val toDate = toDateParam
                    .takeIf { it.isNotBlank() }
                    ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

                val dateRangeError = if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
                    "From date must be before or the same as To date."
                } else {
                    ""
                }

                fun isDateInRange(dateValue: String): Boolean {
                    val itemDate = runCatching { LocalDate.parse(dateValue) }.getOrNull()
                        ?: return false

                    val matchesFrom = fromDate == null || !itemDate.isBefore(fromDate)
                    val matchesTo = toDate == null || !itemDate.isAfter(toDate)

                    return matchesFrom && matchesTo
                }

                val cardioHistory = activityService.getCardioHistory(userId)
                val weightliftingHistory = weightliftingService.getWeightliftingHistory(userId)
                val raceHistory = raceService.getRacesForUser(userId)

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
                        .joinToString(", ") { exercise ->
                            "${exercise.exerciseName} ${exercise.sets}x${exercise.reps}"
                        }
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

                    val summary = listOfNotNull(race.location, race.category)
                        .joinToString(" • ")
                        .ifBlank { "Race logged" }

                    val notePrefix = if (race.isPersonalBest) "Personal best. " else ""
                    val noteBody = race.certificateUrl?.let { "Certificate: $it" } ?: ""

                   ActivityFeedItem(
                        id = "race-${race.id}",
                        type = "race",
                        date = race.eventDate,
                        category = "Race",
                        title = race.eventName,
                        summary = summary,
                        metric = metric,
                        notes = "$notePrefix$noteBody".trim(),
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
                    .filter { item ->
                        dateRangeError.isNotBlank() || isDateInRange(item.date)
                    }
                    .sortedByDescending { it.date }

                val volumeBySession = weightliftingHistory
                    .filter {
                        dateRangeError.isNotBlank() || isDateInRange(it.logDate)
                    }
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
                            "selectedFilter" to selectedFilter,
                            "allCount" to allActivities.size,
                            "workoutsCount" to allActivities.count { it.type != "race" },
                            "racesCount" to allActivities.count { it.type == "race" },
                            "volumeBySession" to volumeBySession,
                            "fromDate" to fromDateParam,
                            "toDate" to toDateParam,
                            "hasDateFilter" to (fromDateParam.isNotBlank() || toDateParam.isNotBlank()),
                            "dateRangeError" to dateRangeError
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
                            workoutName
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
                            workoutName
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
                            notes
                        )
                    }

                    else -> return@post call.respond(HttpStatusCode.BadRequest, "Invalid activity type")
                }

                val updatedActivity = resolveActivity(
                    type,
                    resourceId,
                    userId,
                    activityService,
                    weightliftingService,
                    raceService
                )

                if (updatedActivity != null && call.isHtmxRequest()) {
                    call.respondHx(
                        templateName = "_partials/_activity-detail-view",
                        model = activityDetailMap(updatedActivity),
                        target = "#activity-edit-area",
                        swap = "innerHTML"
                    )
                } else {
                    call.respondRedirect("/activities")
                }
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
