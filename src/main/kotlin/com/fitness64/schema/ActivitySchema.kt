/**
 * ActivitySchema.kt
 *
 * Defines the database schema and service layer for all activity-related data.
 * Covers cardio workouts (running, cycling, swimming), weightlifting exercises,
 * workout laps, and GPS trackpoints. Also manages activity types and exercises.
 */
package com.fitness64.schema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

/**
 * Represents a category of physical activity (e.g. Running, Weightlifting, Swimming).
 *
 * @property name The name of the activity type.
 */
@Serializable
data class ActivityType(
    val name: String
)

/**
 * Represents a specific exercise within an activity type.
 *
 * @property name The name of the exercise (e.g. Bench Press).
 * @property activityTypeId The ID of the associated activity type.
 * @property category Optional muscle group or exercise category (e.g. Chest, Legs).
 * @property measurementType How the exercise is measured (e.g. reps, time).
 */
@Serializable
data class Exercise(
    val name: String,
    val activityTypeId: Int,
    val category: String? = null,
    val measurementType: String? = null
)

/**
 * A simplified exercise representation used for dropdown options in the UI.
 *
 * @property id The exercise ID.
 * @property name The exercise name.
 */
@Serializable
data class ExerciseOption(
    val id: Int,
    val name: String
)

/**
 * Represents a logged workout session for any activity type.
 *
 * @property id The auto-generated workout log ID, or null for new entries.
 * @property userId The ID of the user who logged this workout.
 * @property activityTypeId The ID of the activity type performed.
 * @property logDate The date of the workout (ISO format: yyyy-MM-dd).
 * @property duration Duration of the workout in minutes.
 * @property distance Optional distance covered in km.
 * @property notes Optional notes about the session.
 * @property calories Optional calories burned.
 * @property source The source of the log entry (e.g. manual, tcx_import).
 * @property name Optional custom name for the workout session.
 */
@Serializable
data class WorkoutLog(
    val id: Int? = null,
    val userId: Int,
    val activityTypeId: Int,
    val logDate: String,
    val duration: Int,
    val distance: Double? = null,
    val notes: String? = null,
    val calories: Int? = null,
    val source: String? = null,
    val name: String? = null
)

/**
 * Represents a single cardio activity entry for display in activity history.
 *
 * @property id The workout log ID.
 * @property logDate The date of the activity.
 * @property activityType The type of cardio activity (e.g. Running, Cycling).
 * @property name Optional custom workout name.
 * @property duration Duration in minutes.
 * @property distance Optional distance in km.
 * @property notes Optional session notes.
 * @property source The source of the log entry.
 */
@Serializable
data class CardioHistoryItem(
    val id: Int,
    val logDate: String,
    val activityType: String,
    val name: String? = null,
    val duration: Int,
    val distance: Double? = null,
    val notes: String? = null,
    val source: String? = null
)

/**
 * Represents a specific exercise performed within a workout session.
 *
 * @property workoutLogId The ID of the parent workout log.
 * @property exerciseId The ID of the exercise performed.
 * @property sets Number of sets completed.
 * @property reps Number of repetitions per set.
 * @property weight Weight used in kg.
 */
@Serializable
data class WorkoutExercise(
    val workoutLogId: Int,
    val exerciseId: Int,
    val sets: Int,
    val reps: Int,
    val weight: Double
)

/**
 * Represents a single lap within a cardio workout, used for TCX imports.
 *
 * @property workoutLogId The ID of the parent workout log.
 * @property startTime The ISO timestamp when the lap started.
 * @property totalTimeSeconds Total duration of the lap in seconds.
 * @property distance Distance covered in this lap in metres.
 * @property calories Optional calories burned during this lap.
 */
@Serializable
data class WorkoutLap(
    val id: Int = 0,
    val workoutLogId: Int,
    val startTime: String,
    val totalTimeSeconds: Int,
    val distance: Double,
    val calories: Int? = null
)

