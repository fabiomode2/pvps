package dev.pvps.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.AuthType
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.data.model.OpenCodeMode
import dev.pvps.data.model.SshKey
import dev.pvps.ssh.KeyTools
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.StatusDot
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import dev.pvps.ui.components.GhostButton
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.StatusPill
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Warning
import kotlinx.coroutines.launch
import java.util.UUID

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
                    IconButton(onClick = onManageKeys) {
                        Icon(Icons.Default.Key, contentDescription = "SSH keys", tint = Accent)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddHost, containerColor = Accent) {
                Icon(Icons.Default.Add, contentDescription = "Add host", tint = Color(0xFF00201D))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(hosts, key = { it.id }) { host ->
                val state = states[host.id]
                HostCard(
                    host = host,
                    state = state,
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
            item { Spacer(Modifier.height(80.dp)) }
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
        ConnectionState.Status.NEEDS_HOST_KEY -> Warning
        ConnectionState.Status.ERROR -> Danger
        else -> TextFaint
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (selected) Accent.copy(alpha = 0.055f) else SurfaceRaised.copy(alpha = 0.58f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, if (selected) Accent.copy(alpha = 0.26f) else OutlineDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(statusColor.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    StatusDot(statusColor, 8)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            host.name,
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (selected) {
                            Spacer(Modifier.width(8.dp))
                            Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = Accent)
                        }
                    }
                    Text(
                        "${host.sshUser}@${host.sshHost}:${host.sshPort}",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
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
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (host.openCodeMode) {
                        OpenCodeMode.SSH_TUNNEL -> "OPENCODE · TUNNEL :${host.openCodePort}"
                        OpenCodeMode.DIRECT -> "OPENCODE · DIRECT ${host.effectiveOpenCodeHost}:${host.openCodePort}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = TextSecondary, modifier = Modifier.size(17.dp))
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary, modifier = Modifier.size(17.dp))
                }
            }
            if (state?.status == ConnectionState.Status.ERROR) {
                Spacer(Modifier.height(8.dp))
                ErrorBanner(state.message)
            }
            if (state?.status == ConnectionState.Status.NEEDS_HOST_KEY) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Host key pending: ${state.hostKeySha256}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                    maxLines = 2
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!selected) {
                    GhostButton("Use", onSelect)
                }
                if (connected) {
                    GhostButton("Disconnect", onDisconnect, icon = Icons.Default.LinkOff, color = Danger)
                } else {
                    SignalButton("Connect", onConnect, icon = Icons.Default.Link)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                subtitle = existing?.hostKeySha256?.let { "Host key $it" },
                onBack = onBack,
                actions = {
                    TextButton(onClick = { save() }) { Text("Save", color = Accent) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (error != null) ErrorBanner(error!!) { error = null }
            if (state?.status == ConnectionState.Status.NEEDS_HOST_KEY) {
                ErrorBanner("Unknown host key: ${state.hostKeySha256}. Trust it from the host card before connecting.")
            }
            Text("SSH", style = MaterialTheme.typography.titleMedium, color = Accent)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(
                value = sshHost,
                onValueChange = { sshHost = it },
                label = { Text("Host (public IP or hostname)") },
                supportingText = {
                    Text("Use the public IPv4 printed by install.sh. A Tailscale 100.x address only works when this phone is on the same tailnet.")
                },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = sshPort,
                    onValueChange = { sshPort = it.filter(Char::isDigit) },
                    label = { Text("Port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = sshUser,
                    onValueChange = { sshUser = it },
                    label = { Text("User") },
                    modifier = Modifier.weight(2f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = authType == AuthType.KEY, onClick = { authType = AuthType.KEY }, label = { Text("SSH key") })
                FilterChip(selected = authType == AuthType.PASSWORD, onClick = { authType = AuthType.PASSWORD }, label = { Text("Password") })
            }
            if (authType == AuthType.KEY) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = keys.firstOrNull { it.id == keyId }?.name ?: "Select key",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Key") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .background(Color.Transparent)
                                .clickable { showKeyMenu = true }
                        )
                        DropdownMenu(expanded = showKeyMenu, onDismissRequest = { showKeyMenu = false }) {
                            keys.forEach { key ->
                                DropdownMenuItem(
                                    text = { Text("${key.name} · ${key.algorithm}") },
                                    onClick = {
                                        keyId = key.id
                                        showKeyMenu = false
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Generate new key…", color = Accent) },
                                onClick = {
                                    showKeyMenu = false
                                    showGenerateKey = true
                                }
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("SSH password") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(4.dp))
            Text("OpenCode v2", style = MaterialTheme.typography.titleMedium, color = Accent)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = openCodeMode == OpenCodeMode.SSH_TUNNEL,
                    onClick = { openCodeMode = OpenCodeMode.SSH_TUNNEL },
                    label = { Text("SSH tunnel") }
                )
                FilterChip(
                    selected = openCodeMode == OpenCodeMode.DIRECT,
                    onClick = { openCodeMode = OpenCodeMode.DIRECT },
                    label = { Text("Direct") }
                )
            }
            if (openCodeMode == OpenCodeMode.DIRECT) {
                OutlinedTextField(
                    value = openCodeHost,
                    onValueChange = { openCodeHost = it },
                    label = { Text("OpenCode host (blank = SSH host)") },
                    supportingText = {
                        Text(
                            "Direct mode uses plain HTTP. Use it only on a trusted private network or Tailscale.",
                            color = Warning
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = openCodePort,
                    onValueChange = { openCodePort = it.filter(Char::isDigit) },
                    label = { Text("OpenCode port") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = openCodeUser,
                    onValueChange = { openCodeUser = it },
                    label = { Text("Username") },
                    modifier = Modifier.weight(1f)
                )
            }
            OutlinedTextField(
                value = openCodePassword,
                onValueChange = { openCodePassword = it },
                label = { Text("OpenCode password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = openCodeDirectory,
                onValueChange = { openCodeDirectory = it },
                label = { Text("Working directory (optional)") },
                placeholder = { Text("/home/ubuntu") },
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("Tags (comma separated)") },
                modifier = Modifier.fillMaxWidth()
            )
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Host key: ${pinnedKey ?: "not pinned yet"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                if (pinnedKey != null) {
                    TextButton(onClick = {
                        viewModel.clearHostKey(existing.id)
                        pinnedKey = null
                    }) {
                        Text("Forget pinned host key", color = Danger)
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
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
        topBar = { ScreenHeader(title = "SSH keys", subtitle = "${keys.size} keys", onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showGenerate = true }, containerColor = Accent) {
                Icon(Icons.Default.Add, contentDescription = "Generate key", tint = Color(0xFF00201D))
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(keys, key = { it.id }) { key ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Key, contentDescription = null, tint = Accent)
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(key.name, style = MaterialTheme.typography.titleMedium)
                                Text(key.algorithm, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            IconButton(onClick = { showPublic = key }) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Public key", tint = TextSecondary)
                            }
                            IconButton(onClick = { deleteKey = key }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextSecondary)
                            }
                        }
                        if (key.publicKey.isNotBlank()) {
                            Text(
                                key.publicKey.take(80) + if (key.publicKey.length > 80) "…" else "",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
            item {
                TextButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Import an existing private key")
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
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
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showPublic = null },
            title = { Text(key.name) },
            text = {
                Column {
                    Text("Public key", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        key.publicKey.ifBlank { "No public key stored" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(key.publicKey))
                    showPublic = null
                }) { Text("Copy") }
            },
            dismissButton = { TextButton(onClick = { showPublic = null }) { Text("Close") } }
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

@Composable
fun GenerateKeyDialog(
    onDismiss: () -> Unit,
    onGenerate: (String, String, Boolean) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("phone") }
    // Passphrase is a secret and must not enter Android saved instance state.
    var passphrase by remember { mutableStateOf("") }
    var rsa by rememberSaveable { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate SSH key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase (recommended)") },
                    visualTransformation = PasswordVisualTransformation()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !rsa, onClick = { rsa = false }, label = { Text("Ed25519") })
                    FilterChip(selected = rsa, onClick = { rsa = true }, label = { Text("RSA 4096") })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onGenerate(name, passphrase, rsa) }) { Text("Generate") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
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
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import private key") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (error != null) ErrorBanner(error!!)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
                OutlinedTextField(
                    value = privateKey,
                    onValueChange = { privateKey = it },
                    label = { Text("Private key (OpenSSH/PEM)") },
                    minLines = 4,
                    maxLines = 8
                )
                OutlinedTextField(
                    value = publicKey,
                    onValueChange = { publicKey = it },
                    label = { Text("Public key (optional)") },
                    minLines = 2,
                    maxLines = 4
                )
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text("Passphrase") },
                    visualTransformation = PasswordVisualTransformation()
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
            }) { Text("Import") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
