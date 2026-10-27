package com.example

import com.example.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolRegistryTest {

    @Test
    fun testToolDefinitionsExist() {
        val toolNames = ToolRegistry.TOOLS.map { it.name }
        assertTrue(toolNames.contains("list_files"))
        assertTrue(toolNames.contains("read_file"))
        assertTrue(toolNames.contains("create_file"))
        assertTrue(toolNames.contains("edit_file"))
        assertTrue(toolNames.contains("search_files"))
        assertTrue(toolNames.contains("validate_json_or_config"))
        assertTrue(toolNames.contains("calculate_expression"))
        assertTrue(toolNames.contains("preview_diff"))
    }

    @Test
    fun testGeminiSerialization() {
        val json = ToolRegistry.toGeminiToolsJson()
        assertNotNull(json)
        assertTrue(json.length() > 0)
        val first = json.getJSONObject(0)
        assertTrue(first.has("functionDeclarations"))
    }

    @Test
    fun testOpenAiSerialization() {
        val json = ToolRegistry.toOpenAiToolsJson()
        assertNotNull(json)
        assertEquals(ToolRegistry.TOOLS.size, json.length())
        val first = json.getJSONObject(0)
        assertEquals("function", first.getString("type"))
    }

    @Test
    fun testAnthropicSerialization() {
        val json = ToolRegistry.toAnthropicToolsJson()
        assertNotNull(json)
        assertEquals(ToolRegistry.TOOLS.size, json.length())
        val first = json.getJSONObject(0)
        assertTrue(first.has("input_schema"))
    }
}
