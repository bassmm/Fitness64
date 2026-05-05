package com.fitness64

import com.fitness64.activities.*
import com.fitness64.plans.*
import com.fitness64.races.*
import com.fitness64.users.*
import com.fitness64.weightlifting.*
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

    // Routing
    configureRouting(userService, activityService, planService, raceService, weightliftingService)
    configureUsersRoutes(userService)
    configureActivityRoutes(activityService, userService, weightliftingService, raceService)
    configureWeightliftingRoutes(weightliftingService, userService)
    configureRaceRoutes(raceService)
    configurePlanRoutes(planService, userService)
}