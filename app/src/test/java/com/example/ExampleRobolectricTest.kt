package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WorkspaceFileManager
import com.example.data.model.MessageSender
import com.example.data.model.ToolExecutionRequest
import com.example.tools.ToolExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Collab", appName)
    }

    @Test
    fun workspaceFileManager_fileOperationsAndSandboxSecurity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileManager = WorkspaceFileManager(context)

        // Write file
        val artifact = fileManager.writeFile(
            relativePath = "test.txt",
            content = "Hello World",
            author = MessageSender.USER,
            changeSummary = "Unit test write"
        )
        assertNotNull(artifact)
        assertEquals("test.txt", artifact.name)

        // Read file
        val readBack = fileManager.readFile("test.txt")
        assertEquals("Hello World", readBack)

        // Path traversal outside workspace must throw SecurityException
        var securityExceptionThrown = false
        try {
            fileManager.resolveSafeFile("../../external_forbidden.txt")
        } catch (_: SecurityException) {
            securityExceptionThrown = true
        }
        assertTrue("Path traversal must be forbidden", securityExceptionThrown)
    }

    @Test
    fun toolExecutor_executeValidationAndDiff() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileManager = WorkspaceFileManager(context)
        val executor = ToolExecutor(fileManager)

        // Test list_files
        val listReq = ToolExecutionRequest(
            callId = "1",
            toolName = "list_files",
            arguments = emptyMap()
        )
        val listResult = executor.execute(listReq)
        assertTrue(listResult.isSuccess)

        // Test validate_json_or_config (valid json)
        val jsonReq = ToolExecutionRequest(
            callId = "2",
            toolName = "validate_json_or_config",
            arguments = mapOf("content" to "{\"status\":\"ok\"}", "format" to "json")
        )
        val jsonResult = executor.execute(jsonReq)
        assertTrue(jsonResult.isSuccess)
        assertTrue(jsonResult.output.contains("\"valid\":true") || jsonResult.output.contains("\"valid\": true"))

        // Test validate_json_or_config (invalid json)
        val badJsonReq = ToolExecutionRequest(
            callId = "3",
            toolName = "validate_json_or_config",
            arguments = mapOf("content" to "{broken json", "format" to "json")
        )
        val badResult = executor.execute(badJsonReq)
        assertFalse(badResult.isSuccess)
    }
}
