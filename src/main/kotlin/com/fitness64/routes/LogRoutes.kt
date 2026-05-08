package com.fitness64.routes

import com.fitness64.core.parseActivityDate
import com.fitness64.core.parseDistanceKm
import com.fitness64.core.parseDurationMinutes
import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.core.respondHx
import com.fitness64.schema.ActivityService
import com.fitness64.schema.ActivityType
import com.fitness64.schema.UserService
import com.fitness64.schema.WeightliftingLoggedExercise
import com.fitness64.schema.WeightliftingService
import com.fitness64.schema.WeightliftingWorkoutLog
import com.fitness64.schema.WorkoutLog
import io.ktor.http.Parameters
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Application.configureLogRoutes(
    userService: UserService,
    activityService: ActivityService,
    weightliftingService: WeightliftingService
) {
    routing {
        authenticate("auth-session") {
            get("/log") {
                val activityDate = call.request.queryParameters["date"] ?: LocalDate.now().toString()
                call.respondTemplate("log", mapOf("error" to "", "activityDate" to activityDate))
            }

            get("/log/picker") {
                val activityDate = call.request.queryParameters["date"] ?: LocalDate.now().toString()
                call.respondHx(
                    templateName = "_partials/_log-picker",
                    model = mapOf("activityDate" to activityDate, "error" to ""),
                    target = "#log-form-area", swap = "innerHTML"
                )
            }

            get("/log/form") {
                val activityType = call.request.queryParameters["activityType"]?.trim().orEmpty()
                val activityDate = call.request.queryParameters["activityDate"] ?: LocalDate.now().toString()

                if (activityType.isBlank()) {
                    call.respondHx(
                        templateName = "_partials/_log-picker",
                        model = mapOf("activityDate" to activityDate, "error" to "Please select an activity type."),
                        target = "#log-form-area", swap = "innerHTML"
                    )
                    return@get
                }

                when (activityType) {
                    "Running", "Cycling", "Swimming" -> call.respondHx(
                        templateName = "_partials/_cardio-form",
                        model = mapOf(
                            "selectedType" to activityType, "activityDate" to activityDate,
                            "distance" to "", "duration" to "", "calories" to "", "notes" to "", "error" to ""
                        ),
                        target = "#log-form-area", swap = "innerHTML"
                    )
                    "Weightlifting" -> call.respondHx(
                        templateName = "_partials/_weightlifting-form-partial",
                        model = mapOf(
                            "activityDate" to activityDate,
                            "duration" to "", "calories" to "", "notes" to "", "error" to ""
                        ),
                        target = "#log-form-area", swap = "innerHTML"
                    )
                    else -> call.respondRedirect("/log")
                }
            }

            post("/log/submit") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@post

                val params = call.receiveParameters()
                val type = params["type"]?.trim().orEmpty()
                val activityDate = params["activityDate"]?.trim().orEmpty()

                if (type == "Weightlifting") {
                    val duration = params["duration"]?.toIntOrNull()
                    val notes = params["notes"]?.trim()

                    if (duration == null || duration <= 0) {
                        call.respondHx(
                            templateName = "_partials/_weightlifting-form-partial",
                            model = mapOf("activityDate" to activityDate, "name" to "", "duration" to "", "notes" to (notes ?: ""), "error" to "Duration must be a positive number."),
                            target = "#log-form-area", swap = "innerHTML"
                        )
                        return@post
                    }

                    val formRows = parseWeightliftingRows(params)
                    if (formRows.isNullOrEmpty()) {
                        call.respondHx(
                            templateName = "_partials/_weightlifting-form-partial",
                            model = mapOf("activityDate" to activityDate, "name" to (params["name"]?.trim().orEmpty()), "duration" to duration.toString(), "notes" to (notes ?: ""), "error" to "Each exercise row must have a name, sets, reps. At least one exercise required."),
                            target = "#log-form-area", swap = "innerHTML"
                        )
                        return@post
                    }

                    val name = params["name"]?.trim().orEmpty()
                    val workoutName = name.ifBlank { "Weightlifting Session" }

                    weightliftingService.createWorkoutSession(
                        WeightliftingWorkoutLog(userId = userId, logDate = activityDate, duration = duration, notes = notes?.ifBlank { null }, name = workoutName),
                        formRows.map { WeightliftingLoggedExercise(it.exerciseName, it.sets, it.reps, it.weight) }
                    )

                    call.respondHx(
                        templateName = "_partials/_log-success",
                        model = mapOf("successMessage" to "Weightlifting session saved for $activityDate."),
                        target = "#log-form-area", swap = "innerHTML"
                    )
                } else {
                    val distanceText = params["distance"]?.trim().orEmpty()
                    val durationText = params["duration"]?.trim().orEmpty()
                    val notes = params["notes"]?.trim().orEmpty()
                    val caloriesText = params["calories"]?.trim().orEmpty()
                    val name = params["name"]?.trim().orEmpty()

                    val parsedDate = parseActivityDate(activityDate)
                    val durationMinutes = parseDurationMinutes(durationText)
                    val distanceKm = parseDistanceKm(distanceText)

                    if (type.isBlank() || parsedDate == null || durationMinutes == null) {
                        call.respondHx(
                            templateName = "_partials/_cardio-form",
                            model = mapOf(
                                "selectedType" to type, "activityDate" to activityDate,
                                "distance" to distanceText, "duration" to durationText,
                                "calories" to "", "notes" to notes, "name" to name,
                                "error" to "Please enter a valid date and duration."
                            ),
                            target = "#log-form-area", swap = "innerHTML"
                        )
                        return@post
                    }

                    val activityTypeId = activityService.getActivityTypeByName(type)
                        ?: activityService.createActivityType(ActivityType(type))

                    val workoutName = name.ifBlank { "$type Session" }

                    activityService.createWorkoutLog(
                        WorkoutLog(
                            userId = userId, activityTypeId = activityTypeId, logDate = parsedDate.toString(),
                            duration = durationMinutes, distance = distanceKm, notes = notes.ifBlank { null },
                            calories = caloriesText.toIntOrNull(), source = type, name = workoutName
                        )
                    )

                    call.respondHx(
                        templateName = "_partials/_log-success",
                        model = mapOf("successMessage" to "$type session logged for $activityDate."),
                        target = "#log-form-area", swap = "innerHTML"
                    )
                }
            }
        }
    }
}

