package com.example.services

import com.example.schema.Meetings
import com.example.schema.MeetingParticipants
import com.example.schema.Messages
import com.example.schema.ConversationMembers
import com.example.schema.UserTable
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class MeetingService {

    fun createMeeting(
        conversationId: UUID,
        creatorId: UUID,
        title: String,
        description: String?,
        scheduledAt: Instant,
        location: String?,
        aiGenerated: Boolean = false,
        sourceMessageId: UUID? = null
    ): MeetingDto = transaction {
        val meetingId = Meetings.insertAndGetId {
            it[Meetings.conversationId] = conversationId
            it[Meetings.creatorId] = creatorId
            it[Meetings.title] = title
            it[Meetings.description] = description
            it[Meetings.scheduledAt] = scheduledAt
            it[Meetings.location] = location
            it[Meetings.aiGenerated] = aiGenerated
            it[Meetings.sourceMessageId] = sourceMessageId
            it[Meetings.status] = "pending"
        }.value

        // Добавляем всех участников чата как участников встречи
        val members = ConversationMembers
            .select { ConversationMembers.conversation eq conversationId }
            .map { it[ConversationMembers.user].value }

        members.forEach { memberId ->
            MeetingParticipants.insert {
                it[MeetingParticipants.meetingId] = meetingId
                it[MeetingParticipants.userId] = memberId
                it[MeetingParticipants.status] = if (memberId == creatorId) "accepted" else "pending"
            }
        }

        getMeetingById(meetingId)!!
    }

    /**
     * Создать персональную встречу без привязки к чату
     */
    fun createPersonalMeeting(
        creatorId: UUID,
        title: String,
        description: String?,
        scheduledAt: Instant,
        location: String?
    ): MeetingDto = transaction {
        val meetingId = Meetings.insertAndGetId {
            it[Meetings.conversationId] = null
            it[Meetings.creatorId] = creatorId
            it[Meetings.title] = title
            it[Meetings.description] = description
            it[Meetings.scheduledAt] = scheduledAt
            it[Meetings.location] = location
            it[Meetings.aiGenerated] = false
            it[Meetings.sourceMessageId] = null
            it[Meetings.status] = "confirmed"
        }.value

        // Добавляем только создателя как участника
        MeetingParticipants.insert {
            it[MeetingParticipants.meetingId] = meetingId
            it[MeetingParticipants.userId] = creatorId
            it[MeetingParticipants.status] = "accepted"
        }

        getMeetingById(meetingId)!!
    }

    fun getMeetingById(meetingId: UUID): MeetingDto? = transaction {
        Meetings.select { Meetings.id eq meetingId }
            .firstOrNull()
            ?.toMeetingDto()
    }

    fun getMeetingsForUser(userId: UUID): List<MeetingDto> = transaction {
        val meetingIds = MeetingParticipants
            .select { MeetingParticipants.userId eq userId }
            .map { it[MeetingParticipants.meetingId] }

        Meetings.select { Meetings.id inList meetingIds }
            .orderBy(Meetings.scheduledAt, SortOrder.ASC)
            .map { it.toMeetingDto() }
    }

    fun getMeetingsForConversation(conversationId: UUID): List<MeetingDto> = transaction {
        Meetings.select { Meetings.conversationId eq conversationId }
            .orderBy(Meetings.scheduledAt, SortOrder.ASC)
            .map { it.toMeetingDto() }
    }

    fun respondToMeeting(meetingId: UUID, userId: UUID, accept: Boolean): Boolean = transaction {
        val updated = MeetingParticipants.update({
            (MeetingParticipants.meetingId eq meetingId) and (MeetingParticipants.userId eq userId)
        }) {
            it[status] = if (accept) "accepted" else "declined"
            it[respondedAt] = Instant.now()
        }
        updated > 0
    }

    fun updateMeetingStatus(meetingId: UUID, status: String): Boolean = transaction {
        val updated = Meetings.update({ Meetings.id eq meetingId }) {
            it[Meetings.status] = status
            it[updatedAt] = Instant.now()
        }
        updated > 0
    }

    fun deleteMeeting(meetingId: UUID): Boolean = transaction {
        MeetingParticipants.deleteWhere { MeetingParticipants.meetingId eq meetingId }
        Meetings.deleteWhere { Meetings.id eq meetingId } > 0
    }

    fun getParticipants(meetingId: UUID): List<MeetingParticipantDto> = transaction {
        (MeetingParticipants innerJoin UserTable)
            .select { MeetingParticipants.meetingId eq meetingId }
            .map {
                MeetingParticipantDto(
                    oderId = it[MeetingParticipants.userId].toString(),
                    email = it[UserTable.email],
                    displayName = it[UserTable.displayName],
                    status = it[MeetingParticipants.status]
                )
            }
    }

    private fun ResultRow.toMeetingDto() = MeetingDto(
        id = this[Meetings.id].value.toString(),
        conversationId = this[Meetings.conversationId]?.toString(),
        creatorId = this[Meetings.creatorId].toString(),
        title = this[Meetings.title],
        description = this[Meetings.description],
        scheduledAt = this[Meetings.scheduledAt].toString(),
        location = this[Meetings.location],
        status = this[Meetings.status],
        aiGenerated = this[Meetings.aiGenerated],
        createdAt = this[Meetings.createdAt].toString(),
        updatedAt = this[Meetings.updatedAt].toString()
    )
}

@Serializable
data class MeetingDto(
    val id: String,
    val conversationId: String?,
    val creatorId: String,
    val title: String,
    val description: String?,
    val scheduledAt: String,
    val location: String?,
    val status: String,
    val aiGenerated: Boolean,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MeetingParticipantDto(
    val oderId: String,
    val email: String,
    val displayName: String?,
    val status: String
)

@Serializable
data class CreateMeetingRq(
    val conversationId: String,
    val title: String,
    val description: String? = null,
    val scheduledAt: String,
    val location: String? = null
)

@Serializable
data class MeetingResponseRq(
    val accept: Boolean
)
