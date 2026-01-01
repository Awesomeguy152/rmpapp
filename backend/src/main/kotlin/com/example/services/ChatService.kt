package com.example.services

import com.example.schema.ConversationDTO
import com.example.schema.ConversationMemberDTO
import com.example.schema.ConversationMembers
import com.example.schema.ConversationPins
import com.example.schema.ConversationReadMarkers
import com.example.schema.ConversationSummaryDTO
import com.example.schema.ConversationType
import com.example.schema.Conversations
import com.example.schema.MessageAttachmentDTO
import com.example.schema.MessageAttachments
import com.example.schema.MessageDTO
import com.example.schema.MessageReactionDTO
import com.example.schema.MessageReactions
import com.example.schema.MessageStatus
import com.example.schema.MessageTag
import com.example.schema.Messages
import com.example.schema.UserTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.stringParam
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNull

import java.time.Instant
import java.util.UUID

class ChatService {

    private val allowedEmojis = setOf("👍", "❤️", "😂", "😮", "😢", "👎")

    data class AttachmentInput(
        val fileName: String,
        val contentType: String,
        val dataBase64: String
    )

    fun createDirectConversation(initiatorId: UUID, targetId: UUID, topic: String? = null): ConversationDTO = transaction {
        require(initiatorId != targetId) { "cannot_create_conversation_with_self" }

        val directKey = directKeyFor(initiatorId, targetId)
        val normalizedTopic = topic?.takeIf { it.isNotBlank() }

        val existing = Conversations
            .select { (Conversations.type eq ConversationType.DIRECT) and (Conversations.directKey eq directKey) }
            .singleOrNull()

        val conversationId = existing?.let { it[Conversations.id] } ?: run {
            val newId = Conversations.insertAndGetId {
                it[type] = ConversationType.DIRECT
                it[Conversations.topic] = normalizedTopic
                it[Conversations.directKey] = directKey
                it[createdBy] = EntityID(initiatorId, UserTable)
            }

            ConversationMembers.batchInsert(listOf(initiatorId, targetId)) { memberId ->
                this[ConversationMembers.conversation] = newId
                this[ConversationMembers.user] = EntityID(memberId, UserTable)
            }

            newId
        }

        Conversations
            .select { Conversations.id eq conversationId }
            .single()
            .toConversationDTO()
    }

    fun createGroupConversation(creatorId: UUID, memberIds: List<UUID>, topic: String?): ConversationDTO = transaction {
        val normalizedTopic = topic?.takeIf { it.isNotBlank() }
        val uniqueMembers = (memberIds + creatorId).toSet()

        require(uniqueMembers.size >= 2) { "group_requires_members" }

        val conversationId = Conversations.insertAndGetId {
            it[type] = ConversationType.GROUP
            it[Conversations.topic] = normalizedTopic
            it[Conversations.directKey] = null
            it[createdBy] = EntityID(creatorId, UserTable)
        }

        ConversationMembers.batchInsert(uniqueMembers.toList()) { memberId ->
            this[ConversationMembers.conversation] = conversationId
            this[ConversationMembers.user] = EntityID(memberId, UserTable)
        }

        Conversations
            .select { Conversations.id eq conversationId }
            .single()
            .toConversationDTO()
    }

    fun updateConversationTopic(conversationId: UUID, requesterId: UUID, topic: String?): ConversationDTO = transaction {
        val conversationEntityId = EntityID(conversationId, Conversations)
        ensureGroupOwner(conversationEntityId, requesterId)

        val normalizedTopic = topic?.takeIf { it.isNotBlank() }

        Conversations.update({ Conversations.id eq conversationEntityId }) {
            it[Conversations.topic] = normalizedTopic
        }

        Conversations
            .select { Conversations.id eq conversationEntityId }
            .single()
            .toConversationDTO()
    }

