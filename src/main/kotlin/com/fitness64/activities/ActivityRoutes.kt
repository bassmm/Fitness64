package com.fitness64.activities

import com.fitness64.UserSession
import com.fitness64.races.RaceService
import com.fitness64.users.UserService
import com.fitness64.weightlifting.WeightliftingService
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

private data class ActivityFeedItem(
    val date: String,
    val category: String,
    val title: String,
    val summary: String,
    val metric: String,
    val notes: String
)

fun Application.configureActivityRoutes(
    activityService: ActivityService,
    userService: UserService,
    weightliftingService: WeightliftingService,
    raceService: RaceService
) {
    routing {
        authenticate("auth-session") {
            get("/activities") {
                val session = call.principal<UserSession>()
                    ?: return@get call.respond(HttpStatusCode.Unauthorized, "Not logged in")

                val user = userService.findByEmail(session.email)
                    ?: return@get call.respond(HttpStatusCode.NotFound, "User not found")

                val userId = user.id
                    ?: return@get call.respond(HttpStatusCode.InternalServerError, "User ID not found")

                val cardioHistory = activityService.getCardioHistory(userId)
                val weightliftingHistory = weightliftingService.getWeightliftingHistory(userId)
                val raceHistory = raceService.getRacesForUser(userId)

                val cardioItems = cardioHistory.map { item ->
                    ActivityFeedItem(
                        date = item.logDate,
                        category = "Cardio",
                        title = item.activityType,
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

                    ActivityFeedItem(
                        date = item.logDate,
                        category = "Weightlifting",
                        title = "Weightlifting session",
                        summary = exerciseSummary,
                        metric = "${item.duration} min",
                        notes = item.notes ?: ""
                    )
                }

                val raceItems = raceHistory.map { race ->
                    val metric = race.finishTime
                        ?: race.overallRank?.let { "Overall #$it" }
                        ?: "Completed"

                    val summary = listOfNotNull(race.location, race.category).joinToString(" • ")
                        .ifBlank { "Race logged" }

                    val notePrefix = if (race.isPersonalBest) "Personal best. " else ""
                    val noteBody = race.certificateUrl?.let { "Certificate: $it" } ?: ""

                    ActivityFeedItem(
                        date = race.eventDate,
                        category = "Race",
                        title = race.eventName,
                        summary = summary,
                        metric = metric,
                        notes = "$notePrefix$noteBody".trim()
                    )
                }

                val activities = (cardioItems + weightliftingItems + raceItems)
                    .sortedByDescending { it.date }

                call.respond(
                    PebbleContent(
                        "activity-history",
                        mapOf("activities" to activities)
                    )
                )
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
