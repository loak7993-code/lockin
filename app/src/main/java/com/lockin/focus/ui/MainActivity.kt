package com.lockin.focus.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.lockin.focus.LockIn
import com.lockin.focus.ui.screens.BlockListScreen
import com.lockin.focus.ui.screens.HistoryScreen
import com.lockin.focus.ui.screens.HomeScreen
import com.lockin.focus.ui.screens.OnboardingScreen
import com.lockin.focus.ui.screens.SettingsScreen
import com.lockin.focus.ui.theme.LockInTheme
import com.lockin.focus.util.GuardPermissions
import com.lockin.focus.util.Permissions
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        LockIn.refreshHomePackages()
        setContent {
            val settings by LockIn.settingsFlow.collectAsStateWithLifecycle()
            LockInTheme(
                themeMode = settings.themeMode,
                seed = settings.seed,
                customSeed = settings.customSeedArgb,
                dynamicColor = settings.dynamicColor,
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    LockInApp()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        LockIn.refreshHomePackages()
    }
}

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    LOCK("lock", "Lock", Icons.Rounded.Lock),
    BLOCKS("blocks", "Blocked", Icons.Rounded.Block),
    HISTORY("history", "History", Icons.Rounded.History),
    SETTINGS("settings", "Settings", Icons.Rounded.Settings),
}

@Composable
fun LockInApp() {
    val context = LocalContext.current
    val viewModel: MainViewModel = viewModel()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val session by viewModel.session.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()

    var permissions by remember { mutableStateOf(Permissions.read(context)) }
    val requestNotifications = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { permissions = Permissions.read(context) }

    // The user leaves for Accessibility settings and comes back: re-read, do not
    // ask them to confirm anything.
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            val fresh = Permissions.read(context)
            if (fresh != permissions) permissions = fresh
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !permissions.notificationsOn &&
            settings.onboarded
        ) {
            requestNotifications.launch(Permissions.notificationPermission())
        }
    }

    if (!settings.onboarded) {
        OnboardingScreen(
            settings = settings,
            permissions = permissions,
            viewModel = viewModel,
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    Scaffold(
        // The screens below each own their own Scaffold, so this one must not also
        // apply system bar insets — doing so would add the status bar height twice.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    val selected = currentRoute?.hierarchy?.any { it.route == tab.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Tab.LOCK.route,
            ) {
                composable(Tab.LOCK.route) {
                    HomeScreen(
                        state = session,
                        settings = settings,
                        history = history,
                        permissions = permissions,
                        viewModel = viewModel,
                        onOpenBlocks = {
                            navController.navigate(Tab.BLOCKS.route) { launchSingleTop = true }
                        },
                        onOpenAccessibilitySettings = {
                            Permissions.openAccessibilitySettings(context)
                        },
                        onRollGoal = {
                            context.startActivity(
                                Intent(context, GoalActivity::class.java)
                                    .putExtra(GoalActivity.EXTRA_VOLUNTARY, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                    )
                }
                composable(Tab.BLOCKS.route) {
                    BlockListScreen(
                        settings = settings,
                        installedApps = viewModel.installedApps,
                        loading = viewModel.appsLoading,
                        failed = viewModel.appsFailed,
                        viewModel = viewModel,
                    )
                }
                composable(Tab.HISTORY.route) {
                    HistoryScreen(history = history, viewModel = viewModel)
                }
                composable(Tab.SETTINGS.route) {
                    SettingsScreen(
                        settings = settings,
                        permissions = permissions,
                        viewModel = viewModel,
                    )
                }
            }

            // In-app echo of a check-in that is owed. The guard raises the full-screen
            // takeover on its own; this is the way back to it if the takeover was
            // suppressed (a call, a permission dialog, split screen). It sits above
            // the navigation bar rather than at the top, where it would cover the
            // screen's title.
            AnimatedVisibility(
                visible = session.owesCheckIn,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = fadeIn() + slideInVertically { it },
                exit = fadeOut() + slideOutVertically { it },
            ) {
                CheckInBanner(
                    onAnswer = {
                        context.startActivity(
                            android.content.Intent(context, CheckpointActivity::class.java)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun CheckInBanner(onAnswer: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Bolt, contentDescription = null)
            Text(
                text = "Check-in waiting for an answer",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onAnswer) { Text("Answer") }
        }
    }
}
