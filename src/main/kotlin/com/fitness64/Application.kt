package com.fitness64

import com.fitness64.activities.*
import com.fitness64.races.*
import com.fitness64.users.*
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    val database = configureDatabases()
    val userService = UserService(database)
    val activityService = ActivityService(database)
    val raceService = RaceService(database)

    runBlocking {
        val weightliftingTypeId = activityService.getActivityTypeByName("Weightlifting")
            ?: activityService.createActivityType(ActivityType("Weightlifting"))

        if (activityService.getExerciseByName("Bench Press") == null) {
            activityService.createExercise(
                Exercise(
                    name = "Bench Press",
                    activityTypeId = weightliftingTypeId,
                    category = "Chest",
                    measurementType = "reps"
                )
            )
        }
        if (activityService.getExerciseByName("Squat") == null) {
            activityService.createExercise(
                Exercise(
                    name = "Squat",
                    activityTypeId = weightliftingTypeId,
                    category = "Legs",
                    measurementType = "reps"
                )
            )
        }
        if (activityService.getExerciseByName("Deadlift") == null) {
            activityService.createExercise(
                Exercise(
                    name = "Deadlift",
                    activityTypeId = weightliftingTypeId,
                    category = "Back",
                    measurementType = "reps"
                )
            )
        }
        if (activityService.getExerciseByName("Shoulder Press") == null) {
            activityService.createExercise(
                Exercise(
                    name = "Shoulder Press",
                    activityTypeId = weightliftingTypeId,
                    category = "Shoulders",
                    measurementType = "reps"
                )
            )
        }
    }

    configureTemplating()
    configureSerialization()
    configureSecurity(userService)

    configureRouting(userService)
    configureUsersRoutes(userService)
    configureActivityRoutes(activityService, userService)
    configureRaceRoutes(raceService)
}