package com.fitness64.activities

import com.fitness64.UserSession
import com.fitness64.users.UserService
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Application.configureActivityRoutes(
    activityService: ActivityService,
    userService: UserService
) {
    routing {

        authenticate("auth-session") {

            get("/weightlifting/log") {
                val weightliftingTypeId = activityService.getActivityTypeByName("Weightlifting")
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Weightlifting activity type not found")

                val exercises = activityService.getExercisesByActivityType(weightliftingTypeId)

                call.respond(
                    PebbleContent(
                        "weightlifting-log",
                        mapOf(
                            "today" to LocalDate.now().toString(),
                            "exercises" to exercises
                        )
                    )
                )
            }

            get("/weightlifting/exercises/new") {
                call.respond(
                    PebbleContent(
                        "add-exercise",
                        mapOf(
                            "error" to "",
                            "name" to "",
                            "category" to "",
                            "measurementType" to "reps"
                        )
                    )
                )
            }

            post("/weightlifting/exercises/new") {
                val params = call.receiveParameters()
                val name = params["name"]?.trim().orEmpty()
                val category = params["category"]?.trim().orEmpty()
                val measurementType = params["measurementType"]?.trim().orEmpty()

                if (name.isBlank()) {
                    return@post call.respond(
                        PebbleContent(
                            "add-exercise",
                            mapOf(
                                "error" to "Exercise name is required.",
                                "name" to name,
                                "category" to category,
                                "measurementType" to measurementType
                            )
                        )
                    )
                }

                val weightliftingTypeId = activityService.getActivityTypeByName("Weightlifting")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Weightlifting activity type not found")

                val existingExerciseId = activityService.getExerciseByName(name)
                if (existingExerciseId != null) {
                    return@post call.respond(
                        PebbleContent(
                            "add-exercise",
                            mapOf(
                                "error" to "This exercise already exists.",
                                "name" to name,
                                "category" to category,
                                "measurementType" to measurementType
                            )
                        )
                    )
                }

                activityService.createExercise(
                    Exercise(
                        name = name,
                        activityTypeId = weightliftingTypeId,
                        category = if (category.isBlank()) null else category,
                        measurementType = if (measurementType.isBlank()) null else measurementType
                    )
                )

                call.respondRedirect("/weightlifting/log")
            }

            get("/weightlifting/history") {
                val session = call.principal<UserSession>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

                val user = userService.findByEmail(session.email)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")

                val userId = user.id
                    ?: return@get call.respond(HttpStatusCode.InternalServerError, "User ID not found")

                val history = activityService.getWeightliftingHistory(userId)

                call.respond(
                    PebbleContent(
                        "weightlifting-history",
                        mapOf("history" to history)
                    )
                )
            }

            post("/weightlifting/log") {
                val params = call.receiveParameters()

                val exerciseId = params["exerciseId"]?.toIntOrNull()
                val sets = params["sets"]?.toIntOrNull()
                val reps = params["reps"]?.toIntOrNull()
                val weight = params["weight"]?.toDoubleOrNull()
                val duration = params["duration"]?.toIntOrNull()
                val logDate = params["logDate"] ?: LocalDate.now().toString()
                val notes = params["notes"]

                if (exerciseId == null || sets == null || reps == null || weight == null || duration == null) {
                    return@post call.respond(HttpStatusCode.BadRequest, "Missing or invalid form fields")
                }

                if (sets <= 0 || reps <= 0 || weight < 0 || duration <= 0) {
                    return@post call.respond(
                        HttpStatusCode.BadRequest,
                        "Sets, reps, duration must be greater than 0, and weight cannot be negative"
                    )
                }

                val session = call.principal<UserSession>()
                    ?: return@post call.respond(HttpStatusCode.Unauthorized, "Not logged in")

                val user = userService.findByEmail(session.email)
                    ?: return@post call.respond(HttpStatusCode.NotFound, "User not found")

                val userId = user.id
                    ?: return@post call.respond(HttpStatusCode.InternalServerError, "User ID not found")

                val weightliftingTypeId = activityService.getActivityTypeByName("Weightlifting")
                    ?: return@post call.respond(HttpStatusCode.BadRequest, "Weightlifting activity type not found")

                val workoutLogId = activityService.createWorkoutLog(
                    WorkoutLog(
                        userId = userId,
                        activityTypeId = weightliftingTypeId,
                        logDate = logDate,
                        duration = duration,
                        distance = null,
                        notes = notes,
                        calories = null,
                        source = "gym"
                    )
                )

                activityService.createWorkoutExercise(
                    WorkoutExercise(
                        workoutLogId = workoutLogId,
                        exerciseId = exerciseId,
                        sets = sets,
                        reps = reps,
                        weight = weight
                    )
                )

                call.respondRedirect("/weightlifting/history")
            }

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
                        val inputStream = part.streamProvider()
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

        post("/exercises") {
            val exercise = call.receive<Exercise>()
            val id = activityService.createExercise(exercise)
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

        post("/workout-exercises") {
            val workoutExercise = call.receive<WorkoutExercise>()
            val id = activityService.createWorkoutExercise(workoutExercise)
            call.respond(HttpStatusCode.Created, id)
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