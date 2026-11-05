package dev.pvps.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.opencode.AgentInfo
import dev.pvps.opencode.FileAttachment
import dev.pvps.opencode.ModelInfo
import dev.pvps.opencode.OpenCodeRepository
import dev.pvps.opencode.PermissionRequest
import dev.pvps.opencode.SessionInfo
import dev.pvps.opencode.ToolContent
import dev.pvps.opencode.ToolPart
import dev.pvps.opencode.UiMessage
import dev.pvps.opencode.UiPart
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.components.relativeTime
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.pvps.ui.components.TinyAction
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.Void
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Warning
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onOpenTerminal: () -> Unit
) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connection = host?.let { states[it.id] }
    val chat = viewModel.openCodeState.collectAsStateValue()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var composer by remember { mutableStateOf("") }
    var showSessions by remember { mutableStateOf(false) }
    var showModels by remember { mutableStateOf(false) }
    var showAgents by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(host?.id, connection?.status) {
        if (host != null && connection?.status == ConnectionState.Status.CONNECTED) {
            viewModel.connectOpenCode(host)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.disconnectOpenCode() }
    }

    LaunchedEffect(chat.messages.size, chat.messages.lastOrNull()?.text?.length) {
        if (chat.messages.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(chat.messages.lastIndex) }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "OpenCode",
                subtitle = host?.let {
                    if (chat.connected) "${it.name} · v${chat.serverVersion}" else "${it.name} · not connected"
                } ?: "No host",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showSessions = true }, enabled = chat.connected) {
                        Icon(Icons.Default.List, contentDescription = "Sessions", tint = Accent)
                    }
                    IconButton(onClick = {
                        scope.launch { viewModel.container.openCode.newSession() }
                    }, enabled = chat.connected && !chat.connecting) {
                        Icon(Icons.Default.Add, contentDescription = "New session", tint = Accent)
                    }
                    IconButton(onClick = onOpenTerminal) {
                        Icon(Icons.Default.Terminal, contentDescription = "Terminal", tint = TextSecondary)
                    }
                }
            )
        }
    ) { padding ->
        if (host == null) {
            EmptyState(
                title = "No host",
                message = "Add a host and connect over SSH first.",
                actionLabel = "Connect",
                onAction = { viewModel.connectSelected() }
            )
            return@Scaffold
        }
        if (connection?.status != ConnectionState.Status.CONNECTED) {
            EmptyState(
                title = "SSH not connected",
                message = "OpenCode is reached through the SSH tunnel or the Tailscale address. Connect first.",
                actionLabel = "Connect",
                onAction = { viewModel.connect(host.id) }
            )
            return@Scaffold
        }
        if (chat.error != null) {
            ErrorBanner(chat.error!!) { viewModel.container.openCode.clearError() }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (chat.connecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting to OpenCode…", color = TextSecondary)
                }
            }
            if (!chat.connected && !chat.connecting) {
                EmptyState(
                    title = "OpenCode v2",
                    message = "Set the OpenCode password in the host settings, then connect.",
                    actionLabel = "Connect OpenCode",
                    onAction = { viewModel.connectOpenCode(host) }
                )
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    if (chat.messages.isEmpty()) {
                        EmptyState(
                            title = chat.activeSession?.displayTitle ?: "No session",
                            message = if (chat.activeSession == null) "Create a session to start." else "Ask something. Tool calls, reasoning and images will render here.",
                            actionLabel = if (chat.activeSession == null) "New session" else null,
                            onAction = if (chat.activeSession == null) {
                                { scope.launch { viewModel.container.openCode.newSession() } }
                            } else null
                        )
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(chat.messages, key = { it.id }) { message ->
                                MessageBubble(
                                    message = message,
                                    expanded = expanded,
                                    onToggle = { id ->
                                        expanded = if (id in expanded) expanded - id else expanded + id
                                    },
                                    openCode = viewModel.container.openCode
                                )
                            }
                            if (chat.busy) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = Accent,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("OpenCode is working…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                }
                if (chat.permissions.isNotEmpty()) {
                    PermissionCard(
                        permission = chat.permissions.first(),
                        onReply = { decision ->
                            scope.launch {
                                viewModel.container.openCode.replyPermission(chat.permissions.first(), decision)
                            }
                        }
                    )
                }
                Composer(
                    value = composer,
                    onValueChange = { composer = it },
                    busy = chat.busy,
                    connected = chat.connected,
                    model = chat.activeSession?.model,
                    agent = chat.activeSession?.agent,
                    models = chat.models,
                    agents = chat.agents,
                    showModels = showModels,
                    showAgents = showAgents,
                    onShowModels = { showModels = true },
                    onShowAgents = { showAgents = true },
                    onDismissModels = { showModels = false },
                    onDismissAgents = { showAgents = false },
                    onSelectModel = { model ->
                        showModels = false
                        scope.launch { viewModel.container.openCode.switchModel(model) }
                    },
                    onSelectAgent = { agent ->
                        showAgents = false
                        scope.launch { viewModel.container.openCode.switchAgent(agent) }
                    },
                    onSend = {
                        val text = composer.trim()
                        if (text.isNotEmpty()) {
                            composer = ""
                            scope.launch { viewModel.container.openCode.send(text) }
                        }
                    },
                    onStop = {
                        scope.launch { viewModel.container.openCode.interrupt() }
                    }
                )
            }
        }
    }

    if (showSessions) {
        SessionDialog(
            sessions = chat.sessions,
            activeId = chat.activeSessionId,
            onSelect = { id ->
                showSessions = false
                scope.launch { viewModel.container.openCode.selectSession(id) }
            },
            onNew = {
                showSessions = false
                scope.launch { viewModel.container.openCode.newSession() }
            },
            onDismiss = { showSessions = false }
        )
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    openCode: OpenCodeRepository
) {
    when {
        message.isUser -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    modifier = Modifier.fillMaxWidth(0.90f),
                    color = Accent.copy(alpha = 0.085f),
                    shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                    border = BorderStroke(1.dp, Accent.copy(alpha = 0.16f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SelectionContainer {
                            Text(
                                message.text.ifBlank { "(empty message)" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = TextPrimary
                            )
                        }
                        message.files.forEach { file ->
                            AttachmentChip(file, openCode)
                        }
                    }
                }
            }
        }
        message.isAssistant -> {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        (message.agent ?: "assistant").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent
                    )
                    message.model?.let {
                        Text(
                            "  $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (message.cost > 0) {
                        Text(
                            "$" + "%.4f".format(message.cost),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                if (message.error != null) {
                    ErrorBanner(message.error)
                }
                message.parts.forEachIndexed { index, part ->
                    when (part) {
                        is UiPart.Text -> MarkdownText(part.text)
                        is UiPart.Reasoning -> {
                            val key = "${message.id}-reasoning-$index"
                            ReasoningBlock(
                                text = part.text,
                                expanded = key in expanded,
                                onToggle = { onToggle(key) }
                            )
                        }
                        is UiPart.Tool -> {
                            val key = "${message.id}-tool-${part.tool.id}"
                            ToolCard(
                                tool = part.tool,
                                expanded = key in expanded,
                                onToggle = { onToggle(key) },
                                openCode = openCode
                            )
                        }
                    }
                }
                if (message.parts.isEmpty() && message.text.isNotBlank()) {
                    MarkdownText(message.text)
                }
            }
        }
        else -> {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                Text(
                    message.text.ifBlank { message.type }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String, expanded: Boolean, onToggle: () -> Unit) {
    if (text.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Reasoning", style = MaterialTheme.typography.labelSmall, color = TextSecondary, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle reasoning",
                        tint = TextSecondary
                    )
                }
            }
            if (expanded) {
                SelectionContainer {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontStyle = FontStyle.Italic
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolCard(
    tool: ToolPart,
    expanded: Boolean,
    onToggle: () -> Unit,
    openCode: OpenCodeRepository
) {
    val statusColor = when (tool.status) {
        "completed", "success" -> Success
        "error", "failed" -> Danger
        "running", "streaming" -> Warning
        else -> TextSecondary
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(toolIcon(tool.name), contentDescription = null, tint = statusColor, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(tool.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(6.dp))
                Text(tool.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onToggle, modifier = Modifier.size(24.dp)) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle tool",
                        tint = TextSecondary
                    )
                }
            }
            Text(
                toolSummary(tool),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = FontFamily.Monospace
            )
            if (expanded) {
                if (tool.input.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text("Input", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    MonospaceBox(tool.input)
                }
                tool.content.forEach { content ->
                    when (content) {
                        is ToolContent.Text -> {
                            Spacer(Modifier.height(6.dp))
                            Text("Output", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            MonospaceBox(content.text.take(12_000))
                        }
                        is ToolContent.File -> {
                            Spacer(Modifier.height(6.dp))
                            if (content.mime.startsWith("image/")) {
                                RemoteMedia(uri = content.uri, openCode = openCode)
                            } else {
                                Text(
                                    "File: ${content.name ?: content.uri.take(60)} (${content.mime})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                tool.error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(file: FileAttachment, openCode: OpenCodeRepository) {
    if (file.uri.startsWith("data:image/") || file.uri.startsWith("media:")) {
        RemoteMedia(uri = file.uri, openCode = openCode)
    } else {
        Text(
            file.name ?: file.uri.take(60),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun RemoteMedia(uri: String, openCode: OpenCodeRepository) {
    val result by produceState<Pair<Boolean, ImageBitmap?>>(initialValue = false to null, uri) {
        val bytes = openCode.loadMedia(uri)
        value = true to bytes?.let { data ->
            runCatching { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }.getOrNull()
        }
    }
    if (!result.first) {
        Text("Loading image…", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    } else if (result.second != null) {
        Image(
            bitmap = result.second!!,
            contentDescription = "Image attachment",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp)
                .padding(top = 6.dp)
        )
    } else {
        Text("Image unavailable (unsupported URI)", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun PermissionCard(permission: PermissionRequest, onReply: (String) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Permission requested", style = MaterialTheme.typography.titleSmall, color = Warning)
            Text(
                permission.message ?: permission.action,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (permission.resources.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    permission.resources.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onReply("once") }) { Text("Allow once") }
                TextButton(onClick = { onReply("always") }) { Text("Always") }
                TextButton(onClick = { onReply("reject") }) { Text("Deny", color = Danger) }
            }
        }
    }
}

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    connected: Boolean,
    model: dev.pvps.opencode.ModelRef?,
    agent: String?,
    models: List<ModelInfo>,
    agents: List<AgentInfo>,
    showModels: Boolean,
    showAgents: Boolean,
    onShowModels: () -> Unit,
    onShowAgents: () -> Unit,
    onDismissModels: () -> Unit,
    onDismissAgents: () -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onSelectAgent: (AgentInfo) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Surface(color = DeepBackground.copy(alpha = 0.94f)) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TinyAction(model?.id ?: "model", onShowModels)
                    DropdownMenu(expanded = showModels, onDismissRequest = onDismissModels) {
                        models.take(30).forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.label) },
                                onClick = { onSelectModel(item) }
                            )
                        }
                    }
                }
                Box {
                    TinyAction(agent ?: "agent", onShowAgents)
                    DropdownMenu(expanded = showAgents, onDismissRequest = onDismissAgents) {
                        agents.filterNot { it.hidden }.forEach { item ->
                            DropdownMenuItem(
                                text = { Text(item.name) },
                                onClick = { onSelectAgent(item) }
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    if (busy) "WORKING" else if (connected) "READY" else "OFFLINE",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (busy) Warning else if (connected) Success else TextFaint
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask OpenCode…", color = TextFaint) },
                    maxLines = 6,
                    enabled = connected,
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent.copy(alpha = 0.72f),
                        unfocusedBorderColor = OutlineDark,
                        focusedContainerColor = SurfaceRaised.copy(alpha = 0.42f),
                        unfocusedContainerColor = SurfaceRaised.copy(alpha = 0.28f),
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                if (busy) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Danger.copy(alpha = 0.14f))
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Danger)
                    }
                } else {
                    val sendEnabled = connected && value.isNotBlank()
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (sendEnabled) Accent else OutlineDark)
                            .clickable(enabled = sendEnabled, onClick = onSend),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (sendEnabled) Void else TextFaint
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionDialog(
    sessions: List<SessionInfo>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sessions") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sessions.forEach { session ->
                    Surface(
                        color = if (session.id == activeId) Accent.copy(alpha = 0.12f) else Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        TextButton(
                            onClick = { onSelect(session.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    session.displayTitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${session.agent ?: "agent"} · ${relativeTime(session.time.updated)}" +
                                        if (session.time.busy != null) " · busy" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("New session") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun MarkdownText(text: String) {
    if (text.isBlank()) return
    val chunks = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        chunks.forEachIndexed { index, chunk ->
            if (index % 2 == 1) {
                val firstLine = chunk.lineSequence().firstOrNull().orEmpty()
                val code = if (firstLine.isNotBlank() && !firstLine.contains(' ')) {
                    chunk.substringAfter('\n', "")
                } else {
                    chunk
                }
                MonospaceBox(code.trim())
            } else {
                if (chunk.isNotBlank()) {
                    SelectionContainer {
                        Text(
                            inlineMarkdown(chunk.trim()),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(i + 2, end)) }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFF070B12))) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

private fun toolIcon(name: String): ImageVector = when {
    name.contains("shell") || name.contains("bash") || name.contains("terminal") -> Icons.Default.Terminal
    name.contains("read") || name.contains("write") || name.contains("edit") -> Icons.Default.Description
    name.contains("grep") || name.contains("glob") || name.contains("search") -> Icons.Default.Search
    name.contains("webfetch") || name.contains("fetch") || name.contains("http") -> Icons.Default.Language
    name.contains("task") || name.contains("agent") -> Icons.Default.Build
    else -> Icons.Default.Build
}

private fun toolSummary(tool: ToolPart): String {
    val input = tool.input
    val command = extractJsonString(input, "command")
        ?: extractJsonString(input, "filePath")
        ?: extractJsonString(input, "path")
        ?: extractJsonString(input, "pattern")
        ?: extractJsonString(input, "url")
        ?: extractJsonString(input, "description")
    return command?.take(220) ?: input.take(220).ifBlank { tool.name }
}

private fun extractJsonString(json: String, key: String): String? {
    val regex = Regex("\"$key\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"")
    return regex.find(json)?.groupValues?.getOrNull(1)
        ?.replace("\\n", " ")
        ?.replace("\\\"", "\"")
        ?.replace("\\\\", "\\")
}
