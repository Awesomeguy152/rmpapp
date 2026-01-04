package com.example.schema

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

enum class ConversationType { DIRECT, GROUP }

object Conversations : UUIDTable("conversations") {
    val type = enumerationByName("type", 16, ConversationType::class)
    val topic = varchar("topic", 255).nullable()
    val directKey = varchar("direct_key", 73).nullable().uniqueIndex()
    val createdBy = reference("created_by", UserTable)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

object ConversationMembers : UUIDTable("conversation_members") {
    val conversation = reference("conversation_id", Conversations)
    val user = reference("user_id", UserTable)
    val joinedAt = timestamp("joined_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, conversation, user)
    }
}

object ConversationPins : UUIDTable("conversation_pins") {
    val conversation = reference("conversation_id", Conversations)
    val user = reference("user_id", UserTable)
    val pinnedAt = timestamp("pinned_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, conversation, user)
    }
}

object ConversationArchives : UUIDTable("conversation_archives") {
    val conversation = reference("conversation_id", Conversations)
    val user = reference("user_id", UserTable)
    val archivedAt = timestamp("archived_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, conversation, user)
    }
}

object ConversationMutes : UUIDTable("conversation_mutes") {
    val conversation = reference("conversation_id", Conversations)
    val user = reference("user_id", UserTable)
    val mutedUntil = timestamp("muted_until").nullable() // null = forever
    val mutedAt = timestamp("muted_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, conversation, user)
    }
}

object PinnedMessages : UUIDTable("pinned_messages") {
    val conversation = reference("conversation_id", Conversations)
    val message = reference("message_id", Messages)
    val pinnedBy = reference("pinned_by", UserTable)
    val pinnedAt = timestamp("pinned_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, conversation, message)
    }
}

enum class MessageTag { NONE, ANSWER, MEETING, IMPORTANT }
enum class MessageStatus { SENT, DELIVERED, READ }

object Messages : UUIDTable("messages") {
    val conversation = reference("conversation_id", Conversations)
    val sender = reference("sender_id", UserTable)
    val body = text("body")
    val tag = enumerationByName("tag", 16, MessageTag::class).default(MessageTag.NONE)
    val replyTo = reference("reply_to_id", Messages).nullable()
    val forwardedFrom = reference("forwarded_from_id", Messages).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val editedAt = timestamp("edited_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
}

object MessageReactions : UUIDTable("message_reactions") {
    val message = reference("message_id", Messages)
    val user = reference("user_id", UserTable)
    val emoji = varchar("emoji", 16)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, message, user)
    }
}

object MessageAttachments : UUIDTable("message_attachments") {
    val message = reference("message_id", Messages)
    val fileName = varchar("file_name", 255)
    val contentType = varchar("content_type", 128)
    val dataBase64 = text("data_base64")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

object ConversationReadMarkers : UUIDTable("conversation_read_markers") {
    val conversation = reference("conversation_id", Conversations)
    val user = reference("user_id", UserTable)
    val lastReadMessage = reference("last_read_message_id", Messages).nullable()
    val lastReadAt = timestamp("last_read_at").defaultExpression(CurrentTimestamp())

    init {
        index(true, conversation, user)
    }
}

@Serializable
data class ConversationDTO(
    val id: String,
    val type: ConversationType,
    val topic: String?,
    val directKey: String?,
    val createdBy: String,
    val createdAt: String
)

@Serializable
data class MessageDTO(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val tag: MessageTag,
    val createdAt: String,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val attachments: List<MessageAttachmentDTO> = emptyList(),
    val status: MessageStatus = MessageStatus.DELIVERED,
    val readBy: List<String> = emptyList(),
    val reactions: List<MessageReactionDTO> = emptyList(),
    val replyTo: ReplyMessageDTO? = null
)

@Serializable
data class ReplyMessageDTO(
    val id: String,
    val senderId: String,
    val senderName: String? = null,
    val body: String
)

@Serializable
data class MessageReactionDTO(
    val emoji: String,
    val count: Long,
    val reactedByMe: Boolean
)

@Serializable
data class MessageAttachmentDTO(
    val id: String,
    val fileName: String,
    val contentType: String,
    val dataBase64: String
)

@Serializable
data class ConversationMemberDTO(
    val userId: String,
    val email: String,
    val joinedAt: String,
    val username: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class ConversationSummaryDTO(
    val id: String,
    val type: ConversationType,
    val topic: String?,
    val createdBy: String,
    val createdAt: String,
    val pinnedAt: String? = null,
    val archivedAt: String? = null,
    val isMuted: Boolean = false,
    val mutedUntil: String? = null,
    val members: List<ConversationMemberDTO>,
    val lastMessage: MessageDTO?,
    val unreadCount: Long,
    val pinnedMessages: List<PinnedMessageDTO> = emptyList()
)

@Serializable
data class PinnedMessageDTO(
    val id: String,
    val messageId: String,
    val messageBody: String,
    val senderName: String,
    val pinnedBy: String,
    val pinnedAt: String
)