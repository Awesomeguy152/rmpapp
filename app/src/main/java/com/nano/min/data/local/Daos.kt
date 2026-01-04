package com.nano.min.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO для работы с сообщениями
 */
@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC")
    fun getMessagesByConversation(conversationId: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentMessages(conversationId: String, limit: Int = 50): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE isPinned = 1 AND conversationId = :conversationId")
    fun getPinnedMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET isPinned = :isPinned WHERE id = :messageId")
    suspend fun setPinned(messageId: String, isPinned: Boolean)

    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: String)

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesByConversation(conversationId: String)

    @Query("SELECT * FROM messages WHERE isSynced = 0")
    suspend fun getUnsyncedMessages(): List<MessageEntity>
}

/**
 * DAO для работы с чатами
 */
@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE isArchived = 0 ORDER BY CASE WHEN pinnedAt IS NOT NULL THEN 0 ELSE 1 END, pinnedAt DESC, createdAt DESC")
    fun getActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE isArchived = 1 ORDER BY createdAt DESC")
    fun getArchivedConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations ORDER BY createdAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :conversationId")
    suspend fun getConversationById(conversationId: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversations(conversations: List<ConversationEntity>)

    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    @Query("UPDATE conversations SET isArchived = :isArchived WHERE id = :conversationId")
    suspend fun setArchived(conversationId: String, isArchived: Boolean)

    @Query("UPDATE conversations SET isMuted = :isMuted WHERE id = :conversationId")
    suspend fun setMuted(conversationId: String, isMuted: Boolean)

    @Query("UPDATE conversations SET pinnedAt = :pinnedAt WHERE id = :conversationId")
    suspend fun setPinned(conversationId: String, pinnedAt: String?)

    @Query("UPDATE conversations SET unreadCount = :count WHERE id = :conversationId")
    suspend fun updateUnreadCount(conversationId: String, count: Long)

    @Query("DELETE FROM conversations WHERE id = :conversationId")
    suspend fun deleteConversation(conversationId: String)
}

/**
 * DAO для работы с пользователями
 */
@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE isOnline = 1")
    fun getOnlineUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<UserEntity>)

    @Query("UPDATE users SET isOnline = :isOnline, lastOnlineAt = :lastOnlineAt WHERE id = :userId")
    suspend fun updateOnlineStatus(userId: String, isOnline: Boolean, lastOnlineAt: String?)

    @Update
    suspend fun updateUser(user: UserEntity)
}

/**
 * DAO для ожидающих сообщений (офлайн очередь)
 */
@Dao
interface PendingMessageDao {
    @Query("SELECT * FROM pending_messages ORDER BY createdAt ASC")
    suspend fun getAllPending(): List<PendingMessageEntity>

    @Insert
    suspend fun insertPending(message: PendingMessageEntity): Long

    @Delete
    suspend fun deletePending(message: PendingMessageEntity)

    @Query("DELETE FROM pending_messages WHERE localId = :localId")
    suspend fun deletePendingById(localId: Long)

    @Query("UPDATE pending_messages SET retryCount = retryCount + 1 WHERE localId = :localId")
    suspend fun incrementRetryCount(localId: Long)
}

/**
 * DAO для кеша аватаров
 */
@Dao
interface AvatarCacheDao {
    @Query("SELECT * FROM avatar_cache WHERE url = :url")
    suspend fun getCachedAvatar(url: String): AvatarCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun cacheAvatar(avatar: AvatarCacheEntity)

    @Query("DELETE FROM avatar_cache WHERE cachedAt < :threshold")
    suspend fun clearOldCache(threshold: Long)
}
