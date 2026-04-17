package com.fitness64

import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt

@Serializable
data class UserSession(val email: String)

fun Application.configureSecurity(userService: UserService) {
    install(Sessions) {
        cookie<UserSession>("user_session") {
            cookie.path = "/"
            cookie.maxAgeInSeconds = 3600 * 24 * 7 // 1 week
        }
    }

    install(Authentication) {
        form("auth-form") {
            userParamName = "email"
            passwordParamName = "password"

            validate { credentials ->
                val user = userService.findByEmail(credentials.name)

                // Compare the plain text password from the form against the BCrypt hash in the DB
                if (user != null && BCrypt.checkpw(credentials.password, user.passwordHash)) {
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }

            challenge {
                call.respondRedirect("/login")
            }
        }

        session<UserSession>("auth-session") {
            validate { session -> session }

            challenge {
                call.respondRedirect("/login")
            }
        }
    }
}
