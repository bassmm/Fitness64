package com.fitness64.users

import com.fitness64.UserSession
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureUsersRoutes(
    userService: UserService
) {
    routing {
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

        post("/users") {
            val user = call.receive<User>()
            val id = userService.create(user)
            call.respond(HttpStatusCode.Created, id)
        }

        get("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = userService.read(id)
            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound)
            }
        }

        put("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            val user = call.receive<User>()
            userService.update(id, user)
            call.respond(HttpStatusCode.OK)
        }

        delete("/users/{id}") {
            val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
            userService.delete(id)
            call.respond(HttpStatusCode.OK)
        }
    }
}