    fun listConversations(userId: UUID, limit: Int, offset: Long): List<ConversationSummaryDTO> = transaction {
        val membership = ConversationMembers.alias("membership")
        val pins = ConversationPins.alias("pins")
        val userEntityId = EntityID(userId, UserTable)
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        Conversations
            .join(membership, JoinType.INNER, additionalConstraint = { Conversations.id eq membership[ConversationMembers.conversation] })
            .join(pins, JoinType.LEFT, additionalConstraint = {
                (Conversations.id eq pins[ConversationPins.conversation]) and (pins[ConversationPins.user] eq userEntityId)
            })
            .select { membership[ConversationMembers.user] eq userEntityId }
            .orderBy(pins[ConversationPins.pinnedAt] to SortOrder.DESC, Conversations.createdAt to SortOrder.DESC)
            .limit(safeLimit, safeOffset)
            .map { row ->
                val pinnedAt = row.getOrNull(pins[ConversationPins.pinnedAt])
                row.toConversationSummary(userId, pinnedAt)
            }
    }

    fun getConversation(conversationId: UUID, requesterId: UUID): ConversationSummaryDTO = transaction {
        ensureMembership(conversationId, requesterId)

        val conversationEntityId = EntityID(conversationId, Conversations)
        val pins = ConversationPins.alias("pins")
        val userEntityId = EntityID(requesterId, UserTable)

        Conversations
            .join(pins, JoinType.LEFT, additionalConstraint = {
                (Conversations.id eq pins[ConversationPins.conversation]) and (pins[ConversationPins.user] eq userEntityId)
            })
            .select { Conversations.id eq conversationEntityId }
            .singleOrNull()
            ?.let { row ->
                val pinnedAt = row.getOrNull(pins[ConversationPins.pinnedAt])
                row.toConversationSummary(requesterId, pinnedAt)
            }
            ?: throw NoSuchElementException("conversation_not_found")
    }

    fun pinConversation(conversationId: UUID, requesterId: UUID): ConversationSummaryDTO = transaction {
        ensureMembership(conversationId, requesterId)
        val conversationEntityId = EntityID(conversationId, Conversations)
        val userEntityId = EntityID(requesterId, UserTable)

        ConversationPins.insertIgnore {
            it[conversation] = conversationEntityId
            it[user] = userEntityId
        }

        getConversation(conversationId, requesterId)
    }

    fun unpinConversation(conversationId: UUID, requesterId: UUID): ConversationSummaryDTO = transaction {
        ensureMembership(conversationId, requesterId)
        val conversationEntityId = EntityID(conversationId, Conversations)
        val userEntityId = EntityID(requesterId, UserTable)

        ConversationPins.deleteWhere {
            (ConversationPins.conversation eq conversationEntityId) and (ConversationPins.user eq userEntityId)
        }

        getConversation(conversationId, requesterId)
    }

    fun addMembers(conversationId: UUID, requesterId: UUID, memberIds: List<UUID>): List<ConversationMemberDTO> = transaction {
        val conversationEntityId = EntityID(conversationId, Conversations)
        ensureGroupOwner(conversationEntityId, requesterId)

        val existingMembers = ConversationMembers
            .select { ConversationMembers.conversation eq conversationEntityId }
            .map { it[ConversationMembers.user].value }
            .toMutableSet()

        val newMembers = memberIds.filter { existingMembers.add(it) }

        if (newMembers.isNotEmpty()) {
            ConversationMembers.batchInsert(newMembers) { memberId ->
                this[ConversationMembers.conversation] = conversationEntityId
                this[ConversationMembers.user] = EntityID(memberId, UserTable)
            }
        }

        fetchMembers(conversationId)
    }

    fun removeMember(conversationId: UUID, requesterId: UUID, memberId: UUID): List<ConversationMemberDTO> = transaction {
        val conversationEntityId = EntityID(conversationId, Conversations)
        val memberEntityId = EntityID(memberId, UserTable)

        ensureMembership(conversationId, requesterId)

        val isOwner = Conversations
            .select { Conversations.id eq conversationEntityId }
            .single()[Conversations.createdBy].value == requesterId

        if (!isOwner && requesterId != memberId) {
            throw IllegalArgumentException("not_authorized")
        }

        ConversationMembers.deleteWhere {
            (ConversationMembers.conversation eq conversationEntityId) and (ConversationMembers.user eq memberEntityId)
        }

        ConversationReadMarkers.deleteWhere {
            (ConversationReadMarkers.conversation eq conversationEntityId) and (ConversationReadMarkers.user eq memberEntityId)
        }

        fetchMembers(conversationId)
    }

