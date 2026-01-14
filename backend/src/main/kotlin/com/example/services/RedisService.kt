package com.example.services

import io.ktor.server.application.*
import io.lettuce.core.RedisClient
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Redis/KeyDB сервис — брокер сообщений для микросервисной архитектуры.
 * 
 * Функции:
 * - Pub/Sub для межсервисного взаимодействия
 * - Кэширование данных
 * - Очереди задач
 * - Сессии пользователей
 */
class RedisService(private val app: Application) {
    
    private val redisUrl: String = System.getenv("REDIS_URL") 
        ?: System.getenv("KEYDB_URL")
        ?: "redis://localhost:6379"
    
    private val enabled: Boolean = System.getenv("REDIS_ENABLED")?.toBoolean() ?: false
    
    private var client: RedisClient? = null
    private var connection: StatefulRedisConnection<String, String>? = null
    private var pubSubConnection: StatefulRedisPubSubConnection<String, String>? = null
    
    // Подписчики на каналы
    private val subscribers = ConcurrentHashMap<String, MutableList<(String, String) -> Unit>>()
    
    // Микросервисные каналы
    object Channels {
        const val USER_EVENTS = "microservice:users"
        const val CHAT_EVENTS = "microservice:chats"
        const val NOTIFICATION_EVENTS = "microservice:notifications"
        const val AI_EVENTS = "microservice:ai"
        const val ANALYTICS_EVENTS = "microservice:analytics"
    }
    
    init {
        if (enabled) {
            try {
                client = RedisClient.create(redisUrl)
                connection = client?.connect()
                pubSubConnection = client?.connectPubSub()
                
                setupPubSubListener()
                
                app.log.info("🔴 Redis/KeyDB connected: ${redisUrl.substringBefore("@").substringAfter("://")}")
            } catch (e: Exception) {
                app.log.error("❌ Redis connection failed: ${e.message}")
            }
        } else {
            app.log.info("🔴 Redis/KeyDB disabled (set REDIS_ENABLED=true to enable)")
        }
    }
    
    private fun setupPubSubListener() {
        pubSubConnection?.addListener(object : RedisPubSubAdapter<String, String>() {
            override fun message(channel: String, message: String) {
                subscribers[channel]?.forEach { callback ->
                    try {
                        callback(channel, message)
                    } catch (e: Exception) {
                        app.log.error("Redis subscriber error on $channel: ${e.message}")
                    }
                }
            }
        })
    }
    
    // ============ Pub/Sub для микросервисов ============
    
    /**
     * Публикация события в канал
     */
    fun publish(channel: String, message: String): Boolean {
        if (!enabled || connection == null) return false
        return try {
            connection?.sync()?.publish(channel, message)
            app.log.debug("📤 Published to $channel: $message")
            true
        } catch (e: Exception) {
            app.log.error("Redis publish error: ${e.message}")
            false
        }
    }
    
    /**
     * Подписка на канал
     */
    fun subscribe(channel: String, callback: (String, String) -> Unit) {
        if (!enabled || pubSubConnection == null) return
        
        subscribers.getOrPut(channel) { mutableListOf() }.add(callback)
        pubSubConnection?.sync()?.subscribe(channel)
        app.log.info("📥 Subscribed to channel: $channel")
    }
    
    // ============ Кэширование ============
    
    /**
     * Сохранить значение с TTL
     */
    fun set(key: String, value: String, ttlSeconds: Long? = null): Boolean {
        if (!enabled || connection == null) return false
        return try {
            if (ttlSeconds != null) {
                connection?.sync()?.setex(key, ttlSeconds, value)
            } else {
                connection?.sync()?.set(key, value)
            }
            true
        } catch (e: Exception) {
            app.log.error("Redis SET error: ${e.message}")
            false
        }
    }
    
    /**
     * Получить значение
     */
    fun get(key: String): String? {
        if (!enabled || connection == null) return null
        return try {
            connection?.sync()?.get(key)
        } catch (e: Exception) {
            app.log.error("Redis GET error: ${e.message}")
            null
        }
    }
    
    /**
     * Удалить ключ
     */
    fun delete(key: String): Boolean {
        if (!enabled || connection == null) return false
        return try {
            connection?.sync()?.del(key)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // ============ Сессии пользователей ============
    
    /**
     * Сохранить сессию пользователя
     */
    fun setUserSession(userId: String, sessionData: String, ttlSeconds: Long = 86400) {
        set("session:$userId", sessionData, ttlSeconds)
    }
    
    /**
     * Получить сессию пользователя
     */
    fun getUserSession(userId: String): String? {
        return get("session:$userId")
    }
    
    /**
     * Инвалидировать сессию
     */
    fun invalidateSession(userId: String) {
        delete("session:$userId")
    }
    
    // ============ Очереди задач ============
    
    /**
     * Добавить задачу в очередь
     */
    fun pushToQueue(queueName: String, task: String): Boolean {
        if (!enabled || connection == null) return false
        return try {
            connection?.sync()?.lpush("queue:$queueName", task)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Получить задачу из очереди
     */
    fun popFromQueue(queueName: String): String? {
        if (!enabled || connection == null) return null
        return try {
            connection?.sync()?.rpop("queue:$queueName")
        } catch (e: Exception) {
            null
        }
    }
    
    // ============ Микросервисные события ============
    
    /**
     * Отправить событие о новом пользователе
     */
    fun publishUserEvent(eventType: String, userId: String, data: String = "{}") {
        publish(Channels.USER_EVENTS, """{"type":"$eventType","userId":"$userId","data":$data,"timestamp":${System.currentTimeMillis()}}""")
    }
    
    /**
     * Отправить событие о чате
     */
    fun publishChatEvent(eventType: String, chatId: String, userId: String, data: String = "{}") {
        publish(Channels.CHAT_EVENTS, """{"type":"$eventType","chatId":"$chatId","userId":"$userId","data":$data,"timestamp":${System.currentTimeMillis()}}""")
    }
    
    /**
     * Отправить событие для AI сервиса
     */
    fun publishAiEvent(eventType: String, userId: String, prompt: String) {
        publish(Channels.AI_EVENTS, """{"type":"$eventType","userId":"$userId","prompt":"$prompt","timestamp":${System.currentTimeMillis()}}""")
    }
    
    /**
     * Отправить событие аналитики
     */
    fun publishAnalyticsEvent(eventType: String, data: String) {
        publish(Channels.ANALYTICS_EVENTS, """{"type":"$eventType","data":$data,"timestamp":${System.currentTimeMillis()}}""")
    }
    
    // ============ Статус и метрики ============
    
    fun isConnected(): Boolean = enabled && connection?.isOpen == true
    
    fun getStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to enabled,
            "connected" to isConnected(),
            "url" to redisUrl.substringBefore("@").substringAfter("://"),
            "channels" to listOf(
                Channels.USER_EVENTS,
                Channels.CHAT_EVENTS,
                Channels.NOTIFICATION_EVENTS,
                Channels.AI_EVENTS,
                Channels.ANALYTICS_EVENTS
            )
        )
    }
    
    fun close() {
        pubSubConnection?.close()
        connection?.close()
        client?.shutdown()
    }
}
