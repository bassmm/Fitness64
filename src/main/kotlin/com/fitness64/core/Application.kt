/**
 * Application.kt
 *
 * Entry point for the Fitness64 Ktor application.
 * Initialises all services, configures middleware, and registers all route handlers.
 */
package com.fitness64.core

import com.fitness64.routes.*
import com.fitness64.routes.activities.*
import com.fitness64.schema.*
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
