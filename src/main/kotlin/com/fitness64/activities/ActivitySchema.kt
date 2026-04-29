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
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.suspendTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.time.LocalDate
import java.time.LocalDateTime

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
data class WeightliftingHistoryItem(
    val logDate: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weight: Double,
    val duration: Int,
    val notes: String? = null
)

@Serializable
data class PlanSessionView(
    val day: String,
    val session: String,
    val durationMinutes: Int,
    val intensity: String,
    val isRestDay: Boolean
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

    object TrainingPlans : Table("training_plans") {
        val id = integer("plan_id").autoIncrement()
        val userId = integer("user_id")
        val planType = varchar("plan_type", 50)
        val weekStartDate = varchar("week_start_date", 30)
        val createdAt = varchar("created_at", 40)

        override val primaryKey = PrimaryKey(id)
    }

    object PlanSessions : Table("plan_sessions") {
        val id = integer("session_id").autoIncrement()
        val planId = integer("plan_id").references(TrainingPlans.id)
        val day = varchar("day", 20)
        val sessionName = varchar("session_name", 255)
        val durationMinutes = integer("duration_minutes")
        val intensity = varchar("intensity", 50)
        val isRestDay = bool("is_rest_day")
        val displayOrder = integer("display_order")

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
                Trackpoints,
                TrainingPlans,
                PlanSessions
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
            it[sets] = workoutExercise.sets
            it[reps] = workoutExercise.reps
            it[weight] = workoutExercise.weight
        }[WorkoutExercises.id]
    }

    suspend fun getWeightliftingHistory(userIdValue: Int): List<WeightliftingHistoryItem> = dbQuery {
        (WorkoutExercises innerJoin WorkoutLogs innerJoin Exercises)
            .selectAll()
            .where { WorkoutLogs.userId eq userIdValue }
            .map {
                WeightliftingHistoryItem(
                    logDate = it[WorkoutLogs.logDate],
                    exerciseName = it[Exercises.name],
                    sets = it[WorkoutExercises.sets],
                    reps = it[WorkoutExercises.reps],
                    weight = it[WorkoutExercises.weight],
                    duration = it[WorkoutLogs.duration],
                    notes = it[WorkoutLogs.notes]
                )
            }
            .sortedByDescending { it.logDate }
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

    suspend fun generatePlanForLevel(userIdValue: Int, fitnessLevel: String): Int = dbQuery {
        val existingPlanIds = TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userIdValue }
            .map { it[TrainingPlans.id] }

        if (existingPlanIds.isNotEmpty()) {
            PlanSessions.deleteWhere { PlanSessions.planId inList existingPlanIds }
        }

        TrainingPlans.deleteWhere { TrainingPlans.userId eq userIdValue }

        val today = LocalDate.now()
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)

        val normalizedLevel = fitnessLevel.lowercase()

        val planId = TrainingPlans.insert {
            it[userId] = userIdValue
            it[planType] = normalizedLevel
            it[weekStartDate] = startOfWeek.toString()
            it[createdAt] = LocalDateTime.now().toString()
        }[TrainingPlans.id]

        val sessions = when (normalizedLevel) {
            "intermediate" -> listOf(
                PlanSessionView("Monday", "30-minute steady run", 30, "Moderate", false),
                PlanSessionView("Tuesday", "Full-body strength training", 45, "Moderate", false),
                PlanSessionView("Wednesday", "Rest or mobility", 15, "Low", true),
                PlanSessionView("Thursday", "40-minute cycling or cardio", 40, "Moderate", false),
                PlanSessionView("Friday", "Core and upper body workout", 35, "Moderate", false),
                PlanSessionView("Saturday", "45-minute moderate run", 45, "Moderate", false),
                PlanSessionView("Sunday", "Rest day", 0, "Rest", true)
            )

            "advanced" -> listOf(
                PlanSessionView("Monday", "Recovery run", 40, "Low", false),
                PlanSessionView("Tuesday", "Interval training - 6 x 800m", 60, "High", false),
                PlanSessionView("Wednesday", "Strength training and mobility", 50, "Moderate", false),
                PlanSessionView("Thursday", "Tempo run", 50, "High", false),
                PlanSessionView("Friday", "Rest or easy cross-training", 30, "Low", true),
                PlanSessionView("Saturday", "Long run - marathon preparation", 90, "High", false),
                PlanSessionView("Sunday", "Recovery walk and stretching", 30, "Low", false)
            )

            else -> listOf(
                PlanSessionView("Monday", "20-minute easy walk", 20, "Low", false),
                PlanSessionView("Tuesday", "Rest day", 0, "Rest", true),
                PlanSessionView("Wednesday", "15-minute beginner home workout", 15, "Low", false),
                PlanSessionView("Thursday", "20-minute light cycling or walking", 20, "Low", false),
                PlanSessionView("Friday", "Rest day", 0, "Rest", true),
                PlanSessionView("Saturday", "10-minute stretching and mobility", 10, "Very low", false),
                PlanSessionView("Sunday", "Rest day", 0, "Rest", true)
            )
        }

        sessions.forEachIndexed { index, session ->
            PlanSessions.insert {
                it[PlanSessions.planId] = planId
                it[day] = session.day
                it[sessionName] = session.session
                it[durationMinutes] = session.durationMinutes
                it[intensity] = session.intensity
                it[isRestDay] = session.isRestDay
                it[displayOrder] = index
            }
        }

        planId
    }

    suspend fun getPlanForUser(userIdValue: Int): List<PlanSessionView> = dbQuery {
        (TrainingPlans innerJoin PlanSessions)
            .selectAll()
            .where { TrainingPlans.userId eq userIdValue }
            .orderBy(PlanSessions.displayOrder to SortOrder.ASC)
            .map {
                PlanSessionView(
                    day = it[PlanSessions.day],
                    session = it[PlanSessions.sessionName],
                    durationMinutes = it[PlanSessions.durationMinutes],
                    intensity = it[PlanSessions.intensity],
                    isRestDay = it[PlanSessions.isRestDay]
                )
            }
    }

    suspend fun getPlanSessionByDay(userIdValue: Int, dayValue: String): PlanSessionView? = dbQuery {
        (TrainingPlans innerJoin PlanSessions)
            .selectAll()
            .where {
                (TrainingPlans.userId eq userIdValue) and
                        (PlanSessions.day eq dayValue)
            }
            .map {
                PlanSessionView(
                    day = it[PlanSessions.day],
                    session = it[PlanSessions.sessionName],
                    durationMinutes = it[PlanSessions.durationMinutes],
                    intensity = it[PlanSessions.intensity],
                    isRestDay = it[PlanSessions.isRestDay]
                )
            }
            .singleOrNull()
    }

    suspend fun updatePlanSession(userIdValue: Int, dayValue: String, newSession: String) = dbQuery {
        val planId = TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userIdValue }
            .map { it[TrainingPlans.id] }
            .singleOrNull()

        if (planId != null) {
            PlanSessions.update(
                where = {
                    (PlanSessions.planId eq planId) and
                            (PlanSessions.day eq dayValue)
                }
            ) {
                it[sessionName] = newSession
                it[durationMinutes] = 20
                it[intensity] = "Low"
                it[isRestDay] = newSession.lowercase().contains("rest")
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}