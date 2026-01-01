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
import java.util.UUID

@Serializable
data class ChatEventPayload(
    val type: String,
    val conversationId: String,
    val recipients: List<String>,
    val message: MessageDTO? = null,
    val conversation: ConversationSummaryDTO? = null,
    val messageId: String? = null,
    val readerId: String? = null,
    val status: String? = null,
    val reactionEmoji: String? = null,
    val reactionAction: String? = null,
    val reactions: List<MessageReactionDTO>? = null
)

object ChatEventBroadcaster {
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val sessions = mutableMapOf<UUID, MutableSet<DefaultWebSocketServerSession>>()

    suspend fun register(userId: UUID, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            val bucket = sessions.getOrPut(userId) { mutableSetOf() }
            bucket += session
        }
    }

    suspend fun unregister(userId: UUID, session: DefaultWebSocketServerSession) {
        mutex.withLock {
            sessions[userId]?.let { bucket ->
                bucket.remove(session)
                if (bucket.isEmpty()) {
                    sessions.remove(userId)
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
