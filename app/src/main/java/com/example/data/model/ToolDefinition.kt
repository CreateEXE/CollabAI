package com.example.data.model

data class ToolParameterProperty(
    val type: String, // "string", "number", "boolean", "object", "array"
    val description: String,
    val isRequired: Boolean = true
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: Map<String, ToolParameterProperty>,
    val isConsequential: Boolean = false // If true, requires explicit user approval before mutating files
)

data class ToolExecutionRequest(
    val callId: String,
    val toolName: String,
    val arguments: Map<String, Any?>,
    val isConsequential: Boolean = false
)

data class ToolExecutionResult(
    val callId: String,
    val toolName: String,
    val isSuccess: Boolean,
    val output: String,
    val error: String? = null
)

data class PendingToolApproval(
    val request: ToolExecutionRequest,
    val callingProvider: AiProvider,
    val summary: String,
    val targetPath: String? = null,
    val diffPreview: String? = null
)
