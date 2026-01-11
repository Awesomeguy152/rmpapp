package com.example.services

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID

class AiAssistantService(
    private val chatService: ChatService? = null
) {
    private val openAiApiKey = System.getenv("OPENAI_API_KEY") ?: ""
    private val openAiModel = System.getenv("OPENAI_MODEL") ?: "gpt-4o-mini"
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json { 
                ignoreUnknownKeys = true 
                encodeDefaults = true
            })
        }
    }
    
    private val isConfigured: Boolean get() = openAiApiKey.isNotBlank()

    suspend fun summarizeConversation(conversationId: UUID, requesterId: UUID? = null): AiSummary {
        if (!isConfigured) {
            return AiSummary(
                conversationId = conversationId.toString(),
                summary = "AI не настроен. Установите переменную окружения OPENAI_API_KEY.",
                generatedAt = Instant.now().toString()
            )
        }
        
        // Get conversation messages if ChatService is available
        val messagesText = if (chatService != null && requesterId != null) {
            try {
                val messages = chatService.listMessages(conversationId, requesterId, limit = 50, offset = 0)
                messages.joinToString("\n") { "${it.senderId.take(8)}: ${it.body}" }
            } catch (e: Exception) {
                "Не удалось загрузить сообщения: ${e.message}"
            }
        } else {
            "Сообщения недоступны"
        }
        
        val prompt = """
            Проанализируй следующую переписку и сделай краткое резюме (2-3 предложения) на русском языке.
            Выдели ключевые темы и важные договоренности.
            
            Переписка:
            $messagesText
        """.trimIndent()
        
        val response = callOpenAI(prompt)
        
        return AiSummary(
            conversationId = conversationId.toString(),
            summary = response,
            generatedAt = Instant.now().toString()
        )
    }

    suspend fun suggestNextAction(conversationId: UUID, requesterId: UUID? = null): AiNextAction {
        if (!isConfigured) {
            return AiNextAction(
                conversationId = conversationId.toString(),
                suggestions = listOf("AI не настроен. Установите переменную окружения OPENAI_API_KEY."),
                generatedAt = Instant.now().toString()
            )
        }
        
        val messagesText = if (chatService != null && requesterId != null) {
            try {
                val messages = chatService.listMessages(conversationId, requesterId, limit = 20, offset = 0)
                messages.takeLast(10).joinToString("\n") { "${it.senderId.take(8)}: ${it.body}" }
            } catch (e: Exception) {
                "Не удалось загрузить сообщения"
            }
        } else {
            "Сообщения недоступны"
        }
        
        val prompt = """
            На основе последних сообщений переписки, предложи 2-3 возможных следующих действия или ответа.
            Отвечай на русском языке. Каждое предложение должно быть кратким (1 предложение).
            Формат ответа: каждое предложение с новой строки.
            
            Последние сообщения:
            $messagesText
        """.trimIndent()
        
        val response = callOpenAI(prompt)
        val suggestions = response.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.removePrefix("- ").removePrefix("• ").trim() }
            .take(3)
        
        return AiNextAction(
            conversationId = conversationId.toString(),
            suggestions = suggestions.ifEmpty { listOf("Нет предложений") },
            generatedAt = Instant.now().toString()
        )
    }
    
    suspend fun generateSmartReply(conversationId: UUID, requesterId: UUID): List<String> {
        if (!isConfigured) return emptyList()
        
        val messagesText = if (chatService != null) {
            try {
                val messages = chatService.listMessages(conversationId, requesterId, limit = 10, offset = 0)
                messages.takeLast(5).joinToString("\n") { 
                    val isMe = it.senderId == requesterId.toString()
                    "${if (isMe) "Я" else "Собеседник"}: ${it.body}" 
                }
            } catch (e: Exception) {
                return emptyList()
            }
        } else {
            return emptyList()
        }
        
        val prompt = """
            На основе переписки, предложи 3 коротких варианта ответа (каждый до 10 слов).
            Ответы должны быть естественными и соответствовать контексту.
            Формат: каждый вариант с новой строки, без нумерации.
            
            Переписка:
            $messagesText
        """.trimIndent()
        
        val response = callOpenAI(prompt)
        return response.split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .take(3)
    }
    
    private suspend fun callOpenAI(prompt: String): String {
        return try {
            val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $openAiApiKey")
                contentType(ContentType.Application.Json)
                setBody(OpenAIRequest(
                    model = openAiModel,
                    messages = listOf(
                        OpenAIMessage(role = "system", content = "Ты полезный ассистент в мессенджере. Отвечай кратко и по делу на русском языке."),
                        OpenAIMessage(role = "user", content = prompt)
                    ),
                    maxTokens = 500,
                    temperature = 0.7
                ))
            }
            
            val result: OpenAIResponse = response.body()
            result.choices.firstOrNull()?.message?.content ?: "Не удалось получить ответ от AI"
        } catch (e: Exception) {
            "Ошибка AI: ${e.message}"
        }
    }
    
    /**
     * Извлекает информацию о встречах из сообщений переписки
     */
    suspend fun extractMeetings(conversationId: UUID, requesterId: UUID? = null): List<ExtractedMeetingDto> {
        if (!isConfigured || chatService == null || requesterId == null) {
            return extractMeetingsWithPatterns(conversationId, requesterId)
        }
        
        val messages = try {
            chatService.listMessages(conversationId, requesterId, limit = 50, offset = 0)
        } catch (e: Exception) {
            return emptyList()
        }
        
        if (messages.isEmpty()) return emptyList()
        
        val messagesText = messages.joinToString("\n") { 
            "[${it.senderId.take(8)}]: ${it.body}" 
        }
        
        val prompt = """
            Проанализируй сообщения чата и найди упоминания о встречах, созвонах или встречах.
            Для каждой найденной встречи извлеки:
            - title: Краткое название встречи
            - description: О чём встреча
            - dateTime: Когда встреча (в формате ISO 8601, если упоминается)
            - location: Где будет встреча (если упоминается)
            - confidence: Уверенность что это реальное предложение о встрече (от 0.0 до 1.0)
            
            Верни JSON массив. Если встреч не найдено - верни пустой массив [].
            Включай только реальные предложения или подтверждения встреч.
            
            Текущая дата: ${java.time.LocalDate.now()}
            
            Сообщения:
            $messagesText
            
            Ответ (только JSON):
        """.trimIndent()
        
        return try {
            val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
                header("Authorization", "Bearer $openAiApiKey")
                contentType(ContentType.Application.Json)
                setBody(OpenAIRequest(
                    model = openAiModel,
                    messages = listOf(
                        OpenAIMessage(role = "system", content = "Ты помощник, который извлекает информацию о встречах из сообщений чата. Отвечай только валидным JSON."),
                        OpenAIMessage(role = "user", content = prompt)
                    ),
                    maxTokens = 1000,
                    temperature = 0.3
                ))
            }
            
            val result: OpenAIResponse = response.body()
            val content = result.choices.firstOrNull()?.message?.content ?: return emptyList()
            
            parseExtractedMeetings(content)
        } catch (e: Exception) {
            extractMeetingsWithPatterns(conversationId, requesterId)
        }
    }
    
    private fun extractMeetingsWithPatterns(conversationId: UUID, requesterId: UUID?): List<ExtractedMeetingDto> {
        if (chatService == null || requesterId == null) return emptyList()
        
        val messages = try {
            chatService.listMessages(conversationId, requesterId, limit = 50, offset = 0)
        } catch (e: Exception) {
            return emptyList()
        }
        
        val meetings = mutableListOf<ExtractedMeetingDto>()
        
        val meetingPatterns = listOf(
            Regex("""(?i)(встреч|meeting|созвон|звонок|call).*?(\d{1,2}[.:]\d{2}|\d{1,2}\s*(часов|час|ч|am|pm))"""),
            Regex("""(?i)(давай|let'?s|можем|можно).*?(встретимся|meet|созвонимся|call)"""),
            Regex("""(?i)(завтра|сегодня|tomorrow|today).*?(\d{1,2}[.:]\d{2}|\d{1,2}\s*(часов|час))""")
        )
        
        for (msg in messages) {
            for (pattern in meetingPatterns) {
                if (pattern.containsMatchIn(msg.body)) {
                    meetings.add(ExtractedMeetingDto(
                        title = "Встреча",
                        description = msg.body.take(200),
                        dateTime = null,
                        location = null,
                        confidence = 0.6,
                        sourceMessageId = msg.id
                    ))
                    break
                }
            }
        }
        
        return meetings.distinctBy { it.description }
    }
    
    private fun parseExtractedMeetings(jsonContent: String): List<ExtractedMeetingDto> {
        return try {
            val cleanJson = jsonContent
                .replace("```json", "")
                .replace("```", "")
                .trim()
            
            Json.decodeFromString<List<ExtractedMeetingDto>>(cleanJson)
        } catch (e: Exception) {
            emptyList()
        }
    }
}

@Serializable
data class OpenAIRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    @SerialName("max_tokens") val maxTokens: Int = 500,
    val temperature: Double = 0.7
)

@Serializable
data class OpenAIMessage(
    val role: String,
    val content: String
)

@Serializable
data class OpenAIResponse(
    val id: String = "",
    val choices: List<OpenAIChoice> = emptyList()
)

@Serializable
data class OpenAIChoice(
    val index: Int = 0,
    val message: OpenAIMessage,
    @SerialName("finish_reason") val finishReason: String? = null
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

@Serializable
data class ExtractedMeetingDto(
    val title: String,
    val description: String?,
    val dateTime: String? = null,
    val location: String? = null,
    val confidence: Double = 0.5,
    val sourceMessageId: String? = null
)