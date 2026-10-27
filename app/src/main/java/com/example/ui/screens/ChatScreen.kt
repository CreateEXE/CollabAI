package com.example.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.ui.WorkspaceViewModel
import com.example.ui.components.ComposerBar
import com.example.ui.components.MessageItem
import com.example.ui.components.ReviewRoundDialog
import com.example.ui.components.ToolApprovalDialog

@Composable
fun ChatScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val providerConfigs by viewModel.providerConfigs.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val selectedRecipient by viewModel.selectedRecipient.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()

    var reviewTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Workspace Header Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GroupWork,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Multi-Agent Workspace",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    // Launch Review Round shortcut
                    val lastAgentMsg = messages.lastOrNull { it.sender.provider != null }
                    if (lastAgentMsg != null) {
                        ElevatedButton(
                            onClick = { reviewTargetMessage = lastAgentMsg },
                            modifier = Modifier.testTag("launch_review_round_header_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.RateReview,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Agent Review", fontSize = 11.sp)
                        }
                    }
                }
            }

            // Chat Messages Timeline
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        onRequestCritique = { target -> reviewTargetMessage = target },
                        onRetry = { retryTarget -> viewModel.retryMessage(retryTarget) }
                    )
                }
            }

            // Message Composer
            ComposerBar(
                selectedRecipient = selectedRecipient,
                onSelectRecipient = { viewModel.setRecipient(it) },
                providerConfigs = providerConfigs,
                isGenerating = isGenerating,
                onSendMessage = { prompt -> viewModel.sendMessage(prompt) },
                onStop = { viewModel.stopGenerations() },
                onClearChat = { viewModel.clearChat() }
            )
        }

        // Consequential Tool Approval Dialog
        if (pendingApproval != null) {
            ToolApprovalDialog(
                approval = pendingApproval!!,
                onApprove = { viewModel.submitToolApproval(true) },
                onReject = { viewModel.submitToolApproval(false) }
            )
        }

        // Agent-to-Agent Review Dialog
        if (reviewTargetMessage != null) {
            ReviewRoundDialog(
                targetMessage = reviewTargetMessage!!,
                onDismiss = { reviewTargetMessage = null },
                onStartReview = { reviewer, maxPasses, focusPrompt ->
                    val target = reviewTargetMessage!!
                    reviewTargetMessage = null
                    viewModel.requestCritique(target, reviewer)
                }
            )
        }
    }
}
