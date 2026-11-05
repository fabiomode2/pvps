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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.system.SystemRepository
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.GhostButton
import dev.pvps.ui.components.GlassIconButton
import dev.pvps.ui.components.HostSwitcher
import dev.pvps.ui.components.InfoRow
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.SectionCard
import dev.pvps.ui.components.SectionLabel
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.StatCard
import dev.pvps.ui.components.StatusDot
import dev.pvps.ui.components.StatusPill
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.components.relativeTime
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
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
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = host?.name ?: "PVPS",
                subtitle = host?.let { "${it.sshUser}@${it.sshHost}" } ?: "Portable VPS control",
                onBack = onBack,
                actions = {
                    GlassIconButton(onClick = onManageHosts) {
                        Icon(Icons.Default.MoreHoriz, contentDescription = "Hosts", tint = TextSecondary)
                    }
                    Spacer(Modifier.width(8.dp))
                    GlassIconButton(onClick = { refreshTick++ }, size = 38.dp) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = if (loading) TextFaint else Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (host == null) {
            EmptyState(
                title = "No host yet",
                message = "Add your VPS or PC over SSH. OpenCode stays private behind the tunnel.",
                actionLabel = "Manage hosts",
                onAction = onManageHosts
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
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
                ConnectionHero(
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
                    }
                }
            }
            snapshot?.let { snap ->
                item { SectionLabel("Telemetry", modifier = Modifier.padding(top = 6.dp)) }
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
                            label = "Memory",
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
                            progress = if (snap.containersTotal > 0) {
                                snap.containersRunning.toFloat() / snap.containersTotal
                            } else 0f
                        )
                    }
                }
            }
            item { SectionLabel("Workspace", modifier = Modifier.padding(top = 6.dp)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile("Files", Icons.Default.Folder, Accent, onOpenFiles, Modifier.weight(1f))
                        ActionTile("Docker", Icons.Default.Storage, Violet, onOpenDocker, Modifier.weight(1f))
                        ActionTile("Services", Icons.Default.Dns, Warning, onOpenServices, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile("Processes", Icons.Default.Memory, Danger, onOpenProcesses, Modifier.weight(1f))
                        ActionTile("Terminal", Icons.Default.Terminal, Accent, onOpenTerminal, Modifier.weight(1f))
                        Spacer(Modifier.weight(1f))
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
            snapshot?.let { snap ->
                item { SectionLabel("Machine", modifier = Modifier.padding(top = 6.dp)) }
                item {
                    SectionCard(
                        title = "System",
                        actions = {
                            TextButton(onClick = { refreshTick++ }) {
                                Text("REFRESH", color = Accent, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    ) {
                        InfoRow("Hostname", snap.hostname.ifBlank { "-" })
                        InfoRow("OS", snap.os.ifBlank { "-" })
                        InfoRow("Kernel", snap.kernel.ifBlank { "-" })
                        InfoRow("Uptime", snap.uptime.ifBlank { "-" })
                        InfoRow("Tailscale", snap.tailscaleIp.ifBlank { "-" })
                        InfoRow("Failed units", snap.failedServices.toString())
                        if (lastRefresh > 0) InfoRow("Updated", relativeTime(lastRefresh))
                    }
                }
            }
            if (containers.isNotEmpty()) {
                item { SectionLabel("Containers", modifier = Modifier.padding(top = 6.dp)) }
                item {
                    SectionCard(
                        title = "Running now",
                        actions = {
                            TextButton(onClick = onOpenDocker) {
                                Text("ALL", color = Accent, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    ) {
                        containers.take(5).forEach { container ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusDot(if (container.running) Success else TextFaint, 7)
                                Spacer(Modifier.width(9.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        container.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        container.image,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    container.state.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (container.running) Success else TextFaint
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun ConnectionHero(
    host: Host,
    state: ConnectionState?,
    onConnect: () -> Unit,
    onChat: () -> Unit,
    onTerminal: () -> Unit
) {
    val connected = state?.status == ConnectionState.Status.CONNECTED
    val statusColor = when (state?.status) {
        ConnectionState.Status.CONNECTED -> Success
        ConnectionState.Status.CONNECTING -> Warning
        ConnectionState.Status.ERROR -> Danger
        else -> TextFaint
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.56f),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Box(
            modifier = Modifier.background(
                Brush.linearGradient(
                    listOf(Accent.copy(alpha = 0.08f), Color.Transparent, Violet.copy(alpha = 0.06f))
                )
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "REMOTE SESSION",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            host.name,
                            style = MaterialTheme.typography.displaySmall,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${host.sshUser}@${host.sshHost}:${host.sshPort}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    StatusPill(
                        text = when (state?.status) {
                            ConnectionState.Status.CONNECTED -> "LIVE"
                            ConnectionState.Status.CONNECTING -> "LINKING"
                            ConnectionState.Status.NEEDS_HOST_KEY -> "VERIFY"
                            ConnectionState.Status.ERROR -> "ERROR"
                            else -> "IDLE"
                        },
                        color = statusColor
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(62.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(statusColor.copy(alpha = 0.24f), statusColor.copy(alpha = 0.03f))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(statusColor.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            when (state?.status) {
                                ConnectionState.Status.CONNECTED -> "Encrypted SSH session"
                                ConnectionState.Status.CONNECTING -> "Opening secure channel"
                                ConnectionState.Status.NEEDS_HOST_KEY -> "Host key needs approval"
                                ConnectionState.Status.ERROR -> "Connection failed"
                                else -> "Not connected"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            state?.message?.takeIf { it.isNotBlank() }
                                ?: if (connected) "Keys stay on this device. OpenCode is tunnel-only."
                                else "Tap connect to open the tunnel.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
                if (connected) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalButton(
                            text = "OpenCode",
                            onClick = onChat,
                            icon = Icons.AutoMirrored.Filled.Chat
                        )
                        GhostButton(
                            text = "Terminal",
                            onClick = onTerminal,
                            icon = Icons.Default.Terminal
                        )
                    }
                } else {
                    SignalButton(
                        text = "Connect now",
                        onClick = onConnect,
                        icon = Icons.Default.Power
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.52f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(7.dp))
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun OpenCodeCard(
    connected: Boolean,
    version: String,
    sessions: Int,
    onOpenChat: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Violet.copy(alpha = 0.055f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, Violet.copy(alpha = 0.20f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Violet.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = Violet)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("OpenCode", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "V2",
                        style = MaterialTheme.typography.labelSmall,
                        color = Violet,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    if (connected) "v$version · $sessions sessions" else "Connect a host to open chat",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            TextButton(onClick = onOpenChat) {
                Text("OPEN", color = Violet, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun HostKeyTrustCard(fingerprint: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    Surface(
        color = Warning.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Unknown host key", style = MaterialTheme.typography.titleLarge, color = Warning)
            Spacer(Modifier.height(5.dp))
            Text(
                "Verify this SHA256 fingerprint on the server before trusting it.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(Modifier.height(9.dp))
            Text(
                fingerprint,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MonoFont,
                color = TextPrimary
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SignalButton("Trust and connect", onTrust)
                GhostButton("Cancel", onCancel)
            }
        }
    }
}
