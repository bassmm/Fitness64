package com.fitness64.tcx

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureTcxRoutes(tcxService: TcxService) {
    routing {
        // Create a workout log
        post("/workouts") {
            val workout = call.receive<ExposedWorkoutLog>()
            val id = tcxService.createWorkoutLog(workout)
            call.respond(HttpStatusCode.Created, id)
        }

        // Create a workout lap
        post("/laps") {
            val lap = call.receive<ExposedWorkoutLap>()
            val id = tcxService.createWorkoutLap(lap)
            call.respond(HttpStatusCode.Created, id)
        }

        // Create a trackpoint
        post("/trackpoints") {
            val trackpoint = call.receive<ExposedTrackpoint>()
            val id = tcxService.createTrackpoint(trackpoint)
            call.respond(HttpStatusCode.Created, id)
        }

        // Read all trackpoints for a lap
        get("/laps/{id}/trackpoints") {
            val id = call.parameters["id"]?.toInt()
                ?: throw IllegalArgumentException("Invalid lap ID")
            val trackpoints = tcxService.getTrackpointsForLap(id)
            call.respond(HttpStatusCode.OK, trackpoints)
        }
    }
}