    fun markConversationRead(conversationId: UUID, userId: UUID, messageId: UUID?): Unit = transaction {
        ensureMembership(conversationId, userId)

        val conversationEntityId = EntityID(conversationId, Conversations)
        val userEntityId = EntityID(userId, UserTable)

        messageId?.let {
            val messageEntityId = EntityID(it, Messages)
            val belongs = Messages.select { Messages.id eq messageEntityId }
                .singleOrNull()

            if (belongs == null || belongs[Messages.conversation].value != conversationId) {
                throw IllegalArgumentException("message_not_in_conversation")
            }
        }

        upsertReadMarker(conversationEntityId, userEntityId, messageId, Instant.now())
    }

    fun editMessage(
        messageId: UUID,
        requesterId: UUID,
        newBody: String?,
        attachments: List<AttachmentInput>?
    ): MessageDTO = transaction {
        val messageEntityId = EntityID(messageId, Messages)
        val row = Messages
            .select { Messages.id eq messageEntityId }
            .singleOrNull()
            ?: throw NoSuchElementException("message_not_found")

        if (row[Messages.deletedAt] != null) {
            throw IllegalStateException("message_deleted")
        }

        val senderId = row[Messages.sender].value
        if (senderId != requesterId) {
            throw IllegalArgumentException("not_message_owner")
        }

        ensureMembership(row[Messages.conversation].value, requesterId)

        val trimmedBody = newBody?.trim()
        if (trimmedBody != null) {
            require(trimmedBody.isNotEmpty()) { "message_body_blank" }
        }

        val now = Instant.now()

        Messages.update({ Messages.id eq messageEntityId }) {
            trimmedBody?.let { body ->
                it[Messages.body] = body
            }
            it[Messages.editedAt] = now
        }

        attachments?.let {
            MessageAttachments.deleteWhere { MessageAttachments.message eq messageEntityId }
            saveAttachments(messageEntityId, it)
        }

        val attachmentsMap = attachmentsMap(listOf(messageEntityId))
        val reactions = reactionsMap(listOf(messageEntityId), requesterId)
        val conversationId = row[Messages.conversation].value
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(EntityID(conversationId, Conversations))

        Messages
            .select { Messages.id eq messageEntityId }
            .single()
            .let { updated ->
                val readers = readersForMessage(updated[Messages.createdAt], markers)
                val status = statusForMessage(senderId, members, readers)
                updated.toMessageDTO(attachmentsMap, status, readers, reactions)
            }
    }

    fun deleteMessage(messageId: UUID, requesterId: UUID): MessageDTO = transaction {
        val messageEntityId = EntityID(messageId, Messages)
        val row = Messages
            .select { Messages.id eq messageEntityId }
            .singleOrNull()
            ?: throw NoSuchElementException("message_not_found")

        val senderId = row[Messages.sender].value
        if (senderId != requesterId) {
            throw IllegalArgumentException("not_message_owner")
        }

        ensureMembership(row[Messages.conversation].value, requesterId)

        val now = Instant.now()

        Messages.update({ Messages.id eq messageEntityId }) {
            it[Messages.deletedAt] = now
            it[Messages.editedAt] = now
        }

        MessageAttachments.deleteWhere { MessageAttachments.message eq messageEntityId }

        val attachmentsMap = emptyMap<UUID, List<MessageAttachmentDTO>>()
        val conversationId = row[Messages.conversation].value
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(EntityID(conversationId, Conversations))
        val reactions = reactionsMap(listOf(messageEntityId), requesterId)

        Messages
            .select { Messages.id eq messageEntityId }
            .single()
            .let { updated ->
                val readers = readersForMessage(updated[Messages.createdAt], markers)
                val status = statusForMessage(senderId, members, readers)
                updated.toMessageDTO(attachmentsMap, status, readers, reactions)
            }
    }

    enum class ReactionAction { ADDED, UPDATED, REMOVED }

    data class ReactionUpdate(
        val message: MessageDTO,
        val conversationId: UUID,
        val emoji: String,
        val action: ReactionAction,
        val broadcastReactions: List<MessageReactionDTO>
    )

