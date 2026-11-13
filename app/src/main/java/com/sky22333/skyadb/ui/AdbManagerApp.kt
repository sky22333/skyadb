package com.sky22333.skyadb.ui

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import androidx.compose.animation.Crossfade
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.sky22333.skyadb.R
import com.sky22333.skyadb.ui.apps.AppsScreen
import com.sky22333.skyadb.ui.components.AppIcons
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
import com.sky22333.skyadb.ui.shell.ShellScreen
import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarDefaults
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Info
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.NavTransitions
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Serializable
private data class Route(val name: String) : NavKey

@Composable
fun AdbManagerApp() {
    val stack = rememberNavBackStack<Route>(Route(RootRoute))
    var selectedTab by rememberSaveable { mutableStateOf(AppDestination.Home.route) }
    val current = (stack.lastOrNull() as? Route)?.name ?: RootRoute
    val showBottomBar = current == RootRoute
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val pageFocusRequester = remember { FocusRequester() }
    val activity = LocalActivity.current
    val systemBarBackground = when (current) {
        MirrorRoute -> Color.Black
        AppDestination.Files.route -> MiuixTheme.colorScheme.surface
        else -> MiuixTheme.colorScheme.background
    }
    val lightSystemBarIcons = current != MirrorRoute && MiuixTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        activity?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) window.isNavigationBarContrastEnforced = false
            window.decorView.setBackgroundColor(systemBarBackground.toArgb())
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = lightSystemBarIcons
                isAppearanceLightNavigationBars = lightSystemBarIcons
            }
        }
    }
    var homeHost by rememberSaveable { mutableStateOf("") }
    var homePort by rememberSaveable { mutableStateOf("") }
    var pairingHost by rememberSaveable { mutableStateOf("") }
    var pairingPort by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(current, selectedTab) {
        focusManager.clearFocus(force = true)
        pageFocusRequester.requestFocus()
        keyboard?.hide()
    }

    fun selectTab(route: String) {
        selectedTab = route
    }
    fun navigate(route: String) {
        val key = Route(route)
        val index = stack.indexOf(key)
        if (index >= 0) {
            while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
        } else {
            stack.add(key)
        }
    }
    fun back() {
        val topRoute = (stack.lastOrNull() as? Route)?.name
        if (topRoute == RootRoute) {
            if (selectedTab == AppDestination.Home.route) activity?.finish()
            else selectTab(AppDestination.Home.route)
        } else if (stack.size > 1) {
            stack.removeAt(stack.lastIndex)
        }
    }

    val tabBackState = rememberNavigationEventState(currentInfo = NavigationEventInfo.None)
    NavigationBackHandler(
        state = tabBackState,
        isBackEnabled = showBottomBar && selectedTab != AppDestination.Home.route,
        onBackCompleted = { selectTab(AppDestination.Home.route) },
    )

    Scaffold(
        modifier = Modifier.focusRequester(pageFocusRequester).focusable(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MiuixTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(tween(120)),
                exit = fadeOut(tween(120)),
            ) {
                NavigationBar(
                    color = MiuixTheme.colorScheme.surface,
                ) {
                    bottomDestinations.forEach { destination ->
                        NavigationBarItem(
                            selected = selectedTab == destination.route,
                            onClick = { selectTab(destination.route) },
                            icon = destination.icon,
                            label = stringResource(destination.labelRes),
                        )
                    }
                }
            }
        },
    ) { _ ->
        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val rootBottomPadding = NavigationBarDefaults.ItemHeight + systemBottom + 1.dp
        NavDisplay(
            backStack = stack,
            onBack = ::back,
            modifier = Modifier.fillMaxSize(),
            transition = PageTransition,
            effects = NavDisplayEffects(enableCornerClip = false, dimAmount = 0f),
        ) {
            entry<Route> { route ->
                val pageModifier = Modifier.fillMaxSize().background(
                    when (route.name) {
                        MirrorRoute -> Color.Black
                        AppDestination.Files.route -> MiuixTheme.colorScheme.surface
                        else -> MiuixTheme.colorScheme.background
                    },
                ).then(
                    if (route.name != RootRoute && route.name != MirrorRoute) Modifier.navigationBarsPadding() else Modifier,
                )
                Box(pageModifier) {
                when (route.name) {
                    RootRoute -> Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(220),
                        modifier = Modifier.fillMaxSize(),
                        label = "bottom-tab",
                    ) { tab ->
                        Box(Modifier.fillMaxSize().padding(bottom = rootBottomPadding).background(MiuixTheme.colorScheme.background)) {
                            when (tab) {
                                AppDestination.Home.route -> HomeScreen(
                                    bottomPadding = 0.dp,
                                    onPairingClick = { navigate(AppDestination.Pairing.route) },
                                    onDiscoveryClick = { navigate(AppDestination.Discovery.route) },
                                    discoveredHost = homeHost,
                                    discoveredPort = homePort,
                                    onDiscoveredEndpointConsumed = { homeHost = ""; homePort = "" },
                                )
                                AppDestination.Device.route -> DeviceScreen(
                                    bottomPadding = 0.dp,
                                    onAppsClick = { navigate(AppDestination.Apps.route) },
                                    onLocalAppsClick = { navigate(AppDestination.LocalApps.route) },
                                    onInstallClick = { navigate(AppDestination.Install.route) },
                                    onDownloadClick = { navigate(AppDestination.Download.route) },
                                    onFilesClick = { navigate(AppDestination.Files.route) },
                                    onScreenshotClick = { navigate(AppDestination.Screenshot.route) },
                                    onShellClick = { navigate(AppDestination.Shell.route) },
                                    onRemoteClick = { navigate(RemoteRoute) },
                                    onMirrorClick = { navigate(MirrorRoute) },
                                    onLogsClick = { navigate(LogsRoute) },
                                )
                                AppDestination.Settings.route -> SettingsScreen(
                                    bottomPadding = 0.dp,
                                    onDiagnosticsClick = { navigate(DiagnosticsRoute) },
                                )
                            }
                        }
                    }
                    AppDestination.Pairing.route -> PairingScreen(
                        bottomPadding = 0.dp,
                        onBackClick = ::back,
                        onContinueToConnect = { host, port ->
                            homeHost = host
                            homePort = port?.toString().orEmpty()
                            selectTab(AppDestination.Home.route)
                            while (stack.size > 1) {
                                stack.removeAt(stack.lastIndex)
                            }
                        },
                        discoveredHost = pairingHost,
                        discoveredPort = pairingPort,
                        onDiscoveredEndpointConsumed = { pairingHost = ""; pairingPort = "" },
                    )
                    AppDestination.Discovery.route -> DeviceDiscoveryScreen(
                        bottomPadding = 0.dp,
                        onBackClick = ::back,
                        onUseEndpoint = { host, port ->
                            if ((stack.getOrNull(stack.lastIndex - 1) as? Route)?.name == AppDestination.Pairing.route) {
                                pairingHost = host; pairingPort = port.toString()
                            } else {
                                homeHost = host; homePort = port.toString()
                            }
                            back()
                        },
                        onPairEndpoint = { host, port ->
                            pairingHost = host; pairingPort = port.toString()
                            navigate(AppDestination.Pairing.route)
                        },
                    )
                    AppDestination.Shell.route -> ShellScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    AppDestination.Apps.route -> AppsScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    AppDestination.LocalApps.route -> LocalAppsScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    AppDestination.Download.route -> OnlineDownloadScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    AppDestination.Install.route -> InstallApkScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    AppDestination.Files.route -> FileTransferScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    AppDestination.Screenshot.route -> ScreenshotScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    RemoteRoute -> RemoteControlScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    MirrorRoute -> MirrorScreen(onBackClick = ::back)
                    LogsRoute -> SystemLogScreen(bottomPadding = 0.dp, onBackClick = ::back)
                    DiagnosticsRoute -> DiagnosticLogScreen(bottomPadding = 0.dp, onBackClick = ::back)
                }
                }
            }
        }
    }
}

