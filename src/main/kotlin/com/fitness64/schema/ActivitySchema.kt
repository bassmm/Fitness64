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

@Serializable
data class ActivityType(
    val name: String
)

@Serializable
data class Exercise(
    val name: String,
    val activityTypeId: Int,
    val category: String? = null,
    val measurementType: String? = null
)

@Serializable
data class ExerciseOption(
    val id: Int,
    val name: String
)

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

@Serializable
data class WorkoutExercise(
    val workoutLogId: Int,
    val exerciseId: Int,
    val sets: Int,
    val reps: Int,
    val weight: Double
)

@Serializable
data class WorkoutLap(
    val workoutLogId: Int,
    val startTime: String,
    val totalTimeSeconds: Int,
    val distance: Double,
    val calories: Int? = null
)

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

@Serializable
data class LatestWorkoutSummary(
    val activityType: String,
    val name: String? = null,
    val logDate: String,
    val duration: Int,
    val distance: Double? = null
)

class ActivityService(database: Database) {

    object ActivityTypes : Table("activity_types") {
        val id = integer("activity_type_id").autoIncrement()
        val name = varchar("name", 50).uniqueIndex()

        override val primaryKey = PrimaryKey(id)
    }

    object Exercises : Table("exercises") {
        val id = integer("exercise_id").autoIncrement()
        val name = varchar("name", 255)
        val activityTypeId = integer("activity_type_id").references(ActivityTypes.id)
        val category = varchar("category", 100).nullable()
        val measurementType = varchar("measurement_type", 50).nullable()

        override val primaryKey = PrimaryKey(id)
    }

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

    object WorkoutExercises : Table("workout_exercises") {
        val id = integer("workout_exercise_id").autoIncrement()
        val workoutLogId = integer("workout_log_id").references(WorkoutLogs.id)
        val exerciseId = integer("exercise_id").references(Exercises.id)
        val sets = integer("sets")
        val reps = integer("reps")
        val weight = double("weight")

        override val primaryKey = PrimaryKey(id)
    }

    object WorkoutLaps : Table("workout_laps") {
        val id = integer("lap_id").autoIncrement()
        val workoutLogId = integer("workout_log_id").references(WorkoutLogs.id)
        val startTime = varchar("start_time", 40)
        val totalTimeSeconds = integer("total_time_seconds")
        val distance = double("distance")
        val calories = integer("calories").nullable()

        override val primaryKey = PrimaryKey(id)
    }

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

    suspend fun createActivityType(activityType: ActivityType): Int = dbQuery {
        ActivityTypes.insert {
            it[name] = activityType.name
        }[ActivityTypes.id]
    }

    suspend fun getActivityTypeByName(typeName: String): Int? = dbQuery {
        ActivityTypes.selectAll()
            .where { ActivityTypes.name eq typeName }
            .map { it[ActivityTypes.id] }
            .singleOrNull()
    }

    suspend fun getActivityTypeName(activityTypeIdValue: Int): String? = dbQuery {
        ActivityTypes.selectAll()
            .where { ActivityTypes.id eq activityTypeIdValue }
            .map { it[ActivityTypes.name] }
            .singleOrNull()
    }

    suspend fun getOrCreateActivityType(typeName: String): Int {
        val existingActivityTypeId = getActivityTypeByName(typeName)
        return existingActivityTypeId ?: createActivityType(ActivityType(typeName))
    }

    suspend fun createExercise(exercise: Exercise): Int = dbQuery {
        Exercises.insert {
            it[name] = exercise.name
            it[activityTypeId] = exercise.activityTypeId
            it[category] = exercise.category
            it[measurementType] = exercise.measurementType
        }[Exercises.id]
    }

    suspend fun getExerciseByName(exerciseName: String): Int? = dbQuery {
        Exercises.selectAll()
            .where { Exercises.name eq exerciseName }
            .map { it[Exercises.id] }
            .singleOrNull()
    }

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

    suspend fun deleteWorkoutLog(id: Int) = dbQuery {
        WorkoutLogs.deleteWhere { WorkoutLogs.id eq id }
    }

    suspend fun createWorkoutLap(lap: WorkoutLap): Int = dbQuery {
        WorkoutLaps.insert {
            it[workoutLogId] = lap.workoutLogId
            it[startTime] = lap.startTime
            it[totalTimeSeconds] = lap.totalTimeSeconds
            it[distance] = lap.distance
            it[calories] = lap.calories
        }[WorkoutLaps.id]
    }

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

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}
