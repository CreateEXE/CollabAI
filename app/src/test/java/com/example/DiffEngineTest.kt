package com.example

import com.example.tools.DiffEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiffEngineTest {

    @Test
    fun testUnchangedContent() {
        val old = "line 1\nline 2"
        val new = "line 1\nline 2"
        val diff = DiffEngine.computeDiff(old, new)

        assertTrue(diff.all { it.type == DiffEngine.DiffType.UNCHANGED })
        assertEquals(2, diff.size)
    }

    @Test
    fun testAddedLines() {
        val old = "line 1"
        val new = "line 1\nline 2"
        val diff = DiffEngine.computeDiff(old, new)

        assertEquals(2, diff.size)
        assertEquals(DiffEngine.DiffType.UNCHANGED, diff[0].type)
        assertEquals(DiffEngine.DiffType.ADDED, diff[1].type)
        assertEquals("line 2", diff[1].text)
    }

    @Test
    fun testRemovedLines() {
        val old = "line 1\nline 2"
        val new = "line 1"
        val diff = DiffEngine.computeDiff(old, new)

        assertEquals(2, diff.size)
        assertEquals(DiffEngine.DiffType.UNCHANGED, diff[0].type)
        assertEquals(DiffEngine.DiffType.REMOVED, diff[1].type)
        assertEquals("line 2", diff[1].text)
    }

    @Test
    fun testUnifiedDiffFormat() {
        val old = "val a = 10"
        val new = "val a = 20"
        val formatted = DiffEngine.formatUnifiedDiff("App.kt", old, new)

        assertTrue(formatted.contains("--- a/App.kt"))
        assertTrue(formatted.contains("+++ b/App.kt"))
        assertTrue(formatted.contains("- val a = 10"))
        assertTrue(formatted.contains("+ val a = 20"))
    }
}
