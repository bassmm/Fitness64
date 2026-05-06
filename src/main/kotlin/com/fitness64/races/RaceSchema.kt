/**
 * RaceSchema.kt
 *
 * Defines the database schema and service layer for race records.
 * Handles storage and retrieval of race results logged by users,
 * including finish times, rankings, and personal best flags.
 */
package com.fitness64.races

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

/**
 * Represents a race result logged by a user.
 *
 * @property userId The ID of the user who logged this race.
 * @property eventName The name of the race event.
 * @property eventDate The date the race took place (ISO format: yyyy-MM-dd).
 * @property location Optional location of the race.
 * @property category Optional race category (e.g. 10K, Half Marathon).
 * @property finishTime Optional finish time as a string (e.g. "52:34").
 * @property overallRank Optional overall finishing position.
 * @property categoryRank Optional finishing position within category.
 * @property isPersonalBest Whether this result is a personal best.
 * @property certificateUrl Optional URL to the official race certificate.
 */
@Serializable
data class RaceRecord(
    val userId: Int,
    val eventName: String,
    val eventDate: String,
    val location: String? = null,
    val category: String? = null,
    val finishTime: String? = null,
    val overallRank: Int? = null,
    val categoryRank: Int? = null,
    val isPersonalBest: Boolean = false,
    val certificateUrl: String? = null
)

/**
 * Service class responsible for all database operations related to race records.
 * Handles creation, retrieval, and deletion of race entries in the race_records table.
 *
 * @param database The database connection to use for all operations.
 */
class RaceService(database: Database) {

    /**
     * Database table definition for race_records.
     * Maps each column to its corresponding field in [RaceRecord].
     */
    object RaceRecords : Table("race_records") {
        val id = integer("race_id").autoIncrement()
        val userId = integer("user_id")
        val eventName = varchar("event_name", 255)
        val eventDate = varchar("event_date", 30)
        val location = varchar("location", 255).nullable()
        val category = varchar("category", 100).nullable()
        val finishTime = varchar("finish_time", 50).nullable()
        val overallRank = integer("overall_rank").nullable()
        val categoryRank = integer("category_rank").nullable()
        val isPersonalBest = bool("is_personal_best").default(false)
        val certificateUrl = varchar("certificate_url", 500).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.create(RaceRecords)
        }
    }

    /**
     * Inserts a new race record into the database.
     *
     * @param race The race data to save.
     * @return The auto-generated ID of the newly created race record.
     */
    suspend fun createRace(race: RaceRecord): Int = dbQuery {
        RaceRecords.insert {
            it[userId] = race.userId
            it[eventName] = race.eventName
            it[eventDate] = race.eventDate
            it[location] = race.location
            it[category] = race.category
            it[finishTime] = race.finishTime
            it[overallRank] = race.overallRank
            it[categoryRank] = race.categoryRank
            it[isPersonalBest] = race.isPersonalBest
            it[certificateUrl] = race.certificateUrl
        }[RaceRecords.id]
    }

    /**
     * Retrieves a single race record by its ID.
     *
     * @param id The ID of the race record to retrieve.
     * @return The matching [RaceRecord], or null if not found.
     */
    suspend fun getRace(id: Int): RaceRecord? = dbQuery {
        RaceRecords.selectAll()
            .where { RaceRecords.id eq id }
            .map {
                RaceRecord(
                    userId = it[RaceRecords.userId],
                    eventName = it[RaceRecords.eventName],
                    eventDate = it[RaceRecords.eventDate],
                    location = it[RaceRecords.location],
                    category = it[RaceRecords.category],
                    finishTime = it[RaceRecords.finishTime],
                    overallRank = it[RaceRecords.overallRank],
                    categoryRank = it[RaceRecords.categoryRank],
                    isPersonalBest = it[RaceRecords.isPersonalBest],
                    certificateUrl = it[RaceRecords.certificateUrl]
                )
            }
            .singleOrNull()
    }

    /**
     * Retrieves all race records belonging to a specific user.
     *
     * @param userIdValue The ID of the user whose races to retrieve.
     * @return A list of [RaceRecord] objects for the given user.
     */
    suspend fun getRacesForUser(userIdValue: Int): List<RaceRecord> = dbQuery {
        RaceRecords.selectAll()
            .where { RaceRecords.userId eq userIdValue }
            .map {
                RaceRecord(
                    userId = it[RaceRecords.userId],
                    eventName = it[RaceRecords.eventName],
                    eventDate = it[RaceRecords.eventDate],
                    location = it[RaceRecords.location],
                    category = it[RaceRecords.category],
                    finishTime = it[RaceRecords.finishTime],
                    overallRank = it[RaceRecords.overallRank],
                    categoryRank = it[RaceRecords.categoryRank],
                    isPersonalBest = it[RaceRecords.isPersonalBest],
                    certificateUrl = it[RaceRecords.certificateUrl]
                )
            }
    }

    /**
     * Deletes a race record from the database by its ID.
     *
     * @param id The ID of the race record to delete.
     */
    suspend fun deleteRace(id: Int) = dbQuery {
        RaceRecords.deleteWhere { RaceRecords.id eq id }
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