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
    data class State(
        val connecting: Boolean = false,
        val connected: Boolean = false,
        val hostName: String = "",
        val serverVersion: String = "",
        val sessions: List<SessionInfo> = emptyList(),
        val activeSessionId: String? = null,
        val messages: List<UiMessage> = emptyList(),
        val permissions: List<PermissionRequest> = emptyList(),
        val models: List<ModelInfo> = emptyList(),
        val agents: List<AgentInfo> = emptyList(),
        val busy: Boolean = false,
        val error: String? = null
    ) {
        val activeSession: SessionInfo?
            get() = sessions.firstOrNull { it.id == activeSessionId }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var client: OpenCodeClient? = null
    private var host: Host? = null
    private var eventsJob: Job? = null
    private var refreshJob: Job? = null
    private var mediaCache = LruCache<String, ByteArray>(24)

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
            val active = sessions.firstOrNull()
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
        _state.value = State()
    }

    suspend fun refreshSessions() {
        val c = client ?: return
        runCatching {
            val sessions = c.listSessions()
            val active = _state.value.activeSessionId
            val activeStillExists = sessions.any { it.id == active }
            _state.value = _state.value.copy(
                sessions = sessions,
                activeSessionId = if (activeStillExists) active else sessions.firstOrNull()?.id,
                busy = sessions.firstOrNull { it.id == active }?.time?.busy != null
            )
            if (!activeStillExists) {
                _state.value.activeSessionId?.let { selectSession(it) }
            }
        }.onFailure { _state.value = _state.value.copy(error = it.message) }
    }

    suspend fun selectSession(sessionId: String) {
        _state.value = _state.value.copy(
            activeSessionId = sessionId,
            messages = emptyList(),
            permissions = emptyList(),
            busy = _state.value.sessions.firstOrNull { it.id == sessionId }?.time?.busy != null
        )
        refreshMessages()
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
            val messages = c.messages(id).map { it.toUi() }
            _state.value = _state.value.copy(messages = messages)
        }.onFailure {
            _state.value = _state.value.copy(error = it.message ?: "Could not load messages")
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
        if (text.isBlank()) return
        val c = client ?: return
        val id = _state.value.activeSessionId ?: run {
            newSession()
            _state.value.activeSessionId
        } ?: return
        _state.value = _state.value.copy(busy = true, error = null)
        runCatching {
            c.prompt(id, text)
            refreshMessages()
        }.onFailure {
            _state.value = _state.value.copy(
                busy = false,
                error = it.message ?: "Prompt failed"
            )
        }
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
            "session.created", "session.deleted", "session.renamed", "session.moved",
            "session.archived", "session.unarchived" -> refreshSessions()
            "session.idle", "session.execution.succeeded", "session.execution.failed",
            "session.execution.interrupted" -> {
                _state.value = _state.value.copy(busy = false)
                refreshMessages()
                refreshPermissions()
                refreshSessions()
            }
            else -> {
                if (event.type.startsWith("session.") || event.type.startsWith("permission.")) {
                    val eventSession = (event.data["sessionID"] as? JsonPrimitive)?.contentOrNull
                    if (eventSession == null || eventSession == _state.value.activeSessionId) {
                        scheduleRefresh()
                    }
                }
            }
        }
    }

    private fun scheduleRefresh() {
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(220)
            refreshMessages()
            refreshPermissions()
        }
    }

    private suspend fun refreshAll() {
        refreshSessions()
        refreshMessages()
        refreshPermissions()
    }

    fun close() {
        disconnect()
        scope.cancel()
    }
}
