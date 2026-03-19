package com.comp2850

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.session.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*

data class UserSession(val userId: Int) : Principal

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<UserSession>("fitness_session") {
            cookie.path = "/"
            cookie.httpOnly = true
        }
    }

    install(Authentication) {
        session<UserSession>("auth-session") {
            validate { session ->
                session
            }

            challenge {
                call.respond(
                    HttpStatusCode.Unauthorized,
                    mapOf("error" to "You must be logged in"),
                )
            }
        }
    }
}
