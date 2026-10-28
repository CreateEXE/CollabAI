package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.data.model.ChatRoom
import com.example.data.model.CollaborationMode

@Composable
fun ChatRulesDialog(
    room: ChatRoom,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onSave: (ChatRoom) -> Unit,
    onDelete: () -> Unit
) {
    var title by remember { mutableStateOf(room.title) }
    var details by remember { mutableStateOf(room.projectDetails) }
    var instructions by remember { mutableStateOf(room.systemInstructions) }
    var selectedMode by remember { mutableStateOf(room.collaborationMode) }
    var maxTurns by remember { mutableFloatStateOf(room.maxAutonomousTurns.toFloat()) }
    var autoStop by remember { mutableStateOf(room.autoStopOnCompletionTag) }

    val participatingProviders = remember {
        mutableStateListOf<AiProvider>().apply { addAll(room.participatingProviders) }
    }

    val responseOrder = remember {
        mutableStateListOf<AiProvider>().apply {
            val initial = if (room.responseOrder.isNotEmpty()) room.responseOrder else room.participatingProviders
            addAll(initial)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("AI Collaboration Rules & Pipeline", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chat Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("edit_room_title")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Project Goal / Details") },
                    placeholder = { Text("What are the AIs building or solving?") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("edit_room_details")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("System Instructions") },
                    placeholder = { Text("Rules for all participating models") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("edit_room_instructions")
                )
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Collaboration Responding Mode:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                CollaborationMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { selectedMode = mode },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 11.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Mode-specific settings
                if (selectedMode == CollaborationMode.SEQUENTIAL_RELAY) {
                    Text(
                        text = "Sequential Response Pipeline Order:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Models respond one after another, building on previous outputs.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    responseOrder.forEachIndexed { index, prov ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            shape = RoundedCornerShape(6.dp),
                            color = prov.brandColor.copy(alpha = 0.08f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(20.dp)
                                )
                                Text(
                                    text = prov.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = prov.brandColor,
                                    modifier = Modifier.weight(1f)
                                )

                                IconButton(
                                    onClick = {
                                        if (index > 0) {
                                            val item = responseOrder.removeAt(index)
                                            responseOrder.add(index - 1, item)
                                        }
                                    },
                                    enabled = index > 0,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                }

                                IconButton(
                                    onClick = {
                                        if (index < responseOrder.size - 1) {
                                            val item = responseOrder.removeAt(index)
                                            responseOrder.add(index + 1, item)
                                        }
                                    },
                                    enabled = index < responseOrder.size - 1,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                if (selectedMode == CollaborationMode.AUTONOMOUS_COMPLETION) {
                    Text(
                        text = "Autonomous Work Loop Controls:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Max autonomous turns: ${maxTurns.toInt()}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Slider(
                        value = maxTurns,
                        onValueChange = { maxTurns = it },
                        valueRange = 1f..8f,
                        steps = 6,
                        modifier = Modifier.fillMaxWidth().testTag("max_turns_slider")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(
                            checked = autoStop,
                            onCheckedChange = { autoStop = it },
                            modifier = Modifier.testTag("auto_stop_switch")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Auto-stop on [PROJECT_COMPLETE]",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Stops when models finish and confirm work is complete",
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                if (canDelete) {
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = onDelete,
                        modifier = Modifier.fillMaxWidth().testTag("delete_room_button"),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete This Chat Room")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = room.copy(
                        title = title.trim().ifEmpty { room.title },
                        projectDetails = details.trim(),
                        systemInstructions = instructions.trim(),
                        collaborationMode = selectedMode,
                        responseOrder = responseOrder.toList(),
                        maxAutonomousTurns = maxTurns.toInt(),
                        autoStopOnCompletionTag = autoStop,
                        updatedAt = System.currentTimeMillis()
                    )
                    onSave(updated)
                },
                modifier = Modifier.testTag("save_room_rules_button")
            ) {
                Text("Save Rules")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
