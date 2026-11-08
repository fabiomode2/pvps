package dev.pvps.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.system.SystemRepository
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ActionTile
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.GhostButton
import dev.pvps.ui.components.GlassIconButton
import dev.pvps.ui.components.MeterBar
import dev.pvps.ui.components.PvpsCard
import dev.pvps.ui.components.PvpsMark
import dev.pvps.ui.components.SectionHead
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.StatusPill
import dev.pvps.ui.components.TinyAction
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.components.relativeTime
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.DisplayFont
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
import dev.pvps.ui.theme.cardFill
import dev.pvps.ui.theme.pressable
import dev.pvps.ui.theme.topEdgeLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Home: one hero "link" card, then telemetry, then control. Everything else is
 * reachable from the dock or the control list, so the screen stays calm.
 */
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
    onOpenLink: () -> Unit,
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
            HomeTopBar(
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
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "No host yet",
                    message = "Add your VPS or PC over SSH. OpenCode stays private behind the tunnel.",
                    actionLabel = "Manage hosts",
                    onAction = onManageHosts
                )
            }
            return@Scaffold
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val wide = maxWidth >= 620.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 980.dp)
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                ) {
                    Spacer(Modifier.height(Space.xs))
                    if (wide) {
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                            LinkHero(
                                host = host,
                                state = state,
                                openCodeConnected = openCode.connected,
                                openCodeConnecting = openCode.connecting,
                                openCodeVersion = openCode.serverVersion,
                                openCodeSessions = openCode.sessions.size,
                                openCodeError = openCode.error,
                                onConnect = { viewModel.connect(host.id) },
                                onDisconnect = { viewModel.disconnect(host.id) },
                                onRetryOpenCode = { viewModel.connectOpenCode(host, force = true) },
                                onOpenLink = onOpenLink,
                                onChat = onOpenChat,
                                modifier = Modifier.weight(1.15f)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                if (snapshot != null) {
                                    TelemetryBlock(
                                        snapshot = snapshot!!,
                                        loading = loading,
                                        lastRefresh = lastRefresh,
                                        columns = 2
                                    )
                                } else {
                                    PendingTelemetry(loading = loading, connected = state?.status == ConnectionState.Status.CONNECTED)
                                }
                            }
                        }
                    } else {
                        LinkHero(
                            host = host,
                            state = state,
                            openCodeConnected = openCode.connected,
                            openCodeConnecting = openCode.connecting,
                            openCodeVersion = openCode.serverVersion,
                            openCodeSessions = openCode.sessions.size,
                            openCodeError = openCode.error,
                            onConnect = { viewModel.connect(host.id) },
                            onDisconnect = { viewModel.disconnect(host.id) },
                            onRetryOpenCode = { viewModel.connectOpenCode(host, force = true) },
                            onOpenLink = onOpenLink,
                            onChat = onOpenChat,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (state?.status == ConnectionState.Status.NEEDS_HOST_KEY) {
                        Spacer(Modifier.height(Space.block))
                        HostKeyTrustCard(
                            fingerprint = state.hostKeySha256.orEmpty(),
                            onTrust = { viewModel.trustHostKey(host.id) },
                            onCancel = { viewModel.disconnect(host.id) }
                        )
                    }
                    if (error != null) {
                        Spacer(Modifier.height(Space.block))
                        ErrorBanner(error!!) { error = null }
                    }

                    Spacer(Modifier.height(Space.block))
                    val dockerValue = snapshot
                        ?.takeIf { it.containersTotal > 0 }
                        ?.let { "${it.containersRunning}/${it.containersTotal}" }
                        ?: containers.takeIf { it.isNotEmpty() }
                            ?.let { "${it.count { c -> c.running }}/${it.size}" }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                        ActionTile(
                            label = "Files",
                            icon = Icons.Default.Folder,
                            onClick = onOpenFiles,
                            modifier = Modifier.weight(1f),
                            tint = Violet
                        )
                        ActionTile(
                            label = "Shell",
                            icon = Icons.Default.Terminal,
                            onClick = onOpenTerminal,
                            modifier = Modifier.weight(1f),
                            tint = Success
                        )
                        ActionTile(
                            label = "Docker",
                            icon = Icons.Default.Storage,
                            onClick = onOpenDocker,
                            modifier = Modifier.weight(1f),
                            tint = Accent,
                            meta = dockerValue
                        )
                    }

                    if (!wide) {
                        if (snapshot != null) {
                            TelemetryBlock(
                                snapshot = snapshot!!,
                                loading = loading,
                                lastRefresh = lastRefresh,
                                columns = 2
                            )
                        } else {
                            PendingTelemetry(loading = loading, connected = state?.status == ConnectionState.Status.CONNECTED)
                        }
                    }

                    SectionHead(title = "Control")
                    PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
                        Column {
                            ControlRow(
                                label = "Services",
                                meta = "systemd units",
                                icon = Icons.Default.Dns,
                                tint = Warning,
                                value = snapshot?.failedServices?.let { if (it > 0) "$it failed" else "all up" },
                                valueColor = if ((snapshot?.failedServices ?: 0) > 0) Danger else Success,
                                onClick = onOpenServices
                            )
                            RowDivider()
                            ControlRow(
                                label = "Processes",
                                meta = "cpu · memory",
                                icon = Icons.Default.Memory,
                                tint = Danger,
                                value = null,
                                valueColor = TextFaint,
                                onClick = onOpenProcesses
                            )
                            RowDivider()
                            ControlRow(
                                label = "Hosts",
                                meta = "ssh targets",
                                icon = Icons.Default.Power,
                                tint = Violet,
                                value = "${hosts.size}",
                                valueColor = TextSecondary,
                                onClick = onManageHosts
                            )
                        }
                    }

                    snapshot?.let { snap ->
                        Spacer(Modifier.height(Space.lg))
                        HostFootprint(snap)
                    }
                    Spacer(Modifier.height(Space.dockClearance))
                }
            }
        }
    }
}

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 60.dp)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.05f))
    )
}

