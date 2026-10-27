package com.example.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.screens.ArtifactsScreen
import com.example.ui.screens.ChatScreen
import com.example.ui.screens.SettingsScreen

@Composable
fun MainScreen(
    viewModel: WorkspaceViewModel,
    modifier: Modifier = Modifier
) {
    val currentTab by viewModel.currentTab.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.testTag("bottom_nav_bar")
            ) {
                NavigationBarItem(
                    selected = currentTab == WorkspaceTab.CHAT,
                    onClick = { viewModel.selectTab(WorkspaceTab.CHAT) },
                    icon = { Icon(Icons.Default.Chat, contentDescription = "Chat & Agents") },
                    label = { Text("Chat") },
                    modifier = Modifier.testTag("nav_tab_chat")
                )
                NavigationBarItem(
                    selected = currentTab == WorkspaceTab.ARTIFACTS,
                    onClick = { viewModel.selectTab(WorkspaceTab.ARTIFACTS) },
                    icon = { Icon(Icons.Default.FolderSpecial, contentDescription = "Artifacts") },
                    label = { Text("Artifacts") },
                    modifier = Modifier.testTag("nav_tab_artifacts")
                )
                NavigationBarItem(
                    selected = currentTab == WorkspaceTab.SETTINGS,
                    onClick = { viewModel.selectTab(WorkspaceTab.SETTINGS) },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings & Keys") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_tab_settings")
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = currentTab, label = "tab_transition") { tab ->
                when (tab) {
                    WorkspaceTab.CHAT -> ChatScreen(viewModel = viewModel)
                    WorkspaceTab.ARTIFACTS -> ArtifactsScreen(viewModel = viewModel)
                    WorkspaceTab.SETTINGS -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
