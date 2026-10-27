package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val brandColor: Color,
    val description: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro"),
        brandColor = Color(0xFF1A73E8), // Google Blue
        description = "High-speed multimodal reasoning, coding, and function execution."
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo"),
        brandColor = Color(0xFF10A37F), // Emerald Green
        description = "Advanced reasoning, structured generation, and precise function calling."
    ),
    ANTHROPIC(
        id = "anthropic",
        displayName = "Anthropic Claude",
        defaultModel = "claude-3-5-haiku-20241022",
        availableModels = listOf("claude-3-5-haiku-20241022", "claude-3-5-sonnet-20241022"),
        brandColor = Color(0xFFD97706), // Warm Amber/Coral
        description = "Nuanced critique, code review, safety analysis, and structured tools."
    );

    companion object {
        fun fromId(id: String): AiProvider? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}
