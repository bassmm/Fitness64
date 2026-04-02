package com.comp2850

import io.ktor.server.application.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondRedirect("/login")
        }

        get("/login") {
            call.respondTemplate("login", emptyMap())
        }

        post("/login") {
            val params = call.receiveParameters()
            val email = params["email"]
            call.respondRedirect("/home")
        }

        get("/home") {
            call.respondTemplate(
                "home",
                mapOf(
                    "user" to "Sarah",
                    "today" to "Run 5km"
                )
            )
        }

        get("/log") {
            call.respondTemplate("log", emptyMap())
        }

        post("/log") {
            call.respondRedirect("/home")
        }

        get("/quick-log") {
            call.respondTemplate("quick-log", emptyMap())
        }

        post("/quick-log") {
            call.respondRedirect("/log")
        }

        get("/plan") {
            call.respondTemplate(
                "plan",
                mapOf(
                    "plan" to listOf(
                        "Monday: Run 5km",
                        "Tuesday: Gym",
                        "Wednesday: Rest",
                        "Thursday: Cycling 10km",
                        "Friday: Gym",
                        "Saturday: Long Run",
                        "Sunday: Rest"
                    )
                )
            )
        }

        get("/progress") {
            call.respondTemplate(
                "progress",
                mapOf(
                    "stats" to "5 workouts this week",
                    "pb" to "5km: 25min"
                )
            )
        }

        get("/pebble-index") {
            call.respondTemplate(
                "pebble-index",
                mapOf(
                    "user" to mapOf("name" to "John")
                )
            )
        }
    }
}