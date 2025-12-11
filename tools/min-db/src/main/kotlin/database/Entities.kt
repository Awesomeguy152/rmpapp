package database

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.datetime
import java.util.*
Запуск (3/4) Сопоставить с базой данных

Запуск (4/4) Сформировать список оставшихся задач

Завершено (4/4) Сформировать список оставшихся задач

Пробежалась по всем трём слоям и составила перечень того, что ещё нужно довести, чтобы мобильный клиент, Ktor-бэкенд и Postgres-база работали как единое приложение и можно было полностью тестировать.

Краткий план
Подвести итоги по текущему состоянию каждого слоя (бэк → фронт → база).
Выявить критичные разрывы, мешающие end-to-end сценарию.
Сформировать чеклист задач с приоритетами и подсказками по реализации.
Состояние сейчас
Backend (Ktor)
Реализованы полноценные REST/WebSocket ручки для auth, чатов, сообщений, поиска, тегирования, редактирования, read markers, а также заглушки AI.
Внутренний ChatService + ChatEventBroadcaster покрывают основные сценарии, но ws-события сейчас шлют только message_created.


enum class UserRole { ADMIN, USER }

object Users : UUIDTable("users") {
    val email = varchar("email", 255).uniqueIndex()
    val passwordHash = varchar("password_hash", 255)
    val role = enumerationByName("role", 16, UserRole::class).default(UserRole.USER)
    val displayName = varchar("display_name", 100).nullable()
    val createdAt = datetime("created_at")
}

enum class ConversationType { DIRECT, GROUP }

object Conversations : UUIDTable("conversations") {
    val type = enumerationByName("type", 16, ConversationType::class)
    val topic = varchar("topic", 255).nullable()
    val directKey = varchar("direct_key", 73).nullable().uniqueIndex()
    val createdBy = reference("created_by", Users)
    val createdAt = datetime("created_at")
}

object ConversationMembers : Table("conversation_members") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val joinedAt = datetime("joined_at")

    override val primaryKey = PrimaryKey(conversationId, userId, name = "PK_CONVERSATION_MEMBERS")
}

enum class MessageTag { NONE, ANSWER, MEETING, IMPORTANT }

object Messages : UUIDTable("messages") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val senderId = reference("sender_id", Users, onDelete = ReferenceOption.CASCADE)
    val body = text("body")
    val tag = enumerationByName("tag", 16, MessageTag::class).default(MessageTag.NONE)
    val createdAt = datetime("created_at")
    val editedAt = datetime("edited_at").nullable()
    val deletedAt = datetime("deleted_at").nullable()
}

object MessageAttachments : UUIDTable("message_attachments") {
    val messageId = reference("message_id", Messages, onDelete = ReferenceOption.CASCADE)
    val fileName = varchar("file_name", 255)
    val contentType = varchar("content_type", 128)
    val dataBase64 = text("data_base64")
    val createdAt = datetime("created_at")
}

object ConversationReadMarkers : Table("conversation_read_markers") {
    val conversationId = reference("conversation_id", Conversations, onDelete = ReferenceOption.CASCADE)
    val userId = reference("user_id", Users, onDelete = ReferenceOption.CASCADE)
    val lastReadMessageId = reference("last_read_message_id", Messages, onDelete = ReferenceOption.SET_NULL).nullable()
    val lastReadAt = datetime("last_read_at")

    override val primaryKey = PrimaryKey(conversationId, userId, name = "PK_CONVERSATION_READ_MARKERS")
}

class User(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<User>(Users)

    var email by Users.email
    var passwordHash by Users.passwordHash
    var role by Users.role
    var displayName by Users.displayName
    var createdAt by Users.createdAt

    val conversations by Conversation via ConversationMembers
    val sentMessages by Message referrersOn Messages.senderId
}

class Conversation(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Conversation>(Conversations)

    var type by Conversations.type
    var topic by Conversations.topic
    var directKey by Conversations.directKey
    var createdBy by User referencedOn Conversations.createdBy
    var createdAt by Conversations.createdAt

    val members by User via ConversationMembers
    val messages by Message referrersOn Messages.conversationId
}

class Message(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<Message>(Messages)

    var conversation by Conversation referencedOn Messages.conversationId
    var sender by User referencedOn Messages.senderId
    var body by Messages.body
    var tag by Messages.tag
    var createdAt by Messages.createdAt
    var editedAt by Messages.editedAt
    var deletedAt by Messages.deletedAt
}