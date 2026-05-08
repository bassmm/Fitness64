package com.fitness64.core

import com.fitness64.schema.User
import com.fitness64.schema.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.pebble.*
import io.pebbletemplates.pebble.loader.ClasspathLoader
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.sessions.*

fun Application.configureTemplating() {
    install(Pebble) {
        loader(ClasspathLoader().apply {
            prefix = "templates"
            suffix = ".html"
        })
    }
}

suspend fun ApplicationCall.respondHx(
    templateName: String,
    model: Map<String, Any> = emptyMap(),
    target: String? = null,
    swap: String = "innerHTML",
    pushUrl: String? = null,
    retarget: String? = null,
    reswap: String? = null
) {
    response.header("HX-Request", "true")

    target?.let { response.header("HX-Target", it) }
    response.header("HX-Swap", swap)
    pushUrl?.let { response.header("HX-Push-Url", it) }
    retarget?.let { response.header("HX-Retarget", it) }
    reswap?.let { response.header("HX-Reswap", it) }

    respondTemplate(templateName, model)
}

suspend fun ApplicationCall.respondHxRedirect(url: String) {
    response.header("HX-Redirect", url)
    respondText("")
}

data class AuthenticatedUser(val user: User, val userId: Int)

suspend fun ApplicationCall.requireAuthenticatedUser(userService: UserService): AuthenticatedUser? {
    val session = principal<UserSession>() ?: run { respondRedirect("/login"); return null }
    val user = userService.findByEmail(session.email) ?: run { respondRedirect("/login"); return null }
    val userId = user.id ?: run { respondRedirect("/login"); return null }
    return AuthenticatedUser(user, userId)
}
