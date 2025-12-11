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

enum class MessageTag { NONE, ANSWER, MEETING, IMPORTANT }

object Messages : UUIDTable("messages") {
    val conversation = reference("conversation_id", Conversations)
    val sender = reference("sender_id", UserTable)
    val body = text("body")
    val tag = enumerationByName("tag", 16, MessageTag::class).default(MessageTag.NONE)
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val editedAt = timestamp("edited_at").nullable()
    val deletedAt = timestamp("deleted_at").nullable()
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
    val attachments: List<MessageAttachmentDTO> = emptyList()
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
    val joinedAt: String
)

@Serializable
data class ConversationSummaryDTO(
    val id: String,
    val type: ConversationType,
    val topic: String?,
    val createdBy: String,
    val createdAt: String,
    val members: List<ConversationMemberDTO>,
    val lastMessage: MessageDTO?,
    val unreadCount: Long
)