private val bottomDestinations = listOf(
    BottomDestination(AppDestination.Home.route, R.string.nav_devices, AppIcons.Device),
    BottomDestination(AppDestination.Device.route, R.string.nav_details, MiuixIcons.Info),
    BottomDestination(AppDestination.Settings.route, R.string.nav_settings, MiuixIcons.Settings),
)

private data class BottomDestination(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private sealed class AppDestination(val route: String) {
    data object Home : AppDestination("home")
    data object Device : AppDestination("device")
    data object Settings : AppDestination("settings")
    data object Pairing : AppDestination("pairing")
    data object Discovery : AppDestination("discovery")
    data object Shell : AppDestination("shell")
    data object Apps : AppDestination("apps")
    data object LocalApps : AppDestination("local_apps")
    data object Download : AppDestination("download")
    data object Install : AppDestination("install")
    data object Files : AppDestination("files")
    data object Screenshot : AppDestination("screenshot")
}

private const val RootRoute = "root"
private val PageTransition = object : NavTransition by NavTransitions.MiuixDefault {
    override val motion = NavMotion(
        commit = NavSettleSpec.Spring(stiffness = 600f),
        cancel = NavSettleSpec.Spring(stiffness = 600f),
        programmatic = (NavMotion.Default.programmatic as NavSettleSpec.Tween).copy(durationMillis = 220),
    )
}
private const val RemoteRoute = "remote"
private const val MirrorRoute = "mirror"
private const val LogsRoute = "logs"
private const val DiagnosticsRoute = "diagnostics"
