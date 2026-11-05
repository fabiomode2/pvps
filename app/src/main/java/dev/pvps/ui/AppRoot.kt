package dev.pvps.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
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
import dev.pvps.ui.screens.HostsScreen
import dev.pvps.ui.screens.KeysScreen
import dev.pvps.ui.screens.MoreScreen
import dev.pvps.ui.screens.OnboardingScreen
import dev.pvps.ui.screens.ProcessesScreen
import dev.pvps.ui.screens.ServicesScreen
import dev.pvps.ui.screens.SettingsScreen
import dev.pvps.terminal.TerminalScreen
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceHigh
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
    val showBottomBar = hosts.isNotEmpty() && route in setOf("home", "chat", "files", "more")
    val settings = viewModel.settings.collectAsStateValue()
    val updateState = viewModel.updateState.collectAsStateValue()
    val needsUnknownSources = viewModel.needsUnknownSources.collectAsStateValue()
    val pendingHostKey = selectedHost?.let { states[it.id] }
        ?.takeIf { it.status == ConnectionState.Status.NEEDS_HOST_KEY }

    LaunchedEffect(Unit) {
        // A debug APK cannot install a release-signed APK. Only auto-check in release.
        if (!BuildConfig.DEBUG) viewModel.checkForUpdates()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PvpsBackdrop()
        Scaffold(
            containerColor = Color.Transparent,
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
                            onManageHosts = { navController.navigate("hosts") }
                        )
                    }
                }
                composable("chat") {
                    ChatScreen(
                        viewModel = viewModel,
                        onBack = { navController.navigate("home") { launchSingleTop = true } },
                        onOpenTerminal = { navController.navigate("terminal") }
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
                        onCheckUpdates = { viewModel.checkForUpdates(force = true) }
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
            onDismissRequest = { selectedHost?.let { viewModel.disconnect(it.id) } },
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
                TextButton(onClick = { selectedHost?.let { viewModel.trustHostKey(it.id) } }) {
                    Text("Trust and connect", color = Accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedHost?.let { viewModel.disconnect(it.id) } }) {
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = SurfaceHigh.copy(alpha = 0.96f),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, OutlineDark),
            shadowElevation = 18.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                bottomItems.forEach { item ->
                    val selected = route == item.route
                    val tint by animateColorAsState(
                        targetValue = if (selected) Accent else TextSecondary,
                        label = "dockTint"
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) Accent.copy(alpha = 0.11f) else Color.Transparent)
                            .clickable { onSelect(item.route) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = tint,
                            modifier = Modifier.size(19.dp)
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = fadeIn() + expandHorizontally(),
                            exit = fadeOut() + shrinkHorizontally()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    item.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Accent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
