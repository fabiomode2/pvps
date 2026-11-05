package dev.pvps.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import dev.pvps.opencode.FormField
import dev.pvps.opencode.FormInfo
import dev.pvps.opencode.FormOption
import dev.pvps.opencode.ModelVariant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import dev.pvps.data.model.ConnectionState
import dev.pvps.opencode.AgentInfo
import dev.pvps.opencode.CommandInfo
import dev.pvps.opencode.FileAttachment
import dev.pvps.opencode.ModelInfo
import dev.pvps.opencode.ModelRef
import dev.pvps.opencode.OpenCodeRepository
import dev.pvps.opencode.PermissionRequest
import dev.pvps.opencode.PromptFileInput
import dev.pvps.opencode.SessionInfo
import dev.pvps.opencode.ToolContent
import dev.pvps.opencode.ToolPart
import dev.pvps.opencode.TokenUsage
import dev.pvps.opencode.UiMessage
import dev.pvps.opencode.UiPart
import dev.pvps.opencode.stringOrNull
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.InfoRow
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.StatusDot
import dev.pvps.ui.components.TinyAction
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.relativeTime
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.AccentDim
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Void
import dev.pvps.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.Locale

private data class ChatAttachment(
    val uri: Uri,
    val name: String,
    val mime: String,
    val base64: String,
    val size: Long
) {
    fun promptFile(): PromptFileInput = PromptFileInput(
        uri = "data:$mime;base64,$base64",
        name = name
    )
}

