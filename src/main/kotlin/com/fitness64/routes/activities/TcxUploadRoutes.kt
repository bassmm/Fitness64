package com.fitness64.routes.activities

import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.schema.ActivityService
import com.fitness64.schema.ActivityType
import com.fitness64.schema.Trackpoint
import com.fitness64.schema.UserService
import com.fitness64.schema.WorkoutLap
import com.fitness64.schema.WorkoutLog
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receiveMultipart
import io.ktor.server.response.respond
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
                call.respond(PebbleContent("tcx-upload", mapOf("error" to "", "success" to "")))
            }

            post("/tcx/upload") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@post

                val multipart = call.receiveMultipart()
                var parsed: ParsedTcxData? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        parsed = TcxParser.parse(part.provider().toInputStream())
                        part.dispose()
                    }
                }

                val tcxData = parsed
                if (tcxData == null) {
                    call.respond(PebbleContent("tcx-upload", mapOf("error" to "No file uploaded or file could not be parsed.", "success" to "")))
                    return@post
                }

                val activityTypeId = activityService.getActivityTypeByName("Running")
                    ?: activityService.createActivityType(ActivityType("Running"))

                val workoutLogId = activityService.createWorkoutLog(
                    WorkoutLog(
                        userId = userId, activityTypeId = activityTypeId,
                        logDate = LocalDate.now().toString(),
                        duration = tcxData.totalDuration, distance = tcxData.totalDistance,
                        notes = "Imported from TCX file", calories = tcxData.totalCalories, source = "tcx_import",
                        name = "Running Session"
                    )
                )

                for (lap in tcxData.laps) {
                    val lapId = activityService.createWorkoutLap(
                        WorkoutLap(
                            workoutLogId = workoutLogId, startTime = lap.startTime,
                            totalTimeSeconds = lap.totalTimeSeconds,
                            distance = lap.distance, calories = lap.calories
                        )
                    )
                    for (trackpoint in lap.trackpoints) {
                        activityService.createTrackpoint(
                            Trackpoint(
                                lapId = lapId, time = trackpoint.time,
                                latitude = trackpoint.latitude, longitude = trackpoint.longitude,
                                altitude = trackpoint.altitude, distance = trackpoint.distance,
                                heartRate = trackpoint.heartRate
                            )
                        )
                    }
                }

                call.respond(PebbleContent("tcx-upload", mapOf(
                    "error" to "",
                    "success" to "TCX file imported successfully! ${tcxData.laps.size} laps and ${tcxData.laps.sumOf { it.trackpoints.size }} trackpoints saved."
                )))
            }
        }
    }
}
