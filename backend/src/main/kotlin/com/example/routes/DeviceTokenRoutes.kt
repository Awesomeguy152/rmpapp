package com.example.routes

import com.example.services.DeviceTokenService
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
data class RegisterTokenRequest(
    val token: String,
    val platform: String = "android" // android, ios, web
)

@Serializable
data class RemoveTokenRequest(
    val token: String
)

fun Route.deviceTokenRoutes() {
    val service = DeviceTokenService()

    authenticate("auth-jwt") {
        // Регистрация FCM токена
        post("/api/device-token") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.subject?.toUuidOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val req = call.receive<RegisterTokenRequest>()
            
            if (req.token.isBlank()) {
                return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "token_required"))
            }

            val success = service.saveToken(userId, req.token, req.platform)
            
            if (success) {
                call.respond(HttpStatusCode.OK, mapOf("message" to "token_registered"))
            } else {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "failed_to_save_token"))
            }
        }

        // Удаление FCM токена (при логауте)
        delete("/api/device-token") {
            val req = call.receive<RemoveTokenRequest>()
            
            if (req.token.isBlank()) {
                return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "token_required"))
            }

            service.removeToken(req.token)
            call.respond(HttpStatusCode.OK, mapOf("message" to "token_removed"))
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
