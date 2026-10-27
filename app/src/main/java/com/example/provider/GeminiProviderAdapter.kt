package com.example.provider

import android.util.Log
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

class GeminiProviderAdapter : AiProviderAdapter {

    override suspend fun testConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) return@withContext Result.failure(IllegalArgumentException("Gemini API key is required."))

        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
            val bodyJson = JSONObject().apply {
                val contents = JSONArray()
                val turn = JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", "Hello, response with 'OK'.")))
                }
                contents.put(turn)
                put("contents", contents)
                put("generationConfig", JSONObject().put("maxOutputTokens", 10))
            }

            val request = Request.Builder()
                .url(url)
                .post(bodyJson.toString().toRequestBody(HttpClientHelper.JSON_MEDIA_TYPE))
                .build()

            val response = HttpClientHelper.client.newCall(request).execute()
            val respBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errMessage = parseErrorMessage(respBody)
                return@withContext Result.failure(
                    AiProviderException(response.code, "Google Gemini", errMessage)
                )
            }

            val json = JSONObject(respBody)
            val candidates = json.optJSONArray("candidates")
            if (candidates != null && candidates.length() > 0) {
                Result.success("Connected to $model successfully.")
            } else {
                Result.failure(IOException("Empty response from Gemini"))
            }
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
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val bodyJson = JSONObject()
        val contents = JSONArray()

        // Shared conversation history (limit to last 20 messages for prompt efficiency)
        val recentHistory = history.takeLast(20)
        recentHistory.forEach { msg ->
            val role = if (msg.sender.type == SenderType.USER) "user" else "model"
            val parts = JSONArray()

            if (msg.content.isNotBlank()) {
                val textWithSender = if (msg.sender.type != SenderType.USER) {
                    "[Agent ${msg.sender.displayName}]: ${msg.content}"
                } else {
                    msg.content
                }
                parts.put(JSONObject().put("text", textWithSender))
            }

            msg.toolCalls.forEach { tc ->
                if (role == "model") {
                    val fc = JSONObject()
                    fc.put("name", tc.toolName)
                    try {
                        fc.put("args", JSONObject(tc.argumentsJson))
                    } catch (_: Exception) {
                        fc.put("args", JSONObject())
                    }
                    parts.put(JSONObject().put("functionCall", fc))
                }
            }

            if (parts.length() > 0) {
                contents.put(JSONObject().put("role", role).put("parts", parts))
            }
        }

        // Add current prompt
        val currentTurn = JSONObject().apply {
            put("role", "user")
            put("parts", JSONArray().put(JSONObject().put("text", prompt)))
        }
        contents.put(currentTurn)
        bodyJson.put("contents", contents)

        // Add tools
        if (tools.isNotEmpty()) {
            bodyJson.put("tools", ToolRegistry.toGeminiToolsJson())
        }

        executeGeminiCall(url, bodyJson, model)
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
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"
        val bodyJson = JSONObject()
        val contents = JSONArray()

        // Include history
        val recentHistory = history.takeLast(15)
        recentHistory.forEach { msg ->
            val role = if (msg.sender.type == SenderType.USER) "user" else "model"
            val parts = JSONArray()
            if (msg.content.isNotBlank()) {
                parts.put(JSONObject().put("text", msg.content))
            }
            if (parts.length() > 0) {
                contents.put(JSONObject().put("role", role).put("parts", parts))
            }
        }

        // Add tool response block
        val toolResponseTurn = JSONObject().apply {
            put("role", "user")
            val parts = JSONArray()
            val fr = JSONObject().apply {
                val responseObj = JSONObject()
                try {
                    responseObj.put("result", JSONObject(toolResult))
                } catch (_: Exception) {
                    responseObj.put("result", toolResult)
                }
                put("functionResponse", JSONObject().put("name", toolName).put("response", responseObj))
            }
            parts.put(fr)
            put("parts", parts)
        }
        contents.put(toolResponseTurn)
        bodyJson.put("contents", contents)

        if (tools.isNotEmpty()) {
            bodyJson.put("tools", ToolRegistry.toGeminiToolsJson())
        }

        executeGeminiCall(url, bodyJson, model)
    }

    private fun executeGeminiCall(url: String, bodyJson: JSONObject, model: String): ProviderResponse {
        val request = Request.Builder()
            .url(url)
            .post(bodyJson.toString().toRequestBody(HttpClientHelper.JSON_MEDIA_TYPE))
            .build()

        val response = HttpClientHelper.client.newCall(request).execute()
        val respBody = response.body?.string() ?: ""

        if (!response.isSuccessful) {
            val errMessage = parseErrorMessage(respBody)
            throw AiProviderException(response.code, "Google Gemini", errMessage)
        }

        val json = JSONObject(respBody)
        val textBuilder = StringBuilder()
        val toolCalls = mutableListOf<ToolExecutionRequest>()

        val candidates = json.optJSONArray("candidates")
        if (candidates != null && candidates.length() > 0) {
            val first = candidates.getJSONObject(0)
            val content = first.optJSONObject("content")
            val parts = content?.optJSONArray("parts")

            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        textBuilder.append(part.getString("text"))
                    }
                    if (part.has("functionCall")) {
                        val fc = part.getJSONObject("functionCall")
                        val name = fc.getString("name")
                        val argsObj = fc.optJSONObject("args") ?: JSONObject()
                        val argsMap = mutableMapOf<String, Any?>()
                        argsObj.keys().forEach { key ->
                            argsMap[key] = argsObj.get(key)
                        }
                        val toolDef = ToolRegistry.TOOLS.find { it.name == name }
                        toolCalls.add(
                            ToolExecutionRequest(
                                callId = java.util.UUID.randomUUID().toString(),
                                toolName = name,
                                arguments = argsMap,
                                isConsequential = toolDef?.isConsequential == true
                            )
                        )
                    }
                }
            }
        }

        // Usage metadata
        val usage = json.optJSONObject("usageMetadata")
        val tokenUsage = TokenUsage(
            promptTokens = usage?.optInt("promptTokenCount") ?: 0,
            completionTokens = usage?.optInt("candidatesTokenCount") ?: 0,
            totalTokens = usage?.optInt("totalTokenCount") ?: 0
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

    companion object {
        private const val TAG = "GeminiAdapter"
    }
}
