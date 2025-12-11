package database

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.LocalDateTime

object TestData {
    fun insertTestData() {
        transaction {
            ConversationReadMarkers.deleteAll()
            MessageAttachments.deleteAll()
            Messages.deleteAll()
            ConversationMembers.deleteAll()
            Conversations.deleteAll()
            Users.deleteAll()

            val anna = User.new {
                email = "anyabelousova2005@gmail.com"
                passwordHash = "password"
                role = UserRole.USER
                displayName = "Anna"
                createdAt = LocalDateTime.now().minusDays(5)
            }

            val olga = User.new {
                email = "kuzmina.o3004@gmail.com"
                passwordHash = "password"
                role = UserRole.USER
                displayName = "Olga"
                createdAt = LocalDateTime.now().minusDays(5)
            }

            val vlad = User.new {
                email = "zaimenkovladimir@gmail.com"
                passwordHash = "password"
                role = UserRole.USER
                displayName = "Vlad"
                createdAt = LocalDateTime.now().minusDays(4)
            }

            val maxim = User.new {
                email = "maxim2005@gmail.com"
                passwordHash = "password"
                role = UserRole.USER
                displayName = "Maxim"
                createdAt = LocalDateTime.now().minusDays(4)
            }

            val andrey = User.new {
                email = "andrey1990@gmail.com"
                passwordHash = "password"
                role = UserRole.USER
                displayName = "Andrey"
                createdAt = LocalDateTime.now().minusDays(3)
            }

            val rmpConversation = Conversation.new {
                type = ConversationType.GROUP
                topic = "RMP"
                directKey = null
                createdBy = anna
                createdAt = LocalDateTime.now().minusDays(2)
            }

            val privateConversation = Conversation.new {
                type = ConversationType.DIRECT
                topic = "Приватный чат 1"
                directKey = listOf(anna.id.value, olga.id.value)
                    .sortedBy { it.toString() }
                    .joinToString(":")
                createdBy = anna
                createdAt = LocalDateTime.now().minusDays(2)
            }

            val now = LocalDateTime.now()

            val rmpMembers = listOf(anna, olga, vlad)
            rmpMembers.forEach { member ->
                ConversationMembers.insert {
                    it[conversationId] = rmpConversation.id
                    it[userId] = member.id
                    it[joinedAt] = now.minusDays(2)
                }
            }

            val privateMembers = listOf(anna, olga)
            privateMembers.forEach { member ->
                ConversationMembers.insert {
                    it[conversationId] = privateConversation.id
                    it[userId] = member.id
                    it[joinedAt] = now.minusDays(2)
                }
            }

            val baseTime = LocalDateTime.now().minusDays(1)

            val groupMessages = listOf(
                Triple(anna, "Всем привет! Когда будет созвон?", 0),
                Triple(olga, "Привет, давайте в 18:00", 2),
                Triple(vlad, "Всем привет, я не могу в 18, давайте в 19", 3),
                Triple(anna, "Хорошо, тогда в 19:00", 10),
                Triple(olga, "Поняла, до встречи", 12),
                Triple(vlad, "Отлично", 13)
            )

            val groupMessageEntities = groupMessages.map { (user, content, delayMinutes) ->
                Message.new {
                    conversation = rmpConversation
                    sender = user
                    body = content
                    tag = MessageTag.NONE
                    createdAt = baseTime.plusMinutes(delayMinutes.toLong())
                    editedAt = null
                    deletedAt = null
                }
            }

            val privateMessages = listOf(
                Triple(anna, "Привет, как дела?", 0),
                Triple(olga, "Привет! Все хорошо, делаю проект", 1),
                Triple(anna, "Я тоже", 3),
                Triple(olga, "Супер, есть вопросы какие-то?", 4),
                Triple(anna, "Пока нет", 5)
            )

            privateMessages.forEach { (user, content, delayMinutes) ->
                Message.new {
                    conversation = privateConversation
                    sender = user
                    body = content
                    tag = MessageTag.NONE
                    createdAt = baseTime.plusMinutes(delayMinutes.toLong())
                    editedAt = null
                    deletedAt = null
                }
            }

            groupMessageEntities.forEachIndexed { index, message ->
                val readers = when (index) {
                    in 0..2 -> listOf(olga, vlad)
                    else -> listOf(vlad)
                }
                readers.forEach { reader ->
                    ConversationReadMarkers.insert {
                        it[conversationId] = rmpConversation.id
                        it[userId] = reader.id
                        it[lastReadMessageId] = message.id
                        it[lastReadAt] = message.createdAt
                    }
                }
            }
        }
    }

}