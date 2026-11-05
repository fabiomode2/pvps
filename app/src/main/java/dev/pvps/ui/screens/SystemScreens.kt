package dev.pvps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pvps.data.model.ConnectionState
import dev.pvps.system.SystemRepository
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.StatusDot
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.OnAccent
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
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
                    IconButton(onClick = { refresh++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Accent)
                    }
                }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            EmptyState(
                title = "SSH not connected",
                message = "Connect to the host from the dashboard first.",
                actionLabel = "Connect",
                onAction = { host?.let { viewModel.connect(it.id) } }
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
            item { Spacer(Modifier.height(4.dp)) }
            if (error != null) item { ErrorBanner(error!!) { error = null } }
            if (loading) item { CircularProgressIndicator(color = Accent, modifier = Modifier.padding(16.dp)) }
            if (!loading && containers.isEmpty()) {
                item { EmptyState("No containers", "Docker returned an empty list.") }
            }
            items(containers, key = { it.id }) { container ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(if (container.running) Success else TextSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                container.name,
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(container.state, style = MaterialTheme.typography.labelSmall, color = if (container.running) Success else TextSecondary)
                        }
                        Text(
                            container.image,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (container.ports.isNotBlank()) {
                            Text(container.ports, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 2)
                        }
                        Text(container.status, style = MaterialTheme.typography.labelSmall, color = TextSecondary, maxLines = 2)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (container.running) {
                                IconButton(onClick = {
                                    confirmAction = Triple(container, "restart", "Restart ${container.name}?")
                                }) { Icon(Icons.Default.RestartAlt, contentDescription = "Restart", tint = Warning) }
                                IconButton(onClick = {
                                    confirmAction = Triple(container, "stop", "Stop ${container.name}?")
                                }) { Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Danger) }
                            } else {
                                IconButton(onClick = {
                                    scope.launch {
                                        runCatching { viewModel.system.containerAction(host.id, container.name, "start") }
                                            .onFailure { error = it.message }
                                        refresh++
                                    }
                                }) { Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Success) }
                            }
                            IconButton(onClick = { logsTarget = container }) {
                                Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = Accent)
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
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
                    IconButton(onClick = { refresh++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Accent)
                    }
                }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            EmptyState(
                title = "SSH not connected",
                message = "Connect to the host from the dashboard first.",
                actionLabel = "Connect",
                onAction = { host?.let { viewModel.connect(it.id) } }
            )
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Filter services") },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(selected = filter == "all", onClick = { filter = "all" }, label = { Text("All") })
                FilterChip(selected = filter == "running", onClick = { filter = "running" }, label = { Text("Running") })
                FilterChip(selected = filter == "failed", onClick = { filter = "failed" }, label = { Text("Failed") })
            }
            if (error != null) ErrorBanner(error!!) { error = null }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(filtered, key = { it.unit }) { service ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(
                                    when {
                                        service.isFailed -> Danger
                                        service.isRunning -> Success
                                        else -> TextSecondary
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    service.unit,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${service.active}/${service.sub}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (service.isFailed) Danger else TextSecondary
                                )
                            }
                            Text(
                                service.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (service.isRunning) {
                                    IconButton(onClick = {
                                        confirmAction = Triple(service, "restart", "Restart ${service.unit}?")
                                    }) { Icon(Icons.Default.RestartAlt, contentDescription = "Restart", tint = Warning) }
                                    IconButton(onClick = {
                                        confirmAction = Triple(service, "stop", "Stop ${service.unit}?")
                                    }) { Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Danger) }
                                } else {
                                    IconButton(onClick = {
                                        scope.launch {
                                            runCatching { viewModel.system.serviceAction(host.id, service.unit, "start") }
                                                .onFailure { error = it.message }
                                            refresh++
                                        }
                                    }) { Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = Success) }
                                }
                                IconButton(onClick = { logsTarget = service }) {
                                    Icon(Icons.Default.Terminal, contentDescription = "Logs", tint = Accent)
                                }
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(80.dp)) }
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
                    IconButton(onClick = { refresh++ }, enabled = !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Accent)
                    }
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
                        IconButton(onClick = { search = "" }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear filter",
                                tint = TextFaint,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else {
                    null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
                shape = RoundedCornerShape(13.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Accent.copy(alpha = 0.72f),
                    unfocusedBorderColor = OutlineDark,
                    cursorColor = Accent,
                    focusedContainerColor = SurfaceRaised.copy(alpha = 0.38f),
                    unfocusedContainerColor = SurfaceRaised.copy(alpha = 0.22f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                )
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProcessSortControl(selected = sort, onSelect = { sort = it })
                Spacer(Modifier.weight(1f))
                Text(
                    "${sorted.size} shown",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint
                )
                Spacer(Modifier.width(8.dp))
                AutoRefreshChip(
                    enabled = autoRefresh,
                    onClick = { autoRefresh = !autoRefresh }
                )
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Accent,
                    trackColor = OutlineDark
                )
            }
            if (error != null && processes.isNotEmpty()) ErrorBanner(error!!) { error = null }
            ProcessTableHeader()
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 88.dp)
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
            shape = RoundedCornerShape(24.dp),
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "CPU ${formatPercent(process.cpu)} · ${formatMemory(process.rssKb)} · ${process.elapsed}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(10.dp))
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
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { forceKill = !forceKill }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = forceKill, onCheckedChange = { forceKill = it })
                            Spacer(Modifier.width(4.dp))
                            Column {
                                Text("Force kill", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                                Text(
                                    "SIGKILL · cannot be caught",
                                    style = MaterialTheme.typography.labelSmall,
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
            .clip(RoundedCornerShape(10.dp))
            .background(OutlineDark.copy(alpha = 0.35f))
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProcessSort.values().forEach { option ->
            val active = option == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) Accent else Color.Transparent)
                    .selectable(
                        selected = active,
                        onClick = { onSelect(option) },
                        role = Role.Tab
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) OnAccent else TextSecondary,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun AutoRefreshChip(enabled: Boolean, onClick: () -> Unit) {
    val tint = if (enabled) Accent else TextFaint
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        color = if (enabled) Accent.copy(alpha = 0.12f) else SurfaceRaised.copy(alpha = 0.26f),
        shape = RoundedCornerShape(9.dp),
        border = BorderStroke(
            1.dp,
            if (enabled) Accent.copy(alpha = 0.35f) else OutlineDark.copy(alpha = 0.72f)
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            StatusDot(if (enabled) Success else TextFaint, 5)
            Text(
                "AUTO 5S",
                style = MaterialTheme.typography.labelSmall,
                color = tint,
                fontWeight = if (enabled) FontWeight.SemiBold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ProcessTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 5.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "PROCESS",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            letterSpacing = 0.7.sp
        )
        Text(
            "PID",
            modifier = Modifier.width(ProcessPidWidth),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            textAlign = TextAlign.End
        )
        Text(
            "CPU",
            modifier = Modifier.width(ProcessCpuWidth),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            textAlign = TextAlign.End
        )
        Text(
            "RAM",
            modifier = Modifier.width(ProcessRamWidth),
            style = MaterialTheme.typography.labelSmall,
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
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(26.dp),
            color = Accent,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Reading process table…",
            style = MaterialTheme.typography.labelMedium,
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
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = SurfaceRaised.copy(alpha = 0.44f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "MEMORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            formatBytes(snapshot.memUsed),
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary
                        )
                        Text(
                            " / ${formatBytes(snapshot.memTotal)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextFaint,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "$percent%",
                        style = MaterialTheme.typography.displaySmall,
                        color = color
                    )
                    Text(
                        "USED",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(50)),
                color = color,
                trackColor = OutlineDark
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MemoryStat(
                    label = "AVAILABLE",
                    value = formatBytes(snapshot.memAvailable),
                    modifier = Modifier.weight(1f),
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
                    modifier = Modifier.weight(1.2f),
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
                    modifier = Modifier.weight(1.8f),
                    color = TextPrimary,
                    mono = true
                )
            }
            if (top.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = OutlineDark.copy(alpha = 0.55f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "TOP MEMORY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        letterSpacing = 0.7.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "${snapshot.cpuCount} CORES · ${snapshot.cpuPercent}% LOAD",
                        style = MaterialTheme.typography.labelSmall,
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
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            maxLines = 1,
            letterSpacing = 0.5.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            fontFamily = if (mono) FontFamily.Monospace else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TopRamRow(rank: Int, process: SystemRepository.Process) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rank.toString(),
            modifier = Modifier.width(16.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (rank == 1) Accent else TextFaint,
            fontFamily = FontFamily.Monospace
        )
        Text(
            process.command,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            formatMemory(process.rssKb),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary,
            fontFamily = FontFamily.Monospace
        )
        Text(
            formatPercent(process.mem),
            modifier = Modifier.width(48.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            fontFamily = FontFamily.Monospace,
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
    val rowTint = when (rank) {
        1 -> Accent.copy(alpha = 0.055f)
        2 -> Accent.copy(alpha = 0.035f)
        3 -> Accent.copy(alpha = 0.02f)
        else -> Color.Transparent
    }
    val stripe = when (rank) {
        1 -> Accent.copy(alpha = 0.70f)
        2 -> Accent.copy(alpha = 0.46f)
        3 -> Accent.copy(alpha = 0.28f)
        else -> Color.Transparent
    }
    Column(modifier = Modifier.fillMaxWidth().background(rowTint)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .drawBehind {
                    if (rank != null) {
                        drawRect(color = stripe, size = Size(2.dp.toPx(), size.height))
                    }
                }
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(vertical = 4.dp)) {
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
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "#$rank",
                            style = MaterialTheme.typography.labelSmall,
                            color = Accent,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Text(
                    "${process.user} · ${process.args.ifBlank { process.command }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
            }
            Text(
                process.pid.toString(),
                modifier = Modifier.width(ProcessPidWidth),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.End,
                maxLines = 1
            )
            Column(
                modifier = Modifier.width(ProcessCpuWidth),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    formatPercent(process.cpu),
                    style = MaterialTheme.typography.labelSmall,
                    color = cpuColor,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                MiniMeter(progress = (process.cpu / cpuPeak).toFloat(), color = cpuColor)
            }
            Column(
                modifier = Modifier.width(ProcessRamWidth),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    formatMemory(process.rssKb),
                    style = MaterialTheme.typography.labelSmall,
                    color = memColor,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1
                )
                Spacer(Modifier.height(3.dp))
                MiniMeter(progress = (process.mem / memPeak).toFloat(), color = memColor)
            }
            IconButton(
                onClick = onKill,
                enabled = !protected,
                modifier = Modifier.size(ProcessKillWidth)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = if (protected) "Protected process" else "Kill PID ${process.pid}",
                    tint = if (protected) TextFaint.copy(alpha = 0.45f) else Danger,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = OutlineDark.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun MiniMeter(progress: Float, color: Color) {
    val fraction = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .clip(RoundedCornerShape(50))
            .background(OutlineDark.copy(alpha = 0.55f))
    ) {
        if (fraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
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
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (loading) {
                    CircularProgressIndicator(color = Accent)
                } else if (error != null) {
                    Text(error!!, color = Danger)
                } else {
                    MonospaceBox(content.orEmpty())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
