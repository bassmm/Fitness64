package com.fitness64.users

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
import org.mindrot.jbcrypt.BCrypt
import java.time.LocalDateTime

@Serializable
data class User(
    val id: Int? = null,
    val name: String,
    val email: String,
    val passwordHash: String,
    val fitnessLevel: String? = null,
    val goal: String? = null,
    val trainingDaysPerWeek: Int? = null,
    val createdAt: String? = null
)

class UserService(database: Database) {
    object Users : Table("users") {
        val userId = integer("user_id").autoIncrement()
        val name = varchar("name", length = 255)
        val email = varchar("email", length = 255).uniqueIndex()
        val passwordHash = varchar("password_hash", length = 255)
        val fitnessLevel = varchar("fitness_level", length = 50).nullable()
        val goal = varchar("goal", length = 255).nullable()
        val trainingDaysPerWeek = integer("training_days_per_week").nullable()
        val createdAt = varchar("created_at", length = 50)

        override val primaryKey = PrimaryKey(userId)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun create(user: User): Int = dbQuery {
        val hashed = BCrypt.hashpw(user.passwordHash, BCrypt.gensalt())
        Users.insert {
            it[name] = user.name
            it[email] = user.email
            it[passwordHash] = hashed
            it[fitnessLevel] = user.fitnessLevel
            it[goal] = user.goal
            it[trainingDaysPerWeek] = user.trainingDaysPerWeek
            it[createdAt] = LocalDateTime.now().toString()
        }[Users.userId]
    }

    suspend fun read(id: Int): User? {
        return dbQuery {
            Users.selectAll()
                .where { Users.userId eq id }
                .map {
                    User(
                        id = it[Users.userId],
                        name = it[Users.name],
                        email = it[Users.email],
                        passwordHash = it[Users.passwordHash],
                        fitnessLevel = it[Users.fitnessLevel],
                        goal = it[Users.goal],
                        trainingDaysPerWeek = it[Users.trainingDaysPerWeek],
                        createdAt = it[Users.createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun findByEmail(email: String): User? {
        return dbQuery {
            Users.selectAll()
                .where { Users.email eq email }
                .map {
                    User(
                        id = it[Users.userId],
                        name = it[Users.name],
                        email = it[Users.email],
                        passwordHash = it[Users.passwordHash],
                        fitnessLevel = it[Users.fitnessLevel],
                        goal = it[Users.goal],
                        trainingDaysPerWeek = it[Users.trainingDaysPerWeek],
                        createdAt = it[Users.createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun update(id: Int, user: User) {
        dbQuery {
            Users.update({ Users.userId eq id }) {
                it[name] = user.name
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[fitnessLevel] = user.fitnessLevel
                it[goal] = user.goal
                it[trainingDaysPerWeek] = user.trainingDaysPerWeek
            }
        }
    }

    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.userId eq id }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { suspendTransaction { block() } }
}