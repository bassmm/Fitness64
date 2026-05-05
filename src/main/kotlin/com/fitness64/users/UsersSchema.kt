/**
 * UsersSchema.kt
 *
 * Defines the database schema and service layer for user management.
 * Handles user registration, authentication, profile retrieval,
 * and profile updates including fitness goals and preferences.
 */
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

/**
 * Represents a user account in the system.
 *
 * @property id The auto-generated user ID, or null for new users not yet saved.
 * @property name The user's display name.
 * @property email The user's email address, used for login.
 * @property passwordHash The BCrypt-hashed password (plain text on creation, hashed on save).
 * @property fitnessLevel The user's self-reported fitness level (e.g. Beginner, Advanced).
 * @property goal The user's fitness goal (e.g. Lose weight, Build muscle).
 * @property trainingDaysPerWeek The number of days per week the user plans to train.
 * @property preferredActivities Comma-separated list of preferred activity types.
 * @property community The user's training community or group name.
 * @property createdAt ISO timestamp of when the account was created.
 */
@Serializable
data class User(
    val id: Int? = null,
    val name: String,
    val email: String,
    val passwordHash: String,
    val fitnessLevel: String? = null,
    val goal: String? = null,
    val trainingDaysPerWeek: Int? = null,
    val preferredActivities: String? = null,
    val community: String? = null,
    val createdAt: String? = null
)

/**
 * Service class responsible for all database operations related to users.
 * Handles creation, retrieval, update, and deletion of user accounts.
 *
 * @param database The database connection to use for all operations.
 */
class UserService(database: Database) {

    /**
     * Database table definition for the users table.
     * Stores all user account information including profile fields.
     */
    object Users : Table("users") {
        val userId = integer("user_id").autoIncrement()
        val name = varchar("name", length = 255)
        val email = varchar("email", length = 255).uniqueIndex()
        val passwordHash = varchar("password_hash", length = 255)
        val fitnessLevel = varchar("fitness_level", length = 50).nullable()
        val goal = varchar("goal", length = 255).nullable()
        val trainingDaysPerWeek = integer("training_days_per_week").nullable()
        val preferredActivities = varchar("preferred_activities", length = 255).nullable()
        val community = varchar("community", length = 255).nullable()
        val createdAt = varchar("created_at", length = 50)

        override val primaryKey = PrimaryKey(userId)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    /**
     * Creates a new user account in the database.
     * The password is hashed using BCrypt before being stored.
     *
     * @param user The user data to save. The passwordHash field should contain the plain text password.
     * @return The auto-generated ID of the newly created user.
     */
    suspend fun create(user: User): Int = dbQuery {
        val hashed = BCrypt.hashpw(user.passwordHash, BCrypt.gensalt())

        Users.insert {
            it[name] = user.name
            it[email] = user.email
            it[passwordHash] = hashed
            it[fitnessLevel] = user.fitnessLevel
            it[goal] = user.goal
            it[trainingDaysPerWeek] = user.trainingDaysPerWeek
            it[preferredActivities] = user.preferredActivities
            it[community] = user.community
            it[createdAt] = LocalDateTime.now().toString()
        }[Users.userId]
    }

    /**
     * Retrieves a user by their ID.
     *
     * @param id The ID of the user to retrieve.
     * @return The matching [User], or null if not found.
     */
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
                        preferredActivities = it[Users.preferredActivities],
                        community = it[Users.community],
                        createdAt = it[Users.createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    /**
     * Retrieves a user by their email address.
     * Used during login and session validation.
     *
     * @param email The email address to search for.
     * @return The matching [User], or null if not found.
     */
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
                        preferredActivities = it[Users.preferredActivities],
                        community = it[Users.community],
                        createdAt = it[Users.createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    /**
     * Updates all fields of an existing user record.
     *
     * @param id The ID of the user to update.
     * @param user The updated user data to save.
     */
    suspend fun update(id: Int, user: User) {
        dbQuery {
            Users.update({ Users.userId eq id }) {
                it[name] = user.name
                it[email] = user.email
                it[passwordHash] = user.passwordHash
                it[fitnessLevel] = user.fitnessLevel
                it[goal] = user.goal
                it[trainingDaysPerWeek] = user.trainingDaysPerWeek
                it[preferredActivities] = user.preferredActivities
                it[community] = user.community
            }
        }
    }

    /**
     * Updates a user's profile information.
     * Does not update the password — use [update] for full record updates.
     *
     * @param userId The ID of the user to update.
     * @param name The updated display name.
     * @param email The updated email address.
     * @param fitnessLevel The updated fitness level, or null to clear.
     * @param goal The updated fitness goal, or null to clear.
     * @param trainingDaysPerWeek The updated training days per week, or null to clear.
     * @param preferredActivities The updated preferred activities, or null to clear.
     * @param community The updated community name, or null to clear.
     */
    suspend fun updateProfile(
        userId: Int,
        name: String,
        email: String,
        fitnessLevel: String?,
        goal: String?,
        trainingDaysPerWeek: Int?,
        preferredActivities: String?,
        community: String?
    ) {
        dbQuery {
            Users.update({ Users.userId eq userId }) {
                it[Users.name] = name
                it[Users.email] = email
                it[Users.fitnessLevel] = fitnessLevel
                it[Users.goal] = goal
                it[Users.trainingDaysPerWeek] = trainingDaysPerWeek
                it[Users.preferredActivities] = preferredActivities
                it[Users.community] = community
            }
        }
    }

    /**
     * Deletes a user account from the database by their ID.
     *
     * @param id The ID of the user to delete.
     */
    suspend fun delete(id: Int) {
        dbQuery {
            Users.deleteWhere { Users.userId eq id }
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