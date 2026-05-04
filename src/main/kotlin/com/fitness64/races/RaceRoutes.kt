package com.fitness64.races

import com.fitness64.UserSession
import com.fitness64.users.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRaceRoutes(
    raceService: RaceService,
    userService: UserService
) {
    routing {

        authenticate("auth-session") {
            get("/races") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                if (user == null) {
                    call.respondRedirect("/login")
                    return@get
                }

                call.respondTemplate(
                    "races",
                    mapOf("races" to emptyList<Any>())
                )
            }

            // Log a new race
            post("/races") {
                val race = call.receive<RaceRecord>()
                val id = raceService.createRace(race)
                call.respond(HttpStatusCode.Created, id)
            }

            // Get a specific race by ID
            get("/races/{id}") {
                val id = call.parameters["id"]?.toInt()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid race ID")
                val race = raceService.getRace(id)
                if (race != null) {
                    call.respond(HttpStatusCode.OK, race)
                } else {
                    call.respond(HttpStatusCode.NotFound, "Race not found")
                }
            }

            // Get all races for a user
            get("/users/{userId}/races") {
                val userId = call.parameters["userId"]?.toInt()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                val races = raceService.getRacesForUser(userId)
                call.respond(HttpStatusCode.OK, races)
            }

            // Delete a race
            delete("/races/{id}") {
                val id = call.parameters["id"]?.toInt()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid race ID")
                raceService.deleteRace(id)
                call.respond(HttpStatusCode.OK, "Race deleted")
            }
        }
    }
}
