package com.example.schema

import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

enum class Role { ADMIN, USER }

object UserTable : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = varchar("role", 16)
    val displayName = varchar("display_name", 100).nullable()
    val username = varchar("username", 32).nullable().uniqueIndex()
    val bio = varchar("bio", 255).nullable()
    val avatarUrl = varchar("avatar_url", 512).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
}

@Serializable
data class UserDTO(
    val id: String,
    val email: String,
    val role: Role,
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)

@Serializable
data class UserProfileDTO(
    val id: String,
    val email: String,
    val role: Role,
    val createdAt: String,
    val username: String? = null,
    val displayName: String? = null,
    val bio: String? = null,
    val avatarUrl: String? = null
)