    fun reactToMessage(messageId: UUID, requesterId: UUID, emoji: String): ReactionUpdate = transaction {
        if (!allowedEmojis.contains(emoji)) throw IllegalArgumentException("invalid_emoji")

        val messageEntityId = EntityID(messageId, Messages)

        val messageRow = Messages
            .select { Messages.id eq messageEntityId }
            .singleOrNull()
            ?: throw NoSuchElementException("message_not_found")

        val conversationId = messageRow[Messages.conversation].value
        ensureMembership(conversationId, requesterId)

        if (messageRow[Messages.deletedAt] != null) throw IllegalStateException("message_deleted")

        val userEntityId = EntityID(requesterId, UserTable)
        val existing = MessageReactions
            .select { (MessageReactions.message eq messageEntityId) and (MessageReactions.user eq userEntityId) }
            .singleOrNull()

        val action = if (existing == null) ReactionAction.ADDED else ReactionAction.UPDATED

        if (existing == null) {
            MessageReactions.insert {
                it[message] = messageEntityId
                it[user] = userEntityId
                it[MessageReactions.emoji] = emoji
            }
        } else {
            MessageReactions.update({ MessageReactions.id eq existing[MessageReactions.id] }) {
                it[MessageReactions.emoji] = emoji
            }
        }

        val attachmentsMap = attachmentsMap(listOf(messageEntityId))
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(EntityID(conversationId, Conversations))
        val reactionsForRequester = reactionsMap(listOf(messageEntityId), requesterId)
        val reactionsForBroadcast = reactionsMap(listOf(messageEntityId), null)[messageId] ?: emptyList()

        val updated = Messages
            .select { Messages.id eq messageEntityId }
            .single()

        val readers = readersForMessage(updated[Messages.createdAt], markers)
        val status = statusForMessage(updated[Messages.sender].value, members, readers)

        ReactionUpdate(
            message = updated.toMessageDTO(attachmentsMap, status, readers, reactionsForRequester),
            conversationId = conversationId,
            emoji = emoji,
            action = action,
            broadcastReactions = reactionsForBroadcast
        )
    }

    fun removeReaction(messageId: UUID, requesterId: UUID): ReactionUpdate = transaction {
        val messageEntityId = EntityID(messageId, Messages)

        val messageRow = Messages
            .select { Messages.id eq messageEntityId }
            .singleOrNull()
            ?: throw NoSuchElementException("message_not_found")

        val conversationId = messageRow[Messages.conversation].value
        ensureMembership(conversationId, requesterId)

        val userEntityId = EntityID(requesterId, UserTable)

        MessageReactions.deleteWhere { (MessageReactions.message eq messageEntityId) and (MessageReactions.user eq userEntityId) }

        val attachmentsMap = attachmentsMap(listOf(messageEntityId))
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(EntityID(conversationId, Conversations))
        val reactionsForRequester = reactionsMap(listOf(messageEntityId), requesterId)
        val reactionsForBroadcast = reactionsMap(listOf(messageEntityId), null)[messageId] ?: emptyList()

        val updated = Messages
            .select { Messages.id eq messageEntityId }
            .single()

        val readers = readersForMessage(updated[Messages.createdAt], markers)
        val status = statusForMessage(updated[Messages.sender].value, members, readers)

        ReactionUpdate(
            message = updated.toMessageDTO(attachmentsMap, status, readers, reactionsForRequester),
            conversationId = conversationId,
            emoji = "",
            action = ReactionAction.REMOVED,
            broadcastReactions = reactionsForBroadcast
        )
    }

