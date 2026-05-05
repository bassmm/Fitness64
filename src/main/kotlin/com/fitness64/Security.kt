/**
 * Security.kt
 *
 * Configures authentication and session management for the application.
 * Sets up cookie-based user sessions and two authentication providers:
 * form-based login validation and session-based route protection.
 */
package com.fitness64

import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable
import org.mindrot.jbcrypt.BCrypt

/**
 * Represents an authenticated user session stored in a cookie.
 *
 * @property email The email address of the authenticated user.
 */
@Serializable
data class UserSession(val email: String)

/**
 * Configures session management and authentication providers for the application.
 *
 * Sets up:
 * - A cookie-based session stored as [UserSession] lasting 7 days.
 * - A form authentication provider ("auth-form") that validates email and password
 *   against BCrypt-hashed passwords stored in the database.
 * - A session authentication provider ("auth-session") that protects routes
 *   by verifying an active [UserSession] cookie exists.
 *
 * @param userService The service used to look up users by email during authentication.
 */
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