package com.example.provider

import com.example.data.model.ChatMessage
import com.example.data.model.SenderType
import com.example.data.model.TokenUsage
import com.example.data.model.ToolDefinition
import com.example.data.model.ToolExecutionRequest
import com.example.tools.ToolRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class OpenAiProviderAdapter : AiProviderAdapter {

    override suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("OpenAI API key is required."))

        try {
            val url = "https://api.openai.com/v1/chat/completions"
            val bodyJson = JSONObject().apply {
                put("model", model)
                val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "ping"))
                put("messages", msgs)
                put("max_tokens", 5)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .post(bodyJson.toString().toRequestBody(HttpClientHelper.JSON_MEDIA_TYPE))
                .build()

            val response = HttpClientHelper.client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMessage = parseErrorMessage(respBody)
                return@withContext Result.failure(
                    AiProviderException(response.code, "OpenAI", errMessage)
                )
            }

            Result.success("Connected to OpenAI ($model) successfully.")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun sendMessage(
        history: List<ChatMessage>,
        prompt: String,
        apiKey: String,
        model: String,
        tools: List<ToolDefinition>
    ): ProviderResponse = withContext(Dispatchers.IO) {
        val url = "https://api.openai.com/v1/chat/completions"
        val bodyJson = JSONObject()
        bodyJson.put("model", model)

        val messages = JSONArray()
        // System instruction establishing multi-agent context
        messages.put(
            JSONObject()
                .put("role", "system")
                .put("content", "You are an AI assistant in a Multi-AI Collaboration Workspace. You have access to sandboxed workspace tools. Return structured function calls when file or calculation actions are requested.")
        )

        val recentHistory = history.takeLast(20)
        recentHistory.forEach { msg ->
            val role = if (msg.sender.type == SenderType.USER) "user" else "assistant"
            val content = if (msg.sender.type != SenderType.USER) {
                "[Agent ${msg.sender.displayName}]: ${msg.content}"
            } else {
                msg.content
            }
            messages.put(JSONObject().put("role", role).put("content", content))
        }

        messages.put(JSONObject().put("role", "user").put("content", prompt))
        bodyJson.put("messages", messages)

        if (tools.isNotEmpty()) {
            bodyJson.put("tools", ToolRegistry.toOpenAiToolsJson())
        }

        executeOpenAiCall(url, bodyJson, apiKey, model)
    }

    override suspend fun sendToolResult(
        history: List<ChatMessage>,
        toolCallId: String,
        toolName: String,
        toolResult: String,
        apiKey: String,
        model: String,
        tools: List<ToolDefinition>
    ): ProviderResponse = withContext(Dispatchers.IO) {
        val url = "https://api.openai.com/v1/chat/completions"
        val bodyJson = JSONObject()
        bodyJson.put("model", model)

        val messages = JSONArray()
        val recentHistory = history.takeLast(15)
        recentHistory.forEach { msg ->
            val role = if (msg.sender.type == SenderType.USER) "user" else "assistant"
            messages.put(JSONObject().put("role", role).put("content", msg.content))
        }

        // Add assistant tool invocation dummy and tool result response
        val assistantCall = JSONObject().put("role", "assistant").put("content", JSONObject.NULL)
        val toolCallsArr = JSONArray().put(
            JSONObject().put("id", toolCallId).put("type", "function").put(
                "function",
                JSONObject().put("name", toolName).put("arguments", "{}")
            )
        )
        assistantCall.put("tool_calls", toolCallsArr)
        messages.put(assistantCall)

        val toolMsg = JSONObject().apply {
            put("role", "tool")
            put("tool_call_id", toolCallId)
            put("content", toolResult)
        }
        messages.put(toolMsg)
        bodyJson.put("messages", messages)

        if (tools.isNotEmpty()) {
            bodyJson.put("tools", ToolRegistry.toOpenAiToolsJson())
        }

        executeOpenAiCall(url, bodyJson, apiKey, model)
    }

    private fun executeOpenAiCall(url: String, bodyJson: JSONObject, apiKey: String, model: String): ProviderResponse {
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(bodyJson.toString().toRequestBody(HttpClientHelper.JSON_MEDIA_TYPE))
            .build()

        val response = HttpClientHelper.client.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = parseErrorMessage(respBody)
            throw AiProviderException(response.code, "OpenAI", errMessage)
        }

        val json = JSONObject(respBody)
        val choices = json.optJSONArray("choices")
            ?: throw IOException("No choices returned from OpenAI")
        val choice = choices.getJSONObject(0)
        val msgObj = choice.getJSONObject("message")
        val text = msgObj.optString("content", "")

        val toolCalls = mutableListOf<ToolExecutionRequest>()
        val toolCallsJson = msgObj.optJSONArray("tool_calls")
        if (toolCallsJson != null) {
            for (i in 0 until toolCallsJson.length()) {
                val tc = toolCallsJson.getJSONObject(i)
                val id = tc.optString("id", java.util.UUID.randomUUID().toString())
                val funcObj = tc.getJSONObject("function")
                val name = funcObj.getString("name")
                val argsString = funcObj.optString("arguments", "{}")
                val argsMap = mutableMapOf<String, Any?>()
                try {
                    val parsedArgs = JSONObject(argsString)
                    parsedArgs.keys().forEach { k -> argsMap[k] = parsedArgs.get(k) }
                } catch (_: Exception) {}

                val toolDef = ToolRegistry.TOOLS.find { it.name == name }
                toolCalls.add(
                    ToolExecutionRequest(
                        callId = id,
                        toolName = name,
                        arguments = argsMap,
                        isConsequential = toolDef?.isConsequential == true
                    )
                )
            }
        }

        val usage = json.optJSONObject("usage")
        val tokenUsage = TokenUsage(
            promptTokens = usage?.optInt("prompt_tokens") ?: 0,
            completionTokens = usage?.optInt("completion_tokens") ?: 0,
            totalTokens = usage?.optInt("total_tokens") ?: 0
        )

        return ProviderResponse(
            text = text,
            toolCalls = toolCalls,
            tokenUsage = tokenUsage,
            modelUsed = model
        )
    }

    private fun parseErrorMessage(responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val error = json.optJSONObject("error")
            error?.optString("message") ?: responseBody.take(200)
        } catch (_: Exception) {
            responseBody.take(200)
        }
    }
}