    fun sendMessage(
        conversationId: UUID,
        senderId: UUID,
        body: String,
        attachments: List<AttachmentInput> = emptyList()
    ): MessageDTO = transaction {
        val trimmedBody = body.trim()
        require(trimmedBody.isNotEmpty()) { "message_body_blank" }

        ensureMembership(conversationId, senderId)

        val conversationEntityId = EntityID(conversationId, Conversations)

        val messageId = Messages.insertAndGetId {
            it[Messages.conversation] = conversationEntityId
            it[Messages.sender] = EntityID(senderId, UserTable)
            it[Messages.body] = trimmedBody
            it[Messages.tag] = MessageTag.NONE
            it[Messages.deletedAt] = null
            it[Messages.editedAt] = null
        }

        saveAttachments(messageId, attachments)

        upsertReadMarker(conversationEntityId, EntityID(senderId, UserTable), messageId.value)

        val attachmentsByMessage = attachmentsMap(listOf(messageId))
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(conversationEntityId)

        Messages
            .select { Messages.id eq messageId }
            .single()
            .let { row ->
                val readers = readersForMessage(row[Messages.createdAt], markers)
                val status = statusForMessage(senderId, members, readers)
                row.toMessageDTO(attachmentsByMessage, status, readers)
            }
    }

    fun listMessages(
        conversationId: UUID,
        requesterId: UUID,
        limit: Int,
        offset: Long,
        tag: MessageTag? = null,
        search: String? = null
    ): List<MessageDTO> = transaction {
        ensureMembership(conversationId, requesterId)

        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        var condition: Op<Boolean> = Messages.conversation eq EntityID(conversationId, Conversations)

        tag?.let { condition = condition and (Messages.tag eq it) }

        search?.takeIf { it.isNotBlank() }?.let { keyword ->
            val pattern = "%${keyword.trim()}%"
            condition = condition and Messages.body.ilike(pattern)
        }

        val rows = Messages
            .select { condition }
            .orderBy(Messages.createdAt, SortOrder.ASC)
            .limit(safeLimit, safeOffset)
            .toList()

        val attachments = attachmentsMap(rows.map { it[Messages.id] })
        val reactions = reactionsMap(rows.map { it[Messages.id] }, requesterId)
        val conversationEntityId = EntityID(conversationId, Conversations)
        val markers = loadReadMarkers(conversationEntityId)
        val members = listConversationMemberIds(conversationId).toSet()

        rows.map { row ->
            val readers = readersForMessage(row[Messages.createdAt], markers)
            val status = statusForMessage(row[Messages.sender].value, members, readers)
            row.toMessageDTO(attachments, status, readers, reactions)
        }
    }

    fun searchMessages(
        userId: UUID,
        tag: MessageTag? = null,
        search: String? = null,
        limit: Int,
        offset: Long
    ): List<MessageDTO> = transaction {
        val membership = ConversationMembers.alias("membership")

        val userCondition: Op<Boolean> = membership[ConversationMembers.user] eq EntityID(userId, UserTable)
        val safeLimit = limit.coerceIn(1, 100)
        val safeOffset = offset.coerceAtLeast(0)

        var condition: Op<Boolean> = userCondition
        condition = condition and Messages.deletedAt.isNull()

        tag?.let { condition = condition and (Messages.tag eq it) }

        search?.takeIf { it.isNotBlank() }?.let { keyword ->
            val pattern = "%${keyword.trim()}%"
            condition = condition and Messages.body.ilike(pattern)
        }

        val rows = Messages
            .join(membership, JoinType.INNER, additionalConstraint = { Messages.conversation eq membership[ConversationMembers.conversation] })
            .select { condition }
            .orderBy(Messages.createdAt, SortOrder.DESC)
            .limit(safeLimit, safeOffset)
            .toList()

        val attachments = attachmentsMap(rows.map { it[Messages.id] })
        val markersByConversation = mutableMapOf<UUID, Map<UUID, ReadMarker>>()
        val membersByConversation = mutableMapOf<UUID, Set<UUID>>()

        rows.map { row ->
            val conversationId = row[Messages.conversation].value
            val conversationEntityId = EntityID(conversationId, Conversations)
            val markers = markersByConversation.getOrPut(conversationId) { loadReadMarkers(conversationEntityId) }
            val members = membersByConversation.getOrPut(conversationId) { listConversationMemberIds(conversationId).toSet() }
            val readers = readersForMessage(row[Messages.createdAt], markers)
            val status = statusForMessage(row[Messages.sender].value, members, readers)
            row.toMessageDTO(attachments, status, readers)
        }
    }

