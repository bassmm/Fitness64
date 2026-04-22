package com.fitness64.activities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
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
data class WorkoutLog(
    val userId: Int,
    val activityTypeId: Int,
    val logDate: String,
    val duration: Int,
    val distance: Double? = null,
    val notes: String? = null,
    val calories: Int? = null,
    val source: String? = null
)

@Serializable
data class WorkoutExercise(
    val workoutLogId: Int,
    val exerciseId: Int
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

        override val primaryKey = PrimaryKey(id)
    }

    object WorkoutExercises : Table("workout_exercises") {
        val id = integer("workout_exercise_id").autoIncrement()
        val workoutLogId = integer("workout_log_id").references(WorkoutLogs.id)
        val exerciseId = integer("exercise_id").references(Exercises.id)

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
            SchemaUtils.create(ActivityTypes, Exercises, WorkoutLogs, WorkoutExercises, WorkoutLaps, Trackpoints)
        }
    }

    suspend fun createActivityType(activityType: ActivityType): Int = dbQuery {
        ActivityTypes.insert {
            it[name] = activityType.name
        }[ActivityTypes.id]
    }

    suspend fun createExercise(exercise: Exercise): Int = dbQuery {
        Exercises.insert {
            it[name] = exercise.name
            it[activityTypeId] = exercise.activityTypeId
            it[category] = exercise.category
            it[measurementType] = exercise.measurementType
        }[Exercises.id]
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
        }[WorkoutLogs.id]
    }

    suspend fun getWorkoutLog(id: Int): WorkoutLog? = dbQuery {
        WorkoutLogs.selectAll()
            .where { WorkoutLogs.id eq id }
            .map {
                WorkoutLog(
                    userId = it[WorkoutLogs.userId],
                    activityTypeId = it[WorkoutLogs.activityTypeId],
                    logDate = it[WorkoutLogs.logDate],
                    duration = it[WorkoutLogs.duration],
                    distance = it[WorkoutLogs.distance],
                    notes = it[WorkoutLogs.notes],
                    calories = it[WorkoutLogs.calories],
                    source = it[WorkoutLogs.workoutSource]
                )
            }
            .singleOrNull()
    }

    suspend fun getWorkoutsForUser(userIdValue: Int): List<WorkoutLog> = dbQuery {
        WorkoutLogs.selectAll()
            .where { WorkoutLogs.userId eq userIdValue }
            .map {
                WorkoutLog(
                    userId = it[WorkoutLogs.userId],
                    activityTypeId = it[WorkoutLogs.activityTypeId],
                    logDate = it[WorkoutLogs.logDate],
                    duration = it[WorkoutLogs.duration],
                    distance = it[WorkoutLogs.distance],
                    notes = it[WorkoutLogs.notes],
                    calories = it[WorkoutLogs.calories],
                    source = it[WorkoutLogs.workoutSource]
                )
            }
    }

    suspend fun deleteWorkoutLog(id: Int) = dbQuery {
        WorkoutLogs.deleteWhere { WorkoutLogs.id eq id }
    }

    suspend fun createWorkoutExercise(workoutExercise: WorkoutExercise): Int = dbQuery {
        WorkoutExercises.insert {
            it[workoutLogId] = workoutExercise.workoutLogId
            it[exerciseId] = workoutExercise.exerciseId
        }[WorkoutExercises.id]
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