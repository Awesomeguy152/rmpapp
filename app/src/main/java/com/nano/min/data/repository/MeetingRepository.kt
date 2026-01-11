package com.nano.min.data.repository

import com.nano.min.network.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository для работы со встречами.
 * Взаимодействует с бэкендом для управления встречами, предложенными AI.
 */
class MeetingRepository(
    private val meetingService: MeetingService
) {

    /**
     * Извлечь предложения встреч из чата с помощью AI
     */
    suspend fun extractMeetings(conversationId: String): Result<List<ExtractedMeetingDto>> = 
        withContext(Dispatchers.IO) {
            try {
                val meetings = meetingService.extractMeetings(conversationId)
                Result.success(meetings)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Получить все встречи текущего пользователя
     */
    suspend fun getMeetings(): Result<List<MeetingDto>> = withContext(Dispatchers.IO) {
        try {
            val meetings = meetingService.getMeetings()
            Result.success(meetings)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить встречу по ID
     */
    suspend fun getMeeting(meetingId: String): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val meeting = meetingService.getMeeting(meetingId)
            Result.success(meeting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Получить встречи для конкретного чата
     */
    suspend fun getMeetingsForConversation(conversationId: String): Result<List<MeetingDto>> = 
        withContext(Dispatchers.IO) {
            try {
                val meetings = meetingService.getMeetingsForConversation(conversationId)
                Result.success(meetings)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Создать встречу
     */
    suspend fun createMeeting(
        conversationId: String,
        title: String,
        description: String?,
        scheduledAt: String,
        location: String?,
        aiGenerated: Boolean = false,
        sourceMessageId: String? = null
    ): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val request = CreateMeetingRequest(
                conversationId = conversationId,
                title = title,
                description = description,
                scheduledAt = scheduledAt,
                location = location,
                aiGenerated = aiGenerated,
                sourceMessageId = sourceMessageId
            )
            val meeting = meetingService.createMeeting(request)
            Result.success(meeting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Создать персональную встречу (без привязки к чату)
     */
    suspend fun createMeetingWithoutConversation(
        title: String,
        description: String?,
        scheduledAt: String,
        location: String?
    ): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val meeting = meetingService.createPersonalMeeting(
                CreatePersonalMeetingRequest(
                    title = title,
                    description = description,
                    scheduledAt = scheduledAt,
                    location = location
                )
            )
            Result.success(meeting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Принять или отклонить приглашение на встречу
     */
    suspend fun respondToMeeting(meetingId: String, accept: Boolean): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                meetingService.respondToMeeting(meetingId, accept)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Обновить статус встречи (confirmed, cancelled)
     */
    suspend fun updateMeetingStatus(meetingId: String, status: String): Result<Boolean> = 
        withContext(Dispatchers.IO) {
            try {
                meetingService.updateMeetingStatus(meetingId, status)
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Удалить встречу
     */
    suspend fun deleteMeeting(meetingId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            meetingService.deleteMeeting(meetingId)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Создать встречу из предложения AI
     */
    suspend fun createMeetingFromAi(
        conversationId: String,
        title: String,
        description: String?,
        dateTime: String?,
        location: String?,
        sourceMessageId: String?
    ): Result<MeetingDto> = withContext(Dispatchers.IO) {
        try {
            val request = CreateFromAiRequest(
                conversationId = conversationId,
                title = title,
                description = description,
                dateTime = dateTime,
                location = location,
                sourceMessageId = sourceMessageId
            )
            val meeting = meetingService.createMeetingFromAi(request)
            Result.success(meeting)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
