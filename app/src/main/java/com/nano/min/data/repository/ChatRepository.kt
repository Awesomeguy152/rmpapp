package com.nano.min.data.repository

import com.nano.min.data.local.*
import com.nano.min.network.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Repository для работы с чатами.
 * Синхронизирует данные между сервером и локальной базой Room.
 * Обеспечивает офлайн-режим.
 */
class ChatRepository(
    private val chatService: ChatService,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: PendingMessageDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ==================== CONVERSATIONS ====================

    /**
     * Получает список чатов. Сначала из кеша, потом обновляет с сервера.
     */
    fun getConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getActiveConversations()
    }

    /**
     * Получает архивированные чаты
     */
    fun getArchivedConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getArchivedConversations()
    }

    /**
     * Загружает чаты с сервера и сохраняет в Room
     */
    suspend fun refreshConversations(): Result<List<ConversationSummaryDto>> = withContext(Dispatchers.IO) {
        try {
            val conversations = chatService.getConversations()
            
            // Сохраняем в локальную базу
            val entities = conversations.map { it.toEntity() }
            conversationDao.insertConversations(entities)
            
            Result.success(conversations)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Архивирует чат
     */
    suspend fun archiveConversation(conversationId: String, archive: Boolean) {
        conversationDao.setArchived(conversationId, archive)
    }

    /**
     * Мутит уведомления чата
     */
    suspend fun muteConversation(conversationId: String, mute: Boolean) {
        conversationDao.setMuted(conversationId, mute)
    }

    /**
     * Закрепляет чат
     */
    suspend fun pinConversation(conversationId: String, pin: Boolean) {
        val pinnedAt = if (pin) java.time.Instant.now().toString() else null
        conversationDao.setPinned(conversationId, pinnedAt)
    }

    /**
     * Удаляет чат локально
     */
    suspend fun deleteConversationLocal(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
        messageDao.deleteMessagesByConversation(conversationId)
    }

    // ==================== MESSAGES ====================

    /**
     * Получает сообщения чата из локальной базы
     */
    fun getMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getMessagesByConversation(conversationId)
    }

    /**
     * Получает закреплённые сообщения
     */
    fun getPinnedMessages(conversationId: String): Flow<List<MessageEntity>> {
        return messageDao.getPinnedMessages(conversationId)
    }

    /**
     * Загружает сообщения с сервера и сохраняет в Room
     */
    suspend fun refreshMessages(conversationId: String, limit: Int = 100): Result<List<MessageDto>> = 
        withContext(Dispatchers.IO) {
            try {
                val messages = chatService.getMessages(conversationId, limit)
                
                // Сохраняем в локальную базу
                val entities = messages.map { it.toEntity() }
                messageDao.insertMessages(entities)
                
                Result.success(messages)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Отправляет сообщение. Если офлайн - сохраняет в очередь.
     */
    suspend fun sendMessage(
        conversationId: String,
        body: String,
        attachments: List<MessageAttachmentPayload> = emptyList(),
        replyToMessageId: String? = null,
        forwardedFromMessageId: String? = null
    ): Result<MessageDto> = withContext(Dispatchers.IO) {
        try {
            val message = chatService.sendMessage(
                conversationId, body, attachments, replyToMessageId, forwardedFromMessageId
            )
            
            // Сохраняем в локальную базу
            messageDao.insertMessage(message.toEntity())
            
            Result.success(message)
        } catch (e: Exception) {
            // Офлайн - сохраняем в очередь
            val pending = PendingMessageEntity(
                conversationId = conversationId,
                body = body,
                attachmentsJson = json.encodeToString(attachments),
                replyToMessageId = replyToMessageId,
                forwardedFromMessageId = forwardedFromMessageId,
                createdAt = java.time.Instant.now().toString()
            )
            pendingMessageDao.insertPending(pending)
            
            Result.failure(e)
        }
    }

    /**
     * Закрепляет/открепляет сообщение
     */
    suspend fun pinMessage(messageId: String, pin: Boolean) {
        messageDao.setPinned(messageId, pin)
    }

    /**
     * Синхронизирует ожидающие сообщения (при восстановлении сети)
     */
    suspend fun syncPendingMessages(): Int = withContext(Dispatchers.IO) {
        val pending = pendingMessageDao.getAllPending()
        var successCount = 0
        
        for (msg in pending) {
            try {
                val attachments: List<MessageAttachmentPayload> = 
                    json.decodeFromString(msg.attachmentsJson)
                
                chatService.sendMessage(
                    msg.conversationId,
                    msg.body,
                    attachments,
                    msg.replyToMessageId,
                    msg.forwardedFromMessageId
                )
                
                pendingMessageDao.deletePendingById(msg.localId)
                successCount++
            } catch (e: Exception) {
                pendingMessageDao.incrementRetryCount(msg.localId)
            }
        }
        
        successCount
    }

    // ==================== MAPPERS ====================

    private fun ConversationSummaryDto.toEntity(): ConversationEntity {
        return ConversationEntity(
            id = id,
            type = type.name,
            topic = topic,
            createdBy = createdBy,
            createdAt = createdAt,
            membersJson = json.encodeToString(members),
            lastMessageId = lastMessage?.id,
            unreadCount = unreadCount
        )
    }

    private fun MessageDto.toEntity(): MessageEntity {
        return MessageEntity(
            id = id,
            conversationId = conversationId,
            senderId = senderId,
            body = body,
            tag = tag.name,
            createdAt = createdAt,
            editedAt = editedAt,
            deletedAt = deletedAt,
            status = status.name,
            attachmentsJson = json.encodeToString(attachments),
            replyToJson = replyTo?.let { json.encodeToString(it) },
            forwardedFromJson = forwardedFrom?.let { json.encodeToString(it) },
            reactionsJson = json.encodeToString(reactions),
            readByJson = json.encodeToString(readBy)
        )
    }
}
