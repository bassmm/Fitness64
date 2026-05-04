package com.fitness64.plans

import com.fitness64.UserSession
import com.fitness64.getStartOfWeek
import com.fitness64.loggedActivities
import com.fitness64.users.UserService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

fun Application.configurePlanRoutes(
    planService: PlanService,
    userService: UserService
) {
    routing {
        authenticate("auth-session") {

            get("/onboarding") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

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
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@post call.respondRedirect("/login")

                val userId = user.id
                    ?: return@post call.respondRedirect("/login")

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
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val userId = user.id
                    ?: return@get call.respondRedirect("/login")

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

            get("/plan/replace") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val userId = user.id
                    ?: return@get call.respondRedirect("/login")

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
                    "replace-session",
                    mapOf(
                        "error" to "",
                        "day" to day,
                        "currentSession" to currentSession.session
                    )
                )
            }

            post("/plan/replace") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@post call.respondRedirect("/login")

                val userId = user.id
                    ?: return@post call.respondRedirect("/login")

                val params = call.receiveParameters()
                val day = params["day"]?.trim().orEmpty()
                val newSession = params["newSession"]?.trim().orEmpty()

                if (day.isBlank()) {
                    call.respondRedirect("/plan")
                    return@post
                }

                if (newSession.isBlank()) {
                    val currentSession = planService.getPlanSessionByDay(userId, day)

                    call.respondTemplate(
                        "replace-session",
                        mapOf(
                            "error" to "Please select a replacement session.",
                            "day" to day,
                            "currentSession" to (currentSession?.session ?: "")
                        )
                    )
                    return@post
                }

                planService.updatePlanSession(userId, day, newSession)
                call.respondRedirect("/plan")
            }

            get("/calendar") {
                val session = call.principal<UserSession>()!!
                val user = userService.findByEmail(session.email)
                    ?: return@get call.respondRedirect("/login")

                val userId = user.id
                    ?: return@get call.respondRedirect("/login")

                val today = LocalDate.now()
                val startOfWeek = getStartOfWeek(today)
                val planFromDatabase = planService.getPlan(userId)

                val planByDay = planFromDatabase.associateBy { it.day }

                val calendarItems = (0..6).map { index ->
                    val date = startOfWeek.plusDays(index.toLong())
                    val dayName = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                    val plannedSession = planByDay[dayName]?.session ?: ""
                    val loggedForDate = loggedActivities.filter { it.date == date.toString() }

                    mapOf(
                        "day" to dayName,
                        "date" to date.toString(),
                        "planned" to plannedSession,
                        "isToday" to (date == today),
                        "logged" to loggedForDate.map {
                            mapOf(
                                "type" to it.type,
                                "duration" to it.duration,
                                "distance" to it.distance,
                                "notes" to it.notes
                            )
                        }
                    )
                }

                call.respondTemplate(
                    "calendar",
                    mapOf("calendarItems" to calendarItems)
                )
            }
        }
    }
}
