package com.example.data.local

import android.content.Context
import android.util.Log
import com.example.data.model.Artifact
import com.example.data.model.ArtifactSnapshot
import com.example.data.model.ArtifactType
import com.example.data.model.MessageSender
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WorkspaceFileManager(private val context: Context) {

    val workspaceDir: File = File(context.filesDir, "workspace").apply {
        if (!exists()) mkdirs()
    }

    private val metadataFile: File = File(context.filesDir, "artifacts_metadata.json")

    init {
        // Create initial helpful starter files in workspace if empty
        ensureStarterFiles()
    }

    private fun ensureStarterFiles() {
        try {
            val welcomeFile = resolveSafeFile("welcome.md")
            if (!welcomeFile.exists()) {
                val initialContent = """
                    # Multi-AI Collaboration Workspace
                    
                    Welcome to the shared AI collaboration environment.
                    
                    ### Key Capabilities:
                    - **Shared Chat:** Connect Gemini, OpenAI, and Anthropic.
                    - **Tool Execution:** Models can list, read, edit, search workspace files, evaluate math, and validate configs.
                    - **Agent Critique:** Send any agent's response to another agent for review with bounded rounds.
                    - **Artifact Manager:** Live editing, unified diffs, version snapshots, and sandboxed HTML previews.
                    - **Safe Consequential Tools:** Overwrites and modifications require user confirmation.
                """.trimIndent()
                welcomeFile.parentFile?.mkdirs()
                welcomeFile.writeText(initialContent, Charsets.UTF_8)
                saveArtifactMetadata(
                    Artifact(
                        name = "welcome.md",
                        relativePath = "welcome.md",
                        content = initialContent,
                        fileType = ArtifactType.MARKDOWN,
                        createdBy = MessageSender.SYSTEM,
                        lastModifiedBy = MessageSender.SYSTEM,
                        snapshots = listOf(
                            ArtifactSnapshot(
                                author = MessageSender.SYSTEM,
                                content = initialContent,
                                summary = "Initial workspace setup"
                            )
                        )
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing starter files", e)
        }
    }

    fun resolveSafeFile(relativePath: String): File {
        val sanitized = relativePath.trim().replace("\\", "/").removePrefix("/")
        val target = File(workspaceDir, sanitized).canonicalFile
        val canonicalWorkspace = workspaceDir.canonicalPath
        if (!target.path.startsWith(canonicalWorkspace)) {
            throw SecurityException("Security violation: path traversal outside workspace sandbox is forbidden.")
        }
        return target
    }

    fun listFiles(subPath: String = ""): List<FileItem> {
        val targetDir = if (subPath.isBlank()) workspaceDir else resolveSafeFile(subPath)
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()

        val results = mutableListOf<FileItem>()
        targetDir.listFiles()?.forEach { file ->
            val rel = file.relativeTo(workspaceDir).path.replace("\\", "/")
            results.add(
                FileItem(
                    name = file.name,
                    relativePath = rel,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isFile) file.length() else 0L,
                    lastModified = file.lastModified()
                )
            )
        }
        return results.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    fun readFile(relativePath: String): String {
        val file = resolveSafeFile(relativePath)
        if (!file.exists()) throw NoSuchFileException(file, reason = "File does not exist in workspace.")
        if (file.isDirectory) throw IllegalArgumentException("Target is a directory, not a file.")
        return file.readText(Charsets.UTF_8)
    }

    fun writeFile(
        relativePath: String,
        content: String,
        author: MessageSender,
        changeSummary: String = "File updated"
    ): Artifact {
        val file = resolveSafeFile(relativePath)
        file.parentFile?.mkdirs()

        val oldContent = if (file.exists()) file.readText(Charsets.UTF_8) else ""
        file.writeText(content, Charsets.UTF_8)

        val existingArtifact = getArtifact(relativePath)
        val now = System.currentTimeMillis()

        val snapshot = ArtifactSnapshot(
            author = author,
            content = content,
            summary = changeSummary,
            timestamp = now
        )

        val updatedSnapshots = if (existingArtifact != null) {
            existingArtifact.snapshots + snapshot
        } else {
            listOf(snapshot)
        }

        val artifact = Artifact(
            id = existingArtifact?.id ?: java.util.UUID.randomUUID().toString(),
            name = file.name,
            relativePath = file.relativeTo(workspaceDir).path.replace("\\", "/"),
            content = content,
            fileType = ArtifactType.fromPath(file.name),
            createdBy = existingArtifact?.createdBy ?: author,
            lastModifiedBy = author,
            createdAt = existingArtifact?.createdAt ?: now,
            updatedAt = now,
            snapshots = updatedSnapshots
        )

        saveArtifactMetadata(artifact)
        return artifact
    }

    fun editFile(
        relativePath: String,
        findText: String,
        replaceText: String,
        author: MessageSender
    ): Artifact {
        val file = resolveSafeFile(relativePath)
        if (!file.exists()) throw NoSuchFileException(file, reason = "File to edit does not exist.")

        val current = file.readText(Charsets.UTF_8)
        if (!current.contains(findText)) {
            throw IllegalArgumentException("Target string to replace was not found in file: '$findText'")
        }

        val updated = current.replace(findText, replaceText)
        return writeFile(
            relativePath = relativePath,
            content = updated,
            author = author,
            changeSummary = "Replaced: '$findText' -> '$replaceText'"
        )
    }

    fun searchFiles(query: String): List<SearchResult> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val results = mutableListOf<SearchResult>()
        workspaceDir.walkTopDown().filter { it.isFile }.forEach { file ->
            try {
                val lines = file.readLines(Charsets.UTF_8)
                lines.forEachIndexed { idx, line ->
                    if (line.contains(cleanQuery, ignoreCase = true)) {
                        results.add(
                            SearchResult(
                                relativePath = file.relativeTo(workspaceDir).path.replace("\\", "/"),
                                lineNumber = idx + 1,
                                lineContent = line.trim()
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Skipping file in search: ${file.name}", e)
            }
        }
        return results
    }

    fun deleteFile(relativePath: String): Boolean {
        val file = resolveSafeFile(relativePath)
        val deleted = file.delete()
        if (deleted) {
            removeArtifactMetadata(relativePath)
        }
        return deleted
    }

    fun getAllArtifacts(): List<Artifact> {
        val map = loadAllMetadata()
        // Synchronize with existing files
        val list = mutableListOf<Artifact>()
        workspaceDir.walkTopDown().filter { it.isFile }.forEach { file ->
            val rel = file.relativeTo(workspaceDir).path.replace("\\", "/")
            val existing = map[rel]
            if (existing != null) {
                list.add(existing.copy(content = file.readText(Charsets.UTF_8)))
            } else {
                val content = file.readText(Charsets.UTF_8)
                val newArt = Artifact(
                    name = file.name,
                    relativePath = rel,
                    content = content,
                    fileType = ArtifactType.fromPath(file.name),
                    createdBy = MessageSender.SYSTEM,
                    lastModifiedBy = MessageSender.SYSTEM,
                    createdAt = file.lastModified(),
                    updatedAt = file.lastModified(),
                    snapshots = listOf(
                        ArtifactSnapshot(
                            author = MessageSender.SYSTEM,
                            content = content,
                            summary = "Discovered in workspace"
                        )
                    )
                )
                list.add(newArt)
            }
        }
        return list.sortedByDescending { it.updatedAt }
    }

    fun getArtifact(relativePath: String): Artifact? {
        val normalized = relativePath.trim().replace("\\", "/").removePrefix("/")
        return loadAllMetadata()[normalized]
    }

    @Synchronized
    private fun saveArtifactMetadata(artifact: Artifact) {
        val map = loadAllMetadata().toMutableMap()
        map[artifact.relativePath] = artifact
        persistMetadataMap(map)
    }

    @Synchronized
    private fun removeArtifactMetadata(relativePath: String) {
        val normalized = relativePath.trim().replace("\\", "/").removePrefix("/")
        val map = loadAllMetadata().toMutableMap()
        map.remove(normalized)
        persistMetadataMap(map)
    }

    private fun persistMetadataMap(map: Map<String, Artifact>) {
        try {
            val root = JSONObject()
            val array = JSONArray()
            map.values.forEach { art ->
                val obj = JSONObject()
                obj.put("id", art.id)
                obj.put("name", art.name)
                obj.put("relativePath", art.relativePath)
                obj.put("fileType", art.fileType.name)
                obj.put("createdAt", art.createdAt)
                obj.put("updatedAt", art.updatedAt)

                val snapArray = JSONArray()
                art.snapshots.forEach { snap ->
                    val sObj = JSONObject()
                    sObj.put("snapshotId", snap.snapshotId)
                    sObj.put("timestamp", snap.timestamp)
                    sObj.put("authorType", snap.author.type.name)
                    sObj.put("authorProvider", snap.author.provider?.name)
                    sObj.put("content", snap.content)
                    sObj.put("summary", snap.summary)
                    snapArray.put(sObj)
                }
                obj.put("snapshots", snapArray)
                array.put(obj)
            }
            root.put("artifacts", array)
            metadataFile.writeText(root.toString(2), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving artifact metadata", e)
        }
    }

    private fun loadAllMetadata(): Map<String, Artifact> {
        if (!metadataFile.exists()) return emptyMap()
        return try {
            val content = metadataFile.readText(Charsets.UTF_8)
            val root = JSONObject(content)
            val array = root.optJSONArray("artifacts") ?: return emptyMap()
            val result = mutableMapOf<String, Artifact>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val relPath = obj.getString("relativePath")
                val name = obj.optString("name", relPath.substringAfterLast('/'))
                val type = try {
                    ArtifactType.valueOf(obj.optString("fileType", "TEXT"))
                } catch (_: Exception) {
                    ArtifactType.TEXT
                }

                val snapList = mutableListOf<ArtifactSnapshot>()
                val snapArray = obj.optJSONArray("snapshots")
                if (snapArray != null) {
                    for (j in 0 until snapArray.length()) {
                        val sObj = snapArray.getJSONObject(j)
                        val authorProvider = sObj.optString("authorProvider", null)?.let {
                            try { com.example.data.model.AiProvider.valueOf(it) } catch (_: Exception) { null }
                        }
                        val sender = if (authorProvider != null) MessageSender.ai(authorProvider) else MessageSender.SYSTEM
                        snapList.add(
                            ArtifactSnapshot(
                                snapshotId = sObj.optString("snapshotId"),
                                timestamp = sObj.optLong("timestamp"),
                                author = sender,
                                content = sObj.optString("content"),
                                summary = sObj.optString("summary")
                            )
                        )
                    }
                }

                val currentFile = File(workspaceDir, relPath)
                val currentText = if (currentFile.exists() && currentFile.isFile) currentFile.readText(Charsets.UTF_8) else ""

                result[relPath] = Artifact(
                    id = obj.optString("id"),
                    name = name,
                    relativePath = relPath,
                    content = currentText,
                    fileType = type,
                    createdBy = snapList.firstOrNull()?.author ?: MessageSender.SYSTEM,
                    lastModifiedBy = snapList.lastOrNull()?.author ?: MessageSender.SYSTEM,
                    createdAt = obj.optLong("createdAt"),
                    updatedAt = obj.optLong("updatedAt"),
                    snapshots = snapList
                )
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error loading metadata", e)
            emptyMap()
        }
    }

    data class FileItem(
        val name: String,
        val relativePath: String,
        val isDirectory: Boolean,
        val sizeBytes: Long,
        val lastModified: Long
    )

    data class SearchResult(
        val relativePath: String,
        val lineNumber: Int,
        val lineContent: String
    )

    companion object {
        private const val TAG = "WorkspaceFileManager"
    }
}
