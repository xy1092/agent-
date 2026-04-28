package dev.agentone.ui.pages.chat

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.agentone.core.model.ChatMessage
import dev.agentone.core.providers.ToolCall

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatPage(sessionId: String, onNavigateBack: () -> Unit) {
    val viewModel: ChatViewModel = viewModel(factory = ChatViewModel.Factory(sessionId))
    val session by viewModel.session.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val pendingApprovals by viewModel.pendingApprovals.collectAsState()
    val replyText by viewModel.replyText.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showLogDrawer by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, replyText) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(session?.title ?: "聊天") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showLogDrawer = !showLogDrawer }) { Text("日志") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState
            ) {
                items(count = messages.size, key = { messages[it].id }) { index -> val msg = messages[index]
                    MessageBubble(msg)
                }

                if (pendingApprovals.isNotEmpty()) {
                    item {
                        ToolApprovalCard(
                            toolCalls = pendingApprovals,
                            onApprove = { viewModel.approveTools(pendingApprovals.map { it.id }.toSet()) },
                            onReject = { viewModel.rejectAllTools() }
                        )
                    }
                }

                if (isRunning && replyText.isNotEmpty()) {
                    item {
                        StreamingBubble(replyText)
                    }
                }

                if (isRunning && replyText.isEmpty() && pendingApprovals.isEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("思考中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("输入消息...") },
                    modifier = Modifier.weight(1f),
                    enabled = !isRunning,
                    maxLines = 4
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isRunning) {
                    IconButton(onClick = { viewModel.stopGeneration() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                    }
                } else {
                    IconButton(onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                    }
                }
            }
        }
    }

    if (showLogDrawer) {
        AlertDialog(
            onDismissRequest = { showLogDrawer = false },
            title = { Text("Agent 日志") },
            text = {
                val logs by viewModel.agentLogs.collectAsState()
                if (logs.isEmpty()) {
                    Text("暂无日志记录。")
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(logs) { log ->
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    "[${log.type}] ${log.summary}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDrawer = false }) { Text("关闭") }
            }
        )
    }
}

@Composable
fun MessageBubble(msg: ChatMessage) {
    val isUser = msg.role == "user"
    val isTool = msg.role == "tool_result"
    val alignment = if (isUser) Arrangement.End else Arrangement.Start

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> MaterialTheme.colorScheme.primaryContainer
                    isTool -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isTool && msg.toolName != null) {
                    Text(
                        "工具: ${msg.toolName} (${msg.status ?: ""})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(text = msg.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun StreamingBubble(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .widthIn(max = 320.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun ToolApprovalCard(
    toolCalls: List<ToolCall>,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("需要工具执行确认", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            toolCalls.forEach { tc ->
                Text("- ${tc.name}", style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row {
                Button(onClick = onApprove) { Text("批准") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onReject) { Text("全部拒绝") }
            }
        }
    }
}
