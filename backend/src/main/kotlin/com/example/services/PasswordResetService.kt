package com.example.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.schema.PasswordResetTokens
import com.example.schema.UserTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.security.SecureRandom
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class PasswordResetService(
    private val userService: UserService,
    private val mailService: MailService
) {
    private val random = SecureRandom()

    private fun newToken(): Pair<String, String> {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val hash = BCrypt.withDefaults().hashToString(10, token.toCharArray())
        return token to hash
    }
    
    private fun newCode(): Pair<String, String> {
        val code = (100000 + random.nextInt(900000)).toString()
        val hash = BCrypt.withDefaults().hashToString(10, code.toCharArray())
        return code to hash
    }

    fun requestReset(email: String, ttlMinutes: Long = 30): String? {
        return transaction {
            val user = UserTable
                .select { UserTable.email eq email.lowercase() }
                .singleOrNull()
                ?: return@transaction null

            val (token, hash) = newToken()

            PasswordResetTokens.insert {
                it[userId] = user[UserTable.id]
                it[tokenHash] = hash
                it[expiresAt] = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES)
            }

            token
        }
    }
    
    fun requestResetWithCode(email: String, ttlMinutes: Long = 15): Boolean {
        val code = transaction {
            val user = UserTable
                .select { UserTable.email eq email.lowercase() }
                .singleOrNull()
                ?: return@transaction null
            
            // Удаляем старые неиспользованные коды
            PasswordResetTokens.deleteWhere { 
                (PasswordResetTokens.userId eq user[UserTable.id])
            }

            val (code, hash) = newCode()

            PasswordResetTokens.insert {
                it[userId] = user[UserTable.id]
                it[tokenHash] = hash
                it[expiresAt] = Instant.now().plus(ttlMinutes, ChronoUnit.MINUTES)
            }

            code
        }
        
        if (code != null) {
            mailService.sendPasswordResetCode(email, code)
            return true
        }
        return false
    }
    
    fun verifyCode(email: String, code: String): Boolean {
        return transaction {
            val user = UserTable
                .select { UserTable.email eq email.lowercase() }
                .singleOrNull()
                ?: return@transaction false

            val candidate = PasswordResetTokens
                .select { PasswordResetTokens.userId eq user[UserTable.id] }
                .orderBy(PasswordResetTokens.createdAt, SortOrder.DESC)
                .firstOrNull()
                ?: return@transaction false

            val used = candidate[PasswordResetTokens.usedAt] != null
            val expired = candidate[PasswordResetTokens.expiresAt].isBefore(Instant.now())
            if (used || expired) return@transaction false

            BCrypt.verifyer()
                .verify(code.toCharArray(), candidate[PasswordResetTokens.tokenHash])
                .verified
        }
    }

    fun resetPassword(email: String, token: String, newPassword: String): Boolean {
        return transaction {
            val user = UserTable
                .select { UserTable.email eq email.lowercase() }
                .singleOrNull()
                ?: return@transaction false

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

            val newHash = BCrypt.withDefaults().hashToString(12, newPassword.toCharArray())
            UserTable.update({ UserTable.id eq user[UserTable.id] }) {
                it[passwordHash] = newHash
            }

            PasswordResetTokens.update({ PasswordResetTokens.id eq candidate[PasswordResetTokens.id].value }) {
                it[usedAt] = Instant.now()
            }

            true
        }
    }
}
