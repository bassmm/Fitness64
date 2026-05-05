package com.fitness64.weightlifting

import com.fitness64.UserSession
import com.fitness64.users.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.time.LocalDate

private data class WeightliftingFormRow(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double?
)

fun Application.configureWeightliftingRoutes(
    weightliftingService: WeightliftingService,
    userService: UserService
) {
    routing {
        authenticate("auth-session") {
            get("/weightlifting/log") {
                call.respond(
                    PebbleContent(
                        "weightlifting-log",
                        mapOf("today" to LocalDate.now().toString())
                    )
                )
            }

            get("/weightlifting/history") {
                call.respondRedirect("/activities")
            }

            post("/weightlifting/log") {
                val params = call.receiveParameters()
                val duration = params["duration"]?.toIntOrNull()
                val logDate = params["logDate"] ?: LocalDate.now().toString()
                val notes = params["notes"]?.trim()

                if (duration == null || duration <= 0) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Duration must be a positive number")
                }

                val formRows = parseWeightliftingRows(params)
                    ?: return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Each exercise row must include a name, sets, reps, and optional non-negative weight"
                    )

                if (formRows.isEmpty()) {
                    return@post call.respond(HttpStatusCode.BadRequest, "At least one exercise is required")
                }

                val session = call.principal<UserSession>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Not logged in")

                val user = userService.findByEmail(session.email)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

                val userId = user.id
                    ?: return@post call.respond(HttpStatusCode.InternalServerError, "User ID not found")

                val workout = WeightliftingWorkoutLog(
                    userId = userId,
                    logDate = logDate,
                    duration = duration,
                    notes = notes?.ifBlank { null }
                )

                val exercises = formRows.map { row ->
                    WeightliftingLoggedExercise(
                        exerciseName = row.exerciseName,
                        sets = row.sets,
                        reps = row.reps,
                        weight = row.weight
                    )
                }

                weightliftingService.createWorkoutSession(workout, exercises)
                call.respondRedirect("/activities")
            }
        }
    }
}

private fun parseWeightliftingRows(params: Parameters): List<WeightliftingFormRow>? {
    val exerciseNames = params.getAll("exerciseName").orEmpty()
    val sets = params.getAll("sets").orEmpty()
    val reps = params.getAll("reps").orEmpty()
    val weights = params.getAll("weight").orEmpty()
    val rowCount = listOf(exerciseNames.size, sets.size, reps.size, weights.size).maxOrNull() ?: 0

    if (rowCount == 0) {
        return emptyList()
    }

    val parsedRows = mutableListOf<WeightliftingFormRow>()

    for (index in 0 until rowCount) {
        val exerciseNameRaw = exerciseNames.getOrElse(index) { "" }.trim()
        val setsRaw = sets.getOrElse(index) { "" }.trim()
        val repsRaw = reps.getOrElse(index) { "" }.trim()
        val weightRaw = weights.getOrElse(index) { "" }.trim()

        if (exerciseNameRaw.isBlank() && setsRaw.isBlank() && repsRaw.isBlank() && weightRaw.isBlank()) {
            continue
        }

        val setCount = setsRaw.toIntOrNull() ?: return null
        val repCount = repsRaw.toIntOrNull() ?: return null
        val weight = if (weightRaw.isBlank()) null else weightRaw.toDoubleOrNull() ?: return null

        if (exerciseNameRaw.isBlank() || setCount <= 0 || repCount <= 0 || (weight != null && weight < 0.0)) {
            return null
        }

        parsedRows.add(
            WeightliftingFormRow(
                exerciseName = exerciseNameRaw,
                sets = setCount,
                reps = repCount,
                weight = weight
            )
        )
    }

    return parsedRows
}
