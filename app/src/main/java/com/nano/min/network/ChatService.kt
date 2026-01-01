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
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getConversations(limit: Int = 50, offset: Int = 0): List<ConversationSummaryDto> =
        httpClient.get("$baseUrl/api/chat/conversations") {
            parameter("limit", limit)
            parameter("offset", offset)
        }.body()

    suspend fun getConversation(conversationId: String): ConversationSummaryDto =
        httpClient.get("$baseUrl/api/chat/conversations/$conversationId").body()

    suspend fun getMessages(
        conversationId: String,
        limit: Int = 100,
        offset: Int = 0
    ): List<MessageDto> = httpClient.get("$baseUrl/api/chat/conversations/$conversationId/messages") {
        parameter("limit", limit)
        parameter("offset", offset)
    }.body()

    suspend fun sendMessage(conversationId: String, body: String): MessageDto =
        httpClient.post("$baseUrl/api/chat/conversations/$conversationId/messages") {
            setBody(SendMessageRequest(body = body))
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

    fun observeChatEvents(): Flow<ChatEventDto> = callbackFlow {
        while (isActive) {
            try {
                httpClient.webSocket(urlString = "$baseUrl/api/chat/updates") {
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