/* ------------------------------------------------------------------------- */
/* Top bar                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
private fun HomeTopBar(
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
    val label = connectionTitle(state?.status)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.md, top = Space.md, bottom = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            GlassIconButton(onClick = onBack, size = 36.dp) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = TextPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(Space.md))
        } else {
            PvpsMark(32.dp)
            Spacer(Modifier.width(Space.md))
        }
        Box(modifier = Modifier.weight(1f)) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .pressable(enabled = hosts.isNotEmpty()) { menuOpen = true }
                    .padding(vertical = Space.xxs, horizontal = Space.xxs)
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
                    if (hosts.size > 1) {
                        Spacer(Modifier.width(Space.xs))
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = "Switch host",
                            tint = TextFaint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = PvpsType.monoBody,
                        color = color,
                        maxLines = 1
                    )
                    if (host != null) {
                        Text(
                            text = " · ${host.sshUser}@${host.sshHost}",
                            style = PvpsType.monoBody,
                            color = TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                hosts.forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (item.id == host?.id) Accent else Color.Transparent)
                                )
                                Spacer(Modifier.width(Space.sm))
                                Column {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (item.id == host?.id) Accent else TextPrimary
                                    )
                                    Text(
                                        "${item.sshUser}@${item.sshHost}",
                                        style = PvpsType.monoBody,
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
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Accent,
                strokeWidth = 1.6.dp
            )
            Spacer(Modifier.width(Space.md))
        } else {
            GlassIconButton(onClick = onRefresh, size = 36.dp) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    tint = TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(Space.sm))
        }
        GlassIconButton(onClick = onManageHosts, size = 36.dp) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Manage hosts",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Hero                                                                       */
/* ------------------------------------------------------------------------- */

