package com.fitness64.routes

import com.fitness64.core.UserSession
import com.fitness64.schema.PlanService
import com.fitness64.schema.User
import com.fitness64.schema.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.http.content.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt

fun Application.configureRouting(
    userService: UserService,
    planService: PlanService
) {
    routing {
        staticResources("/assets", "assets")

        get("/") {
            call.respondRedirect("/home")
        }

        get("/login") {
            call.respondTemplate("login", mapOf("error" to "", "email" to ""))
        }

        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                call.respondTemplate("login", mapOf("error" to "Please enter both email and password.", "email" to email))
                return@post
            }

            val user = userService.findByEmail(email)
            if (user == null || !BCrypt.checkpw(password, user.passwordHash)) {
                call.respondTemplate("login", mapOf("error" to "Invalid email or password.", "email" to email))
                return@post
            }

            call.sessions.set(UserSession(email = email))

            val userId = user.id
            if (userId != null && !planService.hasPlan(userId)) {
                call.respondRedirect("/onboarding")
            } else {
                call.respondRedirect("/home")
            }
        }

        get("/register") {
            call.respondTemplate("register", mapOf("error" to "", "name" to "", "email" to ""))
        }

        post("/register") {
            val params = call.receiveParameters()
            val name = params["name"]?.trim().orEmpty()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()
            val fitnessLevel = params["fitnessLevel"]?.trim().orEmpty()

            if (name.isBlank() || email.isBlank() || password.isBlank() || fitnessLevel.isBlank()) {
                call.respondTemplate("register", mapOf("error" to "Please complete all required fields.", "name" to name, "email" to email))
                return@post
            }

            if (userService.findByEmail(email) != null) {
                call.respondTemplate("register", mapOf("error" to "An account with this email already exists.", "name" to name, "email" to email))
                return@post
            }

            val newUser = User(
                name = name, email = email, passwordHash = password, fitnessLevel = fitnessLevel
            )

            try {
                userService.create(newUser)
                call.respondRedirect("/login")
            } catch (e: Exception) {
                call.respondTemplate("register", mapOf("error" to "An error occurred during registration. Please try again.", "name" to name, "email" to email))
            }
        }

        authenticate("auth-session") {
            get("/logout") {
                call.sessions.clear<UserSession>()
                call.respondRedirect("/login")
            }
        }
    }
}
