package com.example.tools

object DiffEngine {

    data class DiffLine(
        val type: DiffType,
        val text: String,
        val oldLineNum: Int? = null,
        val newLineNum: Int? = null
    )

    enum class DiffType {
        UNCHANGED,
        ADDED,
        REMOVED
    }

    /**
     * Computes line-by-line diff using classic LCS algorithm.
     */
    fun computeDiff(oldText: String, newText: String): List<DiffLine> {
        val oldLines = if (oldText.isEmpty()) emptyList() else oldText.lines()
        val newLines = if (newText.isEmpty()) emptyList() else newText.lines()

        val n = oldLines.size
        val m = newLines.size

        // DP table for LCS
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0 until n) {
            for (j in 0 until m) {
                if (oldLines[i] == newLines[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        // Backtrack to find differences
        val result = mutableListOf<DiffLine>()
        var i = n
        var j = m

        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && oldLines[i - 1] == newLines[j - 1] -> {
                    result.add(
                        DiffLine(
                            type = DiffType.UNCHANGED,
                            text = oldLines[i - 1],
                            oldLineNum = i,
                            newLineNum = j
                        )
                    )
                    i--
                    j--
                }
                j > 0 && (i == 0 || dp[i][j - 1] >= dp[i - 1][j]) -> {
                    result.add(
                        DiffLine(
                            type = DiffType.ADDED,
                            text = newLines[j - 1],
                            newLineNum = j
                        )
                    )
                    j--
                }
                i > 0 && (j == 0 || dp[i][j - 1] < dp[i - 1][j]) -> {
                    result.add(
                        DiffLine(
                            type = DiffType.REMOVED,
                            text = oldLines[i - 1],
                            oldLineNum = i
                        )
                    )
                    i--
                }
            }
        }

        return result.reversed()
    }

    /**
     * Formats diff into standard Unified Diff format.
     */
    fun formatUnifiedDiff(fileName: String, oldText: String, newText: String): String {
        val diffLines = computeDiff(oldText, newText)
        if (diffLines.all { it.type == DiffType.UNCHANGED }) {
            return "No changes detected for $fileName."
        }

        val sb = StringBuilder()
        sb.appendLine("--- a/$fileName")
        sb.appendLine("+++ b/$fileName")

        var addedCount = 0
        var removedCount = 0

        diffLines.forEach { line ->
            when (line.type) {
                DiffType.UNCHANGED -> sb.appendLine("  ${line.text}")
                DiffType.ADDED -> {
                    addedCount++
                    sb.appendLine("+ ${line.text}")
                }
                DiffType.REMOVED -> {
                    removedCount++
                    sb.appendLine("- ${line.text}")
                }
            }
        }

        sb.appendLine("@@ Summary: +$addedCount additions, -$removedCount deletions @@")
        return sb.toString()
    }
}
