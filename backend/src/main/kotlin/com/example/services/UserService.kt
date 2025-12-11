package com.example.services

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.schema.Role
import com.example.schema.UserDTO
import com.example.schema.UserProfileDTO
import com.example.schema.UserTable
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.stringParam
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class UserService {
    fun create(email: String, rawPassword: String, role: Role): UserDTO {
        val hash = BCrypt.withDefaults().hashToString(12, rawPassword.toCharArray())
        val id = transaction {
            val exists = UserTable.select { UserTable.email eq email }.any()
            require(!exists) { "User already exists" }

            UserTable.insertAndGetId {
                it[UserTable.email] = email
                it[UserTable.passwordHash] = hash
                it[UserTable.role] = role.name
            }.value
        }
        return UserDTO(id.toString(), email, role)
    }

    fun verifyAndGet(email: String, rawPassword: String): UserDTO? = transaction {
        UserTable.select { UserTable.email eq email }.singleOrNull()?.let { row ->
            val ok = BCrypt.verifyer()
                .verify(rawPassword.toCharArray(), row[UserTable.passwordHash])
                .verified
            if (ok) UserDTO(
                row[UserTable.id].value.toString(),
                row[UserTable.email],
                Role.valueOf(row[UserTable.role])
            ) else null
        }
    }

    fun list(): List<UserDTO> = transaction {
        UserTable.selectAll().map { row ->
            UserDTO(
                row[UserTable.id].value.toString(),
                row[UserTable.email],
                Role.valueOf(row[UserTable.role]))
        }
    }

    fun findProfile(userId: UUID): UserProfileDTO? = transaction {
        UserTable
            .select { UserTable.id eq EntityID(userId, UserTable) }
            .singleOrNull()
            ?.toProfile()
    }

    fun searchContacts(requesterId: UUID, query: String?, limit: Int): List<UserProfileDTO> = transaction {
        val base = UserTable.select { UserTable.id neq EntityID(requesterId, UserTable) }
        val searchQuery = query?.trim().takeUnless { it.isNullOrEmpty() }

        val filtered = if (searchQuery != null) {
            val pattern = "%$searchQuery%"
            base.andWhere { UserTable.email.ilike(pattern) }
        } else {
            base
        }

        filtered
            .orderBy(UserTable.createdAt, SortOrder.DESC)
            .limit(limit.coerceIn(1, 100))
            .map { it.toProfile() }
    }

    private fun ResultRow.toProfile(): UserProfileDTO = UserProfileDTO(
        id = this[UserTable.id].value.toString(),
        email = this[UserTable.email],
        role = Role.valueOf(this[UserTable.role]),
        createdAt = this[UserTable.createdAt].toString()
    )

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