@Composable
fun ChatScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connection = host?.let { states[it.id] }
    val chat = viewModel.openCodeState.collectAsStateValue()
    val settings = viewModel.settings.collectAsStateValue()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val draftKey = chat.activeSessionId
    var composer by remember(draftKey) {
        mutableStateOf(viewModel.container.chatDrafts.get(draftKey))
    }
    var attachments by remember(draftKey) { mutableStateOf<List<ChatAttachment>>(emptyList()) }
    var attachmentError by remember(draftKey) { mutableStateOf<String?>(null) }
    var sending by remember(draftKey) { mutableStateOf(false) }
    var showSessions by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                val picked = mutableListOf<ChatAttachment>()
                val errors = mutableListOf<String>()
                uris.forEach { uri ->
                    runCatching { readChatAttachment(context, uri) }
                        .onSuccess { picked += it }
                        .onFailure { errors += (it.message ?: "Could not read attachment") }
                }
                picked to errors
            }
            val room = (MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)
            var totalBytes = attachments.sumOf { it.size }
            val added = mutableListOf<ChatAttachment>()
            for (candidate in result.first.take(room)) {
                if (totalBytes + candidate.size > MAX_TOTAL_ATTACHMENT_BYTES) break
                totalBytes += candidate.size
                added += candidate
            }
            attachmentError = when {
                result.first.size > room -> "Max $MAX_ATTACHMENTS attachments"
                added.size < result.first.size -> "Total attachment size must stay under 24 MB"
                result.second.isNotEmpty() -> result.second.first()
                else -> null
            }
            attachments = (attachments + added)
        }
    }

    LaunchedEffect(host?.id, connection?.status) {
        if (host != null && connection?.status == ConnectionState.Status.CONNECTED) {
            viewModel.connectOpenCode(host)
        }
    }

    val now by produceState(System.currentTimeMillis(), chat.busy) {
        while (chat.busy) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    val compactTurns = settings.compactToolCards
    val conversation = remember(chat.messages, compactTurns) {
        buildConversation(chat.messages, compactTurns)
    }
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            total == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= total - 2
        }
    }
    LaunchedEffect(conversation.size, chat.messages.lastOrNull()?.text?.length) {
        if (conversation.isNotEmpty() && atBottom) {
            runCatching { listState.animateScrollToItem(conversation.lastIndex) }
        }
    }

    val session = chat.activeSession
    val lastUsage = chat.messages.lastOrNull { it.isAssistant && it.tokens != null }?.tokens
    val contextUsed = lastUsage?.let { it.input + it.cache.read + it.cache.write }
    val modelInfo = session?.model?.let { ref ->
        chat.models.firstOrNull {
            it.modelID == ref.id && it.providerID == ref.providerID
        }
    }
    val contextLimit = modelInfo?.limit?.context?.takeIf { it > 0 }
        ?: modelInfo?.limit?.input?.takeIf { it > 0 }
        ?: 0L
    val contextFraction = if (contextUsed != null && contextLimit > 0) {
        (contextUsed.toDouble() / contextLimit).coerceIn(0.0, 1.0)
    } else null
    val tps = tokensPerSecond(chat.messages, now)
    val runningSubagents = chat.subagents.count { it.isRunning }
    val goMeter = chat.usage?.go?.rolling
    val actualSpend = chat.realCost ?: session?.cost?.takeIf { it > 0.0 }
    val estimatedSpend = if (actualSpend != null) {
        0.0
    } else {
        estimateSessionCost(session?.tokens, modelInfo, chat.messages)
    }
    val spentValue = when {
        actualSpend != null -> formatCost(actualSpend)
        estimatedSpend > 0.0 -> "~" + formatCost(estimatedSpend)
        goMeter != null -> "${goMeter.percent.toInt()}%"
        else -> "—"
    }
    val spentLabel = when {
        actualSpend != null -> "SPENT"
        estimatedSpend > 0.0 -> "SPENT · EST"
        else -> "GO PLAN"
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = session?.displayTitle ?: "OpenCode",
                subtitle = buildString {
                    append(host?.name ?: "No host")
                    append(" · ")
                    append(
                        when {
                            session?.parentID != null -> "subagent"
                            chat.connected -> if (chat.busy) "working" else "ready"
                            chat.connecting -> "connecting"
                            else -> "offline"
                        }
                    )
                },
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showSessions = true }, enabled = chat.connected) {
                        Icon(Icons.Default.List, contentDescription = "Sessions", tint = TextSecondary)
                    }
                    IconButton(
                        onClick = { scope.launch { viewModel.container.openCode.newSession() } },
                        enabled = chat.connected && !chat.connecting
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "New session", tint = Accent)
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
                message = "OpenCode is reached through the SSH tunnel. Connect to ${host.name} first.",
                actionLabel = "Connect",
                onAction = { viewModel.connect(host.id) }
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            if (chat.error != null) {
                ErrorBanner(chat.error!!) { viewModel.container.openCode.clearError() }
            }
            if (chat.connecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Connecting to OpenCode…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (!chat.connected && !chat.connecting) {
                EmptyState(
                    title = "OpenCode v2",
                    message = "Set the OpenCode password in the host settings, then connect.",
                    actionLabel = "Connect OpenCode",
                    onAction = { viewModel.connectOpenCode(host) }
                )
                return@Column
            }

            SessionPulse(
                session = session,
                contextFraction = contextFraction,
                contextUsed = contextUsed,
                contextLimit = contextLimit,
                spentLabel = spentLabel,
                spentValue = spentValue,
                spentDetail = buildString {
                    goMeter?.let { append("plan ").append(it.percent.toInt()).append("%") }
                    if (actualSpend == null && estimatedSpend > 0.0) {
                        if (isNotEmpty()) append(" · ")
                        append("estimated")
                    }
                }.ifBlank { null },
                tps = tps,
                subagents = chat.subagents.size,
                runningSubagents = runningSubagents,
                busy = chat.busy,
                onClick = { showStats = true }
            )

            chat.parentSession?.let { parent ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            scope.launch { viewModel.container.openCode.selectSession(parent.id) }
                        },
                    color = Violet.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Violet.copy(alpha = 0.18f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("←", color = Violet, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SUBAGENT", style = MaterialTheme.typography.labelSmall, color = Violet)
                            Text(
                                "Back to ${parent.displayTitle}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (chat.messages.isEmpty()) {
                    EmptyState(
                        title = session?.displayTitle ?: "No session",
                        message = if (session == null) {
                            "Create a session to start."
                        } else {
                            "Ask anything. Tool calls, reasoning, usage and subagents render here."
                        },
                        actionLabel = if (session == null) "New session" else null,
                        onAction = if (session == null) {
                            { scope.launch { viewModel.container.openCode.newSession() } }
                        } else null
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = 16.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(conversation, key = { it.key }) { item ->
                            val turn = item.turn
                            if (turn != null) {
                                AssistantTurnBlock(
                                    turn = turn,
                                    expanded = expanded,
                                    onToggle = { id ->
                                        expanded = if (id in expanded) expanded - id else expanded + id
                                    },
                                    openCode = viewModel.container.openCode,
                                    showReasoning = settings.showReasoning,
                                    onOpenSubagent = { childId ->
                                        scope.launch { viewModel.container.openCode.selectSession(childId) }
                                    }
                                )
                            } else {
                                item.message?.let { message ->
                                    MessageBubble(
                                        message = message,
                                        expanded = expanded,
                                        onToggle = { id ->
                                            expanded = if (id in expanded) expanded - id else expanded + id
                                        },
                                        openCode = viewModel.container.openCode,
                                        showReasoning = settings.showReasoning,
                                        compactTools = settings.compactToolCards,
                                        onOpenSubagent = { childId ->
                                            scope.launch { viewModel.container.openCode.selectSession(childId) }
                                        }
                                    )
                                }
                            }
                        }
                        val liveActivity = conversation.lastOrNull()?.turn?.any { message ->
                            message.parts.any {
                                it is UiPart.Tool && (it.tool.status == "running" || it.tool.status == "streaming")
                            }
                        } == true
                        if (chat.busy && !liveActivity) {
                            item(key = "working-indicator") { WorkingIndicator() }
                        }
                    }
                }
            }

            chat.forms.firstOrNull()?.let { form ->
                FormCard(
                    form = form,
                    onSubmit = { answer ->
                        scope.launch { viewModel.container.openCode.replyForm(form, answer) }
                    },
                    onCancel = {
                        scope.launch { viewModel.container.openCode.cancelForm(form) }
                    }
                )
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
                onValueChange = {
                    composer = it
                    viewModel.container.chatDrafts.set(draftKey, it)
                },
                busy = chat.busy,
                sending = sending,
                connected = chat.connected,
                modelLabel = modelLabel(session?.model, chat.models),
                model = modelInfo,
                models = chat.models,
                variant = session?.model?.variant,
                agent = session?.agent,
                agents = chat.agents,
                commands = chat.commands,
                queued = chat.queued,
                attachments = attachments,
                attachmentError = attachmentError,
                onAttach = { filePicker.launch(arrayOf("*/*")) },
                onRemoveAttachment = { uri ->
                    attachments = attachments.filterNot { it.uri == uri }
                    attachmentError = null
                },
                onSelectModel = { model ->
                    scope.launch { viewModel.container.openCode.switchModel(model) }
                },
                onSelectVariant = { model, variant ->
                    scope.launch { viewModel.container.openCode.switchModel(model, variant) }
                },
                onSelectAgent = { agent ->
                    scope.launch { viewModel.container.openCode.switchAgent(agent) }
                },
                onPickCommand = { command ->
                    val text = "/${command.name} "
                    composer = text
                    viewModel.container.chatDrafts.set(draftKey, text)
                },
                onCancelQueued = { id ->
                    scope.launch { viewModel.container.openCode.cancelQueued(id) }
                },
                onSendQueuedNow = { item ->
                    scope.launch { viewModel.container.openCode.sendQueuedNow(item) }
                },
                onSend = {
                    if (!sending) {
                        val raw = composer.trim()
                        val sentAttachments = attachments
                        val outgoing = sentAttachments.map { it.promptFile() }
                        val commandName = if (raw.startsWith("/")) {
                            raw.removePrefix("/").substringBefore(' ').trim()
                        } else ""
                        val knownCommand = commandName.isNotBlank() && (
                            commandName == "compact" ||
                                commandName == "init" ||
                                chat.commands.any { it.name == commandName }
                            )
                        if (knownCommand || raw.isNotEmpty() || outgoing.isNotEmpty()) {
                            sending = true
                            viewModel.viewModelScope.launch {
                                val ok = if (knownCommand) {
                                    val args = raw.removePrefix("/$commandName").trim()
                                    viewModel.container.openCode.runCommand(
                                        commandName,
                                        args,
                                        outgoing
                                    )
                                } else {
                                    viewModel.container.openCode.send(raw, outgoing)
                                }
                                sending = false
                                if (ok) {
                                    if (composer.trim() == raw) {
                                        composer = ""
                                        viewModel.container.chatDrafts.clear(draftKey)
                                    }
                                    attachments = attachments - sentAttachments.toSet()
                                    attachmentError = null
                                }
                            }
                        }
                    }
                },
                onStop = { scope.launch { viewModel.container.openCode.interrupt() } }
            )
        }
    }

    if (showSessions) {
        SessionDialog(
            sessions = chat.rootSessions,
            allSessions = chat.sessions,
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
    if (showStats) {
        StatsDialog(
            session = session,
            usage = chat.usage,
            parent = chat.parentSession,
            messages = chat.messages,
            models = chat.models,
            subagents = chat.subagents,
            contextUsed = contextUsed,
            contextLimit = contextLimit,
            contextFraction = contextFraction,
            tps = tps,
            realCost = chat.realCost,
            estimatedCost = if (actualSpend != null) 0.0 else estimatedSpend,
            onOpenSubagent = { id ->
                showStats = false
                scope.launch { viewModel.container.openCode.selectSession(id) }
            },
            onOpenParent = { parent ->
                showStats = false
                scope.launch { viewModel.container.openCode.selectSession(parent.id) }
            },
            onDismiss = { showStats = false }
        )
    }
}

@Composable
private fun SessionPulse(
    session: SessionInfo?,
    contextFraction: Double?,
    contextUsed: Long?,
    contextLimit: Long,
    spentLabel: String,
    spentValue: String,
    spentDetail: String?,
    tps: Double?,
    subagents: Int,
    runningSubagents: Int,
    busy: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulseCell(
            label = "CTX",
            value = contextFraction?.let { "${(it * 100).toInt()}%" } ?: "—",
            detail = contextUsed?.let {
                "${compactTokens(it)}/${compactTokens(contextLimit)}"
            },
            color = when {
                contextFraction == null -> TextSecondary
                contextFraction > 0.85 -> Danger
                contextFraction > 0.65 -> Warning
                else -> TextPrimary
            },
            modifier = Modifier.weight(1.05f)
        )
        PulseCell(
            label = spentLabel,
            value = spentValue,
            detail = spentDetail,
            color = TextPrimary,
            modifier = Modifier.weight(1.25f)
        )
        PulseCell(
            label = "TOK/S",
            value = tps?.let { if (it >= 100) "${it.toInt()}" else String.format(Locale.US, "%.1f", it) } ?: "—",
            detail = if (busy) "live" else null,
            color = if (busy) Warning else TextPrimary,
            modifier = Modifier.weight(1f)
        )
        PulseCell(
            label = "AGENTS",
            value = if (subagents == 0) "0" else "$runningSubagents/$subagents",
            detail = if (runningSubagents > 0) "running" else null,
            color = when {
                runningSubagents > 0 -> Warning
                subagents > 0 -> Violet
                else -> TextSecondary
            },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun PulseCell(
    label: String,
    value: String,
    detail: String?,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextFaint, maxLines = 1)
        Spacer(Modifier.height(1.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Spacer(Modifier.width(4.dp))
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 1.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Accent, strokeWidth = 1.6.dp)
        Spacer(Modifier.width(9.dp))
        Text("OpenCode is working…", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

private data class ConversationItem(
    val key: String,
    val message: UiMessage? = null,
    val turn: List<UiMessage>? = null
)

private fun buildConversation(
    messages: List<UiMessage>,
    compactTurns: Boolean
): List<ConversationItem> {
    if (!compactTurns) {
        return messages.map { ConversationItem("msg-${it.id}", message = it) }
    }
    val out = mutableListOf<ConversationItem>()
    var index = 0
    while (index < messages.size) {
        val message = messages[index]
        if (message.isAssistant) {
            val start = index
            while (index < messages.size && messages[index].isAssistant) index++
            val turn = messages.subList(start, index).toList()
            out += ConversationItem("turn-${turn.first().id}", turn = turn)
        } else {
            out += ConversationItem("msg-${message.id}", message = message)
            index++
        }
    }
    return out
}

@Composable
private fun AssistantTurnBlock(
    turn: List<UiMessage>,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    openCode: OpenCodeRepository,
    showReasoning: Boolean,
    onOpenSubagent: (String) -> Unit
) {
    val first = turn.first()
    val last = turn.last()
    val activity = turn.flatMap { it.parts }.filter {
        (it is UiPart.Tool || (showReasoning && it is UiPart.Reasoning))
    }
    val totalCost = turn.sumOf { it.cost }
    val groupKey = "turn-${first.id}"
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(if (last.completed == null) Warning else Accent, 6)
            Spacer(Modifier.width(8.dp))
            Text(
                (last.agent ?: "assistant").uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Accent
            )
            last.model?.let {
                Text(
                    " · $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.weight(1f))
            val meta = buildString {
                last.tokens?.let { append(compactTokens(it.output + it.reasoning)).append(" tok") }
                if (totalCost > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(formatCost(totalCost))
                }
            }
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = TextFaint)
            }
        }
        Spacer(Modifier.height(8.dp))
        turn.forEach { message ->
            message.error?.let { ErrorBanner(it) }
            val texts = message.parts.filterIsInstance<UiPart.Text>().map { it.text }
            if (texts.isEmpty()) {
                if (message.parts.isEmpty() && message.text.isNotBlank()) {
                    MarkdownText(message.text)
                }
            } else {
                texts.filter { it.isNotBlank() }.forEach { text ->
                    MarkdownText(text)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        if (activity.isNotEmpty()) {
            ActivityGroup(
                parts = activity,
                groupKey = groupKey,
                expanded = expanded,
                onToggle = onToggle,
                openCode = openCode,
                onOpenSubagent = onOpenSubagent
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: UiMessage,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    openCode: OpenCodeRepository,
    showReasoning: Boolean,
    compactTools: Boolean,
    onOpenSubagent: (String) -> Unit
) {
    when {
        message.isUser -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "YOU · ${relativeTime(message.created)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    modifier = Modifier.padding(end = 4.dp, bottom = 4.dp)
                )
                Surface(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    color = Accent.copy(alpha = 0.13f),
                    shape = RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
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
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (message.completed == null) Warning else Accent)
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        (message.agent ?: "assistant").uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Accent
                    )
                    message.model?.let {
                        Text(
                            " · $it",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    val meta = buildString {
                        message.tokens?.let { append(compactTokens(it.output + it.reasoning)).append(" tok") }
                        if (message.cost > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(formatCost(message.cost))
                        }
                    }
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    }
                }
                Spacer(Modifier.height(7.dp))
                if (message.error != null) ErrorBanner(message.error)
                val segments = remember(message.id, message.parts, showReasoning, compactTools) {
                    buildMessageSegments(message.parts, showReasoning, compactTools)
                }
                segments.forEachIndexed { index, segment ->
                    if (segment.text != null) {
                        MarkdownText(segment.text)
                    } else {
                        val key = "${message.id}-activity-$index"
                        ActivityGroup(
                            parts = segment.activity,
                            groupKey = key,
                            expanded = expanded,
                            onToggle = onToggle,
                            openCode = openCode,
                            onOpenSubagent = onOpenSubagent
                        )
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
                    message.text.ifBlank { message.type.replace('_', ' ') }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

private data class MessageSegment(
    val text: String? = null,
    val activity: List<UiPart> = emptyList()
)

private fun buildMessageSegments(
    parts: List<UiPart>,
    showReasoning: Boolean,
    compactTools: Boolean
): List<MessageSegment> {
    val visible = parts.filter { showReasoning || it !is UiPart.Reasoning }
    if (!compactTools) {
        return visible.map { part ->
            when (part) {
                is UiPart.Text -> MessageSegment(text = part.text)
                else -> MessageSegment(activity = listOf(part))
            }
        }
    }
    val out = mutableListOf<MessageSegment>()
    val activity = mutableListOf<UiPart>()
    fun flush() {
        if (activity.isNotEmpty()) {
            out += MessageSegment(activity = activity.toList())
            activity.clear()
        }
    }
    visible.forEach { part ->
        if (part is UiPart.Text) {
            flush()
            out += MessageSegment(text = part.text)
        } else {
            activity += part
        }
    }
    flush()
    return out
}

@Composable
private fun ActivityGroup(
    parts: List<UiPart>,
    groupKey: String,
    expanded: Set<String>,
    onToggle: (String) -> Unit,
    openCode: OpenCodeRepository,
    onOpenSubagent: (String) -> Unit
) {
    val tools = parts.filterIsInstance<UiPart.Tool>().map { it.tool }
    val reasoning = parts.filterIsInstance<UiPart.Reasoning>()
    val running = tools.any { it.status == "running" || it.status == "streaming" }
    val failed = tools.any { it.status == "error" || it.status == "failed" }
    val summary = buildString {
        val noun = if (tools.size == 1) "tool call" else "tool calls"
        when {
            running -> append("Working…")
            failed -> append("Failed…")
            tools.isEmpty() -> append("Thought")
            else -> append("Finished")
        }
        if (tools.isNotEmpty()) {
            append(" · ")
            append(tools.size)
            append(" ")
            append(noun)
        }
        if (reasoning.isNotEmpty()) {
            if (tools.isEmpty()) append(" · reasoning") else append(" · thinking")
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp))
                .clickable { onToggle(groupKey) }
                .padding(horizontal = 6.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    color = Warning,
                    strokeWidth = 1.5.dp
                )
            } else {
                StatusDot(if (failed) Danger else TextFaint, 5)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                summary,
                style = MaterialTheme.typography.labelMedium,
                color = if (failed) Danger else TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (groupKey in expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (groupKey in expanded) "Hide details" else "Show details",
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }
        if (groupKey in expanded) {
            Column(modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)) {
                parts.forEachIndexed { index, part ->
                    val key = "$groupKey-$index"
                    when (part) {
                        is UiPart.Reasoning -> ReasoningBlock(
                            text = part.text,
                            expanded = key in expanded,
                            onToggle = { onToggle(key) }
                        )
                        is UiPart.Tool -> ToolCard(
                            tool = part.tool,
                            expanded = key in expanded,
                            onToggle = { onToggle(key) },
                            openCode = openCode,
                            onOpenSubagent = onOpenSubagent
                        )
                        is UiPart.Text -> MarkdownText(part.text)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReasoningBlock(text: String, expanded: Boolean, onToggle: () -> Unit) {
    if (text.isBlank()) return
    Surface(
        color = SurfaceRaised.copy(alpha = 0.40f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.7f)),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("REASONING", style = MaterialTheme.typography.labelSmall, color = TextFaint, modifier = Modifier.weight(1f))
                Text(
                    if (expanded) "HIDE" else "SHOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            if (expanded) {
                Spacer(Modifier.height(6.dp))
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
    openCode: OpenCodeRepository,
    onOpenSubagent: (String) -> Unit
) {
    val statusColor = toolStatusColor(tool.status)
    val childSession = tool.metadata?.stringOrNull("sessionID")
    val isSubagent = tool.name.contains("task", ignoreCase = true) ||
        tool.name.contains("subagent", ignoreCase = true) ||
        childSession != null
    Surface(
        color = SurfaceRaised.copy(alpha = 0.36f),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (isSubagent) Violet.copy(alpha = 0.24f) else OutlineDark.copy(alpha = 0.65f)),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    toolIcon(tool.name),
                    contentDescription = null,
                    tint = if (isSubagent) Violet else statusColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    tool.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(7.dp))
                StatusDot(statusColor, 6)
                Spacer(Modifier.width(5.dp))
                Text(tool.status, style = MaterialTheme.typography.labelSmall, color = statusColor)
                Spacer(Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Toggle tool",
                    tint = TextFaint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Text(
                toolSummary(tool),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                fontFamily = MonoFont,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (isSubagent && childSession != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 7.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .clickable { onOpenSubagent(childSession) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OPEN SUBAGENT", style = MaterialTheme.typography.labelSmall, color = Violet)
                    Spacer(Modifier.width(6.dp))
                    Text("→", color = Violet, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (expanded) {
                if (tool.input.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("INPUT", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    Spacer(Modifier.height(3.dp))
                    MonospaceBox(tool.input)
                }
                tool.content.forEach { content ->
                    when (content) {
                        is ToolContent.Text -> {
                            Spacer(Modifier.height(8.dp))
                            Text("OUTPUT", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                            Spacer(Modifier.height(3.dp))
                            MonospaceBox(content.text.take(12_000))
                        }
                        is ToolContent.File -> {
                            Spacer(Modifier.height(8.dp))
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
    val uri = file.resolvedUri
    if (uri.startsWith("data:image/") || uri.startsWith("media:")) {
        RemoteMedia(uri = uri, openCode = openCode)
    } else {
        Text(
            file.name ?: uri.take(60).ifBlank { "attachment" },
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
        Text("Image unavailable", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
    }
}

@Composable
private fun PermissionCard(permission: PermissionRequest, onReply: (String) -> Unit) {
    Surface(
        color = Warning.copy(alpha = 0.10f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Warning.copy(alpha = 0.24f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("PERMISSION REQUESTED", style = MaterialTheme.typography.labelSmall, color = Warning)
            Spacer(Modifier.height(4.dp))
            Text(
                permission.message ?: permission.action,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            if (permission.resources.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    permission.resources.joinToString("\n"),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = Monospace,
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

private enum class ComposerPanel { None, Model, Agent }

@Composable
private fun Composer(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    sending: Boolean,
    connected: Boolean,
    modelLabel: String,
    model: ModelInfo?,
    models: List<ModelInfo>,
    variant: String?,
    agent: String?,
    agents: List<AgentInfo>,
    commands: List<CommandInfo>,
    queued: List<OpenCodeRepository.QueuedPrompt>,
    attachments: List<ChatAttachment>,
    attachmentError: String?,
    onAttach: () -> Unit,
    onRemoveAttachment: (Uri) -> Unit,
    onSelectModel: (ModelInfo) -> Unit,
    onSelectVariant: (ModelInfo, ModelVariant) -> Unit,
    onSelectAgent: (AgentInfo) -> Unit,
    onPickCommand: (CommandInfo) -> Unit,
    onCancelQueued: (String) -> Unit,
    onSendQueuedNow: (OpenCodeRepository.QueuedPrompt) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    var panel by remember { mutableStateOf(ComposerPanel.None) }
    var modelQuery by remember { mutableStateOf("") }
    var pendingModel by remember { mutableStateOf<ModelInfo?>(null) }
    val keyboard = LocalSoftwareKeyboardController.current
    val panelModel = pendingModel ?: model
    val efforts = panelModel?.variants.orEmpty()
    val agentLabel = agents.firstOrNull { it.id == agent }?.name
        ?: agent?.takeIf { it.isNotBlank() }
        ?: "agent"
    val effortLabel = efforts.firstOrNull { it.id == variant }?.label
        ?: variant?.takeIf { it.isNotBlank() && it != "default" }
        ?: "auto"
    val commandQuery = value.removePrefix("/").substringBefore(' ').trim()
    val showCommands = connected && value.startsWith("/") && !value.contains(' ') && !value.contains('\n')
    val filteredCommands = commands.filter {
        commandQuery.isBlank() || it.name.contains(commandQuery, ignoreCase = true)
    }

    LaunchedEffect(showCommands) {
        if (showCommands) panel = ComposerPanel.None
    }
    LaunchedEffect(connected) {
        if (!connected) {
            panel = ComposerPanel.None
            pendingModel = null
        }
    }

    Surface(color = DeepBackground.copy(alpha = 0.97f)) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TinyAction(modelLabel) {
                        keyboard?.hide()
                        pendingModel = null
                        panel = if (panel == ComposerPanel.Model) ComposerPanel.None else ComposerPanel.Model
                    }
                    if (efforts.isNotEmpty()) {
                        TinyAction("effort · $effortLabel") {
                            keyboard?.hide()
                            pendingModel = null
                            panel = ComposerPanel.Model
                        }
                    }
                    TinyAction(agentLabel) {
                        keyboard?.hide()
                        panel = if (panel == ComposerPanel.Agent) ComposerPanel.None else ComposerPanel.Agent
                    }
                }
                Spacer(Modifier.width(2.dp))
                StatusDot(
                    when {
                        sending -> Accent
                        busy -> Warning
                        connected -> Success
                        else -> TextFaint
                    },
                    6
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    when {
                        sending -> "SENDING"
                        busy -> "WORKING"
                        connected -> "READY"
                        else -> "OFFLINE"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        sending -> Accent
                        busy -> Warning
                        connected -> Success
                        else -> TextFaint
                    },
                    maxLines = 1,
                    softWrap = false
                )
            }

            if (attachments.isNotEmpty() || attachmentError != null) {
                Spacer(Modifier.height(7.dp))
                if (attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        attachments.forEach { item ->
                            Surface(
                                color = SurfaceRaised.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, OutlineDark)
                            ) {
                                Row(
                                    modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        item.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 160.dp)
                                    )
                                    Text(
                                        " · ${formatAttachmentSize(item.size)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextFaint,
                                        maxLines = 1
                                    )
                                    IconButton(
                                        onClick = { onRemoveAttachment(item.uri) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove attachment",
                                            tint = TextFaint,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                attachmentError?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = Warning,
                        modifier = Modifier.padding(top = 4.dp, start = 2.dp)
                    )
                }
            }

            if (showCommands && filteredCommands.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceRaised.copy(alpha = 0.82f),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, OutlineDark)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 164.dp)) {
                        items(filteredCommands, key = { "cmd-${it.name}" }) { command ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onPickCommand(command)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "/${command.name}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Accent,
                                    fontFamily = MonoFont,
                                    modifier = Modifier.widthIn(min = 72.dp)
                                )
                                Text(
                                    command.description.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextFaint,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            if (panel != ComposerPanel.None) {
                Spacer(Modifier.height(7.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = SurfaceRaised.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, OutlineDark)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (panel == ComposerPanel.Model) "MODEL & EFFORT" else "AGENT",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextFaint,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    panel = ComposerPanel.None
                                    pendingModel = null
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close panel",
                                    tint = TextFaint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                        when (panel) {
                            ComposerPanel.Model -> {
                                OutlinedTextField(
                                    value = modelQuery,
                                    onValueChange = { modelQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text("Search models or providers", style = MaterialTheme.typography.labelSmall)
                                    },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall,
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Search,
                                            contentDescription = null,
                                            tint = TextFaint,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Accent.copy(alpha = 0.28f),
                                        unfocusedBorderColor = OutlineDark,
                                        focusedContainerColor = SurfaceHigh.copy(alpha = 0.30f),
                                        unfocusedContainerColor = SurfaceHigh.copy(alpha = 0.18f),
                                        cursorColor = Accent,
                                        focusedTextColor = TextPrimary,
                                        unfocusedTextColor = TextPrimary
                                    )
                                )
                                val filteredModels = remember(models, modelQuery) {
                                    val query = modelQuery.trim()
                                    if (query.isBlank()) models else models.filter {
                                        it.name.contains(query, true) ||
                                            it.modelID.contains(query, true) ||
                                            it.providerID.contains(query, true) ||
                                            it.family.contains(query, true)
                                    }
                                }
                                Spacer(Modifier.height(5.dp))
                                LazyColumn(modifier = Modifier.heightIn(max = 148.dp)) {
                                    items(filteredModels, key = { "model-${it.providerID}-${it.modelID}" }) { item ->
                                        val selected = model?.modelID == item.modelID &&
                                            model.providerID == item.providerID
                                        ComposerModelRow(
                                            model = item,
                                            selected = selected,
                                            onClick = {
                                                if (selected) {
                                                    if (item.variants.isEmpty()) panel = ComposerPanel.None
                                                } else {
                                                    pendingModel = item
                                                    onSelectModel(item)
                                                    if (item.variants.isEmpty()) panel = ComposerPanel.None
                                                }
                                            }
                                        )
                                    }
                                }
                                if (efforts.isNotEmpty()) {
                                    Spacer(Modifier.height(7.dp))
                                    Text(
                                        "REASONING EFFORT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextFaint
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        efforts.forEach { effort ->
                                            ComposerChoice(
                                                label = effort.label,
                                                selected = effort.id == variant,
                                                onClick = {
                                                    panelModel?.let { onSelectVariant(it, effort) }
                                                    pendingModel = null
                                                    panel = ComposerPanel.None
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                            ComposerPanel.Agent -> {
                                LazyColumn(modifier = Modifier.heightIn(max = 168.dp)) {
                                    items(agents.filterNot { it.hidden }, key = { "agent-${it.id}" }) { item ->
                                        val selected = item.id == agent
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) Accent.copy(alpha = 0.10f) else Color.Transparent)
                                                .clickable {
                                                    onSelectAgent(item)
                                                    panel = ComposerPanel.None
                                                }
                                                .padding(horizontal = 8.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    item.name,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = if (selected) Accent else TextPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                item.description?.takeIf { it.isNotBlank() }?.let {
                                                    Text(
                                                        it,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = TextFaint,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                            if (selected) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Accent,
                                                    modifier = Modifier.size(15.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            ComposerPanel.None -> Unit
                        }
                    }
                }
            }

            if (queued.isNotEmpty()) {
                Spacer(Modifier.height(7.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 112.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    queued.forEach { item ->
                        Surface(
                            color = Warning.copy(alpha = 0.07f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Warning.copy(alpha = 0.16f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = 9.dp, end = 2.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("QUEUED", style = MaterialTheme.typography.labelSmall, color = Warning)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    item.text.replace('\n', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (item.files > 0) {
                                    Text(
                                        " +${item.files} files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextFaint,
                                        maxLines = 1
                                    )
                                }
                                if (!busy) {
                                    Text(
                                        "SEND NOW",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .clickable { onSendQueuedNow(item) }
                                            .padding(horizontal = 7.dp, vertical = 5.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { onCancelQueued(item.id) },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel queued message", tint = TextFaint, modifier = Modifier.size(15.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (busy) "Queue a message…" else "Message OpenCode…",
                            color = TextFaint,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    maxLines = 6,
                    enabled = connected,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (connected && !sending &&
                                (value.isNotBlank() || attachments.isNotEmpty())
                            ) onSend()
                        }
                    ),
                    leadingIcon = {
                        IconButton(onClick = onAttach, enabled = connected) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = "Attach files",
                                tint = if (connected) TextSecondary else TextFaint,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    },
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent.copy(alpha = 0.30f),
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = SurfaceRaised.copy(alpha = 0.40f),
                        unfocusedContainerColor = SurfaceRaised.copy(alpha = 0.34f),
                        cursorColor = Accent,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )
                Spacer(Modifier.width(7.dp))
                if (busy) {
                    CircleAction(
                        icon = Icons.Default.Stop,
                        description = "Stop",
                        background = Danger.copy(alpha = 0.12f),
                        tint = Danger,
                        onClick = onStop
                    )
                    Spacer(Modifier.width(6.dp))
                }
                val sendEnabled = connected && !sending &&
                    (value.isNotBlank() || attachments.isNotEmpty())
                CircleAction(
                    icon = Icons.Default.Send,
                    description = if (busy) "Queue message" else "Send",
                    background = if (sendEnabled) Accent else OutlineDark,
                    tint = if (sendEnabled) Void else TextFaint,
                    enabled = sendEnabled,
                    onClick = onSend
                )
            }
        }
    }
}

@Composable
private fun ComposerModelRow(
    model: ModelInfo,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Accent.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    model.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) Accent else TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    model.providerID,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    maxLines = 1
                )
            }
            Text(
                buildString {
                    if (model.contextLabel.isNotBlank()) append(model.contextLabel).append(" ctx")
                    model.firstCost?.input?.takeIf { it > 0 }?.let {
                        if (isNotEmpty()) append(" · ")
                        append("$").append(String.format(Locale.US, "%.2f", it)).append("/M in")
                    }
                    if (model.variants.isNotEmpty()) {
                        if (isNotEmpty()) append(" · ")
                        append(model.variants.size).append(" effort")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ComposerChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        color = if (selected) Accent else SurfaceRaised.copy(alpha = 0.7f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (selected) Accent else OutlineDark)
    ) {
        Text(
            label.uppercase(),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Void else TextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun CircleAction(
    icon: ImageVector,
    description: String,
    background: Color,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FormCard(
    form: FormInfo,
    onSubmit: (Map<String, JsonElement>) -> Unit,
    onCancel: () -> Unit
) {
    var answers by remember(form.id) { mutableStateOf(defaultFormAnswers(form.fields)) }
    var error by remember(form.id) { mutableStateOf<String?>(null) }
    Surface(
        color = Violet.copy(alpha = 0.09f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Violet.copy(alpha = 0.28f)),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 430.dp)
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("OPENCODE ASKS", style = MaterialTheme.typography.labelSmall, color = Violet)
            Text(
                form.title.ifBlank { "Question" },
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            form.fields.filter { fieldVisible(it, answers) }.forEach { field ->
                when (field.type) {
                    "boolean" -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            val key = field.key
                            val current = answers[key]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false
                            answers = answers + (key to JsonPrimitive(!current))
                        }
                    ) {
                        Checkbox(
                            checked = answers[field.key]?.let { (it as? JsonPrimitive)?.booleanOrNull } ?: false,
                            onCheckedChange = { checked -> answers = answers + (field.key to JsonPrimitive(checked)) }
                        )
                        Text(field.label, color = TextPrimary, style = MaterialTheme.typography.bodyMedium)
                    }
                    "multiselect" -> {
                        FormLabel(field)
                        field.options.forEach { option ->
                            val current = (answers[field.key] as? JsonArray)
                                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                                ?: emptyList()
                            val active = option.value in current
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val next = if (active) current - option.value else current + option.value
                                    answers = answers + (field.key to JsonArray(next.map { JsonPrimitive(it) }))
                                }
                            ) {
                                Checkbox(checked = active, onCheckedChange = null)
                                Text(
                                    option.label.ifBlank { option.value },
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    "external" -> {
                        FormLabel(field)
                        Text(field.url.orEmpty(), color = Accent, style = MaterialTheme.typography.bodySmall)
                    }
                    else -> {
                        FormLabel(field)
                        if (field.options.isNotEmpty()) {
                            field.options.forEach { option ->
                                val current = (answers[field.key] as? JsonPrimitive)?.contentOrNull.orEmpty()
                                FormChoice(
                                    option = option,
                                    active = current == option.value,
                                    onClick = { answers = answers + (field.key to JsonPrimitive(option.value)) }
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = (answers[field.key] as? JsonPrimitive)?.contentOrNull.orEmpty(),
                                onValueChange = { answers = answers + (field.key to JsonPrimitive(it)) },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(field.placeholder ?: field.description ?: "", color = TextFaint) },
                                singleLine = field.type != "string",
                                keyboardOptions = if (field.type == "number" || field.type == "integer") {
                                    KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                                } else KeyboardOptions.Default,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Violet.copy(alpha = 0.55f),
                                    unfocusedBorderColor = OutlineDark,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary,
                                    cursorColor = Violet
                                )
                            )
                        }
                    }
                }
            }
            error?.let { Text(it, color = Danger, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        val visible = form.fields.filter { fieldVisible(it, answers) }
                        val missing = visible.any { field ->
                            field.required && when (field.type) {
                                "boolean" -> false
                                "multiselect" -> ((answers[field.key] as? JsonArray)?.isEmpty() ?: true)
                                "external" -> false
                                else -> (answers[field.key] as? JsonPrimitive)?.contentOrNull.isNullOrBlank()
                            }
                        }
                        if (missing) {
                            error = "Fill the required fields"
                        } else {
                            val payload = visible.mapNotNull { field ->
                                val value = answers[field.key] ?: return@mapNotNull null
                                if (field.type == "external") return@mapNotNull null
                                val raw = (value as? JsonPrimitive)?.contentOrNull
                                if (value is JsonPrimitive && raw.isNullOrBlank()) return@mapNotNull null
                                val typed: JsonElement = when (field.type) {
                                    "number" -> raw?.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: value
                                    "integer" -> raw?.toLongOrNull()?.let { JsonPrimitive(it) } ?: value
                                    else -> value
                                }
                                field.key to typed
                            }.toMap()
                            onSubmit(payload)
                        }
                    }
                ) { Text("Submit answer") }
                TextButton(onClick = onCancel) { Text("Skip", color = TextSecondary) }
            }
        }
    }
}

@Composable
private fun FormLabel(field: FormField) {
    Column {
        Text(
            field.label + if (field.required) " *" else "",
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium
        )
        field.description?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = TextFaint, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun FormChoice(option: FormOption, active: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) Violet.copy(alpha = 0.14f) else SurfaceRaised.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        StatusDot(if (active) Violet else TextFaint, 7)
        Spacer(Modifier.width(9.dp))
        Column {
            Text(option.label.ifBlank { option.value }, color = TextPrimary, style = MaterialTheme.typography.bodySmall)
            option.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TextFaint, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

private fun defaultFormAnswers(fields: List<FormField>): Map<String, JsonElement> =
    fields.mapNotNull { field ->
        when (field.type) {
            "boolean" -> field.key to JsonPrimitive(
                (field.default as? JsonPrimitive)?.booleanOrNull ?: false
            )
            "multiselect" -> field.key to (field.default ?: JsonArray(emptyList()))
            else -> field.default?.let { field.key to it }
        }
    }.toMap()

private fun fieldVisible(field: FormField, answers: Map<String, JsonElement>): Boolean {
    if (field.hidden) return false
    return field.conditions.all { condition ->
        val value = answers[condition.key]?.let { element ->
            if (element is JsonArray) element.joinToString(",") {
                (it as? JsonPrimitive)?.contentOrNull.orEmpty()
            }
            else (element as? JsonPrimitive)?.contentOrNull.orEmpty()
        }.orEmpty()
        val expected = (condition.value as? JsonPrimitive)?.contentOrNull.orEmpty()
        if (condition.op == "neq") value != expected else value == expected
    }
}

@Composable
private fun SessionDialog(
    sessions: List<SessionInfo>,
    allSessions: List<SessionInfo>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) sessions else sessions.filter {
            it.displayTitle.lowercase().contains(needle) || (it.agent ?: "").contains(needle, true)
        }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = DeepBackground) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sessions", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text(
                            "${allSessions.size} sessions · ${allSessions.count { it.isRunning }} running",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Surface(
                        color = Accent,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = onNew)
                    ) {
                        Text(
                            "NEW",
                            style = MaterialTheme.typography.labelMedium,
                            color = Void,
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search sessions", color = TextFaint) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextFaint) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent.copy(alpha = 0.55f),
                        unfocusedBorderColor = OutlineDark,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent
                    )
                )
                Spacer(Modifier.height(10.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 14.dp, end = 14.dp, bottom = 24.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filtered.forEach { session ->
                        item(key = session.id) {
                            SessionRow(
                                session = session,
                                active = session.id == activeId,
                                indent = false,
                                onClick = { onSelect(session.id) }
                            )
                        }
                        items(allSessions.filter { it.parentID == session.id }, key = { it.id }) { child ->
                            SessionRow(
                                session = child,
                                active = child.id == activeId,
                                indent = true,
                                onClick = { onSelect(child.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionRow(
    session: SessionInfo,
    active: Boolean,
    indent: Boolean,
    onClick: () -> Unit
) {
    Surface(
        color = when {
            active -> Accent.copy(alpha = 0.075f)
            else -> SurfaceRaised.copy(alpha = 0.34f)
        },
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            when {
                active -> Accent.copy(alpha = 0.34f)
                indent -> Violet.copy(alpha = 0.18f)
                else -> OutlineDark.copy(alpha = 0.58f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (indent) 18.dp else 0.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            session.isRunning -> Warning.copy(alpha = 0.13f)
                            active -> Accent.copy(alpha = 0.13f)
                            else -> OutlineDark.copy(alpha = 0.35f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                StatusDot(
                    when {
                        session.isRunning -> Warning
                        active -> Accent
                        else -> TextFaint
                    },
                    6
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (indent) "↳ ${session.displayTitle}" else session.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(session.agent ?: "agent")
                        append(" · ")
                        append(relativeTime(session.time.updated))
                        if (session.cost > 0.0) {
                            append(" · ")
                            append(formatCost(session.cost))
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.isRunning) {
                Text(
                    "LIVE",
                    style = MaterialTheme.typography.labelSmall,
                    color = Warning,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            } else if (active) {
                Text("●", color = Accent, modifier = Modifier.padding(horizontal = 7.dp))
            }
        }
    }
}

@Composable
private fun StatsDialog(
    session: SessionInfo?,
    usage: dev.pvps.opencode.UsageLimits?,
    parent: SessionInfo?,
    messages: List<UiMessage>,
    models: List<ModelInfo>,
    subagents: List<SessionInfo>,
    contextUsed: Long?,
    contextLimit: Long,
    contextFraction: Double?,
    tps: Double?,
    realCost: Double?,
    estimatedCost: Double,
    onOpenSubagent: (String) -> Unit,
    onOpenParent: (SessionInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val totalTokens = session?.tokens ?: messages.lastOrNull { it.tokens != null }?.tokens ?: TokenUsage()
    val providerCost = realCost ?: session?.cost?.takeIf { it > 0.0 } ?: 0.0
    val usingEstimate = realCost == null && providerCost <= 0.0 && estimatedCost > 0.0
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        title = { Text("Session telemetry", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    session?.displayTitle ?: "No active session",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        session?.agent?.let { append("$it · ") }
                        session?.model?.let { append("${it.providerID}/${it.id} · ") }
                        append(if (session?.isRunning == true) "working" else "idle")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
                parent?.let { p ->
                    TextButton(onClick = { onOpenParent(p) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("← Back to ${p.displayTitle}", color = Violet)
                    }
                }

                if (contextFraction != null) {
                    Text("CONTEXT WINDOW", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    LinearProgressIndicator(
                        progress = { contextFraction.toFloat() },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                        color = when {
                            contextFraction > 0.85 -> Danger
                            contextFraction > 0.65 -> Warning
                            else -> Accent
                        },
                        trackColor = OutlineDark
                    )
                    Text(
                        "${contextUsed?.let { compactTokens(it) } ?: "—"} / ${compactTokens(contextLimit)} · ${(contextFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        "Estimated from the latest assistant request (input + cache). It updates after each turn.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint
                    )
                }

                StatsGrid(
                    cost = providerCost.takeIf { it > 0.0 } ?: estimatedCost,
                    estimated = usingEstimate,
                    totalTokens = totalTokens,
                    tps = tps,
                    messages = messages.size
                )
                Text(
                    when {
                        realCost != null ->
                            "Actual spend from the OpenCode Console request log for this session."
                        usingEstimate ->
                            "The local provider reports $0.00 for this session. The dollar figure is estimated from model list price; the Go allowance below is authoritative."
                        else -> "Cost reported by the provider for this session."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint
                )
                usage?.go?.let { go ->
                    Text("OPENCODE GO ALLOWANCE", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        StatBox("ROLLING", "${go.rolling.percent.toInt()}%", Modifier.weight(1f))
                        StatBox("WEEKLY", "${go.weekly.percent.toInt()}%", Modifier.weight(1f))
                        StatBox("MONTHLY", "${go.monthly.percent.toInt()}%", Modifier.weight(1f))
                    }
                }
                if (usage != null && usage.budgets.isNotEmpty()) {
                    Text("REPORTED BUDGETS", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    usage.budgets.take(4).forEach { budget ->
                        InfoRow(
                            label = listOf(budget.scope, budget.per)
                                .filter { it.isNotBlank() }
                                .joinToString(" · ")
                                .ifBlank { "Budget" },
                            value = "${formatCost(budget.spent)} / ${formatCost(budget.limit)}"
                        )
                    }
                }

                if (subagents.isNotEmpty()) {
                    Text("SUBAGENTS", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                    subagents.forEach { sub ->
                        Surface(
                            color = Violet.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Violet.copy(alpha = 0.16f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onOpenSubagent(sub.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusDot(if (sub.isRunning) Warning else Violet, 6)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        sub.displayTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${sub.agent ?: "subagent"} · ${if (sub.isRunning) "running" else "done"} · ${formatCost(sub.cost)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextFaint
                                    )
                                }
                                Text("→", color = Violet)
                            }
                        }
                    }
                } else {
                    Text("No subagents for this session.", style = MaterialTheme.typography.bodySmall, color = TextFaint)
                }

                if (session != null) {
                    Text(
                        "created ${relativeTime(session.time.created)} · updated ${relativeTime(session.time.updated)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Accent) } }
    )
}

@Composable
private fun StatsGrid(
    cost: Double,
    estimated: Boolean,
    totalTokens: TokenUsage,
    tps: Double?,
    messages: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatBox(if (estimated) "SPENT · EST" else "SPENT", formatCost(cost), Modifier.weight(1f))
            StatBox("TOK/S", tps?.let { String.format(Locale.US, "%.1f", it) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatBox("INPUT", compactTokens(totalTokens.input), Modifier.weight(1f))
            StatBox("OUTPUT", compactTokens(totalTokens.output + totalTokens.reasoning), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            StatBox("CACHE", compactTokens(totalTokens.cache.read + totalTokens.cache.write), Modifier.weight(1f))
            StatBox("MESSAGES", messages.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = DeepBackground.copy(alpha = 0.55f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.65f))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextFaint)
            Text(value, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
        }
    }
}

@Composable
private fun MarkdownText(text: String) {
    if (text.isBlank()) return
    val chunks = text.split("```")
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        chunks.forEachIndexed { index, chunk ->
            if (index % 2 == 1) {
                val firstLine = chunk.lineSequence().firstOrNull().orEmpty()
                val code = if (firstLine.isNotBlank() && !firstLine.contains(' ')) {
                    chunk.substringAfter('\n', "")
                } else {
                    chunk
                }
                MonospaceBox(code.trim())
            } else if (chunk.isNotBlank()) {
                SelectionContainer {
                    Text(
                        inlineMarkdown(chunk.trim()),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary
                    )
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
                    withStyle(
                        SpanStyle(
                            fontFamily = MonoFont,
                            background = DeepBackground,
                            color = Accent
                        )
                    ) { append(text.substring(i + 1, end)) }
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

private val Monospace: FontFamily
    get() = MonoFont

private fun toolIcon(name: String): ImageVector = when {
    name.contains("shell") || name.contains("bash") || name.contains("terminal") -> Icons.Default.Terminal
    name.contains("read") || name.contains("write") || name.contains("edit") -> Icons.Default.Description
    name.contains("grep") || name.contains("glob") || name.contains("search") -> Icons.Default.Search
    name.contains("webfetch") || name.contains("fetch") || name.contains("http") -> Icons.Default.Language
    name.contains("task") || name.contains("agent") -> Icons.Default.Build
    else -> Icons.Default.Build
}

private fun toolStatusColor(status: String): Color = when (status) {
    "completed", "success" -> Success
    "error", "failed" -> Danger
    "running", "streaming" -> Warning
    else -> TextSecondary
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

private fun estimateSessionCost(
    sessionTokens: TokenUsage?,
    model: ModelInfo?,
    messages: List<UiMessage>
): Double {
    val usage = sessionTokens?.takeIf {
        it.input > 0 || it.output > 0 || it.reasoning > 0 || it.cache.read > 0
    } ?: messages.filter { it.isAssistant }
        .mapNotNull { it.tokens }
        .fold(TokenUsage()) { acc, item ->
            TokenUsage(
                input = acc.input + item.input,
                output = acc.output + item.output,
                reasoning = acc.reasoning + item.reasoning,
                cache = dev.pvps.opencode.CacheUsage(
                    acc.cache.read + item.cache.read,
                    acc.cache.write + item.cache.write
                )
            )
        }
    val pricing = model?.firstCost ?: return 0.0
    return (
        usage.input / 1_000_000.0 * pricing.input +
            (usage.output + usage.reasoning) / 1_000_000.0 * pricing.output +
            usage.cache.read / 1_000_000.0 * pricing.cache.read +
            usage.cache.write / 1_000_000.0 * pricing.cache.write
        )
}

private fun tokensPerSecond(messages: List<UiMessage>, now: Long): Double? {
    val message = messages.lastOrNull {
        it.isAssistant && it.tokens != null && it.created > 0
    } ?: return null
    val tokens = message.tokens ?: return null
    // time.streamed is the first token and is often only a few ms after
    // time.created for cached responses. Measuring streamed→completed produced
    // absurd values like 341000 tok/s. Whole-request duration is stable.
    val start = message.created
    val end = message.completed ?: now
    val elapsedMs = (end - start).coerceAtLeast(400L)
    val seconds = elapsedMs / 1000.0
    val output = tokens.output + tokens.reasoning
    if (output <= 0 || seconds <= 0) return null
    return (output / seconds).coerceIn(0.1, 4000.0)
}

private const val MAX_ATTACHMENTS = 8
private const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024
private const val MAX_TOTAL_ATTACHMENT_BYTES = 24 * 1024 * 1024

private fun readChatAttachment(context: Context, uri: Uri): ChatAttachment {
    val resolver = context.contentResolver
    var name = uri.lastPathSegment ?: "attachment"
    var declaredSize = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    cursor.getString(nameIndex)?.takeIf { it.isNotBlank() }?.let { name = it }
                }
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                    declaredSize = cursor.getLong(sizeIndex)
                }
            }
        }
    if (declaredSize > MAX_ATTACHMENT_BYTES) {
        error("$name is too large (max 20 MB)")
    }
    val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
        ?: error("Could not open $name")
    if (bytes.size > MAX_ATTACHMENT_BYTES) {
        error("$name is too large (max 20 MB)")
    }
    val mime = resolver.getType(uri)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
    return ChatAttachment(
        uri = uri,
        name = name,
        mime = mime,
        base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
        size = bytes.size.toLong()
    )
}

private fun formatAttachmentSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun modelLabel(ref: ModelRef?, models: List<ModelInfo>): String {
    if (ref == null) return "model"
    val info = models.firstOrNull { it.modelID == ref.id && it.providerID == ref.providerID }
    val name = info?.label ?: ref.id
    val provider = ref.providerID.takeIf { it.isNotBlank() }
    return buildString {
        append(name)
        provider?.let { append(" · ").append(it) }
    }
}

private fun formatCost(cost: Double): String = when {
    cost <= 0.0 -> "$0.00"
    cost < 0.01 -> "$" + String.format(Locale.US, "%.4f", cost)
    else -> "$" + String.format(Locale.US, "%.2f", cost)
}

private fun compactTokens(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}
