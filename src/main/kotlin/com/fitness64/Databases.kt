package com.fitness64

import org.jetbrains.exposed.v1.jdbc.Database

fun configureDatabases(): Database {
    return when {
        isRunningTests() -> createTestDatabase()
        else -> createProductionDatabase()
    }
}

private fun createTestDatabase(): Database {
    return Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        driver = "org.h2.Driver"
    )
}

private fun createProductionDatabase(): Database {
    return Database.connect(
        url = "jdbc:sqlite:./data/database.db",
        driver = "org.sqlite.JDBC"
    )
}

private fun isRunningTests(): Boolean {
    return System.getProperty("test.database") == "true" ||
            System.getenv("TEST_MODE")?.toBoolean() == true ||
            // Detect Gradle test task execution
            System.getProperty("gradle.test.mode") == "true" ||
            // Check if JUnit is on the classpath
            try {
                Class.forName("org.junit.platform.engine.TestEngine")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
}
