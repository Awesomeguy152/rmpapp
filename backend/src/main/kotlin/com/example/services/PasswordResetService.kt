package com.example.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.schema.PasswordResetTokens
import com.example.schema.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.Base64

class PasswordResetService(
    private val userService: UserService,
    private val mailService: MailService
) {
    private val random = SecureRandom()

    // Генерируем криптостойкий токен. Храним ХЭШ, отправляем “сырой”
    private fun newToken(): Pair<String, String> {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash  = BCrypt.withDefaults().hashToString(10, token.toCharArray())
        return token to hash
    }

    /**
     * Старт процесса: создаём запись токена в БД.
     * Возвращаем «сырой» токен ИЛИ null, если email не найден.
     * Письмо отправляем СНАРУЖИ, после транзакции.
     */
    fun requestReset(email: String, ttlMinutes: Long = 30): String? {
        return transaction {
            val user = UserTable
                .select { UserTable.email eq email }
                .singleOrNull()
                ?: return@transaction null

            val (token, hash) = newToken()

            PasswordResetTokens.insert {
                // ВАЖНО: reference(...) ожидает EntityID<UUID>, row[UserTable.id] уже EntityID
                it[userId]    = user[UserTable.id]
                it[tokenHash] = hash
                it[expiresAt] = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES)
            }

            token // вернём «сырой» токен наружу
        }
    }

    /**
     * Подтверждение: сверяем токен, меняем пароль, помечаем токен использованным.
     */
    fun resetPassword(email: String, token: String, newPassword: String): Boolean {
        return transaction {
            val user = UserTable
                .select { UserTable.email eq email }
                .singleOrNull()
                ?: return@transaction false

            // Берём самую свежую запись для этого пользователя
            val candidate = PasswordResetTokens
                .select { PasswordResetTokens.userId eq user[UserTable.id] }
                .orderBy(PasswordResetTokens.createdAt, SortOrder.DESC)
                .firstOrNull()
                ?: return@transaction false

            val used = candidate[PasswordResetTokens.usedAt] != null
            val expired = candidate[PasswordResetTokens.expiresAt].isBefore(Instant.now())
            if (used || expired) return@transaction false

            val ok = BCrypt.verifyer()
                .verify(token.toCharArray(), candidate[PasswordResetTokens.tokenHash])
                .verified
            if (!ok) return@transaction false

            // Обновляем пароль
            val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
            UserTable.update({ UserTable.id eq user[UserTable.id] }) {
                it[passwordHash] = newHash
            }

            // Помечаем токен использованным
            PasswordResetTokens.update({ PasswordResetTokens.id eq candidate[PasswordResetTokens.id].value }) {
                it[usedAt] = Instant.now()
            }

            true
        }
    }
}
