package com.nano.min.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val role: String,
    val adminSecret: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class LoginResponse(
    @SerialName("token") val token: String? = null
)

@Serializable
data class MeResponse(
    val id: String? = null,
    val email: String? = null,
    val role: String? = null,
    val createdAt: String? = null
)

@Serializable
data class ErrorResponse(val error: String? = null)

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val role: String,
    val createdAt: String
)

@Serializable
data class ConversationMemberDto(
    val userId: String,
    val joinedAt: String
)

@Serializable
enum class ConversationType { DIRECT, GROUP }

@Serializable
enum class MessageTag { NONE, ANSWER, MEETING, IMPORTANT }

@Serializable
enum class MessageStatus { SENT, DELIVERED, READ }

@Serializable
data class ConversationSummaryDto(
    val id: String,
    val type: ConversationType,
    val topic: String? = null,
    val createdBy: String,
    val createdAt: String,
    val members: List<ConversationMemberDto> = emptyList(),
    val lastMessage: MessageDto? = null,
    val unreadCount: Long = 0
)

@Serializable
data class ConversationDto(
    val id: String,
    val type: ConversationType,
    val topic: String? = null,
    val directKey: String? = null,
    val createdBy: String,
    val createdAt: String
)

@Serializable
data class MessageAttachmentDto(
    val id: String,
    val fileName: String,
    val contentType: String,
    val dataBase64: String
)

@Serializable
data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val tag: MessageTag,
    val createdAt: String,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val attachments: List<MessageAttachmentDto> = emptyList(),
    val status: MessageStatus = MessageStatus.SENT,
    val readBy: List<String> = emptyList(),
    val reactions: List<MessageReactionDto> = emptyList()
)

@Serializable
data class MessageReactionDto(
    val emoji: String,
    val count: Long,
    val reactedByMe: Boolean
)

@Serializable
data class SendMessageRequest(
    val body: String,
    val attachments: List<MessageAttachmentPayload> = emptyList()
)

@Serializable
data class MessageAttachmentPayload(
    val fileName: String,
    val contentType: String,
    val dataBase64: String
)

@Serializable
data class CreateDirectConversationRequest(
    val memberId: String,
    val topic: String? = null
)

@Serializable
data class CreateGroupConversationRequest(
    val topic: String? = null,
    val memberIds: List<String>
)

@Serializable
data class MarkReadRequest(
    val messageId: String? = null
)

@Serializable
data class ReactionRq(
    val emoji: String
)

@Serializable
data class TypingRequest(
    val isTyping: Boolean
)

@Serializable
data class ChatEventDto(
    val type: String,
    val conversationId: String? = null,
    val recipients: List<String> = emptyList(),
    val message: MessageDto? = null,
    val conversation: ConversationSummaryDto? = null,
    val messageId: String? = null,
    val reactionEmoji: String? = null,
    val reactionAction: String? = null,
    val reactions: List<MessageReactionDto>? = null,
    val readerId: String? = null,
    val status: String? = null
)

@Serializable
data class UpdateTopicRequest(
    val topic: String?
)

@Serializable
data class ModifyMembersRequest(
    val memberIds: List<String>
)
