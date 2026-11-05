package dev.pvps.opencode

import android.util.Base64
import android.util.Log
import android.util.LruCache
import dev.pvps.data.model.AuditEntry
import dev.pvps.data.model.Host
import dev.pvps.data.store.AuditStore
import dev.pvps.data.store.SettingsStore
import dev.pvps.service.AppNotifications
import dev.pvps.ssh.SshManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put

class OpenCodeRepository(
    private val ssh: SshManager,
    private val audit: AuditStore,
    private val settings: SettingsStore
) {
    data class QueuedPrompt(
        val id: String,
        val text: String,
        val created: Long,
        val delivery: String = "queue",
        val files: Int = 0
    )

    data class State(
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val hostName: String = "",
        val serverVersion: String = "",
        val sessions: List<SessionInfo> = emptyList(),
        val activeSessionId: String? = null,
        val messages: List<UiMessage> = emptyList(),
        val queued: List<QueuedPrompt> = emptyList(),
        val permissions: List<PermissionRequest> = emptyList(),
        val models: List<ModelInfo> = emptyList(),
        val agents: List<AgentInfo> = emptyList(),
        val commands: List<CommandInfo> = emptyList(),
        val forms: List<FormInfo> = emptyList(),
        val usage: UsageLimits? = null,
        val realCost: Double? = null,
        val busy: Boolean = false,
        val error: String? = null
    ) {
        val activeSession: SessionInfo?
            get() = sessions.firstOrNull { it.id == activeSessionId }

        val rootSessions: List<SessionInfo>
            get() = sessions.filter { it.parentID == null }

        val subagents: List<SessionInfo>
            get() = sessions.filter { it.parentID != null && it.parentID == activeSessionId }

        val parentSession: SessionInfo?
            get() = activeSession?.parentID?.let { parentId ->
                sessions.firstOrNull { it.id == parentId }
            }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var client: OpenCodeClient? = null
    private var host: Host? = null
    private var eventsJob: Job? = null
    private var refreshJob: Job? = null
    private var queueJob: Job? = null
    private var mediaCache = LruCache<String, ByteArray>(24)
    private val optimisticIds = mutableSetOf<String>()
    private val optimisticLock = Any()
    private var lastNotifiedMessageId: String? = null
    private var lastNotifiedFormId: String? = null

    suspend fun connect(host: Host) {
        if (this.host?.id == host.id && _state.value.connected) return
        disconnect()
        this.host = host
        _state.value = State(connecting = true, hostName = host.name)
        if (host.openCodePassword.isBlank()) {
            _state.value = _state.value.copy(
                connecting = false,
                error = "OpenCode password is empty. Edit the host and set it."
            )
            return
        }
        try {
            val baseUrl = ssh.openCodeBaseUrl(host)
            val newClient = OpenCodeClient(baseUrl, host.openCodeUsername, host.openCodePassword)
            val info = newClient.info()
            if (!info.version.startsWith("2.")) {
                newClient.close()
                _state.value = _state.value.copy(
                    connecting = false,
                    error = "OpenCode ${info.version} is not v2. This app requires v2.x."
                )
                return
            }
            client = newClient
            val sessions = newClient.listSessions()
            val models = runCatching { newClient.models(host.openCodeDirectory) }.getOrDefault(emptyList())
            val agents = runCatching { newClient.agents(host.openCodeDirectory) }.getOrDefault(emptyList())
            val commands = runCatching { newClient.commands(host.openCodeDirectory) }.getOrDefault(emptyList())
            val usage = runCatching { newClient.usageLimits(host.openCodeDirectory) }.getOrNull()
            _state.value = _state.value.copy(
                connecting = false,
                connected = true,
                serverVersion = info.version,
                sessions = sessions,
                models = models,
                agents = agents,
                commands = withBuiltinCommands(commands),
                usage = usage,
                error = null
            )
            val active = sessions.firstOrNull { it.parentID == null }
            if (active != null) selectSession(active.id)
            startEvents()
            startQueueDrain()
            audit.add(
                AuditEntry(
                    hostName = host.name,
                    action = "OpenCode connect",
                    detail = "v${info.version} at $baseUrl",
                    success = true
                )
            )
        } catch (e: Exception) {
            client?.close()
            client = null
            _state.value = _state.value.copy(
                connecting = false,
                connected = false,
                error = e.message ?: "Could not connect to OpenCode"
            )
        }
    }

    fun disconnect() {
        eventsJob?.cancel()
        refreshJob?.cancel()
        queueJob?.cancel()
        eventsJob = null
        refreshJob = null
        queueJob = null
        client?.close()
        client = null
        host = null
        mediaCache = LruCache(24)
        synchronized(optimisticLock) { optimisticIds.clear() }
        _state.value = State()
    }

    suspend fun refreshSessions() {
        val c = client ?: return
        runCatching {
            val sessions = c.listSessions()
            val active = _state.value.activeSessionId
            val activeStillExists = sessions.any { it.id == active }
            val fallback = sessions.firstOrNull { it.parentID == null }?.id
            _state.value = _state.value.copy(
                sessions = sessions,
                activeSessionId = if (activeStillExists) active else fallback,
                busy = sessions.firstOrNull { it.id == active }?.isRunning == true
            )
            if (!activeStillExists) {
                _state.value.activeSessionId?.let { selectSession(it) }
            }
        }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    suspend fun selectSession(sessionId: String) {
        synchronized(optimisticLock) { optimisticIds.clear() }
        _state.value = _state.value.copy(
            activeSessionId = sessionId,
            messages = emptyList(),
            queued = emptyList(),
            permissions = emptyList(),
            forms = emptyList(),
            realCost = null,
            busy = _state.value.sessions.firstOrNull { it.id == sessionId }?.isRunning == true
        )
        refreshMessages()
        refreshInbox()
        refreshPermissions()
        refreshForms()
        scope.launch { refreshRealCost(sessionId) }
        // A queue left behind by a previous app process must not stay parked
        // when the session is already idle.
        if (_state.value.activeSession?.isRunning != true) promoteQueued()
    }

    suspend fun newSession() {
        val c = client ?: return
        val h = host ?: return
        runCatching {
            val session = c.createSession(h.openCodeDirectory.takeIf { it.isNotBlank() })
            val saved = settings.settings.value
            if (saved.lastModelId.isNotBlank() && saved.lastModelProvider.isNotBlank()) {
                runCatching {
                    c.switchModel(
                        session.id,
                        ModelRef(
                            id = saved.lastModelId,
                            providerID = saved.lastModelProvider,
                            variant = saved.lastModelVariant.takeIf { it.isNotBlank() }
                        )
                    )
                    if (saved.lastAgentId.isNotBlank()) c.switchAgent(session.id, saved.lastAgentId)
                }
            }
            refreshSessions()
            selectSession(session.id)
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Could not create session")
        }
    }

    suspend fun refreshMessages() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            val server = c.messages(id).map { it.toUi() }
            if (_state.value.activeSessionId != id) return@runCatching
            val serverIds = server.mapTo(mutableSetOf()) { it.id }
            val pending = synchronized(optimisticLock) {
                val localMessages = _state.value.messages
                val serverUsers = server.filter { it.isUser }
                optimisticIds.removeAll { pendingId ->
                    if (pendingId in serverIds) {
                        true
                    } else {
                        // Some server versions project the prompt under a new id.
                        // Match the echoed text within a short window so the
                        // optimistic row does not stay duplicated forever.
                        val local = localMessages.firstOrNull { it.id == pendingId }
                        local != null && serverUsers.any { remote ->
                            remote.text.trim() == local.text.trim() &&
                                kotlin.math.abs(remote.created - local.created) < 120_000
                        }
                    }
                }
                localMessages.filter { it.id in optimisticIds }
            }
            _state.value = _state.value.copy(
                messages = (server + pending)
                    .distinctBy { it.id }
                    .sortedBy { it.created }
            )
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Could not load messages")
        }
    }

    suspend fun refreshInbox() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            val queued = c.inbox(id)
                .filter {
                    it.type == "user" &&
                        (it.payload.text.isNotBlank() || it.payload.files.isNotEmpty())
                }
                .map {
                    QueuedPrompt(
                        id = it.id,
                        text = it.payload.text,
                        created = it.time.created,
                        delivery = it.delivery,
                        files = it.payload.files.size
                    )
                }
            if (_state.value.activeSessionId == id) {
                _state.value = _state.value.copy(queued = queued)
            }
        }.onFailure {
            // The inbox is optional while a session starts; keep local queue on failure.
        }
    }

    suspend fun refreshPermissions() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            _state.value = _state.value.copy(permissions = c.permissions(id))
        }.onFailure {
            // Permission endpoints can transiently fail while a session is moving.
        }
    }

    suspend fun refreshForms() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            val forms = c.forms(id)
            if (_state.value.activeSessionId == id) {
                _state.value = _state.value.copy(forms = forms)
            }
        }.onFailure {
            // Forms are optional while a session is starting.
        }
    }

    suspend fun refreshUsage() {
        val c = client ?: return
        runCatching {
            val usage = c.usageLimits(host?.openCodeDirectory)
            _state.value = _state.value.copy(usage = usage)
        }.onFailure {
            // Usage is a nice-to-have; never surface it as a chat error.
        }
    }

    private suspend fun refreshRealCost(sessionId: String) {
        val c = client ?: return
        val session = _state.value.sessions.firstOrNull { it.id == sessionId } ?: return
        val now = System.currentTimeMillis()
        val since = session.time.created.takeIf { it > 0 }?.minus(60_000)
            ?: (now - 7 * 24 * 60 * 60 * 1000L)
        val until = now + 60_000
        val cost = runCatching { c.consoleSessionCost(sessionId, since, until) }.getOrNull()
            ?: return
        if (_state.value.activeSessionId == sessionId) {
            _state.value = _state.value.copy(realCost = cost)
        }
    }

    suspend fun replyForm(form: FormInfo, answer: Map<String, JsonElement>) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.replyForm(id, form.id, JsonObject(answer))
            refreshForms()
            refreshMessages()
            scheduleRefresh()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Could not submit answer")
        }
    }

    suspend fun cancelForm(form: FormInfo, message: String? = null) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.cancelForm(id, form.id, message)
            refreshForms()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Could not cancel form")
        }
    }

    suspend fun refreshModelsAndAgents() {
        val c = client ?: return
        val directory = host?.openCodeDirectory
        runCatching {
            val models = c.models(directory)
            val agents = c.agents(directory)
            val commands = c.commands(directory)
            _state.value = _state.value.copy(
                models = models,
                agents = agents,
                commands = withBuiltinCommands(commands)
            )
        }.onFailure {
            _state.value = _state.value.copy(error = it.message)
        }
    }

    private fun withBuiltinCommands(commands: List<CommandInfo>): List<CommandInfo> {
        // /compact is a dedicated API in v2, not always present in /api/command.
        // Keep /init available even if the project command list fails to load.
        val all = commands + listOf(
            CommandInfo("compact", "Compact the active context"),
            CommandInfo("init", "Initialize project instructions")
        )
        return all.distinctBy { it.name }.sortedBy { it.name }
    }

    suspend fun send(text: String, files: List<PromptFileInput> = emptyList()): Boolean {
        val prompt = text.trim().ifBlank {
            if (files.isEmpty()) return false else "Please review the attached file(s)."
        }
        val c = client ?: return false
        val id = _state.value.activeSessionId ?: run {
            newSession()
            _state.value.activeSessionId
        } ?: return false

        _state.value = _state.value.copy(error = null, busy = true)
        Log.i("PVPSQueue", "send session=$id files=${files.size} queued=${_state.value.queued.size}")

        return runCatching {
            // Always use queue delivery. The server starts the loop immediately
            // when the session is idle and parks the prompt when a turn is
            // running. Using "steer" here aborts an in-flight turn, and choosing
            // between the two from a cached busy flag caused prompts to be lost
            // whenever that flag was stale.
            val ack = c.prompt(id, prompt, delivery = "queue", files = files)
            val created = ack.time.created.takeIf { it > 0 } ?: System.currentTimeMillis()
            val ackId = ack.id.ifBlank { "local-${System.currentTimeMillis()}" }
            Log.i("PVPSQueue", "ack id=$ackId delivery=${ack.delivery} files=${ack.payload.files.size}")
            addOptimisticUserMessage(ackId, created, prompt, ack.payload.files)
            scheduleRefresh()
            // If the app had a stale "busy" flag, the queue drain can think the
            // turn is still running and never promote this item. Re-check the
            // session shortly after admission and force delivery when idle.
            scope.launch {
                delay(1_500)
                runCatching {
                    refreshSessions()
                    refreshInbox()
                    if (_state.value.activeSession?.isRunning != true) promoteQueued()
                }.onFailure { Log.w("PVPSQueue", "post-send promote failed: ${it.message}") }
            }
            true
        }.getOrElse {
            _state.value = _state.value.copy(
                busy = false,
                error = it.message ?: "Prompt failed"
            )
            false
        }
    }

    suspend fun runCommand(
        name: String,
        text: String,
        files: List<PromptFileInput> = emptyList()
    ): Boolean {
        val command = name.trim().removePrefix("/").trim()
        val c = client ?: return false
        val id = _state.value.activeSessionId ?: run {
            newSession()
            _state.value.activeSessionId
        } ?: return false
        _state.value = _state.value.copy(error = null)
        return runCatching {
            if (command == "compact") {
                c.compact(id)
            } else {
                c.runCommand(id, command, text.trim(), delivery = "queue", files = files)
                addOptimisticUserMessage(
                    "local-${System.currentTimeMillis()}",
                    System.currentTimeMillis(),
                    "/$command${text.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}",
                    emptyList()
                )
            }
            scheduleRefresh()
            true
        }.getOrElse {
            _state.value = _state.value.copy(error = it.message ?: "Command failed")
            false
        }
    }

    private fun addOptimisticUserMessage(
        ackId: String,
        created: Long,
        text: String,
        files: List<FileAttachment>
    ) {
        val optimistic = UiMessage(
            id = ackId,
            type = "user",
            created = created,
            text = text,
            files = files,
            parts = emptyList(),
            agent = null,
            model = null,
            cost = 0.0,
            tokens = null,
            finish = null,
            error = null,
            command = null,
            status = null,
            output = null,
            outcome = null
        )
        synchronized(optimisticLock) {
            optimisticIds += ackId
            _state.value = _state.value.copy(
                messages = (_state.value.messages + optimistic)
                    .distinctBy { it.id }
                    .sortedBy { it.created }
            )
        }
    }

    suspend fun compact() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.compact(id)
            scheduleRefresh()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Could not compact context")
        }
    }

    /**
     * A queued prompt normally sits in the server inbox until the running turn
     * ends. The server usually delivers it automatically, but a stale busy
     * flag or a dropped SSE connection can leave it parked forever. On every
     * idle boundary (and while anything is queued) promote pending items to
     * "steer", which explicitly wakes the agent loop. If the PATCH fails, leave
     * the item parked and retry on the next drain tick.
     */
    private suspend fun promoteQueued() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        refreshSessions()
        if (_state.value.sessions.firstOrNull { it.id == id }?.isRunning == true) return
        refreshInbox()
        val pending = _state.value.queued
        if (pending.isEmpty()) return
        Log.i("PVPSQueue", "promote ${pending.size} pending inbox item(s)")
        pending.forEach { item ->
            val promoted = runCatching { c.updateInboxDelivery(id, item.id, "steer") }
                .onFailure { Log.w("PVPSQueue", "promote ${item.id} failed: ${it.message}") }
                .isSuccess
            Log.i("PVPSQueue", "promote ${item.id} (${item.delivery}) -> steer success=$promoted")
            // If the PATCH failed, leave the item in place and retry on the
            // next 3s drain tick. Re-sending here risks duplicate prompts when
            // the server actually accepted the original admission.
        }
        refreshInbox()
        scheduleRefresh()
    }

    private fun startQueueDrain() {
        if (queueJob?.isActive == true) return
        queueJob = scope.launch {
            while (isActive) {
                delay(3_000)
                if (client == null || !_state.value.connected) continue
                val before = _state.value
                // Poll even when the local queue list is empty: a missed idle
                // event used to leave busy=true forever and hide the fact that
                // the server had already finished the turn.
                if (before.busy || before.queued.isNotEmpty()) {
                    runCatching {
                        refreshSessions()
                        refreshInbox()
                    }.onFailure { Log.w("PVPSQueue", "poll failed: ${it.message}") }
                }
                val after = _state.value
                if (after.queued.isNotEmpty() &&
                    after.activeSession?.isRunning != true
                ) {
                    runCatching { promoteQueued() }
                        .onFailure { Log.w("PVPSQueue", "drain failed: ${it.message}") }
                }
            }
        }
    }

    suspend fun sendQueuedNow(item: QueuedPrompt) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching { c.updateInboxDelivery(id, item.id, "steer") }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
        refreshInbox()
    }

    suspend fun cancelQueued(id: String) {
        val c = client ?: return
        val sessionId = _state.value.activeSessionId ?: return
        _state.value = _state.value.copy(
            queued = _state.value.queued.filterNot { it.id == id }
        )
        runCatching { c.cancelInbox(sessionId, id) }
            .onFailure { _state.value = _state.value.copy(error = it.message) }
        refreshInbox()
    }

    suspend fun interrupt() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.interrupt(id)
            _state.value = _state.value.copy(busy = false)
            refreshMessages()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message)
        }
    }

    suspend fun replyPermission(request: PermissionRequest, decision: String) {
        val c = client ?: return
        runCatching {
            c.replyPermission(request.sessionID, request.id, decision)
            refreshPermissions()
            refreshMessages()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message)
        }
    }

    suspend fun switchModel(model: ModelInfo, variant: ModelVariant? = null) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        val variantId = variant?.id?.takeIf { it.isNotBlank() }
        val ref = ModelRef(id = model.modelID, providerID = model.providerID, variant = variantId)
        runCatching {
            c.switchModel(id, ref)
            // Update the visible session immediately. refreshSessions() is async
            // and used to leave the composer showing the old effort until the
            // next event, which made effort changes look like no-ops.
            _state.value = _state.value.copy(
                sessions = _state.value.sessions.map {
                    if (it.id == id) it.copy(model = ref) else it
                }
            )
            settings.update {
                it.copy(
                    lastModelProvider = model.providerID,
                    lastModelId = model.modelID,
                    lastModelVariant = variantId.orEmpty()
                )
            }
            refreshSessions()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message)
        }
    }

    suspend fun switchAgent(agent: AgentInfo) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.switchAgent(id, agent.id)
            settings.update { it.copy(lastAgentId = agent.id) }
            refreshSessions()
        }.onFailure {
            _state.value = _state.value.copy(error = it.message)
        }
    }

    suspend fun loadMedia(uri: String): ByteArray? {
        if (uri.startsWith("data:")) {
            val comma = uri.indexOf(',')
            if (comma < 0) return null
            return runCatching { Base64.decode(uri.substring(comma + 1), Base64.DEFAULT) }.getOrNull()
        }
        if (uri.startsWith("media:")) {
            val hash = uri.removePrefix("media:")
            mediaCache.get(hash)?.let { return it }
            val bytes = runCatching { client?.media(hash) }.getOrNull() ?: return null
            mediaCache.put(hash, bytes)
            return bytes
        }
        return null
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun startEvents() {
        eventsJob?.cancel()
        eventsJob = scope.launch {
            var backoff = 1_000L
            while (isActive) {
                val c = client ?: break
                try {
                    c.events().collect { event -> onEvent(event) }
                    backoff = 1_000L
                    delay(backoff)
                } catch (_: Exception) {
                    if (!isActive) break
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(15_000L)
                    runCatching { refreshAll() }
                }
            }
        }
    }

    private suspend fun onEvent(event: OpenCodeEvent) {
        val active = _state.value.activeSessionId
        val eventSession = (event.data["sessionID"] as? JsonPrimitive)?.contentOrNull
        when (event.type) {
            "server.connected" -> refreshAll()
            "permission.asked" -> {
                refreshPermissions()
                if ((eventSession == null || eventSession == active) &&
                    settings.settings.value.notifyResponses
                ) {
                    val permission = _state.value.permissions.firstOrNull()
                    AppNotifications.permission(
                        sessionId = active.orEmpty(),
                        sessionTitle = sessionTitle(active),
                        action = permission?.action.orEmpty()
                    )
                }
            }
            "permission.replied" -> refreshPermissions()
            "form.created", "form.replied", "form.cancelled" -> {
                if (eventSession == null || eventSession == active) {
                    refreshForms()
                    if (event.type == "form.created" && settings.settings.value.notifyResponses) {
                        val form = _state.value.forms.firstOrNull()
                        if (form != null && form.id != lastNotifiedFormId) {
                            lastNotifiedFormId = form.id
                            AppNotifications.form(
                                sessionId = active.orEmpty(),
                                sessionTitle = sessionTitle(active),
                                title = form.title
                            )
                        }
                    }
                }
            }
            "session.inbox.enqueued", "session.inbox.delivered",
            "session.inbox.cancelled", "session.inbox.delivery.changed" -> {
                refreshInbox()
                scheduleRefresh()
            }
            "session.usage.updated" -> {
                refreshUsage()
                scheduleRefresh()
            }
            "message.updated", "message.part.updated", "message.part.removed" -> scheduleRefresh()
            "session.execution.started", "session.step.started" -> {
                if (eventSession == null || eventSession == active) {
                    _state.value = _state.value.copy(busy = true)
                    scheduleRefresh()
                }
            }
            "session.model.selected", "session.agent.selected" -> refreshSessions()
            "session.created", "session.deleted", "session.renamed", "session.moved",
            "session.archived", "session.unarchived" -> refreshSessions()
            "session.idle", "session.execution.succeeded", "session.execution.failed",
            "session.execution.interrupted" -> {
                _state.value = _state.value.copy(busy = false)
                refreshMessages()
                refreshPermissions()
                refreshForms()
                refreshSessions()
                refreshInbox()
                refreshUsage()
                scope.launch { _state.value.activeSessionId?.let { refreshRealCost(it) } }
                if (event.type == "session.idle" || event.type.startsWith("session.execution.")) {
                    notifyLatestAssistant()
                    // Delivering existing queue is what makes the "queued
                    // message" feature actually progress after a turn. Only do
                    // it when the active session itself is idle, not when a
                    // subagent finished while its parent is still working.
                    if (_state.value.activeSession?.isRunning != true) promoteQueued()
                }
            }
            else -> {
                if (event.type.startsWith("session.") || event.type.startsWith("permission.")) {
                    if (eventSession == null || eventSession == active) {
                        if (event.type.contains("inbox")) refreshInbox()
                        scheduleRefresh()
                    }
                }
            }
        }
    }

    private fun sessionTitle(sessionId: String?): String =
        _state.value.sessions.firstOrNull { it.id == sessionId }?.displayTitle.orEmpty()

    private fun notifyLatestAssistant() {
        if (!settings.settings.value.notifyResponses) return
        val message = _state.value.messages.lastOrNull {
            it.isAssistant && it.text.isNotBlank() && it.id != lastNotifiedMessageId
        } ?: return
        lastNotifiedMessageId = message.id
        AppNotifications.response(
            sessionId = _state.value.activeSessionId.orEmpty(),
            sessionTitle = sessionTitle(_state.value.activeSessionId),
            text = message.text
        )
    }

    private fun scheduleRefresh() {
        // Throttle, do not debounce: streaming events are frequent enough to
        // cancel a trailing debounce forever, which freezes the transcript.
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            delay(180)
            refreshMessages()
            refreshPermissions()
            refreshInbox()
        }
    }

    private suspend fun refreshAll() {
        refreshSessions()
        refreshModelsAndAgents()
        refreshMessages()
        refreshInbox()
        refreshPermissions()
        refreshForms()
        refreshUsage()
    }

    fun close() {
        disconnect()
        scope.cancel()
    }
}
