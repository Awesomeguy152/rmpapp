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