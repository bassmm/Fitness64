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

@Serializable
data class PlanSessionView(
    val day: String,
    val session: String,
    val durationMinutes: Int,
    val intensity: String,
    val isRestDay: Boolean
)

class PlanService(database: Database) {

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
            SchemaUtils.create(TrainingPlans, PlanSessions)
        }
    }

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

    suspend fun hasPlan(userId: Int): Boolean = dbQuery {
        TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userId }
            .map { it[TrainingPlans.id] }
            .isNotEmpty()
    }

    suspend fun getPlanType(userId: Int): String? = dbQuery {
        TrainingPlans.selectAll()
            .where { TrainingPlans.userId eq userId }
            .map { it[TrainingPlans.planType] }
            .singleOrNull()
    }

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

    suspend fun updatePlanSession(userId: Int, dayValue: String, newSession: String) = dbQuery {
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
                it[durationMinutes] = 20
                it[intensity] = "Low"
                it[isRestDay] = newSession.lowercase().contains("rest")
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}