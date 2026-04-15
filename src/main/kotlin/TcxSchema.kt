package com.comp2850

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

@Serializable
data class ExposedWorkoutLog(
    val userId: Int,
    val logDate: String,
    val duration: Int,
    val distance: Double,
    val notes: String? = null,
    val calories: Int? = null,
    val source: String? = null
)

@Serializable
data class ExposedWorkoutLap(
    val workoutLogId: Int,
    val startTime: String,
    val totalTimeSeconds: Int,
    val distance: Double,
    val calories: Int? = null
)

@Serializable
data class ExposedTrackpoint(
    val lapId: Int,
    val time: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val distance: Double? = null,
    val heartRate: Int? = null
)

class TcxService(database: Database) {

    object WorkoutLogs : Table() {
        val id = integer("id").autoIncrement()
        val userId = integer("user_id")
        val logDate = varchar("log_date", 30)
        val duration = integer("duration")
        val distance = double("distance")
        val notes = varchar("notes", 255).nullable()
        val calories = integer("calories").nullable()
        val workoutSource = varchar("source", 50).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object WorkoutLaps : Table() {
        val id = integer("id").autoIncrement()
        val workoutLogId = integer("workout_log_id").references(WorkoutLogs.id)
        val startTime = varchar("start_time", 40)
        val totalTimeSeconds = integer("total_time_seconds")
        val distance = double("distance")
        val calories = integer("calories").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object Trackpoints : Table() {
        val id = integer("id").autoIncrement()
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
            SchemaUtils.create(WorkoutLogs, WorkoutLaps, Trackpoints)
        }
    }

    suspend fun createWorkoutLog(workout: ExposedWorkoutLog): Int = dbQuery {
        WorkoutLogs.insert {
            it[userId] = workout.userId
            it[logDate] = workout.logDate
            it[duration] = workout.duration
            it[distance] = workout.distance
            it[notes] = workout.notes
            it[calories] = workout.calories
            it[workoutSource] = workout.source
        }[WorkoutLogs.id]
    }

    suspend fun createWorkoutLap(lap: ExposedWorkoutLap): Int = dbQuery {
        WorkoutLaps.insert {
            it[workoutLogId] = lap.workoutLogId
            it[startTime] = lap.startTime
            it[totalTimeSeconds] = lap.totalTimeSeconds
            it[distance] = lap.distance
            it[calories] = lap.calories
        }[WorkoutLaps.id]
    }

    suspend fun createTrackpoint(trackpoint: ExposedTrackpoint): Int = dbQuery {
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

    suspend fun getTrackpointsForLap(lapIdValue: Int): List<ExposedTrackpoint> = dbQuery {
        Trackpoints.selectAll()
            .where { Trackpoints.lapId eq lapIdValue }
            .map {
                ExposedTrackpoint(
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
        newSuspendedTransaction(Dispatchers.IO) { block() }
}