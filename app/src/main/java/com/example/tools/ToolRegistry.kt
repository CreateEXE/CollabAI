package com.example.tools

import com.example.data.model.ToolDefinition
import com.example.data.model.ToolParameterProperty
import org.json.JSONArray
import org.json.JSONObject

object ToolRegistry {

    val TOOLS: List<ToolDefinition> = listOf(
        ToolDefinition(
            name = "list_files",
            description = "List all files and subdirectories in the internal workspace sandbox.",
            parameters = mapOf(
                "subpath" to ToolParameterProperty(
                    type = "string",
                    description = "Optional relative subpath to list within workspace. Defaults to root.",
                    isRequired = false
                )
            ),
            isConsequential = false
        ),
        ToolDefinition(
            name = "read_file",
            description = "Read full contents of a file in the workspace sandbox.",
            parameters = mapOf(
                "path" to ToolParameterProperty(
                    type = "string",
                    description = "Relative path of the file to read (e.g. 'welcome.md' or 'src/App.kt').",
                    isRequired = true
                )
            ),
            isConsequential = false
        ),
        ToolDefinition(
            name = "create_file",
            description = "Create or overwrite a file in the workspace sandbox. If the file already exists, this is treated as a consequential edit requiring user approval.",
            parameters = mapOf(
                "path" to ToolParameterProperty(
                    type = "string",
                    description = "Relative path where file should be created.",
                    isRequired = true
                ),
                "content" to ToolParameterProperty(
                    type = "string",
                    description = "Full text content of the file to write.",
                    isRequired = true
                )
            ),
            isConsequential = true
        ),
        ToolDefinition(
            name = "edit_file",
            description = "Edit an existing file by replacing a unique text chunk with new text. Produces a diff before applying.",
            parameters = mapOf(
                "path" to ToolParameterProperty(
                    type = "string",
                    description = "Relative path of file to edit.",
                    isRequired = true
                ),
                "find_text" to ToolParameterProperty(
                    type = "string",
                    description = "Exact existing text snippet to replace.",
                    isRequired = true
                ),
                "replace_text" to ToolParameterProperty(
                    type = "string",
                    description = "New replacement text snippet.",
                    isRequired = true
                )
            ),
            isConsequential = true
        ),
        ToolDefinition(
            name = "search_files",
            description = "Search for a text query across all workspace text files.",
            parameters = mapOf(
                "query" to ToolParameterProperty(
                    type = "string",
                    description = "Text query or keyword to search for.",
                    isRequired = true
                )
            ),
            isConsequential = false
        ),
        ToolDefinition(
            name = "validate_json_or_config",
            description = "Validate JSON syntax or basic key-value configuration format. Returns validation status and line-level errors.",
            parameters = mapOf(
                "content" to ToolParameterProperty(
                    type = "string",
                    description = "Content string to validate.",
                    isRequired = true
                ),
                "format" to ToolParameterProperty(
                    type = "string",
                    description = "Format to check: 'json' or 'properties'. Defaults to 'json'.",
                    isRequired = false
                )
            ),
            isConsequential = false
        ),
        ToolDefinition(
            name = "calculate_expression",
            description = "Safely evaluate a mathematical expression (e.g. '25 * (10 + 4) / 2', '2^8').",
            parameters = mapOf(
                "expression" to ToolParameterProperty(
                    type = "string",
                    description = "Mathematical expression to evaluate.",
                    isRequired = true
                )
            ),
            isConsequential = false
        ),
        ToolDefinition(
            name = "preview_diff",
            description = "Generate a unified diff preview comparing an existing file's current text with proposed new text.",
            parameters = mapOf(
                "path" to ToolParameterProperty(
                    type = "string",
                    description = "Relative path of the target file.",
                    isRequired = true
                ),
                "new_content" to ToolParameterProperty(
                    type = "string",
                    description = "Proposed new text content.",
                    isRequired = true
                )
            ),
            isConsequential = false
        )
    )

    /**
     * Serializes tools into Gemini functionDeclarations format.
     */
    fun toGeminiToolsJson(): JSONArray {
        val toolsArray = JSONArray()
        val funcDecls = JSONArray()

        TOOLS.forEach { tool ->
            val decl = JSONObject()
            decl.put("name", tool.name)
            decl.put("description", tool.description)

            val paramsObj = JSONObject()
            paramsObj.put("type", "OBJECT")

            val propsObj = JSONObject()
            val reqArray = JSONArray()

            tool.parameters.forEach { (paramName, prop) ->
                val p = JSONObject()
                p.put("type", prop.type.uppercase())
                p.put("description", prop.description)
                propsObj.put(paramName, p)
                if (prop.isRequired) {
                    reqArray.put(paramName)
                }
            }

            paramsObj.put("properties", propsObj)
            if (reqArray.length() > 0) {
                paramsObj.put("required", reqArray)
            }
            decl.put("parameters", paramsObj)
            funcDecls.put(decl)
        }

        val container = JSONObject()
        container.put("functionDeclarations", funcDecls)
        toolsArray.put(container)
        return toolsArray
    }

    /**
     * Serializes tools into OpenAI format.
     */
    fun toOpenAiToolsJson(): JSONArray {
        val toolsArray = JSONArray()

        TOOLS.forEach { tool ->
            val toolObj = JSONObject()
            toolObj.put("type", "function")

            val funcObj = JSONObject()
            funcObj.put("name", tool.name)
            funcObj.put("description", tool.description)

            val paramsObj = JSONObject()
            paramsObj.put("type", "object")

            val propsObj = JSONObject()
            val reqArray = JSONArray()

            tool.parameters.forEach { (paramName, prop) ->
                val p = JSONObject()
                p.put("type", prop.type.lowercase())
                p.put("description", prop.description)
                propsObj.put(paramName, p)
                if (prop.isRequired) {
                    reqArray.put(paramName)
                }
            }

            paramsObj.put("properties", propsObj)
            if (reqArray.length() > 0) {
                paramsObj.put("required", reqArray)
            }
            funcObj.put("parameters", paramsObj)
            toolObj.put("function", funcObj)
            toolsArray.put(toolObj)
        }

        return toolsArray
    }

    /**
     * Serializes tools into Anthropic format.
     */
    fun toAnthropicToolsJson(): JSONArray {
        val toolsArray = JSONArray()

        TOOLS.forEach { tool ->
            val toolObj = JSONObject()
            toolObj.put("name", tool.name)
            toolObj.put("description", tool.description)

            val schemaObj = JSONObject()
            schemaObj.put("type", "object")

            val propsObj = JSONObject()
            val reqArray = JSONArray()

            tool.parameters.forEach { (paramName, prop) ->
                val p = JSONObject()
                p.put("type", prop.type.lowercase())
                p.put("description", prop.description)
                propsObj.put(paramName, p)
                if (prop.isRequired) {
                    reqArray.put(paramName)
                }
            }

            schemaObj.put("properties", propsObj)
            if (reqArray.length() > 0) {
                schemaObj.put("required", reqArray)
            }
            toolObj.put("input_schema", schemaObj)
            toolsArray.put(toolObj)
        }

        return toolsArray
    }
}