/**
 * Represents a GPS trackpoint recorded during a workout lap.
 *
 * @property lapId The ID of the parent lap.
 * @property time The ISO timestamp of this trackpoint.
 * @property latitude GPS latitude in decimal degrees, or null if not recorded.
 * @property longitude GPS longitude in decimal degrees, or null if not recorded.
 * @property altitude Altitude in metres, or null if not recorded.
 * @property distance Cumulative distance in metres at this point, or null if not recorded.
 * @property heartRate Heart rate in BPM, or null if not recorded.
 */
@Serializable
data class Trackpoint(
    val lapId: Int,
    val time: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val distance: Double? = null,
    val heartRate: Int? = null
)

/**
 * A summary of the most recent workout for display on the dashboard.
 *
 * @property activityType The type of activity performed.
 * @property name Optional custom workout name.
 * @property logDate The date of the workout.
 * @property duration Duration in minutes.
 * @property distance Optional distance in km.
 */
@Serializable
data class LatestWorkoutSummary(
    val activityType: String,
    val name: String? = null,
    val logDate: String,
    val duration: Int,
    val distance: Double? = null
)

/**
 * Service class responsible for all database operations related to activities.
 * Manages activity types, exercises, workout logs, laps, trackpoints,
 * and provides query methods for history and statistics.
 *
 * @param database The database connection to use for all operations.
 */
class ActivityService(database: Database) {

    /**
     * Database table for activity types (e.g. Running, Weightlifting, Swimming).
     */
    object ActivityTypes : Table("activity_types") {
        val id = integer("activity_type_id").autoIncrement()
        val name = varchar("name", 50).uniqueIndex()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table for exercises associated with activity types.
     */
    object Exercises : Table("exercises") {
        val id = integer("exercise_id").autoIncrement()
        val name = varchar("name", 255)
        val activityTypeId = integer("activity_type_id").references(ActivityTypes.id)
        val category = varchar("category", 100).nullable()
        val measurementType = varchar("measurement_type", 50).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table for individual workout log entries.
     */
    object WorkoutLogs : Table("workout_logs") {
        val id = integer("workout_log_id").autoIncrement()
        val userId = integer("user_id")
        val activityTypeId = integer("activity_type_id").references(ActivityTypes.id)
        val logDate = varchar("log_date", 30)
        val duration = integer("duration")
        val distance = double("distance").nullable()
        val notes = varchar("notes", 255).nullable()
        val calories = integer("calories").nullable()
        val workoutSource = varchar("source", 50).nullable()
        val workoutName = varchar("name", 255).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table linking workout logs to specific exercises with sets, reps and weight.
     */
    object WorkoutExercises : Table("workout_exercises") {
        val id = integer("workout_exercise_id").autoIncrement()
        val workoutLogId = integer("workout_log_id").references(WorkoutLogs.id)
        val exerciseId = integer("exercise_id").references(Exercises.id)
        val sets = integer("sets")
        val reps = integer("reps")
        val weight = double("weight")

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table for individual laps within a workout (used for TCX imports).
     */
    object WorkoutLaps : Table("workout_laps") {
        val id = integer("lap_id").autoIncrement()
        val workoutLogId = integer("workout_log_id").references(WorkoutLogs.id)
        val startTime = varchar("start_time", 40)
        val totalTimeSeconds = integer("total_time_seconds")
        val distance = double("distance")
        val calories = integer("calories").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table for GPS trackpoints recorded during workout laps.
     */
    object Trackpoints : Table("trackpoints") {
        val id = integer("trackpoint_id").autoIncrement()
        val lapId = integer("lap_id").references(WorkoutLaps.id)
        val time = varchar("time", 40)
        val latitude = double("latitude").nullable()
        val longitude = double("longitude").nullable()
        val altitude = double("altitude").nullable()
        val distance = double("distance").nullable()
        val heartRate = integer("heart_rate").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(
                ActivityTypes,
                Exercises,
                WorkoutLogs,
                WorkoutExercises,
                WorkoutLaps,
                Trackpoints
            )
        }
    }

    /**
     * Creates a new activity type in the database.
     *
     * @param activityType The activity type to create.
     * @return The auto-generated ID of the new activity type.
     */
    suspend fun createActivityType(activityType: ActivityType): Int = dbQuery {
        ActivityTypes.insert {
            it[name] = activityType.name
        }[ActivityTypes.id]
    }

