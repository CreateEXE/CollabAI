package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Difference
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Preview
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Artifact
import com.example.data.model.ArtifactType
import com.example.ui.WorkspaceViewModel
import com.example.ui.components.DiffViewer
import com.example.ui.components.HtmlPreviewView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ArtifactsScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val artifacts by viewModel.artifacts.collectAsState()
    val selectedArtifact by viewModel.selectedArtifact.collectAsState()
    val context = LocalContext.current

    var showCreateDialog by remember { mutableStateOf(false) }

    if (selectedArtifact != null) {
        BackHandler {
            viewModel.selectArtifact(null)
        }

        ArtifactDetailView(
            artifact = selectedArtifact!!,
            onBack = { viewModel.selectArtifact(null) },
            onSave = { updatedText, summary ->
                viewModel.saveArtifact(selectedArtifact!!, updatedText, summary)
            },
            onDelete = {
                viewModel.deleteArtifact(selectedArtifact!!)
            },
            onShare = {
                shareArtifact(context, selectedArtifact!!)
            }
        )
    } else {
        Column(modifier = modifier.fillMaxSize().padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Workspace Artifacts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Row {
                    IconButton(
                        onClick = { viewModel.refreshArtifacts() },
                        modifier = Modifier.testTag("refresh_artifacts_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh files")
                    }
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("create_artifact_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New File", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (artifacts.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No files in workspace yet.\nAsk AI to create files or tap 'New File' above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                ) {
                    items(artifacts, key = { it.relativePath }) { art ->
                        ArtifactListItem(
                            artifact = art,
                            onClick = { viewModel.selectArtifact(art) }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateFileDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { path, content ->
                viewModel.createArtifact(path, content)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun ArtifactListItem(
    artifact: Artifact,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() }
            .testTag("artifact_item_${artifact.name}"),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when (artifact.fileType) {
                ArtifactType.HTML -> Icons.Default.Language
                ArtifactType.KOTLIN -> Icons.Default.Code
                ArtifactType.JSON -> Icons.Default.DataObject
                else -> Icons.Default.Description
            }

            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = artifact.relativePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.outline
                )

                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = artifact.fileType.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "By: ${artifact.lastModifiedBy.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${artifact.snapshots.size} ver",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtifactDetailView(
    artifact: Artifact,
    onBack: () -> Unit,
    onSave: (newContent: String, summary: String) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var selectedTabIdx by remember { mutableIntStateOf(0) }
    var currentText by remember(artifact.content) { mutableStateOf(artifact.content) }
    var changeSummary by remember { mutableStateOf("Manual edit by user") }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val hasHtmlPreview = artifact.fileType == ArtifactType.HTML
    val tabs = mutableListOf("Editor", "Diff")
    if (hasHtmlPreview) tabs.add("Preview")
    tabs.add("History (${artifact.snapshots.size})")

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back_from_artifact_button")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artifact.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${artifact.relativePath} • Last edit: ${artifact.lastModifiedBy.displayName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            IconButton(onClick = onShare) {
                Icon(Icons.Default.Share, contentDescription = "Share file")
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete file", tint = MaterialTheme.colorScheme.error)
            }
        }

        // Tab Selector
        TabRow(selectedTabIndex = selectedTabIdx.coerceAtMost(tabs.size - 1)) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIdx == index,
                    onClick = { selectedTabIdx = index },
                    text = { Text(title, fontSize = 12.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Tab Content
        when (tabs.getOrNull(selectedTabIdx)) {
            "Editor" -> {
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    OutlinedTextField(
                        value = currentText,
                        onValueChange = { currentText = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .testTag("artifact_editor_input"),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        shape = RoundedCornerShape(8.dp)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = changeSummary,
                            onValueChange = { changeSummary = it },
                            placeholder = { Text("Snapshot summary (e.g. Added styles)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { onSave(currentText, changeSummary) },
                            modifier = Modifier.testTag("save_artifact_button")
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save")
                        }
                    }
                }
            }

            "Diff" -> {
                val previousSnapshot = artifact.snapshots.dropLast(1).lastOrNull()
                val oldText = previousSnapshot?.content ?: ""
                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    Text(
                        text = "Comparing current content vs previous snapshot (${previousSnapshot?.summary ?: "Initial"})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DiffViewer(
                        oldText = oldText,
                        newText = currentText,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
            }

            "Preview" -> {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.White, RoundedCornerShape(8.dp))
                ) {
                    HtmlPreviewView(htmlContent = currentText)
                }
            }

            "History (${artifact.snapshots.size})" -> {
                LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    items(artifact.snapshots.reversed()) { snap ->
                        val dateStr = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(Date(snap.timestamp))
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = snap.author.displayName,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(text = dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                Text(text = snap.summary, style = MaterialTheme.typography.bodySmall)

                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        currentText = snap.content
                                        onSave(snap.content, "Restored snapshot from $dateStr")
                                    },
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Restore this version", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Artifact") },
            text = { Text("Are you sure you want to permanently delete '${artifact.name}' from workspace?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CreateFileDialog(
    onDismiss: () -> Unit,
    onCreate: (path: String, content: String) -> Unit
) {
    var path by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Workspace File") },
        text = {
            Column {
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("File Name / Relative Path") },
                    placeholder = { Text("e.g. script.js, index.html, notes.md") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_file_path_input")
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Initial Content (Optional)") },
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("new_file_content_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (path.isNotBlank()) onCreate(path, content) },
                enabled = path.isNotBlank(),
                modifier = Modifier.testTag("confirm_create_file_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun shareArtifact(context: Context, artifact: Artifact) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, artifact.content)
            putExtra(Intent.EXTRA_TITLE, artifact.name)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share ${artifact.name}")
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
