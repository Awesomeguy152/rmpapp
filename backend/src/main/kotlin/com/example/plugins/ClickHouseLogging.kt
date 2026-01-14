package com.example.plugins

import com.example.services.ClickHouseService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*

/**
 * Плагин для автоматического логирования всех HTTP запросов в ClickHouse
 */
val ClickHouseLoggingPlugin = createApplicationPlugin(name = "ClickHouseLogging") {
    
    val startTimeKey = AttributeKey<Long>("requestStartTime")
    
    onCall { call ->
        call.attributes.put(startTimeKey, System.currentTimeMillis())
    }
    
    onCallRespond { call, _ ->
        val clickHouseService = call.application.attributes.getOrNull(clickHouseServiceKey)
            ?: return@onCallRespond
        
        val startTime = call.attributes.getOrNull(startTimeKey) ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        
        // Получаем userId из JWT если есть
        val userId = try {
            call.principal<JWTPrincipal>()?.subject
        } catch (e: Exception) {
            null
        }
        
        // Логируем запрос
        clickHouseService.logApiRequest(
            method = call.request.httpMethod.value,
            path = call.request.path(),
            statusCode = call.response.status()?.value ?: 0,
            durationMs = duration,
            userId = userId,
            ipAddress = call.request.origin.remoteHost,
            userAgent = call.request.userAgent() ?: ""
        )
        
        // Записываем метрику времени ответа
        clickHouseService.recordMetric(
            name = "http_response_time_ms",
            value = duration.toDouble(),
            tags = mapOf(
                "method" to call.request.httpMethod.value,
                "path" to call.request.path(),
                "status" to (call.response.status()?.value?.toString() ?: "unknown")
            )
        )
    }
}

val clickHouseServiceKey = AttributeKey<ClickHouseService>("ClickHouseService")

/**
 * Установка ClickHouse сервиса в атрибуты приложения
 */
fun Application.configureClickHouseLogging(clickHouseService: ClickHouseService) {
    attributes.put(clickHouseServiceKey, clickHouseService)
    install(ClickHouseLoggingPlugin)
}
