/**
 * PlanSchema.kt
 *
 * Defines the database schema and service layer for training plans.
 * Manages weekly training plan generation, retrieval, and session updates.
 * Supports multiple plan types including cardio, weightlifting, custom, and beginner.
 */
package com.fitness64.plans

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

/**
 * Represents a single planned session within a weekly training plan.
 *
 * @property day The day of the week (e.g. Monday, Tuesday).
 * @property session The name or description of the planned session.
 * @property durationMinutes The planned duration in minutes.
 * @property intensity The intensity level (e.g. Low, Moderate, High, Rest).
 * @property isRestDay Whether this day is a designated rest day.
 */
@Serializable
data class PlanSessionView(
    val day: String,
    val session: String,
    val durationMinutes: Int,
    val intensity: String,
    val isRestDay: Boolean
)

/**
 * Service class responsible for all database operations related to training plans.
 * Handles plan generation for different fitness goals, retrieval of plan sessions,
 * and updates to individual session days.
 *
 * @param database The database connection to use for all operations.
 */
class PlanService(database: Database) {

    /**
     * Database table for training plans.
     * Each user can have one active plan at a time.
     */
    object TrainingPlans : Table("training_plans") {
        val id = integer("plan_id").autoIncrement()
        val userId = integer("user_id")
        val planType = varchar("plan_type", 50)
        val weekStartDate = varchar("week_start_date", 30)
        val createdAt = varchar("created_at", 40)

        override val primaryKey = PrimaryKey(id)
    }

    /**
     * Database table for individual sessions within a training plan.
     * Each row represents one day's planned session.
     */
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
            SchemaUtils.create(TrainingPlans, PlanSessions)
        }
    }

    /**
     * Generates a new weekly training plan for a user based on their chosen plan type.
     * Replaces any existing plan for the user before creating the new one.
     * Supported plan types: cardio, weightlifting, custom, and beginner (default).
     *
     * @param userId The ID of the user to generate the plan for.
     * @param planType The type of plan to generate (e.g. "cardio", "weightlifting", "custom").
     * @return The auto-generated ID of the newly created training plan.
     */
    suspend fun generatePlanForType(userId: Int, planType: String): Int = dbQuery {
        val existingPlanIds = TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userId }
            .map { it[TrainingPlans.id] }

        if (existingPlanIds.isNotEmpty()) {
            PlanSessions.deleteWhere { PlanSessions.planId inList existingPlanIds }
        }

        TrainingPlans.deleteWhere { TrainingPlans.userId eq userId }

        val today = LocalDate.now()
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val normalizedPlanType = planType.lowercase()

        val planId = TrainingPlans.insert {
            it[this.userId] = userId
            it[this.planType] = normalizedPlanType
            it[weekStartDate] = startOfWeek.toString()
            it[createdAt] = LocalDateTime.now().toString()
        }[TrainingPlans.id]

        val sessions = when (normalizedPlanType) {
            "cardio" -> listOf(
                PlanSessionView("Monday", "30-minute steady run", 30, "Moderate", false),
                PlanSessionView("Tuesday", "20-minute easy cycling", 20, "Low", false),
                PlanSessionView("Wednesday", "Rest or stretching", 10, "Low", true),
                PlanSessionView("Thursday", "Interval cardio session", 35, "High", false),
                PlanSessionView("Friday", "Rest day", 0, "Rest", true),
                PlanSessionView("Saturday", "45-minute long walk or run", 45, "Moderate", false),
                PlanSessionView("Sunday", "Recovery mobility", 15, "Low", false)
            )

            "weightlifting" -> listOf(
                PlanSessionView("Monday", "Upper body strength training", 45, "Moderate", false),
                PlanSessionView("Tuesday", "Rest or light walk", 20, "Low", true),
                PlanSessionView("Wednesday", "Lower body strength training", 45, "Moderate", false),
                PlanSessionView("Thursday", "Core and mobility", 25, "Low", false),
                PlanSessionView("Friday", "Full body strength training", 50, "Moderate", false),
                PlanSessionView("Saturday", "Rest day", 0, "Rest", true),
                PlanSessionView("Sunday", "Stretching and recovery", 15, "Low", false)
            )

            "custom" -> listOf(
                PlanSessionView("Monday", "Custom session", 30, "Custom", false),
                PlanSessionView("Tuesday", "Custom session", 30, "Custom", false),
                PlanSessionView("Wednesday", "Custom session", 30, "Custom", false),
                PlanSessionView("Thursday", "Custom session", 30, "Custom", false),
                PlanSessionView("Friday", "Custom session", 30, "Custom", false),
                PlanSessionView("Saturday", "Custom session", 30, "Custom", false),
                PlanSessionView("Sunday", "Rest or custom recovery", 0, "Custom", true)
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

    /**
     * Retrieves all plan sessions for a specific user ordered by display order.
     *
     * @param userId The ID of the user whose plan to retrieve.
     * @return A list of [PlanSessionView] objects representing the full weekly plan.
     */
    suspend fun getPlan(userId: Int): List<PlanSessionView> = dbQuery {
        (TrainingPlans innerJoin PlanSessions)
            .selectAll()
            .where { TrainingPlans.userId eq userId }
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

    /**
     * Checks whether a training plan exists for a specific user.
     *
     * @param userId The ID of the user to check.
     * @return True if the user has an existing plan, false otherwise.
     */
    suspend fun hasPlan(userId: Int): Boolean = dbQuery {
        TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userId }
            .map { it[TrainingPlans.id] }
            .isNotEmpty()
    }

    /**
     * Retrieves the plan type for a specific user's current training plan.
     *
     * @param userId The ID of the user whose plan type to retrieve.
     * @return The plan type string (e.g. "cardio", "weightlifting"), or null if no plan exists.
     */
    suspend fun getPlanType(userId: Int): String? = dbQuery {
        TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userId }
            .map { it[TrainingPlans.planType] }
            .singleOrNull()
    }

    /**
     * Retrieves the planned session for a specific day of the week for a user.
     * Used on the dashboard to show today's planned training.
     *
     * @param userId The ID of the user.
     * @param dayValue The day name to look up (e.g. "Monday", "Tuesday").
     * @return The matching [PlanSessionView], or null if no session is planned for that day.
     */
    suspend fun getPlanSessionByDay(userId: Int, dayValue: String): PlanSessionView? = dbQuery {
        (TrainingPlans innerJoin PlanSessions)
            .selectAll()
            .where {
                (TrainingPlans.userId eq userId) and
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

    /**
     * Updates a specific day's session within a user's training plan.
     *
     * @param userId The ID of the user whose plan to update.
     * @param dayValue The day of the week to update (e.g. "Monday").
     * @param newSession The new session name or description.
     * @param newDuration The new planned duration in minutes.
     * @param newIntensity The new intensity level.
     */
    suspend fun updatePlanSession(
        userId: Int,
        dayValue: String,
        newSession: String,
        newDuration: Int,
        newIntensity: String
    ) = dbQuery {
        val planId = TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userId }
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
                it[durationMinutes] = newDuration
                it[intensity] = newIntensity
                it[isRestDay] = newSession.lowercase().contains("rest")
            }
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