package com.example.routes

import com.example.services.ChatEventBroadcaster
import com.example.services.UserService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class UserPresenceResponse(
    val userId: String,
    val isOnline: Boolean,
    val lastSeenAt: String
)

fun Route.userRoutes() {
    val service = UserService()

    authenticate("auth-jwt") {
        get("/api/users") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@get call.respond(HttpStatusCode.Unauthorized)

            val requesterId = principal.subject?.toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val limit = call.request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, 100) ?: 20
            val query = call.request.queryParameters["q"]

            val users = service.searchContacts(requesterId, query, limit)
            call.respond(users)
        }

        // Получить онлайн-статус пользователя
        get("/api/users/{id}/presence") {
            val userId = call.parameters["id"]?.toUuidOrNull()
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_user_id"))

            val presence = ChatEventBroadcaster.getUserPresence(userId)
            call.respond(UserPresenceResponse(
                userId = userId.toString(),
                isOnline = presence.isOnline,
                lastSeenAt = presence.lastSeenAt
            ))
        }

        // Получить онлайн-статус нескольких пользователей
        get("/api/users/presence") {
            val userIds = call.request.queryParameters["ids"]
                ?.split(",")
                ?.mapNotNull { it.trim().toUuidOrNull() }
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "ids_required"))

            val presences = userIds.map { userId ->
                val presence = ChatEventBroadcaster.getUserPresence(userId)
                UserPresenceResponse(
                    userId = userId.toString(),
                    isOnline = presence.isOnline,
                    lastSeenAt = presence.lastSeenAt
                )
            }
            call.respond(presences)
        }

        // Получить список онлайн пользователей
        get("/api/users/online") {
            val onlineIds = ChatEventBroadcaster.getOnlineUserIds()
            call.respond(mapOf("onlineUserIds" to onlineIds.map { it.toString() }))
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
