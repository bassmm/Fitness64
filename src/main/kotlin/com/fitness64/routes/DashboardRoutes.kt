package com.fitness64.routes

import com.fitness64.core.UserSession
import com.fitness64.core.requireAuthenticatedUser
import com.fitness64.core.respondHx
import com.fitness64.core.getStartOfWeek
import com.fitness64.core.splitPreferredActivities
import com.fitness64.schema.ActivityService
import com.fitness64.schema.PlanService
import com.fitness64.schema.UserService
import com.fitness64.schema.WeightliftingService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.*

fun Application.configureDashboardRoutes(
    userService: UserService,
    activityService: ActivityService,
    weightliftingService: WeightliftingService,
    planService: PlanService
) {
    routing {
        authenticate("auth-session") {
            get("/home") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val (user, userId) = auth

                val today = LocalDate.now()
                val todayDayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)

                val todayTraining = planService.getPlanSessionByDay(userId, todayDayName)?.session
                    ?: "No training planned"

                // New: get the full weekly plan so Home can show a summary/quick view
                val weeklyPlanSummary = planService.getPlan(userId)

                val startOfWeek = getStartOfWeek(today)
                val startDate = startOfWeek.toString()
                val todayStr = today.toString()

                val cardioCount = activityService.countWorkoutsForUserBetween(userId, startDate, todayStr)
                val weightliftingCount = weightliftingService.getWeightliftingHistory(userId)
                    .count { it.logDate >= startDate && it.logDate <= todayStr }
                val workoutsThisWeek = cardioCount + weightliftingCount

                val consistency = if (workoutsThisWeek > 0) {
                    "$workoutsThisWeek workout${if (workoutsThisWeek == 1) "" else "s"} this week"
                } else {
                    "Start your weekly streak"
                }

                val nextGoal = when {
                    !user.goal.isNullOrBlank() -> user.goal
                    user.trainingDaysPerWeek != null -> "Complete ${user.trainingDaysPerWeek} workouts this week"
                    else -> "Set your next fitness goal"
                }

                val latestCardio = activityService.getLatestWorkoutSummaryForUser(userId)
                val latestWeightlifting = weightliftingService.getWeightliftingHistory(userId).firstOrNull()

                val latestAchievement = when {
                    latestCardio == null && latestWeightlifting == null -> "Complete your next workout"
                    latestCardio == null -> "Latest ${latestWeightlifting?.name ?: "Weightlifting Session"} logged"
                    latestWeightlifting == null -> "Latest ${latestCardio.name ?: "${latestCardio.activityType} Session"} logged"
                    latestWeightlifting.logDate >= latestCardio.logDate -> "Latest ${latestWeightlifting.name ?: "Weightlifting Session"} logged"
                    else -> "Latest ${latestCardio.name ?: "${latestCardio.activityType} Session"} logged"
                }

                call.respondTemplate(
                    "home",
                    mapOf(
                        "user" to user.name,
                        "today" to todayTraining,
                        "todayDate" to today.toString(),
                        "streak" to consistency,
                        "nextGoal" to nextGoal,
                        "achievement" to latestAchievement,
                        "weeklyPlanSummary" to weeklyPlanSummary
                    )
                )
            }

            get("/profile") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val user = auth.user

                call.respondTemplate(
                    "profile",
                    mapOf<String, Any>(
                        "name" to user.name,
                        "email" to user.email,
                        "fitnessLevel" to (user.fitnessLevel ?: "Not set"),
                        "goals" to (user.goal ?: "No goals set"),
                        "activities" to splitPreferredActivities(user.preferredActivities),
                        "community" to (user.community ?: "No community set")
                    )
                )
            }

            get("/profile/view") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val user = auth.user

                call.respondHx(
                    templateName = "_partials/_profile-view",
                    model = mapOf<String, Any>(
                        "name" to user.name,
                        "email" to user.email,
                        "fitnessLevel" to (user.fitnessLevel ?: "Not set"),
                        "goals" to (user.goal ?: "No goals set"),
                        "activities" to splitPreferredActivities(user.preferredActivities),
                        "community" to (user.community ?: "No community set")
                    ),
                    target = "#profile-edit-area",
                    swap = "innerHTML"
                )
            }

            get("/profile/edit-form") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@get
                val user = auth.user

                call.respondHx(
                    templateName = "_partials/_profile-edit-form",
                    model = mapOf<String, Any>(
                        "name" to user.name,
                        "email" to user.email,
                        "fitnessLevel" to (user.fitnessLevel ?: ""),
                        "goal" to (user.goal ?: ""),
                        "trainingDaysPerWeek" to (user.trainingDaysPerWeek ?: ""),
                        "preferredActivities" to (user.preferredActivities ?: ""),
                        "community" to (user.community ?: ""),
                        "error" to ""
                    ),
                    target = "#profile-edit-area",
                    swap = "innerHTML"
                )
            }

            post("/profile/save") {
                val auth = call.requireAuthenticatedUser(userService) ?: return@post
                val (user, userId) = auth

                val params = call.receiveParameters()
                val name = params["name"]?.trim().orEmpty()
                val email = params["email"]?.trim().orEmpty()
                val fitnessLevel = params["fitnessLevel"]?.trim()
                val goal = params["goal"]?.trim()
                val trainingDaysPerWeek = params["trainingDaysPerWeek"]?.toIntOrNull()
                val preferredActivities = params["preferredActivities"]?.trim()
                val community = params["community"]?.trim()

                if (name.isBlank() || email.isBlank()) {
                    call.respondHx(
                        templateName = "_partials/_profile-edit-form",
                        model = mapOf<String, Any>(
                            "name" to name,
                            "email" to email,
                            "fitnessLevel" to (fitnessLevel ?: ""),
                            "goal" to (goal ?: ""),
                            "trainingDaysPerWeek" to (trainingDaysPerWeek ?: ""),
                            "preferredActivities" to (preferredActivities ?: ""),
                            "community" to (community ?: ""),
                            "error" to "Name and email are required."
                        ),
                        target = "#profile-edit-area",
                        swap = "innerHTML"
                    )
                    return@post
                }

                if (email != user.email && userService.findByEmail(email) != null) {
                    call.respondHx(
                        templateName = "_partials/_profile-edit-form",
                        model = mapOf<String, Any>(
                            "name" to name,
                            "email" to email,
                            "fitnessLevel" to (fitnessLevel ?: ""),
                            "goal" to (goal ?: ""),
                            "trainingDaysPerWeek" to (trainingDaysPerWeek ?: ""),
                            "preferredActivities" to (preferredActivities ?: ""),
                            "community" to (community ?: ""),
                            "error" to "Email already in use."
                        ),
                        target = "#profile-edit-area",
                        swap = "innerHTML"
                    )
                    return@post
                }

                userService.updateProfile(
                    userId,
                    name,
                    email,
                    fitnessLevel.takeIf { !it.isNullOrBlank() },
                    goal.takeIf { !it.isNullOrBlank() },
                    trainingDaysPerWeek,
                    preferredActivities.takeIf { !it.isNullOrBlank() },
                    community.takeIf { !it.isNullOrBlank() }
                )

                call.sessions.set(UserSession(email = email))

                call.respondHx(
                    templateName = "_partials/_profile-view",
                    model = mapOf<String, Any>(
                        "name" to name,
                        "email" to email,
                        "fitnessLevel" to (fitnessLevel ?: "Not set"),
                        "goals" to (goal ?: "No goals set"),
                        "activities" to splitPreferredActivities(preferredActivities),
                        "community" to (community ?: "No community set")
                    ),
                    target = "#profile-edit-area",
                    swap = "innerHTML"
                )
            }
        }
    }
}