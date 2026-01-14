package com.example.routes

import com.example.services.RedisService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * API эндпоинты для Redis/KeyDB брокера сообщений
 */
fun Route.redisRoutes(redisService: RedisService) {
    
    // Публичный эндпоинт для проверки статуса Redis
    get("/api/redis/status") {
        call.respond(HttpStatusCode.OK, RedisStatusResponse(
            enabled = redisService.isConnected(),
            status = if (redisService.isConnected()) "connected" else "disabled",
            provider = "Redis/KeyDB",
            channels = listOf(
                RedisService.Channels.USER_EVENTS,
                RedisService.Channels.CHAT_EVENTS,
                RedisService.Channels.NOTIFICATION_EVENTS,
                RedisService.Channels.AI_EVENTS,
                RedisService.Channels.ANALYTICS_EVENTS
            )
        ))
    }
    
    // Эндпоинт микросервисов
    get("/api/microservices/status") {
        call.respond(HttpStatusCode.OK, MicroservicesStatusResponse(
            architecture = "microservices",
            totalServices = 5,
            services = listOf(
                ServiceInfo("user-service", 8081, "Аутентификация и пользователи", true),
                ServiceInfo("chat-service", 8082, "Чаты и сообщения", true),
                ServiceInfo("ai-service", 8083, "AI ассистент (OpenAI)", true),
                ServiceInfo("notification-service", 8084, "Push-уведомления (FCM)", true),
                ServiceInfo("analytics-service", 8085, "Аналитика (ClickHouse)", true)
            ),
            messageBroker = MessageBrokerInfo(
                type = "Redis/KeyDB",
                connected = redisService.isConnected(),
                channels = 5
            )
        ))
    }
    
    authenticate("auth-jwt") {
        route("/api/redis") {
            
            /**
             * POST /api/redis/publish
             * Публикация сообщения в канал (для тестирования)
             */
            post("/publish") {
                // Только для отладки/демонстрации
                call.respond(HttpStatusCode.OK, mapOf(
                    "message" to "Use Redis Pub/Sub through microservices"
                ))
            }
            
            /**
             * GET /api/redis/cache/{key}
             * Получить значение из кэша
             */
            get("/cache/{key}") {
                val key = call.parameters["key"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, 
                    RedisErrorResponse("key_required")
                )
                
                val value = redisService.get(key)
                if (value != null) {
                    call.respond(HttpStatusCode.OK, RedisCacheResponse(key, value))
                } else {
                    call.respond(HttpStatusCode.NotFound, RedisErrorResponse("key_not_found"))
                }
            }
        }
    }
}

// ============ Response DTOs ============

@Serializable
data class RedisStatusResponse(
    val enabled: Boolean,
    val status: String,
    val provider: String,
    val channels: List<String>
)

@Serializable
data class MicroservicesStatusResponse(
    val architecture: String,
    val totalServices: Int,
    val services: List<ServiceInfo>,
    val messageBroker: MessageBrokerInfo
)

@Serializable
data class ServiceInfo(
    val name: String,
    val port: Int,
    val description: String,
    val active: Boolean
)

@Serializable
data class MessageBrokerInfo(
    val type: String,
    val connected: Boolean,
    val channels: Int
)

@Serializable
data class RedisCacheResponse(
    val key: String,
    val value: String
)

@Serializable
data class RedisErrorResponse(
    val error: String
)
