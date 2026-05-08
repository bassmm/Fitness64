package com.fitness64.routes.activities

import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.schema.ActivityService
import com.fitness64.schema.ActivityType
import com.fitness64.schema.Trackpoint
import com.fitness64.schema.UserService
import com.fitness64.schema.WorkoutLap
import com.fitness64.schema.WorkoutLog
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
import io.ktor.server.response.respondRedirect
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.jvm.javaio.toInputStream
import java.time.LocalDate

fun Application.configureTcxUploadRoutes(
    activityService: ActivityService,
    userService: UserService
) {
    routing {
        authenticate("auth-session") {
            get("/tcx/upload") {
                call.respondRedirect("/import")
            }

            get("/import") {
                call.respond(PebbleContent("import", mapOf("error" to "", "success" to "")))
            }

            post("/import") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@post

                val multipart = call.receiveMultipart()
                var fileBytes: ByteArray? = null
                var fileName = ""

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        fileName = part.originalFileName ?: ""
                        fileBytes = part.provider().toInputStream().readBytes()
                        part.dispose()
                    }
                }

                val bytes = fileBytes
                if (bytes == null || bytes.isEmpty()) {
                    call.respond(PebbleContent("import", mapOf("error" to "No file uploaded.", "success" to "")))
                    return@post
                }

                val extension = fileName.substringAfterLast('.', "").lowercase()
                if (extension !in setOf("tcx", "gpx", "csv")) {
                    call.respond(PebbleContent("import", mapOf("error" to "Unsupported file type '$extension'. Please upload a TCX, GPX, or CSV file.", "success" to "")))
                    return@post
                }

                when (extension) {
                    "tcx", "gpx" -> importTrackFile(bytes, extension, userId, activityService, call)
                    "csv" -> importCsvFile(bytes, userId, activityService, call)
                }
            }
        }
    }
}

private suspend fun importTrackFile(
    bytes: ByteArray,
    extension: String,
    userId: Int,
    activityService: ActivityService,
    call: io.ktor.server.application.ApplicationCall
) {
    val tcxData = runCatching {
        if (extension == "gpx") GpxParser.parse(bytes.inputStream())
        else TcxParser.parse(bytes.inputStream())
    }.getOrElse {
        call.respond(PebbleContent("import", mapOf("error" to "Could not parse file: ${it.message}", "success" to "")))
        return
    }

    val activityTypeId = activityService.getActivityTypeByName("Running")
        ?: activityService.createActivityType(ActivityType("Running"))

    val workoutLogId = activityService.createWorkoutLog(
        WorkoutLog(
            userId = userId,
            activityTypeId = activityTypeId,
            logDate = LocalDate.now().toString(),
            duration = tcxData.totalDuration,
            distance = tcxData.totalDistance,
            notes = "Imported from ${extension.uppercase()} file",
            calories = tcxData.totalCalories,
            source = "${extension}_import",
            name = "Running Session"
        )
    )

    for (lap in tcxData.laps) {
        val lapId = activityService.createWorkoutLap(
            WorkoutLap(
                workoutLogId = workoutLogId,
                startTime = lap.startTime,
                totalTimeSeconds = lap.totalTimeSeconds,
                distance = lap.distance,
                calories = lap.calories
            )
        )
        for (trackpoint in lap.trackpoints) {
            activityService.createTrackpoint(
                Trackpoint(
                    lapId = lapId,
                    time = trackpoint.time,
                    latitude = trackpoint.latitude,
                    longitude = trackpoint.longitude,
                    altitude = trackpoint.altitude,
                    distance = trackpoint.distance,
                    heartRate = trackpoint.heartRate
                )
            )
        }
    }

    call.respond(
        PebbleContent(
            "import",
            mapOf(
                "error" to "",
                "success" to "${extension.uppercase()} file imported successfully! ${tcxData.laps.size} lap(s) and ${tcxData.laps.sumOf { it.trackpoints.size }} trackpoints saved."
            )
        )
    )
}

private suspend fun importCsvFile(
    bytes: ByteArray,
    userId: Int,
    activityService: ActivityService,
    call: io.ktor.server.application.ApplicationCall
) {
    runCatching {
        val csv = CsvParser.parse(bytes.inputStream())
        val activityTypeId = activityService.getActivityTypeByName(csv.activityType)?: 
        activityService.createActivityType(ActivityType(csv.activityType))

        activityService.createWorkoutLog(
            WorkoutLog(
                userId = userId,
                activityTypeId = activityTypeId,
                logDate = csv.date,
                duration = csv.durationSeconds,
                distance = csv.distanceMetres,
                notes = csv.notes.ifBlank { "Imported from CSV file" },
                calories = csv.calories,
                source = "csv_import",
                name = "${csv.activityType} Session"
            )
        )

        call.respond(
            PebbleContent(
                "import",
                mapOf("error" to "", "success" to "CSV imported successfully! Activity logged for ${csv.date}.")
            )
        )
    }.getOrElse {
        call.respond(PebbleContent("import", mapOf("error" to "Could not parse CSV: ${it.message}", "success" to "")))
        return
    }
}
