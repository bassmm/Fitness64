package com.comp2850

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.principal
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*

fun Application.configureRouting() {
    val userService = attributes[UserServiceKey]

    routing {
        get("/") {
            call.respondText("Fitness app is running")
        }

        route("/api/auth") {
            post("/register") {
                val request = call.receive<RegisterRequest>()

                if (request.username.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Username is required"),
                    )
                    return@post
                }

                if (request.email.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Email is required"),
                    )
                    return@post
                }

                if (request.password.length < 8) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Password must be at least 8 characters long"),
                    )
                    return@post
                }

                val user = userService.register(request)

                if (user == null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("error" to "Username or email already exists"),
                    )
                    return@post
                }

                call.sessions.set(UserSession(user.userId))

                call.respond(
                    HttpStatusCode.Created,
                    AuthResponse(
                        user = user,
                        message = "Registration successful",
                    ),
                )
            }

            post("/login") {
                val request = call.receive<LoginRequest>()

                if (request.email.isBlank() || request.password.isBlank()) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        mapOf("error" to "Email and password are required"),
                    )
                    return@post
                }

                val user = userService.authenticate(request)

                if (user == null) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        mapOf("error" to "Invalid email or password"),
                    )
                    return@post
                }

                call.sessions.set(UserSession(user.userId))

                call.respond(
                    HttpStatusCode.OK,
                    AuthResponse(
                        user = user,
                        message = "Login successful",
                    ),
                )
            }

            post("/logout") {
                call.sessions.clear<UserSession>()
                call.respond(
                    HttpStatusCode.OK,
                    mapOf("message" to "Logged out"),
                )
            }
        }

        authenticate("auth-session") {
            route("/api/profile") {
                get("/me") {
                    val session = call.principal<UserSession>()
                        ?: return@get call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "You must be logged in"),
                        )

                    val profile = userService.getProfile(session.userId)

                    if (profile == null) {
                        call.respond(
                            HttpStatusCode.NotFound,
                            mapOf("error" to "User not found"),
                        )
                        return@get
                    }

                    call.respond(HttpStatusCode.OK, profile)
                }

                put("/me") {
                    val session = call.principal<UserSession>()
                        ?: return@put call.respond(
                            HttpStatusCode.Unauthorized,
                            mapOf("error" to "You must be logged in"),
                        )

                    val request = call.receive<UpdateProfileRequest>()

                    if (request.email.isBlank()) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            mapOf("error" to "Email is required"),
                        )
                        return@put
                    }

                    val result = userService.updateProfile(session.userId, request)

                    when (result.status) {
                        UpdateProfileStatus.EMAIL_ALREADY_IN_USE -> {
                            call.respond(
                                HttpStatusCode.Conflict,
                                mapOf("error" to "Email already in use"),
                            )
                        }

                        UpdateProfileStatus.USER_NOT_FOUND -> {
                            call.respond(
                                HttpStatusCode.NotFound,
                                mapOf("error" to "User not found"),
                            )
                        }

                        UpdateProfileStatus.UPDATED -> {
                            call.respond(
                                HttpStatusCode.OK,
                                result.profile ?: mapOf("message" to "Profile updated"),
                            )
                        }
                    }
                }
            }
        }
    }
}
