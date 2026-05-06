package com.fitness64.core

import com.fitness64.routes.*
import com.fitness64.routes.activities.*
import com.fitness64.schema.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val database = configureDatabases()
    val userService = UserService(database)
    val activityService = ActivityService(database)
    val weightliftingService = WeightliftingService(database)
    val raceService = RaceService(database)
    val planService = PlanService(database)

    configureTemplating()
    configureSerialization()
    configureSecurity(userService)

    configureRouting(userService, planService)
    configureDashboardRoutes(userService, activityService, weightliftingService, planService)
    configureLogRoutes(userService, activityService, weightliftingService)
    configureCalendarRoutes(userService, activityService, weightliftingService, raceService, planService)
    configureProgressRoutes(userService, activityService, weightliftingService)
    configureRacesPagesRoutes(userService, raceService)
    configureActivityRoutes(activityService, userService, weightliftingService, raceService)
    configureTcxUploadRoutes(activityService, userService)
    configurePlanRoutes(planService, userService)
}