private data class WeightliftingFormRow(
    val exerciseName: String, val sets: Int, val reps: Int, val weight: Double?
)

private fun parseWeightliftingRows(params: Parameters): List<WeightliftingFormRow>? {
    val exerciseNames = params.getAll("exerciseName").orEmpty()
    val sets = params.getAll("sets").orEmpty()
    val reps = params.getAll("reps").orEmpty()
    val weights = params.getAll("weight").orEmpty()
    val rowCount = listOf(exerciseNames.size, sets.size, reps.size, weights.size).maxOrNull() ?: 0

    if (rowCount == 0) return emptyList()

    val parsedRows = mutableListOf<WeightliftingFormRow>()
    for (index in 0 until rowCount) {
        val name = exerciseNames.getOrElse(index) { "" }.trim()
        val s = sets.getOrElse(index) { "" }.trim()
        val r = reps.getOrElse(index) { "" }.trim()
        val w = weights.getOrElse(index) { "" }.trim()

        if (name.isBlank() && s.isBlank() && r.isBlank() && w.isBlank()) continue

        val setCount = s.toIntOrNull() ?: return null
        val repCount = r.toIntOrNull() ?: return null
        val weight = if (w.isBlank()) null else w.toDoubleOrNull() ?: return null

        if (name.isBlank() || setCount <= 0 || repCount <= 0 || (weight != null && weight < 0.0)) return null

        parsedRows.add(WeightliftingFormRow(name, setCount, repCount, weight))
    }
    return parsedRows
}
