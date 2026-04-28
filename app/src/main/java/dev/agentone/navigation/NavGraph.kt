package dev.agentone.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector, val selectedIcon: ImageVector) {
    data object Sessions : Screen("sessions", "会话", Icons.Outlined.Chat, Icons.Filled.Chat)
    data object Browser : Screen("browser", "浏览器", Icons.Outlined.Language, Icons.Filled.Language)
    data object Files : Screen("files", "文件", Icons.Outlined.Folder, Icons.Filled.Folder)
    data object Calendar : Screen("calendar", "日历", Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth)
    data object Reminders : Screen("reminders", "提醒", Icons.Outlined.Notifications, Icons.Filled.Notifications)
    data object Memory : Screen("memory", "记忆", Icons.Outlined.Memory, Icons.Filled.Memory)
    data object Settings : Screen("settings", "设置", Icons.Outlined.Settings, Icons.Filled.Settings)
}

sealed class SubScreen(val route: String) {
    data object Onboarding : SubScreen("onboarding")
    data object Chat : SubScreen("chat/{sessionId}") {
        fun createRoute(sessionId: String) = "chat/$sessionId"
    }
}

val bottomNavItems = listOf(
    Screen.Sessions,
    Screen.Browser,
    Screen.Files,
    Screen.Calendar,
    Screen.Reminders,
    Screen.Memory,
    Screen.Settings
)
