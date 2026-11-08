package dev.pvps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.ConnectionState
import dev.pvps.system.SystemRepository
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.MeterBar
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.PvpsCard
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.StatusDot
import dev.pvps.ui.components.StatusPill
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
import dev.pvps.ui.theme.pressable
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DockerScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connected = host != null && states[host.id]?.status == ConnectionState.Status.CONNECTED
    var containers by remember { mutableStateOf<List<SystemRepository.Container>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var confirmAction by remember { mutableStateOf<Triple<SystemRepository.Container, String, String>?>(null) }
    var logsTarget by remember { mutableStateOf<SystemRepository.Container?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(host?.id, connected, refresh) {
        if (!connected || host == null) return@LaunchedEffect
        loading = true
        runCatching { viewModel.system.containers(host.id) }
            .onSuccess { containers = it }
            .onFailure { error = it.message }
        loading = false
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Docker",
                subtitle = host?.let { "${it.name} · ${containers.count { c -> c.running }}/${containers.size} running" } ?: "No host",
                onBack = onBack,
                actions = {
                    IconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextSecondary,
                        enabled = !loading,
                        onClick = { refresh++ }
                    )
                }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "SSH not connected",
                    message = "Connect to the host from the dashboard first.",
                    actionLabel = "Connect",
                    onAction = { host?.let { viewModel.connect(it.id) } }
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item { Spacer(Modifier.height(Space.xs)) }
            if (error != null) item { ErrorBanner(error!!) { error = null } }
            if (loading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            if (!loading && containers.isEmpty()) {
                item { EmptyState("No containers", "Docker returned an empty list.") }
            }
            items(containers, key = { it.id }) { container ->
                val stateColor = if (container.running) Success else TextSecondary
                PvpsCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(stateColor, 7)
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                container.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(Space.sm))
                            StatusPill(container.state, stateColor)
                        }
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            container.image,
                            style = PvpsType.monoBody,
                            color = TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (container.ports.isNotBlank()) {
                            Spacer(Modifier.height(Space.xxs))
                            Text(
                                container.ports,
                                style = PvpsType.monoBody,
                                color = TextFaint,
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.height(Space.xxs))
                        Text(
                            container.status,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 2
                        )
                        Spacer(Modifier.height(Space.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                            if (container.running) {
                                IconAction(
                                    icon = Icons.Default.RestartAlt,
                                    contentDescription = "Restart",
                                    tint = Warning,
                                    size = 32.dp,
                                    onClick = {
                                        confirmAction = Triple(container, "restart", "Restart ${container.name}?")
                                    }
                                )
                                IconAction(
                                    icon = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Danger,
                                    size = 32.dp,
                                    onClick = {
                                        confirmAction = Triple(container, "stop", "Stop ${container.name}?")
                                    }
                                )
                            } else {
                                IconAction(
                                    icon = Icons.Default.PlayArrow,
                                    contentDescription = "Start",
                                    tint = Success,
                                    size = 32.dp,
                                    onClick = {
                                        scope.launch {
                                            runCatching { viewModel.system.containerAction(host.id, container.name, "start") }
                                                .onFailure { error = it.message }
                                            refresh++
                                        }
                                    }
                                )
                            }
                            IconAction(
                                icon = Icons.Default.Terminal,
                                contentDescription = "Logs",
                                tint = TextSecondary,
                                size = 32.dp,
                                onClick = { logsTarget = container }
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(Space.dockClearance)) }
        }
    }

    confirmAction?.let { (container, action, message) ->
        ConfirmDialog(
            title = "Confirm action",
            message = message,
            confirmLabel = action.replaceFirstChar { it.uppercase() },
            destructive = action == "stop",
            onConfirm = {
                scope.launch {
                    runCatching { viewModel.system.containerAction(host!!.id, container.name, action) }
                        .onFailure { error = it.message }
                    refresh++
                }
            },
            onDismiss = { confirmAction = null }
        )
    }
    logsTarget?.let { container ->
        LogsDialog(
            title = "docker logs · ${container.name}",
            load = { viewModel.system.containerLogs(host!!.id, container.name) },
            onDismiss = { logsTarget = null }
        )
    }
}

@Composable
fun ServicesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connected = host != null && states[host.id]?.status == ConnectionState.Status.CONNECTED
    var services by remember { mutableStateOf<List<SystemRepository.Service>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    var confirmAction by remember { mutableStateOf<Triple<SystemRepository.Service, String, String>?>(null) }
    var logsTarget by remember { mutableStateOf<SystemRepository.Service?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(host?.id, connected, refresh) {
        if (!connected || host == null) return@LaunchedEffect
        loading = true
        runCatching { viewModel.system.services(host.id) }
            .onSuccess { services = it }
            .onFailure { error = it.message }
        loading = false
    }

    val filtered = services.filter { service ->
        (filter == "all" || (filter == "running" && service.isRunning) || (filter == "failed" && service.isFailed)) &&
            (search.isBlank() || service.unit.contains(search, true) || service.description.contains(search, true))
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Services",
                subtitle = host?.let { "${it.name} · ${services.count { s -> s.isRunning }} running" } ?: "No host",
                onBack = onBack,
                actions = {
                    IconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextSecondary,
                        enabled = !loading,
                        onClick = { refresh++ }
                    )
                }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "SSH not connected",
                    message = "Connect to the host from the dashboard first.",
                    actionLabel = "Connect",
                    onAction = { host?.let { viewModel.connect(it.id) } }
                )
            }
            return@Scaffold
        }
        val filterColors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.05f),
            labelColor = TextSecondary,
            selectedContainerColor = Accent.copy(alpha = 0.14f),
            selectedLabelColor = Accent
        )
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Filter services", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                shape = RoundedCornerShape(Radius.md),
                textStyle = MaterialTheme.typography.bodyMedium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent.copy(alpha = 0.70f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                    focusedLabelColor = Accent,
                    unfocusedLabelColor = TextFaint,
                    cursorColor = Accent,
                    focusedContainerColor = SurfaceRaised.copy(alpha = 0.55f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.035f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                FilterChip(
                    selected = filter == "all",
                    onClick = { filter = "all" },
                    label = { Text("All") },
                    shape = RoundedCornerShape(Radius.pill),
                    colors = filterColors,
                    border = null
                )
                FilterChip(
                    selected = filter == "running",
                    onClick = { filter = "running" },
                    label = { Text("Running") },
                    shape = RoundedCornerShape(Radius.pill),
                    colors = filterColors,
                    border = null
                )
                FilterChip(
                    selected = filter == "failed",
                    onClick = { filter = "failed" },
                    label = { Text("Failed") },
                    shape = RoundedCornerShape(Radius.pill),
                    colors = filterColors,
                    border = null
                )
            }
            if (error != null) {
                Box(modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)) {
                    ErrorBanner(error!!) { error = null }
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = Space.gutter),
                verticalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                item { Spacer(Modifier.height(Space.xs)) }
                items(filtered, key = { it.unit }) { service ->
                    val stateColor = when {
                        service.isFailed -> Danger
                        service.isRunning -> Success
                        else -> TextSecondary
                    }
                    PvpsCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.md)
                    ) {
                        Column(modifier = Modifier.padding(Space.lg)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(stateColor, 7)
                                Spacer(Modifier.width(Space.sm))
                                Text(
                                    service.unit,
                                    style = PvpsType.monoStrong,
                                    color = TextPrimary,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.width(Space.sm))
                                StatusPill("${service.active}/${service.sub}", stateColor)
                            }
                            Spacer(Modifier.height(Space.xs))
                            Text(
                                service.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(Space.md))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                                if (service.isRunning) {
                                    IconAction(
                                        icon = Icons.Default.RestartAlt,
                                        contentDescription = "Restart",
                                        tint = Warning,
                                        size = 32.dp,
                                        onClick = {
                                            confirmAction = Triple(service, "restart", "Restart ${service.unit}?")
                                        }
                                    )
                                    IconAction(
                                        icon = Icons.Default.Stop,
                                        contentDescription = "Stop",
                                        tint = Danger,
                                        size = 32.dp,
                                        onClick = {
                                            confirmAction = Triple(service, "stop", "Stop ${service.unit}?")
                                        }
                                    )
                                } else {
                                    IconAction(
                                        icon = Icons.Default.PlayArrow,
                                        contentDescription = "Start",
                                        tint = Success,
                                        size = 32.dp,
                                        onClick = {
                                            scope.launch {
                                                runCatching { viewModel.system.serviceAction(host.id, service.unit, "start") }
                                                    .onFailure { error = it.message }
                                                refresh++
                                            }
                                        }
                                    )
                                }
                                IconAction(
                                    icon = Icons.Default.Terminal,
                                    contentDescription = "Logs",
                                    tint = TextSecondary,
                                    size = 32.dp,
                                    onClick = { logsTarget = service }
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(Space.dockClearance)) }
            }
        }
    }

    confirmAction?.let { (service, action, message) ->
        val protected = service.unit in setOf("ssh.service", "sshd.service", "tailscaled.service", "docker.service")
        ConfirmDialog(
            title = if (protected) "Protected service" else "Confirm action",
            message = if (protected) "$message This can lock you out of the host." else message,
            confirmLabel = action.replaceFirstChar { it.uppercase() },
            destructive = action == "stop",
            onConfirm = {
                scope.launch {
                    runCatching { viewModel.system.serviceAction(host!!.id, service.unit, action) }
                        .onFailure { error = it.message }
                    refresh++
                }
            },
            onDismiss = { confirmAction = null }
        )
    }
    logsTarget?.let { service ->
        LogsDialog(
            title = "journalctl · ${service.unit}",
            load = { viewModel.system.serviceLogs(host!!.id, service.unit) },
            onDismiss = { logsTarget = null }
        )
    }
}

