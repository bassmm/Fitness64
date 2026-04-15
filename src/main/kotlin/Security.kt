package com.comp2850

import io.ktor.server.application.*
import io.ktor.server.sessions.*
import kotlinx.serialization.Serializable

@Serializable
data class UserSession(
    val name: String,
    val email: String,
    val fitnessLevel: String
)

fun Application.configureSecurity() {
    install(Sessions) {
        cookie<UserSession>("user_session")
    }
}