    fun tagMessage(messageId: UUID, requesterId: UUID, tag: MessageTag): MessageDTO = transaction {
        val messageEntityId = EntityID(messageId, Messages)

        val messageRow = Messages
            .select { Messages.id eq messageEntityId }
            .singleOrNull()
            ?: throw NoSuchElementException("message_not_found")

        val conversationId = messageRow[Messages.conversation].value

        ensureMembership(conversationId, requesterId)

        Messages.update({ Messages.id eq messageEntityId }) {
            it[Messages.tag] = tag
        }

        val conversationEntityId = EntityID(conversationId, Conversations)
        val attachments = attachmentsMap(listOf(messageEntityId))
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(conversationEntityId)

        Messages
            .select { Messages.id eq messageEntityId }
            .single()
            .let { row ->
                val readers = readersForMessage(row[Messages.createdAt], markers)
                val status = statusForMessage(row[Messages.sender].value, members, readers)
                row.toMessageDTO(attachments, status, readers)
            }
    }

    fun assertMembership(conversationId: UUID, userId: UUID) = transaction {
        ensureMembership(conversationId, userId)
    }

    private fun ensureMembership(conversationId: UUID, userId: UUID) {
        val conversationEntityId = EntityID(conversationId, Conversations)
        val conversationExists = Conversations
            .select { Conversations.id eq conversationEntityId }
            .empty()
            .not()

        if (!conversationExists) {
            throw NoSuchElementException("conversation_not_found")
        }

        val isMember = ConversationMembers
            .select {
                (ConversationMembers.conversation eq conversationEntityId) and
                (ConversationMembers.user eq EntityID(userId, UserTable))
            }
            .empty()
            .not()

        if (!isMember) {
            throw IllegalArgumentException("not_a_conversation_member")
        }
    }

    private fun ResultRow.toConversationSummary(viewerId: UUID, pinnedAt: java.time.Instant? = null): ConversationSummaryDTO {
        val conversationId = this[Conversations.id].value

        return ConversationSummaryDTO(
            id = conversationId.toString(),
            type = this[Conversations.type],
            topic = this[Conversations.topic],
            createdBy = this[Conversations.createdBy].value.toString(),
            createdAt = this[Conversations.createdAt].toInstantString(),
            pinnedAt = pinnedAt?.toInstantString(),
            members = fetchMembers(conversationId),
            lastMessage = fetchLastMessage(conversationId),
            unreadCount = unreadCount(conversationId, viewerId)
        )
    }

    private fun ResultRow.toConversationDTO(): ConversationDTO = ConversationDTO(
        id = this[Conversations.id].value.toString(),
        type = this[Conversations.type],
        topic = this[Conversations.topic],
        directKey = this[Conversations.directKey],
        createdBy = this[Conversations.createdBy].value.toString(),
        createdAt = this[Conversations.createdAt].toInstantString()
    )

    private fun ResultRow.toMessageDTO(
        attachmentsByMessage: Map<UUID, List<MessageAttachmentDTO>>,
        status: MessageStatus = MessageStatus.DELIVERED,
        readBy: Set<UUID> = emptySet(),
        reactionsByMessage: Map<UUID, List<MessageReactionDTO>> = emptyMap()
    ): MessageDTO {
        val messageId = this[Messages.id].value
        val deletedAt = this[Messages.deletedAt]
        val editedAt = this[Messages.editedAt]

        return MessageDTO(
            id = messageId.toString(),
            conversationId = this[Messages.conversation].value.toString(),
            senderId = this[Messages.sender].value.toString(),
            body = if (deletedAt == null) this[Messages.body] else "",
            tag = this[Messages.tag],
            createdAt = this[Messages.createdAt].toInstantString(),
            editedAt = editedAt?.toInstantString(),
            deletedAt = deletedAt?.toInstantString(),
            attachments = if (deletedAt == null) attachmentsByMessage[messageId] ?: emptyList() else emptyList(),
            status = status,
            readBy = readBy.map(UUID::toString),
            reactions = reactionsByMessage[messageId] ?: emptyList()
        )
    }

