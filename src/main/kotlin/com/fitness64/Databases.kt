package com.fitness64

import org.jetbrains.exposed.v1.jdbc.Database

fun configureDatabases(): Database {
    val database = Database.connect(
        url = "jdbc:sqlite:./data/database.db",
        driver = "org.sqlite.JDBC"
    )
    return database
}
