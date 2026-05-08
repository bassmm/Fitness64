package com.fitness64.routes

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureCalendarRoutes() {
    routing {
        authenticate("auth-session") {
            get("/calendar") {
                call.respondRedirect("/activities")
            }
        }
    }
}
