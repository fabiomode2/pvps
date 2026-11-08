package dev.pvps.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.AuthType
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.data.model.OpenCodeMode
import dev.pvps.data.model.SshKey
import dev.pvps.ssh.KeyTools
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.GhostButton
import dev.pvps.ui.components.GlassIconButton
import dev.pvps.ui.components.PvpsCard
import dev.pvps.ui.components.PvpsTextField
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.StatusPill
import dev.pvps.ui.components.TinyAction
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DisplayFont
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Warning
import dev.pvps.ui.theme.pressable
import kotlinx.coroutines.launch
import java.util.UUID

/* ------------------------------------------------------------------------- */
/* Hosts                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun HostsScreen(
    viewModel: AppViewModel,
    onAddHost: () -> Unit,
    onEditHost: (String) -> Unit,
    onManageKeys: () -> Unit,
    onBack: () -> Unit
) {
    val hosts = viewModel.hosts.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val selectedId = viewModel.selectedHostId.collectAsStateValue()
    var deleteHost by remember { mutableStateOf<Host?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Hosts",
                subtitle = "${hosts.size} saved",
                onBack = onBack,
                actions = {
                    GlassIconButton(onClick = onManageKeys, size = 38.dp) {
                        Icon(
                            Icons.Default.Key,
                            contentDescription = "SSH keys",
                            tint = Accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    GlassIconButton(onClick = onAddHost, size = 38.dp) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add host",
                            tint = TextPrimary,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.block)
        ) {
            item { Spacer(Modifier.height(Space.xs)) }
            items(hosts, key = { it.id }) { host ->
                HostCard(
                    host = host,
                    state = states[host.id],
                    selected = host.id == selectedId,
                    onSelect = { viewModel.selectHost(host.id) },
                    onConnect = {
                        viewModel.selectHost(host.id)
                        viewModel.connect(host.id)
                    },
                    onDisconnect = { viewModel.disconnect(host.id) },
                    onEdit = { onEditHost(host.id) },
                    onDelete = { deleteHost = host }
                )
            }
            item {
                Spacer(Modifier.height(Space.sm))
                SignalButton(
                    text = "Add host",
                    icon = Icons.Default.Add,
                    onClick = onAddHost,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(Space.dockClearance))
            }
        }
    }

    deleteHost?.let { host ->
        ConfirmDialog(
            title = "Delete host?",
            message = "Remove ${host.name} and its saved connection settings. SSH keys are kept.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteHost(host.id) },
            onDismiss = { deleteHost = null }
        )
    }
}

@Composable
private fun HostCard(
    host: Host,
    state: ConnectionState?,
    selected: Boolean,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val connected = state?.status == ConnectionState.Status.CONNECTED
    val statusColor = when (state?.status) {
        ConnectionState.Status.CONNECTED -> Success
        ConnectionState.Status.CONNECTING -> Warning
        ConnectionState.Status.RECONNECTING -> Warning
        ConnectionState.Status.NEEDS_HOST_KEY -> Warning
        ConnectionState.Status.ERROR -> Danger
        else -> TextFaint
    }
    PvpsCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.lg),
        fill = dev.pvps.ui.theme.cardFill(
            if (selected) Accent.copy(alpha = 0.10f) else dev.pvps.ui.theme.SurfaceRaised.copy(alpha = 0.86f),
            if (selected) Accent.copy(alpha = 0.04f) else dev.pvps.ui.theme.SurfaceDark.copy(alpha = 0.92f)
        )
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(statusColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                }
                Spacer(Modifier.width(Space.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            host.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontFamily = DisplayFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (selected) {
                            Spacer(Modifier.width(Space.sm))
                            Text("ACTIVE", style = PvpsType.eyebrow, color = Accent)
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${host.sshUser}@${host.sshHost}:${host.sshPort}",
                        style = PvpsType.monoBody,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(Space.sm))
                StatusPill(
                    text = when (state?.status) {
                        ConnectionState.Status.CONNECTED -> "LIVE"
                        ConnectionState.Status.CONNECTING -> "LINKING"
                        ConnectionState.Status.RECONNECTING -> "RETRY"
                        ConnectionState.Status.NEEDS_HOST_KEY -> "VERIFY"
                        ConnectionState.Status.ERROR -> "ERROR"
                        else -> "IDLE"
                    },
                    color = statusColor
                )
            }

            Spacer(Modifier.height(Space.md))
            Text(
                when (host.openCodeMode) {
                    OpenCodeMode.SSH_TUNNEL -> "OpenCode · tunnel :${host.openCodePort}"
                    OpenCodeMode.DIRECT -> "OpenCode · direct ${host.effectiveOpenCodeHost}:${host.openCodePort}"
                },
                style = PvpsType.monoBody,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (state?.status == ConnectionState.Status.ERROR) {
                Spacer(Modifier.height(Space.md))
                ErrorBanner(state.message)
            }
            if (state?.status == ConnectionState.Status.NEEDS_HOST_KEY) {
                Spacer(Modifier.height(Space.md))
                Text(
                    "Host key pending · ${state.hostKeySha256}",
                    style = PvpsType.monoBody,
                    color = Warning,
                    maxLines = 2
                )
            }

            Spacer(Modifier.height(Space.lg))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!selected) {
                    TinyAction("Use") { onSelect() }
                    Spacer(Modifier.width(Space.sm))
                }
                if (connected) {
                    TinyAction("Disconnect") { onDisconnect() }
                    Spacer(Modifier.weight(1f))
                } else {
                    SignalButton(
                        text = "Connect",
                        icon = Icons.Default.Link,
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(Space.sm))
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Host editor                                                                */
/* ------------------------------------------------------------------------- */

