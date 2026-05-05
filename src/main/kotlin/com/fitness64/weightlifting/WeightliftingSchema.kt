package com.fitness64.weightlifting

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class WeightliftingWorkoutLog(
    val userId: Int,
    val logDate: String,
    val duration: Int,
    val notes: String? = null
)

@Serializable
data class WeightliftingLoggedExercise(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double? = null
)

@Serializable
data class WeightliftingWorkoutEntry(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double? = null
)

@Serializable
data class WeightliftingHistoryItem(
    val logDate: String,
    val duration: Int,
    val notes: String? = null,
    val totalSets: Int,
    val exercises: List<WeightliftingWorkoutEntry>
)

class WeightliftingService(database: Database) {

    object WeightliftingWorkoutLogs : Table("weightlifting_workout_logs") {
        val id = integer("weightlifting_workout_log_id").autoIncrement()
        val userId = integer("user_id")
        val logDate = varchar("log_date", 30)
        val duration = integer("duration")
        val notes = varchar("notes", 255).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object WeightliftingSessionExercises : Table("weightlifting_session_exercises") {
        val id = integer("weightlifting_session_exercise_id").autoIncrement()
        val workoutLogId = integer("weightlifting_workout_log_id").references(WeightliftingWorkoutLogs.id)
        val exerciseName = varchar("exercise_name", 255)
        val sets = integer("sets")
        val reps = integer("reps")
        val weight = double("weight").nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(
                WeightliftingWorkoutLogs,
                WeightliftingSessionExercises
            )
        }
    }

    suspend fun createWorkoutSession(
        workout: WeightliftingWorkoutLog,
        exercises: List<WeightliftingLoggedExercise>
    ): Int = dbQuery {
        val insertedWorkoutLogId = WeightliftingWorkoutLogs.insert {
            it[userId] = workout.userId
            it[logDate] = workout.logDate
            it[duration] = workout.duration
            it[notes] = workout.notes
        }[WeightliftingWorkoutLogs.id]

        exercises.forEach { exercise ->
            WeightliftingSessionExercises.insert {
                it[workoutLogId] = insertedWorkoutLogId
                it[exerciseName] = exercise.exerciseName
                it[sets] = exercise.sets
                it[reps] = exercise.reps
                it[weight] = exercise.weight
            }
        }

        insertedWorkoutLogId
    }

    suspend fun getWeightliftingHistory(userIdValue: Int): List<WeightliftingHistoryItem> = dbQuery {
        val rows = (WeightliftingWorkoutLogs innerJoin WeightliftingSessionExercises)
            .selectAll()
            .where { WeightliftingWorkoutLogs.userId eq userIdValue }
            .orderBy(WeightliftingWorkoutLogs.logDate to SortOrder.DESC)
            .map { row ->
                Triple(
                    row[WeightliftingWorkoutLogs.id],
                    row,
                    WeightliftingWorkoutEntry(
                        exerciseName = row[WeightliftingSessionExercises.exerciseName],
                        sets = row[WeightliftingSessionExercises.sets],
                        reps = row[WeightliftingSessionExercises.reps],
                        weight = row[WeightliftingSessionExercises.weight]
                    )
                )
            }

        rows.groupBy { it.first }.values.map { workoutRows ->
            val firstRow = workoutRows.first().second
            val exerciseEntries = workoutRows.map { it.third }
            val totalSets = exerciseEntries.sumOf { it.sets }

            WeightliftingHistoryItem(
                logDate = firstRow[WeightliftingWorkoutLogs.logDate],
                duration = firstRow[WeightliftingWorkoutLogs.duration],
                notes = firstRow[WeightliftingWorkoutLogs.notes],
                totalSets = totalSets,
                exercises = exerciseEntries
            )
        }.sortedByDescending { it.logDate }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}



