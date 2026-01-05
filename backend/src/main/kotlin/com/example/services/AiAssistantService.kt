package com.example.services

import com.example.schema.Messages
import com.example.schema.UserTable
import com.example.schema.ConversationMembers
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

class AiAssistantService {
    
    private val openAiApiKey = System.getenv("OPENAI_API_KEY")
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    /**
     * Анализирует последние сообщения и извлекает информацию о встречах
     */
    suspend fun extractMeetings(conversationId: UUID): List<ExtractedMeeting> {
        val messages = getRecentMessages(conversationId, limit = 50)
        if (messages.isEmpty()) return emptyList()
        
        if (openAiApiKey.isNullOrBlank()) {
            // Fallback: простой паттерн-матчинг без AI
            return extractMeetingsWithPatterns(messages)
        }
        
        return extractMeetingsWithAi(messages)
    }

    private suspend fun extractMeetingsWithAi(messages: List<MessageContext>): List<ExtractedMeeting> {
        val messagesText = messages.joinToString("\n") { 
            "[${it.senderName}]: ${it.content}" 
        }
        
        val prompt = """
            Analyze the following chat messages and extract any meetings or appointments being discussed.
            For each meeting found, extract:
            - title: Brief title for the meeting
            - description: What the meeting is about
            - dateTime: When the meeting should happen (ISO 8601 format, if mentioned)
            - location: Where the meeting will take place (if mentioned)
            - confidence: How confident you are this is a real meeting proposal (0.0 to 1.0)
            
            Return JSON array. If no meetings found, return empty array [].
            Only include meetings that seem like actual proposals or confirmations, not hypothetical discussions.
            
            Current date: ${LocalDateTime.now().format(DateTimeFormatter.ISO_DATE)}
            
            Messages:
            $messagesText
            
            Response (JSON only):
        """.trimIndent()

        try {
            val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $openAiApiKey")
                contentType(ContentType.Application.Json)
                setBody(OpenAiRequest(
                    model = "gpt-3.5-turbo",
                    messages = listOf(
                        OpenAiMessage("system", "You are a helpful assistant that extracts meeting information from chat messages. Respond only with valid JSON."),
                        OpenAiMessage("user", prompt)
                    ),
                    temperature = 0.3
                ))
            }
            
            val result: OpenAiResponse = response.body()
            val content = result.choices.firstOrNull()?.message?.content ?: return emptyList()
            
            // Parse JSON response
            return parseAiMeetings(content)
        } catch (e: Exception) {
            e.printStackTrace()
            return extractMeetingsWithPatterns(messages)
        }
    }

    private fun extractMeetingsWithPatterns(messages: List<MessageContext>): List<ExtractedMeeting> {
        val meetings = mutableListOf<ExtractedMeeting>()
        
        // Паттерны для поиска встреч
        val meetingPatterns = listOf(
            Regex("""(?i)(встреч|meeting|созвон|звонок|call).*?(\d{1,2}[.:]\d{2}|\d{1,2}\s*(часов|час|ч|am|pm))"""),
            Regex("""(?i)(давай|let'?s|можем|можно).*?(встретимся|meet|созвонимся|call)"""),
            Regex("""(?i)(завтра|сегодня|tomorrow|today|понедельник|вторник|среда|четверг|пятница|суббота|воскресенье).*?(\d{1,2}[.:]\d{2}|\d{1,2}\s*(часов|час))""")
        )
        
        for (msg in messages) {
            for (pattern in meetingPatterns) {
                if (pattern.containsMatchIn(msg.content)) {
                    meetings.add(ExtractedMeeting(
                        title = "Встреча",
                        description = msg.content.take(200),
                        dateTime = null, // Нужен более сложный парсинг
                        location = null,
                        confidence = 0.6,
                        sourceMessageId = msg.messageId
                    ))
                    break
                }
            }
        }
        
        return meetings.distinctBy { it.description }
    }

    private fun parseAiMeetings(jsonContent: String): List<ExtractedMeeting> {
        return try {
            // Очищаем от markdown
            val cleanJson = jsonContent
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            Json.decodeFromString<List<ExtractedMeeting>>(cleanJson)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getRecentMessages(conversationId: UUID, limit: Int): List<MessageContext> = transaction {
        (Messages innerJoin UserTable)
            .select { Messages.conversation eq conversationId }
            .orderBy(Messages.createdAt, SortOrder.DESC)
            .limit(limit)
            .map {
                MessageContext(
                    messageId = it[Messages.id].value.toString(),
                    content = it[Messages.body],
                    senderName = it[UserTable.displayName] ?: it[UserTable.email],
                    sentAt = it[Messages.createdAt].toString()
                )
            }
            .reversed()
    }

    fun summarizeConversation(conversationId: UUID): AiSummary = AiSummary(
        conversationId = conversationId.toString(),
        summary = "AI summary is not configured yet. Add OPENAI_API_KEY to enable.",
        generatedAt = Instant.now().toString()
    )

    fun suggestNextAction(conversationId: UUID): AiNextAction = AiNextAction(
        conversationId = conversationId.toString(),
        suggestions = listOf(
            "Review recent messages for action items",
            "Check if any meetings need to be scheduled"
        ),
        generatedAt = Instant.now().toString()
    )
}

@Serializable
data class ExtractedMeeting(
    val title: String,
    val description: String?,
    val dateTime: String?,
    val location: String?,
    val confidence: Double,
    val sourceMessageId: String? = null
)

@Serializable
data class MessageContext(
    val messageId: String,
    val content: String,
    val senderName: String,
    val sentAt: String
)

@Serializable
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAiMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAiResponse(
    val choices: List<OpenAiChoice>
)

@Serializable
data class OpenAiChoice(
    val message: OpenAiMessage
)

@Serializable
data class AiSummary(
    val conversationId: String,
    val summary: String,
    val generatedAt: String
)

@Serializable
data class AiNextAction(
    val conversationId: String,
    val suggestions: List<String>,
    val generatedAt: String
)