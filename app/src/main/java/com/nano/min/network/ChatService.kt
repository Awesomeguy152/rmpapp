package com.nano.min.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class ChatService(private val apiClient: ApiClient) {
    private val httpClient get() = apiClient.httpClient
    private val baseUrl get() = apiClient.baseUrl
    private val wsBaseUrl get() = baseUrl
        .replace("https://", "wss://")
        .replace("http://", "ws://")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getConversations(limit: Int = 50, offset: Int = 0): List<ConversationSummaryDto> =
        httpClient.get("$baseUrl/api/chat/conversations") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()
    
    suspend fun listConversations(limit: Int = 50, offset: Int = 0, includeArchived: Boolean = false): List<ConversationSummaryDto> =
        httpClient.get("$baseUrl/api/chat/conversations") {
            parameter("limit", limit)
            parameter("offset", offset)
            parameter("includeArchived", includeArchived)
        }.body()

    suspend fun getConversation(conversationId: String): ConversationSummaryDto =
        httpClient.get("$baseUrl/api/chat/conversations/$conversationId").body()

    suspend fun getMessages(
        conversationId: String,
        limit: Int = 100,
        offset: Int = 0,
        query: String? = null
    ): List<MessageDto> = httpClient.get("$baseUrl/api/chat/conversations/$conversationId/messages") {
        parameter("limit", limit)
        parameter("offset", offset)
        if (!query.isNullOrBlank()) {
            parameter("q", query)
        }
    }.body()

    suspend fun searchMessages(
        query: String,
        limit: Int = 50,
        offset: Int = 0
    ): List<MessageDto> = httpClient.get("$baseUrl/api/chat/messages/search") {
        parameter("q", query)
        parameter("limit", limit)
        parameter("offset", offset)
    }.body()

    suspend fun sendMessage(
        conversationId: String, 
        body: String,
        attachments: List<MessageAttachmentPayload> = emptyList(),
        replyToMessageId: String? = null,
        forwardedFromMessageId: String? = null
    ): MessageDto =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/messages") {
            setBody(SendMessageRequest(
                body = body,
                attachments = attachments,
                replyToMessageId = replyToMessageId,
                forwardedFromMessageId = forwardedFromMessageId
            ))
        }.body()

    suspend fun createDirectConversation(memberId: String, topic: String?): ConversationDto =
        httpClient.post("$baseUrl/api/chat/conversations/direct") {
            setBody(CreateDirectConversationRequest(memberId = memberId, topic = topic))
        }.body()

    suspend fun createGroupConversation(memberIds: List<String>, topic: String?): ConversationDto =
        httpClient.post("$baseUrl/api/chat/conversations/group") {
            setBody(CreateGroupConversationRequest(topic = topic, memberIds = memberIds))
        }.body()

    suspend fun markConversationRead(conversationId: String, messageId: String? = null) {
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/read") {
            setBody(MarkReadRequest(messageId = messageId))
        }
    }

    suspend fun sendTyping(conversationId: String, isTyping: Boolean) {
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/typing") {
            setBody(TypingRequest(isTyping))
        }
    }

    suspend fun editMessage(messageId: String, body: String): MessageDto =
        httpClient.patch("$baseUrl/api/chat/messages/$messageId") {
            setBody(EditMessageRequest(body = body))
        }.body()

    suspend fun deleteMessage(messageId: String): MessageDto =
        httpClient.delete("$baseUrl/api/chat/messages/$messageId").body()

    suspend fun reactToMessage(messageId: String, emoji: String): MessageDto =
        httpClient.post("$baseUrl/api/chat/messages/$messageId/reactions") {
            setBody(ReactionRq(emoji))
        }.body()

    suspend fun removeReaction(messageId: String): MessageDto =
        httpClient.delete("$baseUrl/api/chat/messages/$messageId/reactions").body()

    suspend fun updateConversationTopic(conversationId: String, topic: String?): ConversationDto =
        httpClient.patch("$baseUrl/api/chat/conversations/$conversationId/topic") {
            setBody(UpdateTopicRequest(topic = topic))
        }.body()

    suspend fun addConversationMembers(conversationId: String, memberIds: List<String>): List<ConversationMemberDto> =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/members") {
            setBody(ModifyMembersRequest(memberIds = memberIds))
        }.body()

    suspend fun removeConversationMember(conversationId: String, memberId: String): List<ConversationMemberDto> =
        httpClient.delete("$baseUrl/api/chat/conversations/$conversationId/members/$memberId").body()

    suspend fun searchUsers(query: String?, limit: Int = 20): List<UserProfileDto> =
        httpClient.get("$baseUrl/api/users") {
            parameter("limit", limit)
            if (!query.isNullOrBlank()) {
                parameter("q", query)
            }
        }.body()

    /**
     * Получает онлайн-статус пользователя
     */
    suspend fun getUserPresence(userId: String): UserPresenceDto =
        httpClient.get("$baseUrl/api/users/$userId/presence").body()

    /**
     * Получает онлайн-статус нескольких пользователей
     */
    suspend fun getUsersPresence(userIds: List<String>): List<UserPresenceDto> =
        httpClient.get("$baseUrl/api/users/presence") {
            parameter("ids", userIds.joinToString(","))
        }.body()

    /**
     * Получает список онлайн пользователей
     */
    suspend fun getOnlineUsers(): List<String> {
        val response: Map<String, List<String>> = httpClient.get("$baseUrl/api/users/online").body()
        return response["onlineUserIds"] ?: emptyList()
    }

    /**
     * Архивировать чат
     */
    suspend fun archiveConversation(conversationId: String): ConversationSummaryDto =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/archive").body()

    /**
     * Разархивировать чат
     */
    suspend fun unarchiveConversation(conversationId: String): ConversationSummaryDto =
        httpClient.delete("$baseUrl/api/chat/conversations/$conversationId/archive").body()

    /**
     * Заглушить уведомления для чата
     */
    suspend fun muteConversation(conversationId: String, until: String? = null): ConversationSummaryDto =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/mute") {
            if (until != null) {
                setBody(mapOf("mutedUntil" to until))
            }
        }.body()

    /**
     * Включить уведомления для чата
     */
    suspend fun unmuteConversation(conversationId: String): ConversationSummaryDto =
        httpClient.delete("$baseUrl/api/chat/conversations/$conversationId/mute").body()

    /**
     * Удалить чат
     */
    suspend fun deleteConversation(conversationId: String) {
        httpClient.delete("$baseUrl/api/chat/conversations/$conversationId")
    }

    /**
     * Выйти из группового чата (не удаляя его)
     */
    suspend fun leaveConversation(conversationId: String) {
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/leave")
    }

    /**
     * Закрепить чат
     */
    suspend fun pinConversation(conversationId: String): ConversationSummaryDto =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/pin").body()

    /**
     * Открепить чат
     */
    suspend fun unpinConversation(conversationId: String): ConversationSummaryDto =
        httpClient.delete("$baseUrl/api/chat/conversations/$conversationId/pin").body()

    // === Pinned Messages ===

    /**
     * Получить закреплённые сообщения
     */
    suspend fun getPinnedMessages(conversationId: String): List<PinnedMessageDto> =
        httpClient.get("$baseUrl/api/chat/conversations/$conversationId/pins").body()

    /**
     * Закрепить сообщение
     */
    suspend fun pinMessage(conversationId: String, messageId: String): PinnedMessageDto =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/messages/$messageId/pin").body()

    /**
     * Открепить сообщение
     */
    suspend fun unpinMessage(conversationId: String, messageId: String) {
        httpClient.delete("$baseUrl/api/chat/conversations/$conversationId/messages/$messageId/pin")
    }

    fun observeChatEvents(): Flow<ChatEventDto> = callbackFlow {
        while (isActive) {
            try {
                httpClient.webSocket(urlString = "$wsBaseUrl/api/chat/updates") {
                    for (frame in incoming) {
                        when (frame) {
                            is Frame.Text -> {
                                val payload = frame.readText()
                                runCatching {
                                    json.decodeFromString<ChatEventDto>(payload)
                                }.onSuccess { event ->
                                    trySend(event)
                                }
                            }

                            is Frame.Close -> {
                                break
                            }

                            else -> Unit
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                if (!isActive) break
                // краткая пауза перед реконнектом
                delay(1000)
            }
        }
        awaitClose { }
    }
}
