package com.example.routes

import com.example.services.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Instant
import java.util.UUID

fun Route.meetingRoutes() {
    val meetingService = MeetingService()
    val chatService = ChatService()
    val aiService = AiAssistantService(chatService)

    authenticate("auth-jwt") {
        route("/api/meetings") {
            
            // Получить все встречи пользователя
            get {
                val userId = call.userId() ?: return@get call.respondUnauthorized()
                val meetings = meetingService.getMeetingsForUser(userId)
                call.respond(HttpStatusCode.OK, meetings)
            }
            
            // Получить встречи для конкретного чата
            get("/conversation/{conversationId}") {
                val userId = call.userId() ?: return@get call.respondUnauthorized()
                val conversationId = call.parameters["conversationId"]?.toUuidOrNull()
                    ?: return@get call.respondError("invalid_conversation_id")
                
                try {
                    chatService.assertMembership(conversationId, userId)
                } catch (e: Exception) {
                    return@get call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not_a_member"))
                }
                
                val meetings = meetingService.getMeetingsForConversation(conversationId)
                call.respond(HttpStatusCode.OK, meetings)
            }
            
            // Создать встречу вручную
            post {
                val userId = call.userId() ?: return@post call.respondUnauthorized()
                val rq = call.receive<CreateMeetingRq>()
                
                val conversationId = rq.conversationId.toUuidOrNull()
                    ?: return@post call.respondError("invalid_conversation_id")
                
                try {
                    chatService.assertMembership(conversationId, userId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not_a_member"))
                }
                
                val scheduledAt = try {
                    Instant.parse(rq.scheduledAt)
                } catch (e: Exception) {
                    return@post call.respondError("invalid_date_format")
                }
                
                val meeting = meetingService.createMeeting(
                    conversationId = conversationId,
                    creatorId = userId,
                    title = rq.title,
                    description = rq.description,
                    scheduledAt = scheduledAt,
                    location = rq.location,
                    aiGenerated = false
                )
                
                call.respond(HttpStatusCode.Created, meeting)
            }
            
            // Создать персональную встречу (без привязки к чату)
            post("/personal") {
                val userId = call.userId() ?: return@post call.respondUnauthorized()
                val rq = call.receive<CreatePersonalMeetingRq>()
                
                val scheduledAt = try {
                    Instant.parse(rq.scheduledAt)
                } catch (e: Exception) {
                    return@post call.respondError("invalid_date_format")
                }
                
                val meeting = meetingService.createPersonalMeeting(
                    creatorId = userId,
                    title = rq.title,
                    description = rq.description,
                    scheduledAt = scheduledAt,
                    location = rq.location
                )
                
                call.respond(HttpStatusCode.Created, meeting)
            }
            
            // AI: Извлечь встречи из сообщений
            post("/extract/{conversationId}") {
                val userId = call.userId() ?: return@post call.respondUnauthorized()
                val conversationId = call.parameters["conversationId"]?.toUuidOrNull()
                    ?: return@post call.respondError("invalid_conversation_id")
                
                try {
                    chatService.assertMembership(conversationId, userId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not_a_member"))
                }
                
                val extractedMeetings = aiService.extractMeetings(conversationId, userId)
                call.respond(HttpStatusCode.OK, AiMeetingExtractionResponse(
                    conversationId = conversationId.toString(),
                    meetings = extractedMeetings,
                    extractedAt = Instant.now().toString()
                ))
            }
            
            // AI: Создать встречу из предложения AI
            post("/create-from-ai") {
                val userId = call.userId() ?: return@post call.respondUnauthorized()
                val rq = call.receive<CreateFromAiRq>()
                
                val conversationId = rq.conversationId.toUuidOrNull()
                    ?: return@post call.respondError("invalid_conversation_id")
                
                try {
                    chatService.assertMembership(conversationId, userId)
                } catch (e: Exception) {
                    return@post call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not_a_member"))
                }
                
                val scheduledAt = try {
                    Instant.parse(rq.dateTime)
                } catch (e: Exception) {
                    Instant.now().plusSeconds(86400) // Завтра, если дата не распознана
                }
                
                val meeting = meetingService.createMeeting(
                    conversationId = conversationId,
                    creatorId = userId,
                    title = rq.title,
                    description = rq.description,
                    scheduledAt = scheduledAt,
                    location = rq.location,
                    aiGenerated = true,
                    sourceMessageId = rq.sourceMessageId?.toUuidOrNull()
                )
                
                call.respond(HttpStatusCode.Created, meeting)
            }
            
            // Ответить на приглашение (принять/отклонить)
            post("/{meetingId}/respond") {
                val userId = call.userId() ?: return@post call.respondUnauthorized()
                val meetingId = call.parameters["meetingId"]?.toUuidOrNull()
                    ?: return@post call.respondError("invalid_meeting_id")
                
                val rq = call.receive<MeetingResponseRq>()
                val success = meetingService.respondToMeeting(meetingId, userId, rq.accept)
                
                if (success) {
                    call.respond(HttpStatusCode.OK, mapOf("status" to if (rq.accept) "accepted" else "declined"))
                } else {
                    call.respond(HttpStatusCode.NotFound, mapOf("error" to "meeting_not_found"))
                }
            }
            
            // Получить участников встречи
            get("/{meetingId}/participants") {
                val userId = call.userId() ?: return@get call.respondUnauthorized()
                val meetingId = call.parameters["meetingId"]?.toUuidOrNull()
                    ?: return@get call.respondError("invalid_meeting_id")
                
                val participants = meetingService.getParticipants(meetingId)
                call.respond(HttpStatusCode.OK, participants)
            }
            
            // Удалить встречу
            delete("/{meetingId}") {
                val userId = call.userId() ?: return@delete call.respondUnauthorized()
                val meetingId = call.parameters["meetingId"]?.toUuidOrNull()
                    ?: return@delete call.respondError("invalid_meeting_id")
                
                val meeting = meetingService.getMeetingById(meetingId)
                if (meeting == null) {
                    return@delete call.respond(HttpStatusCode.NotFound, mapOf("error" to "meeting_not_found"))
                }
                
                if (meeting.creatorId != userId.toString()) {
                    return@delete call.respond(HttpStatusCode.Forbidden, mapOf("error" to "not_creator"))
                }
                
                meetingService.deleteMeeting(meetingId)
                call.respond(HttpStatusCode.OK, mapOf("status" to "deleted"))
            }
        }
    }
}

private fun ApplicationCall.userId(): UUID? {
    val principal = principal<JWTPrincipal>()
    return principal?.subject?.let { 
        runCatching { UUID.fromString(it) }.getOrNull() 
    }
}

private suspend fun ApplicationCall.respondUnauthorized() {
    respond(HttpStatusCode.Unauthorized)
}

private suspend fun ApplicationCall.respondError(code: String) {
    respond(HttpStatusCode.BadRequest, mapOf("error" to code))
}

private fun String.toUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()

@kotlinx.serialization.Serializable
data class AiMeetingExtractionResponse(
    val conversationId: String,
    val meetings: List<ExtractedMeetingDto>,
    val extractedAt: String
)

@kotlinx.serialization.Serializable
data class CreateFromAiRq(
    val conversationId: String,
    val title: String,
    val description: String?,
    val dateTime: String?,
    val location: String?,
    val sourceMessageId: String?
)

@kotlinx.serialization.Serializable
data class CreatePersonalMeetingRq(
    val title: String,
    val description: String? = null,
    val scheduledAt: String,
    val location: String? = null
)
