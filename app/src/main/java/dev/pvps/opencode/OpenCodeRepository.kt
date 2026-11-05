package dev.pvps.opencode

import android.util.Base64
import android.util.LruCache
import dev.pvps.data.model.AuditEntry
import dev.pvps.data.model.Host
import dev.pvps.data.store.AuditStore
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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

class OpenCodeRepository(
    private val ssh: SshManager,
    private val audit: AuditStore
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
            _state.value = _state.value.copy(
                connecting = false,
                connected = true,
                serverVersion = info.version,
                sessions = sessions,
                models = models,
                agents = agents,
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
                busy = sessions.firstOrNull { it.id == active }?.time?.busy != null
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
            busy = _state.value.sessions.firstOrNull { it.id == sessionId }?.time?.busy != null
        )
        refreshMessages()
        refreshInbox()
        refreshPermissions()
    }

    suspend fun newSession() {
        val c = client ?: return
        val h = host ?: return
        runCatching {
            val session = c.createSession(h.openCodeDirectory.takeIf { it.isNotBlank() })
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

        val wasBusy = _state.value.busy
        _state.value = _state.value.copy(error = null, busy = true)

        runCatching {
            // While OpenCode is working, enqueue instead of aborting/steering.
            // The server delivers one queued prompt at the next idle boundary.
            val ack = c.prompt(id, prompt, delivery = if (wasBusy) "queue" else null)
            if (ack.id.isNotBlank() && !wasBusy) {
                val optimistic = UiMessage(
                    id = ack.id,
                    type = "user",
                    created = ack.time.created.takeIf { it > 0 } ?: System.currentTimeMillis(),
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
                    optimisticIds += ack.id
                    _state.value = _state.value.copy(
                        messages = (_state.value.messages + optimistic)
                            .distinctBy { it.id }
                            .sortedBy { it.created }
                    )
                }
            }
            if (wasBusy) {
                _state.value = _state.value.copy(
                    queued = (_state.value.queued + QueuedPrompt(
                        id = ack.id.ifBlank { "local-${System.currentTimeMillis()}" },
                        text = prompt,
                        created = ack.time.created.takeIf { it > 0 } ?: System.currentTimeMillis(),
                        delivery = ack.delivery
                    )).distinctBy { it.id }
                )
            }
            scheduleRefresh()
        }.onFailure {
            _state.value = _state.value.copy(
                busy = if (wasBusy) _state.value.busy else false,
                error = it.message ?: "Prompt failed"
            )
        }
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

    suspend fun switchModel(model: ModelInfo) {
        val c = client ?: return
        val id = _state.value.activeSessionId ?: return
        runCatching {
            c.switchModel(id, ModelRef(id = model.modelID, providerID = model.providerID))
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
        when (event.type) {
            "server.connected" -> refreshAll()
            "permission.asked", "permission.replied" -> refreshPermissions()
            "session.inbox.enqueued", "session.inbox.delivered",
            "session.inbox.cancelled", "session.inbox.delivery.changed" -> {
                refreshInbox()
                scheduleRefresh()
            }
            "session.created", "session.deleted", "session.renamed", "session.moved",
            "session.archived", "session.unarchived" -> refreshSessions()
            "session.idle", "session.execution.succeeded", "session.execution.failed",
            "session.execution.interrupted" -> {
                _state.value = _state.value.copy(busy = false)
                refreshMessages()
                refreshPermissions()
                refreshSessions()
                refreshInbox()
            }
            else -> {
                if (event.type.startsWith("session.") || event.type.startsWith("permission.")) {
                    val eventSession = (event.data["sessionID"] as? JsonPrimitive)?.contentOrNull
                    if (eventSession == null || eventSession == _state.value.activeSessionId) {
                        if (event.type.contains("inbox")) refreshInbox()
                        scheduleRefresh()
                    }
                }
            }
        }
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
    }

    fun close() {
        disconnect()
        scope.cancel()
    }
}
