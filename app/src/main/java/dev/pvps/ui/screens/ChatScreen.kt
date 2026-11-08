package dev.pvps.ui.screens

import android.content.ContentResolver
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.core.content.FileProvider
import dev.pvps.security.SecuritySupport
import java.io.File
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.UnfoldLess
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.style.TextAlign
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
import dev.pvps.ui.components.GlassIconButton
import dev.pvps.ui.components.InfoRow
import dev.pvps.ui.components.MarkdownText
import dev.pvps.ui.components.MeterBar
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.PvpsCard
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
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Void
import dev.pvps.ui.theme.Warning
import dev.pvps.ui.theme.pressable
import coil.compose.AsyncImage
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

    val importUris: (List<Uri>, Boolean) -> Unit = { uris, allowOwnProvider ->
        if (uris.isNotEmpty()) {
            scope.launch {
                val result = withContext(Dispatchers.IO) {
                    val picked = mutableListOf<ChatAttachment>()
                    val errors = mutableListOf<String>()
                    uris.forEach { uri ->
                        runCatching {
                            requireReadableAttachmentUri(context, uri, allowOwnProvider)
                            readChatAttachment(context, uri)
                        }.onSuccess { picked += it }
                            .onFailure { errors += (it.message ?: "Could not read attachment") }
                    }
                    picked to errors
                }
                val room = (MAX_ATTACHMENTS - attachments.size).coerceAtLeast(0)
                var totalBytes = attachments.sumOf { it.size }
                val added = mutableListOf<ChatAttachment>()
                for (candidate in result.first) {
                    if (added.size >= room) break
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
                attachments = (attachments + added).distinctBy { it.uri }
            }
        }
    }
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> importUris(uris, false) }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_ATTACHMENTS)
    ) { uris -> importUris(uris, false) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) importUris(listOf(uri), true)
    }
    val launchCamera: () -> Unit = {
        runCatching {
            val photo = File(context.cacheDir, "camera-${System.currentTimeMillis()}.jpg")
            FileProvider.getUriForFile(context, "${context.packageName}.files", photo)
        }.onSuccess { uri ->
            pendingCameraUri = uri
            cameraPicker.launch(uri)
        }.onFailure {
            attachmentError = "Camera unavailable: ${it.message}"
        }
    }

    LaunchedEffect(host?.id, connection?.status, connection?.message) {
        if (host != null && connection?.status == ConnectionState.Status.CONNECTED) {
            // The repository compares the current tunnel URL and reconnects
            // only when the SSH watchdog rebuilt the forward on a new port.
            viewModel.connectOpenCode(host)
        }
    }

    val pendingOpenSession = viewModel.pendingOpenSession.collectAsStateValue()
    LaunchedEffect(pendingOpenSession, chat.connected, chat.sessions.size, chat.activeSessionId) {
        val target = pendingOpenSession ?: return@LaunchedEffect
        if (!chat.connected) return@LaunchedEffect
        when {
            chat.activeSessionId == target -> viewModel.consumePendingOpenSession()
            chat.sessions.any { it.id == target } -> {
                viewModel.consumePendingOpenSession()
                viewModel.container.openCode.selectSession(target)
            }
            chat.sessions.isNotEmpty() -> viewModel.consumePendingOpenSession()
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
    val contextUsed = chat.contextTokens.takeIf { it > 0 }
        ?: lastUsage?.let { it.input + it.cache.read + it.cache.write }
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
    // Console logs often report an explicit 0.00 for sessions covered by the Go
    // allowance. Do not turn that into a misleading "$0.00" headline.
    val actualSpend = chat.realCost?.takeIf { it > 0.0 }
        ?: session?.cost?.takeIf { it > 0.0 }
    val estimatedSpend = if (actualSpend != null) {
        0.0
    } else {
        estimateSessionCost(session?.tokens, modelInfo, chat.messages)
    }
    val spentValue = when {
        actualSpend != null -> formatCost(actualSpend)
        goMeter != null -> "${goMeter.percent.toInt()}%"
        estimatedSpend > 0.0 -> "~" + formatCost(estimatedSpend)
        else -> "—"
    }
    val spentLabel = when {
        actualSpend != null -> "SPENT"
        goMeter != null -> "GO PLAN"
        estimatedSpend > 0.0 -> "SPENT · EST"
        else -> "SPENT"
    }
    val activeForm = chat.forms.firstOrNull()

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ChatTopBar(
                hostName = host?.name,
                stateLabel = when {
                    session?.parentID != null -> "subagent"
                    chat.connected -> if (chat.busy) "working" else "ready"
                    chat.connecting -> "connecting"
                    else -> "offline"
                },
                contextFraction = contextFraction,
                contextUsed = contextUsed,
                contextLimit = contextLimit,
                spentValue = spentValue,
                spentLabel = spentLabel,
                tps = tps,
                busy = chat.busy,
                onBack = onBack,
                onStats = { showStats = true },
                onCollapse = { expanded = emptySet() },
                canCollapse = expanded.isNotEmpty(),
                onSessions = { showSessions = true },
                sessionsEnabled = chat.connected,
                onNewSession = {
                    scope.launch { viewModel.container.openCode.newSession() }
                },
                newSessionEnabled = chat.connected && !chat.connecting
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
        if (connection?.status != ConnectionState.Status.CONNECTED &&
            connection?.status != ConnectionState.Status.RECONNECTING
        ) {
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
            if (connection?.status == ConnectionState.Status.RECONNECTING) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Warning,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        "SSH link lost. Reconnecting automatically…",
                        color = Warning,
                        style = PvpsType.monoBody
                    )
                }
            }
            if (chat.error != null) {
                Box(modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.xs)) {
                    ErrorBanner(chat.error!!) { viewModel.container.openCode.clearError() }
                }
            }
            if (chat.connecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
                    Spacer(Modifier.width(Space.sm))
                    Text("Connecting to OpenCode…", color = TextSecondary, style = PvpsType.monoBody)
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

            chat.parentSession?.let { parent ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.xs)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(Violet.copy(alpha = 0.08f))
                        .pressable {
                            scope.launch { viewModel.container.openCode.selectSession(parent.id) }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("←", color = Violet, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(Space.sm))
                        Column(Modifier.weight(1f)) {
                            Text("SUBAGENT", style = PvpsType.eyebrow, color = Violet)
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
                if (activeForm != null) {
                    FormWizard(
                        form = activeForm,
                        onSubmit = { answer ->
                            scope.launch { viewModel.container.openCode.replyForm(activeForm, answer) }
                        },
                        onCancel = {
                            scope.launch { viewModel.container.openCode.cancelForm(activeForm) }
                        }
                    )
                } else if (chat.messages.isEmpty()) {
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
                            start = Space.gutter,
                            end = Space.gutter,
                            top = Space.md,
                            bottom = Space.lg
                        ),
                        verticalArrangement = Arrangement.spacedBy(Space.lg)
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
                if (activeForm == null && conversation.isNotEmpty() && !atBottom) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Space.block)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(SurfaceHigh.copy(alpha = 0.94f))
                            .pressable {
                                scope.launch {
                                    runCatching { listState.animateScrollToItem(conversation.lastIndex) }
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = "Go to latest message",
                                tint = Accent,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(Modifier.width(Space.xs))
                            Text(
                                "Latest",
                                style = PvpsType.eyebrow,
                                color = TextPrimary
                            )
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

            if (activeForm == null && session?.parentID != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Space.gutter, vertical = Space.sm)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(Violet.copy(alpha = 0.08f))
                ) {
                    Text(
                        "Read-only subagent. Messages can only be sent to the parent session.",
                        style = PvpsType.monoBody,
                        color = Violet,
                        modifier = Modifier.padding(horizontal = Space.md, vertical = Space.md)
                    )
                }
            } else if (activeForm == null) {
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
                onAttachFiles = { filePicker.launch(arrayOf("*/*")) },
                onAttachPhotos = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onAttachCamera = launchCamera,
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
    }

    if (showSessions) {
        SessionDialog(
            sessions = chat.rootSessions,
            projects = chat.projects,
            activeSession = session,
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
private fun ChatTopBar(
    hostName: String?,
    stateLabel: String,
    contextFraction: Double?,
    contextUsed: Long?,
    contextLimit: Long,
    spentValue: String,
    spentLabel: String,
    tps: Double?,
    busy: Boolean,
    onBack: () -> Unit,
    onStats: () -> Unit,
    onCollapse: () -> Unit,
    canCollapse: Boolean,
    onSessions: () -> Unit,
    sessionsEnabled: Boolean,
    onNewSession: () -> Unit,
    newSessionEnabled: Boolean
) {
    val contextColor = when {
        contextFraction == null -> TextFaint
        contextFraction > 0.85 -> Danger
        contextFraction > 0.65 -> Warning
        else -> Accent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(onClick = onBack, size = 36.dp) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(Space.sm))
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .pressable(onClick = onStats),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { (contextFraction ?: 0.0).toFloat() },
                modifier = Modifier.fillMaxSize().padding(2.dp),
                color = contextColor,
                trackColor = Color.White.copy(alpha = 0.07f),
                strokeWidth = 2.5.dp
            )
            Text(
                contextFraction?.let { "${(it * 100).toInt()}%" } ?: "—",
                style = PvpsType.numericHero.copy(
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    letterSpacing = 0.sp
                ),
                color = contextColor,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(Space.sm))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    when {
                        busy -> Warning
                        stateLabel == "ready" -> Success
                        stateLabel == "offline" -> Danger
                        else -> TextFaint
                    },
                    5
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    hostName ?: "OpenCode",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    stateLabel.uppercase(),
                    style = PvpsType.eyebrow,
                    color = TextFaint,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(Space.xxs))
            Text(
                buildString {
                    append(spentLabel.lowercase())
                    append(" ")
                    append(spentValue)
                    if (tps != null) {
                        append("  ·  ")
                        append(
                            if (tps >= 100) "${tps.toInt()}"
                            else String.format(Locale.US, "%.1f", tps)
                        )
                        append(" tok/s")
                    }
                    if (contextUsed != null && contextLimit > 0) {
                        append("  ·  ")
                        append(compactTokens(contextUsed))
                        append("/")
                        append(compactTokens(contextLimit))
                    }
                },
                style = PvpsType.monoBody,
                color = if (busy && tps != null) Warning else TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TopBarIconAction(
                icon = Icons.Default.UnfoldLess,
                description = "Collapse all thinking and tools",
                enabled = canCollapse,
                tint = if (canCollapse) TextSecondary else TextFaint,
                onClick = onCollapse
            )
            TopBarIconAction(
                icon = Icons.Default.List,
                description = "Sessions",
                enabled = sessionsEnabled,
                tint = if (sessionsEnabled) TextSecondary else TextFaint,
                onClick = onSessions
            )
            TopBarIconAction(
                icon = Icons.Default.Add,
                description = "New session",
                enabled = newSessionEnabled,
                tint = if (newSessionEnabled) TextSecondary else TextFaint,
                onClick = onNewSession
            )
        }
    }
}

@Composable
private fun TopBarIconAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun WorkingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Accent, strokeWidth = 1.6.dp)
        Spacer(Modifier.width(Space.sm))
        Text("OpenCode is working…", color = TextSecondary, style = PvpsType.monoBody)
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
            Spacer(Modifier.width(Space.sm))
            Text(
                (last.agent ?: "assistant").uppercase(),
                style = PvpsType.eyebrow,
                color = TextSecondary
            )
            last.model?.let {
                Text(
                    " · $it",
                    style = PvpsType.monoBody,
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
                Text(meta, style = PvpsType.eyebrow, color = TextFaint)
            }
        }
        Spacer(Modifier.height(10.dp))
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
        if (last.completed == null) {
            val reasoning = turn.asReversed()
                .flatMap { it.parts }
                .filterIsInstance<UiPart.Reasoning>()
                .lastOrNull { it.text.isNotBlank() }
                ?.text
            if (reasoning != null) {
                LiveReasoningWindow(reasoning)
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
private fun LiveReasoningWindow(text: String) {
    // Keep the visible window small so the transcript stays readable while the
    // model streams. The full reasoning remains available in activity history.
    val words = remember(text) { text.trim().split(Regex("\\s+")) }
    val window = remember(words) {
        val tail = words.takeLast(32)
        buildString {
            if (words.size > tail.size) append("… ")
            append(tail.joinToString(" "))
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.sm)
            .clip(RoundedCornerShape(Radius.sm))
            .background(Warning.copy(alpha = 0.055f))
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    color = Warning,
                    strokeWidth = 1.3.dp
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    "THINKING · LIVE",
                    style = PvpsType.eyebrow,
                    color = Warning
                )
            }
            Spacer(Modifier.height(Space.xs))
            Text(
                window,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                fontStyle = FontStyle.Italic,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
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
                    style = PvpsType.eyebrow,
                    color = TextFaint,
                    modifier = Modifier.padding(end = Space.xs, bottom = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Accent.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.md)) {
                        MarkdownText(
                            message.text.ifBlank { "(empty message)" },
                            compact = true
                        )
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
                    Spacer(Modifier.width(Space.sm))
                    Text(
                        (message.agent ?: "assistant").uppercase(),
                        style = PvpsType.eyebrow,
                        color = TextFaint
                    )
                    message.model?.let {
                        Text(
                            " · $it",
                            style = PvpsType.monoBody,
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
                        Text(meta, style = PvpsType.eyebrow, color = TextFaint)
                    }
                }
                Spacer(Modifier.height(10.dp))
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
                    style = PvpsType.eyebrow,
                    color = TextFaint,
                    modifier = Modifier.padding(vertical = Space.xxs)
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
    var showHistory by remember(groupKey) { mutableStateOf(false) }
    val activeIndex = remember(parts) {
        parts.indexOfLast {
            it is UiPart.Tool && (it.tool.status == "running" || it.tool.status == "streaming")
        }.takeIf { it >= 0 }
            ?: parts.indexOfLast { it is UiPart.Tool }.takeIf { it >= 0 }
            ?: parts.lastIndex
    }
    val activePart = parts.getOrNull(activeIndex)
    val activeTool = (activePart as? UiPart.Tool)?.tool
    val previousCount = parts.size - if (activePart == null) 0 else 1
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
        if ((running || failed) && activeTool != null) {
            append(" · ")
            append(activeTool.name)
        }
        if (reasoning.isNotEmpty()) {
            if (tools.isEmpty()) append(" · reasoning") else append(" · thinking")
        }
    }
    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.sm))
                .background(Color.White.copy(alpha = 0.035f))
                .pressable { onToggle(groupKey) }
                .padding(horizontal = Space.md, vertical = Space.sm),
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
            Spacer(Modifier.width(Space.sm))
            Text(
                summary,
                style = PvpsType.eyebrow,
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
            Column(modifier = Modifier.padding(horizontal = Space.xs, vertical = Space.xxs)) {
                activePart?.let { part ->
                    Text(
                        if (running) "CURRENT STEP" else "LATEST STEP",
                        style = PvpsType.eyebrow,
                        color = if (running) Warning else TextFaint,
                        modifier = Modifier.padding(start = Space.xs, top = Space.sm, bottom = Space.xxs)
                    )
                    ActivityPartView(
                        part = part,
                        partKey = "$groupKey-$activeIndex",
                        expanded = true,
                        onToggle = { onToggle("$groupKey-$activeIndex") },
                        openCode = openCode,
                        onOpenSubagent = onOpenSubagent
                    )
                }
                if (previousCount > 0) {
                    TinyAction(
                        label = "Show previous · $previousCount",
                        modifier = Modifier.padding(start = Space.xs, top = Space.sm),
                        onClick = { showHistory = true }
                    )
                }
            }
        }
    }
    if (showHistory) {
        Dialog(
            onDismissRequest = { showHistory = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.block)
                    .heightIn(max = 680.dp),
                color = SurfaceHigh,
                shape = RoundedCornerShape(Radius.xl)
            ) {
                Column(modifier = Modifier.padding(Space.lg)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "ACTIVITY HISTORY · $previousCount",
                            style = PvpsType.eyebrow,
                            color = TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .pressable { showHistory = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close history",
                                tint = TextFaint,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.md))
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(top = Space.xxs),
                        verticalArrangement = Arrangement.spacedBy(Space.xs)
                    ) {
                        parts.forEachIndexed { index, part ->
                            if (index != activeIndex) {
                                ActivityPartView(
                                    part = part,
                                    partKey = "$groupKey-$index",
                                    expanded = "$groupKey-$index" in expanded,
                                    onToggle = { onToggle("$groupKey-$index") },
                                    openCode = openCode,
                                    onOpenSubagent = onOpenSubagent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityPartView(
    part: UiPart,
    partKey: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    openCode: OpenCodeRepository,
    onOpenSubagent: (String) -> Unit
) {
    when (part) {
        is UiPart.Reasoning -> ReasoningBlock(
            text = part.text,
            expanded = expanded,
            onToggle = onToggle
        )
        is UiPart.Tool -> ToolCard(
            tool = part.tool,
            expanded = expanded,
            onToggle = onToggle,
            openCode = openCode,
            onOpenSubagent = onOpenSubagent
        )
        is UiPart.Text -> MarkdownText(part.text)
    }
}

@Composable
private fun ReasoningBlock(text: String, expanded: Boolean, onToggle: () -> Unit) {
    if (text.isBlank()) return
    PvpsCard(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xs))
                    .pressable(onClick = onToggle)
                    .padding(vertical = Space.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "REASONING",
                    style = PvpsType.eyebrow,
                    color = TextFaint,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    if (expanded) "HIDE" else "SHOW",
                    style = PvpsType.eyebrow,
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
                Spacer(Modifier.height(Space.sm))
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
    PvpsCard(
        modifier = Modifier.fillMaxWidth().padding(top = Space.sm),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(modifier = Modifier.padding(horizontal = Space.md, vertical = Space.md)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.xs))
                    .pressable(onClick = onToggle)
                    .padding(vertical = Space.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    toolIcon(tool.name),
                    contentDescription = null,
                    tint = if (isSubagent) Violet else statusColor,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(Modifier.width(Space.sm))
                Text(
                    tool.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(Space.sm))
                StatusDot(statusColor, 6)
                Spacer(Modifier.width(Space.xs))
                Text(tool.status, style = PvpsType.eyebrow, color = statusColor)
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
                style = PvpsType.monoBody,
                color = TextSecondary,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Space.xs)
            )
            if (isSubagent && childSession != null) {
                Row(
                    modifier = Modifier
                        .padding(top = Space.sm)
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(Violet.copy(alpha = 0.10f))
                        .pressable { onOpenSubagent(childSession) }
                        .padding(horizontal = Space.sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("OPEN SUBAGENT", style = PvpsType.eyebrow, color = Violet)
                    Spacer(Modifier.width(Space.sm))
                    Text("→", color = Violet, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (expanded) {
                if (tool.input.isNotBlank()) {
                    Spacer(Modifier.height(Space.sm))
                    Text("INPUT", style = PvpsType.eyebrow, color = TextFaint)
                    Spacer(Modifier.height(Space.xs))
                    MonospaceBox(tool.input.take(1_600))
                }
                tool.content.forEach { content ->
                    when (content) {
                        is ToolContent.Text -> {
                            Spacer(Modifier.height(Space.sm))
                            Text("OUTPUT", style = PvpsType.eyebrow, color = TextFaint)
                            Spacer(Modifier.height(Space.xs))
                            MonospaceBox(content.text.take(2_400))
                        }
                        is ToolContent.File -> {
                            Spacer(Modifier.height(Space.sm))
                            if (content.mime.startsWith("image/")) {
                                RemoteMedia(uri = content.uri, openCode = openCode)
                            } else {
                                Text(
                                    "File: ${content.name ?: content.uri.take(60)} (${content.mime})",
                                    style = PvpsType.monoBody,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
                tool.error?.let {
                    Spacer(Modifier.height(Space.sm))
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
            style = PvpsType.monoBody,
            color = TextSecondary,
            modifier = Modifier.padding(top = Space.xs)
        )
    }
}

@Composable
private fun RemoteMedia(uri: String, openCode: OpenCodeRepository) {
    var zoom by remember { mutableStateOf(false) }
    val result by produceState<Pair<Boolean, ImageBitmap?>>(initialValue = false to null, uri) {
        val bitmap = withContext(Dispatchers.IO) {
            openCode.loadMedia(uri)?.let { data ->
                runCatching { decodeSampledBitmap(data) }.getOrNull()
            }
        }
        value = true to bitmap
    }
    when {
        !result.first -> Text(
            "Loading image…",
            style = PvpsType.monoBody,
            color = TextSecondary,
            modifier = Modifier.padding(top = Space.sm)
        )
        result.second != null -> {
            val bitmap = result.second!!
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Space.sm)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(DeepBackground.copy(alpha = 0.5f))
                    .pressable { zoom = true }
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = "Image attachment",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = DeepBackground.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        "TAP TO OPEN",
                        style = PvpsType.eyebrow,
                        color = TextSecondary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
            if (zoom) {
                Dialog(
                    onDismissRequest = { zoom = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.96f))
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Full image",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        )
                        IconButton(
                            onClick = { zoom = false },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .statusBarsPadding()
                                .padding(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close image",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
        else -> Text(
            "Image unavailable",
            style = PvpsType.monoBody,
            color = TextSecondary,
            modifier = Modifier.padding(top = Space.sm)
        )
    }
}

private fun decodeSampledBitmap(data: ByteArray, maxDimension: Int = 1600): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxDimension) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(data, 0, data.size, options)?.asImageBitmap()
}

@Composable
private fun PermissionCard(permission: PermissionRequest, onReply: (String) -> Unit) {
    PvpsCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.gutter, vertical = Space.xs),
        shape = RoundedCornerShape(Radius.md)
    ) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text("PERMISSION REQUESTED", style = PvpsType.eyebrow, color = Warning)
            Spacer(Modifier.height(Space.sm))
            Text(
                permission.message ?: permission.action,
                style = MaterialTheme.typography.bodyMedium,
                color = TextPrimary
            )
            if (permission.resources.isNotEmpty()) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    permission.resources.joinToString("\n"),
                    style = PvpsType.monoBody,
                    color = TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(Space.md))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Space.sm)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Accent)
                        .pressable { onReply("once") }
                        .padding(horizontal = Space.md, vertical = 8.dp)
                ) {
                    Text(
                        "Allow once".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Void,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Color.White.copy(alpha = 0.06f))
                        .pressable { onReply("always") }
                        .padding(horizontal = Space.md, vertical = 8.dp)
                ) {
                    Text(
                        "Always".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextPrimary,
                        maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(Danger.copy(alpha = 0.10f))
                        .pressable { onReply("reject") }
                        .padding(horizontal = Space.md, vertical = 8.dp)
                ) {
                    Text(
                        "Deny".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = Danger,
                        maxLines = 1
                    )
                }
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
    onAttachFiles: () -> Unit,
    onAttachPhotos: () -> Unit,
    onAttachCamera: () -> Unit,
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
    var showAttachMenu by remember { mutableStateOf(false) }
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
    // Compact row: provider stays hidden until the full model picker opens.
    val compactModelLabel = (model?.name?.takeIf { it.isNotBlank() } ?: modelLabel)
        .substringBefore(" · ")
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
        Column(modifier = Modifier.padding(horizontal = Space.gutter, vertical = Space.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.xs)
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TinyAction(compactModelLabel, Modifier.weight(1.15f)) {
                        keyboard?.hide()
                        pendingModel = null
                        panel = if (panel == ComposerPanel.Model) ComposerPanel.None else ComposerPanel.Model
                    }
                    if (efforts.isNotEmpty()) {
                        TinyAction(effortLabel, Modifier.weight(0.8f)) {
                            keyboard?.hide()
                            pendingModel = null
                            panel = ComposerPanel.Model
                        }
                    }
                    TinyAction(agentLabel, Modifier.weight(0.85f)) {
                        keyboard?.hide()
                        panel = if (panel == ComposerPanel.Agent) ComposerPanel.None else ComposerPanel.Agent
                    }
                }
                Spacer(Modifier.width(Space.xxs))
                StatusDot(
                    when {
                        sending -> Accent
                        busy -> Warning
                        connected -> Success
                        else -> TextFaint
                    },
                    6
                )
                Spacer(Modifier.width(Space.xs))
                Text(
                    when {
                        sending -> "SENDING"
                        busy -> "WORKING"
                        connected -> "READY"
                        else -> "OFFLINE"
                    },
                    style = PvpsType.eyebrow,
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
                Spacer(Modifier.height(Space.sm))
                if (attachments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        attachments.forEach { item ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.pill))
                                    .background(Color.White.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        start = if (item.mime.startsWith("image/")) Space.xs else Space.md,
                                        end = Space.xxs
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (item.mime.startsWith("image/")) {
                                        AsyncImage(
                                            model = item.uri,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(Radius.xs))
                                        )
                                        Spacer(Modifier.width(Space.sm))
                                    }
                                    Text(
                                        item.name,
                                        style = PvpsType.monoBody,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.widthIn(max = 160.dp)
                                    )
                                    Text(
                                        " · ${formatAttachmentSize(item.size)}",
                                        style = PvpsType.monoBody,
                                        color = TextFaint,
                                        maxLines = 1
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .pressable { onRemoveAttachment(item.uri) },
                                        contentAlignment = Alignment.Center
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
                        style = PvpsType.monoBody,
                        color = Warning,
                        modifier = Modifier.padding(top = Space.xs, start = Space.xxs)
                    )
                }
            }

            if (showCommands && filteredCommands.isNotEmpty()) {
                Spacer(Modifier.height(Space.sm))
                PvpsCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    LazyColumn(modifier = Modifier.heightIn(max = 164.dp)) {
                        items(filteredCommands, key = { "cmd-${it.name}" }) { command ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressable {
                                        onPickCommand(command)
                                    }
                                    .padding(horizontal = Space.md, vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "/${command.name}",
                                    style = PvpsType.monoBody,
                                    color = Accent,
                                    modifier = Modifier.widthIn(min = 72.dp)
                                )
                                Text(
                                    command.description.orEmpty(),
                                    style = PvpsType.monoBody,
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
                Spacer(Modifier.height(Space.sm))
                PvpsCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Column(modifier = Modifier.padding(Space.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (panel == ComposerPanel.Model) "MODEL & EFFORT" else "AGENT",
                                style = PvpsType.eyebrow,
                                color = TextFaint,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .pressable {
                                        panel = ComposerPanel.None
                                        pendingModel = null
                                    },
                                contentAlignment = Alignment.Center
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
                                        Text("Search models or providers", style = MaterialTheme.typography.bodySmall, color = TextFaint)
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
                                    shape = RoundedCornerShape(Radius.sm),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = Color.White.copy(alpha = 0.12f),
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
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
                                Spacer(Modifier.height(Space.xs))
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
                                    Spacer(Modifier.height(Space.sm))
                                    Text(
                                        "REASONING EFFORT",
                                        style = PvpsType.eyebrow,
                                        color = TextFaint
                                    )
                                    Spacer(Modifier.height(Space.xs))
                                    Row(
                                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(Space.xs)
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
                                                .clip(RoundedCornerShape(Radius.sm))
                                                .background(if (selected) Accent.copy(alpha = 0.09f) else Color.Transparent)
                                                .pressable {
                                                    onSelectAgent(item)
                                                    panel = ComposerPanel.None
                                                }
                                                .padding(horizontal = Space.sm, vertical = Space.sm),
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
                                                        style = PvpsType.monoBody,
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
                Spacer(Modifier.height(Space.sm))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 126.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    Text(
                        "PENDING · ${queued.size} · delivers at the next step boundary",
                        style = PvpsType.eyebrow,
                        color = Warning,
                        modifier = Modifier.padding(start = Space.xxs, bottom = Space.xxs)
                    )
                    queued.forEach { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Warning.copy(alpha = 0.07f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = Space.md, end = Space.xxs, top = Space.xxs, bottom = Space.xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("NEXT STEP", style = PvpsType.eyebrow, color = Warning)
                                Spacer(Modifier.width(Space.sm))
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
                                        style = PvpsType.monoBody,
                                        color = TextFaint,
                                        maxLines = 1
                                    )
                                }
                                if (!busy) {
                                    Text(
                                        "DELIVER NOW",
                                        style = PvpsType.eyebrow,
                                        color = Accent,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(Radius.xs))
                                            .pressable { onSendQueuedNow(item) }
                                            .padding(horizontal = Space.sm, vertical = 6.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .pressable { onCancelQueued(item.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Cancel queued message",
                                        tint = TextFaint,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(Space.sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.045f))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                    .padding(Space.xs),
                verticalAlignment = Alignment.Bottom
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                            .pressable(enabled = connected) { showAttachMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AttachFile,
                            contentDescription = "Attach photos or files",
                            tint = if (connected) TextSecondary else TextFaint,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Photos", color = TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Image, contentDescription = null, tint = Accent)
                            },
                            onClick = {
                                showAttachMenu = false
                                onAttachPhotos()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Camera", color = TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Warning)
                            },
                            onClick = {
                                showAttachMenu = false
                                onAttachCamera()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Files", color = TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = TextSecondary)
                            },
                            onClick = {
                                showAttachMenu = false
                                onAttachFiles()
                            }
                        )
                    }
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = Space.sm, vertical = 10.dp),
                    enabled = connected,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (connected && !sending &&
                                (value.isNotBlank() || attachments.isNotEmpty())
                            ) onSend()
                        }
                    ),
                    maxLines = 6,
                    cursorBrush = SolidColor(Accent),
                    decorationBox = { innerTextField ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    if (busy) "Message for the next step…" else "Message OpenCode…",
                                    color = TextFaint,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            innerTextField()
                        }
                    }
                )
                if (busy) {
                    CircleAction(
                        icon = Icons.Default.Stop,
                        description = "Stop",
                        background = Danger.copy(alpha = 0.12f),
                        tint = Danger,
                        onClick = onStop
                    )
                    Spacer(Modifier.width(Space.xs))
                }
                val sendEnabled = connected && !sending &&
                    (value.isNotBlank() || attachments.isNotEmpty())
                CircleAction(
                    icon = Icons.Default.Send,
                    description = if (busy) "Queue message" else "Send",
                    background = if (sendEnabled) Accent else Color.White.copy(alpha = 0.06f),
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
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (selected) Accent.copy(alpha = 0.09f) else Color.Transparent)
            .pressable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.sm),
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
                Spacer(Modifier.width(Space.sm))
                Text(
                    model.providerID,
                    style = PvpsType.monoBody,
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
                style = PvpsType.monoBody,
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) Accent else Color.White.copy(alpha = 0.05f))
            .pressable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 7.dp)
    ) {
        Text(
            label.uppercase(),
            style = PvpsType.eyebrow,
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
            .size(40.dp)
            .clip(CircleShape)
            .background(background)
            .pressable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun FormWizard(
    form: FormInfo,
    onSubmit: (Map<String, JsonElement>) -> Unit,
    onCancel: () -> Unit
) {
    var answers by remember(form.id) { mutableStateOf(defaultFormAnswers(form.fields)) }
    var extra by remember(form.id) { mutableStateOf<Map<String, String>>(emptyMap()) }
    var step by remember(form.id) { mutableIntStateOf(0) }
    var error by remember(form.id) { mutableStateOf<String?>(null) }
    val visible = form.fields.filter { fieldVisible(it, answers) }
    val total = visible.size
    LaunchedEffect(total) {
        if (step > total - 1) step = (total - 1).coerceAtLeast(0)
    }
    val field = visible.getOrNull(step)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("OPENCODE ASKS", style = MaterialTheme.typography.labelSmall, color = Violet)
                Text(
                    form.title.ifBlank { "Question" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontFamily = dev.pvps.ui.theme.DisplayFont
                )
            }
            TextButton(onClick = onCancel) { Text("Skip", color = TextFaint) }
        }
        if (total > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "QUESTION ${step + 1} OF $total",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${((step + 1) * 100) / total}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent
                )
            }
            LinearProgressIndicator(
                progress = { (step + 1).toFloat() / total },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)),
                color = Violet,
                trackColor = OutlineDark
            )
        }

        if (field == null) {
            Text("No questions to answer.", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = { onSubmit(emptyMap()) }) { Text("Submit") }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = SurfaceRaised.copy(alpha = 0.52f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Violet.copy(alpha = 0.24f))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    FormLabel(field)
                    FormFieldInput(
                        field = field,
                        answers = answers,
                        extra = extra,
                        onAnswers = { answers = it },
                        onExtra = { extra = it }
                    )
                }
            }
            error?.let {
                Text(it, color = Danger, style = MaterialTheme.typography.bodySmall)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (step > 0) {
                    TextButton(onClick = {
                        error = null
                        step--
                    }) { Text("← Back", color = TextSecondary) }
                }
                Spacer(Modifier.weight(1f))
                val last = step >= total - 1
                Button(onClick = {
                    val problem = validateFormField(field, answers, extra)
                    if (problem != null) {
                        error = problem
                    } else {
                        error = null
                        if (last) {
                            onSubmit(buildFormPayload(visible, answers, extra))
                        } else {
                            step++
                        }
                    }
                }) {
                    Text(if (last) "Submit answer" else "Next →")
                }
            }
        }
    }
}

@Composable
private fun FormFieldInput(
    field: FormField,
    answers: Map<String, JsonElement>,
    extra: Map<String, String>,
    onAnswers: (Map<String, JsonElement>) -> Unit,
    onExtra: (Map<String, String>) -> Unit
) {
    val answer = answers[field.key]
    when (field.type) {
        "boolean" -> {
            val current = (answer as? JsonPrimitive)?.booleanOrNull ?: false
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormChoice(
                    option = FormOption("true", "Yes"),
                    active = current,
                    onClick = { onAnswers(answers + (field.key to JsonPrimitive(true))) }
                )
                FormChoice(
                    option = FormOption("false", "No"),
                    active = !current,
                    onClick = { onAnswers(answers + (field.key to JsonPrimitive(false))) }
                )
            }
        }
        "multiselect" -> {
            val selected = (answer as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                ?: emptyList()
            field.options.forEach { option ->
                val active = option.value in selected
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (active) Violet.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable {
                            val next = if (active) selected - option.value else selected + option.value
                            onAnswers(answers + (field.key to JsonArray(next.map { JsonPrimitive(it) })))
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Checkbox(checked = active, onCheckedChange = null)
                    Column {
                        Text(
                            option.label.ifBlank { option.value },
                            color = TextPrimary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        option.description?.takeIf { it.isNotBlank() }?.let {
                            Text(it, color = TextFaint, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            if (field.custom) {
                CustomAnswerField(
                    value = extra[field.key].orEmpty(),
                    placeholder = "Type your own answer",
                    onValueChange = { onExtra(extra + (field.key to it)) }
                )
                val custom = extra[field.key].orEmpty().trim()
                if (custom.isNotEmpty() && custom !in selected) {
                    TextButton(onClick = {
                        onAnswers(
                            answers + (field.key to JsonArray((selected + custom).map { JsonPrimitive(it) }))
                        )
                        onExtra(extra + (field.key to ""))
                    }) { Text("Add “$custom”", color = Accent) }
                }
            }
        }
        "external" -> {
            Text(
                field.url.orEmpty().ifBlank { "External field" },
                color = Accent,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        else -> {
            val current = (answer as? JsonPrimitive)?.contentOrNull.orEmpty()
            val known = field.options.any { it.value == current }
            field.options.forEach { option ->
                FormChoice(
                    option = option,
                    active = current == option.value,
                    onClick = { onAnswers(answers + (field.key to JsonPrimitive(option.value))) }
                )
            }
            if (field.options.isEmpty() || field.custom) {
                CustomAnswerField(
                    value = if (field.options.isNotEmpty() && known) "" else current,
                    placeholder = when {
                        field.custom && field.options.isNotEmpty() -> "Or type your own answer"
                        else -> field.placeholder ?: field.description ?: "Type your answer"
                    },
                    numeric = field.type == "number" || field.type == "integer",
                    onValueChange = { onAnswers(answers + (field.key to JsonPrimitive(it))) }
                )
            }
        }
    }
}

@Composable
private fun CustomAnswerField(
    value: String,
    placeholder: String,
    numeric: Boolean = false,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(placeholder, color = TextFaint) },
        singleLine = true,
        keyboardOptions = if (numeric) {
            KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
        } else KeyboardOptions.Default,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Violet.copy(alpha = 0.62f),
            unfocusedBorderColor = OutlineDark,
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary,
            cursorColor = Violet
        )
    )
}

private fun validateFormField(
    field: FormField,
    answers: Map<String, JsonElement>,
    extra: Map<String, String>
): String? {
    val value = answers[field.key]
    val raw = (value as? JsonPrimitive)?.contentOrNull.orEmpty()
    val custom = extra[field.key].orEmpty().trim()
    if (field.type == "boolean" || field.type == "external") return null
    if (field.type == "multiselect") {
        val count = (value as? JsonArray)?.size ?: 0
        val totalCount = count + (if (custom.isBlank()) 0 else 1)
        if (field.required && totalCount == 0) return "Select at least one option"
        if (field.minItems != null && totalCount < field.minItems) {
            return "Select at least ${field.minItems} options"
        }
        if (field.maxItems != null && totalCount > field.maxItems) {
            return "Select at most ${field.maxItems} options"
        }
        return null
    }
    if (field.required && raw.isBlank()) return "This field is required"
    if (raw.isNotBlank()) {
        if (field.minLength != null && raw.length < field.minLength) {
            return "Use at least ${field.minLength} characters"
        }
        if (field.maxLength != null && raw.length > field.maxLength) {
            return "Use at most ${field.maxLength} characters"
        }
        if (field.type == "number" && raw.toDoubleOrNull() == null) return "Enter a valid number"
        if (field.type == "integer" && raw.toLongOrNull() == null) return "Enter a whole number"
        if (field.pattern != null) {
            val matches = runCatching { Regex(field.pattern).matches(raw) }.getOrDefault(true)
            if (!matches) return "The answer does not match the required format"
        }
    }
    return null
}

private fun buildFormPayload(
    fields: List<FormField>,
    answers: Map<String, JsonElement>,
    extra: Map<String, String>
): Map<String, JsonElement> = fields.mapNotNull { field ->
    if (field.type == "external") return@mapNotNull null
    val value = answers[field.key] ?: return@mapNotNull null
    val raw = (value as? JsonPrimitive)?.contentOrNull
    if (field.type == "multiselect") {
        val items = ((value as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
            ?: emptyList()).toMutableList()
        extra[field.key].orEmpty().trim().takeIf { it.isNotBlank() }?.let {
            if (it !in items) items += it
        }
        field.key to JsonArray(items.map { JsonPrimitive(it) })
    } else {
        if (value is JsonPrimitive && raw.isNullOrBlank()) return@mapNotNull null
        val typed: JsonElement = when (field.type) {
            "number" -> raw?.toDoubleOrNull()?.let { JsonPrimitive(it) } ?: value
            "integer" -> raw?.toLongOrNull()?.let { JsonPrimitive(it) } ?: value
            else -> value
        }
        field.key to typed
    }
}.toMap()

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
    projects: List<dev.pvps.opencode.ProjectInfo>,
    activeSession: SessionInfo?,
    activeId: String?,
    onSelect: (String) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var showAll by remember { mutableStateOf(false) }
    val projectByID = remember(projects) { projects.associateBy { it.id } }
    fun projectDirectory(session: SessionInfo): String =
        session.location?.directory?.takeIf { it.isNotBlank() }
            ?: projectByID[session.projectID]?.canonical
            ?: "global"
    fun projectName(session: SessionInfo): String {
        val project = projectByID[session.projectID]
        return project?.displayName
            ?: projectDirectory(session).trimEnd('/').substringAfterLast('/').ifBlank { "Global" }
    }
    val currentDirectory = activeSession?.let { projectDirectory(it) }
    val scoped = remember(sessions, showAll, currentDirectory) {
        if (showAll || currentDirectory == null) sessions
        else sessions.filter { projectDirectory(it) == currentDirectory }
    }
    val filtered = remember(scoped, query) {
        val needle = query.trim().lowercase()
        if (needle.isBlank()) scoped else scoped.filter {
            it.displayTitle.lowercase().contains(needle) ||
                (it.agent ?: "").contains(needle, true) ||
                projectName(it).lowercase().contains(needle)
        }
    }
    val grouped = remember(filtered, projectByID) {
        filtered.groupBy { projectDirectory(it) }
            .toList()
            .sortedByDescending { (_, list) -> list.maxOfOrNull { it.time.updated } ?: 0L }
    }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(Space.sm),
            color = DeepBackground,
            shape = RoundedCornerShape(Radius.xl)
        ) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Sessions", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text(
                            "${sessions.size} sessions · ${sessions.count { it.isRunning }} running",
                            style = PvpsType.monoBody,
                            color = TextSecondary
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(Accent)
                            .pressable(onClick = onNew)
                            .padding(horizontal = Space.md, vertical = 7.dp)
                    ) {
                        Text(
                            "NEW",
                            style = PvpsType.eyebrow,
                            color = Void
                        )
                    }
                    Spacer(Modifier.width(Space.sm))
                    GlassIconButton(onClick = onDismiss, size = 36.dp) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
                    placeholder = { Text("Search sessions or projects", color = TextFaint, style = PvpsType.monoBody) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TextFaint) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.sm),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White.copy(alpha = 0.14f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.08f),
                        focusedContainerColor = Color.White.copy(alpha = 0.045f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.045f),
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Accent
                    )
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = Space.gutter, vertical = Space.sm),
                    horizontalArrangement = Arrangement.spacedBy(Space.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ComposerChoice(
                        label = activeSession?.let { projectName(it) } ?: "Current project",
                        selected = !showAll,
                        onClick = { showAll = false }
                    )
                    ComposerChoice(
                        label = "All projects",
                        selected = showAll,
                        onClick = { showAll = true }
                    )
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = Space.gutter, end = Space.gutter, bottom = Space.xxl
                    ),
                    verticalArrangement = Arrangement.spacedBy(Space.xs)
                ) {
                    grouped.forEach { (directory, list) ->
                        val label = projectByID[list.first().projectID]?.displayName
                            ?: directory.trimEnd('/').substringAfterLast('/').ifBlank { "Global" }
                        item(key = "project-$directory") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(start = Space.xs, top = Space.sm, bottom = Space.xxs),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    tint = Accent.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(Space.xs))
                                Text(
                                    label.uppercase(),
                                    style = PvpsType.eyebrow,
                                    color = TextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    "${list.size}",
                                    style = PvpsType.eyebrow,
                                    color = TextFaint
                                )
                            }
                        }
                        items(list, key = { it.id }) { session ->
                            SessionRow(
                                session = session,
                                active = session.id == activeId,
                                onClick = { onSelect(session.id) }
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
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Radius.md)
    Surface(
        color = when {
            active -> Accent.copy(alpha = 0.075f)
            else -> Color.White.copy(alpha = 0.035f)
        },
        shape = shape,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .pressable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(
                        when {
                            session.isRunning -> Warning.copy(alpha = 0.13f)
                            active -> Accent.copy(alpha = 0.13f)
                            else -> Color.White.copy(alpha = 0.06f)
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
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(
                    session.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (active) TextPrimary else TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(Space.xxs))
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
                    style = PvpsType.monoBody,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (session.isRunning) {
                Text(
                    "LIVE",
                    style = PvpsType.eyebrow,
                    color = Warning,
                    modifier = Modifier.padding(horizontal = Space.sm)
                )
            } else if (active) {
                Text("●", color = Accent, style = PvpsType.eyebrow)
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
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize().padding(Space.sm),
            color = DeepBackground,
            shape = RoundedCornerShape(Radius.xl)
        ) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Telemetry", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                        Text(
                            "Live session · context · spend · usage",
                            style = PvpsType.monoBody,
                            color = TextFaint
                        )
                    }
                    GlassIconButton(onClick = onDismiss, size = 36.dp) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close telemetry",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.gutter, vertical = Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.md)
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
                    style = PvpsType.monoBody,
                    color = TextSecondary
                )
                parent?.let { p ->
                    TextButton(onClick = { onOpenParent(p) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text("← Back to ${p.displayTitle}", color = Violet)
                    }
                }

                if (contextFraction != null) {
                    Text("CONTEXT WINDOW", style = PvpsType.eyebrow, color = TextFaint)
                    MeterBar(
                        progress = contextFraction.toFloat(),
                        accent = when {
                            contextFraction > 0.85 -> Danger
                            contextFraction > 0.65 -> Warning
                            else -> Accent
                        }
                    )
                    Text(
                        "${contextUsed?.let { compactTokens(it) } ?: "—"} / ${compactTokens(contextLimit)} · ${(contextFraction * 100).toInt()}%",
                        style = PvpsType.monoBody,
                        color = TextSecondary
                    )
                    Text(
                        "Measured from the live session context and the latest assistant usage. Refreshes after each step and when the SSE stream resyncs.",
                        style = MaterialTheme.typography.bodySmall,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = TextFaint
                )
                usage?.go?.let { go ->
                    Text("OPENCODE GO ALLOWANCE", style = PvpsType.eyebrow, color = TextFaint)
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        StatBox("ROLLING", "${go.rolling.percent.toInt()}%", Modifier.weight(1f))
                        StatBox("WEEKLY", "${go.weekly.percent.toInt()}%", Modifier.weight(1f))
                        StatBox("MONTHLY", "${go.monthly.percent.toInt()}%", Modifier.weight(1f))
                    }
                }
                if (usage != null && usage.budgets.isNotEmpty()) {
                    Text("REPORTED BUDGETS", style = PvpsType.eyebrow, color = TextFaint)
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
                    Text("SUBAGENTS", style = PvpsType.eyebrow, color = TextFaint)
                    subagents
                        .sortedWith(compareByDescending<SessionInfo> { it.isRunning }
                            .thenByDescending { it.time.updated })
                        .forEach { sub ->
                        Surface(
                            color = Violet.copy(alpha = 0.06f),
                            shape = RoundedCornerShape(Radius.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .pressable { onOpenSubagent(sub.id) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                StatusDot(if (sub.isRunning) Warning else Violet, 6)
                                Spacer(Modifier.width(Space.sm))
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
                                        style = PvpsType.monoBody,
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
                        style = PvpsType.monoBody,
                        color = TextFaint
                    )
                }
                Spacer(Modifier.height(Space.xxl))
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(
    cost: Double,
    estimated: Boolean,
    totalTokens: TokenUsage,
    tps: Double?,
    messages: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatBox(if (estimated) "SPENT · EST" else "SPENT", formatCost(cost), Modifier.weight(1f))
            StatBox("TOK/S", tps?.let { String.format(Locale.US, "%.1f", it) } ?: "—", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatBox("INPUT", compactTokens(totalTokens.input), Modifier.weight(1f))
            StatBox("OUTPUT", compactTokens(totalTokens.output + totalTokens.reasoning), Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            StatBox("CACHE", compactTokens(totalTokens.cache.read + totalTokens.cache.write), Modifier.weight(1f))
            StatBox("MESSAGES", messages.toString(), Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    PvpsCard(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.sm)
    ) {
        Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
            Text(label, style = PvpsType.eyebrow, color = TextFaint)
            Spacer(Modifier.height(Space.xxs))
            Text(value, style = PvpsType.numeric, color = TextPrimary, maxLines = 1)
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

/**
 * Picker results must be content:// documents. The camera path is the only
 * caller allowed to read PVPS's own unexported FileProvider URI, because that
 * URI is created by this app rather than returned by an external picker.
 */
private fun requireReadableAttachmentUri(context: Context, uri: Uri, allowOwnProvider: Boolean) {
    val ownFileProvider = "${context.packageName}.files"
    if (allowOwnProvider &&
        uri.scheme == ContentResolver.SCHEME_CONTENT &&
        uri.authority == ownFileProvider
    ) {
        return
    }
    SecuritySupport.requirePickerReadUri(context, uri)
}

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
    val bytes = resolver.openInputStream(uri)?.use { input ->
        SecuritySupport.readLimitedBytes(input, MAX_ATTACHMENT_BYTES)
    } ?: error("Could not open $name")
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
