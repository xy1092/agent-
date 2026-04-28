package dev.agentone.ui.pages.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import dev.agentone.core.database.AgentOneDatabase
import dev.agentone.core.providers.ProviderType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage() {
    val app = AgentOneApp.instance
    val security = app.securityManager
    val db = app.database

    var autoApprove by remember { mutableStateOf(security.isAutoApproveLowRisk()) }
    var memoryEnabled by remember { mutableStateOf(security.isMemoryEnabled()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf<ProviderType?>(null) }
    var apiKeyInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Provider API Keys
            Text(
                "API 密钥",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            ProviderType.entries.forEach { type ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable {
                            showApiKeyDialog = type
                            apiKeyInput = security.getApiKey(type.name.lowercase()) ?: ""
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(type.name)
                            val hasKey = security.getApiKey(type.name.lowercase()) != null
                            Text(
                                if (hasKey) "已配置" else "未配置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Agent settings
            Text(
                "Agent 设置",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("自动批准低风险工具")
                        Text("读取操作无需确认即可执行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = autoApprove,
                        onCheckedChange = {
                            autoApprove = it
                            security.setAutoApproveLowRisk(it)
                        }
                    )
                }
            }

            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("记忆")
                        Text("启用长期记忆存储", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = memoryEnabled,
                        onCheckedChange = {
                            memoryEnabled = it
                            security.setMemoryEnabled(it)
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Data management
            Text(
                "数据",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .clickable { showClearDialog = true }
            ) {
                Text(
                    "清除所有数据",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                "AgentOne v0.1.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
            Text(
                "私人 AI Agent 工作空间",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清除所有数据") },
            text = { Text("这将删除所有会话、消息、记忆、提醒和设置。此操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    kotlinx.coroutines.runBlocking {
                        db.sessionDao().deleteAll()
                    }
                    security.clearAll()
                    showClearDialog = false
                }) { Text("全部清除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            }
        )
    }

    showApiKeyDialog?.let { type ->
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = null },
            title = { Text("API 密钥: ${type.name}") },
            text = {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API 密钥") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (apiKeyInput.isNotBlank()) {
                        security.saveApiKey(type.name.lowercase(), apiKeyInput)
                    } else {
                        security.deleteApiKey(type.name.lowercase())
                    }
                    showApiKeyDialog = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = null }) { Text("取消") }
            }
        )
    }
}
