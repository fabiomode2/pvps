package dev.pvps.opencode

import android.util.Base64
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
        val delivery: String = "queue"
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
        val forms: List<FormInfo> = emptyList(),
        val usage: UsageLimits? = null,
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
            val usage = runCatching { newClient.usageLimits(host.openCodeDirectory) }.getOrNull()
            _state.value = _state.value.copy(
                connecting = false,
                connected = true,
                serverVersion = info.version,
                sessions = sessions,
                models = models,
                agents = agents,
                usage = usage,
                error = null
            )
            val active = sessions.firstOrNull { it.parentID == null }
            if (active != null) selectSession(active.id)
            startEvents()
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
        eventsJob = null
        refreshJob = null
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
            busy = _state.value.sessions.firstOrNull { it.id == sessionId }?.isRunning == true
        )
        refreshMessages()
        refreshInbox()
        refreshPermissions()
        refreshForms()
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
                .filter { it.type == "user" && it.payload.text.isNotBlank() }
                .map {
                    QueuedPrompt(
                        id = it.id,
                        text = it.payload.text,
                        created = it.time.created,
                        delivery = it.delivery
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
            _state.value = _state.value.copy(models = models, agents = agents)
        }.onFailure {
            _state.value = _state.value.copy(error = it.message)
        }
    }

    suspend fun send(text: String) {
        val prompt = text.trim()
        if (prompt.isBlank()) return
        val c = client ?: return
        val id = _state.value.activeSessionId ?: run {
            newSession()
            _state.value.activeSessionId
        } ?: return

        // Never trust the cached busy flag: it can be stale after a missed SSE
        // event. Ask the server for the session's real running state.
        refreshSessions()
        val actualBusy = _state.value.sessions
            .firstOrNull { it.id == id }?.isRunning == true
        _state.value = _state.value.copy(error = null, busy = true)

        runCatching {
            // A prompt sent while the agent is working must be queued, not
            // steered (steering aborts the current turn). When idle, deliver it
            // immediately and let the server start the loop.
            val ack = c.prompt(id, prompt, delivery = if (actualBusy) "queue" else "steer")
            val created = ack.time.created.takeIf { it > 0 } ?: System.currentTimeMillis()
            val ackId = ack.id.ifBlank { "local-${System.currentTimeMillis()}" }
            if (!actualBusy) {
                val optimistic = UiMessage(
                    id = ackId,
                    type = "user",
                    created = created,
                    text = prompt,
                    files = ack.payload.files,
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
            } else {
                _state.value = _state.value.copy(
                    queued = (_state.value.queued + QueuedPrompt(
                        id = ackId,
                        text = prompt,
                        created = created,
                        delivery = ack.delivery
                    )).distinctBy { it.id }
                )
            }
            scheduleRefresh()
        }.onFailure {
            _state.value = _state.value.copy(
                busy = actualBusy,
                error = it.message ?: "Prompt failed"
            )
        }
    }

    /**
     * A queued prompt stays in the server inbox until the current turn ends.
     * Promote it to "steer" at the idle boundary so the agent actually resumes
     * instead of leaving the message parked forever.
     */
    private suspend fun promoteQueued() {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        refreshInbox()
        val pending = _state.value.queued.filter { it.delivery == "queue" }
        if (pending.isEmpty()) return
        pending.forEach { item ->
            runCatching { c.updateInboxDelivery(id, item.id, "steer") }
        }
        refreshInbox()
        scheduleRefresh()
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
        runCatching {
            c.switchModel(
                id,
                ModelRef(id = model.modelID, providerID = model.providerID, variant = variantId)
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
            "session.usage.updated" -> refreshUsage()
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
