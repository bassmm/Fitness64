package com.comp2850

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import org.jetbrains.exposed.sql.*

fun Application.configureDatabases() {
    val database = Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )
    val userService = UserService(database)
    val tcxService = TcxService(database)
    routing {
        // Create user
        post("/users") {
            val user = call.receive<ExposedUser>()
            val id = userService.create(user)
            call.respond(HttpStatusCode.Created, id)
        }

        // Read user
        get("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = userService.read(id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        // Update user
        put("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = call.receive<ExposedUser>()
            userService.update(id, user)
            call.respond(HttpStatusCode.OK)
        }

        // Delete user
        delete("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            userService.delete(id)
            call.respond(HttpStatusCode.OK)
        }
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
