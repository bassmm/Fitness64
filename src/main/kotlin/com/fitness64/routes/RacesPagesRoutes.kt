package com.fitness64.routes

import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.schema.RaceRecord
import com.fitness64.schema.RaceService
import com.fitness64.schema.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URI


private fun normaliseCertificateUrl(value: String): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null

    val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null

    return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
        trimmed
    } else {
        null
    }
}

fun Application.configureRacesPagesRoutes(
    userService: UserService,
    raceService: RaceService
) {
    routing {
        authenticate("auth-session") {
            get("/races") {
                call.respondRedirect("/activities?filter=races")
            }

            get("/races/log") {
                val eventDate = call.request.queryParameters["date"] ?: ""

                call.respondTemplate("race-log", mapOf(
                    "error" to "",
                    "eventName" to "",
                    "eventDate" to eventDate,
                    "location" to "",
                    "category" to "",
                    "finishTime" to "",
                    "overallRank" to "",
                    "categoryRank" to "",
                    "certificateUrl" to "",
                    "isPersonalBest" to false
                ))
            }

            post("/races/log") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@post

                val params = call.receiveParameters()
                val eventName = params["eventName"]?.trim().orEmpty()
                val eventDate = params["eventDate"]?.trim().orEmpty()
                val location = params["location"]?.trim().orEmpty()
                val category = params["category"]?.trim().orEmpty()
                val finishTime = params["finishTime"]?.trim().orEmpty()
                val overallRankText = params["overallRank"]?.trim().orEmpty()
                val categoryRankText = params["categoryRank"]?.trim().orEmpty()
                val certificateUrlText = params["certificateUrl"]?.trim().orEmpty()
                val certificateUrl = normaliseCertificateUrl(certificateUrlText)
                val isPersonalBest = params["isPersonalBest"] == "true"

                if (certificateUrlText.isNotBlank() && certificateUrl == null) {
                    call.respondTemplate("race-log", mapOf(
                        "error" to "Certificate URL must start with http:// or https://.",
                        "eventName" to eventName,
                        "eventDate" to eventDate,
                        "location" to location,
                        "category" to category,
                        "finishTime" to finishTime,
                        "overallRank" to overallRankText,
                        "categoryRank" to categoryRankText,
                        "certificateUrl" to certificateUrlText,
                        "isPersonalBest" to isPersonalBest
                    ))
                    return@post
                }

                if (eventName.isBlank() || eventDate.isBlank()) {
                    call.respondTemplate("race-log", mapOf(
                        "error" to "Race name and date are required.",
                        "eventName" to eventName,
                        "eventDate" to eventDate,
                        "location" to location,
                        "category" to category,
                        "finishTime" to finishTime,
                        "overallRank" to overallRankText,
                        "categoryRank" to categoryRankText,
                        "certificateUrl" to certificateUrl,
                        "isPersonalBest" to isPersonalBest
                    ))
                    return@post
                }

                raceService.createRace(
                    RaceRecord(
                        userId = userId,
                        eventName = eventName,
                        eventDate = eventDate,
                        location = location.ifBlank { null },
                        category = category.ifBlank { null },
                        finishTime = finishTime.ifBlank { null },
                        overallRank = overallRankText.toIntOrNull(),
                        categoryRank = categoryRankText.toIntOrNull(),
                        isPersonalBest = isPersonalBest,
                        certificateUrl = certificateUrl
                    )
                )

                call.respondRedirect("/activities?filter=races")
            }
        }
    }
}
