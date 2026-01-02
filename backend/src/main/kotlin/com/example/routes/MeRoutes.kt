package com.example.routes

import com.example.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UpdateProfileRq(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

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

        patch("/api/me") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@patch call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.subject?.toUuidOrNull()
                ?: return@patch call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val rq = call.receive<UpdateProfileRq>()

            try {
                val updated = service.updateProfile(
                    userId = userId,
                    username = rq.username,
                    displayName = rq.displayName,
                    bio = rq.bio,
                    avatarUrl = rq.avatarUrl
                )
                call.respond(updated)
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to (e.message ?: "invalid_request")))
            }
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