@Composable
private fun LinkHero(
    host: Host,
    state: ConnectionState?,
    openCodeConnected: Boolean,
    openCodeConnecting: Boolean,
    openCodeVersion: String,
    openCodeSessions: Int,
    openCodeError: String?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetryOpenCode: () -> Unit,
    onOpenLink: () -> Unit,
    onChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = state?.status
    val connected = status == ConnectionState.Status.CONNECTED
    val busy = status == ConnectionState.Status.CONNECTING || status == ConnectionState.Status.RECONNECTING
    val color = connectionColor(status)
    val title = connectionTitle(status)
    val detail = when (status) {
        ConnectionState.Status.CONNECTED ->
            state?.message?.takeIf { it.isNotBlank() } ?: "SSH session established"
        ConnectionState.Status.CONNECTING ->
            state?.message?.takeIf { it.isNotBlank() } ?: "Opening SSH session"
        ConnectionState.Status.RECONNECTING ->
            state?.message?.takeIf { it.isNotBlank() } ?: "Retrying the SSH session"
        ConnectionState.Status.NEEDS_HOST_KEY -> "Verify the fingerprint before trusting"
        ConnectionState.Status.ERROR ->
            state?.message?.takeIf { it.isNotBlank() } ?: "Check the address, key or network"
        else -> "Tap connect to bring the link up"
    }
    val openCodeLabel = when {
        openCodeConnected -> buildString {
            append("OpenCode")
            if (openCodeVersion.isNotBlank()) append(" v").append(openCodeVersion)
            append(" · ").append(openCodeSessions)
            append(if (openCodeSessions == 1) " session" else " sessions")
        }
        openCodeConnecting -> "OpenCode · connecting"
        else -> "OpenCode · offline"
    }
    val address = buildString {
        append(host.sshUser)
        append('@')
        append(host.sshHost)
        if (host.sshPort != 22) {
            append(':')
            append(host.sshPort)
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.hero))
            .background(cardFill(SurfaceRaised.copy(alpha = 0.95f), SurfaceDark))
            .border(1.dp, topEdgeLight(0.10f), RoundedCornerShape(Radius.hero))
    ) {
        // A single light source behind the status orb.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(color.copy(alpha = 0.13f), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 480f
                    )
                )
        )
        Column(modifier = Modifier.padding(Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusOrb(color = color, live = connected, size = 62.dp)
                Spacer(Modifier.width(Space.lg))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = DisplayFont,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(Space.xxs))
                    Text(
                        text = address,
                        style = PvpsType.monoBody,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(Modifier.height(Space.lg))
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(
                    text = openCodeLabel,
                    color = if (openCodeConnected) Success else if (openCodeConnecting) Warning else TextFaint
                )
                Spacer(Modifier.weight(1f))
                TinyAction("Details") { onOpenLink() }
            }
            if (connected && !openCodeConnected && !openCodeConnecting) {
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = openCodeError?.takeIf { it.isNotBlank() } ?: "OpenCode is not answering behind the tunnel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Warning,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    TinyAction("Retry") { onRetryOpenCode() }
                }
            }
            Spacer(Modifier.height(Space.xl))
            when {
                connected -> Row(verticalAlignment = Alignment.CenterVertically) {
                    SignalButton(
                        text = "Conversation",
                        icon = Icons.AutoMirrored.Filled.Chat,
                        onClick = onChat,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.055f))
                            .border(1.dp, Color.White.copy(alpha = 0.09f), CircleShape)
                            .pressable(onClick = onDisconnect),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.LinkOff,
                            contentDescription = "Disconnect",
                            tint = TextSecondary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
                busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Warning,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Space.md))
                    Text(
                        "Reconnecting automatically…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    GhostButton("Cancel", onClick = onDisconnect)
                }
                status == ConnectionState.Status.NEEDS_HOST_KEY -> GhostButton(
                    text = "Waiting for verification",
                    onClick = onDisconnect,
                    modifier = Modifier.fillMaxWidth()
                )
                else -> SignalButton(
                    text = if (status == ConnectionState.Status.ERROR) "Retry connection" else "Connect",
                    icon = Icons.Default.Power,
                    onClick = onConnect,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun StatusOrb(color: Color, live: Boolean, size: androidx.compose.ui.unit.Dp) {
    val transition = rememberInfiniteTransition(label = "orb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
        label = "orbPulse"
    )
    Canvas(modifier = Modifier.size(size)) {
        val radius = this.size.minDimension / 2f
        drawCircle(color = color.copy(alpha = 0.055f), radius = radius)
        drawCircle(
            color = color.copy(alpha = 0.10f),
            radius = radius * 0.78f,
            style = Stroke(width = 1.dp.toPx())
        )
        if (live) {
            drawCircle(
                color = color.copy(alpha = 0.22f * (1f - pulse)),
                radius = radius * (0.45f + 0.55f * pulse),
                style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        drawCircle(color = color.copy(alpha = if (live) 0.95f else 0.45f), radius = radius * 0.24f)
    }
}

/* ------------------------------------------------------------------------- */
/* Telemetry                                                                  */
/* ------------------------------------------------------------------------- */

@Composable
private fun TelemetryBlock(
    snapshot: SystemRepository.Snapshot,
    loading: Boolean,
    lastRefresh: Long,
    columns: Int
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHead(title = "Telemetry") {
            Text(
                text = if (loading) "refreshing…" else "updated ${relativeTime(lastRefresh)}",
                style = PvpsType.monoBody,
                color = TextFaint,
                maxLines = 1
            )
        }
        val tiles = listOf(
            MetricSpec("CPU", "${snapshot.cpuPercent}", "%", "load ${"%.2f".format(snapshot.load1)} · ${snapshot.cpuCount} cores", snapshot.cpuPercent / 100f, snapshot.cpuPercent > 85),
            MetricSpec("RAM", "${snapshot.memPercent}", "%", "${formatBytes(snapshot.memUsed)} / ${formatBytes(snapshot.memTotal)}", snapshot.memPercent / 100f, snapshot.memPercent > 90),
            MetricSpec("DISK", "${snapshot.diskPercent}", "%", "${formatBytes(snapshot.diskUsed)} / ${formatBytes(snapshot.diskTotal)}", snapshot.diskPercent / 100f, snapshot.diskPercent > 90),
            MetricSpec(
                "DOCKER",
                "${snapshot.containersRunning}",
                "/${snapshot.containersTotal}",
                "containers running",
                if (snapshot.containersTotal > 0) snapshot.containersRunning.toFloat() / snapshot.containersTotal else 0f,
                false
            )
        )
        if (columns == 1) {
            tiles.forEach { spec ->
                MetricTile(spec, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.md))
            }
        } else {
            tiles.chunked(2).forEach { rowSpecs ->
                Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    rowSpecs.forEach { spec ->
                        MetricTile(spec, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(Space.md))
            }
        }
    }
}

private data class MetricSpec(
    val label: String,
    val value: String,
    val unit: String,
    val detail: String,
    val progress: Float,
    val critical: Boolean
)

@Composable
private fun MetricTile(spec: MetricSpec, modifier: Modifier = Modifier) {
    val accent = if (spec.critical) Danger else Accent
    PvpsCard(modifier = modifier, shape = RoundedCornerShape(Radius.md)) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                spec.label,
                style = PvpsType.eyebrow,
                color = TextFaint,
                maxLines = 1
            )
            Spacer(Modifier.height(Space.sm))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    spec.value,
                    style = PvpsType.numericHero,
                    color = if (spec.critical) Danger else TextPrimary,
                    maxLines = 1
                )
                Text(
                    spec.unit,
                    style = PvpsType.monoBody,
                    color = TextFaint,
                    modifier = Modifier.padding(bottom = 6.dp),
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(Space.sm))
            MeterBar(progress = spec.progress, accent = accent)
            Spacer(Modifier.height(Space.sm))
            Text(
                spec.detail,
                style = PvpsType.monoBody,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PendingTelemetry(loading: Boolean, connected: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHead(title = "Telemetry")
        PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
            Row(
                modifier = Modifier.padding(Space.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Accent,
                        strokeWidth = 1.6.dp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TextFaint)
                    )
                }
                Spacer(Modifier.width(Space.md))
                Text(
                    text = when {
                        loading -> "Reading telemetry"
                        connected -> "Waiting for the first sample"
                        else -> "Connect to stream live metrics"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Control                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
private fun ControlRow(
    label: String,
    meta: String,
    icon: ImageVector,
    tint: Color,
    value: String?,
    valueColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(tint.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1
            )
            Text(
                meta,
                style = PvpsType.monoBody,
                color = TextFaint,
                maxLines = 1
            )
        }
        if (value != null) {
            Text(
                value,
                style = PvpsType.monoStrong,
                color = valueColor,
                maxLines = 1
            )
            Spacer(Modifier.width(Space.sm))
        }
        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextFaint,
            modifier = Modifier.size(17.dp)
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Footer + host key                                                          */
/* ------------------------------------------------------------------------- */

@Composable
private fun HostFootprint(snapshot: SystemRepository.Snapshot) {
    val systemLine = listOf(snapshot.hostname, snapshot.os, snapshot.kernel)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val runtimeLine = listOf(
        snapshot.uptime.takeIf { it.isNotBlank() },
        snapshot.tailscaleIp.takeIf { it.isNotBlank() }?.let { "tailscale $it" }
    ).filterNotNull().joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xxs)
    ) {
        if (systemLine.isNotBlank()) {
            Text(
                text = systemLine,
                style = PvpsType.monoBody,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (runtimeLine.isNotBlank()) {
            Text(
                text = runtimeLine,
                style = PvpsType.monoBody,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun HostKeyTrustCard(fingerprint: String, onTrust: () -> Unit, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Warning.copy(alpha = 0.07f))
            .border(1.dp, Warning.copy(alpha = 0.22f), RoundedCornerShape(Radius.lg))
            .padding(Space.lg)
    ) {
        Text(
            "Unknown host key",
            style = MaterialTheme.typography.titleMedium,
            color = Warning
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Verify this SHA256 fingerprint on the server before trusting it.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            fingerprint,
            style = PvpsType.monoBody,
            color = TextPrimary
        )
        Spacer(Modifier.height(Space.md))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SignalButton("Trust and connect", onTrust)
            GhostButton("Cancel", onCancel)
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Status mapping                                                             */
/* ------------------------------------------------------------------------- */

private fun connectionColor(status: ConnectionState.Status?): Color = when (status) {
    ConnectionState.Status.CONNECTED -> Success
    ConnectionState.Status.CONNECTING -> Warning
    ConnectionState.Status.RECONNECTING -> Warning
    ConnectionState.Status.NEEDS_HOST_KEY -> Warning
    ConnectionState.Status.ERROR -> Danger
    else -> TextFaint
}

private fun connectionTitle(status: ConnectionState.Status?): String = when (status) {
    ConnectionState.Status.CONNECTED -> "Connected"
    ConnectionState.Status.CONNECTING -> "Connecting…"
    ConnectionState.Status.RECONNECTING -> "Reconnecting…"
    ConnectionState.Status.NEEDS_HOST_KEY -> "Verify host key"
    ConnectionState.Status.ERROR -> "Connection failed"
    else -> "Offline"
}
