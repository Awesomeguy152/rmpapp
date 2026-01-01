package com.example.services

import com.example.schema.*
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatServiceTest {

    private val chatService = ChatService()

    @BeforeTest
    fun setup() {
        Database.connect(
            url = "jdbc:h2:mem:chat-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver"
        )

        transaction {
            SchemaUtils.create(
                UserTable,
                PasswordResetTokens,
                Conversations,
                ConversationMembers,
                Messages,
                MessageAttachments,
                ConversationReadMarkers
            )
        }
    }

    @AfterTest
    fun tearDown() {
        transaction {
            SchemaUtils.drop(
                MessageAttachments,
                ConversationReadMarkers,
                Messages,
                ConversationMembers,
                Conversations,
                PasswordResetTokens,
                UserTable
            )
        }
    }

    @Test
    fun `creates direct conversation once and reuses it`() {
        val initiator = createUser("initiator@example.com")
        val peer = createUser("peer@example.com")

        val first = chatService.createDirectConversation(initiator, peer, topic = "Project Alpha")
        val second = chatService.createDirectConversation(initiator, peer)

        assertEquals(first.id, second.id)
        assertEquals("Project Alpha", first.topic)

        transaction {
            val memberCount = ConversationMembers
                .select { ConversationMembers.conversation eq EntityID(UUID.fromString(first.id), Conversations) }
                .count()
            assertEquals(2L, memberCount)
        }
    }

    @Test
    fun `send message persists data and default tag`() {
        val initiator = createUser("user1@example.com")
        val peer = createUser("user2@example.com")
        val conversation = chatService.createDirectConversation(initiator, peer)
        val conversationId = UUID.fromString(conversation.id)

        val message = chatService.sendMessage(conversationId, initiator, "Hello there")

        assertEquals("Hello there", message.body)
        assertEquals(MessageTag.NONE, message.tag)
        assertEquals(conversation.id, message.conversationId)
    }

    @Test
    fun `list and search messages support tag and keyword filters`() {
        val initiator = createUser("owner@example.com")
        val peer = createUser("manager@example.com")
        val conversation = chatService.createDirectConversation(initiator, peer)
        val conversationId = UUID.fromString(conversation.id)

        chatService.sendMessage(conversationId, initiator, "Schedule a meeting for tomorrow")
        val answer = chatService.sendMessage(conversationId, peer, "Confirmed, meeting booked")
        chatService.tagMessage(UUID.fromString(answer.id), peer, MessageTag.MEETING)

        val allMessages = chatService.listMessages(conversationId, initiator, limit = 10, offset = 0)
        assertEquals(2, allMessages.size)

        val meetingOnly = chatService.listMessages(conversationId, initiator, limit = 10, offset = 0, tag = MessageTag.MEETING)
        assertEquals(1, meetingOnly.size)
        assertEquals(MessageTag.MEETING, meetingOnly.first().tag)

        val searchResults = chatService.searchMessages(initiator, tag = MessageTag.MEETING, search = "booked", limit = 10, offset = 0)
        assertEquals(1, searchResults.size)
        assertNotNull(searchResults.firstOrNull { it.id == answer.id })
    }

    @Test
    fun `conversation listing returns unread count and last message`() {
        val alice = createUser("alice@example.com")
        val bob = createUser("bob@example.com")
        val conversation = chatService.createDirectConversation(alice, bob)
        val conversationId = UUID.fromString(conversation.id)

        chatService.sendMessage(conversationId, alice, "First message")
        chatService.sendMessage(conversationId, bob, "Second message")

    val listForAlice = chatService.listConversations(alice, limit = 10, offset = 0L)
        val summary = listForAlice.first { it.id == conversation.id }

        assertEquals(1, summary.unreadCount) // only the message from Bob is unread
        assertEquals("Second message", summary.lastMessage?.body)

        val lastMessageId = UUID.fromString(summary.lastMessage!!.id)
        chatService.markConversationRead(conversationId, alice, lastMessageId)

    val afterRead = chatService.listConversations(alice, limit = 10, offset = 0L)
        assertEquals(0, afterRead.first { it.id == conversation.id }.unreadCount)
    }

    @Test
    fun `group conversation allows member management`() {
        val creator = createUser("owner@example.com")
        val memberA = createUser("memberA@example.com")
        val memberB = createUser("memberB@example.com")
        val extra = createUser("extra@example.com")

        val group = chatService.createGroupConversation(creator, listOf(memberA, memberB), topic = "Project")
        val groupId = UUID.fromString(group.id)

    val members = chatService.listConversations(creator, 10, 0L)
            .first { it.id == group.id }
            .members

        assertEquals(3, members.size)

        val updatedMembers = chatService.addMembers(groupId, creator, listOf(extra))
        assertEquals(4, updatedMembers.size)

        val afterRemoval = chatService.removeMember(groupId, creator, memberB)
        assertEquals(3, afterRemoval.size)
        assertTrue(afterRemoval.none { it.userId == memberB.toString() })
    }

    @Test
    fun `edit and delete message update metadata and attachments`() {
        val author = createUser("author@example.com")
        val peer = createUser("peer@example.com")
        val conversation = chatService.createDirectConversation(author, peer)
        val conversationId = UUID.fromString(conversation.id)

        val created = chatService.sendMessage(
            conversationId,
            author,
            body = "Original",
            attachments = listOf(ChatService.AttachmentInput("file.txt", "text/plain", "ZmlsZQ=="))
        )

        assertEquals(1, created.attachments.size)
        val messageId = UUID.fromString(created.id)

        val edited = chatService.editMessage(
            messageId,
            author,
            newBody = "Edited body",
            attachments = listOf(ChatService.AttachmentInput("new.txt", "text/plain", "bmV3"))
        )

        assertEquals("Edited body", edited.body)
        assertNotNull(edited.editedAt)
        assertEquals(1, edited.attachments.size)
        assertEquals("new.txt", edited.attachments.first().fileName)

        val deleted = chatService.deleteMessage(messageId, author)
        assertEquals("", deleted.body)
        assertNotNull(deleted.deletedAt)
        assertTrue(deleted.attachments.isEmpty())

    val messages = chatService.listMessages(conversationId, peer, limit = 10, offset = 0L)
        assertEquals(1, messages.size)
        assertEquals("", messages.first().body)
    }

    private fun createUser(email: String): UUID = transaction {
        UserTable.insertAndGetId {
            it[UserTable.email] = email
            it[passwordHash] = "hash"
            it[role] = Role.USER.name
        }.value
    }
}