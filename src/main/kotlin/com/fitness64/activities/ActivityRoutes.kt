package com.fitness64.activities

import com.fitness64.UserSession
import com.fitness64.users.UserService
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.Application
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.pebble.PebbleContent
import io.ktor.server.request.receive
import io.ktor.server.request.receiveMultipart
import io.ktor.utils.io.jvm.javaio.toInputStream
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun Application.configureActivityRoutes(
    activityService: ActivityService,
    userService: UserService
) {
    routing {
        authenticate("auth-session") {
            // --- TCX Upload ---
            get("/tcx/upload") {
                call.respond(
                    PebbleContent(
                        "tcx-upload",
                        mapOf("error" to "", "success" to "")
                    )
                )
            }

            post("/tcx/upload") {
                val session = call.principal<UserSession>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Not logged in")

                val user = userService.findByEmail(session.email)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

                val userId = user.id
                    ?: return@post call.respond(HttpStatusCode.InternalServerError, "User ID not found")

                val multipart = call.receiveMultipart()
                var parsed: ParsedTcxData? = null

                multipart.forEachPart { part ->
                    if (part is PartData.FileItem) {
                        val inputStream = part.provider().toInputStream()
                        parsed = TcxParser.parse(inputStream)
                        part.dispose()
                    }
                }

                val tcxData = parsed
                if (tcxData == null) {
                    call.respond(
                        PebbleContent(
                            "tcx-upload",
                            mapOf(
                                "error" to "No file uploaded or file could not be parsed.",
                                "success" to ""
                            )
                        )
                    )
                    return@post
                }

                val activityTypeId = activityService.getActivityTypeByName("Running")
                    ?: activityService.createActivityType(ActivityType("Running"))

                val workoutLogId = activityService.createWorkoutLog(
                    WorkoutLog(
                        userId = userId,
                        activityTypeId = activityTypeId,
                        logDate = java.time.LocalDate.now().toString(),
                        duration = tcxData.totalDuration,
                        distance = tcxData.totalDistance,
                        notes = "Imported from TCX file",
                        calories = tcxData.totalCalories,
                        source = "tcx_import"
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
                        "tcx-upload",
                        mapOf(
                            "error" to "",
                            "success" to "TCX file imported successfully! ${tcxData.laps.size} laps and ${tcxData.laps.sumOf { it.trackpoints.size }} trackpoints saved."
                        )
                    )
                )
            }
        }

        post("/activity-types") {
            val activityType = call.receive<ActivityType>()
            val id = activityService.createActivityType(activityType)
            call.respond(HttpStatusCode.Created, id)
        }

        post("/workouts") {
            val workout = call.receive<WorkoutLog>()
            val id = activityService.createWorkoutLog(workout)
            call.respond(HttpStatusCode.Created, id)
        }

        get("/workouts/{id}") {
            val id = call.parameters["id"]?.toInt()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid workout ID")
            val workout = activityService.getWorkoutLog(id)
            if (workout != null) {
                call.respond(HttpStatusCode.OK, workout)
            } else {
                call.respond(HttpStatusCode.NotFound, "Workout not found")
            }
        }

        get("/users/{userId}/workouts") {
            val userId = call.parameters["userId"]?.toInt()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
            val workouts = activityService.getWorkoutsForUser(userId)
            call.respond(HttpStatusCode.OK, workouts)
        }

        delete("/workouts/{id}") {
            val id = call.parameters["id"]?.toInt()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid workout ID")
            activityService.deleteWorkoutLog(id)
            call.respond(HttpStatusCode.OK, "Workout deleted")
        }

        post("/laps") {
            val lap = call.receive<WorkoutLap>()
            val id = activityService.createWorkoutLap(lap)
            call.respond(HttpStatusCode.Created, id)
        }

        post("/trackpoints") {
            val trackpoint = call.receive<Trackpoint>()
            val id = activityService.createTrackpoint(trackpoint)
            call.respond(HttpStatusCode.Created, id)
        }

        get("/laps/{id}/trackpoints") {
            val id = call.parameters["id"]?.toInt()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid lap ID")
            val trackpoints = activityService.getTrackpointsForLap(id)
            call.respond(HttpStatusCode.OK, trackpoints)
        }
    }
}

