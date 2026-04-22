package com.fitness64.activities

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureActivityRoutes(activityService: ActivityService) {
    routing {

        // --- Activity Types ---

        post("/activity-types") {
            val activityType = call.receive<ActivityType>()
            val id = activityService.createActivityType(activityType)
            call.respond(HttpStatusCode.Created, id)
        }

        // --- Exercises ---

        post("/exercises") {
            val exercise = call.receive<Exercise>()
            val id = activityService.createExercise(exercise)
            call.respond(HttpStatusCode.Created, id)
        }

        // --- Workout Logs ---

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

        // --- Workout Exercises ---

        post("/workout-exercises") {
            val workoutExercise = call.receive<WorkoutExercise>()
            val id = activityService.createWorkoutExercise(workoutExercise)
            call.respond(HttpStatusCode.Created, id)
        }

        // --- Laps ---

        post("/laps") {
            val lap = call.receive<WorkoutLap>()
            val id = activityService.createWorkoutLap(lap)
            call.respond(HttpStatusCode.Created, id)
        }

        // --- Trackpoints ---

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