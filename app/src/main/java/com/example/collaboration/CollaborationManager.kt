package com.example.collaboration

import android.content.Context
import android.util.Log
import com.example.data.local.AppPreferences
import com.example.data.local.WorkspaceFileManager
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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

    private val _chatRooms = MutableStateFlow<List<ChatRoom>>(emptyList())
    val chatRooms: StateFlow<List<ChatRoom>> = _chatRooms.asStateFlow()

    private val _activeChatRoom = MutableStateFlow<ChatRoom>(ChatRoom.DEFAULT)
    val activeChatRoom: StateFlow<ChatRoom> = _activeChatRoom.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    // Filter messages for current room
    val currentRoomMessages: StateFlow<List<ChatMessage>> = combine(_messages, _activeChatRoom) { allMsgs, room ->
        val filtered = allMsgs.filter { it.chatRoomId == room.id }
        if (filtered.isEmpty()) {
            listOf(
                ChatMessage(
                    chatRoomId = room.id,
                    sender = MessageSender.SYSTEM,
                    recipient = MessageRecipient.ALL,
                    content = "Group chat: **${room.title}**\nParticipating AIs: ${room.participatingProviders.joinToString { it.displayName }}\n\n${if (room.projectDetails.isNotBlank()) "Goal: ${room.projectDetails}\n" else ""}${if (room.systemInstructions.isNotBlank()) "Rules: ${room.systemInstructions}" else ""}".trim(),
                    status = MessageStatus.COMPLETED
                )
            )
        } else {
            filtered
        }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _providerConfigs = MutableStateFlow<Map<AiProvider, ProviderConfig>>(emptyMap())
    val providerConfigs: StateFlow<Map<AiProvider, ProviderConfig>> = _providerConfigs.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _pendingApproval = MutableStateFlow<PendingToolApproval?>(null)
    val pendingApproval: StateFlow<PendingToolApproval?> = _pendingApproval.asStateFlow()

    private val _activeJobs = mutableMapOf<String, Job>()

    init {
        _providerConfigs.value = appPreferences.loadProviderConfigs()

        val loadedRooms = appPreferences.loadChatRooms()
        _chatRooms.value = loadedRooms
        val activeId = appPreferences.getActiveChatRoomId()
        _activeChatRoom.value = loadedRooms.find { it.id == activeId } ?: loadedRooms.firstOrNull() ?: ChatRoom.DEFAULT

        val loaded = appPreferences.loadMessages()
        if (loaded.isEmpty()) {
            val welcomeMsg = ChatMessage(
                chatRoomId = _activeChatRoom.value.id,
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

    fun createChatRoom(
        title: String,
        projectDetails: String,
        systemInstructions: String,
        participatingProviders: List<AiProvider>,
        collaborationMode: com.example.data.model.CollaborationMode = com.example.data.model.CollaborationMode.SEQUENTIAL_RELAY,
        responseOrder: List<AiProvider> = participatingProviders,
        maxAutonomousTurns: Int = 3,
        autoStopOnCompletionTag: Boolean = true,
        isRoundtableEnabled: Boolean = true
    ): ChatRoom {
        val providers = if (participatingProviders.isEmpty()) listOf(AiProvider.GEMINI, AiProvider.GROQ) else participatingProviders
        val order = if (responseOrder.isEmpty()) providers else responseOrder
        val newRoom = ChatRoom(
            title = title.trim(),
            projectDetails = projectDetails.trim(),
            systemInstructions = systemInstructions.trim(),
            participatingProviders = providers,
            collaborationMode = collaborationMode,
            responseOrder = order,
            maxAutonomousTurns = maxAutonomousTurns,
            autoStopOnCompletionTag = autoStopOnCompletionTag,
            isRoundtableEnabled = isRoundtableEnabled
        )
        val updatedRooms = _chatRooms.value + newRoom
        _chatRooms.value = updatedRooms
        appPreferences.saveChatRooms(updatedRooms)
        selectChatRoom(newRoom.id)
        return newRoom
    }

    fun selectChatRoom(roomId: String) {
        val room = _chatRooms.value.find { it.id == roomId } ?: return
        _activeChatRoom.value = room
        appPreferences.setActiveChatRoomId(roomId)
    }

    fun updateChatRoom(room: ChatRoom) {
        val updated = _chatRooms.value.map { if (it.id == room.id) room else it }
        _chatRooms.value = updated
        if (_activeChatRoom.value.id == room.id) {
            _activeChatRoom.value = room
        }
        appPreferences.saveChatRooms(updated)
    }

    fun deleteChatRoom(roomId: String) {
        if (_chatRooms.value.size <= 1) return // Keep at least one room
        val updated = _chatRooms.value.filter { it.id != roomId }
        _chatRooms.value = updated
        appPreferences.saveChatRooms(updated)
        if (_activeChatRoom.value.id == roomId) {
            selectChatRoom(updated.first().id)
        }
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
        val currentRoom = _activeChatRoom.value

        val userMsg = ChatMessage(
            chatRoomId = currentRoom.id,
            sender = MessageSender.USER,
            recipient = recipient,
            content = trimmed,
            status = MessageStatus.COMPLETED
        )

        val updatedList = _messages.value + userMsg
        _messages.value = updatedList
        appPreferences.saveMessages(updatedList)

        // Context prefix from project room details & instructions
        val promptWithContext = buildPromptWithRoomContext(currentRoom, trimmed)

        val configured = currentRoom.participatingProviders.filter { prov ->
            val cfg = _providerConfigs.value[prov]
            cfg?.isEnabled != false && keystoreManager.hasApiKey(prov)
        }
        val available = if (configured.isNotEmpty()) configured else currentRoom.participatingProviders

        if (recipient.type == MessageRecipientType.ALL) {
            when (currentRoom.collaborationMode) {
                com.example.data.model.CollaborationMode.SEQUENTIAL_RELAY -> {
                    val order = if (currentRoom.responseOrder.isNotEmpty()) {
                        val filtered = currentRoom.responseOrder.filter { available.contains(it) }
                        if (filtered.isNotEmpty()) filtered else available
                    } else available

                    executeSequentialRelay(
                        order = order,
                        currentIndex = 0,
                        currentPrompt = promptWithContext,
                        replyTo = null,
                        room = currentRoom
                    )
                }
                com.example.data.model.CollaborationMode.AUTONOMOUS_COMPLETION -> {
                    val pool = if (currentRoom.responseOrder.isNotEmpty()) {
                        val filtered = currentRoom.responseOrder.filter { available.contains(it) }
                        if (filtered.isNotEmpty()) filtered else available
                    } else available

                    executeAutonomousLoop(
                        providers = pool,
                        turn = 1,
                        maxTurns = currentRoom.maxAutonomousTurns.coerceIn(1, 10),
                        currentPrompt = promptWithContext,
                        replyTo = null,
                        room = currentRoom
                    )
                }
                com.example.data.model.CollaborationMode.PARALLEL_BROADCAST -> {
                    available.forEach { provider ->
                        dispatchAgentCall(provider, promptWithContext, replyTo = null)
                    }
                }
                com.example.data.model.CollaborationMode.MANUAL_DIRECTED -> {
                    val target = available.firstOrNull() ?: AiProvider.GEMINI
                    dispatchAgentCall(target, promptWithContext, replyTo = null)
                }
            }
        } else {
            // Directed to single provider
            val target = recipient.provider ?: available.firstOrNull() ?: AiProvider.GEMINI
            dispatchAgentCall(target, promptWithContext, replyTo = null)
        }
    }

    private fun executeSequentialRelay(
        order: List<AiProvider>,
        currentIndex: Int,
        currentPrompt: String,
        replyTo: ChatMessage?,
        room: ChatRoom
    ) {
        if (currentIndex >= order.size) {
            val doneMsg = ChatMessage(
                chatRoomId = room.id,
                sender = MessageSender.SYSTEM,
                recipient = MessageRecipient.ALL,
                content = "✅ Sequential relay finished (${order.joinToString(" → ") { it.displayName }}).",
                status = MessageStatus.COMPLETED
            )
            val updated = _messages.value + doneMsg
            _messages.value = updated
            appPreferences.saveMessages(updated)
            return
        }

        val provider = order[currentIndex]
        val isFirst = currentIndex == 0

        dispatchAgentCall(
            provider = provider,
            prompt = currentPrompt,
            replyTo = replyTo,
            isCritique = !isFirst,
            onSuccess = { finishedMsg ->
                val nextIndex = currentIndex + 1
                if (nextIndex < order.size) {
                    val nextProvider = order[nextIndex]
                    val nextPrompt = """
                        ${provider.displayName} completed step ${currentIndex + 1} of ${order.size}:
                        ---
                        ${finishedMsg.content}
                        ---
                        Instruction for ${nextProvider.displayName}: You are next in the sequential relay (${order.joinToString(" → ") { it.displayName }}). Review ${provider.displayName}'s work, use workspace tools to test, refine, or build the next part of the project, and share your output.
                    """.trimIndent()
                    executeSequentialRelay(
                        order = order,
                        currentIndex = nextIndex,
                        currentPrompt = buildPromptWithRoomContext(room, nextPrompt),
                        replyTo = finishedMsg,
                        room = room
                    )
                } else {
                    val doneMsg = ChatMessage(
                        chatRoomId = room.id,
                        sender = MessageSender.SYSTEM,
                        recipient = MessageRecipient.ALL,
                        content = "✅ Sequential relay finished (${order.joinToString(" → ") { it.displayName }}).",
                        status = MessageStatus.COMPLETED
                    )
                    val updated = _messages.value + doneMsg
                    _messages.value = updated
                    appPreferences.saveMessages(updated)
                }
            }
        )
    }

    private fun executeAutonomousLoop(
        providers: List<AiProvider>,
        turn: Int,
        maxTurns: Int,
        currentPrompt: String,
        replyTo: ChatMessage?,
        room: ChatRoom
    ) {
        if (turn > maxTurns) {
            val stopMsg = ChatMessage(
                chatRoomId = room.id,
                sender = MessageSender.SYSTEM,
                recipient = MessageRecipient.ALL,
                content = "⏹️ Autonomous turn limit reached ($maxTurns turns). Execution paused by turn limit rule.",
                status = MessageStatus.COMPLETED
            )
            val updated = _messages.value + stopMsg
            _messages.value = updated
            appPreferences.saveMessages(updated)
            return
        }

        val provider = providers[(turn - 1) % providers.size]

        dispatchAgentCall(
            provider = provider,
            prompt = currentPrompt,
            replyTo = replyTo,
            isCritique = turn > 1,
            onSuccess = { finishedMsg ->
                val hasCompletedTag = room.autoStopOnCompletionTag && (
                    finishedMsg.content.contains("[PROJECT_COMPLETE]", ignoreCase = true) ||
                    finishedMsg.content.contains("[TASK_COMPLETE]", ignoreCase = true)
                )

                if (hasCompletedTag) {
                    val completeMsg = ChatMessage(
                        chatRoomId = room.id,
                        sender = MessageSender.SYSTEM,
                        recipient = MessageRecipient.ALL,
                        content = "🏁 Project marked complete by ${provider.displayName} in turn $turn of $maxTurns.",
                        status = MessageStatus.COMPLETED
                    )
                    val updated = _messages.value + completeMsg
                    _messages.value = updated
                    appPreferences.saveMessages(updated)
                    return@dispatchAgentCall
                }

                if (turn < maxTurns) {
                    val nextProvider = providers[turn % providers.size]
                    val nextPrompt = """
                        Autonomous Turn $turn completed by ${provider.displayName}:
                        ---
                        ${finishedMsg.content}
                        ---
                        Instruction for ${nextProvider.displayName} (Turn ${turn + 1} of $maxTurns): Continue working freely on the project. Inspect artifacts with tools, test or improve code, and communicate directly with the team. When finished, include [PROJECT_COMPLETE].
                    """.trimIndent()
                    executeAutonomousLoop(
                        providers = providers,
                        turn = turn + 1,
                        maxTurns = maxTurns,
                        currentPrompt = buildPromptWithRoomContext(room, nextPrompt),
                        replyTo = finishedMsg,
                        room = room
                    )
                } else {
                    val stopMsg = ChatMessage(
                        chatRoomId = room.id,
                        sender = MessageSender.SYSTEM,
                        recipient = MessageRecipient.ALL,
                        content = "⏹️ Autonomous work limit reached ($maxTurns turns). You can send another prompt to continue.",
                        status = MessageStatus.COMPLETED
                    )
                    val updated = _messages.value + stopMsg
                    _messages.value = updated
                    appPreferences.saveMessages(updated)
                }
            }
        )
    }

    fun requestCritique(targetMessage: ChatMessage, reviewerProvider: AiProvider) {
        val currentRoom = _activeChatRoom.value
        val prompt = """
            Please review the following response from ${targetMessage.sender.displayName}:
            ---
            ${targetMessage.content}
            ---
            Evaluate the solution for correctness, potential edge cases, workspace tool usage, and suggest specific improvements.
        """.trimIndent()

        val userPromptMsg = ChatMessage(
            chatRoomId = currentRoom.id,
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
            prompt = buildPromptWithRoomContext(currentRoom, prompt),
            replyTo = targetMessage,
            isCritique = true
        )
    }

    fun startReviewRound(config: ReviewRoundConfig, initialTask: String) {
        val trimmed = initialTask.trim()
        if (trimmed.isEmpty()) return
        val currentRoom = _activeChatRoom.value

        val userMsg = ChatMessage(
            chatRoomId = currentRoom.id,
            sender = MessageSender.USER,
            recipient = MessageRecipient.to(config.primaryAgent),
            content = "Collaborative Task: $trimmed\nFocus: ${config.reviewFocus}",
            status = MessageStatus.COMPLETED
        )

        val systemNotice = ChatMessage(
            chatRoomId = currentRoom.id,
            sender = MessageSender.SYSTEM,
            recipient = MessageRecipient.ALL,
            content = "🚀 Multi-Agent Collaboration: ${config.primaryAgent.displayName} ⇄ ${config.reviewerAgent.displayName} (${config.maxPasses} pass${if (config.maxPasses > 1) "es" else ""})\nTopic: $trimmed",
            status = MessageStatus.COMPLETED
        )

        val updatedList = _messages.value + userMsg + systemNotice
        _messages.value = updatedList
        appPreferences.saveMessages(updatedList)

        // Step 1: Dispatch Primary Agent
        val primaryPrompt = buildPromptWithRoomContext(
            currentRoom,
            "User Task: $trimmed\nFocus: ${config.reviewFocus}\nPlease produce the initial solution or create the necessary workspace artifact files using your tools."
        )

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
                    prompt = buildPromptWithRoomContext(currentRoom, reviewerPrompt),
                    replyTo = primaryMsg,
                    isCritique = true,
                    onSuccess = { reviewerMsg ->
                        if (config.maxPasses > 1) {
                            val followUpPrompt = """
                                ${config.reviewerAgent.displayName} reviewed your work with the following feedback:
                                ---
                                ${reviewerMsg.content}
                                ---
                                Please provide final adjustments, address their critique, and update any workspace artifacts as needed.
                            """.trimIndent()

                            dispatchAgentCall(
                                provider = config.primaryAgent,
                                prompt = buildPromptWithRoomContext(currentRoom, followUpPrompt),
                                replyTo = reviewerMsg,
                                isCritique = false,
                                onSuccess = {
                                    val doneMsg = ChatMessage(
                                        chatRoomId = currentRoom.id,
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
                                chatRoomId = currentRoom.id,
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
        val currentRoom = _activeChatRoom.value
        val userPrompt = if (customPrompt.isNotBlank()) customPrompt else "Please reply to ${targetMessage.sender.displayName}'s message above, collaborate on their ideas, and refine any workspace artifacts."
        val promptForAgent = """
            Message from ${targetMessage.sender.displayName}:
            ---
            ${targetMessage.content}
            ---
            Instruction: $userPrompt
        """.trimIndent()

        val userPromptMsg = ChatMessage(
            chatRoomId = currentRoom.id,
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
            prompt = buildPromptWithRoomContext(currentRoom, promptForAgent),
            replyTo = targetMessage,
            isCritique = false
        )
    }

    private fun buildPromptWithRoomContext(room: ChatRoom, basePrompt: String): String {
        val prefix = buildString {
            if (room.projectDetails.isNotBlank()) {
                appendLine("[Project Goal]: ${room.projectDetails}")
            }
            if (room.systemInstructions.isNotBlank()) {
                appendLine("[Project Instructions]: ${room.systemInstructions}")
            }
            if (room.collaborationMode == com.example.data.model.CollaborationMode.AUTONOMOUS_COMPLETION && room.autoStopOnCompletionTag) {
                appendLine("[Autonomous Rule]: Work with workspace tools to make artifacts. When the goal is completely finished and verified, include the tag [PROJECT_COMPLETE] in your final message to conclude.")
            }
        }
        return if (prefix.isNotBlank()) "$prefix\n$basePrompt" else basePrompt
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
        val currentRoomId = _activeChatRoom.value.id

        val placeholderId = UUID.randomUUID().toString()
        val agentMsg = ChatMessage(
            id = placeholderId,
            chatRoomId = currentRoomId,
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
                    val keyUrl = provider.freeKeyUrl
                    throw IllegalArgumentException("API key for ${provider.displayName} is missing. Please enter your free key in Settings ($keyUrl).")
                }

                if (!appPreferences.isOnline()) {
                    throw IllegalStateException("Device is offline. Please check your internet connection.")
                }

                if (config.autoStopOnLimit && config.usage.totalTokens >= config.tokenWarningLimit) {
                    throw IllegalStateException("Token warning limit reached (${config.tokenWarningLimit} tokens). Execution paused by spend controls.")
                }

                val adapter = ProviderFactory.getAdapter(provider)
                val response = adapter.sendMessage(
                    history = _messages.value.filter { it.chatRoomId == currentRoomId && it.id != placeholderId },
                    prompt = prompt,
                    apiKey = apiKey,
                    model = config.selectedModel,
                    tools = ToolRegistry.TOOLS
                )

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

            try {
                val followUp = adapter.sendToolResult(
                    history = _messages.value.filter { it.chatRoomId == _activeChatRoom.value.id },
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
        val roomId = failedMessage.chatRoomId
        _messages.value = _messages.value.filter { it.id != failedMessage.id }
        appPreferences.saveMessages(_messages.value)

        val lastUserPrompt = _messages.value.lastOrNull { it.chatRoomId == roomId && it.sender.type == SenderType.USER }?.content ?: "Continue"
        dispatchAgentCall(prov, lastUserPrompt, replyTo = null)
    }

    fun clearChat() {
        stopAllGenerations()
        val currentRoom = _activeChatRoom.value
        // Clear only current room messages
        val otherRoomMessages = _messages.value.filter { it.chatRoomId != currentRoom.id }
        val welcomeMsg = ChatMessage(
            chatRoomId = currentRoom.id,
            sender = MessageSender.SYSTEM,
            recipient = MessageRecipient.ALL,
            content = "Chat cleared for room: **${currentRoom.title}**.",
            status = MessageStatus.COMPLETED
        )
        _messages.value = otherRoomMessages + welcomeMsg
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
