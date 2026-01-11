package com.example.schema

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.ReferenceOption

/**
 * Таблица для хранения FCM токенов устройств пользователей
 */
object DeviceTokens : UUIDTable("device_tokens") {
    val userId = reference("user_id", UserTable, onDelete = ReferenceOption.CASCADE)
    val token = varchar("token", 512).uniqueIndex()
    val platform = varchar("platform", 20) // "android", "ios", "web"
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp())
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp())
}
