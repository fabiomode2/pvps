package dev.pvps.opencode

import android.util.Base64
import android.util.LruCache
import dev.pvps.data.model.AuditEntry
import dev.pvps.data.model.Host
import dev.pvps.data.store.AuditStore
import dev.pvps.data.store.SettingsStore
import dev.pvps.diagnostics.Diagnostics
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
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
        val delivery: String = "steer",
        val files: Int = 0
    )

    data class State(
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val hostName: String = "",
        val serverVersion: String = "",
        val sessions: List<SessionInfo> = emptyList(),
        val projects: List<ProjectInfo> = emptyList(),
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
        val contextTokens: Long = 0,
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
    private val eventJson = Json { ignoreUnknownKeys = true; isLenient = true }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var client: OpenCodeClient? = null
    private var host: Host? = null
    private var connectedBaseUrl: String? = null
    private var eventsJob: Job? = null
    private var refreshJob: Job? = null
    private var queueJob: Job? = null
    private var eventGeneration = 0
    private var mediaCache = LruCache<String, ByteArray>(24)
    private val optimisticIds = mutableSetOf<String>()
    private val optimisticLock = Any()
    private val liveMessages = mutableMapOf<String, UiMessage>()
    private val liveLock = Any()
    private var lastNotifiedMessageId: String? = null
    private var lastNotifiedFormId: String? = null
    private var lastStreamActivityAt = 0L

    suspend fun connect(host: Host, force: Boolean = false) {
        val desiredBase = runCatching { ssh.openCodeBaseUrl(host) }.getOrNull()
        val sameHost = this.host?.id == host.id
        if (!force && sameHost && _state.value.connected &&
            client != null && desiredBase != null && connectedBaseUrl == desiredBase
        ) return

        // A tunnel can come back on a different local port. Keep the transcript,
        // queue and scroll position in that case and only replace the client.
        if (!sameHost) stopRuntime(resetState = true)
        this.host = host
        _state.update {
            it.copy(
                connecting = true,
                hostName = host.name,
                error = null
            )
        }
        Diagnostics.event("opencode", "connect host=${host.name} sameHost=$sameHost base=$desiredBase")
        if (host.openCodePassword.isBlank()) {
            _state.update {
                it.copy(
                    connecting = false,
                    connected = false,
                    error = "OpenCode password is empty. Edit the host and set it."
                )
            }
            return
        }
        try {
            val baseUrl = desiredBase ?: ssh.openCodeBaseUrl(host)
            val newClient = OpenCodeClient(baseUrl, host.openCodeUsername, host.openCodePassword)
            val info = newClient.info()
            if (!info.version.startsWith("2.")) {
                newClient.close()
                _state.update {
                    it.copy(
                        connecting = false,
                        connected = false,
                        error = "OpenCode ${info.version} is not v2. This app requires v2.x."
                    )
                }
                return
            }
            stopEventsAndDrain()
            client?.close()
            client = newClient
            connectedBaseUrl = baseUrl
            val sessions = newClient.listSessions()
            val oldActive = _state.value.activeSessionId
            val models = _state.value.models.ifEmpty {
                runCatching { newClient.models(host.openCodeDirectory) }.getOrDefault(emptyList())
            }
            val agents = _state.value.agents.ifEmpty {
                runCatching { newClient.agents(host.openCodeDirectory) }.getOrDefault(emptyList())
            }
            val commands = _state.value.commands.ifEmpty {
                runCatching { newClient.commands(host.openCodeDirectory) }.getOrDefault(emptyList())
            }
            val usage = runCatching { newClient.usageLimits(host.openCodeDirectory) }.getOrNull()
            val activeId = oldActive?.takeIf { id -> sessions.any { it.id == id } }
                ?: sessions.firstOrNull { it.parentID == null }?.id
                ?: sessions.firstOrNull()?.id
            _state.update {
                it.copy(
                    connecting = false,
                    connected = true,
                    serverVersion = info.version,
                    sessions = sessions,
                    activeSessionId = activeId,
                    models = models,
                    agents = agents,
                    commands = withBuiltinCommands(commands),
                    usage = usage,
                    busy = sessions.firstOrNull { session -> session.id == activeId }?.isRunning == true,
                    error = null
                )
            }
            refreshProjects()
            if (activeId != null) {
                if (oldActive == activeId) {
                    refreshMessages()
                    refreshInbox()
                    refreshPermissions()
                    refreshForms()
                    refreshContextUsage()
                    refreshSessions()
                } else {
                    selectSession(activeId)
                }
            }
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
            Diagnostics.event("opencode", "connect failed", e)
            _state.update {
                it.copy(
                    connecting = false,
                    connected = false,
                    error = e.message ?: "Could not connect to OpenCode"
                )
            }
        }
    }

    fun disconnect() {
        stopRuntime(resetState = true)
    }

    private fun stopEventsAndDrain() {
        eventsJob?.cancel()
        refreshJob?.cancel()
        queueJob?.cancel()
        eventsJob = null
        refreshJob = null
        queueJob = null
        eventGeneration++
    }

    private fun stopRuntime(resetState: Boolean) {
        stopEventsAndDrain()
        client?.close()
        client = null
        host = null
        connectedBaseUrl = null
        mediaCache = LruCache(24)
        synchronized(optimisticLock) { optimisticIds.clear() }
        synchronized(liveLock) { liveMessages.clear() }
        if (resetState) _state.value = State()
    }

    suspend fun refreshProjects() {
        val c = client ?: return
        runCatching { c.projects() }
            .onSuccess { projects ->
                _state.update { it.copy(projects = projects) }
            }
            .onFailure {
                Diagnostics.event("opencode", "refresh projects failed", it)
            }
    }

    suspend fun refreshSessions() {
        val c = client ?: return
        runCatching {
            val sessions = c.listSessions()
            val active = _state.value.activeSessionId
            val hasActive = sessions.any { it.id == active }
            val fallback = sessions.firstOrNull { it.parentID == null }?.id
                ?: sessions.firstOrNull()?.id
            val liveActive = synchronized(liveLock) { liveMessages.isNotEmpty() }
            _state.update {
                it.copy(
                    sessions = sessions,
                    activeSessionId = if (hasActive) active else fallback,
                    busy = sessions.firstOrNull { session -> session.id == active }?.isRunning == true ||
                        (hasActive && liveActive)
                )
            }
            if (!hasActive && _state.value.activeSessionId != null) {
                selectSession(_state.value.activeSessionId!!)
            }
        }.onFailure {
            Diagnostics.event("opencode", "refresh sessions failed", it)
            _state.update { state -> state.copy(error = it.message) }
        }
    }

    suspend fun selectSession(sessionId: String) {
        synchronized(optimisticLock) { optimisticIds.clear() }
        synchronized(liveLock) { liveMessages.clear() }
        _state.update {
            it.copy(
                activeSessionId = sessionId,
                messages = emptyList(),
                queued = emptyList(),
                permissions = emptyList(),
                forms = emptyList(),
                realCost = null,
                contextTokens = 0,
                busy = it.sessions.firstOrNull { session -> session.id == sessionId }?.isRunning == true
            )
        }
        refreshMessages()
        refreshInbox()
        refreshPermissions()
        refreshForms()
        refreshContextUsage()
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
            _state.update { state -> state.copy(error = it.message ?: "Could not create session") }
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
                        val local = localMessages.firstOrNull { it.id == pendingId }
                        local != null && serverUsers.any { remote ->
                            remote.text.trim() == local.text.trim() &&
                                kotlin.math.abs(remote.created - local.created) < 120_000
                        }
                    }
                }
                localMessages.filter { it.id in optimisticIds }
            }
            val live = synchronized(liveLock) {
                // Once the canonical message is in the server page it no longer
                // needs a live overlay. Keeping the entry would make the next
                // refreshSessions() think the session is still busy forever.
                liveMessages.keys.removeAll(serverIds)
                liveMessages.values.toList()
            }
            val merged = (server + pending + live)
                .distinctBy { it.id }
                .sortedBy { it.created }
            val serverAssistantRunning = server.any {
                it.isAssistant && it.completed == null && it.error == null
            }
            _state.update {
                it.copy(
                    messages = merged,
                    busy = live.isNotEmpty() || serverAssistantRunning
                )
            }
        }.onFailure {
            Diagnostics.event("opencode", "refresh messages failed", it)
            _state.update { state -> state.copy(error = it.message ?: "Could not load messages") }
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
                _state.update { it.copy(queued = queued) }
            }
        }.onFailure {
            // The inbox is optional while a session starts; keep local queue on failure.
        }
    }

    suspend fun refreshPermissions() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            _state.update { it.copy(permissions = c.permissions(id)) }
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
                _state.update { it.copy(forms = forms) }
            }
        }.onFailure {
            // Forms are optional while a session is starting.
        }
    }

    suspend fun refreshUsage() {
        val c = client ?: return
        runCatching {
            val usage = c.usageLimits(host?.openCodeDirectory)
            _state.update { it.copy(usage = usage) }
        }.onFailure {
            // Usage is a nice-to-have; never surface it as a chat error.
        }
    }

    private suspend fun refreshContextUsage() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            val last = c.context(id).lastOrNull { it.type == "assistant" }
            val tokens = last?.tokens
            val used = tokens?.let { it.input + it.cache.read + it.cache.write } ?: 0
            if (_state.value.activeSessionId == id) {
                _state.update { it.copy(contextTokens = used) }
            }
        }.onFailure {
            Diagnostics.event("opencode", "context usage failed", it)
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
            _state.update { it.copy(realCost = cost) }
        }
    }

    suspend fun replyForm(form: FormInfo, answer: Map<String, JsonElement>) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.replyForm(id, form.id, JsonObject(answer))
            refreshForms()
            refreshMessages()
        }.onFailure {
            _state.update { state -> state.copy(error = it.message ?: "Could not submit answer") }
        }
    }

    suspend fun cancelForm(form: FormInfo, message: String? = null) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.cancelForm(id, form.id, message)
            refreshForms()
        }.onFailure {
            _state.update { state -> state.copy(error = it.message ?: "Could not cancel form") }
        }
    }

    suspend fun refreshModelsAndAgents() {
        val c = client ?: return
        val directory = host?.openCodeDirectory
        runCatching {
            val models = c.models(directory)
            val agents = c.agents(directory)
            val commands = c.commands(directory)
            _state.update {
                it.copy(
                    models = models,
                    agents = agents,
                    commands = withBuiltinCommands(commands)
                )
            }
        }.onFailure {
            _state.update { state -> state.copy(error = it.message) }
        }
    }

    private fun withBuiltinCommands(commands: List<CommandInfo>): List<CommandInfo> {
        // /compact is a dedicated API in v2, not always present in /api/command.
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
        val session = _state.value.sessions.firstOrNull { it.id == id }
        if (session?.parentID != null) {
            _state.update {
                it.copy(error = "This is a subagent session. Open the parent session to reply.")
            }
            return false
        }

        _state.update { it.copy(error = null, busy = true) }
        Diagnostics.event("queue", "send session=$id files=${files.size} busy=${_state.value.busy}")

        return runCatching {
            // "steer" is admitted immediately and injected by the server at the
            // next step boundary. "queue" waits for the whole turn to finish,
            // which feels lost when the agent is running tools. Keep steer for
            // both idle and busy sends; pending items remain visible in inbox.
            val ack = c.prompt(id, prompt, delivery = "steer", files = files)
            val created = ack.time.created.takeIf { it > 0 } ?: System.currentTimeMillis()
            val ackId = ack.id.ifBlank { "local-${System.currentTimeMillis()}" }
            Diagnostics.event("queue", "ack id=$ackId delivery=${ack.delivery}")
            addOptimisticUserMessage(ackId, created, prompt, ack.payload.files)
            refreshMessagesSoon()
            scope.launch {
                delay(1_200)
                runCatching {
                    refreshInbox()
                    refreshSessions()
                }.onFailure { Diagnostics.event("queue", "post-send reconcile failed", it) }
            }
            true
        }.getOrElse { failure ->
            Diagnostics.event("queue", "send failed", failure)
            _state.update { state ->
                state.copy(
                    busy = state.activeSession?.isRunning == true,
                    error = failure.message ?: "Prompt failed"
                )
            }
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
        if (_state.value.sessions.firstOrNull { it.id == id }?.parentID != null) {
            _state.update { it.copy(error = "Subagent sessions are read-only.") }
            return false
        }
        _state.update { it.copy(error = null, busy = true) }
        return runCatching {
            if (command == "compact") {
                c.compact(id)
            } else {
                c.runCommand(id, command, text.trim(), delivery = "steer", files = files)
                addOptimisticUserMessage(
                    "local-${System.currentTimeMillis()}",
                    System.currentTimeMillis(),
                    "/$command${text.takeIf { it.isNotBlank() }?.let { " $it" }.orEmpty()}",
                    emptyList()
                )
            }
            refreshMessagesSoon()
            true
        }.getOrElse { failure ->
            _state.update { state ->
                state.copy(
                    busy = state.activeSession?.isRunning == true,
                    error = failure.message ?: "Command failed"
                )
            }
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
            _state.update {
                it.copy(
                    messages = (it.messages + optimistic)
                        .distinctBy { message -> message.id }
                        .sortedBy { message -> message.created }
                )
            }
        }
    }

    suspend fun compact() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.compact(id)
            refreshMessagesSoon()
        }.onFailure {
            _state.update { state -> state.copy(error = it.message ?: "Could not compact context") }
        }
    }

    /**
     * Items in the inbox are undelivered work. Steer items are delivered by the
     * server at the next step boundary even while the turn is running. Only
     * queue items need promotion, and only once the session is actually idle.
     */
    private suspend fun promoteQueued() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        refreshSessions()
        if (_state.value.sessions.firstOrNull { it.id == id }?.isRunning == true) return
        refreshInbox()
        val pending = _state.value.queued.filter { it.delivery != "steer" }
        if (pending.isEmpty()) return
        Diagnostics.event("queue", "promote ${pending.size} parked item(s)")
        pending.forEach { item ->
            val promoted = runCatching { c.updateInboxDelivery(id, item.id, "steer") }
                .onFailure { Diagnostics.event("queue", "promote ${item.id} failed", it) }
                .isSuccess
            Diagnostics.event("queue", "promote ${item.id} (${item.delivery}) -> steer=$promoted")
        }
        refreshInbox()
    }

    private fun startQueueDrain() {
        if (queueJob?.isActive == true) return
        queueJob = scope.launch {
            while (isActive) {
                delay(4_000)
                if (client == null || !_state.value.connected) continue
                val before = _state.value
                // Reconcile only while there is something that can be stuck.
                if (before.busy || before.queued.isNotEmpty()) {
                    runCatching {
                        refreshInbox()
                        if (before.queued.isNotEmpty()) refreshSessions()
                    }.onFailure { Diagnostics.event("queue", "drain poll failed", it) }
                }
                val after = _state.value
                if (after.queued.any { it.delivery != "steer" } &&
                    after.activeSession?.isRunning != true
                ) {
                    runCatching { promoteQueued() }
                        .onFailure { Diagnostics.event("queue", "drain promote failed", it) }
                }
            }
        }
    }

    suspend fun sendQueuedNow(item: QueuedPrompt) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching { c.updateInboxDelivery(id, item.id, "steer") }
            .onFailure { _state.update { state -> state.copy(error = it.message) } }
        refreshInbox()
    }

    suspend fun cancelQueued(id: String) {
        val c = client ?: return
        val sessionId = _state.value.activeSessionId ?: return
        _state.update {
            it.copy(queued = it.queued.filterNot { item -> item.id == id })
        }
        runCatching { c.cancelInbox(sessionId, id) }
            .onFailure { _state.update { state -> state.copy(error = it.message) } }
        refreshInbox()
    }

    suspend fun interrupt() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.interrupt(id)
            _state.update { it.copy(busy = false) }
            refreshMessages()
        }.onFailure {
            _state.update { state -> state.copy(error = it.message) }
        }
    }

    suspend fun replyPermission(request: PermissionRequest, decision: String) {
        val c = client ?: return
        runCatching {
            c.replyPermission(request.sessionID, request.id, decision)
            refreshPermissions()
            refreshMessages()
        }.onFailure {
            _state.update { state -> state.copy(error = it.message) }
        }
    }

    suspend fun switchModel(model: ModelInfo, variant: ModelVariant? = null) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        val variantId = variant?.id?.takeIf { it.isNotBlank() }
        val ref = ModelRef(id = model.modelID, providerID = model.providerID, variant = variantId)
        runCatching {
            c.switchModel(id, ref)
            _state.update {
                it.copy(
                    sessions = it.sessions.map { session ->
                        if (session.id == id) session.copy(model = ref) else session
                    }
                )
            }
            settings.update {
                it.copy(
                    lastModelProvider = model.providerID,
                    lastModelId = model.modelID,
                    lastModelVariant = variantId.orEmpty()
                )
            }
            refreshSessions()
        }.onFailure {
            _state.update { state -> state.copy(error = it.message) }
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
            _state.update { state -> state.copy(error = it.message) }
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
        _state.update { it.copy(error = null) }
    }

    private fun startEvents() {
        eventsJob?.cancel()
        val generation = ++eventGeneration
        eventsJob = scope.launch {
            var backoff = 400L
            while (isActive) {
                val c = client ?: break
                try {
                    c.events().collect { event ->
                        lastStreamActivityAt = System.currentTimeMillis()
                        onEvent(event)
                    }
                    // The stream ended without an exception. The v2 contract is
                    // live-only, so reconnect immediately and resync on the
                    // server.connected marker.
                    backoff = 400L
                    delay(backoff)
                } catch (e: Exception) {
                    if (!isActive || generation != eventGeneration) break
                    Diagnostics.event("sse", "disconnected; retry in ${backoff}ms", e)
                    delay(backoff)
                    backoff = (backoff * 2).coerceAtMost(5_000L)
                    runCatching { refreshAll() }
                }
            }
        }
    }

    private suspend fun onEvent(event: OpenCodeEvent) {
        val active = _state.value.activeSessionId
        val eventSession = event.data.stringOrNull("sessionID")
        // Session metadata events are global. Everything else can be filtered
        // cheaply by sessionID without issuing a request.
        when (event.type) {
            "server.connected" -> {
                Diagnostics.event("sse", "connected; resyncing")
                refreshAll()
                return
            }
            "session.created", "session.deleted", "session.renamed", "session.moved",
            "session.archived", "session.unarchived", "session.model.selected",
            "session.agent.selected" -> {
                refreshSessions()
                return
            }
            "session.usage.updated" -> {
                if (eventSession == null || eventSession == active) {
                    applyUsageEvent(event.data)
                    refreshUsage()
                }
                return
            }
            "session.inbox.enqueued", "session.inbox.delivered",
            "session.inbox.cancelled", "session.inbox.delivery.changed" -> {
                if (eventSession == null || eventSession == active) {
                    refreshInbox()
                } else {
                    scope.launch { refreshInbox() }
                }
                return
            }
            "permission.asked" -> {
                if (eventSession == null || eventSession == active) {
                    refreshPermissions()
                    if (settings.settings.value.notifyResponses) {
                        val permission = _state.value.permissions.firstOrNull()
                        AppNotifications.permission(
                            sessionId = active.orEmpty(),
                            sessionTitle = sessionTitle(active),
                            action = permission?.action.orEmpty()
                        )
                    }
                }
                return
            }
            "permission.replied" -> {
                if (eventSession == null || eventSession == active) refreshPermissions()
                return
            }
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
                return
            }
        }

        if (eventSession != null && eventSession != active) return

        when (event.type) {
            "session.execution.started" -> {
                _state.update { it.copy(busy = true) }
            }
            "session.status" -> {
                val type = event.data.objectOrNull("status")
                    ?.stringOrNull("type")
                    ?: event.data.stringOrNull("status")
                if (type == "idle") _state.update { it.copy(busy = false) }
                if (type == "busy" || type == "retry") _state.update { it.copy(busy = true) }
            }
            "session.step.started" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val model = event.data.objectOrNull("model")?.stringOrNull("id")
                ensureLiveAssistant(
                    assistantID,
                    agent = event.data.stringOrNull("agent"),
                    model = model
                )
                _state.update { it.copy(busy = true) }
            }
            "session.text.started" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val ordinal = event.data.intOrNull("ordinal") ?: 0
                updateLive(assistantID) { message ->
                    upsertText(message, "text-$ordinal", "")
                }
            }
            "session.text.delta" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val ordinal = event.data.intOrNull("ordinal") ?: 0
                val delta = event.data.stringOrNull("delta").orEmpty()
                updateLive(assistantID) { message ->
                    appendText(message, "text-$ordinal", delta)
                }
            }
            "session.text.ended" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val ordinal = event.data.intOrNull("ordinal") ?: 0
                val text = event.data.stringOrNull("text").orEmpty()
                updateLive(assistantID) { message ->
                    upsertText(message, "text-$ordinal", text)
                }
            }
            "session.reasoning.started" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val ordinal = event.data.intOrNull("ordinal") ?: 0
                updateLive(assistantID) { message ->
                    upsertReasoning(message, "reasoning-$ordinal", "")
                }
            }
            "session.reasoning.delta" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val ordinal = event.data.intOrNull("ordinal") ?: 0
                val delta = event.data.stringOrNull("delta").orEmpty()
                updateLive(assistantID) { message ->
                    appendReasoning(message, "reasoning-$ordinal", delta)
                }
            }
            "session.reasoning.ended" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val ordinal = event.data.intOrNull("ordinal") ?: 0
                val text = event.data.stringOrNull("text").orEmpty()
                updateLive(assistantID) { message ->
                    upsertReasoning(message, "reasoning-$ordinal", text)
                }
            }
            "session.tool.input.started" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val name = event.data.stringOrNull("name").orEmpty()
                updateLive(assistantID) { message ->
                    val existing = message.parts.filterIsInstance<UiPart.Tool>()
                        .firstOrNull { it.tool.id == toolID }?.tool
                        ?: ToolPart(toolID, name, "streaming", "", emptyList())
                    upsertTool(
                        message,
                        existing.copy(
                            name = name.ifBlank { existing.name },
                            status = "streaming"
                        )
                    )
                }
            }
            "session.tool.input.delta" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val delta = event.data.stringOrNull("delta").orEmpty()
                updateLive(assistantID) { message ->
                    updateTool(message, toolID) { it.copy(input = it.input + delta) }
                }
            }
            "session.tool.input.ended" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val input = event.data.stringOrNull("text").orEmpty()
                updateLive(assistantID) { message ->
                    updateTool(message, toolID) { it.copy(input = input) }
                }
            }
            "session.tool.called" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val name = event.data.stringOrNull("name").orEmpty()
                val input = event.data["input"]?.toString().orEmpty()
                updateLive(assistantID) { message ->
                    val existing = message.parts.filterIsInstance<UiPart.Tool>()
                        .firstOrNull { it.tool.id == toolID }?.tool
                    upsertTool(
                        message,
                        (existing ?: ToolPart(toolID, name, "running", "", emptyList()))
                            .copy(name = name.ifBlank { existing?.name.orEmpty() }, status = "running", input = input)
                    )
                }
            }
            "session.tool.progress" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val metadata = event.data.objectOrNull("metadata")
                updateLive(assistantID) { message ->
                    updateTool(message, toolID) { it.copy(status = "running", metadata = metadata) }
                }
            }
            "session.tool.success" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val content = parseToolContent(event.data["content"])
                val metadata = event.data.objectOrNull("metadata")
                updateLive(assistantID) { message ->
                    updateTool(message, toolID) {
                        it.copy(status = "completed", content = content, metadata = metadata, error = null)
                    }
                }
            }
            "session.tool.failed" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val toolID = event.data.stringOrNull("id") ?: return
                val content = parseToolContent(event.data["content"])
                val error = event.data.objectOrNull("error")?.stringOrNull("message")
                    ?: event.data.stringOrNull("error")
                updateLive(assistantID) { message ->
                    updateTool(message, toolID) {
                        it.copy(status = "error", content = content, error = error)
                    }
                }
            }
            "session.step.streamed", "session.step.ended" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val tokens = event.data.objectOrNull("tokens")
                    ?.let { element ->
                        runCatching {
                            eventJson.decodeFromJsonElement(TokenUsage.serializer(), element)
                        }.getOrNull()
                    }
                val cost = event.data.doubleOrNull("cost")
                val finish = event.data.stringOrNull("finish")
                if (tokens != null && event.data.stringOrNull("sessionID") == _state.value.activeSessionId) {
                    val used = tokens.input + tokens.cache.read + tokens.cache.write
                    if (used > 0) _state.update { it.copy(contextTokens = used) }
                }
                updateLive(assistantID) { message ->
                    message.copy(
                        tokens = tokens ?: message.tokens,
                        cost = cost ?: message.cost,
                        finish = finish ?: message.finish,
                        streamed = System.currentTimeMillis()
                    )
                }
            }
            "session.step.failed" -> {
                val assistantID = event.data.stringOrNull("assistantMessageID") ?: return
                val error = event.data.objectOrNull("error")?.stringOrNull("message")
                    ?: event.data.stringOrNull("error")
                    ?: "Step failed"
                updateLive(assistantID) { message -> message.copy(error = error) }
            }
            "session.execution.succeeded", "session.execution.failed",
            "session.execution.interrupted", "session.idle" -> {
                finishExecution(event.type)
            }
            "session.compaction.started" -> {
                _state.update { it.copy(busy = true) }
            }
            "session.compaction.ended", "session.compaction.failed" -> {
                refreshMessagesSoon()
                refreshContextUsage()
            }
            "session.retry.scheduled" -> {
                _state.update { it.copy(busy = true) }
            }
        }
    }

    private suspend fun finishExecution(type: String) {
        _state.update { it.copy(busy = false) }
        // The event stream gives us deltas quickly, but the canonical message
        // is the source of truth for completed text, tool results and token
        // counts. One targeted list refresh at the turn boundary is cheap.
        refreshMessages()
        refreshPermissions()
        refreshForms()
        refreshSessions()
        refreshInbox()
        refreshContextUsage()
        refreshUsage()
        scope.launch { _state.value.activeSessionId?.let { refreshRealCost(it) } }
        if (type == "session.execution.succeeded" || type == "session.idle") {
            notifyLatestAssistant()
            if (_state.value.activeSession?.isRunning != true) promoteQueued()
        }
    }

    private fun applyUsageEvent(data: JsonObject) {
        val sessionID = data.stringOrNull("sessionID") ?: return
        val cost = data.doubleOrNull("cost")
        val tokens = data.objectOrNull("tokens")?.let { element ->
            runCatching {
                eventJson.decodeFromJsonElement(TokenUsage.serializer(), element)
            }.getOrNull()
        }
        _state.update { state ->
            val used = if (sessionID == state.activeSessionId) {
                tokens?.let { it.input + it.cache.read + it.cache.write }?.takeIf { it > 0 }
            } else null
            state.copy(
                contextTokens = used ?: state.contextTokens,
                sessions = state.sessions.map { session ->
                    if (session.id != sessionID) session else session.copy(
                        cost = cost ?: session.cost,
                        tokens = tokens ?: session.tokens
                    )
                }
            )
        }
    }

    private fun ensureLiveAssistant(id: String, agent: String?, model: String?) {
        synchronized(liveLock) {
            val existing = liveMessages[id]
            val message = existing ?: UiMessage(
                id = id,
                type = "assistant",
                created = System.currentTimeMillis(),
                streamed = System.currentTimeMillis(),
                text = "",
                files = emptyList(),
                parts = emptyList(),
                agent = agent,
                model = model,
                cost = 0.0,
                tokens = null,
                finish = null,
                error = null,
                command = null,
                status = null,
                output = null,
                outcome = null
            )
            val updated = message.copy(
                agent = agent ?: message.agent,
                model = model ?: message.model
            )
            liveMessages[id] = updated
            _state.update { state ->
                state.copy(
                    messages = (state.messages.filterNot { it.id == id } + updated)
                        .distinctBy { it.id }
                        .sortedBy { it.created },
                    busy = true
                )
            }
        }
    }

    private fun updateLive(id: String, transform: (UiMessage) -> UiMessage) {
        synchronized(liveLock) {
            val current = liveMessages[id] ?: UiMessage(
                id = id,
                type = "assistant",
                created = System.currentTimeMillis(),
                streamed = System.currentTimeMillis(),
                text = "",
                files = emptyList(),
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
            val transformed = transform(current)
            val updated = transformed.copy(
                text = transformed.parts
                    .filterIsInstance<UiPart.Text>()
                    .joinToString("\n") { it.text }
                    .ifBlank { transformed.text },
                streamed = System.currentTimeMillis()
            )
            liveMessages[id] = updated
            _state.update { state ->
                state.copy(
                    messages = (state.messages.filterNot { it.id == id } + updated)
                        .distinctBy { it.id }
                        .sortedBy { it.created },
                    busy = true
                )
            }
        }
    }

    private fun upsertText(message: UiMessage, partId: String, text: String): UiMessage {
        val parts = message.parts.toMutableList()
        val index = parts.indexOfFirst { it is UiPart.Text && it.id == partId }
        if (index >= 0) {
            parts[index] = UiPart.Text(text, partId)
        } else {
            parts += UiPart.Text(text, partId)
        }
        return message.copy(parts = parts, text = parts.filterIsInstance<UiPart.Text>().joinToString("\n") { it.text })
    }

    private fun appendText(message: UiMessage, partId: String, delta: String): UiMessage {
        val existing = message.parts.filterIsInstance<UiPart.Text>().firstOrNull { it.id == partId }
        return upsertText(message, partId, (existing?.text.orEmpty()) + delta)
    }

    private fun upsertReasoning(message: UiMessage, partId: String, text: String): UiMessage {
        val parts = message.parts.toMutableList()
        val index = parts.indexOfFirst { it is UiPart.Reasoning && it.id == partId }
        if (index >= 0) {
            parts[index] = UiPart.Reasoning(text, partId)
        } else {
            parts += UiPart.Reasoning(text, partId)
        }
        return message.copy(parts = parts)
    }

    private fun appendReasoning(message: UiMessage, partId: String, delta: String): UiMessage {
        val existing = message.parts.filterIsInstance<UiPart.Reasoning>().firstOrNull { it.id == partId }
        return upsertReasoning(message, partId, (existing?.text.orEmpty()) + delta)
    }

    private fun upsertTool(message: UiMessage, tool: ToolPart): UiMessage {
        val parts = message.parts.toMutableList()
        val index = parts.indexOfFirst { it is UiPart.Tool && it.tool.id == tool.id }
        if (index >= 0) parts[index] = UiPart.Tool(tool) else parts += UiPart.Tool(tool)
        return message.copy(parts = parts)
    }

    private fun updateTool(message: UiMessage, toolID: String, transform: (ToolPart) -> ToolPart): UiMessage {
        val existing = message.parts.filterIsInstance<UiPart.Tool>()
            .firstOrNull { it.tool.id == toolID }?.tool
            ?: ToolPart(toolID, "tool", "running", "", emptyList())
        return upsertTool(message, transform(existing))
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

    private fun refreshMessagesSoon() {
        if (refreshJob?.isActive == true) return
        refreshJob = scope.launch {
            delay(120)
            refreshMessages()
        }
    }

    private suspend fun refreshAll() {
        refreshSessions()
        refreshProjects()
        if (_state.value.models.isEmpty()) refreshModelsAndAgents()
        refreshMessages()
        refreshInbox()
        refreshPermissions()
        refreshForms()
        refreshUsage()
        refreshContextUsage()
    }

    fun close() {
        disconnect()
        scope.cancel()
    }
}
