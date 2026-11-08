package dev.pvps.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.pvps.ui.theme.Hairline
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.pvps.BuildConfig
import dev.pvps.data.model.ConnectionState
import dev.pvps.ui.components.PvpsBackdrop
import dev.pvps.ui.components.UpdateDialog
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.screens.AuditScreen
import dev.pvps.ui.screens.ChatScreen
import dev.pvps.ui.screens.DashboardScreen
import dev.pvps.ui.screens.DockerScreen
import dev.pvps.ui.screens.FilesScreen
import dev.pvps.ui.screens.HostEditScreen
import dev.pvps.ui.screens.HarnessScreen
import dev.pvps.ui.screens.HostsScreen
import dev.pvps.ui.screens.KeysScreen
import dev.pvps.ui.screens.LinkScreen
import dev.pvps.ui.screens.MoreScreen
import dev.pvps.ui.screens.OnboardingScreen
import dev.pvps.ui.screens.ProcessesScreen
import dev.pvps.ui.screens.ServicesScreen
import dev.pvps.ui.screens.SettingsScreen
import dev.pvps.terminal.TerminalScreen
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Warning

private data class NavItem(val route: String, val label: String, val icon: ImageVector)

private val bottomItems = listOf(
    NavItem("home", "Home", Icons.Default.Home),
    NavItem("chat", "Chat", Icons.Default.Chat),
    NavItem("files", "Files", Icons.Default.Folder),
    NavItem("terminal", "Shell", Icons.Default.Terminal),
    NavItem("more", "More", Icons.Default.MoreHoriz)
)

