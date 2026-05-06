/**
 * Databases.kt
 *
 * Configures and initialises the database connection for the application.
 * Automatically selects between an in-memory H2 database for testing
 * and a persistent SQLite database for production.
 */
package com.fitness64

import org.jetbrains.exposed.v1.jdbc.Database

/**
 * Configures and returns the appropriate database connection based on the runtime environment.
 * Uses an in-memory H2 database when running tests, and a persistent SQLite database in production.
 *
 * @return The configured [Database] connection.
 */
fun configureDatabases(): Database {
    return when {
        isRunningTests() -> createTestDatabase()
        else -> createProductionDatabase()
    }
}

/**
 * Creates an in-memory H2 database connection for use during testing.
 * The database is kept alive for the duration of the JVM session.
 *
 * @return A [Database] connected to an in-memory H2 instance.
 */
private fun createTestDatabase(): Database {
    return Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=false",
        driver = "org.h2.Driver"
    )
}

/**
 * Creates a persistent SQLite database connection for production use.
 * The database file is stored at ./data/database.db relative to the working directory.
 *
 * @return A [Database] connected to the SQLite production database.
 */
private fun createProductionDatabase(): Database {
    return Database.connect(
        url = "jdbc:sqlite:./data/database.db",
        driver = "org.sqlite.JDBC"
    )
}

/**
 * Detects whether the application is currently running in a test environment.
 * Checks multiple indicators including system properties, environment variables,
 * Gradle test mode, and the presence of JUnit on the classpath.
 *
 * @return True if running in a test environment, false otherwise.
 */
private fun isRunningTests(): Boolean {
    return System.getProperty("test.database") == "true" ||
            System.getenv("TEST_MODE")?.toBoolean() == true ||
            System.getProperty("gradle.test.mode") == "true" ||
            try {
                Class.forName("org.junit.platform.engine.TestEngine")
                true
            } catch (e: ClassNotFoundException) {
                false
            }
}