package dev.pvps.ui.screens

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.ConnectionState
import dev.pvps.system.SystemRepository
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.HostSwitcher
import dev.pvps.ui.components.InfoRow
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.SectionCard
import dev.pvps.ui.components.StatCard
import dev.pvps.ui.components.StatusPill
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.components.relativeTime
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onOpenChat: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDocker: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenProcesses: () -> Unit,
    onManageHosts: () -> Unit,
    onBack: (() -> Unit)? = null
) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val hosts = viewModel.hosts.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val openCode = viewModel.openCodeState.collectAsStateValue()
    val state = host?.let { states[it.id] }
    var snapshot by remember { mutableStateOf<SystemRepository.Snapshot?>(null) }
    var containers by remember { mutableStateOf<List<SystemRepository.Container>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var lastRefresh by remember { mutableStateOf(0L) }

    LaunchedEffect(host?.id, state?.status, refreshTick) {
        val h = host ?: return@LaunchedEffect
        if (state?.status != ConnectionState.Status.CONNECTED) return@LaunchedEffect
        loading = true
        error = null
        runCatching {
            withContext(Dispatchers.IO) {
                val snap = viewModel.system.snapshot(h.id)
                val cs = viewModel.system.containers(h.id)
                snap to cs
            }
        }.onSuccess { (snap, cs) ->
            snapshot = snap
            containers = cs
            lastRefresh = System.currentTimeMillis()
        }.onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "PVPS",
                subtitle = host?.let { "${it.name} · ${it.sshHost}" } ?: "Portable VPS control",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onManageHosts) {
                        Icon(Icons.Default.Dns, contentDescription = "Hosts", tint = TextSecondary)
                    }
                    IconButton(onClick = { refreshTick++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Accent)
                    }
                }
            )
        }
    ) { padding ->
        if (host == null) {
            EmptyState(
                title = "No host yet",
                message = "Add your VPS or PC over SSH. The app never exposes OpenCode publicly.",
                actionLabel = "Manage hosts",
                onAction = onManageHosts
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(6.dp))
                HostSwitcher(
                    hosts = hosts,
                    selected = host,
                    state = state,
                    onSelect = { viewModel.selectHost(it.id) },
                    onConnect = { viewModel.connect(host.id) },
                    onDisconnect = { viewModel.disconnect(host.id) }
                )
            }
            item {
                HeroCard(
                    host = host,
                    state = state,
                    onConnect = { viewModel.connect(host.id) },
                    onChat = onOpenChat,
                    onTerminal = onOpenTerminal
                )
            }
            if (state?.status == ConnectionState.Status.NEEDS_HOST_KEY) {
                item {
                    HostKeyTrustCard(
                        fingerprint = state.hostKeySha256.orEmpty(),
                        onTrust = { viewModel.trustHostKey(host.id) },
                        onCancel = { viewModel.disconnect(host.id) }
                    )
                }
            }
            if (error != null) item { ErrorBanner(error!!) { error = null } }
            if (loading && snapshot == null) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    }
                }
            }
            snapshot?.let { snap ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "CPU",
                            value = "${snap.cpuPercent}%",
                            detail = "load ${"%.2f".format(snap.load1)} · ${snap.cpuCount} cores",
                            accent = if (snap.cpuPercent > 85) Danger else Accent,
                            modifier = Modifier.weight(1f),
                            progress = snap.cpuPercent / 100f
                        )
                        StatCard(
                            label = "RAM",
                            value = "${snap.memPercent}%",
                            detail = "${formatBytes(snap.memUsed)} / ${formatBytes(snap.memTotal)}",
                            accent = if (snap.memPercent > 90) Danger else Success,
                            modifier = Modifier.weight(1f),
                            progress = snap.memPercent / 100f
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(
                            label = "Disk /",
                            value = "${snap.diskPercent}%",
                            detail = "${formatBytes(snap.diskUsed)} / ${formatBytes(snap.diskTotal)}",
                            accent = if (snap.diskPercent > 90) Danger else Warning,
                            modifier = Modifier.weight(1f),
                            progress = snap.diskPercent / 100f
                        )
                        StatCard(
                            label = "Docker",
                            value = "${snap.containersRunning}/${snap.containersTotal}",
                            detail = "running / total",
                            accent = Violet,
                            modifier = Modifier.weight(1f),
                            progress = if (snap.containersTotal > 0) snap.containersRunning.toFloat() / snap.containersTotal else 0f
                        )
                    }
                }
            }
            item {
                OpenCodeCard(
                    connected = openCode.connected,
                    version = openCode.serverVersion,
                    sessions = openCode.sessions.size,
                    onOpenChat = onOpenChat
                )
            }
            item {
                SectionCard(title = "Server tools") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolTile("Files", Icons.Default.Folder, onOpenFiles, Modifier.weight(1f))
                        ToolTile("Docker", Icons.Default.Storage, onOpenDocker, Modifier.weight(1f))
                        ToolTile("Services", Icons.Default.Dns, onOpenServices, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ToolTile("Processes", Icons.Default.Memory, onOpenProcesses, Modifier.weight(1f))
                        ToolTile("Terminal", Icons.Default.Terminal, onOpenTerminal, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            snapshot?.let { snap ->
                item {
                    SectionCard(title = "System") {
                        InfoRow("Hostname", snap.hostname.ifBlank { "-" })
                        InfoRow("OS", snap.os.ifBlank { "-" })
                        InfoRow("Kernel", snap.kernel.ifBlank { "-" })
                        InfoRow("Uptime", snap.uptime.ifBlank { "-" })
                        InfoRow("Tailscale", snap.tailscaleIp.ifBlank { "-" })
                        InfoRow("Failed services", snap.failedServices.toString())
                        if (lastRefresh > 0) InfoRow("Updated", relativeTime(lastRefresh))
                    }
                }
            }
            if (containers.isNotEmpty()) {
                item {
                    SectionCard(
                        title = "Containers",
                        actions = { TextButton(onClick = onOpenDocker) { Text("All", color = Accent) } }
                    ) {
                        containers.take(5).forEach { container ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(if (container.running) Success else TextSecondary, RoundedCornerShape(50))
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(container.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(container.image, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Text(
                                    container.state,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (container.running) Success else TextSecondary
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun HeroCard(
    host: dev.pvps.data.model.Host,
    state: ConnectionState?,
    onConnect: () -> Unit,
    onChat: () -> Unit,
    onTerminal: () -> Unit
) {
    val connected = state?.status == ConnectionState.Status.CONNECTED
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(Accent.copy(alpha = 0.10f), Color.Transparent, Violet.copy(alpha = 0.08f))
                )
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(host.name, style = MaterialTheme.typography.headlineMedium)
                        Text(
                            "${host.sshUser}@${host.sshHost}:${host.sshPort}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    StatusPill(
                        text = if (connected) "ONLINE" else "OFFLINE",
                        color = if (connected) Success else TextSecondary
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (connected) {
                        Button(onClick = onChat) {
                            Icon(Icons.Default.Chat, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("OpenCode")
                        }
                        OutlinedButton(onClick = onTerminal) {
                            Icon(Icons.Default.Terminal, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Terminal")
                        }
                    } else {
                        Button(onClick = onConnect) {
                            Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Connect")
                        }
                    }
                }
                if (state?.status == ConnectionState.Status.ERROR) {
                    Spacer(Modifier.height(10.dp))
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = Danger)
                }
            }
        }
    }
}

@Composable
private fun OpenCodeCard(connected: Boolean, version: String, sessions: Int, onOpenChat: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(Violet.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Chat, contentDescription = null, tint = Violet)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("OpenCode v2", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (connected) "v$version · $sessions sessions" else "Not connected",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            TextButton(onClick = onOpenChat) { Text("Open", color = Accent) }
        }
    }
}

@Composable
private fun ToolTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.clickable { onClick() },
        color = SurfaceRaised,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = label, tint = Accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun HostKeyTrustCard(fingerprint: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.10f)),
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("Unknown host key", style = MaterialTheme.typography.titleMedium, color = Warning)
            Spacer(Modifier.height(4.dp))
            Text(
                "Verify this SHA256 fingerprint on the server before trusting it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                fingerprint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTrust) { Text("Trust and connect") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    }
}
