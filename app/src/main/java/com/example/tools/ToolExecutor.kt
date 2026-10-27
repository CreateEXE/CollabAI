package com.example.tools

import com.example.data.local.WorkspaceFileManager
import com.example.data.model.AiProvider
import com.example.data.model.MessageSender
import com.example.data.model.PendingToolApproval
import com.example.data.model.ToolExecutionRequest
import com.example.data.model.ToolExecutionResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ToolExecutor(private val fileManager: WorkspaceFileManager) {

    /**
     * Checks if this request requires explicit user confirmation before executing.
     * Returns PendingToolApproval if consequential and not yet approved, or null if safe to execute immediately.
     */
    fun checkApprovalRequirement(
        request: ToolExecutionRequest,
        callingProvider: AiProvider
    ): PendingToolApproval? {
        val tool = ToolRegistry.TOOLS.find { it.name == request.toolName } ?: return null
        val args = request.arguments

        when (request.toolName) {
            "create_file" -> {
                val path = args["path"] as? String ?: return null
                val newContent = args["content"] as? String ?: ""
                try {
                    val existingFile = fileManager.resolveSafeFile(path)
                    if (existingFile.exists()) {
                        val oldContent = existingFile.readText(Charsets.UTF_8)
                        val diff = DiffEngine.formatUnifiedDiff(existingFile.name, oldContent, newContent)
                        return PendingToolApproval(
                            request = request,
                            callingProvider = callingProvider,
                            summary = "Agent wants to overwrite existing file '$path'.",
                            targetPath = path,
                            diffPreview = diff
                        )
                    }
                } catch (_: Exception) {}
            }
            "edit_file" -> {
                val path = args["path"] as? String ?: return null
                val findText = args["find_text"] as? String ?: ""
                val replaceText = args["replace_text"] as? String ?: ""
                try {
                    val existingFile = fileManager.resolveSafeFile(path)
                    if (existingFile.exists()) {
                        val oldContent = existingFile.readText(Charsets.UTF_8)
                        val newContent = oldContent.replace(findText, replaceText)
                        val diff = DiffEngine.formatUnifiedDiff(existingFile.name, oldContent, newContent)
                        return PendingToolApproval(
                            request = request,
                            callingProvider = callingProvider,
                            summary = "Agent wants to modify existing file '$path'.",
                            targetPath = path,
                            diffPreview = diff
                        )
                    }
                } catch (_: Exception) {}
            }
        }
        return null
    }

    /**
     * Executes the requested tool and returns structured result.
     */
    fun execute(
        request: ToolExecutionRequest,
        author: MessageSender = MessageSender.SYSTEM
    ): ToolExecutionResult {
        val args = request.arguments
        return try {
            when (request.toolName) {
                "list_files" -> {
                    val subpath = (args["subpath"] as? String) ?: ""
                    val items = fileManager.listFiles(subpath)
                    val jsonArray = JSONArray()
                    items.forEach { item ->
                        val obj = JSONObject()
                        obj.put("name", item.name)
                        obj.put("path", item.relativePath)
                        obj.put("isDirectory", item.isDirectory)
                        obj.put("sizeBytes", item.sizeBytes)
                        jsonArray.put(obj)
                    }
                    val output = JSONObject().put("files", jsonArray).put("count", items.size).toString(2)
                    ToolExecutionResult(request.callId, request.toolName, true, output)
                }

                "read_file" -> {
                    val path = args["path"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing required 'path' argument")
                    val content = fileManager.readFile(path)
                    val output = JSONObject()
                        .put("path", path)
                        .put("content", content)
                        .put("lines", content.lines().size)
                        .toString(2)
                    ToolExecutionResult(request.callId, request.toolName, true, output)
                }

                "create_file" -> {
                    val path = args["path"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'path'")
                    val content = args["content"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'content'")
                    val artifact = fileManager.writeFile(path, content, author, "Created by ${author.displayName}")
                    val output = JSONObject()
                        .put("status", "success")
                        .put("path", artifact.relativePath)
                        .put("bytes", content.toByteArray(Charsets.UTF_8).size)
                        .put("fileType", artifact.fileType.name)
                        .toString(2)
                    ToolExecutionResult(request.callId, request.toolName, true, output)
                }

                "edit_file" -> {
                    val path = args["path"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'path'")
                    val findText = args["find_text"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'find_text'")
                    val replaceText = args["replace_text"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'replace_text'")

                    val artifact = fileManager.editFile(path, findText, replaceText, author)
                    val output = JSONObject()
                        .put("status", "success")
                        .put("path", artifact.relativePath)
                        .put("summary", "Successfully replaced target text")
                        .toString(2)
                    ToolExecutionResult(request.callId, request.toolName, true, output)
                }

                "search_files" -> {
                    val query = args["query"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'query'")
                    val matches = fileManager.searchFiles(query)
                    val jsonArray = JSONArray()
                    matches.forEach { match ->
                        val obj = JSONObject()
                        obj.put("path", match.relativePath)
                        obj.put("line", match.lineNumber)
                        obj.put("snippet", match.lineContent)
                        jsonArray.put(obj)
                    }
                    val output = JSONObject()
                        .put("query", query)
                        .put("totalMatches", matches.size)
                        .put("results", jsonArray)
                        .toString(2)
                    ToolExecutionResult(request.callId, request.toolName, true, output)
                }

                "validate_json_or_config" -> {
                    val content = args["content"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'content'")
                    val format = (args["format"] as? String)?.lowercase() ?: "json"

                    if (format == "json") {
                        try {
                            val trimmed = content.trim()
                            if (trimmed.startsWith("{")) {
                                JSONObject(trimmed)
                            } else if (trimmed.startsWith("[")) {
                                JSONArray(trimmed)
                            } else {
                                throw IllegalArgumentException("JSON must start with { or [")
                            }
                            val output = JSONObject().put("valid", true).put("format", "json").toString()
                            ToolExecutionResult(request.callId, request.toolName, true, output)
                        } catch (e: Exception) {
                            val output = JSONObject()
                                .put("valid", false)
                                .put("format", "json")
                                .put("error", e.message ?: "Malformed JSON")
                                .toString(2)
                            ToolExecutionResult(request.callId, request.toolName, false, output, e.message)
                        }
                    } else {
                        // Properties validation (key=value)
                        val invalidLines = mutableListOf<String>()
                        content.lines().forEachIndexed { index, line ->
                            val trimmed = line.trim()
                            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.contains("=")) {
                                invalidLines.add("Line ${index + 1}: Missing '=' delimiter in '$trimmed'")
                            }
                        }
                        val isValid = invalidLines.isEmpty()
                        val output = JSONObject()
                            .put("valid", isValid)
                            .put("format", "properties")
                            .put("issues", JSONArray(invalidLines))
                            .toString(2)
                        ToolExecutionResult(request.callId, request.toolName, isValid, output, if (isValid) null else "Syntax issues found")
                    }
                }

                "calculate_expression" -> {
                    val expr = args["expression"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'expression'")
                    val evalResult = ExpressionEvaluator.evaluate(expr)
                    if (evalResult.isSuccess) {
                        val value = evalResult.getOrThrow()
                        val formatted = if (value % 1.0 == 0.0 && value in -1e12..1e12) {
                            value.toLong().toString()
                        } else {
                            value.toString()
                        }
                        val output = JSONObject()
                            .put("expression", expr)
                            .put("result", formatted)
                            .put("numericValue", value)
                            .toString(2)
                        ToolExecutionResult(request.callId, request.toolName, true, output)
                    } else {
                        val err = evalResult.exceptionOrNull()?.message ?: "Evaluation failed"
                        ToolExecutionResult(request.callId, request.toolName, false, "", err)
                    }
                }

                "preview_diff" -> {
                    val path = args["path"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'path'")
                    val newContent = args["new_content"] as? String
                        ?: return ToolExecutionResult(request.callId, request.toolName, false, "", "Missing 'new_content'")

                    val oldContent = try { fileManager.readFile(path) } catch (_: Exception) { "" }
                    val diff = DiffEngine.formatUnifiedDiff(path, oldContent, newContent)
                    val output = JSONObject()
                        .put("path", path)
                        .put("diff", diff)
                        .toString(2)
                    ToolExecutionResult(request.callId, request.toolName, true, output)
                }

                else -> {
                    ToolExecutionResult(request.callId, request.toolName, false, "", "Unknown tool: ${request.toolName}")
                }
            }
        } catch (e: Exception) {
            ToolExecutionResult(request.callId, request.toolName, false, "", e.message ?: "Execution error")
        }
    }
}
