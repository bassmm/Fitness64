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
    val certificateUrl: String? = null,
    val id: Int = 0
)

class RaceService(database: Database) {

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

    suspend fun getRace(id: Int): RaceRecord? = dbQuery {
        RaceRecords.selectAll()
            .where { RaceRecords.id eq id }
            .map {
                RaceRecord(
                    id = it[RaceRecords.id],
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

    suspend fun getRacesForUser(userIdValue: Int): List<RaceRecord> = dbQuery {
        RaceRecords.selectAll()
            .where { RaceRecords.userId eq userIdValue }
            .map {
                RaceRecord(
                    id = it[RaceRecords.id],
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

    suspend fun deleteRace(id: Int) = dbQuery {
        RaceRecords.deleteWhere { RaceRecords.id eq id }
    }

    suspend fun updateRace(id: Int, finishTime: String?, overallRank: Int?, isPersonalBest: Boolean, notes: String) = dbQuery {
        RaceRecords.update({ RaceRecords.id eq id }) {
            it[RaceRecords.finishTime] = finishTime
            it[RaceRecords.overallRank] = overallRank
            it[RaceRecords.isPersonalBest] = isPersonalBest
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}