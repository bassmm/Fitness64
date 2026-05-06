package com.fitness64.routes

import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.core.getStartOfWeek
import com.fitness64.schema.PlanService
import com.fitness64.schema.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate

fun Application.configurePlanRoutes(
    planService: PlanService,
    userService: UserService
) {
    routing {
        authenticate("auth-session") {

            get("/onboarding") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val user = auth.user

                val fitnessLevel = user.fitnessLevel ?: "Not set"
                val recommendedPlan = when (fitnessLevel.lowercase()) {
                    "beginner" -> "beginner"
                    "advanced" -> "custom"
                    else -> ""
                }

                call.respondTemplate(
                    "onboarding",
                    mapOf(
                        "fitnessLevel" to fitnessLevel,
                        "recommendedPlan" to recommendedPlan,
                        "error" to ""
                    )
                )
            }

            post("/onboarding") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@post
                val (user, userId) = auth

                val params = call.receiveParameters()
                val planType = params["planType"]?.trim().orEmpty()

                if (planType.isBlank()) {
                    val fitnessLevel = user.fitnessLevel ?: "Not set"
                    val recommendedPlan = when (fitnessLevel.lowercase()) {
                        "beginner" -> "beginner"
                        "advanced" -> "custom"
                        else -> ""
                    }

                    call.respondTemplate(
                        "onboarding",
                        mapOf(
                            "fitnessLevel" to fitnessLevel,
                            "recommendedPlan" to recommendedPlan,
                            "error" to "Please choose a training plan."
                        )
                    )
                    return@post
                }

                planService.generatePlanForType(userId, planType)
                call.respondRedirect("/plan")
            }

            get("/plan") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val (user, userId) = auth

                val startOfWeek = getStartOfWeek(LocalDate.now())
                val planFromDatabase = planService.getPlan(userId)
                val currentPlanType = planService.getPlanType(userId) ?: "No plan selected"

                val weeklyPlan = planFromDatabase.mapIndexed { index, entry ->
                    val date = startOfWeek.plusDays(index.toLong())
                    mapOf(
                        "day" to entry.day,
                        "date" to date.toString(),
                        "session" to entry.session,
                        "durationMinutes" to entry.durationMinutes,
                        "intensity" to entry.intensity,
                        "isRestDay" to entry.isRestDay
                    )
                }

                call.respondTemplate(
                    "plan",
                    mapOf(
                        "weeklyPlan" to weeklyPlan,
                        "fitnessLevel" to (user.fitnessLevel ?: "Beginner"),
                        "planType" to currentPlanType
                    )
                )
            }

            post("/plan/generate") {
                call.respondRedirect("/onboarding")
            }

            get("/plan/update-session") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@get

                val day = call.request.queryParameters["day"]?.trim().orEmpty()

                if (day.isBlank()) {
                    call.respondRedirect("/plan")
                    return@get
                }

                val currentSession = planService.getPlanSessionByDay(userId, day)
                if (currentSession == null) {
                    call.respondRedirect("/plan")
                    return@get
                }

                call.respondTemplate(
                    "update-session",
                    mapOf(
                        "error" to "",
                        "day" to day,
                        "currentSession" to currentSession.session,
                        "newSession" to currentSession.session,
                        "newDuration" to currentSession.durationMinutes,
                        "newIntensity" to currentSession.intensity,
                    )
                )
            }

            post("/plan/update-session") {
                val (_, userId) = call.requireAuthenticatedUser(userService) ?: return@post

                val params = call.receiveParameters()
                val day = params["day"]?.trim().orEmpty()
                val newSession = params["newSession"]?.trim().orEmpty()
                val newDuration = params["newDuration"]?.trim()?.toIntOrNull() ?: 0
                val newIntensity = params["newIntensity"]?.trim().orEmpty()

                if (day.isBlank()) {
                    call.respondRedirect("/plan")
                    return@post
                }

                if (newSession.isBlank()) {
                    val currentSession = planService.getPlanSessionByDay(userId, day)

                    call.respondTemplate(
                        "update-session",
                        mapOf(
                            "error" to "Please enter an updated session.",
                            "day" to day,
                            "currentSession" to (currentSession?.session ?: ""),
                            "newSession" to "",
                            "newDuration" to (currentSession?.durationMinutes ?: 0),
                            "newIntensity" to (currentSession?.intensity ?: ""),
                        )
                    )
                    return@post
                }

                planService.updatePlanSession(userId, day, newSession, newDuration, newIntensity)
                call.respondRedirect("/plan")
            }

        }
    }
}