    private fun reactionsMap(messageIds: List<EntityID<UUID>>, currentUserId: UUID?): Map<UUID, List<MessageReactionDTO>> {
        if (messageIds.isEmpty()) return emptyMap()

        val rows = MessageReactions
            .select { MessageReactions.message inList messageIds.distinct() }
            .toList()

        val me = currentUserId

        val aggregated = mutableMapOf<UUID, MutableMap<String, Pair<Long, Boolean>>>()

        rows.forEach { row ->
            val messageId = row[MessageReactions.message].value
            val emoji = row[MessageReactions.emoji]
            val byMe = me != null && row[MessageReactions.user].value == me
            val bucket = aggregated.getOrPut(messageId) { mutableMapOf() }
            val current = bucket[emoji]
            val newCount = (current?.first ?: 0) + 1
            val reactedByMe = (current?.second ?: false) || byMe
            bucket[emoji] = newCount to reactedByMe
        }

        return aggregated.mapValues { (_, emojiMap) ->
            emojiMap.entries
                .sortedWith(compareByDescending<Map.Entry<String, Pair<Long, Boolean>>> { it.value.first }.thenBy { it.key })
                .map { (emoji, data) ->
                    MessageReactionDTO(
                        emoji = emoji,
                        count = data.first,
                        reactedByMe = data.second
                    )
                }
        }
    }

    private fun ResultRow.toAttachmentDTO(): MessageAttachmentDTO = MessageAttachmentDTO(
        id = this[MessageAttachments.id].value.toString(),
        fileName = this[MessageAttachments.fileName],
        contentType = this[MessageAttachments.contentType],
        dataBase64 = this[MessageAttachments.dataBase64]
    )

    private fun ResultRow.toConversationMemberDTO(): ConversationMemberDTO = ConversationMemberDTO(
        userId = this[ConversationMembers.user].value.toString(),
        joinedAt = this[ConversationMembers.joinedAt].toInstantString()
    )

    private fun attachmentsMap(messageIds: List<EntityID<UUID>>): Map<UUID, List<MessageAttachmentDTO>> {
        if (messageIds.isEmpty()) return emptyMap()

        val unique = messageIds.distinct()

        return MessageAttachments
            .select { MessageAttachments.message inList unique }
            .groupBy { it[MessageAttachments.message].value }
            .mapValues { entry -> entry.value.map { it.toAttachmentDTO() } }
    }

    private data class ReadMarker(val lastReadAt: Instant, val lastReadMessage: UUID?)

    private fun loadReadMarkers(conversationId: EntityID<UUID>): Map<UUID, ReadMarker> {
        return ConversationReadMarkers
            .select { ConversationReadMarkers.conversation eq conversationId }
            .associate { row ->
                val userId = row[ConversationReadMarkers.user].value
                val messageId = row[ConversationReadMarkers.lastReadMessage]?.value
                userId to ReadMarker(row[ConversationReadMarkers.lastReadAt], messageId)
            }
    }

    private fun readersForMessage(
        createdAt: Instant,
        markers: Map<UUID, ReadMarker>
    ): Set<UUID> {
        return markers.filter { (_, marker) ->
            val byTime = marker.lastReadAt >= createdAt
            val byMessage = marker.lastReadMessage != null
            if (byMessage) byTime || marker.lastReadAt >= createdAt else byTime
        }.keys
    }

    private fun statusForMessage(
        senderId: UUID,
        members: Set<UUID>,
        readers: Set<UUID>
    ): MessageStatus {
        val others = members - senderId
        return if (others.isEmpty()) {
            MessageStatus.READ
        } else if (others.all { readers.contains(it) }) {
            MessageStatus.READ
        } else {
            MessageStatus.DELIVERED
        }
    }

    private fun saveAttachments(messageId: EntityID<UUID>, attachments: List<AttachmentInput>) {
        if (attachments.isEmpty()) return

        MessageAttachments.batchInsert(attachments) { attachment ->
            this[MessageAttachments.message] = messageId
            this[MessageAttachments.fileName] = attachment.fileName
            this[MessageAttachments.contentType] = attachment.contentType
            this[MessageAttachments.dataBase64] = attachment.dataBase64
        }
    }

