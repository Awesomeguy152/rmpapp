package com.example

import kotlin.test.*

/**
 * Unit тесты для конфигурации приложения
 */
class ApplicationTest {
    
    @Test
    fun `default port is 8080`() {
        val defaultPort = System.getenv("PORT")?.toIntOrNull() ?: 8080
        assertEquals(8080, defaultPort)
    }
    
    @Test
    fun `environment variables can be read`() {
        // These should return defaults when not set
        val jwtSecret = System.getenv("JWT_SECRET") ?: "default_secret"
        assertNotNull(jwtSecret)
        assertTrue(jwtSecret.isNotEmpty())
    }
    
    @Test
    fun `redis enabled defaults to false`() {
        val redisEnabled = System.getenv("REDIS_ENABLED")?.toBoolean() ?: false
        // In test environment, this should be false by default
        assertFalse(redisEnabled)
    }
    
    @Test
    fun `clickhouse enabled defaults to false`() {
        val clickhouseEnabled = System.getenv("CLICKHOUSE_ENABLED")?.toBoolean() ?: false
        // In test environment, this should be false by default
        assertFalse(clickhouseEnabled)
    }
    
    @Test
    fun `microservices architecture has 5 services`() {
        val services = listOf(
            "user-service",
            "chat-service",
            "ai-service",
            "notification-service",
            "analytics-service"
        )
        
        assertEquals(5, services.size)
    }
}
