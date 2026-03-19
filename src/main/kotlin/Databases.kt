package com.comp2850

import io.ktor.server.application.*
import io.ktor.util.*
import org.jetbrains.exposed.sql.Database

val UserServiceKey = AttributeKey<UserService>("userService")

fun Application.configureDatabases() {
    val database = Database.connect(
        url = "jdbc:h2:mem:test;DB_CLOSE_DELAY=-1",
        user = "root",
        driver = "org.h2.Driver",
        password = "",
    )

    attributes.put(UserServiceKey, UserService(database))
}