    /**
     * Finds an activity type by name and returns its ID.
     *
     * @param typeName The name of the activity type to look up.
     * @return The ID of the matching activity type, or null if not found.
     */
    suspend fun getActivityTypeByName(typeName: String): Int? = dbQuery {
        ActivityTypes.selectAll()
            .where { ActivityTypes.name eq typeName }
            .map { it[ActivityTypes.id] }
            .singleOrNull()
    }

    /**
     * Finds an activity type name by its ID.
     *
     * @param activityTypeIdValue The ID of the activity type to look up.
     * @return The name of the matching activity type, or null if not found.
     */
    suspend fun getActivityTypeName(activityTypeIdValue: Int): String? = dbQuery {
        ActivityTypes.selectAll()
            .where { ActivityTypes.id eq activityTypeIdValue }
            .map { it[ActivityTypes.name] }
            .singleOrNull()
    }

    /**
     * Retrieves an existing activity type ID by name, or creates it if it doesn't exist.
     *
     * @param typeName The name of the activity type to find or create.
     * @return The ID of the existing or newly created activity type.
     */
    suspend fun getOrCreateActivityType(typeName: String): Int {
        val existingActivityTypeId = getActivityTypeByName(typeName)
        return existingActivityTypeId ?: createActivityType(ActivityType(typeName))
    }

    /**
     * Creates a new exercise in the database.
     *
     * @param exercise The exercise to create.
     * @return The auto-generated ID of the new exercise.
     */
    suspend fun createExercise(exercise: Exercise): Int = dbQuery {
        Exercises.insert {
            it[name] = exercise.name
            it[activityTypeId] = exercise.activityTypeId
            it[category] = exercise.category
            it[measurementType] = exercise.measurementType
        }[Exercises.id]
    }

    /**
     * Finds an exercise by name and returns its ID.
     *
     * @param exerciseName The name of the exercise to look up.
     * @return The ID of the matching exercise, or null if not found.
     */
    suspend fun getExerciseByName(exerciseName: String): Int? = dbQuery {
        Exercises.selectAll()
            .where { Exercises.name eq exerciseName }
            .map { it[Exercises.id] }
            .singleOrNull()
    }

    /**
     * Retrieves all exercises belonging to a specific activity type.
     *
     * @param activityTypeIdValue The ID of the activity type to filter by.
     * @return A sorted list of [ExerciseOption] objects for the given activity type.
     */
    suspend fun getExercisesByActivityType(activityTypeIdValue: Int): List<ExerciseOption> = dbQuery {
        Exercises.selectAll()
            .where { Exercises.activityTypeId eq activityTypeIdValue }
            .map {
                ExerciseOption(
                    id = it[Exercises.id],
                    name = it[Exercises.name]
                )
            }
            .sortedBy { it.name }
    }

    /**
     * Creates a new workout log entry in the database.
     *
     * @param workout The workout log data to save.
     * @return The auto-generated ID of the new workout log.
     */
    suspend fun createWorkoutLog(workout: WorkoutLog): Int = dbQuery {
        WorkoutLogs.insert {
            it[userId] = workout.userId
            it[activityTypeId] = workout.activityTypeId
            it[logDate] = workout.logDate
            it[duration] = workout.duration
            it[distance] = workout.distance
            it[notes] = workout.notes
            it[calories] = workout.calories
            it[workoutSource] = workout.source
            if (workout.name != null) {
                it[workoutName] = workout.name
            }
        }[WorkoutLogs.id]
    }

    /**
     * Retrieves a single workout log by its ID.
     *
     * @param id The ID of the workout log to retrieve.
     * @return The matching [WorkoutLog], or null if not found.
     */
    suspend fun getWorkoutLog(id: Int): WorkoutLog? = dbQuery {
        WorkoutLogs.selectAll()
            .where { WorkoutLogs.id eq id }
            .map {
                WorkoutLog(
                    id = it[WorkoutLogs.id],
                    userId = it[WorkoutLogs.userId],
                    activityTypeId = it[WorkoutLogs.activityTypeId],
                    logDate = it[WorkoutLogs.logDate],
                    duration = it[WorkoutLogs.duration],
                    distance = it[WorkoutLogs.distance],
                    notes = it[WorkoutLogs.notes],
                    calories = it[WorkoutLogs.calories],
                    source = it[WorkoutLogs.workoutSource],
                    name = it[WorkoutLogs.workoutName]
                )
            }
            .singleOrNull()
    }

