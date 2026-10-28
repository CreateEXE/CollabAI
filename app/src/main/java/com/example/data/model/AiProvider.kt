package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class AiProvider(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val availableModels: List<String>,
    val brandColor: Color,
    val isFreeTierAvailable: Boolean,
    val freeKeyUrl: String,
    val description: String
) {
    GEMINI(
        id = "gemini",
        displayName = "Google Gemini",
        defaultModel = "gemini-2.5-flash",
        availableModels = listOf("gemini-2.5-flash", "gemini-1.5-flash", "gemini-1.5-pro"),
        brandColor = Color(0xFF1A73E8), // Google Blue
        isFreeTierAvailable = true,
        freeKeyUrl = "https://aistudio.google.com/app/apikey",
        description = "Free tier via Google AI Studio. High speed multimodal reasoning and tools."
    ),
    GROQ(
        id = "groq",
        displayName = "Groq LPU",
        defaultModel = "llama-3.3-70b-versatile",
        availableModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "mixtral-8x7b-32768"),
        brandColor = Color(0xFFF55036), // Groq Orange
        isFreeTierAvailable = true,
        freeKeyUrl = "https://console.groq.com/keys",
        description = "100% Free API keys. Ultra-fast LPU inference running Llama 3.3 70B."
    ),
    OPENROUTER(
        id = "openrouter",
        displayName = "OpenRouter",
        defaultModel = "meta-llama/llama-3.3-70b-instruct:free",
        availableModels = listOf(
            "meta-llama/llama-3.3-70b-instruct:free",
            "qwen/qwen-2.5-coder-32b-instruct:free",
            "google/gemini-2.0-flash-exp:free"
        ),
        brandColor = Color(0xFF6366F1), // Indigo
        isFreeTierAvailable = true,
        freeKeyUrl = "https://openrouter.ai/keys",
        description = "Free models available without payment. Access to Qwen Coder and Llama 3.3."
    ),
    OPENAI(
        id = "openai",
        displayName = "OpenAI",
        defaultModel = "gpt-4o-mini",
        availableModels = listOf("gpt-4o-mini", "gpt-4o", "gpt-4-turbo"),
        brandColor = Color(0xFF10A37F), // Emerald Green
        isFreeTierAvailable = false,
        freeKeyUrl = "https://platform.openai.com/api-keys",
        description = "Advanced reasoning, structured generation, and precise function calling."
    );

    companion object {
        fun fromId(id: String): AiProvider? = entries.find { it.id.equals(id, ignoreCase = true) }
    }
}
