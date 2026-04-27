package dev.agentone.ui.pages.sessions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.agentone.AgentOneApp
import dev.agentone.core.model.ChatSession
import dev.agentone.core.model.ProviderConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsPage(onSessionClick: (String) -> Unit) {
    val viewModel: SessionsViewModel = viewModel()
    val sessions by viewModel.sessions
    var showNewDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ChatSession?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("AgentOne") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showNewDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New Session")
            }
        }
    ) { padding ->
        if (sessions.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("No sessions yet", style = MaterialTheme.typography.bodyLarge)
                Text("Tap + to create one", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onClick = { onSessionClick(session.id) },
                        onDelete = { showDeleteDialog = session },
                        onTogglePin = { viewModel.togglePin(session.id, !session.pinned) }
                    )
                }
            }
        }
    }

    if (showNewDialog) {
        NewSessionDialog(
            onDismiss = { showNewDialog = false },
            onCreate = { providerId, modelId ->
                viewModel.createSession(providerId, modelId)
                showNewDialog = false
            }
        )
    }

    showDeleteDialog?.let { session ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Session") },
            text = { Text("Delete \"${session.title}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSession(session.id)
                    showDeleteDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun SessionCard(
    session: ChatSession,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (session.pinned) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${session.providerId} / ${session.modelId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    Icons.Filled.PushPin, contentDescription = "Pin",
                    tint = if (session.pinned) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionDialog(
    onDismiss: () -> Unit,
    onCreate: (providerId: String, modelId: String) -> Unit
) {
    val db = AgentOneApp.instance.database
    val registry = AgentOneApp.instance.providerRegistry
    var availableProviders by remember { mutableStateOf<List<ProviderConfig>>(emptyList()) }
    var selectedProviderId by remember { mutableStateOf("fake") }
    var selectedModel by remember { mutableStateOf("fake-v1") }
    var customModel by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        availableProviders = db.providerConfigDao().getEnabled()
        if (availableProviders.isNotEmpty()) {
            selectedProviderId = availableProviders.first().id
            try {
                selectedModel = registry.getByTypeString(availableProviders.first().type).defaultModels().firstOrNull() ?: "default"
            } catch (_: Exception) { }
        }
    }

    val defaultModels = remember(selectedProviderId, availableProviders) {
        val config = availableProviders.find { it.id == selectedProviderId }
        if (config != null) {
            try { registry.getByTypeString(config.type).defaultModels() }
            catch (_: Exception) { listOf("default") }
        } else listOf("default")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Session") },
        text = {
            Column {
                Text("Provider", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    OutlinedTextField(
                        value = availableProviders.find { it.id == selectedProviderId }?.displayName ?: selectedProviderId,
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        availableProviders.forEach { config ->
                            DropdownMenuItem(
                                text = { Text(config.displayName) },
                                onClick = {
                                    selectedProviderId = config.id
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("Model", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(4.dp))
                defaultModels.forEach { model ->
                    TextButton(onClick = { selectedModel = model }) {
                        Text(if (selectedModel == model) "> $model" else model)
                    }
                }
                OutlinedTextField(
                    value = customModel,
                    onValueChange = { customModel = it; if (it.isNotBlank()) selectedModel = it },
                    label = { Text("Or custom model ID") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(selectedProviderId, selectedModel) }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
