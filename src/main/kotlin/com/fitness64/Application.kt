/**
 * Application.kt
 *
 * Entry point for the Fitness64 Ktor application.
 * Initialises all services, configures middleware, and registers all route handlers.
 */
package com.fitness64

import com.fitness64.activities.*
import com.fitness64.plans.*
import com.fitness64.races.*
import com.fitness64.users.*
import com.fitness64.weightlifting.*
import io.ktor.server.application.*

/**
 * Application entry point. Starts the Ktor server using the Netty engine.
 *
 * @param args Command line arguments passed to the engine.
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/**
 * Main application module. Initialises all services and configures the application.
 *
 * Performs the following setup in order:
 * 1. Connects to the database and initialises all service layers.
 * 2. Configures templating, serialization, and security middleware.
 * 3. Registers all route handlers for each feature module.
 */
fun Application.module() {
    // Service Init
    val database = configureDatabases()
    val userService = UserService(database)
    val activityService = ActivityService(database)
    val weightliftingService = WeightliftingService(database)
    val raceService = RaceService(database)
    val planService = PlanService(database)

    // Middleware setup
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