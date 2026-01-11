package com.example.services

import com.example.schema.DeviceTokens
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID

class DeviceTokenService {

    /**
     * Сохраняет или обновляет FCM токен устройства для пользователя
     */
    fun saveToken(userId: UUID, token: String, platform: String): Boolean = transaction {
        // Проверяем, существует ли токен
        val existing = DeviceTokens.select { DeviceTokens.token eq token }.singleOrNull()

        if (existing != null) {
            // Обновляем userId если токен уже существует (переключение аккаунтов)
            DeviceTokens.update({ DeviceTokens.token eq token }) {
                it[DeviceTokens.userId] = userId
                it[DeviceTokens.platform] = platform
                it[updatedAt] = Instant.now()
            }
        } else {
            // Создаём новую запись
            DeviceTokens.insert {
                it[DeviceTokens.userId] = userId
                it[DeviceTokens.token] = token
                it[DeviceTokens.platform] = platform
            }
        }
        true
    }

    /**
     * Удаляет токен (при логауте)
     */
    fun removeToken(token: String): Boolean = transaction {
        DeviceTokens.deleteWhere { DeviceTokens.token eq token } > 0
    }

    /**
     * Получает все токены пользователя
     */
    fun getTokensForUser(userId: UUID): List<String> = transaction {
        DeviceTokens.select { DeviceTokens.userId eq userId }
            .map { it[DeviceTokens.token] }
    }

    /**
     * Получает токены для списка пользователей (для отправки в чат)
     */
    fun getTokensForUsers(userIds: List<UUID>): Map<UUID, List<String>> = transaction {
        DeviceTokens.select { DeviceTokens.userId inList userIds }
            .groupBy { it[DeviceTokens.userId].value }
            .mapValues { entry -> entry.value.map { it[DeviceTokens.token] } }
    }
}
