package com.nano.min.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Entity для хранения сообщений офлайн
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val tag: String, // NONE, ANSWER, MEETING, IMPORTANT
    val createdAt: String,
    val editedAt: String? = null,
    val deletedAt: String? = null,
    val status: String = "SENT", // SENT, DELIVERED, READ
    val attachmentsJson: String = "[]", // JSON array of attachments
    val replyToJson: String? = null, // JSON of ReplyMessageDto
    val forwardedFromJson: String? = null, // JSON of ForwardedMessageDto
    val reactionsJson: String = "[]", // JSON array of reactions
    val readByJson: String = "[]", // JSON array of user ids
    val isSynced: Boolean = true, // false если сообщение ещё не отправлено на сервер
    val isPinned: Boolean = false
)

/**
 * Entity для хранения чатов/разговоров
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val type: String, // DIRECT, GROUP
    val topic: String? = null,
    val directKey: String? = null,
    val createdBy: String,
    val createdAt: String,
    val membersJson: String = "[]", // JSON array of ConversationMemberDto
    val lastMessageId: String? = null,
    val unreadCount: Long = 0,
    val isArchived: Boolean = false,
    val isMuted: Boolean = false,
    val pinnedAt: String? = null // null = не закреплён
)

/**
 * Entity для хранения пользователей
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val email: String,
    val role: String,
    val createdAt: String,
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null,
    val lastOnlineAt: String? = null,
    val isOnline: Boolean = false
)

/**
 * Entity для ожидающих отправки сообщений (очередь офлайн)
 */
@Entity(tableName = "pending_messages")
data class PendingMessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val conversationId: String,
    val body: String,
    val attachmentsJson: String = "[]",
    val replyToMessageId: String? = null,
    val forwardedFromMessageId: String? = null,
    val createdAt: String,
    val retryCount: Int = 0
)

/**
 * Entity для кеширования аватаров
 */
@Entity(tableName = "avatar_cache")
data class AvatarCacheEntity(
    @PrimaryKey val url: String,
    val localPath: String,
    val cachedAt: Long = System.currentTimeMillis()
)
