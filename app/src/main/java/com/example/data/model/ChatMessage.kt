package com.example.data.model

import java.util.UUID

enum class SenderType {
    USER,
    AI_PROVIDER,
    SYSTEM
}

data class MessageSender(
    val type: SenderType,
    val provider: AiProvider? = null,
    val customName: String? = null
) {
    val displayName: String
        get() = when (type) {
            SenderType.USER -> "You"
            SenderType.AI_PROVIDER -> provider?.displayName ?: "AI Agent"
            SenderType.SYSTEM -> customName ?: "System"
        }

    companion object {
        val USER = MessageSender(SenderType.USER)
        val SYSTEM = MessageSender(SenderType.SYSTEM)
        fun ai(provider: AiProvider) = MessageSender(SenderType.AI_PROVIDER, provider)
    }
}

enum class MessageRecipientType {
    ALL,
    SINGLE_PROVIDER
}

data class MessageRecipient(
    val type: MessageRecipientType,
    val provider: AiProvider? = null
) {
    val displayName: String
        get() = when (type) {
            MessageRecipientType.ALL -> "Broadcast (All Agents)"
            MessageRecipientType.SINGLE_PROVIDER -> provider?.displayName ?: "Agent"
        }

    companion object {
        val ALL = MessageRecipient(MessageRecipientType.ALL)
        fun to(provider: AiProvider) = MessageRecipient(MessageRecipientType.SINGLE_PROVIDER, provider)
    }
}

enum class MessageStatus {
    PENDING,
    IN_PROGRESS,
    EXECUTING_TOOL,
    COMPLETED,
    ERROR,
    CANCELLED
}

data class ToolCallRecord(
    val id: String = UUID.randomUUID().toString(),
    val toolName: String,
    val argumentsJson: String,
    val resultJson: String? = null,
    val isConsequential: Boolean = false,
    val isApproved: Boolean = false,
    val isExecuting: Boolean = false,
    val errorMessage: String? = null
)

data class TokenUsage(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = 0
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val recipient: MessageRecipient = MessageRecipient.ALL,
    val content: String,
    val status: MessageStatus = MessageStatus.COMPLETED,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val tokenUsage: TokenUsage? = null,
    val toolCalls: List<ToolCallRecord> = emptyList(),
    val inReplyToId: String? = null,
    val inReplyToSender: String? = null,
    val isCritique: Boolean = false,
    val errorMessage: String? = null
)
