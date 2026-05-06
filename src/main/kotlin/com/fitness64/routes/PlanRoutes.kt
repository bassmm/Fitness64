/**
 * PlanRoutes.kt
 *
 * Defines the UI routes for training plan management.
 * Handles the onboarding flow for new users selecting a plan,
 * displaying the weekly plan, and updating individual sessions.
 * All routes are protected and require an authenticated session.
 */
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

/**
 * Registers all training plan related routes on the application.
 *
 * @param planService The service used for training plan database operations.
 * @param userService The service used for user lookup and authentication.
 */
fun Application.configurePlanRoutes(
    planService: PlanService,
    userService: UserService
) {
    routing {
        authenticate("auth-session") {

            /**
             * GET /onboarding
             * Displays the onboarding page for new users to select a training plan.
             * Recommends a plan based on the user's fitness level.
             */
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

            /**
             * POST /onboarding
             * Processes the onboarding form submission.
             * Generates a new training plan for the user based on their selected plan type.
             * Redirects to /plan on success, or re-renders the form with an error if no plan was selected.
             */
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

            /**
             * GET /plan
             * Displays the user's current weekly training plan.
             * Maps each session to its corresponding calendar date for the current week.
             */
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

            /**
             * POST /plan/generate
             * Redirects to the onboarding page to allow the user to select a new plan type.
             */
            post("/plan/generate") {
                call.respondRedirect("/onboarding")
            }

            /**
             * GET /plan/update-session
             * Displays the form for updating a specific day's planned session.
             * Pre-fills the form with the current session details for that day.
             * Redirects to /plan if the day parameter is missing or invalid.
             */
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

            /**
             * POST /plan/update-session
             * Processes the session update form submission.
             * Validates the new session name and updates the plan in the database.
             * Redirects to /plan on success, or re-renders the form with an error if validation fails.
             */
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