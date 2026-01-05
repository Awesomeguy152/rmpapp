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
    val createdAt: String? = null,
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class UpdateProfileRq(
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ErrorResponse(val error: String? = null)

@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val role: String,
    val createdAt: String,
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ConversationMemberDto(
    @SerialName("userId") val userId: String,
    @SerialName("email") val email: String? = null,
    @SerialName("joinedAt") val joinedAt: String,
    @SerialName("username") val username: String? = null,
    @SerialName("displayName") val displayName: String? = null,
    @SerialName("avatarUrl") val avatarUrl: String? = null
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
    val pinnedAt: String? = null,
    val archivedAt: String? = null,
    val isMuted: Boolean = false,
    val mutedUntil: String? = null,
    val members: List<ConversationMemberDto> = emptyList(),
    val lastMessage: MessageDto? = null,
    val unreadCount: Long = 0,
    val pinnedMessages: List<PinnedMessageDto> = emptyList()
)

@Serializable
data class PinnedMessageDto(
    val id: String,
    val messageId: String,
    val messageBody: String,
    val senderName: String,
    val pinnedBy: String,
    val pinnedAt: String
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
data class ReplyMessageDto(
    val id: String,
    val senderId: String,
    val senderName: String,
    val body: String
)

@Serializable
data class ForwardedMessageDto(
    val originalMessageId: String,
    val originalSenderId: String,
    val originalSenderName: String,
    val originalConversationId: String
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
    val reactions: List<MessageReactionDto> = emptyList(),
    val replyTo: ReplyMessageDto? = null,
    val forwardedFrom: ForwardedMessageDto? = null
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
    val attachments: List<MessageAttachmentPayload> = emptyList(),
    val replyToMessageId: String? = null,
    val forwardedFromMessageId: String? = null
)

@Serializable
data class MessageAttachmentPayload(
    val fileName: String,
    val contentType: String,
    val dataBase64: String
)

@Serializable
data class EditMessageRequest(
    val body: String
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
    val status: String? = null,
    // Presence fields
    val userId: String? = null,
    val isOnline: Boolean? = null,
    val lastSeenAt: String? = null
)

@Serializable
data class UserPresenceDto(
    val userId: String,
    val isOnline: Boolean,
    val lastSeenAt: String
)

@Serializable
data class UpdateTopicRequest(
    val topic: String?
)

@Serializable
data class ModifyMembersRequest(
    val memberIds: List<String>
)

// ==================== Meetings ====================

@Serializable
data class MeetingDto(
    val id: String,
    val conversationId: String,
    val creatorId: String,
    val title: String,
    val description: String? = null,
    val scheduledAt: String,
    val location: String? = null,
    val status: String,
    val aiGenerated: Boolean = false,
    val createdAt: String,
    val updatedAt: String,
    val participants: List<MeetingParticipantDto> = emptyList()
)

@Serializable
data class MeetingParticipantDto(
    val id: String,
    val meetingId: String,
    val userId: String,
    val userName: String? = null,
    val userAvatar: String? = null,
    val status: String,
    val respondedAt: String? = null
)

@Serializable
data class ExtractedMeetingDto(
    val title: String,
    val description: String? = null,
    val dateTime: String? = null,
    val location: String? = null,
    val confidence: Double,
    val sourceMessageId: String? = null
)

@Serializable
data class ExtractMeetingsResponse(
    val meetings: List<ExtractedMeetingDto>
)

@Serializable
data class CreateMeetingRequest(
    val conversationId: String,
    val title: String,
    val description: String? = null,
    val scheduledAt: String,
    val location: String? = null,
    val aiGenerated: Boolean = false,
    val sourceMessageId: String? = null
)

@Serializable
data class CreatePersonalMeetingRequest(
    val title: String,
    val description: String? = null,
    val scheduledAt: String,
    val location: String? = null
)

@Serializable
data class RespondMeetingRequest(
    val accept: Boolean
)

@Serializable
data class UpdateMeetingStatusRequest(
    val status: String
)
