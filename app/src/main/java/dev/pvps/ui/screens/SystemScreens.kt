package dev.pvps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
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
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
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
    NAME("Name")
}

@Composable
fun ProcessesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connected = host != null && states[host.id]?.status == ConnectionState.Status.CONNECTED
    var processes by remember { mutableStateOf<List<SystemRepository.Process>>(emptyList()) }
    var snapshot by remember { mutableStateOf<SystemRepository.Snapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var search by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(ProcessSort.RAM) }
    var killTarget by remember { mutableStateOf<SystemRepository.Process?>(null) }
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
        loading = false
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
        }
    )
    val top = processes.maxByOrNull { it.mem }
    val maxMem = sorted.maxOfOrNull { it.mem }?.coerceAtLeast(1.0) ?: 1.0

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Processes",
                subtitle = host?.let {
                    "${it.name} · ${processes.size} processes${if (loading) " · refreshing" else ""}"
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
            EmptyState(
                title = "SSH not connected",
                message = "Connect to the host from the dashboard first.",
                actionLabel = "Connect",
                onAction = { host?.let { viewModel.connect(it.id) } }
            )
            return@Scaffold
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            snapshot?.let { snap ->
                ProcessMemoryCard(snapshot = snap, top = top)
            }
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                placeholder = {
                    Text("Filter command, args or user", color = TextFaint)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (search.isBlank()) "TOP BY" else "MATCHES",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint
                )
                ProcessSort.values().forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { sort = option },
                        label = {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Accent,
                    trackColor = OutlineDark
                )
            }
            if (error != null) ErrorBanner(error!!) { error = null }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                if (sorted.isEmpty()) {
                    item {
                        Text(
                            if (loading) "Reading processes…" else "No processes match that filter.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
                items(sorted, key = { it.pid }) { process ->
                    ProcessRow(
                        process = process,
                        maxMem = maxMem,
                        sort = sort,
                        onKill = {
                            killTarget = process
                            forceKill = false
                        }
                    )
                }
            }
        }
    }

    killTarget?.let { process ->
        ConfirmDialog(
            title = "Kill ${process.command}?",
            message = "PID ${process.pid} (${process.user}). ${if (forceKill) "SIGKILL cannot be caught." else "SIGTERM lets the process shut down cleanly."}",
            confirmLabel = if (forceKill) "SIGKILL" else "SIGTERM",
            destructive = true,
            onConfirm = {
                scope.launch {
                    runCatching { viewModel.system.killProcess(host!!.id, process.pid, forceKill) }
                        .onFailure { error = it.message }
                    refresh++
                }
            },
            onDismiss = { killTarget = null }
        )
    }
}

@Composable
private fun ProcessMemoryCard(
    snapshot: SystemRepository.Snapshot,
    top: SystemRepository.Process?
) {
    val percent = snapshot.memPercent
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("MEMORY", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    Text(
                        "${formatBytes(snapshot.memUsed)} used",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary
                    )
                }
                Text(
                    "$percent%",
                    style = MaterialTheme.typography.displaySmall,
                    color = color
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                color = color,
                trackColor = OutlineDark
            )
            Spacer(Modifier.height(7.dp))
            Row {
                Text(
                    "${formatBytes(snapshot.memAvailable)} available · ${snapshot.memTotal / (1024 * 1024 * 1024)} GB total",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "load ${snapshot.cpuPercent}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (snapshot.cpuPercent > 85) Danger else TextSecondary
                )
            }
            top?.let { process ->
                Spacer(Modifier.height(5.dp))
                Text(
                    "Top RAM: ${process.command} · ${formatBytes(process.rssKb * 1024)} · ${"%.1f".format(process.mem)}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Violet,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProcessRow(
    process: SystemRepository.Process,
    maxMem: Double,
    sort: ProcessSort,
    onKill: () -> Unit
) {
    val cpuColor = when {
        process.cpu >= 50 -> Danger
        process.cpu >= 20 -> Warning
        else -> TextSecondary
    }
    val memColor = when {
        process.mem >= 25 -> Danger
        process.mem >= 10 -> Warning
        else -> TextSecondary
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        process.command,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "PID ${process.pid}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        process.user,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        maxLines = 1
                    )
                }
                Text(
                    process.args.ifBlank { process.command },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = FontFamily.Monospace
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "CPU ${"%.1f".format(process.cpu)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = cpuColor
                    )
                    Text(
                        "RAM ${"%.1f".format(process.mem)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = memColor
                    )
                    Text(
                        formatBytes(process.rssKb * 1024),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint
                    )
                    Text(
                        process.elapsed,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint
                    )
                }
            }
            IconButton(onClick = onKill) {
                Icon(Icons.Default.Delete, contentDescription = "Kill", tint = Danger, modifier = Modifier.size(19.dp))
            }
        }
        if (sort == ProcessSort.RAM && process.mem > 0) {
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { (process.mem / maxMem).toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)),
                color = memColor,
                trackColor = OutlineDark.copy(alpha = 0.5f)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 7.dp),
            thickness = 0.5.dp,
            color = OutlineDark.copy(alpha = 0.45f)
        )
    }
}

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
