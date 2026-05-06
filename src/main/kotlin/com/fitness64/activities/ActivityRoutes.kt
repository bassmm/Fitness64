/**
 * ActivityRoutes.kt
 *
 * Defines the API and UI routes for activity management.
 * Handles the unified activity history feed (cardio, weightlifting, races),
 * TCX file upload and parsing, and REST API endpoints for workout logs,
 * laps, trackpoints, and activity types.
 */
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

/**
 * Internal data class representing a single item in the unified activity feed.
 *
 * @property date The date of the activity (ISO format: yyyy-MM-dd).
 * @property category The category of activity (e.g. Cardio, Weightlifting, Race).
 * @property title The display title of the activity.
 * @property summary A brief summary of the activity (e.g. distance or exercises).
 * @property metric A key metric for the activity (e.g. duration, finish time).
 * @property notes Any additional notes about the activity.
 */
private data class ActivityFeedItem(
    val date: String,
    val category: String,
    val title: String,
    val summary: String,
    val metric: String,
    val notes: String
)

/**
 * Registers all activity-related routes on the application.
 * Combines cardio, weightlifting, and race history into a unified feed,
 * handles TCX file uploads, and exposes REST API endpoints for workouts.
 *
 * @param activityService The service for cardio workout database operations.
 * @param userService The service for user lookup and authentication.
 * @param weightliftingService The service for weightlifting session database operations.
 * @param raceService The service for race record database operations.
 */
fun Application.configureActivityRoutes(
    activityService: ActivityService,
    userService: UserService,
    weightliftingService: WeightliftingService,
    raceService: RaceService
) {
    routing {
        authenticate("auth-session") {

            /**
             * GET /activities
             * Displays the unified activity history feed combining cardio sessions,
             * weightlifting sessions, and race results sorted by date descending.
             * Also passes weightlifting volume data for the chart.
             */
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

                // Weightlifting volume over time for chart
                val volumeBySession = weightliftingHistory
                    .sortedBy { it.logDate }
                    .map { item ->
                        mapOf(
                            "date" to item.logDate,
                            "totalSets" to item.totalSets
                        )
                    }

                val activities = (cardioItems + weightliftingItems + raceItems)
                    .sortedByDescending { it.date }

                call.respond(
                    PebbleContent(
                        "activity-history",
                        mapOf(
                            "activities" to activities,
                            "volumeBySession" to volumeBySession
                        )
                    )
                )
            }

            /**
             * GET /tcx/upload
             * Displays the TCX file upload page.
             */
            get("/tcx/upload") {
                call.respond(
                    PebbleContent(
                        "tcx-upload",
                        mapOf("error" to "", "success" to "")
                    )
                )
            }

            /**
             * POST /tcx/upload
             * Handles TCX file upload from a fitness device or export service.
             * Parses the file and saves all laps and trackpoints to the database.
             * Responds with a success message showing the number of laps and trackpoints saved,
             * or an error message if no file was provided or parsing failed.
             */
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

        /**
         * POST /activity-types
         * Creates a new activity type via the REST API.
         * Responds with 201 Created and the new activity type ID.
         */
        post("/activity-types") {
            val activityType = call.receive<ActivityType>()
            val id = activityService.createActivityType(activityType)
            call.respond(HttpStatusCode.Created, id)
        }

        /**
         * POST /workouts
         * Creates a new workout log entry via the REST API.
         * Responds with 201 Created and the new workout log ID.
         */
        post("/workouts") {
            val workout = call.receive<WorkoutLog>()
            val id = activityService.createWorkoutLog(workout)
            call.respond(HttpStatusCode.Created, id)
        }

        /**
         * GET /workouts/{id}
         * Retrieves a single workout log by its ID via the REST API.
         * Responds with 200 OK and the workout data, or 404 if not found.
         */
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

        /**
         * GET /users/{userId}/workouts
         * Retrieves all workout logs for a specific user via the REST API.
         * Responds with 200 OK and a list of workout logs.
         */
        get("/users/{userId}/workouts") {
            val userId = call.parameters["userId"]?.toInt()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
            val workouts = activityService.getWorkoutsForUser(userId)
            call.respond(HttpStatusCode.OK, workouts)
        }

        /**
         * DELETE /workouts/{id}
         * Deletes a workout log by its ID via the REST API.
         * Responds with 200 OK on success.
         */
        delete("/workouts/{id}") {
            val id = call.parameters["id"]?.toInt()
                ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid workout ID")
            activityService.deleteWorkoutLog(id)
            call.respond(HttpStatusCode.OK, "Workout deleted")
        }

        /**
         * POST /laps
         * Creates a new workout lap entry via the REST API.
         * Responds with 201 Created and the new lap ID.
         */
        post("/laps") {
            val lap = call.receive<WorkoutLap>()
            val id = activityService.createWorkoutLap(lap)
            call.respond(HttpStatusCode.Created, id)
        }

        /**
         * POST /trackpoints
         * Creates a new GPS trackpoint entry via the REST API.
         * Responds with 201 Created and the new trackpoint ID.
         */
        post("/trackpoints") {
            val trackpoint = call.receive<Trackpoint>()
            val id = activityService.createTrackpoint(trackpoint)
            call.respond(HttpStatusCode.Created, id)
        }

        /**
         * GET /laps/{id}/trackpoints
         * Retrieves all trackpoints for a specific lap via the REST API.
         * Responds with 200 OK and a list of trackpoints.
         */
        get("/laps/{id}/trackpoints") {
            val id = call.parameters["id"]?.toInt()
                ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid lap ID")
            val trackpoints = activityService.getTrackpointsForLap(id)
            call.respond(HttpStatusCode.OK, trackpoints)
        }
    }
}