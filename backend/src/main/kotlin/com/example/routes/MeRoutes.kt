package com.example.routes

import com.example.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.util.UUID

fun Route.meRoutes() {
    val service = UserService()

    authenticate("auth-jwt") {
        get("/api/me") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.subject?.toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val profile = service.findProfile(userId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "user_not_found"))

            call.respond(profile)
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
