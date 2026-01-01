package com.example.services

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

class AiAssistantService {

    fun summarizeConversation(conversationId: UUID): AiSummary = AiSummary(
        conversationId = conversationId.toString(),
        summary = "AI summary is not configured yet. Connect an LLM provider to generate real insights.",
        generatedAt = Instant.now().toString()
    )

    fun suggestNextAction(conversationId: UUID): AiNextAction = AiNextAction(
        conversationId = conversationId.toString(),
        suggestions = listOf(
            "Gather context and plug in an AI provider to produce actionable recommendations.",
            "Manually review the latest messages to decide on the next step."
        ),
        generatedAt = Instant.now().toString()
    )
}

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