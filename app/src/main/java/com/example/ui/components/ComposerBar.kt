package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiProvider
import com.example.data.model.MessageRecipient
import com.example.data.model.MessageRecipientType
import com.example.data.model.ProviderConfig

@Composable
fun ComposerBar(
    selectedRecipient: MessageRecipient,
    onSelectRecipient: (MessageRecipient) -> Unit,
    providerConfigs: Map<AiProvider, ProviderConfig>,
    isGenerating: Boolean,
    onSendMessage: (String) -> Unit,
    onStop: () -> Unit,
    onClearChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Recipient Selector Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Broadcast Chip
                val isAllSelected = selectedRecipient.type == MessageRecipientType.ALL
                FilterChip(
                    selected = isAllSelected,
                    onClick = { onSelectRecipient(MessageRecipient.ALL) },
                    label = { Text("Broadcast (All)", fontSize = 12.sp) },
                    modifier = Modifier.padding(end = 6.dp).testTag("recipient_chip_all"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )

                // Individual Provider Chips
                AiProvider.entries.forEach { provider ->
                    val isSelected = selectedRecipient.type == MessageRecipientType.SINGLE_PROVIDER &&
                            selectedRecipient.provider == provider
                    val config = providerConfigs[provider]
                    val isConnected = config?.isConnected == true

                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectRecipient(MessageRecipient.to(provider)) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isConnected -> Color(0xFF4CAF50) // Verified connected
                                            config?.lastTestedAt != null -> Color(0xFFF44336) // Failed
                                            else -> Color.Gray
                                        }
                                    )
                            )
                        },
                        label = {
                            Text(
                                text = provider.displayName.removePrefix("Google ").removePrefix("Anthropic "),
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.padding(end = 6.dp).testTag("recipient_chip_${provider.id}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = provider.brandColor.copy(alpha = 0.2f),
                            selectedLabelColor = provider.brandColor
                        )
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onClearChat,
                    modifier = Modifier.size(32.dp).testTag("clear_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear conversation",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Text Input & Send / Stop Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            text = when (selectedRecipient.type) {
                                MessageRecipientType.ALL -> "Message all connected AI agents..."
                                MessageRecipientType.SINGLE_PROVIDER -> "Message ${selectedRecipient.provider?.displayName}..."
                            },
                            fontSize = 13.sp
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("message_input"),
                    shape = RoundedCornerShape(20.dp),
                    maxLines = 4
                )

                Spacer(modifier = Modifier.width(6.dp))

                if (isGenerating) {
                    Button(
                        onClick = onStop,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("stop_generation_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Stop generation",
                            tint = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            if (text.isNotBlank()) {
                                onSendMessage(text)
                                text = ""
                            }
                        },
                        enabled = text.isNotBlank(),
                        shape = CircleShape,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
