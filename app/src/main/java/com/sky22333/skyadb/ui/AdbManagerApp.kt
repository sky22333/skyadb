package com.sky22333.skyadb.ui

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sky22333.skyadb.ui.apps.AppsScreen
import com.sky22333.skyadb.ui.device.DeviceScreen
import com.sky22333.skyadb.ui.diagnostics.DiagnosticLogScreen
import com.sky22333.skyadb.ui.discovery.DeviceDiscoveryScreen
import com.sky22333.skyadb.ui.download.OnlineDownloadScreen
import com.sky22333.skyadb.ui.files.FileTransferScreen
import com.sky22333.skyadb.ui.home.HomeScreen
import com.sky22333.skyadb.ui.install.InstallApkScreen
import com.sky22333.skyadb.ui.localapps.LocalAppsScreen
import com.sky22333.skyadb.ui.logs.SystemLogScreen
import com.sky22333.skyadb.ui.mirror.MirrorScreen
import com.sky22333.skyadb.ui.pairing.PairingScreen
import com.sky22333.skyadb.ui.remote.RemoteControlScreen
import com.sky22333.skyadb.ui.screenshot.ScreenshotScreen
import com.sky22333.skyadb.ui.settings.SettingsScreen
import com.sky22333.skyadb.ui.shared.LocalSharedTransitionScope
import com.sky22333.skyadb.ui.shared.SharedToolKeys
import com.sky22333.skyadb.ui.shared.appComposable
import com.sky22333.skyadb.ui.shell.ShellScreen

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AdbManagerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val bottomRoutes = remember { bottomDestinations.map { it.route }.toSet() }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            if (currentDestination?.route != MirrorRoute &&
                currentDestination?.route != AppDestination.Files.route
            ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 0.dp,
                    windowInsets = WindowInsets.navigationBars,
                ) {
                    bottomDestinations.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        val bottomPadding = padding.calculateBottomPadding()

        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                NavHost(
                    navController = navController,
                    startDestination = AppDestination.Home.route,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        val tabSwitch = initialState.destination.route in bottomRoutes &&
                            targetState.destination.route in bottomRoutes
                        fadeIn(animationSpec = tween(if (tabSwitch) 120 else 180))
                    },
                    exitTransition = {
                        val tabSwitch = initialState.destination.route in bottomRoutes &&
                            targetState.destination.route in bottomRoutes
                        fadeOut(animationSpec = tween(if (tabSwitch) 100 else 140))
                    },
                    popEnterTransition = {
                        fadeIn(animationSpec = tween(180))
                    },
                    popExitTransition = {
                        fadeOut(animationSpec = tween(140))
                    },
                ) {
                    appComposable(AppDestination.Home.route) {
                        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
                        val discoveredHostState = savedStateHandle
                            ?.getStateFlow(DiscoveryHostKey, "")
                            ?.collectAsState()
                            ?: remember { mutableStateOf("") }
                        val discoveredPortState = savedStateHandle
                            ?.getStateFlow(DiscoveryPortKey, "")
                            ?.collectAsState()
                            ?: remember { mutableStateOf("") }
                        val discoveredHost by discoveredHostState
                        val discoveredPort by discoveredPortState
                        HomeScreen(
                            bottomPadding = bottomPadding,
                            onPairingClick = { navController.navigate(AppDestination.Pairing.route) },
                            onDiscoveryClick = { navController.navigate(AppDestination.Discovery.route) },
                            discoveredHost = discoveredHost,
                            discoveredPort = discoveredPort,
                            onDiscoveredEndpointConsumed = {
                                savedStateHandle?.remove<String>(DiscoveryHostKey)
                                savedStateHandle?.remove<String>(DiscoveryPortKey)
                            },
                        )
                    }
                    appComposable(AppDestination.Device.route) {
                        DeviceScreen(
                            bottomPadding = bottomPadding,
                            onAppsClick = { navController.navigate(AppDestination.Apps.route) },
                            onLocalAppsClick = { navController.navigate(AppDestination.LocalApps.route) },
                            onInstallClick = { navController.navigate(AppDestination.Install.route) },
                            onDownloadClick = { navController.navigate(AppDestination.Download.route) },
                            onFilesClick = { navController.navigate(AppDestination.Files.route) },
                            onScreenshotClick = { navController.navigate(AppDestination.Screenshot.route) },
                            onShellClick = { navController.navigate(AppDestination.Shell.route) },
                            onRemoteClick = { navController.navigate(RemoteRoute) },
                            onMirrorClick = { navController.navigate(MirrorRoute) },
                            onLogsClick = { navController.navigate(LogsRoute) },
                        )
                    }
                    appComposable(AppDestination.Settings.route) {
                        SettingsScreen(
                            bottomPadding = bottomPadding,
                            onDiagnosticsClick = { navController.navigate(DiagnosticsRoute) },
                        )
                    }
                    appComposable(AppDestination.Pairing.route) {
                        val savedStateHandle = navController.currentBackStackEntry?.savedStateHandle
                        val pairingHostState = savedStateHandle
                            ?.getStateFlow(PairingHostKey, "")
                            ?.collectAsState()
                            ?: remember { mutableStateOf("") }
                        val pairingPortState = savedStateHandle
                            ?.getStateFlow(PairingPortKey, "")
                            ?.collectAsState()
                            ?: remember { mutableStateOf("") }
                        val pairingHost by pairingHostState
                        val pairingPort by pairingPortState
                        PairingScreen(
                            bottomPadding = bottomPadding,
                            onBackClick = { navController.popBackStack() },
                            discoveredHost = pairingHost,
                            discoveredPort = pairingPort,
                            onDiscoveredEndpointConsumed = {
                                savedStateHandle?.remove<String>(PairingHostKey)
                                savedStateHandle?.remove<String>(PairingPortKey)
                            },
                        )
                    }
                    appComposable(AppDestination.Discovery.route) {
                        DeviceDiscoveryScreen(
                            bottomPadding = bottomPadding,
                            onBackClick = { navController.popBackStack() },
                            onUseEndpoint = { host, port ->
                                navController.previousBackStackEntry?.savedStateHandle?.set(DiscoveryHostKey, host)
                                navController.previousBackStackEntry?.savedStateHandle?.set(DiscoveryPortKey, port.toString())
                                navController.popBackStack()
                            },
                            onPairEndpoint = { host, port ->
                                navController.navigate(AppDestination.Pairing.route)
                                navController.currentBackStackEntry?.savedStateHandle?.set(PairingHostKey, host)
                                navController.currentBackStackEntry?.savedStateHandle?.set(PairingPortKey, port.toString())
                            },
                        )
                    }
                    appComposable(AppDestination.Shell.route, SharedToolKeys.Shell) {
                        ShellScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(AppDestination.Apps.route, SharedToolKeys.Apps) {
                        AppsScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(AppDestination.LocalApps.route, SharedToolKeys.LocalApps) {
                        LocalAppsScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(AppDestination.Download.route, SharedToolKeys.Download) {
                        OnlineDownloadScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(AppDestination.Install.route, SharedToolKeys.Install) {
                        InstallApkScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(AppDestination.Files.route, SharedToolKeys.Files) {
                        FileTransferScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(AppDestination.Screenshot.route, SharedToolKeys.Screenshot) {
                        ScreenshotScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(RemoteRoute, SharedToolKeys.Remote) {
                        RemoteControlScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(MirrorRoute) {
                        MirrorScreen(onBackClick = { navController.popBackStack() })
                    }
                    appComposable(LogsRoute, SharedToolKeys.Logs) {
                        SystemLogScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                    appComposable(DiagnosticsRoute) {
                        DiagnosticLogScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

private val bottomDestinations = listOf(
    AppDestination.Home,
    AppDestination.Device,
    AppDestination.Settings,
)

private sealed class AppDestination(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    data object Home : AppDestination("home", "设备", Icons.Outlined.Devices)
    data object Device : AppDestination("device", "详情", Icons.Outlined.PhoneAndroid)
    data object Settings : AppDestination("settings", "设置", Icons.Outlined.Settings)
    data object Pairing : AppDestination("pairing", "配对", Icons.Outlined.PhoneAndroid)
    data object Discovery : AppDestination("discovery", "扫描", Icons.Outlined.Devices)
    data object Shell : AppDestination("shell", "Shell", Icons.Outlined.PhoneAndroid)
    data object Apps : AppDestination("apps", "应用", Icons.Outlined.PhoneAndroid)
    data object LocalApps : AppDestination("local_apps", "本机应用", Icons.Outlined.PhoneAndroid)
    data object Download : AppDestination("download", "下载", Icons.Outlined.PhoneAndroid)
    data object Install : AppDestination("install", "安装", Icons.Outlined.PhoneAndroid)
    data object Files : AppDestination("files", "文件", Icons.Outlined.PhoneAndroid)
    data object Screenshot : AppDestination("screenshot", "截图", Icons.Outlined.PhoneAndroid)
}

private const val DiscoveryHostKey = "discovery_host"
private const val DiscoveryPortKey = "discovery_port"
private const val PairingHostKey = "pairing_host"
private const val PairingPortKey = "pairing_port"
private const val RemoteRoute = "remote"
private const val MirrorRoute = "mirror"
private const val LogsRoute = "logs"
private const val DiagnosticsRoute = "diagnostics"
