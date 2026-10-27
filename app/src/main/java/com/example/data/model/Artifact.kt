package com.example.data.model

import java.util.UUID

enum class ArtifactType(val extension: String, val displayName: String) {
    HTML("html", "HTML Document"),
    KOTLIN("kt", "Kotlin Source"),
    JSON("json", "JSON Config / Data"),
    MARKDOWN("md", "Markdown Doc"),
    CSS("css", "CSS Stylesheet"),
    JAVASCRIPT("js", "JavaScript Code"),
    TEXT("txt", "Plain Text");

    companion object {
        fun fromPath(path: String): ArtifactType {
            val ext = path.substringAfterLast('.', "").lowercase()
            return entries.find { it.extension == ext } ?: TEXT
        }
    }
}

data class ArtifactSnapshot(
    val snapshotId: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val author: MessageSender,
    val content: String,
    val summary: String
)

data class Artifact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val relativePath: String,
    val content: String,
    val fileType: ArtifactType,
    val createdBy: MessageSender,
    val lastModifiedBy: MessageSender,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val snapshots: List<ArtifactSnapshot> = emptyList()
)