    private fun upsertReadMarker(
        conversationId: EntityID<UUID>,
        userId: EntityID<UUID>,
        messageId: UUID?,
        readAt: Instant = Instant.now()
    ) {
        val existing = ConversationReadMarkers
            .select { (ConversationReadMarkers.conversation eq conversationId) and (ConversationReadMarkers.user eq userId) }
            .singleOrNull()

        val messageEntity = messageId?.let { EntityID(it, Messages) }

        if (existing == null) {
            ConversationReadMarkers.insert {
                it[ConversationReadMarkers.conversation] = conversationId
                it[ConversationReadMarkers.user] = userId
                it[ConversationReadMarkers.lastReadAt] = readAt
                it[ConversationReadMarkers.lastReadMessage] = messageEntity
            }
        } else {
            ConversationReadMarkers.update({ (ConversationReadMarkers.conversation eq conversationId) and (ConversationReadMarkers.user eq userId) }) {
                it[lastReadAt] = readAt
                it[lastReadMessage] = messageEntity
            }
        }
    }

    private fun fetchMembers(conversationId: UUID): List<ConversationMemberDTO> {
        val conversationEntityId = EntityID(conversationId, Conversations)
        return ConversationMembers
            .select { ConversationMembers.conversation eq conversationEntityId }
            .orderBy(ConversationMembers.joinedAt, SortOrder.ASC)
            .map { it.toConversationMemberDTO() }
    }

    fun listConversationMemberIds(conversationId: UUID): List<UUID> = transaction {
        val conversationEntityId = EntityID(conversationId, Conversations)
        ConversationMembers
            .select { ConversationMembers.conversation eq conversationEntityId }
            .map { it[ConversationMembers.user].value }
    }

    private fun fetchLastMessage(conversationId: UUID): MessageDTO? {
        val conversationEntityId = EntityID(conversationId, Conversations)
        val rows = Messages
            .select { (Messages.conversation eq conversationEntityId) and Messages.deletedAt.isNull() }
            .orderBy(Messages.createdAt, SortOrder.DESC)
            .limit(1)
            .toList()

        if (rows.isEmpty()) return null

        val attachments = attachmentsMap(rows.map { it[Messages.id] })
        val reactions = reactionsMap(rows.map { it[Messages.id] }, null)
        val members = listConversationMemberIds(conversationId).toSet()
        val markers = loadReadMarkers(conversationEntityId)
        return rows.first().let { row ->
            val readers = readersForMessage(row[Messages.createdAt], markers)
            val status = statusForMessage(row[Messages.sender].value, members, readers)
            row.toMessageDTO(attachments, status, readers, reactions)
        }
    }

    private fun unreadCount(conversationId: UUID, userId: UUID): Long {
        val conversationEntityId = EntityID(conversationId, Conversations)
        val userEntityId = EntityID(userId, UserTable)

        val marker = ConversationReadMarkers
            .select { (ConversationReadMarkers.conversation eq conversationEntityId) and (ConversationReadMarkers.user eq userEntityId) }
            .singleOrNull()

        var condition: Op<Boolean> = (Messages.conversation eq conversationEntityId) and Messages.deletedAt.isNull()

        marker?.let {
            val lastReadAt = it[ConversationReadMarkers.lastReadAt]
            condition = condition and (Messages.createdAt greater lastReadAt)
        }

        val countExpr = Messages.id.count()

        return Messages
            .slice(countExpr)
            .select { condition }
            .single()[countExpr]
    }

    private fun ensureGroupOwner(conversationId: EntityID<UUID>, requesterId: UUID) {
        val row = Conversations
            .select { Conversations.id eq conversationId }
            .singleOrNull()
            ?: throw NoSuchElementException("conversation_not_found")

        if (row[Conversations.type] != ConversationType.GROUP) {
            throw IllegalArgumentException("not_group_conversation")
        }

        if (row[Conversations.createdBy].value != requesterId) {
            throw IllegalArgumentException("not_conversation_owner")
        }
    }

    private fun directKeyFor(a: UUID, b: UUID): String =
        listOf(a.toString(), b.toString()).sorted().joinToString(":")

    private fun Instant.toInstantString(): String = this.toString()

    private fun Column<String>.ilike(pattern: String): Op<Boolean> = object : Op<Boolean>() {
        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder {
                append(this@ilike)
                append(" ILIKE ")
                append(stringParam(pattern))
            }
        }
    }
}