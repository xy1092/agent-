package dev.agentone.ui.pages.files

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.agentone.AgentOneApp
import java.io.File

data class FileEntry(val name: String, val isDirectory: Boolean, val path: String, val size: Long = 0)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesPage() {
    val filesDir = File(AgentOneApp.instance.filesDir, "workspace").also { it.mkdirs() }
    val currentFiles = remember { mutableStateListOf<FileEntry>() }
    var currentPath by remember { mutableStateOf(filesDir) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<FileEntry?>(null) }
    var selectedFile by remember { mutableStateOf<FileEntry?>(null) }
    var fileContent by remember { mutableStateOf("") }
    var refresh by remember { mutableStateOf(0) }

    fun refreshFiles() {
        currentFiles.clear()
        val parent = currentPath
        if (parent != filesDir) {
            currentFiles.add(FileEntry("..", true, parent.parentFile?.absolutePath ?: ""))
        }
        parent.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { f ->
            currentFiles.add(FileEntry(f.name, f.isDirectory, f.absolutePath, f.length()))
        }
    }

    if (currentFiles.isEmpty()) refreshFiles()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("文件: ${currentPath.name}") },
                actions = {
                    TextButton(onClick = { showCreateDialog = true }) { Text("新建文件") }
                }
            )
        }
    ) { padding ->
        if (selectedFile != null) {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                TextButton(onClick = {
                    val file = File(selectedFile!!.path)
                    if (file.exists()) {
                        file.writeText(fileContent)
                    }
                    selectedFile = null
                    refreshFiles()
                }) { Text("保存") }
                OutlinedTextField(
                    value = fileContent,
                    onValueChange = { fileContent = it },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    label = { Text(selectedFile?.name ?: "") }
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(count = currentFiles.size, key = { currentFiles[it].path }) { index -> val entry = currentFiles[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clickable {
                                if (entry.isDirectory) {
                                    currentPath = File(entry.path)
                                    refreshFiles()
                                } else {
                                    selectedFile = entry
                                    fileContent = File(entry.path).readText()
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                                contentDescription = null,
                                tint = if (entry.isDirectory) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (!entry.isDirectory && entry.size > 0) {
                                    Text("${entry.size} 字节", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            IconButton(onClick = { showDeleteDialog = entry }) {
                                Icon(Icons.Filled.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        var fileName by remember { mutableStateOf("") }
        var fileContent by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("创建文件") },
            text = {
                Column {
                    OutlinedTextField(value = fileName, onValueChange = { fileName = it }, label = { Text("文件名") })
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(value = fileContent, onValueChange = { fileContent = it }, label = { Text("内容") }, maxLines = 4)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (fileName.isNotBlank()) {
                        File(currentPath, fileName).writeText(fileContent)
                        refreshFiles()
                        showCreateDialog = false
                    }
                }) { Text("创建") }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("取消") } }
        )
    }

    showDeleteDialog?.let { entry ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除") },
            text = { Text("确定删除 ${entry.name}？") },
            confirmButton = {
                TextButton(onClick = {
                    File(entry.path).delete()
                    refreshFiles()
                    showDeleteDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = null }) { Text("取消") } }
        )
    }
}
