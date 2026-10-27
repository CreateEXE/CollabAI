package com.example.data.model

data class ProviderUsage(
    val totalRequests: Int = 0,
    val promptTokens: Long = 0,
    val completionTokens: Long = 0,
    val totalTokens: Long = 0,
    val estimatedCostUsd: Double = 0.0
)

data class ProviderConfig(
    val provider: AiProvider,
    val selectedModel: String = provider.defaultModel,
    val isEnabled: Boolean = true,
    val isConnected: Boolean = false,
    val lastTestedAt: Long? = null,
    val lastError: String? = null,
    val usage: ProviderUsage = ProviderUsage(),
    val tokenWarningLimit: Long = 100_000L, // Warn when provider reaches token limit
    val autoStopOnLimit: Boolean = true
)

data class ReviewRoundConfig(
    val primaryAgent: AiProvider,
    val reviewerAgent: AiProvider,
    val maxPasses: Int = 1, // Strictly bounded (1 to 3 max)
    val reviewFocus: String = "Review the implementation for correctness, edge cases, tool accuracy, and improvements."
)
