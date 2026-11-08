package dev.pvps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import dev.pvps.ui.components.SectionLabel
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.StatusDot
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.components.relativeTime
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.OutlineSoft
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
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
    var snapshot by remember(host?.id) { mutableStateOf<SystemRepository.Snapshot?>(null) }
    var containers by remember(host?.id) { mutableStateOf<List<SystemRepository.Container>>(emptyList()) }
    var loading by remember(host?.id) { mutableStateOf(false) }
    var error by remember(host?.id) { mutableStateOf<String?>(null) }
    var refreshTick by remember { mutableIntStateOf(0) }
    var lastRefresh by remember(host?.id) { mutableStateOf(0L) }

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
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            DashboardHeader(
                host = host,
                hosts = hosts,
                state = state,
                loading = loading,
                onBack = onBack,
                onSelectHost = { viewModel.selectHost(it.id) },
                onRefresh = { refreshTick++ },
                onManageHosts = onManageHosts
            )
        }
    ) { padding ->
        if (host == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                EmptyState(
                    title = "No host yet",
                    message = "Add your VPS or PC over SSH. OpenCode stays private behind the tunnel.",
                    actionLabel = "Manage hosts",
                    onAction = onManageHosts
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                ConnectionPanel(
                    state = state,
                    openCodeConnected = openCode.connected,
                    openCodeConnecting = openCode.connecting,
                    openCodeVersion = openCode.serverVersion,
                    openCodeSessions = openCode.sessions.size,
                    onConnect = { viewModel.connect(host.id) },
                    onDisconnect = { viewModel.disconnect(host.id) },
                    onChat = onOpenChat,
                    onFiles = onOpenFiles,
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
            if (loading && snapshot == null) item { LoadingTelemetry() }
            snapshot?.let { snap ->
                item { TelemetryHeader(loading = loading, lastRefresh = lastRefresh) }
                item { TelemetryGrid(snap) }
            }
            item { SectionLabel("Control", modifier = Modifier.padding(top = 2.dp)) }
            item {
                val dockerValue = snapshot
                    ?.takeIf { it.containersTotal > 0 }
                    ?.let { "${it.containersRunning}/${it.containersTotal}" }
                    ?: containers.takeIf { it.isNotEmpty() }
                        ?.let { "${it.count { c -> c.running }}/${it.size}" }
                val failed = snapshot?.failedServices
                QuickAccess(
                    dockerValue = dockerValue,
                    serviceValue = failed?.let { if (it > 0) "$it failed" else "0 failed" },
                    serviceColor = if ((failed ?: 0) > 0) Danger else TextSecondary,
                    onOpenDocker = onOpenDocker,
                    onOpenServices = onOpenServices,
                    onOpenProcesses = onOpenProcesses
                )
            }
            snapshot?.let { snap -> item { HostFootprint(snap) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DashboardHeader(
    host: Host?,
    hosts: List<Host>,
    state: ConnectionState?,
    loading: Boolean,
    onBack: (() -> Unit)?,
    onSelectHost: (Host) -> Unit,
    onRefresh: () -> Unit,
    onManageHosts: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val color = connectionColor(state?.status)
    val label = connectionLabel(state?.status)
    val address = host?.let {
        buildString {
            append(it.sshUser)
            append('@')
            append(it.sshHost)
            if (it.sshPort != 22) {
                append(':')
                append(it.sshPort)
            }
        }
    } ?: "No host selected"

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepBackground.copy(alpha = 0.94f)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    GlassIconButton(onClick = onBack, size = 38.dp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = hosts.isNotEmpty()) { menuOpen = true }
                            .padding(vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = host?.name ?: "PVPS",
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (hosts.isNotEmpty()) {
                                Spacer(Modifier.width(5.dp))
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = "Switch host",
                                    tint = TextFaint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = address,
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = MonoFont,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(Modifier.width(7.dp))
                            StatusDot(color, 5)
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = color,
                                maxLines = 1
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        hosts.forEach { item ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (item.id == host?.id) {
                                            StatusDot(Accent, 5)
                                            Spacer(Modifier.width(8.dp))
                                        }
                                        Column {
                                            Text(
                                                item.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                color = if (item.id == host?.id) Accent else TextPrimary
                                            )
                                            Text(
                                                "${item.sshUser}@${item.sshHost}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = TextSecondary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    menuOpen = false
                                    onSelectHost(item)
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                GlassIconButton(onClick = onRefresh, size = 38.dp) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = if (loading) TextFaint else Accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                GlassIconButton(onClick = onManageHosts, size = 38.dp) {
                    Icon(
                        Icons.Default.MoreHoriz,
                        contentDescription = "Manage hosts",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = OutlineDark.copy(alpha = 0.65f)
            )
        }
    }
}

@Composable
private fun ConnectionPanel(
    state: ConnectionState?,
    openCodeConnected: Boolean,
    openCodeConnecting: Boolean,
    openCodeVersion: String,
    openCodeSessions: Int,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onChat: () -> Unit,
    onFiles: () -> Unit,
    onTerminal: () -> Unit
) {
    val status = state?.status
    val connected = status == ConnectionState.Status.CONNECTED
    val color = connectionColor(status)
    val title = when (status) {
        ConnectionState.Status.CONNECTED -> "Connected"
        ConnectionState.Status.CONNECTING -> "Connecting"
        ConnectionState.Status.RECONNECTING -> "Reconnecting"
        ConnectionState.Status.NEEDS_HOST_KEY -> "Host key required"
        ConnectionState.Status.ERROR -> "Connection failed"
        else -> "Not connected"
    }
    val detail = when (status) {
        ConnectionState.Status.CONNECTED -> state?.message?.takeIf { it.isNotBlank() } ?: "SSH session established."
        ConnectionState.Status.CONNECTING -> state?.message?.takeIf { it.isNotBlank() } ?: "Opening SSH session."
        ConnectionState.Status.RECONNECTING -> state?.message?.takeIf { it.isNotBlank() } ?: "Retrying the SSH session."
        ConnectionState.Status.NEEDS_HOST_KEY -> "Verify the fingerprint below before trusting this host."
        ConnectionState.Status.ERROR -> state?.message?.takeIf { it.isNotBlank() } ?: "Check the address, key, or network."
        else -> "Connect to load live telemetry and open a session."
    }
    val openCodeMeta = when {
        openCodeConnected -> buildString {
            append("OpenCode")
            if (openCodeVersion.isNotBlank()) append(" · v").append(openCodeVersion)
            append(" · ").append(openCodeSessions)
            append(if (openCodeSessions == 1) " session" else " sessions")
        }
        openCodeConnecting -> "OpenCode · connecting"
        else -> "OpenCode · not connected"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.44f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color, 8)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                when {
                    connected -> ConnectionAction(
                        text = "Disconnect",
                        icon = Icons.Default.LinkOff,
                        color = TextSecondary,
                        onClick = onDisconnect
                    )
                    status == ConnectionState.Status.CONNECTING -> CircularProgressIndicator(
                        modifier = Modifier.size(15.dp),
                        color = Warning,
                        strokeWidth = 2.dp
                    )
                    status == ConnectionState.Status.NEEDS_HOST_KEY -> Unit
                    else -> ConnectionAction(
                        text = "Connect",
                        icon = Icons.Default.Power,
                        color = Accent,
                        onClick = onConnect
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            PrimaryConversationAction(
                meta = openCodeMeta,
                onClick = onChat
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryAction(
                    label = "Files",
                    icon = Icons.Default.Folder,
                    onClick = onFiles,
                    modifier = Modifier.weight(1f)
                )
                SecondaryAction(
                    label = "Shell",
                    icon = Icons.Default.Terminal,
                    onClick = onTerminal,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PrimaryConversationAction(
    meta: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(onClick = onClick),
        color = Accent,
        shape = shape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Chat,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Conversation",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.60f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SecondaryAction(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = modifier
            .clip(shape)
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.42f),
        shape = shape,
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun ConnectionAction(
    text: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(9.dp)
    Surface(
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick),
        color = color.copy(alpha = 0.07f),
        shape = shape,
        border = BorderStroke(1.dp, color.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetryHeader(loading: Boolean, lastRefresh: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SectionLabel("Telemetry", modifier = Modifier.weight(1f))
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                color = Accent,
                strokeWidth = 1.5.dp
            )
        } else if (lastRefresh > 0) {
            Text(
                text = "updated ${relativeTime(lastRefresh)}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TelemetryGrid(snapshot: SystemRepository.Snapshot) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.42f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.72f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                MetricCell(
                    label = "CPU",
                    value = "${snapshot.cpuPercent}%",
                    detail = "load ${"%.2f".format(snapshot.load1)} · ${snapshot.cpuCount} cores",
                    progress = snapshot.cpuPercent / 100f,
                    critical = snapshot.cpuPercent > 85,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = OutlineDark.copy(alpha = 0.7f)
                )
                MetricCell(
                    label = "RAM",
                    value = "${snapshot.memPercent}%",
                    detail = "${formatBytes(snapshot.memUsed)} / ${formatBytes(snapshot.memTotal)}",
                    progress = snapshot.memPercent / 100f,
                    critical = snapshot.memPercent > 90,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider(
                thickness = 1.dp,
                color = OutlineDark.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
            ) {
                MetricCell(
                    label = "Disk",
                    value = "${snapshot.diskPercent}%",
                    detail = "${formatBytes(snapshot.diskUsed)} / ${formatBytes(snapshot.diskTotal)}",
                    progress = snapshot.diskPercent / 100f,
                    critical = snapshot.diskPercent > 90,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight(),
                    thickness = 1.dp,
                    color = OutlineDark.copy(alpha = 0.7f)
                )
                MetricCell(
                    label = "Docker",
                    value = "${snapshot.containersRunning}/${snapshot.containersTotal}",
                    detail = "running / total",
                    progress = if (snapshot.containersTotal > 0) {
                        snapshot.containersRunning.toFloat() / snapshot.containersTotal
                    } else {
                        0f
                    },
                    critical = false,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MetricCell(
    label: String,
    value: String,
    detail: String,
    progress: Float,
    critical: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = if (critical) Danger else TextPrimary,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = detail,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(RoundedCornerShape(50))
                .background(OutlineSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (critical) Danger else Accent)
            )
        }
    }
}

@Composable
private fun QuickAccess(
    dockerValue: String?,
    serviceValue: String?,
    serviceColor: Color,
    onOpenDocker: () -> Unit,
    onOpenServices: () -> Unit,
    onOpenProcesses: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.42f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.72f))
    ) {
        Column {
            QuickRow(
                label = "Docker",
                icon = Icons.Default.Storage,
                value = dockerValue,
                valueColor = TextSecondary,
                onClick = onOpenDocker
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = OutlineDark.copy(alpha = 0.7f)
            )
            QuickRow(
                label = "Services",
                icon = Icons.Default.Dns,
                value = serviceValue,
                valueColor = serviceColor,
                onClick = onOpenServices
            )
            HorizontalDivider(
                thickness = 1.dp,
                color = OutlineDark.copy(alpha = 0.7f)
            )
            QuickRow(
                label = "Processes",
                icon = Icons.Default.Memory,
                value = null,
                valueColor = TextFaint,
                onClick = onOpenProcesses
            )
        }
    }
}

@Composable
private fun QuickRow(
    label: String,
    icon: ImageVector,
    value: String?,
    valueColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = valueColor,
                maxLines = 1
            )
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun HostFootprint(snapshot: SystemRepository.Snapshot) {
    val systemLine = listOf(snapshot.hostname, snapshot.os, snapshot.kernel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val runtimeLine = listOf(
        snapshot.uptime.takeIf { it.isNotBlank() },
        snapshot.tailscaleIp.takeIf { it.isNotBlank() }?.let { "tailscale $it" }
    ).filterNotNull().joinToString(" · ")

    if (systemLine.isNotBlank() || runtimeLine.isNotBlank()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
            if (systemLine.isNotBlank()) {
                Text(
                    text = systemLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (runtimeLine.isNotBlank()) {
                Text(
                    text = runtimeLine,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = MonoFont,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun LoadingTelemetry() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.40f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Accent,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Reading telemetry",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
fun HostKeyTrustCard(fingerprint: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    Surface(
        color = Warning.copy(alpha = 0.07f),
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.26f)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "Unknown host key",
                style = MaterialTheme.typography.titleMedium,
                color = Warning
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Verify this SHA256 fingerprint on the server before trusting it.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
            Spacer(Modifier.height(8.dp))
            Text(
                fingerprint,
                style = MaterialTheme.typography.labelSmall,
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

private fun connectionColor(status: ConnectionState.Status?): Color = when (status) {
    ConnectionState.Status.CONNECTED -> Success
    ConnectionState.Status.CONNECTING -> Warning
    ConnectionState.Status.RECONNECTING -> Warning
    ConnectionState.Status.NEEDS_HOST_KEY -> Warning
    ConnectionState.Status.ERROR -> Danger
    else -> TextSecondary
}

private fun connectionLabel(status: ConnectionState.Status?): String = when (status) {
    ConnectionState.Status.CONNECTED -> "LIVE"
    ConnectionState.Status.CONNECTING -> "LINKING"
    ConnectionState.Status.RECONNECTING -> "RETRY"
    ConnectionState.Status.NEEDS_HOST_KEY -> "VERIFY"
    ConnectionState.Status.ERROR -> "ERROR"
    else -> "OFFLINE"
}
