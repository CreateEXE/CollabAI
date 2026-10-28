package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.GroupWork
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.example.data.model.AiProvider
import com.example.data.model.ChatMessage
import com.example.data.model.ChatRoom
import com.example.ui.WorkspaceViewModel
import com.example.ui.components.ChatRulesDialog
import com.example.ui.components.ComposerBar
import com.example.ui.components.CreateRoomDialog
import com.example.ui.components.MessageItem
import com.example.ui.components.ReviewRoundDialog
import com.example.ui.components.ToolApprovalDialog

@Composable
fun ChatScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val messages by viewModel.messages.collectAsState()
    val chatRooms by viewModel.chatRooms.collectAsState()
    val activeRoom by viewModel.activeChatRoom.collectAsState()
    val providerConfigs by viewModel.providerConfigs.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val selectedRecipient by viewModel.selectedRecipient.collectAsState()
    val pendingApproval by viewModel.pendingApproval.collectAsState()

    var reviewTargetMessage by remember { mutableStateOf<ChatMessage?>(null) }
    var showCreateRoomDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var roomMenuExpanded by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()

    // Scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Workspace Header Bar with Group Chat Switcher & Rules
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Room Selector Dropdown
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .clickable { roomMenuExpanded = true }
                                    .testTag("room_selector_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Forum,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = activeRoom.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = roomMenuExpanded,
                                onDismissRequest = { roomMenuExpanded = false }
                            ) {
                                chatRooms.forEach { room ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(room.title, fontWeight = if (room.id == activeRoom.id) FontWeight.Bold else FontWeight.Normal)
                                                Text(
                                                    "${room.collaborationMode.displayName} • ${room.participatingProviders.size} AIs",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontSize = 10.sp,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.selectChatRoom(room.id)
                                            roomMenuExpanded = false
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("+ Create New Group Chat", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                        }
                                    },
                                    onClick = {
                                        roomMenuExpanded = false
                                        showCreateRoomDialog = true
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        // Rules Button
                        OutlinedButton(
                            onClick = { showRulesDialog = true },
                            modifier = Modifier.testTag("open_chat_rules_button"),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rules & Order", fontSize = 11.sp)
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Launch Review Round shortcut
                        val lastAgentMsg = messages.lastOrNull { it.sender.provider != null }
                        if (lastAgentMsg != null) {
                            ElevatedButton(
                                onClick = { reviewTargetMessage = lastAgentMsg },
                                modifier = Modifier.testTag("launch_review_round_header_button"),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.RateReview,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Review", fontSize = 11.sp)
                            }
                        }
                    }

                    // Room Subtitle Bar: Mode pill & participating AIs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = when (activeRoom.collaborationMode) {
                                    com.example.data.model.CollaborationMode.SEQUENTIAL_RELAY ->
                                        "Relay: " + activeRoom.responseOrder.joinToString(" → ") { it.displayName }
                                    com.example.data.model.CollaborationMode.AUTONOMOUS_COMPLETION ->
                                        "Autonomous (max ${activeRoom.maxAutonomousTurns} turns)"
                                    com.example.data.model.CollaborationMode.PARALLEL_BROADCAST ->
                                        "Parallel Broadcast"
                                    com.example.data.model.CollaborationMode.MANUAL_DIRECTED ->
                                        "Manual Directed"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            activeRoom.participatingProviders.forEach { prov ->
                                Surface(
                                    color = prov.brandColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.padding(end = 4.dp)
                                ) {
                                    Text(
                                        text = prov.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontSize = 9.sp,
                                        color = prov.brandColor,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Quick Tool & Multi-Agent Starters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistChip(
                    onClick = {
                        viewModel.sendMessage("Create an interactive HTML artifact named app.html with a modern dashboard, responsive CSS styling, and a JavaScript counter widget using the create_file tool.")
                    },
                    label = { Text("🌐 Generate HTML App", fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                AssistChip(
                    onClick = {
                        val last = messages.lastOrNull { it.sender.provider != null } ?: messages.firstOrNull()
                        if (last != null) reviewTargetMessage = last
                    },
                    label = { Text("🤝 Multi-Agent Round", fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                AssistChip(
                    onClick = {
                        viewModel.sendMessage("Use the list_files and search_files tools to find and summarize all files in our workspace.")
                    },
                    label = { Text("🔍 Search Files", fontSize = 11.sp) },
                    modifier = Modifier.padding(end = 6.dp)
                )
                AssistChip(
                    onClick = {
                        viewModel.sendMessage("Calculate 128 * 4 + 2^5 using the calculate_expression tool and validate this JSON config with validate_json_or_config: {\"status\": \"active\", \"version\": 1.0}")
                    },
                    label = { Text("🧮 Math & Config Tools", fontSize = 11.sp) }
                )
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
                        onRetry = { retryTarget -> viewModel.retryMessage(retryTarget) },
                        onOpenArtifact = { relativePath -> viewModel.openArtifact(relativePath) },
                        onReplyWithAgent = { targetMsg, respondingProv ->
                            viewModel.replyToAgent(targetMsg, respondingProv)
                        }
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
                    val primary = target.sender.provider ?: AiProvider.GEMINI
                    reviewTargetMessage = null
                    viewModel.startReviewRound(
                        primary = primary,
                        reviewer = reviewer,
                        maxPasses = maxPasses,
                        focus = focusPrompt
                    )
                }
            )
        }

        // Create Room Dialog
        if (showCreateRoomDialog) {
            CreateRoomDialog(
                onDismiss = { showCreateRoomDialog = false },
                onCreate = { title, details, instructions, providers, mode ->
                    showCreateRoomDialog = false
                    viewModel.createChatRoom(
                        title = title,
                        projectDetails = details,
                        systemInstructions = instructions,
                        participatingProviders = providers,
                        collaborationMode = mode
                    )
                }
            )
        }

        // Room Rules & Response Order Dialog
        if (showRulesDialog) {
            ChatRulesDialog(
                room = activeRoom,
                canDelete = chatRooms.size > 1,
                onDismiss = { showRulesDialog = false },
                onSave = { updatedRoom ->
                    showRulesDialog = false
                    viewModel.updateChatRoom(updatedRoom)
                },
                onDelete = {
                    showRulesDialog = false
                    viewModel.deleteChatRoom(activeRoom.id)
                }
            )
        }
    }
}
