package dev.agentone.ui.pages.chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.agentone.core.model.ChatMessage
import dev.agentone.core.providers.ToolCall

@Composable
fun TypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    val alpha1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600), repeatMode = RepeatMode.Reverse),
        label = "dot1"
    )
    val alpha2 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 200), repeatMode = RepeatMode.Reverse),
        label = "dot2"
    )
    val alpha3 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(animation = tween(600, delayMillis = 400), repeatMode = RepeatMode.Reverse),
        label = "dot3"
    )

    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(alpha1, alpha2, alpha3).forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("思考中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}


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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(session?.title ?: "聊天", style = MaterialTheme.typography.titleMedium) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { showLogDrawer = !showLogDrawer }) { 
                        Text("日志", color = MaterialTheme.colorScheme.primary) 
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState
            ) {
                items(count = messages.size, key = { messages[it].id }) { index ->
                    val msg = messages[index]
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
                        TypingIndicator()
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("输入消息...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        enabled = !isRunning,
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    if (isRunning) {
                        IconButton(
                            onClick = { viewModel.stopGeneration() },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "停止", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    viewModel.sendMessage(inputText)
                                    inputText = ""
                                }
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "发送",
                                tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
    
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = alignment
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = when {
                    isUser -> MaterialTheme.colorScheme.primary
                    isTool -> MaterialTheme.colorScheme.surfaceVariant
                    else -> MaterialTheme.colorScheme.surface
                }
            ),
            shape = bubbleShape,
            modifier = Modifier.widthIn(max = 300.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                if (isTool && msg.toolName != null) {
                    Text(
                        "TOOL: ${msg.toolName.uppercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isUser) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = msg.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun StreamingBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 4.dp, bottomEnd = 20.dp),
            modifier = Modifier.widthIn(max = 300.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
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
