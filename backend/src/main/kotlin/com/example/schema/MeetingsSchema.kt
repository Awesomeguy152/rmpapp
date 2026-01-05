package com.example.schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp

/**
 * Таблица встреч, предложенных AI или созданных вручную
 */
object Meetings : UUIDTable("meetings") {
    val conversationId = uuid("conversation_id").references(Conversations.id).nullable()
    val creatorId = uuid("creator_id").references(UserTable.id)
    val title = varchar("title", 255)
    val description = text("description").nullable()
    val scheduledAt = timestamp("scheduled_at")
    val location = varchar("location", 255).nullable()
    val status = varchar("status", 32).default("pending") // pending, confirmed, cancelled
    val aiGenerated = bool("ai_generated").default(false)
    val sourceMessageId = uuid("source_message_id").references(Messages.id).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}

/**
 * Участники встречи
 */
object MeetingParticipants : UUIDTable("meeting_participants") {
    val meetingId = uuid("meeting_id").references(Meetings.id)
    val userId = uuid("user_id").references(UserTable.id)
    val status = varchar("status", 32).default("pending") // pending, accepted, declined
    val respondedAt = timestamp("responded_at").nullable()
}
