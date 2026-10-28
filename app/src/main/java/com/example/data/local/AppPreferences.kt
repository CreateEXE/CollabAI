package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.data.model.MessageRecipient
import com.example.data.model.MessageRecipientType
import com.example.data.model.MessageSender
import com.example.data.model.MessageStatus
import com.example.data.model.ProviderConfig
import com.example.data.model.ProviderUsage
import com.example.data.model.SenderType
import com.example.data.model.TokenUsage
import com.example.data.model.ToolCallRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class AppPreferences(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val messagesFile: File = File(context.filesDir, "chat_history.json")
    private val roomsFile: File = File(context.filesDir, "chat_rooms.json")

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun loadProviderConfigs(): Map<AiProvider, ProviderConfig> {
        val map = mutableMapOf<AiProvider, ProviderConfig>()
        AiProvider.entries.forEach { provider ->
            val prefix = "prov_${provider.id}_"
            val selectedModel = prefs.getString("${prefix}model", provider.defaultModel) ?: provider.defaultModel
            val isEnabled = prefs.getBoolean("${prefix}enabled", true)
            val isConnected = prefs.getBoolean("${prefix}connected", false)
            val lastTestedAt = if (prefs.contains("${prefix}last_test")) prefs.getLong("${prefix}last_test", 0L) else null
            val lastError = prefs.getString("${prefix}last_err", null)

            val totalReq = prefs.getInt("${prefix}total_req", 0)
            val promptTok = prefs.getLong("${prefix}prompt_tok", 0L)
            val compTok = prefs.getLong("${prefix}comp_tok", 0L)
            val totalTok = prefs.getLong("${prefix}total_tok", 0L)
            val costUsd = prefs.getFloat("${prefix}cost", 0.0f).toDouble()
            val warnLimit = prefs.getLong("${prefix}limit", 100_000L)
            val autoStop = prefs.getBoolean("${prefix}auto_stop", true)

            map[provider] = ProviderConfig(
                provider = provider,
                selectedModel = selectedModel,
                isEnabled = isEnabled,
                isConnected = isConnected,
                lastTestedAt = lastTestedAt,
                lastError = lastError,
                usage = ProviderUsage(
                    totalRequests = totalReq,
                    promptTokens = promptTok,
                    completionTokens = compTok,
                    totalTokens = totalTok,
                    estimatedCostUsd = costUsd
                ),
                tokenWarningLimit = warnLimit,
                autoStopOnLimit = autoStop
            )
        }
        return map
    }

    fun saveProviderConfig(config: ProviderConfig) {
        val prefix = "prov_${config.provider.id}_"
        prefs.edit().apply {
            putString("${prefix}model", config.selectedModel)
            putBoolean("${prefix}enabled", config.isEnabled)
            putBoolean("${prefix}connected", config.isConnected)
            config.lastTestedAt?.let { putLong("${prefix}last_test", it) } ?: remove("${prefix}last_test")
            config.lastError?.let { putString("${prefix}last_err", it) } ?: remove("${prefix}last_err")
            putInt("${prefix}total_req", config.usage.totalRequests)
            putLong("${prefix}prompt_tok", config.usage.promptTokens)
            putLong("${prefix}comp_tok", config.usage.completionTokens)
            putLong("${prefix}total_tok", config.usage.totalTokens)
            putFloat("${prefix}cost", config.usage.estimatedCostUsd.toFloat())
            putLong("${prefix}limit", config.tokenWarningLimit)
            putBoolean("${prefix}auto_stop", config.autoStopOnLimit)
            apply()
        }
    }

    fun recordUsage(provider: AiProvider, tokens: TokenUsage) {
        val configs = loadProviderConfigs()
        val current = configs[provider] ?: ProviderConfig(provider)
        val newPrompt = current.usage.promptTokens + tokens.promptTokens
        val newComp = current.usage.completionTokens + tokens.completionTokens
        val newTotal = current.usage.totalTokens + tokens.totalTokens
        val newReq = current.usage.totalRequests + 1

        val estCost = (newTotal * 0.00000020)

        val updated = current.copy(
            usage = current.usage.copy(
                totalRequests = newReq,
                promptTokens = newPrompt,
                completionTokens = newComp,
                totalTokens = newTotal,
                estimatedCostUsd = estCost
            )
        )
        saveProviderConfig(updated)
    }

    fun getActiveChatRoomId(): String {
        return prefs.getString("active_chat_room_id", ChatRoom.DEFAULT.id) ?: ChatRoom.DEFAULT.id
    }

    fun setActiveChatRoomId(id: String) {
        prefs.edit().putString("active_chat_room_id", id).apply()
    }

    @Synchronized
    fun saveChatRooms(rooms: List<ChatRoom>) {
        try {
            val root = JSONObject()
            val arr = JSONArray()
            rooms.forEach { room ->
                val obj = JSONObject()
                obj.put("id", room.id)
                obj.put("title", room.title)
                obj.put("projectDetails", room.projectDetails)
                obj.put("systemInstructions", room.systemInstructions)
                obj.put("isRoundtableEnabled", room.isRoundtableEnabled)
                obj.put("createdAt", room.createdAt)
                obj.put("updatedAt", room.updatedAt)

                obj.put("collaborationMode", room.collaborationMode.name)
                obj.put("maxAutonomousTurns", room.maxAutonomousTurns)
                obj.put("autoStopOnCompletionTag", room.autoStopOnCompletionTag)

                val orderArr = JSONArray()
                room.responseOrder.forEach { orderArr.put(it.name) }
                obj.put("responseOrder", orderArr)

                val provArr = JSONArray()
                room.participatingProviders.forEach { prov ->
                    provArr.put(prov.name)
                }
                obj.put("participatingProviders", provArr)
                arr.put(obj)
            }
            root.put("rooms", arr)
            roomsFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save chat rooms", e)
        }
    }

    fun loadChatRooms(): List<ChatRoom> {
        if (!roomsFile.exists()) return listOf(ChatRoom.DEFAULT)
        return try {
            val content = roomsFile.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val arr = root.optJSONArray("rooms") ?: return listOf(ChatRoom.DEFAULT)
            val list = mutableListOf<ChatRoom>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val title = obj.getString("title")
                val details = obj.optString("projectDetails", "")
                val instructions = obj.optString("systemInstructions", "")
                val isRoundtable = obj.optBoolean("isRoundtableEnabled", true)
                val maxTurns = obj.optInt("maxAutonomousTurns", 3)
                val autoStop = obj.optBoolean("autoStopOnCompletionTag", true)
                val mode = try {
                    com.example.data.model.CollaborationMode.valueOf(obj.optString("collaborationMode", com.example.data.model.CollaborationMode.SEQUENTIAL_RELAY.name))
                } catch (_: Exception) {
                    com.example.data.model.CollaborationMode.SEQUENTIAL_RELAY
                }
                val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                val updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())

                val provList = mutableListOf<AiProvider>()
                val provArr = obj.optJSONArray("participatingProviders")
                if (provArr != null) {
                    for (j in 0 until provArr.length()) {
                        val provName = provArr.getString(j)
                        try {
                            provList.add(AiProvider.valueOf(provName))
                        } catch (_: Exception) {}
                    }
                }
                if (provList.isEmpty()) {
                    provList.addAll(listOf(AiProvider.GEMINI, AiProvider.GROQ))
                }

                val orderList = mutableListOf<AiProvider>()
                val orderArr = obj.optJSONArray("responseOrder")
                if (orderArr != null) {
                    for (j in 0 until orderArr.length()) {
                        try {
                            orderList.add(AiProvider.valueOf(orderArr.getString(j)))
                        } catch (_: Exception) {}
                    }
                }
                if (orderList.isEmpty()) {
                    orderList.addAll(provList)
                }

                list.add(
                    ChatRoom(
                        id = id,
                        title = title,
                        projectDetails = details,
                        systemInstructions = instructions,
                        participatingProviders = provList,
                        collaborationMode = mode,
                        responseOrder = orderList,
                        maxAutonomousTurns = maxTurns,
                        autoStopOnCompletionTag = autoStop,
                        isRoundtableEnabled = isRoundtable,
                        createdAt = createdAt,
                        updatedAt = updatedAt
                    )
                )
            }
            if (list.isEmpty()) listOf(ChatRoom.DEFAULT) else list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load chat rooms", e)
            listOf(ChatRoom.DEFAULT)
        }
    }

    @Synchronized
    fun saveMessages(messages: List<ChatMessage>) {
        try {
            val root = JSONObject()
            val arr = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject()
                obj.put("id", msg.id)
                obj.put("chatRoomId", msg.chatRoomId)
                obj.put("senderType", msg.sender.type.name)
                obj.put("senderProvider", msg.sender.provider?.name)
                obj.put("senderCustomName", msg.sender.customName)
                obj.put("recipientType", msg.recipient.type.name)
                obj.put("recipientProvider", msg.recipient.provider?.name)
                obj.put("content", msg.content)
                obj.put("status", msg.status.name)
                obj.put("timestamp", msg.timestamp)
                obj.put("modelUsed", msg.modelUsed)
                obj.put("inReplyToId", msg.inReplyToId)
                obj.put("inReplyToSender", msg.inReplyToSender)
                obj.put("isCritique", msg.isCritique)
                obj.put("errorMessage", msg.errorMessage)

                if (msg.tokenUsage != null) {
                    val tokObj = JSONObject()
                    tokObj.put("prompt", msg.tokenUsage.promptTokens)
                    tokObj.put("comp", msg.tokenUsage.completionTokens)
                    tokObj.put("total", msg.tokenUsage.totalTokens)
                    obj.put("tokenUsage", tokObj)
                }

                val tcArr = JSONArray()
                msg.toolCalls.forEach { tc ->
                    val tcObj = JSONObject()
                    tcObj.put("id", tc.id)
                    tcObj.put("toolName", tc.toolName)
                    tcObj.put("argumentsJson", tc.argumentsJson)
                    tcObj.put("resultJson", tc.resultJson)
                    tcObj.put("isConsequential", tc.isConsequential)
                    tcObj.put("isApproved", tc.isApproved)
                    tcObj.put("isExecuting", tc.isExecuting)
                    tcObj.put("errorMessage", tc.errorMessage)
                    tcArr.put(tcObj)
                }
                obj.put("toolCalls", tcArr)
                arr.put(obj)
            }
            root.put("messages", arr)
            messagesFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save messages", e)
        }
    }

    fun loadMessages(): List<ChatMessage> {
        if (!messagesFile.exists()) return emptyList()
        return try {
            val content = messagesFile.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val arr = root.optJSONArray("messages") ?: return emptyList()
            val list = mutableListOf<ChatMessage>()

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val chatRoomId = obj.optString("chatRoomId", "default_project_chat")
                val sType = SenderType.valueOf(obj.optString("senderType", SenderType.SYSTEM.name))
                val sProv = obj.optString("senderProvider", null)?.let {
                    try { AiProvider.valueOf(it) } catch (_: Exception) { null }
                }
                val sCust = obj.optString("senderCustomName", null)
                val sender = MessageSender(sType, sProv, sCust)

                val rType = MessageRecipientType.valueOf(obj.optString("recipientType", MessageRecipientType.ALL.name))
                val rProv = obj.optString("recipientProvider", null)?.let {
                    try { AiProvider.valueOf(it) } catch (_: Exception) { null }
                }
                val recipient = MessageRecipient(rType, rProv)

                val status = try {
                    MessageStatus.valueOf(obj.optString("status", MessageStatus.COMPLETED.name))
                } catch (_: Exception) {
                    MessageStatus.COMPLETED
                }

                val tokObj = obj.optJSONObject("tokenUsage")
                val tokUsage = tokObj?.let {
                    TokenUsage(
                        promptTokens = it.optInt("prompt"),
                        completionTokens = it.optInt("comp"),
                        totalTokens = it.optInt("total")
                    )
                }

                val tcList = mutableListOf<ToolCallRecord>()
                val tcArr = obj.optJSONArray("toolCalls")
                if (tcArr != null) {
                    for (j in 0 until tcArr.length()) {
                        val tcObj = tcArr.getJSONObject(j)
                        tcList.add(
                            ToolCallRecord(
                                id = tcObj.optString("id"),
                                toolName = tcObj.optString("toolName"),
                                argumentsJson = tcObj.optString("argumentsJson"),
                                resultJson = tcObj.optString("resultJson", null),
                                isConsequential = tcObj.optBoolean("isConsequential"),
                                isApproved = tcObj.optBoolean("isApproved"),
                                isExecuting = tcObj.optBoolean("isExecuting"),
                                errorMessage = tcObj.optString("errorMessage", null)
                            )
                        )
                    }
                }

                list.add(
                    ChatMessage(
                        id = obj.getString("id"),
                        chatRoomId = chatRoomId,
                        sender = sender,
                        recipient = recipient,
                        content = obj.optString("content"),
                        status = status,
                        timestamp = obj.optLong("timestamp"),
                        modelUsed = obj.optString("modelUsed", null),
                        tokenUsage = tokUsage,
                        toolCalls = tcList,
                        inReplyToId = obj.optString("inReplyToId", null),
                        inReplyToSender = obj.optString("inReplyToSender", null),
                        isCritique = obj.optBoolean("isCritique", false),
                        errorMessage = obj.optString("errorMessage", null)
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages", e)
            emptyList()
        }
    }

    companion object {
        private const val TAG = "AppPreferences"
        private const val PREFS_NAME = "ai_collab_general_prefs"
    }
}
