package com.example.routes

import com.example.schema.MessageTag
import com.example.services.ChatEventBroadcaster
import com.example.services.ChatEventPayload
import com.example.services.ChatService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class CreateDirectConversationRq(
    val memberId: String,
    val topic: String? = null
)

@Serializable
data class SendMessageRq(
    val body: String,
    val attachments: List<AttachmentRq> = emptyList()
)

@Serializable
data class TagMessageRq(
    val tag: MessageTag
)

@Serializable
data class AttachmentRq(
    val fileName: String,
    val contentType: String,
    val dataBase64: String
)

@Serializable
data class CreateGroupConversationRq(
    val topic: String? = null,
    val memberIds: List<String>
)

@Serializable
data class UpdateTopicRq(
    val topic: String?
)

@Serializable
data class ModifyMembersRq(
    val memberIds: List<String>
)

@Serializable
data class EditMessageRq(
    val body: String? = null,
    val attachments: List<AttachmentRq>? = null
)

@Serializable
data class ReactionRq(
    val emoji: String
)

@Serializable
data class TypingRq(
    val isTyping: Boolean
)

@Serializable
data class MarkReadRq(
    val messageId: String? = null
)

fun Route.chatRoutes() {
    val service = ChatService()

    authenticate("auth-jwt") {
        route("/api/chat") {
            webSocket("/updates") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.userIdOrNull()
                if (userId == null) {
                    close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "unauthorized"))
                    return@webSocket
                }

                ChatEventBroadcaster.register(userId, this)
                try {
                    send(Frame.Text("{\"type\":\"connected\"}"))
                    for (frame in incoming) {
                        if (frame is Frame.Close) break
                    }
                } finally {
                    ChatEventBroadcaster.unregister(userId, this)
                }
            }

            get("/conversations") {
                val principal = call.principalOrUnauthorized() ?: return@get
                val userId = principal.userIdOrNull()
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L

                val conversations = service.listConversations(userId, limit, offset)
                call.respond(HttpStatusCode.OK, conversations)
            }

            get("/conversations/{id}") {
                val principal = call.principalOrUnauthorized() ?: return@get
                val requesterId = principal.userIdOrNull()
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val conversation = try {
                    service.getConversation(conversationId, requesterId)
                } catch (nse: NoSuchElementException) {
                    return@get call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = if (iae.message == "not_a_conversation_member") HttpStatusCode.Forbidden else HttpStatusCode.BadRequest
                    return@get call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, conversation)
            }

            post("/conversations/{id}/pin") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val conversation = try {
                    service.pinConversation(conversationId, requesterId)
                } catch (iae: IllegalArgumentException) {
                    return@post call.respondError(HttpStatusCode.BadRequest, iae.message ?: "invalid_request")
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                }

                call.respond(HttpStatusCode.OK, conversation)
            }

            delete("/conversations/{id}/pin") {
                val principal = call.principalOrUnauthorized() ?: return@delete
                val requesterId = principal.userIdOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val conversation = try {
                    service.unpinConversation(conversationId, requesterId)
                } catch (iae: IllegalArgumentException) {
                    return@delete call.respondError(HttpStatusCode.BadRequest, iae.message ?: "invalid_request")
                } catch (nse: NoSuchElementException) {
                    return@delete call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                }

                call.respond(HttpStatusCode.OK, conversation)
            }

            post("/conversations/direct") {
                val principal = call.principalOrUnauthorized() ?: return@post

                val initiatorId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val rq = call.receive<CreateDirectConversationRq>()

                val targetId = rq.memberId.toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_member_id")

                val result = try {
                    service.createDirectConversation(initiatorId, targetId, rq.topic)
                } catch (iae: IllegalArgumentException) {
                    return@post call.respondError(HttpStatusCode.BadRequest, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, result)
            }

            post("/conversations/group") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val creatorId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val rq = call.receive<CreateGroupConversationRq>()
                val memberIds = rq.memberIds.mapToUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_member_id")

                val conversation = try {
                    service.createGroupConversation(creatorId, memberIds, rq.topic)
                } catch (iae: IllegalArgumentException) {
                    return@post call.respondError(HttpStatusCode.BadRequest, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.Created, conversation)
            }

            patch("/conversations/{id}/topic") {
                val principal = call.principalOrUnauthorized() ?: return@patch
                val requesterId = principal.userIdOrNull()
                    ?: return@patch call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@patch call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val rq = call.receive<UpdateTopicRq>()

                val updated = try {
                    service.updateConversationTopic(conversationId, requesterId, rq.topic)
                } catch (nse: NoSuchElementException) {
                    return@patch call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "not_group_conversation" -> HttpStatusCode.BadRequest
                        "not_conversation_owner" -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    return@patch call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, updated)
            }

            post("/conversations/{id}/members") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val rq = call.receive<ModifyMembersRq>()
                val memberIds = rq.memberIds.mapToUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_member_id")

                val members = try {
                    service.addMembers(conversationId, requesterId, memberIds)
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "not_conversation_owner" -> HttpStatusCode.Forbidden
                        "not_group_conversation" -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.BadRequest
                    }
                    return@post call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, members)
            }

            delete("/conversations/{id}/members/{memberId}") {
                val principal = call.principalOrUnauthorized() ?: return@delete
                val requesterId = principal.userIdOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val memberId = call.parameters["memberId"].toUuidOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_member_id")

                val members = try {
                    service.removeMember(conversationId, requesterId, memberId)
                } catch (nse: NoSuchElementException) {
                    return@delete call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "not_authorized" -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    return@delete call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, members)
            }

            post("/conversations/{id}/read") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val rq = call.receive<MarkReadRq>()
                val messageId = rq.messageId?.toUuidOrNull()

                try {
                    service.markConversationRead(conversationId, requesterId, messageId)
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "message_not_in_conversation" -> HttpStatusCode.BadRequest
                        else -> HttpStatusCode.BadRequest
                    }
                    return@post call.respondError(status, iae.message ?: "invalid_request")
                }

                val recipients = service.listConversationMemberIds(conversationId).map(UUID::toString)
                ChatEventBroadcaster.broadcast(
                    ChatEventPayload(
                        type = "conversation_read",
                        conversationId = conversationId.toString(),
                        recipients = recipients,
                        messageId = messageId?.toString(),
                        readerId = requesterId.toString(),
                        status = "READ"
                    )
                )

                call.respond(HttpStatusCode.Accepted)
            }

            post("/conversations/{id}/typing") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val rq = call.receive<TypingRq>()

                // We don't persist typing status, just broadcast
                // Check membership first (optional but good for security)
                try {
                    service.ensureMembership(conversationId, requesterId)
                } catch (e: Exception) {
                     return@post call.respondError(HttpStatusCode.Forbidden, "not_a_member")
                }

                val recipients = service.listConversationMemberIds(conversationId)
                    .filter { it != requesterId } // Don't send to self
                    .map(UUID::toString)

                ChatEventBroadcaster.broadcast(
                    ChatEventPayload(
                        type = "user_typing",
                        conversationId = conversationId.toString(),
                        recipients = recipients,
                        readerId = requesterId.toString(), // reusing readerId as "actorId"
                        status = if (rq.isTyping) "TYPING" else "STOPPED"
                    )
                )

                call.respond(HttpStatusCode.Accepted)
            }

            post("/conversations/{id}/messages") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val senderId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val rq = call.receive<SendMessageRq>()

                val result = try {
                    service.sendMessage(
                        conversationId,
                        senderId,
                        rq.body,
                        rq.attachments.map { it.toAttachmentInput() }
                    )
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = if (iae.message == "not_a_conversation_member") HttpStatusCode.Forbidden else HttpStatusCode.BadRequest
                    return@post call.respondError(status, iae.message ?: "invalid_request")
                }

                val recipients = service.listConversationMemberIds(conversationId)
                    .map(UUID::toString)

                ChatEventBroadcaster.broadcast(
                    ChatEventPayload(
                        type = "message_created",
                        conversationId = conversationId.toString(),
                        recipients = recipients,
                        message = result
                    )
                )

                call.respond(HttpStatusCode.Created, result)
            }

            get("/conversations/{id}/messages") {
                val principal = call.principalOrUnauthorized() ?: return@get
                val requesterId = principal.userIdOrNull()
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val conversationId = call.parameters["id"].toUuidOrNull()
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_conversation_id")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                val tagFilter = call.request.queryParameters["only"].toMessageTagFilter()
                val search = call.request.queryParameters["q"]

                val messages = try {
                    service.listMessages(conversationId, requesterId, limit, offset, tagFilter, search)
                } catch (nse: NoSuchElementException) {
                    return@get call.respondError(HttpStatusCode.NotFound, nse.message ?: "conversation_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = if (iae.message == "not_a_conversation_member") HttpStatusCode.Forbidden else HttpStatusCode.BadRequest
                    return@get call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, messages)
            }

            get("/messages") {
                val principal = call.principalOrUnauthorized() ?: return@get
                val requesterId = principal.userIdOrNull()
                    ?: return@get call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
                val offset = call.request.queryParameters["offset"]?.toLongOrNull() ?: 0L
                val tagFilter = call.request.queryParameters["only"].toMessageTagFilter()
                val search = call.request.queryParameters["q"]

                val messages = service.searchMessages(requesterId, tagFilter, search, limit, offset)
                call.respond(HttpStatusCode.OK, messages)
            }

            post("/messages/{id}/tag") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val messageId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_message_id")

                val rq = call.receive<TagMessageRq>()

                val updated = try {
                    service.tagMessage(messageId, requesterId, rq.tag)
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "message_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = if (iae.message == "not_a_conversation_member") HttpStatusCode.Forbidden else HttpStatusCode.BadRequest
                    return@post call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, updated)
            }

            patch("/messages/{id}") {
                val principal = call.principalOrUnauthorized() ?: return@patch
                val requesterId = principal.userIdOrNull()
                    ?: return@patch call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val messageId = call.parameters["id"].toUuidOrNull()
                    ?: return@patch call.respondError(HttpStatusCode.BadRequest, "invalid_message_id")

                val rq = call.receive<EditMessageRq>()

                val updated = try {
                    service.editMessage(
                        messageId,
                        requesterId,
                        rq.body,
                        rq.attachments?.map { it.toAttachmentInput() }
                    )
                } catch (nse: NoSuchElementException) {
                    return@patch call.respondError(HttpStatusCode.NotFound, nse.message ?: "message_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "not_message_owner" -> HttpStatusCode.Forbidden
                        "not_a_conversation_member" -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    return@patch call.respondError(status, iae.message ?: "invalid_request")
                } catch (ise: IllegalStateException) {
                    return@patch call.respondError(HttpStatusCode.BadRequest, ise.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, updated)
            }

            delete("/messages/{id}") {
                val principal = call.principalOrUnauthorized() ?: return@delete
                val requesterId = principal.userIdOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val messageId = call.parameters["id"].toUuidOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_message_id")

                val updated = try {
                    service.deleteMessage(messageId, requesterId)
                } catch (nse: NoSuchElementException) {
                    return@delete call.respondError(HttpStatusCode.NotFound, nse.message ?: "message_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "not_message_owner" -> HttpStatusCode.Forbidden
                        "not_a_conversation_member" -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    return@delete call.respondError(status, iae.message ?: "invalid_request")
                }

                call.respond(HttpStatusCode.OK, updated)
            }

            post("/messages/{id}/reactions") {
                val principal = call.principalOrUnauthorized() ?: return@post
                val requesterId = principal.userIdOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val messageId = call.parameters["id"].toUuidOrNull()
                    ?: return@post call.respondError(HttpStatusCode.BadRequest, "invalid_message_id")

                val rq = call.receive<ReactionRq>()

                val updated = try {
                    service.reactToMessage(messageId, requesterId, rq.emoji)
                } catch (nse: NoSuchElementException) {
                    return@post call.respondError(HttpStatusCode.NotFound, nse.message ?: "message_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "invalid_emoji" -> HttpStatusCode.BadRequest
                        "not_a_conversation_member" -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    return@post call.respondError(status, iae.message ?: "invalid_request")
                } catch (ise: IllegalStateException) {
                    return@post call.respondError(HttpStatusCode.BadRequest, ise.message ?: "invalid_request")
                }

                val recipients = service.listConversationMemberIds(updated.conversationId)
                    .map(UUID::toString)

                ChatEventBroadcaster.broadcast(
                    ChatEventPayload(
                        type = "reaction_updated",
                        conversationId = updated.conversationId.toString(),
                        recipients = recipients,
                        messageId = messageId.toString(),
                        message = updated.message,
                        reactionEmoji = rq.emoji,
                        reactionAction = updated.action.name.lowercase(),
                        reactions = updated.broadcastReactions
                    )
                )

                call.respond(HttpStatusCode.OK, updated.message)
            }

            delete("/messages/{id}/reactions") {
                val principal = call.principalOrUnauthorized() ?: return@delete
                val requesterId = principal.userIdOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_subject")

                val messageId = call.parameters["id"].toUuidOrNull()
                    ?: return@delete call.respondError(HttpStatusCode.BadRequest, "invalid_message_id")

                val updated = try {
                    service.removeReaction(messageId, requesterId)
                } catch (nse: NoSuchElementException) {
                    return@delete call.respondError(HttpStatusCode.NotFound, nse.message ?: "message_not_found")
                } catch (iae: IllegalArgumentException) {
                    val status = when (iae.message) {
                        "not_a_conversation_member" -> HttpStatusCode.Forbidden
                        else -> HttpStatusCode.BadRequest
                    }
                    return@delete call.respondError(status, iae.message ?: "invalid_request")
                }

                val recipients = service.listConversationMemberIds(updated.conversationId)
                    .map(UUID::toString)

                ChatEventBroadcaster.broadcast(
                    ChatEventPayload(
                        type = "reaction_updated",
                        conversationId = updated.conversationId.toString(),
                        recipients = recipients,
                        messageId = messageId.toString(),
                        message = updated.message,
                        reactionEmoji = updated.emoji.ifEmpty { null },
                        reactionAction = updated.action.name.lowercase(),
                        reactions = updated.broadcastReactions
                    )
                )

                call.respond(HttpStatusCode.OK, updated.message)
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

private fun String?.toMessageTagFilter(): MessageTag? = when (this?.lowercase()) {
    "meetings" -> MessageTag.MEETING
    "answers" -> MessageTag.ANSWER
    "important" -> MessageTag.IMPORTANT
    else -> null
}

private fun List<String>.mapToUuidOrNull(): List<UUID>? {
    val result = mutableListOf<UUID>()
    for (raw in this) {
        val parsed = raw.toUuidOrNull() ?: return null
        result += parsed
    }
    return result
}

private fun AttachmentRq.toAttachmentInput(): ChatService.AttachmentInput =
    ChatService.AttachmentInput(fileName, contentType, dataBase64)