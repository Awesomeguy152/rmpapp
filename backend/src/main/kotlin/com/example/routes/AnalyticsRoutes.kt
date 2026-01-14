package com.example.routes

import com.example.services.ClickHouseService
import com.example.services.EndpointStats
import com.example.services.RequestStats
import com.example.services.UserActivityStats
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * API эндпоинты для аналитики (ClickHouse)
 */
fun Route.analyticsRoutes(clickHouseService: ClickHouseService) {
    
    // Публичный эндпоинт для проверки статуса (без авторизации)
    get("/api/analytics/status") {
        val stats = clickHouseService.getRequestStats(1)
        call.respond(HttpStatusCode.OK, ClickHouseStatusResponse(
            enabled = stats != null,
            status = if (stats != null) "connected" else "disabled",
            provider = "ClickHouse Cloud"
        ))
    }
    
    authenticate("auth-jwt") {
        route("/api/analytics") {
            
            /**
             * GET /api/analytics/requests
             * Статистика API запросов за период
             */
            get("/requests") {
                val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24
                
                val stats = clickHouseService.getRequestStats(hours)
                if (stats != null) {
                    call.respond(HttpStatusCode.OK, RequestStatsResponse(
                        totalRequests = stats.totalRequests,
                        successCount = stats.successCount,
                        errorCount = stats.errorCount,
                        avgDurationMs = stats.avgDurationMs,
                        maxDurationMs = stats.maxDurationMs,
                        uniqueUsers = stats.uniqueUsers,
                        periodHours = hours
                    ))
                } else {
                    call.respond(HttpStatusCode.ServiceUnavailable, AnalyticsErrorResponse(
                        error = "analytics_disabled",
                        message = "ClickHouse analytics is not enabled"
                    ))
                }
            }
            
            /**
             * GET /api/analytics/endpoints
             * Топ эндпоинтов по запросам
             */
            get("/endpoints") {
                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 10
                val hours = call.request.queryParameters["hours"]?.toIntOrNull() ?: 24
                
                val endpoints = clickHouseService.getTopEndpoints(limit, hours)
                call.respond(HttpStatusCode.OK, TopEndpointsResponse(
                    endpoints = endpoints.map { 
                        EndpointStatsDto(
                            path = it.path,
                            method = it.method,
                            requestCount = it.requestCount,
                            avgDurationMs = it.avgDurationMs,
                            errorCount = it.errorCount
                        )
                    },
                    periodHours = hours
                ))
            }
            
            /**
             * GET /api/analytics/user/{userId}
             * Статистика активности пользователя
             */
            get("/user/{userId}") {
                val userId = call.parameters["userId"] 
                    ?: return@get call.respond(HttpStatusCode.BadRequest, AnalyticsErrorResponse(error = "user_id_required"))
                val days = call.request.queryParameters["days"]?.toIntOrNull() ?: 7
                
                val stats = clickHouseService.getUserActivityStats(userId, days)
                if (stats != null) {
                    call.respond(HttpStatusCode.OK, UserActivityStatsResponse(
                        userId = stats.userId,
                        totalActions = stats.totalActions,
                        uniqueActions = stats.uniqueActions,
                        firstActivity = stats.firstActivity?.toString(),
                        lastActivity = stats.lastActivity?.toString(),
                        periodDays = days
                    ))
                } else {
                    call.respond(HttpStatusCode.NotFound, AnalyticsErrorResponse(error = "no_activity_found"))
                }
            }
        }
    }
}

// ============ Response DTOs ============

@Serializable
data class RequestStatsResponse(
    val totalRequests: Long,
    val successCount: Long,
    val errorCount: Long,
    val avgDurationMs: Double,
    val maxDurationMs: Long,
    val uniqueUsers: Long,
    val periodHours: Int
)

@Serializable
data class TopEndpointsResponse(
    val endpoints: List<EndpointStatsDto>,
    val periodHours: Int
)

@Serializable
data class EndpointStatsDto(
    val path: String,
    val method: String,
    val requestCount: Long,
    val avgDurationMs: Double,
    val errorCount: Long
)

@Serializable
data class UserActivityStatsResponse(
    val userId: String,
    val totalActions: Long,
    val uniqueActions: Long,
    val firstActivity: String?,
    val lastActivity: String?,
    val periodDays: Int
)

@Serializable
data class ClickHouseStatusResponse(
    val enabled: Boolean,
    val status: String,
    val provider: String
)

@Serializable
data class AnalyticsErrorResponse(
    val error: String,
    val message: String? = null
)
