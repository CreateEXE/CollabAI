package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.collaboration.CollaborationManager
import com.example.data.local.AppPreferences
import com.example.data.local.WorkspaceFileManager
import com.example.data.model.AiProvider
import com.example.data.model.Artifact
import com.example.data.model.ChatMessage
import com.example.data.model.MessageRecipient
import com.example.data.model.MessageSender
import com.example.data.model.PendingToolApproval
import com.example.data.model.ProviderConfig
import com.example.data.model.ReviewRoundConfig
import com.example.data.security.KeystoreManager
import com.example.provider.ProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class WorkspaceTab(val title: String) {
    CHAT("Chat & Agents"),
    ARTIFACTS("Artifacts"),
    SETTINGS("Settings & Keys")
}

class WorkspaceViewModel(application: Application) : AndroidViewModel(application) {

    val keystoreManager = KeystoreManager(application)
    val fileManager = WorkspaceFileManager(application)
    val appPreferences = AppPreferences(application)

    val collaborationManager = CollaborationManager(
        context = application,
        keystoreManager = keystoreManager,
        fileManager = fileManager,
        appPreferences = appPreferences,
        scope = viewModelScope
    )

    val messages: StateFlow<List<ChatMessage>> = collaborationManager.messages
    val providerConfigs: StateFlow<Map<AiProvider, ProviderConfig>> = collaborationManager.providerConfigs
    val isGenerating: StateFlow<Boolean> = collaborationManager.isGenerating
    val pendingApproval: StateFlow<PendingToolApproval?> = collaborationManager.pendingApproval

    private val _currentTab = MutableStateFlow(WorkspaceTab.CHAT)
    val currentTab: StateFlow<WorkspaceTab> = _currentTab.asStateFlow()

    private val _selectedRecipient = MutableStateFlow(MessageRecipient.ALL)
    val selectedRecipient: StateFlow<MessageRecipient> = _selectedRecipient.asStateFlow()

    private val _artifacts = MutableStateFlow<List<Artifact>>(emptyList())
    val artifacts: StateFlow<List<Artifact>> = _artifacts.asStateFlow()

    private val _selectedArtifact = MutableStateFlow<Artifact?>(null)
    val selectedArtifact: StateFlow<Artifact?> = _selectedArtifact.asStateFlow()

    private val _testingProvider = MutableStateFlow<AiProvider?>(null)
    val testingProvider: StateFlow<AiProvider?> = _testingProvider.asStateFlow()

    private val _testResults = MutableStateFlow<Map<AiProvider, Result<String>>>(emptyMap())
    val testResults: StateFlow<Map<AiProvider, Result<String>>> = _testResults.asStateFlow()

    init {
        refreshArtifacts()
    }

    fun selectTab(tab: WorkspaceTab) {
        _currentTab.value = tab
        if (tab == WorkspaceTab.ARTIFACTS) {
            refreshArtifacts()
        }
    }

    fun setRecipient(recipient: MessageRecipient) {
        _selectedRecipient.value = recipient
    }

    fun sendMessage(prompt: String) {
        collaborationManager.sendMessage(prompt, _selectedRecipient.value)
    }

    fun stopGenerations() {
        collaborationManager.stopAllGenerations()
    }

    fun retryMessage(message: ChatMessage) {
        collaborationManager.retryMessage(message)
    }

    fun clearChat() {
        collaborationManager.clearChat()
    }

    fun requestCritique(message: ChatMessage, reviewer: AiProvider) {
        collaborationManager.requestCritique(message, reviewer)
    }

    fun startReviewRound(primary: AiProvider, reviewer: AiProvider, maxPasses: Int, focus: String) {
        val config = ReviewRoundConfig(primary, reviewer, maxPasses, focus)
        collaborationManager.startReviewRound(config, focus)
    }

    fun submitToolApproval(approved: Boolean) {
        collaborationManager.submitToolApproval(approved)
        refreshArtifacts()
    }

    fun saveApiKey(provider: AiProvider, key: String) {
        keystoreManager.saveEncryptedApiKey(provider, key)
        collaborationManager.refreshConfigs()
    }

    fun testConnection(provider: AiProvider) {
        viewModelScope.launch(Dispatchers.IO) {
            _testingProvider.value = provider
            val key = keystoreManager.getApiKey(provider)
            val config = providerConfigs.value[provider] ?: ProviderConfig(provider)
            val adapter = ProviderFactory.getAdapter(provider)
            val result = adapter.testConnection(key, config.selectedModel)

            _testResults.value = _testResults.value + (provider to result)
            _testingProvider.value = null

            val updatedConfig = config.copy(
                isConnected = result.isSuccess,
                lastTestedAt = System.currentTimeMillis(),
                lastError = result.exceptionOrNull()?.message
            )
            appPreferences.saveProviderConfig(updatedConfig)
            collaborationManager.refreshConfigs()
        }
    }

    fun selectModel(provider: AiProvider, model: String) {
        val config = providerConfigs.value[provider] ?: ProviderConfig(provider)
        val updated = config.copy(selectedModel = model)
        appPreferences.saveProviderConfig(updated)
        collaborationManager.refreshConfigs()
    }

    fun toggleProviderEnabled(provider: AiProvider, enabled: Boolean) {
        val config = providerConfigs.value[provider] ?: ProviderConfig(provider)
        val updated = config.copy(isEnabled = enabled)
        appPreferences.saveProviderConfig(updated)
        collaborationManager.refreshConfigs()
    }

    fun updateTokenWarningLimit(provider: AiProvider, limit: Long) {
        val config = providerConfigs.value[provider] ?: ProviderConfig(provider)
        val updated = config.copy(tokenWarningLimit = limit)
        appPreferences.saveProviderConfig(updated)
        collaborationManager.refreshConfigs()
    }

    fun refreshArtifacts() {
        _artifacts.value = fileManager.getAllArtifacts()
        val currentSel = _selectedArtifact.value
        if (currentSel != null) {
            _selectedArtifact.value = fileManager.getArtifact(currentSel.relativePath)
        }
    }

    fun selectArtifact(artifact: Artifact?) {
        _selectedArtifact.value = artifact
    }

    fun saveArtifact(artifact: Artifact, newContent: String, summary: String = "Manual edit by User") {
        val updated = fileManager.writeFile(
            relativePath = artifact.relativePath,
            content = newContent,
            author = MessageSender.USER,
            changeSummary = summary
        )
        refreshArtifacts()
        _selectedArtifact.value = updated
    }

    fun createArtifact(relativePath: String, content: String) {
        val created = fileManager.writeFile(
            relativePath = relativePath,
            content = content,
            author = MessageSender.USER,
            changeSummary = "Created file"
        )
        refreshArtifacts()
        _selectedArtifact.value = created
    }

    fun deleteArtifact(artifact: Artifact) {
        fileManager.deleteFile(artifact.relativePath)
        if (_selectedArtifact.value?.id == artifact.id) {
            _selectedArtifact.value = null
        }
        refreshArtifacts()
    }
}
