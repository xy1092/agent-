package dev.agentone.ui.pages.calendar

import android.content.ContentResolver
import android.provider.CalendarContract
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.agentone.AgentOneApp
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CalendarEvent(val title: String, val startStr: String, val endStr: String, val location: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarPage() {
    val context = LocalContext.current
    val events = remember { mutableStateListOf<CalendarEvent>() }
    var showAddDialog by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }

    fun loadEvents() {
        events.clear()
        try {
            val now = System.currentTimeMillis()
            val end = now + 30 * 24 * 60 * 60 * 1000L
            val cursor = context.contentResolver.query(
                CalendarContract.Events.CONTENT_URI,
                arrayOf(CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND, CalendarContract.Events.EVENT_LOCATION),
                "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ?",
                arrayOf(now.toString(), end.toString()),
                "${CalendarContract.Events.DTSTART} ASC"
            )
            cursor?.use {
                val fmt = DateTimeFormatter.ofPattern("MM/dd HH:mm")
                while (it.moveToNext()) {
                    val title = it.getString(0) ?: "Untitled"
                    val startMs = it.getLong(1)
                    val endMs = it.getLong(2)
                    val location = it.getString(3) ?: ""
                    val startStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(startMs), ZoneId.systemDefault()).format(fmt)
                    val endStr = LocalDateTime.ofInstant(Instant.ofEpochMilli(endMs), ZoneId.systemDefault()).format(fmt)
                    events.add(CalendarEvent(title, startStr, endStr, location))
                }
            }
        } catch (_: SecurityException) { }
        loaded = true
    }

    if (!loaded) loadEvents()

    Scaffold(
        topBar = { TopAppBar(title = { Text("日历") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "添加事件")
            }
        }
    ) { padding ->
        if (events.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
            ) {
                Text("暂无即将到来的事件")
                Text("请授予日历权限以查看事件", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(events) { event ->
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(event.title, style = MaterialTheme.typography.titleMedium)
                            Text("${event.startStr} - ${event.endStr}", style = MaterialTheme.typography.bodySmall)
                            if (event.location.isNotEmpty()) {
                                Text(event.location, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var title by remember { mutableStateOf("") }
        var location by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        var startDate by remember { mutableStateOf("") }
        var endDate by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加日历事件") },
            text = {
                Column {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") })
                    OutlinedTextField(value = startDate, onValueChange = { startDate = it }, label = { Text("开始 (yyyy-MM-ddTHH:mm)") })
                    OutlinedTextField(value = endDate, onValueChange = { endDate = it }, label = { Text("结束 (yyyy-MM-ddTHH:mm)") })
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") })
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("描述") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                        val startMs = LocalDateTime.parse(startDate, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val endMs = LocalDateTime.parse(endDate, fmt).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val values = android.content.ContentValues().apply {
                            put(CalendarContract.Events.TITLE, title)
                            put(CalendarContract.Events.DTSTART, startMs)
                            put(CalendarContract.Events.DTEND, endMs)
                            put(CalendarContract.Events.EVENT_LOCATION, location)
                            put(CalendarContract.Events.DESCRIPTION, description)
                            put(CalendarContract.Events.CALENDAR_ID, 1)
                            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
                        }
                        context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
                        loadEvents()
                    } catch (_: Exception) { }
                    showAddDialog = false
                }) { Text("添加") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("取消") } }
        )
    }
}