private enum class ProcessSort(val label: String) {
    RAM("RAM"),
    CPU("CPU"),
    NAME("NAME")
}

private data class SwapUsage(val total: Long, val used: Long)

private val ProcessPidWidth = 50.dp
private val ProcessCpuWidth = 48.dp
private val ProcessRamWidth = 62.dp
private val ProcessKillWidth = 40.dp

/** 32–34dp chrome action: quiet fill, no ripple, tactile press. */
@Composable
private fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 34.dp,
    container: Color = Color.White.copy(alpha = 0.05f)
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (enabled) container else Color.White.copy(alpha = 0.02f))
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else TextFaint.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

@Composable
fun ProcessesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connected = host != null && states[host.id]?.status == ConnectionState.Status.CONNECTED
    var processes by remember { mutableStateOf<List<SystemRepository.Process>>(emptyList()) }
    var snapshot by remember { mutableStateOf<SystemRepository.Snapshot?>(null) }
    var swap by remember { mutableStateOf<SwapUsage?>(null) }
    var loading by remember { mutableStateOf(host != null && connected) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProcessSort.RAM) }
    var autoRefresh by remember { mutableStateOf(false) }
    var killTarget by remember { mutableStateOf<Pair<String, SystemRepository.Process>?>(null) }
    var forceKill by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(host?.id, connected, refresh) {
        if (!connected || host == null) return@LaunchedEffect
        loading = true
        error = null
        snapshot = runCatching { viewModel.system.snapshot(host.id) }
            .onFailure { error = it.message }
            .getOrNull()
        runCatching { viewModel.system.processes(host.id) }
            .onSuccess { processes = it }
            .onFailure { error = it.message }
        // Snapshot predates swap reporting. This supplemental read-only probe
        // keeps the memory card useful without changing repository interfaces.
        swap = runCatching {
            viewModel.container.ssh.exec(
                host.id,
                "LC_ALL=C free -b 2>/dev/null | awk '/^Swap:/{print ${'$'}2, ${'$'}3}'",
                15_000
            )
        }.getOrNull()
            ?.takeIf { it.isSuccess }
            ?.stdout
            ?.let(::parseSwap)
        loading = false
    }

    // Wait for the current fetch to finish before arming the next tick. This
    // avoids overlapping SSH reads, and the effect is cancelled on dispose or
    // whenever the screen stops being composed.
    LaunchedEffect(host?.id, connected, autoRefresh, loading) {
        if (!autoRefresh || !connected || host == null || loading) return@LaunchedEffect
        delay(5_000)
        refresh++
    }

    val filtered = processes.filter {
        search.isBlank() ||
            it.command.contains(search, true) ||
            it.args.contains(search, true) ||
            it.user.contains(search, true)
    }
    val sorted = filtered.sortedWith(
        when (sort) {
            ProcessSort.RAM -> compareByDescending<SystemRepository.Process> { it.mem }
                .thenByDescending { it.rssKb }
            ProcessSort.CPU -> compareByDescending<SystemRepository.Process> { it.cpu }
                .thenByDescending { it.rssKb }
            ProcessSort.NAME -> compareBy<SystemRepository.Process> { it.command.lowercase() }
                .thenBy { it.pid }
        }
    )
    val top = processes
        .sortedWith(compareByDescending<SystemRepository.Process> { it.rssKb }.thenByDescending { it.mem })
        .take(3)
    val topRanks = top.mapIndexed { index, process -> process.pid to index + 1 }.toMap()
    val cpuPeak = filtered.maxOfOrNull { it.cpu }?.coerceAtLeast(1.0) ?: 1.0
    val memPeak = filtered.maxOfOrNull { it.mem }?.coerceAtLeast(0.1) ?: 0.1

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Processes",
                subtitle = host?.let {
                    "${it.name} · ${processes.size} processes · ${when {
                        loading -> "syncing"
                        autoRefresh -> "auto 5s"
                        else -> "live"
                    }}"
                } ?: "No host",
                onBack = onBack,
                actions = {
                    IconAction(
                        icon = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = TextSecondary,
                        enabled = !loading,
                        onClick = { refresh++ }
                    )
                }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                EmptyState(
                    title = "SSH not connected",
                    message = "Connect to the host from the dashboard first.",
                    actionLabel = "Connect",
                    onAction = { host?.let { viewModel.connect(it.id) } }
                )
            }
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            snapshot?.let { snap ->
                ProcessMemoryCard(snapshot = snap, top = top, swap = swap)
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = {
                    Text("Filter name, args or user", color = TextFaint, style = MaterialTheme.typography.bodySmall)
                },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = TextFaint,
                        modifier = Modifier.size(17.dp)
                    )
                },
                trailingIcon = if (search.isNotBlank()) {
                    {
                        IconAction(
                            icon = Icons.Default.Close,
                            contentDescription = "Clear filter",
                            tint = TextFaint,
                            size = 28.dp,
                            container = Color.Transparent,
                            onClick = { search = "" }
                        )
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.xs),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(Radius.md),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent.copy(alpha = 0.70f),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
                    cursorColor = Accent,
                    focusedContainerColor = SurfaceRaised.copy(alpha = 0.55f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.035f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter, vertical = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProcessSortControl(selected = sort, onSelect = { sort = it })
                Spacer(Modifier.weight(1f))
                Text(
                    "${sorted.size} shown",
                    style = PvpsType.monoBody,
                    color = TextFaint
                )
                Spacer(Modifier.width(Space.sm))
                AutoRefreshChip(
                    enabled = autoRefresh,
                    onClick = { autoRefresh = !autoRefresh }
                )
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter)
                        .height(2.dp)
                        .clip(RoundedCornerShape(Radius.pill)),
                    color = Accent,
                    trackColor = Color.White.copy(alpha = 0.06f)
                )
            }
            if (error != null && processes.isNotEmpty()) {
                Box(modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)) {
                    ErrorBanner(error!!) { error = null }
                }
            }
            ProcessTableHeader()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = Space.dockClearance)
            ) {
                when {
                    loading && processes.isEmpty() -> item { ProcessLoadingState() }
                    error != null && processes.isEmpty() -> item {
                        EmptyState(
                            title = "Couldn't read processes",
                            message = error.orEmpty().ifBlank { "The host did not return a process table." },
                            actionLabel = "Retry",
                            onAction = { refresh++ }
                        )
                    }
                    sorted.isEmpty() -> item {
                        EmptyState(
                            title = if (search.isBlank()) "No processes" else "No matches",
                            message = if (search.isBlank()) {
                                "The host returned an empty process table."
                            } else {
                                "Nothing matches \"$search\". Try a command, user or shorter term."
                            },
                            actionLabel = if (search.isBlank()) "Refresh" else "Clear filter",
                            onAction = {
                                if (search.isBlank()) refresh++ else search = ""
                            }
                        )
                    }
                    else -> items(sorted, key = { it.pid }) { process ->
                        ProcessRow(
                            process = process,
                            rank = topRanks[process.pid],
                            cpuPeak = cpuPeak,
                            memPeak = memPeak,
                            onKill = {
                                host?.id?.let { hostId ->
                                    killTarget = hostId to process
                                    forceKill = false
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    killTarget?.let { (hostId, process) ->
        val protected = isProtectedProcess(process)
        AlertDialog(
            onDismissRequest = { killTarget = null },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(Radius.xl),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = { Text("Kill ${process.command}?", style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        "PID ${process.pid} · ${process.user}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "CPU ${formatPercent(process.cpu)} · ${formatMemory(process.rssKb)} · ${process.elapsed}",
                        style = PvpsType.monoBody,
                        color = TextFaint
                    )
                    Spacer(Modifier.height(Space.md))
                    Text(
                        when {
                            protected -> "This process is system-critical and must not be killed."
                            forceKill -> "SIGKILL cannot be caught or ignored. The process will be terminated immediately."
                            else -> "SIGTERM lets the process shut down cleanly. Use force kill only if it is stuck."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (protected) Danger else TextSecondary
                    )
                    if (!protected) {
                        Spacer(Modifier.height(Space.sm))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(Color.White.copy(alpha = 0.04f))
                                .pressable { forceKill = !forceKill }
                                .padding(horizontal = Space.sm, vertical = Space.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = forceKill, onCheckedChange = { forceKill = it })
                            Spacer(Modifier.width(Space.xs))
                            Column {
                                Text("Force kill", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    "SIGKILL · cannot be caught",
                                    style = PvpsType.monoBody,
                                    color = TextFaint
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        killTarget = null
                        scope.launch {
                            runCatching {
                                viewModel.system.killProcess(hostId, process.pid, forceKill)
                            }.onFailure { error = it.message }
                            refresh++
                        }
                    },
                    enabled = !protected
                ) {
                    Text(
                        if (forceKill) "SIGKILL" else "SIGTERM",
                        color = if (protected) TextFaint else Danger,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { killTarget = null }) {
                    Text("CANCEL", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
                }
            }
        )
    }
}

@Composable
private fun ProcessSortControl(selected: ProcessSort, onSelect: (ProcessSort) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(Space.xxs),
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProcessSort.values().forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (active) Accent.copy(alpha = 0.14f) else Color.Transparent)
                    .selectable(
                        selected = active,
                        onClick = { onSelect(option) },
                        role = Role.Tab
                    )
                    .padding(horizontal = Space.sm, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) Accent else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun AutoRefreshChip(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Accent else TextFaint
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (enabled) Accent.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.04f))
            .pressable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xxs)
    ) {
        StatusDot(if (enabled) Success else TextFaint, 5)
        Text(
            "AUTO 5S",
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun ProcessTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.gutter, end = Space.gutter, top = Space.md, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "PROCESS",
            modifier = Modifier.weight(1f),
            style = PvpsType.eyebrow,
            color = TextFaint
        )
        Text(
            "PID",
            modifier = Modifier.width(ProcessPidWidth),
            style = PvpsType.eyebrow,
            color = TextFaint,
            textAlign = TextAlign.End
        )
        Text(
            "CPU",
            modifier = Modifier.width(ProcessCpuWidth),
            style = PvpsType.eyebrow,
            color = TextFaint,
            textAlign = TextAlign.End
        )
        Text(
            "RAM",
            modifier = Modifier.width(ProcessRamWidth),
            style = PvpsType.eyebrow,
            color = TextFaint,
            textAlign = TextAlign.End
        )
        Spacer(Modifier.width(ProcessKillWidth))
    }
}

@Composable
private fun ProcessLoadingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = Accent,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            "Reading process table…",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )
    }
}

@Composable
private fun ProcessMemoryCard(
    snapshot: SystemRepository.Snapshot,
    top: List<SystemRepository.Process>,
    swap: SwapUsage?
) {
    val percent = snapshot.memPercent.coerceIn(0, 100)
    val color = when {
        percent >= 90 -> Danger
        percent >= 75 -> Warning
        else -> Success
    }
    PvpsCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        shape = RoundedCornerShape(Radius.lg)
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                "MEMORY",
                style = PvpsType.eyebrow,
                color = TextFaint
            )
            Spacer(Modifier.height(Space.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        formatBytes(snapshot.memUsed),
                        style = PvpsType.numericHero,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        " / ${formatBytes(snapshot.memTotal)}",
                        style = PvpsType.monoBody,
                        color = TextFaint,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.width(Space.sm))
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$percent%",
                        style = PvpsType.numericHero,
                        color = color,
                        maxLines = 1
                    )
                    Text(
                        "USED",
                        style = PvpsType.eyebrow,
                        color = TextFaint
                    )
                }
            }
            Spacer(Modifier.height(Space.md))
            MeterBar(progress = percent / 100f, accent = color)
            Spacer(Modifier.height(Space.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                MemoryStat(
                    label = "AVAILABLE",
                    value = formatBytes(snapshot.memAvailable),
                    modifier = Modifier.weight(1.1f),
                    color = Success
                )
                val swapTotal = swap?.total ?: 0L
                MemoryStat(
                    label = if (swapTotal > 0) "SWAP ${formatBytes(swapTotal)}" else "SWAP",
                    value = when {
                        swap == null -> "n/a"
                        swap.total <= 0L -> "none"
                        else -> formatBytes(swap.used)
                    },
                    modifier = Modifier.weight(1.45f),
                    color = if (swap != null && swap.total > 0) TextSecondary else TextFaint
                )
                MemoryStat(
                    label = "LOAD AVG",
                    value = String.format(
                        Locale.US,
                        "%.2f %.2f %.2f",
                        snapshot.load1,
                        snapshot.load5,
                        snapshot.load15
                    ),
                    modifier = Modifier.weight(1.45f),
                    color = TextPrimary,
                    mono = true
                )
            }
            if (top.isNotEmpty()) {
                Spacer(Modifier.height(Space.lg))
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = Color.White.copy(alpha = 0.06f)
                )
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TOP MEMORY",
                        style = PvpsType.eyebrow,
                        color = TextFaint
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${snapshot.cpuCount} CORES · ${snapshot.cpuPercent}% LOAD",
                        style = PvpsType.monoBody,
                        color = if (snapshot.cpuPercent >= 85) Danger else TextFaint
                    )
                }
                top.forEachIndexed { index, process ->
                    TopRamRow(rank = index + 1, process = process)
                }
            }
        }
    }
}

