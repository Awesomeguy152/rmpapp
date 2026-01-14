package com.example.services

import kotlin.test.*

/**
 * Unit тесты для RedisService
 */
class RedisServiceTest {
    
    @Test
    fun `redis channels are defined correctly`() {
        assertEquals("microservice:users", RedisService.Channels.USER_EVENTS)
        assertEquals("microservice:chats", RedisService.Channels.CHAT_EVENTS)
        assertEquals("microservice:notifications", RedisService.Channels.NOTIFICATION_EVENTS)
        assertEquals("microservice:ai", RedisService.Channels.AI_EVENTS)
        assertEquals("microservice:analytics", RedisService.Channels.ANALYTICS_EVENTS)
    }
    
    @Test
    fun `all 5 microservice channels exist`() {
        val channels = listOf(
            RedisService.Channels.USER_EVENTS,
            RedisService.Channels.CHAT_EVENTS,
            RedisService.Channels.NOTIFICATION_EVENTS,
            RedisService.Channels.AI_EVENTS,
            RedisService.Channels.ANALYTICS_EVENTS
        )
        
        assertEquals(5, channels.size)
        assertTrue(channels.all { it.startsWith("microservice:") })
    }
}
