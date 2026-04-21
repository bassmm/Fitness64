package com.fitness64.activities

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureActivityRoutes(activityService: ActivityService) {
    routing {
        // Create a workout log
        post("/workouts") {
            val workout = call.receive<ExposedWorkoutLog>()
            val id = activityService.createWorkoutLog(workout)
            call.respond(HttpStatusCode.Created, id)
        }

        // Create a workout lap
        post("/laps") {
            val lap = call.receive<ExposedWorkoutLap>()
            val id = activityService.createWorkoutLap(lap)
            call.respond(HttpStatusCode.Created, id)
        }

        // Create a trackpoint
        post("/trackpoints") {
            val trackpoint = call.receive<ExposedTrackpoint>()
            val id = activityService.createTrackpoint(trackpoint)
            call.respond(HttpStatusCode.Created, id)
        }

        // Read all trackpoints for a lap
        get("/laps/{id}/trackpoints") {
            val id = call.parameters["id"]?.toInt()
                ?: throw IllegalArgumentException("Invalid lap ID")
            val trackpoints = activityService.getTrackpointsForLap(id)
            call.respond(HttpStatusCode.OK, trackpoints)
        }
    }
}