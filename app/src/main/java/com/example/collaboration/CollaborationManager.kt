package com.example.collaboration

import android.content.Context
import android.util.Log
import com.example.data.local.AppPreferences
import com.example.data.local.WorkspaceFileManager
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRecipient
import com.example.data.model.MessageRecipientType
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.PendingToolApproval
import com.example.data.model.ProviderConfig
import com.example.data.model.ReviewRoundConfig
import com.example.data.model.SenderType
import com.example.data.model.ToolCallRecord
import com.example.data.model.ToolExecutionRequest
import com.example.data.security.KeystoreManager
import com.example.provider.AiProviderException
import com.example.provider.ProviderFactory
import com.example.tools.ToolExecutor
import com.example.tools.ToolRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.UUID

class CollaborationManager(
    private val context: Context,
    private val keystoreManager: KeystoreManager,
    private val fileManager: WorkspaceFileManager,
    private val appPreferences: AppPreferences,
    private val scope: CoroutineScope
) {
    val toolExecutor = ToolExecutor(fileManager)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _providerConfigs = MutableStateFlow<Map<AiProvider, ProviderConfig>>(emptyMap())
    val providerConfigs: StateFlow<Map<AiProvider, ProviderConfig>> = _providerConfigs.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingToolApproval?>(null)
    val pendingApproval: StateFlow<PendingToolApproval?> = _pendingApproval.asStateFlow()

    private val _activeJobs = mutableMapOf<String, Job>()

    init {
        _providerConfigs.value = appPreferences.loadProviderConfigs()
        val loaded = appPreferences.loadMessages()
        if (loaded.isEmpty()) {
            val welcomeMsg = ChatMessage(
                sender = MessageSender.SYSTEM,
                recipient = MessageRecipient.ALL,
                content = "Welcome to AI Collab Workspace! Connect your AI providers in Settings, then send prompts, invoke sandboxed workspace tools, and run agent-to-agent reviews.",
                status = MessageStatus.COMPLETED
            )
            _messages.value = listOf(welcomeMsg)
            appPreferences.saveMessages(_messages.value)
        } else {
            _messages.value = loaded
        }
    }

    fun refreshConfigs() {
        _providerConfigs.value = appPreferences.loadProviderConfigs()
    }

    fun stopAllGenerations() {
        _activeJobs.values.forEach { it.cancel() }
        _activeJobs.clear()
        _isGenerating.value = false

        // Mark any in-progress messages as CANCELLED
        val updated = _messages.value.map { msg ->
            if (msg.status == MessageStatus.IN_PROGRESS || msg.status == MessageStatus.EXECUTING_TOOL) {
                msg.copy(status = MessageStatus.CANCELLED)
            } else msg
        }
        _messages.value = updated
        appPreferences.saveMessages(updated)
    }

    fun sendMessage(prompt: String, recipient: MessageRecipient) {
        val trimmed = prompt.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            recipient = recipient,
            content = trimmed,
            status = MessageStatus.COMPLETED
        )

        val updatedList = _messages.value + userMsg
        _messages.value = updatedList
        appPreferences.saveMessages(updatedList)

        val targets = when (recipient.type) {
            MessageRecipientType.ALL -> {
                val available = AiProvider.entries.filter { prov ->
                    val cfg = _providerConfigs.value[prov]
                    cfg?.isEnabled == true && keystoreManager.hasApiKey(prov)
                }
                if (available.isEmpty()) {
                    // Fallback to Gemini if no other configured
                    listOf(AiProvider.GEMINI)
                } else available
            }
            MessageRecipientType.SINGLE_PROVIDER -> {
                listOfNotNull(recipient.provider)
            }
        }

        targets.forEach { provider ->
            dispatchAgentCall(provider, prompt, replyTo = null)
        }
    }

    fun requestCritique(targetMessage: ChatMessage, reviewerProvider: AiProvider) {
        val prompt = """
            Please review the following response from ${targetMessage.sender.displayName}:
            ---
            ${targetMessage.content}
            ---
            Evaluate the solution for correctness, potential edge cases, workspace tool usage, and suggest specific improvements.
        """.trimIndent()

        val userPromptMsg = ChatMessage(
            sender = MessageSender.USER,
            recipient = MessageRecipient.to(reviewerProvider),
            content = "Requested review of ${targetMessage.sender.displayName}'s response.",
            status = MessageStatus.COMPLETED,
            inReplyToId = targetMessage.id,
            inReplyToSender = targetMessage.sender.displayName,
            isCritique = true
        )

        val updated = _messages.value + userPromptMsg
        _messages.value = updated
        appPreferences.saveMessages(updated)

        dispatchAgentCall(
            provider = reviewerProvider,
            prompt = prompt,
            replyTo = targetMessage,
            isCritique = true
        )
    }

    fun startReviewRound(config: ReviewRoundConfig, initialTask: String) {
        val trimmed = initialTask.trim()
        if (trimmed.isEmpty()) return

        val userMsg = ChatMessage(
            sender = MessageSender.USER,
            recipient = MessageRecipient.to(config.primaryAgent),
            content = "Collaborative Task: $trimmed\nFocus: ${config.reviewFocus}",
            status = MessageStatus.COMPLETED
        )

        val systemNotice = ChatMessage(
            sender = MessageSender.SYSTEM,
            recipient = MessageRecipient.ALL,
            content = "🚀 Multi-Agent Collaboration Round: ${config.primaryAgent.displayName} ⇄ ${config.reviewerAgent.displayName} (${config.maxPasses} pass${if (config.maxPasses > 1) "es" else ""})\nTopic: $trimmed",
            status = MessageStatus.COMPLETED
        )

        val updatedList = _messages.value + userMsg + systemNotice
        _messages.value = updatedList
        appPreferences.saveMessages(updatedList)

        // Step 1: Dispatch Primary Agent
        val primaryPrompt = "User Task: $trimmed\nFocus: ${config.reviewFocus}\nPlease produce the initial solution or create the necessary workspace artifact files using your tools."
        dispatchAgentCall(
            provider = config.primaryAgent,
            prompt = primaryPrompt,
            replyTo = null,
            isCritique = false,
            onSuccess = { primaryMsg ->
                // Step 2: Dispatch Reviewer Agent to review Primary Agent's work
                val reviewerPrompt = """
                    Please review the solution and artifacts produced by ${config.primaryAgent.displayName}:
                    ---
                    ${primaryMsg.content}
                    ---
                    Review Focus: ${config.reviewFocus}
                    Please evaluate their work, inspect any created workspace files, communicate your feedback directly to ${config.primaryAgent.displayName}, and use workspace tools to propose improvements or refine the artifacts.
                """.trimIndent()

                dispatchAgentCall(
                    provider = config.reviewerAgent,
                    prompt = reviewerPrompt,
                    replyTo = primaryMsg,
                    isCritique = true,
                    onSuccess = { reviewerMsg ->
                        if (config.maxPasses > 1) {
                            // Step 3: Final refinement pass from Primary Agent responding to Reviewer
                            val followUpPrompt = """
                                ${config.reviewerAgent.displayName} reviewed your work with the following feedback:
                                ---
                                ${reviewerMsg.content}
                                ---
                                Please provide final adjustments, address their critique, and update any workspace artifacts as needed.
                            """.trimIndent()

                            dispatchAgentCall(
                                provider = config.primaryAgent,
                                prompt = followUpPrompt,
                                replyTo = reviewerMsg,
                                isCritique = false,
                                onSuccess = {
                                    val doneMsg = ChatMessage(
                                        sender = MessageSender.SYSTEM,
                                        recipient = MessageRecipient.ALL,
                                        content = "✅ Multi-Agent Collaboration Round completed successfully.",
                                        status = MessageStatus.COMPLETED
                                    )
                                    _messages.value = _messages.value + doneMsg
                                    appPreferences.saveMessages(_messages.value)
                                }
                            )
                        } else {
                            val doneMsg = ChatMessage(
                                sender = MessageSender.SYSTEM,
                                recipient = MessageRecipient.ALL,
                                content = "✅ Multi-Agent Collaboration Round completed.",
                                status = MessageStatus.COMPLETED
                            )
                            _messages.value = _messages.value + doneMsg
                            appPreferences.saveMessages(_messages.value)
                        }
                    }
                )
            }
        )
    }

    fun replyToAgent(targetMessage: ChatMessage, respondingProvider: AiProvider, customPrompt: String = "") {
        val userPrompt = if (customPrompt.isNotBlank()) customPrompt else "Please reply to ${targetMessage.sender.displayName}'s message above, collaborate on their ideas, and refine any workspace artifacts."
        val promptForAgent = """
            Message from ${targetMessage.sender.displayName}:
            ---
            ${targetMessage.content}
            ---
            Instruction: $userPrompt
        """.trimIndent()

        val userPromptMsg = ChatMessage(
            sender = MessageSender.USER,
            recipient = MessageRecipient.to(respondingProvider),
            content = "Prompt to ${respondingProvider.displayName}: $userPrompt",
            status = MessageStatus.COMPLETED,
            inReplyToId = targetMessage.id,
            inReplyToSender = targetMessage.sender.displayName
        )

        val updated = _messages.value + userPromptMsg
        _messages.value = updated
        appPreferences.saveMessages(updated)

        dispatchAgentCall(
            provider = respondingProvider,
            prompt = promptForAgent,
            replyTo = targetMessage,
            isCritique = false
        )
    }

    private fun dispatchAgentCall(
        provider: AiProvider,
        prompt: String,
        replyTo: ChatMessage? = null,
        isCritique: Boolean = false,
        onSuccess: ((ChatMessage) -> Unit)? = null
    ) {
        val apiKey = keystoreManager.getApiKey(provider)
        val config = _providerConfigs.value[provider] ?: ProviderConfig(provider)

        val placeholderId = UUID.randomUUID().toString()
        val agentMsg = ChatMessage(
            id = placeholderId,
            sender = MessageSender.ai(provider),
            recipient = MessageRecipient.to(provider),
            content = "",
            status = MessageStatus.IN_PROGRESS,
            modelUsed = config.selectedModel,
            inReplyToId = replyTo?.id,
            inReplyToSender = replyTo?.sender?.displayName,
            isCritique = isCritique
        )

        _messages.value = _messages.value + agentMsg
        _isGenerating.value = true

        val job = scope.launch {
            try {
                if (apiKey.isBlank()) {
                    throw IllegalArgumentException("API key for ${provider.displayName} is missing. Please configure it in Settings.")
                }

                if (!appPreferences.isOnline()) {
                    throw IllegalStateException("Device is offline. Please check your internet connection.")
                }

                // Check spend control
                if (config.autoStopOnLimit && config.usage.totalTokens >= config.tokenWarningLimit) {
                    throw IllegalStateException("Token warning limit reached (${config.tokenWarningLimit} tokens). Execution paused by spend controls.")
                }

                val adapter = ProviderFactory.getAdapter(provider)
                val response = adapter.sendMessage(
                    history = _messages.value.filter { it.id != placeholderId },
                    prompt = prompt,
                    apiKey = apiKey,
                    model = config.selectedModel,
                    tools = ToolRegistry.TOOLS
                )

                // Record tokens
                appPreferences.recordUsage(provider, response.tokenUsage)
                _providerConfigs.value = appPreferences.loadProviderConfigs()

                if (response.hasToolCalls) {
                    handleAgentToolCalls(
                        provider = provider,
                        messageId = placeholderId,
                        initialText = response.text,
                        toolCalls = response.toolCalls,
                        apiKey = apiKey,
                        model = config.selectedModel,
                        onSuccess = onSuccess
                    )
                } else {
                    val completed = updateMessage(placeholderId) {
                        it.copy(
                            content = response.text,
                            status = MessageStatus.COMPLETED,
                            tokenUsage = response.tokenUsage,
                            modelUsed = response.modelUsed
                        )
                    }
                    if (completed != null) {
                        onSuccess?.invoke(completed)
                    }
                }
            } catch (ce: CancellationException) {
                updateMessage(placeholderId) {
                    it.copy(
                        status = MessageStatus.CANCELLED,
                        errorMessage = "Request cancelled by user."
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Agent call failed for ${provider.name}", e)
                val errMsg = when (e) {
                    is AiProviderException -> e.message ?: "Provider error"
                    else -> e.localizedMessage ?: "Unknown error"
                }
                updateMessage(placeholderId) {
                    it.copy(
                        status = MessageStatus.ERROR,
                        errorMessage = errMsg
                    )
                }
            } finally {
                _activeJobs.remove(placeholderId)
                if (_activeJobs.isEmpty()) {
                    _isGenerating.value = false
                }
            }
        }

        _activeJobs[placeholderId] = job
    }

    private suspend fun handleAgentToolCalls(
        provider: AiProvider,
        messageId: String,
        initialText: String,
        toolCalls: List<ToolExecutionRequest>,
        apiKey: String,
        model: String,
        onSuccess: ((ChatMessage) -> Unit)? = null
    ) {
        val toolRecords = toolCalls.map { tc ->
            ToolCallRecord(
                id = tc.callId,
                toolName = tc.toolName,
                argumentsJson = JSONObject(tc.arguments).toString(),
                isConsequential = tc.isConsequential,
                isExecuting = true
            )
        }

        updateMessage(messageId) {
            it.copy(
                content = initialText,
                status = MessageStatus.EXECUTING_TOOL,
                toolCalls = toolRecords
            )
        }

        val adapter = ProviderFactory.getAdapter(provider)
        var currentText = initialText
        val finalToolRecords = toolRecords.toMutableList()

        for ((index, request) in toolCalls.withIndex()) {
            val approval = toolExecutor.checkApprovalRequirement(request, provider)
            val executionResult = if (approval != null) {
                // Pause and wait for user approval
                _pendingApproval.value = approval
                val approved = waitForApproval(approval)
                _pendingApproval.value = null

                if (approved) {
                    toolExecutor.execute(request, MessageSender.ai(provider))
                } else {
                    com.example.data.model.ToolExecutionResult(
                        callId = request.callId,
                        toolName = request.toolName,
                        isSuccess = false,
                        output = "",
                        error = "Action rejected by user."
                    )
                }
            } else {
                toolExecutor.execute(request, MessageSender.ai(provider))
            }

            finalToolRecords[index] = finalToolRecords[index].copy(
                resultJson = executionResult.output.ifEmpty { executionResult.error },
                isApproved = true,
                isExecuting = false,
                errorMessage = executionResult.error
            )

            updateMessage(messageId) {
                it.copy(toolCalls = finalToolRecords)
            }

            // Return tool result to agent
            try {
                val followUp = adapter.sendToolResult(
                    history = _messages.value,
                    toolCallId = request.callId,
                    toolName = request.toolName,
                    toolResult = executionResult.output.ifEmpty { executionResult.error ?: "Done" },
                    apiKey = apiKey,
                    model = model,
                    tools = ToolRegistry.TOOLS
                )
                currentText = if (currentText.isBlank()) followUp.text else "$currentText\n\n${followUp.text}"
                appPreferences.recordUsage(provider, followUp.tokenUsage)
                _providerConfigs.value = appPreferences.loadProviderConfigs()
            } catch (e: Exception) {
                Log.e(TAG, "Failed sending tool result", e)
            }
        }

        val completed = updateMessage(messageId) {
            it.copy(
                content = currentText,
                status = MessageStatus.COMPLETED,
                toolCalls = finalToolRecords
            )
        }
        if (completed != null) {
            onSuccess?.invoke(completed)
        }
    }

    private val approvalLock = Object()
    private var lastApprovalResult: Boolean = false
    private var waitingForApproval: Boolean = false

    private suspend fun waitForApproval(approval: PendingToolApproval): Boolean = withContext(Dispatchers.IO) {
        waitingForApproval = true
        synchronized(approvalLock) {
            while (waitingForApproval) {
                try {
                    approvalLock.wait()
                } catch (_: InterruptedException) {
                    return@withContext false
                }
            }
        }
        lastApprovalResult
    }

    fun submitToolApproval(approved: Boolean) {
        synchronized(approvalLock) {
            lastApprovalResult = approved
            waitingForApproval = false
            _pendingApproval.value = null
            approvalLock.notifyAll()
        }
    }

    fun retryMessage(failedMessage: ChatMessage) {
        val prov = failedMessage.sender.provider ?: return
        // Remove the failed message
        _messages.value = _messages.value.filter { it.id != failedMessage.id }
        appPreferences.saveMessages(_messages.value)

        val lastUserPrompt = _messages.value.lastOrNull { it.sender.type == SenderType.USER }?.content ?: "Continue"
        dispatchAgentCall(prov, lastUserPrompt, replyTo = null)
    }

    fun clearChat() {
        stopAllGenerations()
        val welcomeMsg = ChatMessage(
            sender = MessageSender.SYSTEM,
            recipient = MessageRecipient.ALL,
            content = "Chat cleared. Start a new conversation, invoke tools, or compare AI model responses.",
            status = MessageStatus.COMPLETED
        )
        _messages.value = listOf(welcomeMsg)
        appPreferences.saveMessages(_messages.value)
    }

    private fun updateMessage(id: String, transform: (ChatMessage) -> ChatMessage): ChatMessage? {
        val current = _messages.value
        val index = current.indexOfFirst { it.id == id }
        if (index != -1) {
            val updated = current.toMutableList()
            val newMsg = transform(current[index])
            updated[index] = newMsg
            _messages.value = updated
            appPreferences.saveMessages(updated)
            return newMsg
        }
        return null
    }

    companion object {
        private const val TAG = "CollaborationManager"
    }
}
