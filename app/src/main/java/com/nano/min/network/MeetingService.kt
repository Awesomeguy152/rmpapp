package com.nano.min.network

import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class MeetingService(private val apiClient: ApiClient) {
    private val httpClient get() = apiClient.httpClient
    private val baseUrl get() = apiClient.baseUrl

    /**
     * Извлечь предложения встреч из сообщений чата с помощью AI
     */
    suspend fun extractMeetings(conversationId: String): List<ExtractedMeetingDto> =
        httpClient.post("$baseUrl/api/meetings/extract") {
            setBody(mapOf("conversationId" to conversationId))
        }.body()

    /**
     * Получить все встречи текущего пользователя
     */
    suspend fun getMeetings(): List<MeetingDto> =
        httpClient.get("$baseUrl/api/meetings").body()

    /**
     * Получить встречу по ID
     */
    suspend fun getMeeting(meetingId: String): MeetingDto =
        httpClient.get("$baseUrl/api/meetings/$meetingId").body()

    /**
     * Получить встречи для конкретного чата
     */
    suspend fun getMeetingsForConversation(conversationId: String): List<MeetingDto> =
        httpClient.get("$baseUrl/api/meetings/conversation/$conversationId").body()

    /**
     * Создать встречу
     */
    suspend fun createMeeting(request: CreateMeetingRequest): MeetingDto =
        httpClient.post("$baseUrl/api/meetings") {
            setBody(request)
        }.body()

    /**
     * Создать персональную встречу (без привязки к чату)
     */
    suspend fun createPersonalMeeting(request: CreatePersonalMeetingRequest): MeetingDto =
        httpClient.post("$baseUrl/api/meetings/personal") {
            setBody(request)
        }.body()

    /**
     * Принять или отклонить приглашение на встречу
     */
    suspend fun respondToMeeting(meetingId: String, accept: Boolean): Boolean {
        httpClient.post("$baseUrl/api/meetings/$meetingId/respond") {
            setBody(RespondMeetingRequest(accept))
        }
        return true
    }

    /**
     * Обновить статус встречи (confirmed, cancelled)
     */
    suspend fun updateMeetingStatus(meetingId: String, status: String): Boolean {
        httpClient.put("$baseUrl/api/meetings/$meetingId/status") {
            setBody(UpdateMeetingStatusRequest(status))
        }
        return true
    }

    /**
     * Удалить встречу
     */
    suspend fun deleteMeeting(meetingId: String): Boolean {
        httpClient.delete("$baseUrl/api/meetings/$meetingId")
        return true
    }
}
