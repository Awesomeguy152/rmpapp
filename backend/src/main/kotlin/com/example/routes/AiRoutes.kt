package com.example.routes

import com.example.services.AiAssistantService
import com.example.services.ChatService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AiConversationRq(val conversationId: String)

fun Route.aiRoutes() {
    val chatService = ChatService()
    val aiService = AiAssistantService()

    authenticate("auth-jwt") {
        route("/api/ai") {
            post("/summary") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val rq = call.receive<AiConversationRq>()
                val conversationId = rq.conversationId.toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                try {
                    chatService.assertMembership(conversationId, requesterId)
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    return@post call.respondError(HttpStatusCode.Forbidden, iae.message ?: "not_a_conversation_member")
                }

                val response = aiService.summarizeConversation(conversationId)
                call.respond(HttpStatusCode.OK, response)
            }

            post("/next-action") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val rq = call.receive<AiConversationRq>()
                val conversationId = rq.conversationId.toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                try {
                    chatService.assertMembership(conversationId, requesterId)
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    return@post call.respondError(HttpStatusCode.Forbidden, iae.message ?: "not_a_conversation_member")
                }

                val response = aiService.suggestNextAction(conversationId)
                call.respond(HttpStatusCode.OK, response)
            }
        }
    }
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: String) = respond(status, mapOf("error" to code))

private suspend fun ApplicationCall.principalOrUnauthorized(): JWTPrincipal? {
    val principal = principal<JWTPrincipal>()
    if (principal == null) {
        respond(HttpStatusCode.Unauthorized)
    }
    return principal
}

private fun JWTPrincipal.userIdOrNull(): UUID? = subject?.let {
    runCatching { UUID.fromString(it) }.getOrNull()
}

private fun String?.toUuidOrNull(): UUID? = this?.let { runCatching { UUID.fromString(it) }.getOrNull() }