@Composable
fun AppRoot(viewModel: AppViewModel) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: "home"
    val hosts = viewModel.hosts.collectAsStateValue()
    val selectedHost = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    val showBottomBar = hosts.isNotEmpty() &&
        route in setOf("home", "chat", "files", "terminal", "more") &&
        !imeVisible
    val settings = viewModel.settings.collectAsStateValue()
    val pendingOpenSession = viewModel.pendingOpenSession.collectAsStateValue()
    val updateState = viewModel.updateState.collectAsStateValue()
    val needsUnknownSources = viewModel.needsUnknownSources.collectAsStateValue()
    val pendingHost = hosts.firstOrNull {
        states[it.id]?.status == ConnectionState.Status.NEEDS_HOST_KEY
    }
    val pendingHostKey = pendingHost?.let { states[it.id] }

    LaunchedEffect(Unit) {
        // A debug APK cannot install a release-signed APK. Only auto-check in release.
        if (!BuildConfig.DEBUG) viewModel.checkForUpdates()
    }

    LaunchedEffect(pendingOpenSession) {
        if (pendingOpenSession != null) {
            navController.navigate("chat") {
                launchSingleTop = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PvpsBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets.statusBars,
            bottomBar = {
                if (showBottomBar) {
                    PvpsDock(route = route) { destination ->
                        navController.navigate(destination) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                composable("home") {
                    if (hosts.isEmpty()) {
                        OnboardingScreen(
                            viewModel = viewModel,
                            onAddHost = { navController.navigate("hostEdit") },
                            onKeys = { navController.navigate("keys") },
                            onHostCreated = { navController.navigate("home") { launchSingleTop = true } }
                        )
                    } else {
                        DashboardScreen(
                            viewModel = viewModel,
                            onOpenChat = { navController.navigate("chat") },
                            onOpenFiles = { navController.navigate("files") },
                            onOpenTerminal = { navController.navigate("terminal") },
                            onOpenDocker = { navController.navigate("docker") },
                            onOpenServices = { navController.navigate("services") },
                            onOpenProcesses = { navController.navigate("processes") },
                            onManageHosts = { navController.navigate("hosts") },
                            onOpenLink = { navController.navigate("link") }
                        )
                    }
                }
                composable("chat") {
                    ChatScreen(
                        viewModel = viewModel,
                        onBack = { navController.navigate("home") { launchSingleTop = true } }
                    )
                }
                composable("files") {
                    FilesScreen(
                        viewModel = viewModel,
                        onBack = { navController.navigate("home") { launchSingleTop = true } }
                    )
                }
                composable("terminal") {
                    selectedHost?.let { host ->
                        TerminalScreen(
                            host = host,
                            ssh = viewModel.container.ssh,
                            settings = settings,
                            onBack = { navController.popBackStack() },
                            onFontSizeChange = { size -> viewModel.updateSettings { it.copy(terminalFontSize = size) } }
                        )
                    } ?: Column(modifier = Modifier.fillMaxSize()) {
                        TextButton(
                            onClick = { navController.navigate("home") { launchSingleTop = true } },
                            modifier = Modifier.padding(8.dp)
                        ) { Text("Back") }
                        OnboardingScreen(
                            viewModel = viewModel,
                            onAddHost = { navController.navigate("hostEdit") },
                            onKeys = { navController.navigate("keys") },
                            onHostCreated = { navController.navigate("home") { launchSingleTop = true } }
                        )
                    }
                }
                composable("more") {
                    MoreScreen(
                        viewModel = viewModel,
                        onHosts = { navController.navigate("hosts") },
                        onKeys = { navController.navigate("keys") },
                        onDocker = { navController.navigate("docker") },
                        onServices = { navController.navigate("services") },
                        onProcesses = { navController.navigate("processes") },
                        onAudit = { navController.navigate("audit") },
                        onSettings = { navController.navigate("settings") },
                        onOpenTerminal = { navController.navigate("terminal") },
                        onOpenFiles = { navController.navigate("files") },
                        onCheckUpdates = { viewModel.checkForUpdates(force = true) },
                        onHarness = { navController.navigate("harness") }
                    )
                }
                composable("link") {
                    LinkScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable("harness") {
                    HarnessScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("hosts") {
                    HostsScreen(
                        viewModel = viewModel,
                        onAddHost = { navController.navigate("hostEdit") },
                        onEditHost = { id -> navController.navigate("hostEdit?hostId=$id") },
                        onManageKeys = { navController.navigate("keys") },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = "hostEdit?hostId={hostId}",
                    arguments = listOf(navArgument("hostId") { type = NavType.StringType; defaultValue = "new" })
                ) { entry ->
                    val hostId = entry.arguments?.getString("hostId")?.takeIf { it != "new" }
                    HostEditScreen(
                        viewModel = viewModel,
                        hostId = hostId,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("keys") {
                    KeysScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable("settings") {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onHosts = { navController.navigate("hosts") },
                        onKeys = { navController.navigate("keys") },
                        onAudit = { navController.navigate("audit") }
                    )
                }
                composable("audit") {
                    AuditScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable("docker") {
                    DockerScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable("services") {
                    ServicesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
                composable("processes") {
                    ProcessesScreen(viewModel = viewModel, onBack = { navController.popBackStack() })
                }
            }
        }
    }

    if (pendingHostKey != null) {
        AlertDialog(
            onDismissRequest = { pendingHost?.let { viewModel.disconnect(it.id) } },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Unknown host key", color = Warning, style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Verify this SHA256 fingerprint on the server before trusting it. If it does not match, you may be under a MITM attack.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        pendingHostKey.hostKeySha256.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingHost?.let { viewModel.trustHostKey(it.id) } }) {
                    Text("Trust and connect", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHost?.let { viewModel.disconnect(it.id) } }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
    UpdateDialog(
        state = updateState,
        needsUnknownSources = needsUnknownSources,
        onDismiss = { viewModel.dismissUpdate() },
        onUpdate = { viewModel.downloadUpdate(it) },
        onInstall = { viewModel.installReadyUpdate() },
        onOpenSettings = { viewModel.openUpdateSettings() }
    )
}

@Composable
private fun PvpsDock(route: String, onSelect: (String) -> Unit) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(24.dp, RoundedCornerShape(Radius.xl), ambientColor = Color.Black.copy(alpha = 0.5f))
                .clip(RoundedCornerShape(Radius.xl))
                .background(SurfaceHigh.copy(alpha = 0.96f))
                .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(Radius.xl))
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            bottomItems.forEach { item ->
                val selected = route == item.route
                val tint by animateColorAsState(
                    targetValue = if (selected) Accent else TextFaint,
                    animationSpec = tween(220),
                    label = "dockTint"
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.94f,
                    animationSpec = tween(220),
                    label = "dockScale"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(
                            if (selected) Accent.copy(alpha = 0.10f) else Color.Transparent
                        )
                        .clickable {
                            if (!selected) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelect(item.route)
                            }
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier
                            .size(21.dp)
                            .scale(iconScale)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        item.label.uppercase(),
                        style = PvpsType.eyebrow.copy(letterSpacing = 1.1.sp),
                        color = tint,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
