package com.example.services

import com.example.schema.ConversationSummaryDTO
import com.example.schema.MessageDTO
import com.example.schema.MessageReactionDTO
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

@Serializable
data class ChatEventPayload(
    val type: String,
    val conversationId: String? = null,
    val recipients: List<String> = emptyList(),
    val message: MessageDTO? = null,
    val conversation: ConversationSummaryDTO? = null,
    val messageId: String? = null,
    val readerId: String? = null,
    val status: String? = null,
    val reactionEmoji: String? = null,
    val reactionAction: String? = null,
    val reactions: List<MessageReactionDTO>? = null,
    // Presence fields
    val userId: String? = null,
    val isOnline: Boolean? = null,
    val lastSeenAt: String? = null
)

@Serializable
data class UserPresence(
    val isOnline: Boolean,
    val lastSeenAt: String
)

object ChatEventBroadcaster {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val sessions = mutableMapOf<UUID, MutableSet<DefaultWebSocketServerSession>>()
    
    // Хранение информации о присутствии пользователей
    private val presenceMap = mutableMapOf<UUID, UserPresence>()

    /**
     * Проверяет, онлайн ли пользователь
     */
    fun isUserOnline(userId: UUID): Boolean {
        return sessions[userId]?.isNotEmpty() == true
    }

    /**
     * Получает информацию о присутствии пользователя
     */
    fun getUserPresence(userId: UUID): UserPresence {
        return presenceMap[userId] ?: UserPresence(
            isOnline = false,
            lastSeenAt = Instant.now().toString()
        )
    }

    /**
     * Получает список онлайн пользователей
     */
    fun getOnlineUserIds(): Set<UUID> {
        return sessions.keys.filter { sessions[it]?.isNotEmpty() == true }.toSet()
    }

    suspend fun register(userId: UUID, session: DefaultWebSocketServerSession) {
        val wasOffline = mutex.withLock {
            val bucket = sessions.getOrPut(userId) { mutableSetOf() }
            val wasEmpty = bucket.isEmpty()
            bucket += session
            
            // Обновляем статус присутствия
            presenceMap[userId] = UserPresence(
                isOnline = true,
                lastSeenAt = Instant.now().toString()
            )
            
            wasEmpty
        }
        
        // Рассылаем событие о входе пользователя онлайн
        if (wasOffline) {
            broadcastPresence(userId, isOnline = true)
        }
    }

    suspend fun unregister(userId: UUID, session: DefaultWebSocketServerSession) {
        val wentOffline = mutex.withLock {
            sessions[userId]?.let { bucket ->
                bucket.remove(session)
                if (bucket.isEmpty()) {
                    sessions.remove(userId)
                    // Обновляем время последнего визита
                    presenceMap[userId] = UserPresence(
                        isOnline = false,
                        lastSeenAt = Instant.now().toString()
                    )
                    true
                } else false
            } ?: false
        }
        
        // Рассылаем событие о выходе пользователя офлайн
        if (wentOffline) {
            broadcastPresence(userId, isOnline = false)
        }
    }

    /**
     * Рассылает событие об изменении статуса присутствия
     */
    private suspend fun broadcastPresence(userId: UUID, isOnline: Boolean) {
        val event = ChatEventPayload(
            type = "presence_changed",
            userId = userId.toString(),
            isOnline = isOnline,
            lastSeenAt = Instant.now().toString()
        )
        
        // Отправляем всем подключённым пользователям
        val text = json.encodeToString(event)
        val snapshot = mutex.withLock {
            sessions.mapValues { it.value.toList() }
        }
        
        snapshot.forEach { (_, sockets) ->
            sockets.forEach { session ->
                runCatching {
                    session.send(Frame.Text(text))
                }.onFailure {
                    runCatching { session.close() }
                }
            }
        }
    }

    suspend fun broadcast(event: ChatEventPayload) {
        val text = json.encodeToString(event)
        val recipients = if (event.recipients.isEmpty()) null else event.recipients.toSet()
        val snapshot = mutex.withLock {
            sessions.mapValues { it.value.toList() }
        }

        snapshot.forEach { (userId, sockets) ->
            if (recipients == null || recipients.contains(userId.toString())) {
                sockets.forEach { session ->
                    runCatching {
                        session.send(Frame.Text(text))
                    }.onFailure {
                        runCatching { session.close() }
                    }
                }
            }
        }
    }
}
