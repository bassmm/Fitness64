package com.fitness64.routes

import com.fitness64.schema.ActivityService
import com.fitness64.schema.PlanService
import com.fitness64.schema.RaceService
import com.fitness64.schema.UserService
import com.fitness64.schema.WeightliftingService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureCalendarRoutes(
    userService: UserService,
    activityService: ActivityService,
    weightliftingService: WeightliftingService,
    raceService: RaceService,
    planService: PlanService
) {
    routing {
        authenticate("auth-session") {
            get("/calendar") {
                call.respondRedirect("/activities")
            }
        }
    }
}