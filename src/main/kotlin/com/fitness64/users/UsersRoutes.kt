package com.fitness64.users

import com.fitness64.UserSession
import com.fitness64.plans.PlanService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

fun Application.configureUsersRoutes(
    userService: UserService,
    planService: PlanService
) {
    routing {
        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()

            if (email.isBlank() || password.isBlank()) {
                call.respondTemplate(
                    "login",
                    mapOf(
                        "error" to "Please enter both email and password.",
                        "email" to email
                    )
                )
                return@post
            }

            val user = userService.findByEmail(email)

            if (user == null || !BCrypt.checkpw(password, user.passwordHash)) {
                call.respondTemplate(
                    "login",
                    mapOf(
                        "error" to "Invalid email or password.",
                        "email" to email
                    )
                )
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

        post("/register") {
            val params = call.receiveParameters()
            val name = params["name"]?.trim().orEmpty()
            val email = params["email"]?.trim().orEmpty()
            val password = params["password"]?.trim().orEmpty()
            val fitnessLevel = params["fitnessLevel"]?.trim().orEmpty()

            if (name.isBlank() || email.isBlank() || password.isBlank() || fitnessLevel.isBlank()) {
                call.respondTemplate(
                    "register",
                    mapOf(
                        "error" to "Please complete all required fields.",
                        "name" to name,
                        "email" to email
                    )
                )
                return@post
            }

            if (userService.findByEmail(email) != null) {
                call.respondTemplate(
                    "register",
                    mapOf(
                        "error" to "An account with this email already exists.",
                        "name" to name,
                        "email" to email
                    )
                )
                return@post
            }

            val newUser = User(
                name = name,
                email = email,
                passwordHash = password,
                fitnessLevel = fitnessLevel
            )

            try {
                userService.create(newUser)
                call.respondRedirect("/login")
            } catch (e: Exception) {
                call.respondTemplate(
                    "register",
                    mapOf(
                        "error" to "An error occurred during registration. Please try again.",
                        "name" to name,
                        "email" to email
                    )
                )
            }
        }

        post("/signup") {
            val user = call.receive<User>()
            // Check if user already exists
            if (userService.findByEmail(user.email) != null) {
                call.respond(HttpStatusCode.Conflict, "User with this email already exists")
                return@post
            }
            val id = userService.create(user)
            call.sessions.set(UserSession(user.email))
            call.respond(HttpStatusCode.Created, id)
        }

        authenticate("auth-form") {
            post("/login") {
                val principal = call.principal<UserIdPrincipal>()
                if (principal != null) {
                    call.sessions.set(UserSession(principal.name))
                    call.respond(HttpStatusCode.OK, "Logged in successfully")
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                }
            }
        }

        authenticate("auth-session") {
            get("/logout") {
                call.sessions.clear<UserSession>()
                call.respond(HttpStatusCode.OK, "Logged out successfully")
            }

            get("/home") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                val today = LocalDate.now()
                val todayDate = today.toString()
                val todayDayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

                val todayTraining = user?.id?.let { userId ->
                    planService.getPlanSessionByDay(userId, todayDayName)?.session
                } ?: "No training planned"

                call.respondTemplate(
                    "home",
                    mapOf(
                        "user" to (user?.name ?: session.email),
                        "today" to todayTraining,
                        "todayDate" to todayDate,
                        "streak" to "3-week streak",
                        "nextGoal" to "Complete 6 workouts next week",
                        "achievement" to "New 5km personal best"
                    )
                )
            }

            get("/profile") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)

                call.respondTemplate(
                    "profile",
                    mapOf(
                        "name" to (user?.name ?: session.email),
                        "email" to session.email,
                        "fitnessLevel" to (user?.fitnessLevel ?: "Not set"),
                        "goals" to (user?.goal ?: "No goals set"),
                        "activities" to listOf("Running", "Cycling", "Weightlifting"),
                        "community" to "Training group: Leeds Runners"
                    )
                )
            }

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

