package com.example.schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object PasswordResetTokens : UUIDTable("password_reset_tokens") {
    val userId = reference("user_id", UserTable)
    val tokenHash = varchar("token_hash", 255)
    val expiresAt = timestamp("expires_at")
    val usedAt = timestamp("used_at").nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val ip = varchar("ip", 64).nullable()
    val userAgent = varchar("user_agent", 255).nullable()
}
