package com.gmini.android.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.gmini.android.data.AppSettings
import com.gmini.android.di.appContainer
import com.gmini.android.service.AgentConnectionService
import com.gmini.android.ui.chat.ChatScreen
import com.gmini.android.ui.navigation.Routes
import com.gmini.android.ui.navigation.topDestinations
import com.gmini.android.ui.pairing.PairingScreen
import com.gmini.android.ui.sessions.SessionsScreen
import com.gmini.android.ui.settings.SettingsScreen
import com.gmini.android.ui.tasks.TasksScreen
import com.gmini.android.ui.theme.GminiTheme

@Composable
fun GminiApp(initialPairingLink: String? = null) {
    val context = LocalContext.current
    val container = remember { context.appContainer() }
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())

    GminiTheme(themeMode = settings.themeMode) {
        val activeProfile by container.repository.activeProfile.collectAsStateWithLifecycle()
        val navController = rememberNavController()
        val factory = remember(container) { GminiViewModelFactory(container) }

        LaunchedEffectStartService(
            enabled = settings.backgroundConnectionEnabled,
            hasProfile = activeProfile != null,
        )

        val hasProfile = activeProfile != null
        val startDestination =
            if (!hasProfile || initialPairingLink != null) Routes.PAIRING else Routes.CHAT_WITH_SESSION

        val backStackEntry by navController.currentBackStackEntryAsState()
        val baseRoute = backStackEntry?.destination?.route?.substringBefore('?')
        val showBottomBar = hasProfile && baseRoute in topDestinations.map { it.route }

        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar {
                        topDestinations.forEach { item ->
                            val selected = baseRoute == item.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(item.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = startDestination,
                modifier = Modifier.padding(padding),
            ) {
                composable(Routes.PAIRING) {
                    PairingScreen(
                        factory = factory,
                        initialLink = initialPairingLink,
                        canGoBack = hasProfile,
                        onBack = { navController.popBackStack() },
                        onPaired = {
                            navController.navigate(Routes.CHAT) {
                                popUpTo(Routes.PAIRING) { inclusive = true }
                            }
                        },
                    )
                }
                composable(
                    route = Routes.CHAT_WITH_SESSION,
                    arguments = listOf(
                        navArgument("sessionId") {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    ChatScreen(
                        factory = factory,
                        resumeSessionId = entry.arguments?.getString("sessionId"),
                    )
                }
                composable(Routes.SESSIONS) {
                    SessionsScreen(
                        factory = factory,
                        onOpenSession = { sessionId ->
                            navController.navigate(Routes.chatForSession(sessionId)) {
                                launchSingleTop = true
                            }
                        },
                    )
                }
                composable(Routes.TASKS) {
                    TasksScreen(factory = factory)
                }
                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        factory = factory,
                        onAddServer = { navController.navigate(Routes.PAIRING) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LaunchedEffectStartService(enabled: Boolean, hasProfile: Boolean) {
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(enabled, hasProfile) {
        if (enabled && hasProfile) {
            AgentConnectionService.start(context)
        }
    }
}