@Composable
private fun MemoryStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color = TextPrimary,
    mono: Boolean = false
) {
    Column(modifier = modifier) {
        Text(
            label,
            style = PvpsType.eyebrow,
            color = TextFaint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(Space.xxs))
        Text(
            value,
            style = if (mono) PvpsType.monoStrong else PvpsType.monoBody,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopRamRow(rank: Int, process: SystemRepository.Process) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rank.toString(),
            modifier = Modifier.width(18.dp),
            style = PvpsType.monoBody,
            color = if (rank == 1) Accent else TextFaint
        )
        Text(
            process.command,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.width(Space.sm))
        Text(
            formatMemory(process.rssKb),
            style = PvpsType.monoBody,
            color = TextPrimary
        )
        Text(
            formatPercent(process.mem),
            modifier = Modifier.width(48.dp),
            style = PvpsType.monoBody,
            color = TextFaint,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ProcessRow(
    process: SystemRepository.Process,
    rank: Int?,
    cpuPeak: Double,
    memPeak: Double,
    onKill: () -> Unit
) {
    val protected = isProtectedProcess(process)
    val cpuColor = when {
        process.cpu >= 50 -> Danger
        process.cpu >= 20 -> Warning
        else -> Accent
    }
    val memColor = when {
        process.mem >= 25 -> Danger
        process.mem >= 10 -> Warning
        else -> Violet
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.gutter, end = Space.gutter, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = Space.sm)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        process.command,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontWeight = if (rank != null) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (rank != null) {
                        Spacer(Modifier.width(Space.xs))
                        Text(
                            "#$rank",
                            style = PvpsType.eyebrow,
                            color = Accent
                        )
                    }
                }
                Spacer(Modifier.height(Space.xxs))
                Text(
                    "${process.user} · ${process.args.ifBlank { process.command }}",
                    style = PvpsType.monoBody,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                process.pid.toString(),
                modifier = Modifier.width(ProcessPidWidth),
                style = PvpsType.monoBody,
                color = TextSecondary,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Column(
                modifier = Modifier.width(ProcessCpuWidth),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    formatPercent(process.cpu),
                    style = PvpsType.monoBody,
                    color = cpuColor,
                    maxLines = 1
                )
                Spacer(Modifier.height(Space.xs))
                MiniMeter(progress = (process.cpu / cpuPeak).toFloat(), color = cpuColor)
            }
            Column(
                modifier = Modifier.width(ProcessRamWidth),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    formatMemory(process.rssKb),
                    style = PvpsType.monoBody,
                    color = memColor,
                    maxLines = 1
                )
                Spacer(Modifier.height(Space.xs))
                MiniMeter(progress = (process.mem / memPeak).toFloat(), color = memColor)
            }
            Box(
                modifier = Modifier.width(ProcessKillWidth),
                contentAlignment = Alignment.Center
            ) {
                IconAction(
                    icon = Icons.Default.Delete,
                    contentDescription = if (protected) "Protected process" else "Kill PID ${process.pid}",
                    tint = if (protected) TextFaint.copy(alpha = 0.45f) else Danger,
                    enabled = !protected,
                    size = 32.dp,
                    onClick = onKill
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = Space.gutter),
            thickness = 0.5.dp,
            color = Color.White.copy(alpha = 0.05f)
        )
    }
}