    /**
     * Updates an existing workout log with new values.
     *
     * @param id The ID of the workout log to update.
     * @param duration The new duration in minutes.
     * @param distance The new distance in km, or null.
     * @param notes The new session notes.
     * @param calories The new calories burned, or null.
     * @param activityTypeId The new activity type ID, or null to leave unchanged.
     * @param name The new custom workout name, or null to leave unchanged.
     */
    suspend fun updateWorkoutLog(id: Int, duration: Int, distance: Double?, notes: String, calories: Int?, activityTypeId: Int?, name: String?) = dbQuery {
        WorkoutLogs.update({ WorkoutLogs.id eq id }) {
            it[WorkoutLogs.duration] = duration
            it[WorkoutLogs.distance] = distance
            it[WorkoutLogs.notes] = notes
            it[WorkoutLogs.calories] = calories
            if (activityTypeId != null) {
                it[WorkoutLogs.activityTypeId] = activityTypeId
            }
            if (name != null) {
                it[WorkoutLogs.workoutName] = name
            }
        }
    }

    /**
     * Retrieves all workout logs for a specific user.
     *
     * @param userIdValue The ID of the user whose workouts to retrieve.
     * @return A list of [WorkoutLog] objects for the given user.
     */
    suspend fun getWorkoutsForUser(userIdValue: Int): List<WorkoutLog> = dbQuery {
        WorkoutLogs.selectAll()
            .where { WorkoutLogs.userId eq userIdValue }
            .map {
                WorkoutLog(
                    id = it[WorkoutLogs.id],
                    userId = it[WorkoutLogs.userId],
                    activityTypeId = it[WorkoutLogs.activityTypeId],
                    logDate = it[WorkoutLogs.logDate],
                    duration = it[WorkoutLogs.duration],
                    distance = it[WorkoutLogs.distance],
                    notes = it[WorkoutLogs.notes],
                    calories = it[WorkoutLogs.calories],
                    source = it[WorkoutLogs.workoutSource],
                    name = it[WorkoutLogs.workoutName]
                )
            }
    }

    /**
     * Retrieves the cardio activity history for a specific user.
     * Only includes Running, Cycling, and Swimming activity types.
     *
     * @param userIdValue The ID of the user whose cardio history to retrieve.
     * @return A list of [CardioHistoryItem] objects sorted by date descending.
     */
    suspend fun getCardioHistory(userIdValue: Int): List<CardioHistoryItem> = dbQuery {
        val cardioTypes = listOf("Running", "Cycling", "Swimming")

        (WorkoutLogs innerJoin ActivityTypes)
            .selectAll()
            .where {
                (WorkoutLogs.userId eq userIdValue) and (ActivityTypes.name inList cardioTypes)
            }
            .orderBy(WorkoutLogs.logDate to SortOrder.DESC)
            .map {
                CardioHistoryItem(
                    id = it[WorkoutLogs.id],
                    logDate = it[WorkoutLogs.logDate],
                    activityType = it[ActivityTypes.name],
                    name = it[WorkoutLogs.workoutName],
                    duration = it[WorkoutLogs.duration],
                    distance = it[WorkoutLogs.distance],
                    notes = it[WorkoutLogs.notes],
                    source = it[WorkoutLogs.workoutSource]
                )
            }
    }

    /**
     * Counts the number of workout logs for a user within a date range.
     *
     * @param userIdValue The ID of the user.
     * @param startDate The start of the date range (ISO format: yyyy-MM-dd).
     * @param endDate The end of the date range (ISO format: yyyy-MM-dd).
     * @return The number of workout logs within the given date range.
     */
    suspend fun countWorkoutsForUserBetween(
        userIdValue: Int,
        startDate: String,
        endDate: String
    ): Int {
        return getWorkoutsForUser(userIdValue)
            .count { workout ->
                workout.logDate >= startDate && workout.logDate <= endDate
            }
    }

