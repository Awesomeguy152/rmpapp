package com.example.database

import at.favre.lib.crypto.bcrypt.BCrypt
import com.example.schema.Conversations
import com.example.schema.MessageDTO
import com.example.schema.Role
import com.example.schema.UserTable
import com.example.services.ChatService
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.max

object SampleDataSeeder {
    private val logger = LoggerFactory.getLogger(SampleDataSeeder::class.java)
    private val chatService = ChatService()

    private data class SeedUser(
        val email: String,
        val password: String,
        val displayName: String,
        val role: Role = Role.USER
    )

    private data class SeedMessage(
        val senderEmail: String,
        val content: String
    )

    private data class SeedGroupConversation(
        val topic: String,
        val creatorEmail: String,
        val memberEmails: List<String>,
        val messages: List<SeedMessage>,
        val readReceipts: Map<String, Set<Int>>
    )

    private data class SeedDirectConversation(
        val topic: String?,
        val creatorEmail: String,
        val targetEmail: String,
        val messages: List<SeedMessage>
    )

    private val users = listOf(
        SeedUser("anyabelousova2005@gmail.com", "password", "Anna"),
        SeedUser("kuzmina.o3004@gmail.com", "password", "Olga"),
        SeedUser("zaimenkovladimir@gmail.com", "password", "Vlad"),
        SeedUser("maxim2005@gmail.com", "password", "Maxim"),
        SeedUser("andrey1990@gmail.com", "password", "Andrey")
    )

    private val groupConversation = SeedGroupConversation(
        topic = "RMP",
        creatorEmail = "anyabelousova2005@gmail.com",
        memberEmails = listOf("anyabelousova2005@gmail.com", "kuzmina.o3004@gmail.com", "zaimenkovladimir@gmail.com"),
        messages = listOf(
            SeedMessage("anyabelousova2005@gmail.com", "Всем привет! Когда будет созвон?"),
            SeedMessage("kuzmina.o3004@gmail.com", "Привет, давайте в 18:00"),
            SeedMessage("zaimenkovladimir@gmail.com", "Всем привет, я не могу в 18, давайте в 19"),
            SeedMessage("anyabelousova2005@gmail.com", "Хорошо, тогда в 19:00"),
            SeedMessage("kuzmina.o3004@gmail.com", "Поняла, до встречи"),
            SeedMessage("zaimenkovladimir@gmail.com", "Отлично")
        ),
        readReceipts = mapOf(
            "kuzmina.o3004@gmail.com" to setOf(0, 2, 3, 5),
            "zaimenkovladimir@gmail.com" to setOf(0, 1, 3, 4)
        )
    )

    private val directConversation = SeedDirectConversation(
        topic = "Приватный чат 1",
        creatorEmail = "anyabelousova2005@gmail.com",
        targetEmail = "kuzmina.o3004@gmail.com",
        messages = listOf(
            SeedMessage("anyabelousova2005@gmail.com", "Привет, как дела?"),
            SeedMessage("kuzmina.o3004@gmail.com", "Привет! Все хорошо, делаю проект"),
            SeedMessage("anyabelousova2005@gmail.com", "Я тоже"),
            SeedMessage("kuzmina.o3004@gmail.com", "Супер, есть вопросы какие-то?"),
            SeedMessage("anyabelousova2005@gmail.com", "Пока нет")
        )
    )

    fun seedIfEnabled(environment: io.ktor.server.application.ApplicationEnvironment) {
        val enabled = environment.config.propertyOrNull("sampleData.enabled")?.getString()?.toBooleanStrictOrNull()
            ?: System.getenv("SEED_SAMPLE_DATA")?.toBooleanStrictOrNull()
            ?: false

        if (!enabled) {
            logger.debug("Sample data seeding disabled")
            return
        }

        val hasConversations = transaction {
            Conversations.selectAll().limit(1).empty().not()
        }

        if (hasConversations) {
            logger.info("Skipping sample data seeding; conversations already present")
            return
        }

        val ids = seedUsers()
        val groupId = seedGroup(ids)
        seedDirect(ids)
        logger.info("Sample data seeding completed. Group conversation ID: {}", groupId)
    }

    private fun seedUsers(): Map<String, UUID> = transaction {
        val hasher = BCrypt.withDefaults()
        users.associate { user ->
            val existing = UserTable
                .select { UserTable.email eq user.email }
                .singleOrNull()

            val id = existing?.get(UserTable.id)?.value ?: run {
                val hash = hasher.hashToString(12, user.password.toCharArray())
                UserTable.insertAndGetId {
                    it[email] = user.email
                    it[passwordHash] = hash
                    it[role] = user.role.name
                    it[displayName] = user.displayName
                }.value
            }
            user.email to id
        }
    }

    private fun seedGroup(ids: Map<String, UUID>): UUID {
        val creatorId = requireNotNull(ids[groupConversation.creatorEmail])
        val memberIds = groupConversation.memberEmails.map { requireNotNull(ids[it]) }

        val conversation = chatService.createGroupConversation(
            creatorId,
            memberIds.filter { it != creatorId },
            topic = groupConversation.topic
        )
        val conversationId = UUID.fromString(conversation.id)

        val messageDtos = groupConversation.messages.map { message ->
            val senderId = requireNotNull(ids[message.senderEmail])
            chatService.sendMessage(conversationId, senderId, message.content)
        }

        groupConversation.readReceipts.forEach { (email, indexes) ->
            val userId = ids[email] ?: return@forEach
            val lastIndex = indexes.maxOrNull() ?: return@forEach
            if (lastIndex in messageDtos.indices) {
                val lastMessageId = UUID.fromString(messageDtos[lastIndex].id)
                chatService.markConversationRead(conversationId, userId, lastMessageId)
            }
        }

        return conversationId
    }

    private fun seedDirect(ids: Map<String, UUID>) {
        val creatorId = requireNotNull(ids[directConversation.creatorEmail])
        val targetId = requireNotNull(ids[directConversation.targetEmail])

        val conversation = chatService.createDirectConversation(creatorId, targetId, topic = directConversation.topic)
        val conversationId = UUID.fromString(conversation.id)

        val messages = mutableListOf<MessageDTO>()
        directConversation.messages.forEach { message ->
            val senderId = requireNotNull(ids[message.senderEmail])
            messages += chatService.sendMessage(conversationId, senderId, message.content)
        }

        val participants = listOf(creatorId to creatorId.toString(), targetId to targetId.toString())
        participants.forEach { (userId, _) ->
            val lastIndex = messages.indexOfLast { UUID.fromString(it.senderId) != userId }
            if (lastIndex >= 0) {
                val lastMessageId = UUID.fromString(messages[max(0, lastIndex)].id)
                chatService.markConversationRead(conversationId, userId, lastMessageId)
            }
        }
    }

    private fun <T> Iterable<T>.empty(): Boolean = !this.iterator().hasNext()
}
