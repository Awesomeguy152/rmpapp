package com.example.routes

import com.example.services.DeviceTokenService
import com.example.services.PushNotificationService
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

@Serializable
data class TestPushRequest(
    val title: String = "Тестовое уведомление",
    val body: String = "Это тестовое push-уведомление от RMP App!"
)

@Serializable
data class PushTestResponse(
    val success: Boolean,
    val message: String,
    val tokensCount: Int
)

fun Route.deviceTokenRoutes() {
    val service = DeviceTokenService()
    val pushService = PushNotificationService()

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
        
        // 🔔 Тестирование Push уведомлений — отправка самому себе
        post("/api/push/test") {
            val principal = call.principal<JWTPrincipal>()
                ?: return@post call.respond(HttpStatusCode.Unauthorized)

            val userId = principal.subject?.toUuidOrNull()
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "invalid_subject"))

            val req = try {
                call.receive<TestPushRequest>()
            } catch (e: Exception) {
                TestPushRequest()
            }
            
            val tokens = service.getTokensForUser(userId)
            
            if (tokens.isEmpty()) {
                return@post call.respond(HttpStatusCode.OK, PushTestResponse(
                    success = false,
                    message = "Нет зарегистрированных устройств. Откройте приложение на телефоне.",
                    tokensCount = 0
                ))
            }
            
            val success = pushService.sendToUser(
                userId = userId,
                title = req.title,
                body = req.body,
                data = mapOf("type" to "test", "timestamp" to System.currentTimeMillis().toString())
            )
            
            call.respond(HttpStatusCode.OK, PushTestResponse(
                success = success,
                message = if (success) "Push отправлен на ${tokens.size} устройство(а)" else "Ошибка отправки",
                tokensCount = tokens.size
            ))
        }
    }
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