@Composable
private fun MiniMeter(progress: Float, color: Color) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(Color.White.copy(alpha = 0.07f))
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(color)
            )
        }
    }
}

private fun parseSwap(output: String): SwapUsage? {
    val parts = output.trim().split(Regex("\\s+"))
    if (parts.size < 2) return null
    val total = parts[0].toLongOrNull() ?: return null
    val used = parts[1].toLongOrNull() ?: return null
    return SwapUsage(total = total, used = used)
}

private fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)

private fun formatMemory(rssKb: Long): String {
    val mb = rssKb / 1024.0
    return when {
        mb >= 1024.0 -> String.format(Locale.US, "%.2f GB", mb / 1024.0)
        mb >= 10.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.1f MB", mb)
    }
}

private fun isProtectedProcess(process: SystemRepository.Process): Boolean =
    process.pid <= 2 || process.args.trimStart().startsWith("[")

@Composable
private fun LogsDialog(title: String, load: suspend () -> String, onDismiss: () -> Unit) {
    var content by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        runCatching { load() }
            .onSuccess { content = it }
            .onFailure { error = it.message }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        shape = RoundedCornerShape(Radius.xl),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (loading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = Space.lg),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = Accent,
                            strokeWidth = 2.dp
                        )
                    }
                } else if (error != null) {
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = Danger
                    )
                } else {
                    MonospaceBox(content.orEmpty())
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Accent, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}
