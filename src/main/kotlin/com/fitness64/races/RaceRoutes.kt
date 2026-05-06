/**
 * RaceRoutes.kt
 *
 * Defines the API routes for race record management.
 * Provides endpoints for creating, retrieving, and deleting race records.
 * All routes are protected and require an authenticated session.
 */
package com.fitness64.races

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Registers all race-related API routes on the application.
 * All routes are wrapped in an authenticated session block.
 *
 * @param raceService The service used to perform race database operations.
 */
fun Application.configureRaceRoutes(raceService: RaceService) {
    routing {

        authenticate("auth-session") {

            /**
             * POST /races
             * Creates a new race record from the request body.
             * Responds with 201 Created and the new race ID on success.
             */
            post("/races") {
                val race = call.receive<RaceRecord>()
                val id = raceService.createRace(race)
                call.respond(HttpStatusCode.Created, id)
            }

            /**
             * GET /races/{id}
             * Retrieves a single race record by its ID.
             * Responds with 200 OK and the race data, or 404 if not found.
             */
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

            /**
             * GET /users/{userId}/races
             * Retrieves all race records for a specific user.
             * Responds with 200 OK and a list of race records.
             */
            get("/users/{userId}/races") {
                val userId = call.parameters["userId"]?.toInt()
                    ?: return@get call.respond(HttpStatusCode.BadRequest, "Invalid user ID")
                val races = raceService.getRacesForUser(userId)
                call.respond(HttpStatusCode.OK, races)
            }

            /**
             * DELETE /races/{id}
             * Deletes a race record by its ID.
             * Responds with 200 OK on success.
             */
            delete("/races/{id}") {
                val id = call.parameters["id"]?.toInt()
                    ?: return@delete call.respond(HttpStatusCode.BadRequest, "Invalid race ID")
                raceService.deleteRace(id)
                call.respond(HttpStatusCode.OK, "Race deleted")
            }
        }
    }
}