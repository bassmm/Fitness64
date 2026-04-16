package com.fitness64

import com.fitness64.users.configureUsersRoutes
import io.ktor.server.application.*
import org.jetbrains.exposed.v1.jdbc.Database

fun Application.configureDatabases(): Database {
    val database = Database.connect(
        url = "jdbc:sqlite:./data/database.db",
        driver = "org.sqlite.JDBC"
    )
    return database
}
