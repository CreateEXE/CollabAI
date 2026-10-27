package com.example.provider

import com.example.data.model.ChatMessage
import com.example.data.model.TokenUsage
import com.example.data.model.ToolDefinition
import com.example.data.model.ToolExecutionRequest

data class ProviderResponse(
    val text: String,
    val toolCalls: List<ToolExecutionRequest> = emptyList(),
    val tokenUsage: TokenUsage = TokenUsage(),
    val modelUsed: String
) {
    val hasToolCalls: Boolean get() = toolCalls.isNotEmpty()
}

interface AiProviderAdapter {
    /**
     * Sends a minimal real test ping to verify credentials and connectivity.
     * Returns Result.success(details) or Result.failure(exception).
     */
    suspend fun testConnection(apiKey: String, model: String): Result<String>

    /**
     * Sends a user/agent message along with shared history and tool schemas.
     */
    suspend fun sendMessage(
        history: List<ChatMessage>,
        prompt: String,
        apiKey: String,
        model: String,
        tools: List<ToolDefinition>
    ): ProviderResponse

    /**
     * Returns tool output back to the model so it can continue generating.
     */
    suspend fun sendToolResult(
        history: List<ChatMessage>,
        toolCallId: String,
        toolName: String,
        toolResult: String,
        apiKey: String,
        model: String,
        tools: List<ToolDefinition>
    ): ProviderResponse
}
