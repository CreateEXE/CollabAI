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

class AnthropicProviderAdapter : AiProviderAdapter {

    override suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("Anthropic API key is required."))

        try {
            val url = "https://api.anthropic.com/v1/messages"
            val bodyJson = JSONObject().apply {
                put("model", model)
                put("max_tokens", 10)
                val msgs = JSONArray().put(JSONObject().put("role", "user").put("content", "ping"))
                put("messages", msgs)
            }

            val request = Request.Builder()
                .url(url)
                .addHeader("x-api-key", apiKey)
                .addHeader("anthropic-version", "2023-06-01")
                .post(bodyJson.toString().toRequestBody(HttpClientHelper.JSON_MEDIA_TYPE))
                .build()

            val response = HttpClientHelper.client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMessage = parseErrorMessage(respBody)
                return@withContext Result.failure(
                    AiProviderException(response.code, "Anthropic Claude", errMessage)
                )
            }

            Result.success("Connected to Anthropic ($model) successfully.")
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
        val url = "https://api.anthropic.com/v1/messages"
        val bodyJson = JSONObject()
        bodyJson.put("model", model)
        bodyJson.put("max_tokens", 4096)
        bodyJson.put(
            "system",
            "You are an AI collaborator inside a Multi-AI Android Workspace. You have access to tools for interacting with sandboxed workspace files and math evaluation. Call tools where appropriate."
        )

        val messages = JSONArray()
        val recentHistory = history.takeLast(20)
        recentHistory.forEach { msg ->
            val role = if (msg.sender.type == SenderType.USER) "user" else "assistant"
            val content = if (msg.sender.type != SenderType.USER) {
                "[Agent ${msg.sender.displayName}]: ${msg.content}"
            } else {
                msg.content
            }
            if (content.isNotBlank()) {
                messages.put(JSONObject().put("role", role).put("content", content))
            }
        }

        messages.put(JSONObject().put("role", "user").put("content", prompt))
        bodyJson.put("messages", messages)

        if (tools.isNotEmpty()) {
            bodyJson.put("tools", ToolRegistry.toAnthropicToolsJson())
        }

        executeAnthropicCall(url, bodyJson, apiKey, model)
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
        val url = "https://api.anthropic.com/v1/messages"
        val bodyJson = JSONObject()
        bodyJson.put("model", model)
        bodyJson.put("max_tokens", 4096)

        val messages = JSONArray()
        val recentHistory = history.takeLast(15)
        recentHistory.forEach { msg ->
            val role = if (msg.sender.type == SenderType.USER) "user" else "assistant"
            if (msg.content.isNotBlank()) {
                messages.put(JSONObject().put("role", role).put("content", msg.content))
            }
        }

        // Assistant tool_use block
        val assistantContent = JSONArray().put(
            JSONObject()
                .put("type", "tool_use")
                .put("id", toolCallId)
                .put("name", toolName)
                .put("input", JSONObject())
        )
        messages.put(JSONObject().put("role", "assistant").put("content", assistantContent))

        // User tool_result block
        val userContent = JSONArray().put(
            JSONObject()
                .put("type", "tool_result")
                .put("tool_use_id", toolCallId)
                .put("content", toolResult)
        )
        messages.put(JSONObject().put("role", "user").put("content", userContent))
        bodyJson.put("messages", messages)

        if (tools.isNotEmpty()) {
            bodyJson.put("tools", ToolRegistry.toAnthropicToolsJson())
        }

        executeAnthropicCall(url, bodyJson, apiKey, model)
    }

    private fun executeAnthropicCall(url: String, bodyJson: JSONObject, apiKey: String, model: String): ProviderResponse {
        val request = Request.Builder()
            .url(url)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .post(bodyJson.toString().toRequestBody(HttpClientHelper.JSON_MEDIA_TYPE))
            .build()

        val response = HttpClientHelper.client.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = parseErrorMessage(respBody)
            throw AiProviderException(response.code, "Anthropic Claude", errMessage)
        }

        val json = JSONObject(respBody)
        val contentArray = json.optJSONArray("content")
            ?: throw IOException("No content returned from Anthropic")

        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolExecutionRequest>()

        for (i in 0 until contentArray.length()) {
            val block = contentArray.getJSONObject(i)
            val type = block.optString("type")
            if (type == "text") {
                textBuilder.append(block.optString("text"))
            } else if (type == "tool_use") {
                val id = block.optString("id")
                val name = block.optString("name")
                val inputObj = block.optJSONObject("input") ?: JSONObject()
                val argsMap = mutableMapOf<String, Any?>()
                inputObj.keys().forEach { k -> argsMap[k] = inputObj.get(k) }

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
        val inputTokens = usage?.optInt("input_tokens") ?: 0
        val outputTokens = usage?.optInt("output_tokens") ?: 0
        val tokenUsage = TokenUsage(
            promptTokens = inputTokens,
            completionTokens = outputTokens,
            totalTokens = inputTokens + outputTokens
        )

        return ProviderResponse(
            text = textBuilder.toString(),
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
