package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.tools.DiffEngine

@Composable
fun DiffViewer(
    oldText: String,
    newText: String,
    modifier: Modifier = Modifier
) {
    val diffLines = DiffEngine.computeDiff(oldText, newText)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            diffLines.forEach { line ->
                val (bgColor, textColor, prefix) = when (line.type) {
                    DiffEngine.DiffType.UNCHANGED -> Triple(Color.Transparent, MaterialTheme.colorScheme.onSurfaceVariant, "  ")
                    DiffEngine.DiffType.ADDED -> Triple(Color(0xFF1B5E20).copy(alpha = 0.25f), Color(0xFF4CAF50), "+ ")
                    DiffEngine.DiffType.REMOVED -> Triple(Color(0xFFB71C1C).copy(alpha = 0.25f), Color(0xFFEF5350), "- ")
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor, RoundedCornerShape(2.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = line.newLineNum?.toString() ?: line.oldLineNum?.toString() ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.width(28.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$prefix${line.text}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = textColor
                    )
                }
            }
        }
    }
}
