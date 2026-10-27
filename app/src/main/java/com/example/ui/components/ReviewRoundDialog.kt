package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage

@Composable
fun ReviewRoundDialog(
    targetMessage: ChatMessage,
    onDismiss: () -> Unit,
    onStartReview: (reviewer: AiProvider, maxPasses: Int, focusPrompt: String) -> Unit
) {
    // Determine possible reviewers (exclude the original author if it's an AI)
    val availableReviewers = AiProvider.entries.filter { it != targetMessage.sender.provider }
    var selectedReviewer by remember { mutableStateOf(availableReviewers.firstOrNull() ?: AiProvider.GEMINI) }
    var maxPasses by remember { mutableIntStateOf(1) }
    var focusPrompt by remember {
        mutableStateOf("Review this response for correctness, edge cases, tool accuracy, and propose specific refinements.")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.RateReview,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text("Agent-to-Agent Review", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Request a structured review of ${targetMessage.sender.displayName}'s response.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Select Reviewer Agent:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                availableReviewers.forEach { prov ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { selectedReviewer = prov },
                        shape = RoundedCornerShape(8.dp),
                        color = if (selectedReviewer == prov) prov.brandColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            RadioButton(
                                selected = selectedReviewer == prov,
                                onClick = { selectedReviewer = prov }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = prov.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedReviewer == prov) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedReviewer == prov) prov.brandColor else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Bounded Review Passes (Max calls):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(1, 2).forEach { passes ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { maxPasses = passes }
                                .padding(end = 16.dp)
                        ) {
                            RadioButton(
                                selected = maxPasses == passes,
                                onClick = { maxPasses = passes }
                            )
                            Text("$passes pass${if (passes > 1) "es" else ""}")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = focusPrompt,
                    onValueChange = { focusPrompt = it },
                    label = { Text("Review Instructions / Focus") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onStartReview(selectedReviewer, maxPasses, focusPrompt) },
                modifier = Modifier.testTag("start_review_button")
            ) {
                Text("Start Review")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