    /**
     * Retrieves a summary of the most recent workout for a user.
     *
     * @param userIdValue The ID of the user.
     * @return A [LatestWorkoutSummary] for the most recent workout, or null if none exist.
     */
    suspend fun getLatestWorkoutSummaryForUser(userIdValue: Int): LatestWorkoutSummary? {
        val latestWorkout = getWorkoutsForUser(userIdValue)
            .maxByOrNull { it.logDate }

        if (latestWorkout == null) {
            return null
        }

        val activityTypeName = getActivityTypeName(latestWorkout.activityTypeId)
            ?: latestWorkout.source
            ?: "Workout"
        val displayName = latestWorkout.name?.takeIf { it.isNotBlank() } ?: "$activityTypeName Session"

        return LatestWorkoutSummary(
            activityType = activityTypeName,
            name = displayName,
            logDate = latestWorkout.logDate,
            duration = latestWorkout.duration,
            distance = latestWorkout.distance
        )
    }

    /**
     * Deletes a workout log from the database by its ID.
     *
     * @param id The ID of the workout log to delete.
     */
    suspend fun deleteWorkoutLog(id: Int) = dbQuery {
        WorkoutLogs.deleteWhere { WorkoutLogs.id eq id }
    }

    /**
     * Creates a new workout lap entry in the database.
     *
     * @param lap The lap data to save.
     * @return The auto-generated ID of the new lap entry.
     */
    suspend fun createWorkoutLap(lap: WorkoutLap): Int = dbQuery {
        WorkoutLaps.insert {
            it[workoutLogId] = lap.workoutLogId
            it[startTime] = lap.startTime
            it[totalTimeSeconds] = lap.totalTimeSeconds
            it[distance] = lap.distance
            it[calories] = lap.calories
        }[WorkoutLaps.id]
    }

    /**
     * Creates a new GPS trackpoint entry in the database.
     *
     * @param trackpoint The trackpoint data to save.
     * @return The auto-generated ID of the new trackpoint entry.
     */
    suspend fun createTrackpoint(trackpoint: Trackpoint): Int = dbQuery {
        Trackpoints.insert {
            it[lapId] = trackpoint.lapId
            it[time] = trackpoint.time
            it[latitude] = trackpoint.latitude
            it[longitude] = trackpoint.longitude
            it[altitude] = trackpoint.altitude
            it[distance] = trackpoint.distance
            it[heartRate] = trackpoint.heartRate
        }[Trackpoints.id]
    }

    /**
     * Retrieves all trackpoints for a specific lap.
     *
     * @param lapIdValue The ID of the lap whose trackpoints to retrieve.
     * @return A list of [Trackpoint] objects for the given lap.
     */
    suspend fun getTrackpointsForLap(lapIdValue: Int): List<Trackpoint> = dbQuery {
        Trackpoints.selectAll()
            .where { Trackpoints.lapId eq lapIdValue }
            .map {
                Trackpoint(
                    lapId = it[Trackpoints.lapId],
                    time = it[Trackpoints.time],
                    latitude = it[Trackpoints.latitude],
                    longitude = it[Trackpoints.longitude],
                    altitude = it[Trackpoints.altitude],
                    distance = it[Trackpoints.distance],
                    heartRate = it[Trackpoints.heartRate]
                )
            }
    }

    suspend fun getLapsForWorkoutLog(workoutLogId: Int): List<WorkoutLap> = dbQuery {
        WorkoutLaps.selectAll()
            .where { WorkoutLaps.workoutLogId eq workoutLogId }
            .orderBy(WorkoutLaps.id to SortOrder.ASC)
            .map {
                WorkoutLap(
                    id = it[WorkoutLaps.id],
                    workoutLogId = it[WorkoutLaps.workoutLogId],
                    startTime = it[WorkoutLaps.startTime],
                    totalTimeSeconds = it[WorkoutLaps.totalTimeSeconds],
                    distance = it[WorkoutLaps.distance],
                    calories = it[WorkoutLaps.calories]
                )
            }
    }

    /**
     * Executes a database query on the IO dispatcher using a suspended transaction.
     *
     * @param block The database operation to execute.
     * @return The result of the database operation.
     */
    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}
