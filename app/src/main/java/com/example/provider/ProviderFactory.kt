package com.example.provider

import com.example.data.model.AiProvider

object ProviderFactory {
    private val geminiAdapter by lazy { GeminiProviderAdapter() }
    private val openAiAdapter by lazy { OpenAiProviderAdapter() }
    private val anthropicAdapter by lazy { AnthropicProviderAdapter() }

    fun getAdapter(provider: AiProvider): AiProviderAdapter {
        return when (provider) {
            AiProvider.GEMINI -> geminiAdapter
            AiProvider.OPENAI -> openAiAdapter
            AiProvider.ANTHROPIC -> anthropicAdapter
        }
    }
}
