package com.comp2850

import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant

@Serializable
data class RegisterRequest(
    val username: String,
    val email: String,
    val password: String,
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class UpdateProfileRequest(
    val email: String,
    val currentWeight: Double? = null,
    val targetWeight: Double? = null,
)

@Serializable
data class UserProfileResponse(
    val userId: Int,
    val username: String,
    val email: String,
    val dateJoined: String,
    val currentWeight: Double? = null,
    val targetWeight: Double? = null,
)

@Serializable
data class AuthResponse(
    val user: UserProfileResponse,
    val message: String,
)

enum class UpdateProfileStatus {
    UPDATED,
    EMAIL_ALREADY_IN_USE,
    USER_NOT_FOUND,
}

data class UpdateProfileResult(
    val status: UpdateProfileStatus,
    val profile: UserProfileResponse? = null,
)

class UserService(private val database: Database) {
    object Users : Table("users") {
        val userId = integer("user_id").autoIncrement()
        val username = varchar("username", 50).uniqueIndex()
        val email = varchar("email", 255).uniqueIndex()
        val passwordHash = varchar("password_hash", 255)
        val dateJoined = varchar("date_joined", 50)
        val currentWeight = double("current_weight").nullable()
        val targetWeight = double("target_weight").nullable()

        override val primaryKey = PrimaryKey(userId)
    }

    init {
        transaction(database) {
            SchemaUtils.create(Users)
        }
    }

    suspend fun register(request: RegisterRequest): UserProfileResponse? = dbQuery {
        val cleanedUsername = request.username.trim()
        val normalizedEmail = request.email.trim().lowercase()
        val joinedAt = Instant.now().toString()

        val existingByUsername = Users
            .selectAll()
            .where { Users.username eq cleanedUsername }
            .singleOrNull()

        val existingByEmail = Users
            .selectAll()
            .where { Users.email eq normalizedEmail }
            .singleOrNull()

        if (existingByUsername != null || existingByEmail != null) {
            return@dbQuery null
        }

        val newUserId = Users.insert {
            it[username] = cleanedUsername
            it[email] = normalizedEmail
            it[passwordHash] = Passwords.hash(request.password)
            it[dateJoined] = joinedAt
            it[currentWeight] = null
            it[targetWeight] = null
        }[Users.userId]

        UserProfileResponse(
            userId = newUserId,
            username = cleanedUsername,
            email = normalizedEmail,
            dateJoined = joinedAt,
            currentWeight = null,
            targetWeight = null,
        )
    }

    suspend fun authenticate(request: LoginRequest): UserProfileResponse? = dbQuery {
        val normalizedEmail = request.email.trim().lowercase()

        val row = Users
            .selectAll()
            .where { Users.email eq normalizedEmail }
            .singleOrNull()
            ?: return@dbQuery null

        val passwordMatches = Passwords.verify(
            password = request.password,
            hash = row[Users.passwordHash],
        )

        if (!passwordMatches) {
            return@dbQuery null
        }

        row.toProfile()
    }

    suspend fun getProfile(userId: Int): UserProfileResponse? = dbQuery {
        Users
            .selectAll()
            .where { Users.userId eq userId }
            .map { it.toProfile() }
            .singleOrNull()
    }

    suspend fun updateProfile(userId: Int, request: UpdateProfileRequest): UpdateProfileResult = dbQuery {
        val normalizedEmail = request.email.trim().lowercase()

        val existingByEmail = Users
            .selectAll()
            .where { Users.email eq normalizedEmail }
            .singleOrNull()

        if (existingByEmail != null && existingByEmail[Users.userId] != userId) {
            return@dbQuery UpdateProfileResult(UpdateProfileStatus.EMAIL_ALREADY_IN_USE)
        }

        val updatedRows = Users.update({ Users.userId eq userId }) {
            it[email] = normalizedEmail
            it[currentWeight] = request.currentWeight
            it[targetWeight] = request.targetWeight
        }

        if (updatedRows == 0) {
            return@dbQuery UpdateProfileResult(UpdateProfileStatus.USER_NOT_FOUND)
        }

        val updatedProfile = Users
            .selectAll()
            .where { Users.userId eq userId }
            .map { it.toProfile() }
            .single()

        UpdateProfileResult(
            status = UpdateProfileStatus.UPDATED,
            profile = updatedProfile,
        )
    }

    private fun ResultRow.toProfile() = UserProfileResponse(
        userId = this[Users.userId],
        username = this[Users.username],
        email = this[Users.email],
        dateJoined = this[Users.dateJoined],
        currentWeight = this[Users.currentWeight],
        targetWeight = this[Users.targetWeight],
    )

    private suspend fun <T> dbQuery(block: suspend Transaction.() -> T): T =
        newSuspendedTransaction(Dispatchers.IO, db = database) {
            block()
        }
}
