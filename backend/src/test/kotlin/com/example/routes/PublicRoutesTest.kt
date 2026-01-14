package com.example.routes

import kotlin.test.*

/**
 * Unit тесты для публичных API эндпоинтов (структурные тесты)
 */
class PublicRoutesTest {
    
    @Test
    fun `RedisStatusResponse has correct fields`() {
        val response = RedisStatusResponse(
            enabled = true,
            status = "connected",
            provider = "Redis/KeyDB",
            channels = listOf("channel1", "channel2")
        )
        
        assertTrue(response.enabled)
        assertEquals("connected", response.status)
        assertEquals("Redis/KeyDB", response.provider)
        assertEquals(2, response.channels.size)
    }
    
    @Test
    fun `MicroservicesStatusResponse has 5 services`() {
        val services = listOf(
            ServiceInfo("user-service", 8081, "Users", true),
            ServiceInfo("chat-service", 8082, "Chats", true),
            ServiceInfo("ai-service", 8083, "AI", true),
            ServiceInfo("notification-service", 8084, "Push", true),
            ServiceInfo("analytics-service", 8085, "Analytics", true)
        )
        
        val response = MicroservicesStatusResponse(
            architecture = "microservices",
            totalServices = 5,
            services = services,
            messageBroker = MessageBrokerInfo("Redis", true, 5)
        )
        
        assertEquals(5, response.totalServices)
        assertEquals(5, response.services.size)
        assertTrue(response.messageBroker.connected)
    }
    
    @Test
    fun `ServiceInfo ports are in valid range`() {
        val service = ServiceInfo("test-service", 8081, "Test", true)
        
        assertTrue(service.port in 8080..8090)
        assertTrue(service.active)
    }
    
    @Test
    fun `MessageBrokerInfo has correct structure`() {
        val broker = MessageBrokerInfo("Redis/KeyDB", true, 5)
        
        assertEquals("Redis/KeyDB", broker.type)
        assertTrue(broker.connected)
        assertEquals(5, broker.channels)
    }
}