@Composable
fun HostEditScreen(
    viewModel: AppViewModel,
    hostId: String?,
    onBack: () -> Unit
) {
    val existing = remember(hostId) { viewModel.hosts.value.firstOrNull { it.id == hostId } }
    var pinnedKey by rememberSaveable(hostId) { mutableStateOf(existing?.hostKeySha256) }
    val keys = viewModel.keys.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val state = hostId?.let { states[it] }
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var sshHost by rememberSaveable { mutableStateOf(existing?.sshHost.orEmpty()) }
    var sshPort by rememberSaveable { mutableStateOf((existing?.sshPort ?: 22).toString()) }
    var sshUser by rememberSaveable { mutableStateOf(existing?.sshUser ?: "ubuntu") }
    var authType by rememberSaveable { mutableStateOf(existing?.authType ?: AuthType.KEY) }
    var keyId by rememberSaveable { mutableStateOf(existing?.keyId) }
    // Secrets deliberately use plain remember instead of rememberSaveable:
    // saved instance state lives outside the Keystore-encrypted vault. Activity
    // recreation or process death therefore loses in-progress secret input by
    // design; ordinary recomposition still preserves it.
    var password by remember { mutableStateOf(existing?.password.orEmpty()) }
    var showPassword by remember { mutableStateOf(false) }
    var openCodeMode by rememberSaveable { mutableStateOf(existing?.openCodeMode ?: OpenCodeMode.SSH_TUNNEL) }
    var openCodeHost by rememberSaveable { mutableStateOf(existing?.openCodeHost.orEmpty()) }
    var openCodePort by rememberSaveable { mutableStateOf((existing?.openCodePort ?: 49374).toString()) }
    var openCodeUser by rememberSaveable { mutableStateOf(existing?.openCodeUsername ?: "opencode") }
    var openCodePassword by remember { mutableStateOf(existing?.openCodePassword.orEmpty()) }
    var openCodeDirectory by rememberSaveable { mutableStateOf(existing?.openCodeDirectory.orEmpty()) }
    var tags by rememberSaveable { mutableStateOf(existing?.tags?.joinToString(", ").orEmpty()) }
    var showKeyMenu by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showGenerateKey by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun save() {
        val port = sshPort.toIntOrNull()
        val ocPort = openCodePort.toIntOrNull()
        when {
            name.isBlank() -> error = "Name is required"
            sshHost.isBlank() -> error = "SSH host is required"
            sshUser.isBlank() -> error = "SSH user is required"
            port == null || port !in 1..65535 -> error = "Invalid SSH port"
            ocPort == null || ocPort !in 1..65535 -> error = "Invalid OpenCode port"
            authType == AuthType.KEY && keyId == null -> error = "Select or create an SSH key"
            authType == AuthType.KEY && keys.none { it.id == keyId } -> error = "Selected key no longer exists"
            authType == AuthType.PASSWORD && password.isBlank() -> error = "SSH password is required"
            else -> {
                val base = existing ?: Host(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    sshHost = sshHost
                )
                val updated = base.copy(
                    name = name.trim(),
                    sshHost = sshHost.trim(),
                    sshPort = port,
                    sshUser = sshUser.trim(),
                    authType = authType,
                    keyId = if (authType == AuthType.KEY) keyId else null,
                    password = if (authType == AuthType.PASSWORD) password else "",
                    openCodeMode = openCodeMode,
                    openCodeHost = openCodeHost.trim(),
                    openCodePort = ocPort,
                    openCodeUsername = openCodeUser.trim().ifBlank { "opencode" },
                    openCodePassword = openCodePassword,
                    openCodeDirectory = openCodeDirectory.trim(),
                    tags = tags.split(',').map { it.trim() }.filter { it.isNotBlank() }
                )
                viewModel.saveHost(updated)
                onBack()
            }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = if (existing == null) "New host" else "Edit host",
                subtitle = existing?.let { it.sshUser + "@" + it.sshHost },
                onBack = onBack,
                actions = {
                    TinyAction("Save") { save() }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Space.gutter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
                    Spacer(Modifier.height(Space.xs))
                    if (error != null) {
                        ErrorBanner(error!!) { error = null }
                        Spacer(Modifier.height(Space.md))
                    }
                    if (state?.status == ConnectionState.Status.NEEDS_HOST_KEY) {
                        ErrorBanner("Unknown host key: ${state.hostKeySha256}. Trust it from the host card before connecting.")
                        Spacer(Modifier.height(Space.md))
                    }

                    FormGroup(title = "SSH") {
                        Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            PvpsTextField(value = name, onValueChange = { name = it }, label = "Name", placeholder = "my vps")
                            PvpsTextField(
                                value = sshHost,
                                onValueChange = { sshHost = it },
                                label = "Host",
                                placeholder = "203.0.113.10",
                                singleLine = true
                            )
                            Text(
                                "Use the public IPv4 printed by install.sh. A Tailscale 100.x address only works when this phone is on the same tailnet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextFaint
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                PvpsTextField(
                                    value = sshPort,
                                    onValueChange = { sshPort = it.filter(Char::isDigit) },
                                    label = "Port",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                PvpsTextField(
                                    value = sshUser,
                                    onValueChange = { sshUser = it },
                                    label = "User",
                                    modifier = Modifier.weight(2f)
                                )
                            }
                            SegmentedChoices(
                                options = listOf("SSH key" to (authType == AuthType.KEY), "Password" to (authType == AuthType.PASSWORD)),
                                onSelect = { index ->
                                    authType = if (index == 0) AuthType.KEY else AuthType.PASSWORD
                                }
                            )
                            if (authType == AuthType.KEY) {
                                KeySelector(
                                    label = keys.firstOrNull { it.id == keyId }?.let { "${it.name} · ${it.algorithm}" } ?: "Select a key",
                                    expanded = showKeyMenu,
                                    onExpand = { showKeyMenu = true },
                                    onDismiss = { showKeyMenu = false },
                                    keys = keys,
                                    onPick = { keyId = it.id; showKeyMenu = false },
                                    onGenerate = { showKeyMenu = false; showGenerateKey = true }
                                )
                            } else {
                                PvpsTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "SSH password",
                                    isPassword = !showPassword,
                                    trailingIcon = {
                                        IconButton(onClick = { showPassword = !showPassword }) {
                                            Icon(
                                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = if (showPassword) "Hide password" else "Show password",
                                                tint = TextFaint
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(Space.section))

                    FormGroup(title = "OpenCode v2") {
                        Column(modifier = Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                            SegmentedChoices(
                                options = listOf(
                                    "SSH tunnel" to (openCodeMode == OpenCodeMode.SSH_TUNNEL),
                                    "Direct" to (openCodeMode == OpenCodeMode.DIRECT)
                                ),
                                onSelect = { index ->
                                    openCodeMode = if (index == 0) OpenCodeMode.SSH_TUNNEL else OpenCodeMode.DIRECT
                                }
                            )
                            if (openCodeMode == OpenCodeMode.DIRECT) {
                                PvpsTextField(
                                    value = openCodeHost,
                                    onValueChange = { openCodeHost = it },
                                    label = "OpenCode host (blank = SSH host)"
                                )
                                Text(
                                    "Direct mode uses plain HTTP. Use it only on a trusted private network or Tailscale.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Warning
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                                PvpsTextField(
                                    value = openCodePort,
                                    onValueChange = { openCodePort = it.filter(Char::isDigit) },
                                    label = "OpenCode port",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                PvpsTextField(
                                    value = openCodeUser,
                                    onValueChange = { openCodeUser = it },
                                    label = "Username",
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            PvpsTextField(
                                value = openCodePassword,
                                onValueChange = { openCodePassword = it },
                                label = "OpenCode password",
                                isPassword = true
                            )
                            PvpsTextField(
                                value = openCodeDirectory,
                                onValueChange = { openCodeDirectory = it },
                                label = "Working directory (optional)",
                                placeholder = "/home/ubuntu"
                            )
                            PvpsTextField(
                                value = tags,
                                onValueChange = { tags = it },
                                label = "Tags (comma separated)"
                            )
                        }
                    }

                    if (existing != null) {
                        Spacer(Modifier.height(Space.section))
                        FormGroup(title = "Host key") {
                            Column(modifier = Modifier.padding(Space.lg)) {
                                Text(
                                    pinnedKey ?: "Not pinned yet",
                                    style = PvpsType.monoBody,
                                    color = if (pinnedKey == null) TextFaint else TextSecondary
                                )
                                if (pinnedKey != null) {
                                    Spacer(Modifier.height(Space.sm))
                                    Text(
                                        "Forget pinned host key",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Danger,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(Radius.sm))
                                            .pressable {
                                                viewModel.clearHostKey(existing.id)
                                                pinnedKey = null
                                            }
                                            .padding(vertical = Space.sm)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.dockClearance))
                }
            }
        }
    }

    if (showGenerateKey) {
        GenerateKeyDialog(
            onDismiss = { showGenerateKey = false },
            onGenerate = { keyName, passphrase, rsa ->
                scope.launch {
                    val key = viewModel.generateKey(keyName, passphrase, rsa)
                    keyId = key.id
                    showGenerateKey = false
                }
            }
        )
    }
}

@Composable
private fun FormGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = PvpsType.eyebrow,
            color = TextFaint,
            modifier = Modifier.padding(start = Space.xs, bottom = Space.sm)
        )
        PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
            Column { content() }
        }
    }
}

@Composable
private fun SegmentedChoices(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.pill))
            .background(Color.White.copy(alpha = 0.04f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { index, (label, selected) ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) Accent else TextSecondary,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (selected) Accent.copy(alpha = 0.13f) else Color.Transparent)
                    .pressable { onSelect(index) }
                    .padding(vertical = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun KeySelector(
    label: String,
    expanded: Boolean,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    keys: List<SshKey>,
    onPick: (SshKey) -> Unit,
    onGenerate: () -> Unit
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(Color.White.copy(alpha = 0.045f))
                .pressable(onClick = onExpand)
                .padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Key, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(Space.md))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(17.dp)
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            keys.forEach { key ->
                DropdownMenuItem(
                    text = { Text("${key.name} · ${key.algorithm}") },
                    onClick = { onPick(key) }
                )
            }
            DropdownMenuItem(
                text = { Text("Generate new key…", color = Accent) },
                onClick = onGenerate
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Keys                                                                       */
/* ------------------------------------------------------------------------- */

@Composable
fun KeysScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val keys = viewModel.keys.collectAsStateValue()
    var showGenerate by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showPublic by remember { mutableStateOf<SshKey?>(null) }
    var deleteKey by remember { mutableStateOf<SshKey?>(null) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "SSH keys",
                subtitle = "${keys.size} keys",
                onBack = onBack,
                actions = { TinyAction("New") { showGenerate = true } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.block)
        ) {
            item { Spacer(Modifier.height(Space.xs)) }
            items(keys, key = { it.id }) { key ->
                PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(Radius.xs))
                                    .background(Accent.copy(alpha = 0.11f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    tint = Accent,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            Spacer(Modifier.width(Space.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    key.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(key.algorithm, style = PvpsType.monoBody, color = TextFaint)
                            }
                            IconButton(onClick = { showPublic = key }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Public key",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                            IconButton(onClick = { deleteKey = key }, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = TextSecondary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                        if (key.publicKey.isNotBlank()) {
                            Spacer(Modifier.height(Space.md))
                            Text(
                                key.publicKey.take(96) + if (key.publicKey.length > 96) "…" else "",
                                style = PvpsType.monoBody,
                                color = TextSecondary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(Space.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    SignalButton("Generate key", onClick = { showGenerate = true }, modifier = Modifier.weight(1f))
                    GhostButton("Import", onClick = { showImport = true })
                }
            }
            item { Spacer(Modifier.height(Space.dockClearance)) }
        }
    }

    if (showGenerate) {
        GenerateKeyDialog(
            onDismiss = { showGenerate = false },
            onGenerate = { name, passphrase, rsa ->
                scope.launch {
                    viewModel.generateKey(name, passphrase, rsa)
                    showGenerate = false
                }
            }
        )
    }
    if (showImport) {
        ImportKeyDialog(
            onDismiss = { showImport = false },
            onImport = { name, privateKey, passphrase, publicKey ->
                val validation = KeyTools.validate(privateKey, passphrase)
                if (validation.isSuccess) {
                    viewModel.saveKey(
                        SshKey(
                            name = name,
                            privateKey = privateKey,
                            publicKey = publicKey,
                            passphrase = passphrase,
                            algorithm = "imported"
                        )
                    )
                    showImport = false
                }
            }
        )
    }
    showPublic?.let { key ->
        AlertDialog(
            onDismissRequest = { showPublic = null },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(Radius.xl),
            title = { Text(key.name, style = MaterialTheme.typography.headlineSmall, color = TextPrimary) },
            text = {
                Column {
                    Text("PUBLIC KEY", style = PvpsType.eyebrow, color = TextFaint)
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        key.publicKey.ifBlank { "No public key stored" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(key.publicKey))
                    showPublic = null
                }) { Text("Copy", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showPublic = null }) { Text("Close", color = TextSecondary) } }
        )
    }
    deleteKey?.let { key ->
        ConfirmDialog(
            title = "Delete key?",
            message = "Hosts using ${key.name} will need another key.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { viewModel.deleteKey(key.id) },
            onDismiss = { deleteKey = null }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Dialog helpers                                                             */
/* ------------------------------------------------------------------------- */

@Composable
fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, String, Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("phone") }
    // Passphrase is a secret and must not enter Android saved instance state.
    var passphrase by remember { mutableStateOf("") }
    var rsa by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        shape = RoundedCornerShape(Radius.xl),
        title = { Text("Generate SSH key", style = MaterialTheme.typography.headlineSmall, color = TextPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                PvpsTextField(value = name, onValueChange = { name = it }, label = "Name")
                PvpsTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = "Passphrase (recommended)",
                    isPassword = true
                )
                SegmentedChoices(
                    options = listOf("Ed25519" to !rsa, "RSA 4096" to rsa),
                    onSelect = { index -> rsa = index == 1 }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(name, passphrase, rsa) }) { Text("Generate", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
fun ImportKeyDialog(
    onDismiss: () -> Unit,
    onImport: (String, String, String, String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("imported") }
    // Private-key material and its passphrase stay out of saved instance state;
    // activity recreation or process death intentionally clears them and the
    // import must be retried rather than restored from an OS-managed bundle.
    var privateKey by remember { mutableStateOf("") }
    var publicKey by rememberSaveable { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        shape = RoundedCornerShape(Radius.xl),
        title = { Text("Import private key", style = MaterialTheme.typography.headlineSmall, color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Space.md)
            ) {
                if (error != null) ErrorBanner(error!!)
                PvpsTextField(value = name, onValueChange = { name = it }, label = "Name")
                PvpsTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = "Private key (OpenSSH/PEM)",
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8
                )
                PvpsTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    label = "Public key (optional)",
                    singleLine = false,
                    minLines = 2,
                    maxLines = 4
                )
                PvpsTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = "Passphrase",
                    isPassword = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (privateKey.isBlank()) {
                    error = "Private key is required"
                } else if (KeyTools.validate(privateKey, passphrase).isFailure) {
                    error = "Could not parse that private key"
                } else {
                    onImport(name, privateKey, passphrase, publicKey)
                }
            }) { Text("Import", color = Accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
