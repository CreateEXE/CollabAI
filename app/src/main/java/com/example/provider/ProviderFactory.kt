package com.example.provider

import com.example.data.model.AiProvider

object ProviderFactory {
    private val geminiAdapter by lazy { GeminiProviderAdapter() }
    private val groqAdapter by lazy { GroqProviderAdapter() }
    private val openRouterAdapter by lazy { OpenRouterProviderAdapter() }
    private val openAiAdapter by lazy { OpenAiProviderAdapter() }

    fun getAdapter(provider: AiProvider): AiProviderAdapter {
        return when (provider) {
            AiProvider.GEMINI -> geminiAdapter
            AiProvider.GROQ -> groqAdapter
            AiProvider.OPENROUTER -> openRouterAdapter
            AiProvider.OPENAI -> openAiAdapter
        }
    }
}
