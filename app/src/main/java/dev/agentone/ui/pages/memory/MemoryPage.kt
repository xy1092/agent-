package dev.agentone.ui.pages.memory

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.agentone.AgentOneApp
import dev.agentone.core.model.MemoryEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryPage() {
    val app = AgentOneApp.instance
    val db = app.database
    val security = app.securityManager
    val memoryDao = db.memoryEntryDao()

    var memoryEnabled by remember { mutableStateOf(security.isMemoryEnabled()) }
    var searchQuery by remember { mutableStateOf("") }
    val entries = remember { mutableStateOf<List<MemoryEntry>>(emptyList()) }

    fun loadEntries() {
        entries.value = runBlocking {
            if (searchQuery.isNotBlank()) memoryDao.search(searchQuery).first()
            else memoryDao.observeAll().first()
        }
    }

    if (entries.value.isEmpty()) loadEntries()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Memory") },
                actions = {
                    Text("${if (memoryEnabled) "ON" else "OFF"}")
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = {
                            memoryEnabled = it
                            security.setMemoryEnabled(it)
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search memories...") },
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = { loadEntries() }) {
                    Icon(Icons.Filled.Search, contentDescription = "Search")
                }
            }

            val list = entries.value
            if (list.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("No memories found")
                    if (!memoryEnabled) {
                        Text("Memory is disabled", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                LazyColumn {
                    items(count = list.size, key = { list[it].id }) { index -> val entry = list[index]
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                        if (entry.tags.isNotEmpty()) {
                                            Text(entry.tags, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    IconButton(onClick = {
                                        runBlocking { memoryDao.deleteById(entry.id) }
                                        loadEntries()
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
