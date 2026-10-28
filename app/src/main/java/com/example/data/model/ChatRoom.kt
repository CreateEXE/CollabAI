package com.example.data.model

import java.util.UUID

enum class CollaborationMode(val displayName: String, val description: String) {
    SEQUENTIAL_RELAY(
        displayName = "Sequential Relay",
        description = "AIs respond in a custom pipeline order (e.g. Coder → Reviewer → Tester)."
    ),
    AUTONOMOUS_COMPLETION(
        displayName = "Autonomous until Complete",
        description = "AIs converse and use tools iteratively until the task is complete (or max turns reached)."
    ),
    PARALLEL_BROADCAST(
        displayName = "Parallel Broadcast",
        description = "All selected AIs respond independently to compare different solutions."
    ),
    MANUAL_DIRECTED(
        displayName = "Manual / Mention Only",
        description = "Only the selected agent or @mentioned model responds."
    )
}

data class ChatRoom(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val projectDetails: String = "",
    val systemInstructions: String = "",
    val participatingProviders: List<AiProvider> = listOf(AiProvider.GEMINI, AiProvider.GROQ),
    val collaborationMode: CollaborationMode = CollaborationMode.SEQUENTIAL_RELAY,
    val responseOrder: List<AiProvider> = listOf(AiProvider.GEMINI, AiProvider.GROQ),
    val maxAutonomousTurns: Int = 3,
    val autoStopOnCompletionTag: Boolean = true,
    val isRoundtableEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        val DEFAULT = ChatRoom(
            id = "default_project_chat",
            title = "Main AI Group Chat",
            projectDetails = "Collaborative development and artifact creation workspace.",
            systemInstructions = "You are in an AI group chat. Communicate with each other, review proposals, and use workspace tools to create and edit artifacts.",
            participatingProviders = listOf(AiProvider.GEMINI, AiProvider.GROQ, AiProvider.OPENROUTER),
            collaborationMode = CollaborationMode.SEQUENTIAL_RELAY,
            responseOrder = listOf(AiProvider.GEMINI, AiProvider.GROQ, AiProvider.OPENROUTER),
            maxAutonomousTurns = 3,
            autoStopOnCompletionTag = true,
            isRoundtableEnabled = true
        )
    }
}
