/**
 * WeightliftingSchema.kt
 *
 * Defines the database schema and service layer for weightlifting sessions.
 * Manages workout logs and their associated exercises, supporting
 * multi-exercise sessions with sets, reps, and optional weight tracking.
 */
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

/**
 * Represents a weightlifting workout session log entry.
 *
 * @property userId The ID of the user who logged this session.
 * @property logDate The date of the session (ISO format: yyyy-MM-dd).
 * @property duration Duration of the session in minutes.
 * @property notes Optional notes about the session.
 */
@Serializable
data class WeightliftingWorkoutLog(
    val userId: Int,
    val logDate: String,
    val duration: Int,
    val notes: String? = null,
    val name: String? = null
)

/**
 * Represents a single exercise logged within a weightlifting session.
 *
 * @property exerciseName The name of the exercise performed.
 * @property sets Number of sets completed.
 * @property reps Number of repetitions per set.
 * @property weight Optional weight used in kg.
 */
@Serializable
data class WeightliftingLoggedExercise(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double? = null
)

/**
 * Represents a single exercise entry within a weightlifting history item.
 * Used for display in the activity history feed.
 *
 * @property exerciseName The name of the exercise.
 * @property sets Number of sets completed.
 * @property reps Number of repetitions per set.
 * @property weight Optional weight used in kg.
 */
@Serializable
data class WeightliftingWorkoutEntry(
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double? = null
)

/**
 * Represents a complete weightlifting session for display in activity history.
 * Groups all exercises performed in a single session.
 *
 * @property logDate The date of the session (ISO format: yyyy-MM-dd).
 * @property duration Duration of the session in minutes.
 * @property notes Optional session notes.
 * @property totalSets Total number of sets across all exercises in this session.
 * @property exercises List of exercises performed in this session.
 */
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

/**
 * Response wrapper for listing weightlifting sessions via the API.
 *
 * @property sessions The list of weightlifting session responses.
 */
@Serializable
data class WeightliftingListResponse(
    val sessions: List<WeightliftingApiResponse>
)

/**
 * Service class responsible for all database operations related to weightlifting sessions.
 * Handles creation and retrieval of workout logs and their associated exercises.
 *
 * @param database The database connection to use for all operations.
 */
class WeightliftingService(database: Database) {

    /**
     * Database table for weightlifting workout session logs.
     */
    object WeightliftingWorkoutLogs : Table("weightlifting_workout_logs") {
        val id = integer("weightlifting_workout_log_id").autoIncrement()
        val userId = integer("user_id")
        val logDate = varchar("log_date", 30)
        val duration = integer("duration")
        val notes = varchar("notes", 255).nullable()
        val workoutName = varchar("name", 255).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table for individual exercises within a weightlifting session.
     * Each row represents one exercise performed during a session.
     */
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

    /**
     * Creates a new weightlifting session with all its exercises in a single transaction.
     * Inserts the session log first, then inserts each exercise linked to that session.
     *
     * @param workout The session metadata to save (date, duration, notes).
     * @param exercises The list of exercises performed during the session.
     * @return The auto-generated ID of the newly created workout session log.
     */
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

    /**
     * Retrieves the complete weightlifting history for a specific user.
     * Groups exercises by session and calculates total sets per session.
     *
     * @param userIdValue The ID of the user whose history to retrieve.
     * @return A list of [WeightliftingHistoryItem] objects sorted by date descending.
     */
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

    /**
     * Updates the metadata of an existing weightlifting session.
     *
     * @param id The ID of the session to update.
     * @param duration The new duration in minutes.
     * @param notes The new session notes.
     * @param name Optional new custom workout name.
     */
    suspend fun updateWorkoutSession(id: Int, duration: Int, notes: String, name: String? = null, date: String? = null) = dbQuery {
        WeightliftingWorkoutLogs.update({ WeightliftingWorkoutLogs.id eq id }) {
            it[WeightliftingWorkoutLogs.duration] = duration
            it[WeightliftingWorkoutLogs.notes] = notes
            if (name != null) {
                it[WeightliftingWorkoutLogs.workoutName] = name
            }
            if (date != null) {
                it[WeightliftingWorkoutLogs.logDate] = date
            }
        }
    }

    /**
     * Replaces all exercises for an existing weightlifting session.
     * Deletes current exercises and inserts the new set.
     *
     * @param id The ID of the session to update.
     * @param exercises The new list of exercises to associate with the session.
     */
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

    /**
     * Retrieves a single weightlifting session by its ID for a specific user.
     *
     * @param id The ID of the session to retrieve.
     * @param userIdValue The ID of the user who owns the session.
     * @return A [WeightliftingHistoryItem] for the session, or null if not found.
     */
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

    /**
     * Deletes a weightlifting session and all its exercises.
     *
     * @param id The ID of the session to delete.
     * @param userIdValue The ID of the user who owns the session (for ownership verification).
     * @return True if a session was deleted, false if no matching session was found.
     */
    suspend fun deleteWorkoutSession(id: Int, userIdValue: Int): Boolean = dbQuery {
        val deleted = WeightliftingWorkoutLogs.deleteWhere {
            (WeightliftingWorkoutLogs.id eq id) and (WeightliftingWorkoutLogs.userId eq userIdValue)
        }
        deleted > 0
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