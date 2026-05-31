package com.sky22333.skyadb.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sky22333.skyadb.ui.apps.AppsScreen
import com.sky22333.skyadb.ui.device.DeviceScreen
import com.sky22333.skyadb.ui.discovery.DeviceDiscoveryScreen
import com.sky22333.skyadb.ui.download.OnlineDownloadScreen
import com.sky22333.skyadb.ui.files.FileTransferScreen
import com.sky22333.skyadb.ui.fastboot.FastbootScreen
import com.sky22333.skyadb.ui.home.HomeScreen
import com.sky22333.skyadb.ui.install.InstallApkScreen
import com.sky22333.skyadb.ui.localapps.LocalAppsScreen
import com.sky22333.skyadb.ui.logs.SystemLogScreen
import com.sky22333.skyadb.ui.pairing.PairingScreen
import com.sky22333.skyadb.ui.remote.RemoteControlScreen
import com.sky22333.skyadb.ui.screenshot.ScreenshotScreen
import com.sky22333.skyadb.ui.settings.SettingsScreen
import com.sky22333.skyadb.ui.shell.ShellScreen
import com.sky22333.skyadb.ui.usb.UsbAdbScreen

@Composable
fun AdbManagerApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
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
        },
    ) { padding ->
        val bottomPadding = padding.calculateBottomPadding()

        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.fillMaxSize(),
            enterTransition = {
                fadeIn(animationSpec = tween(150)) +
                    slideInHorizontally(animationSpec = tween(150), initialOffsetX = { it / 14 })
            },
            exitTransition = {
                fadeOut(animationSpec = tween(120)) +
                    slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { -it / 24 })
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(150)) +
                    slideInHorizontally(animationSpec = tween(150), initialOffsetX = { -it / 14 })
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(120)) +
                    slideOutHorizontally(animationSpec = tween(120), targetOffsetX = { it / 24 })
            },
        ) {
            composable(AppDestination.Home.route) {
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
                    onUsbClick = { navController.navigate(AppDestination.UsbAdb.route) },
                    discoveredHost = discoveredHost,
                    discoveredPort = discoveredPort,
                    onDiscoveredEndpointConsumed = {
                        savedStateHandle?.remove<String>(DiscoveryHostKey)
                        savedStateHandle?.remove<String>(DiscoveryPortKey)
                    },
                )
            }
            composable(AppDestination.Device.route) {
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
                    onLogsClick = { navController.navigate(LogsRoute) },
                    onFastbootClick = { navController.navigate(AppDestination.Fastboot.route) },
                )
            }
            composable(AppDestination.Settings.route) { SettingsScreen(bottomPadding = bottomPadding) }
            composable(AppDestination.Pairing.route) {
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
            composable(AppDestination.Discovery.route) {
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
            composable(AppDestination.UsbAdb.route) {
                UsbAdbScreen(
                    bottomPadding = bottomPadding,
                    onBackClick = { navController.popBackStack() },
                    onConnected = {
                        navController.navigate(AppDestination.Device.route) {
                            popUpTo(AppDestination.Home.route)
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(AppDestination.Shell.route) {
                ShellScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.Apps.route) {
                AppsScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.LocalApps.route) {
                LocalAppsScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.Download.route) {
                OnlineDownloadScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.Install.route) {
                InstallApkScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.Files.route) {
                FileTransferScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.Screenshot.route) {
                ScreenshotScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(RemoteRoute) {
                RemoteControlScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(LogsRoute) {
                SystemLogScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
            }
            composable(AppDestination.Fastboot.route) {
                FastbootScreen(bottomPadding = bottomPadding, onBackClick = { navController.popBackStack() })
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
    data object UsbAdb : AppDestination("usb_adb", "USB ADB", Icons.Outlined.Usb)
    data object Shell : AppDestination("shell", "Shell", Icons.Outlined.PhoneAndroid)
    data object Apps : AppDestination("apps", "应用", Icons.Outlined.PhoneAndroid)
    data object LocalApps : AppDestination("local_apps", "本机应用", Icons.Outlined.PhoneAndroid)
    data object Download : AppDestination("download", "下载", Icons.Outlined.PhoneAndroid)
    data object Install : AppDestination("install", "安装", Icons.Outlined.PhoneAndroid)
    data object Files : AppDestination("files", "文件", Icons.Outlined.PhoneAndroid)
    data object Screenshot : AppDestination("screenshot", "截图", Icons.Outlined.PhoneAndroid)
    data object Fastboot : AppDestination("fastboot", "Fastboot", Icons.Outlined.Usb)
}

private const val DiscoveryHostKey = "discovery_host"
private const val DiscoveryPortKey = "discovery_port"
private const val PairingHostKey = "pairing_host"
private const val PairingPortKey = "pairing_port"
private const val RemoteRoute = "remote"
private const val LogsRoute = "logs"
