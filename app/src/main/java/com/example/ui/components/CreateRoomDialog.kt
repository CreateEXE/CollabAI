package com.example.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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

@Composable
fun CreateRoomDialog(
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        details: String,
        instructions: String,
        providers: List<AiProvider>,
        mode: com.example.data.model.CollaborationMode
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("Use workspace tools to create real code files. Review and build upon each other's work.") }
    val selectedProviders = remember {
        mutableStateListOf(AiProvider.GEMINI, AiProvider.GROQ, AiProvider.OPENROUTER)
    }
    var selectedMode by remember { mutableStateOf(com.example.data.model.CollaborationMode.SEQUENTIAL_RELAY) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text("Create AI Group Chat Room", fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Configure a multi-AI collaborative project room with participating models and custom guidelines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Group Chat Title") },
                    placeholder = { Text("e.g. Web Game Project, Architecture Review") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("room_title_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Project Goal / Details (Optional)") },
                    placeholder = { Text("e.g. Building an interactive canvas game with CSS") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("room_details_input")
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("System Instructions for All AIs") },
                    placeholder = { Text("e.g. Focus on concise code, inspect artifacts") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth().testTag("room_instructions_input")
                )
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Collaboration Responding Rule:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                com.example.data.model.CollaborationMode.entries.forEach { mode ->
                    val isChecked = selectedMode == mode
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { selectedMode = mode },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isChecked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = mode.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = mode.description,
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 10.sp,
                                color = if (isChecked) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Participating AI Models:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))

                AiProvider.entries.forEach { provider ->
                    val isChecked = selectedProviders.contains(provider)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable {
                                if (isChecked) {
                                    if (selectedProviders.size > 1) selectedProviders.remove(provider)
                                } else {
                                    selectedProviders.add(provider)
                                }
                            },
                        shape = RoundedCornerShape(6.dp),
                        color = if (isChecked) provider.brandColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (!selectedProviders.contains(provider)) selectedProviders.add(provider)
                                    } else {
                                        if (selectedProviders.size > 1) selectedProviders.remove(provider)
                                    }
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = provider.brandColor
                                    )
                                    if (provider.isFreeTierAvailable) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "FREE KEY",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = provider.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onCreate(title, details, instructions, selectedProviders.toList(), selectedMode)
                    }
                },
                enabled = title.isNotBlank() && selectedProviders.isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_room_button")
            ) {
                Text("Create Group Chat")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
