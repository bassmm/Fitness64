package com.fitness64.schema

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction

@Serializable
data class WeightliftingWorkoutLog(
    val userId: Int,
    val logDate: String,
    val duration: Int,
    val notes: String? = null,
    val name: String? = null
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
    val id: Int,
    val logDate: String,
    val duration: Int,
    val notes: String? = null,
    val totalSets: Int,
    val exercises: List<WeightliftingWorkoutEntry>,
    val name: String? = null
)

@Serializable
data class WeightliftingCreateRequest(
    val logDate: String,
    val duration: Int,
    val notes: String? = null,
    val exercises: List<WeightliftingLoggedExercise>
)

@Serializable
data class WeightliftingUpdateRequest(
    val duration: Int? = null,
    val notes: String? = null
)

@Serializable
data class WeightliftingApiResponse(
    val id: Int,
    val logDate: String,
    val duration: Int,
    val notes: String? = null,
    val totalSets: Int,
    val exercises: List<WeightliftingWorkoutEntry>
)

@Serializable
data class WeightliftingListResponse(
    val sessions: List<WeightliftingApiResponse>
)

class WeightliftingService(database: Database) {

    object WeightliftingWorkoutLogs : Table("weightlifting_workout_logs") {
        val id = integer("weightlifting_workout_log_id").autoIncrement()
        val userId = integer("user_id")
        val logDate = varchar("log_date", 30)
        val duration = integer("duration")
        val notes = varchar("notes", 255).nullable()
        val workoutName = varchar("name", 255).nullable()

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
            if (workout.name != null) {
                it[workoutName] = workout.name
            }
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
            val workoutId = workoutRows.first().first
            val exerciseEntries = workoutRows.map { it.third }
            val totalSets = exerciseEntries.sumOf { it.sets }

            WeightliftingHistoryItem(
                id = workoutId,
                logDate = firstRow[WeightliftingWorkoutLogs.logDate],
                duration = firstRow[WeightliftingWorkoutLogs.duration],
                notes = firstRow[WeightliftingWorkoutLogs.notes],
                totalSets = totalSets,
                exercises = exerciseEntries,
                name = firstRow[WeightliftingWorkoutLogs.workoutName]
            )
        }.sortedByDescending { it.logDate }
    }

    suspend fun updateWorkoutSession(id: Int, duration: Int, notes: String, name: String? = null) = dbQuery {
        WeightliftingWorkoutLogs.update({ WeightliftingWorkoutLogs.id eq id }) {
            it[WeightliftingWorkoutLogs.duration] = duration
            it[WeightliftingWorkoutLogs.notes] = notes
            if (name != null) {
                it[WeightliftingWorkoutLogs.workoutName] = name
            }
        }
    }

    suspend fun updateWorkoutSessionExercises(id: Int, exercises: List<WeightliftingLoggedExercise>) = dbQuery {
        WeightliftingSessionExercises.deleteWhere { WeightliftingSessionExercises.workoutLogId eq id }
        exercises.forEach { exercise ->
            WeightliftingSessionExercises.insert {
                it[workoutLogId] = id
                it[exerciseName] = exercise.exerciseName
                it[sets] = exercise.sets
                it[reps] = exercise.reps
                it[weight] = exercise.weight
            }
        }
    }

    suspend fun getWorkoutSessionById(id: Int, userIdValue: Int): WeightliftingHistoryItem? = dbQuery {
        val rows = (WeightliftingWorkoutLogs innerJoin WeightliftingSessionExercises)
            .selectAll()
            .where { (WeightliftingWorkoutLogs.id eq id) and (WeightliftingWorkoutLogs.userId eq userIdValue) }
            .map { row ->
                WeightliftingWorkoutEntry(
                    exerciseName = row[WeightliftingSessionExercises.exerciseName],
                    sets = row[WeightliftingSessionExercises.sets],
                    reps = row[WeightliftingSessionExercises.reps],
                    weight = row[WeightliftingSessionExercises.weight]
                )
            }

        if (rows.isEmpty()) return@dbQuery null

        val workoutRow = (WeightliftingWorkoutLogs innerJoin WeightliftingSessionExercises)
            .selectAll()
            .where { (WeightliftingWorkoutLogs.id eq id) and (WeightliftingWorkoutLogs.userId eq userIdValue) }
            .first()

        WeightliftingHistoryItem(
            id = workoutRow[WeightliftingWorkoutLogs.id],
            logDate = workoutRow[WeightliftingWorkoutLogs.logDate],
            duration = workoutRow[WeightliftingWorkoutLogs.duration],
            notes = workoutRow[WeightliftingWorkoutLogs.notes],
            totalSets = rows.sumOf { it.sets },
            exercises = rows,
            name = workoutRow[WeightliftingWorkoutLogs.workoutName]
        )
    }

    suspend fun deleteWorkoutSession(id: Int, userIdValue: Int): Boolean = dbQuery {
        val deleted = WeightliftingWorkoutLogs.deleteWhere {
            (WeightliftingWorkoutLogs.id eq id) and (WeightliftingWorkoutLogs.userId eq userIdValue)
        }
        deleted > 0
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}



