package dev.agentone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.agentone.navigation.Screen
import dev.agentone.navigation.SubScreen
import dev.agentone.navigation.bottomNavItems
import dev.agentone.ui.pages.browser.BrowserPage
import dev.agentone.ui.pages.calendar.CalendarPage
import dev.agentone.ui.pages.chat.ChatPage
import dev.agentone.ui.pages.files.FilesPage
import dev.agentone.ui.pages.memory.MemoryPage
import dev.agentone.ui.pages.onboarding.OnboardingPage
import dev.agentone.ui.pages.reminders.RemindersPage
import dev.agentone.ui.pages.sessions.SessionsPage
import dev.agentone.ui.pages.settings.SettingsPage
import dev.agentone.ui.theme.AgentOneTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AgentOneTheme {
                AgentOneMain()
            }
        }
    }
}

@Composable
fun AgentOneMain() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                        NavigationBarItem(
                            icon = {
                                Icon(
                                    imageVector = if (selected) screen.selectedIcon else screen.icon,
                                    contentDescription = screen.label
                                )
                            },
                            label = { Text(screen.label) },
                            selected = selected,
                            onClick = {
                                navController.navigate(screen.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = if (AgentOneApp.instance.securityManager.isOnboardingComplete()) Screen.Sessions.route else SubScreen.Onboarding.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(SubScreen.Onboarding.route) {
                OnboardingPage(onComplete = { navController.navigate(Screen.Sessions.route) })
            }

            composable(Screen.Sessions.route) {
                val navigateToChat = { sessionId: String -> navController.navigate(SubScreen.Chat.createRoute(sessionId)) }
                SessionsPage(
                    onSessionClick = navigateToChat,
                    onSessionCreated = navigateToChat
                )
            }

            composable(
                route = SubScreen.Chat.route,
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                ChatPage(sessionId = sessionId, onNavigateBack = { navController.popBackStack() })
            }

            composable(Screen.Browser.route) { BrowserPage() }
            composable(Screen.Files.route) { FilesPage() }
            composable(Screen.Calendar.route) { CalendarPage() }
            composable(Screen.Reminders.route) { RemindersPage() }
            composable(Screen.Memory.route) { MemoryPage() }
            composable(Screen.Settings.route) { SettingsPage() }
        }
    }
}
