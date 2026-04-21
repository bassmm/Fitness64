package com.fitness64

import com.fitness64.tcx.*
import com.fitness64.users.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    // Service Init
    val database = configureDatabases()
    val userService = UserService(database)
    val tcxService = TcxService(database)

    // Setup
    configureTemplating()
    configureSerialization()
    configureSecurity(userService)

    // Routing
    configureRouting(userService)
    configureUsersRoutes(userService)
    configureTcxRoutes(tcxService)
}
