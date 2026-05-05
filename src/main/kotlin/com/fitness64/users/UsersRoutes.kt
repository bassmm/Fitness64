/**
 * UsersRoutes.kt
 *
 * Defines the API routes for user account management.
 * Provides endpoints for user registration, profile retrieval,
 * and full CRUD operations on user accounts.
 * The /me route is protected and requires an authenticated session.
 */
package com.fitness64.users

import com.fitness64.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

/**
 * Registers all user-related API routes on the application.
 *
 * @param userService The service used to perform user database operations.
 */
fun Application.configureUsersRoutes(
    userService: UserService
) {
    routing {

        /**
         * POST /signup
         * Registers a new user account and creates a session.
         * Responds with 409 Conflict if the email is already registered,
         * or 201 Created with the new user ID on success.
         */
        post("/signup") {
            val user = call.receive<User>()
            if (userService.findByEmail(user.email) != null) {
                call.respond(HttpStatusCode.Conflict, "User with this email already exists")
                return@post
            }
            val id = userService.create(user)
            call.sessions.set(UserSession(user.email))
            call.respond(HttpStatusCode.Created, id)
        }

        authenticate("auth-session") {

            /**
             * GET /me
             * Retrieves the currently authenticated user's profile.
             * Responds with 200 OK and the user data, or 404 if not found.
             */
            get("/me") {
                val userSession = call.principal<UserSession>()
                if (userSession != null) {
                    val user = userService.findByEmail(userSession.email)
                    if (user != null) {
                        call.respond(HttpStatusCode.OK, user)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found")
                    }
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Not logged in")
                }
            }
        }

        /**
         * POST /users
         * Creates a new user via the REST API.
         * Responds with 201 Created and the new user ID.
         */
        post("/users") {
            val user = call.receive<User>()
            val id = userService.create(user)
            call.respond(HttpStatusCode.Created, id)
        }

        /**
         * GET /users/{id}
         * Retrieves a user by their ID via the REST API.
         * Responds with 200 OK and the user data, or 404 if not found.
         */
        get("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = userService.read(id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        /**
         * PUT /users/{id}
         * Updates an existing user record via the REST API.
         * Responds with 200 OK on success.
         */
        put("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = call.receive<User>()
            userService.update(id, user)
            call.respond(HttpStatusCode.OK)
        }

        /**
         * DELETE /users/{id}
         * Deletes a user account by their ID via the REST API.
         * Responds with 200 OK on success.
         */
        delete("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            userService.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}