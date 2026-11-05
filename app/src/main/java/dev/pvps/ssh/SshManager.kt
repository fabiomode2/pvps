package dev.pvps.ssh

import dev.pvps.data.model.CommandResult
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.data.model.OpenCodeMode
import dev.pvps.data.store.AuditStore
import dev.pvps.data.store.SettingsStore
import dev.pvps.data.store.VaultRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class SshManager(
    private val vault: VaultRepository,
    private val audit: AuditStore,
    private val settings: SettingsStore
) {

    private val _states = MutableStateFlow<Map<String, ConnectionState>>(emptyMap())
    val states: StateFlow<Map<String, ConnectionState>> = _states.asStateFlow()

    private val connections = ConcurrentHashMap<String, SshConnection>()
    private val forwards = ConcurrentHashMap<String, LocalForward>()
    private val attemptSeq = AtomicLong()
    private val activeAttempts = ConcurrentHashMap<String, Long>()

    fun stateFor(hostId: String): ConnectionState =
        _states.value[hostId] ?: ConnectionState(hostId, ConnectionState.Status.DISCONNECTED)

    fun connection(hostId: String): SshConnection? = connections[hostId]

    fun forward(hostId: String): LocalForward? = forwards[hostId]

    suspend fun connect(hostId: String, autoTrust: Boolean = false): ConnectionState {
        // Two rapid taps must not create two SSH sessions for the same host.
        if (stateFor(hostId).status == ConnectionState.Status.CONNECTING) {
            return stateFor(hostId)
        }
        val host = vault.host(hostId)
            ?: return fail(hostId, "Host not found")
        if (connections[hostId]?.isConnected == true) {
            return stateFor(hostId)
        }
        val key = vault.key(host.keyId)
        if (host.authType == dev.pvps.data.model.AuthType.KEY && key == null) {
            return fail(hostId, "No SSH key selected for this host")
        }

        setState(ConnectionState(hostId, ConnectionState.Status.CONNECTING, "Connecting to ${host.sshHost}:${host.sshPort}"))
        val attempt = attemptSeq.incrementAndGet()
        activeAttempts[hostId] = attempt
        val connection = SshConnection(
            host = host,
            key = key,
            pinnedHostKey = host.hostKeySha256,
            autoTrustHostKey = autoTrust || !settings.settings.value.strictHostKeys
        )
        val outcome = try {
            connection.connect()
        } catch (e: CancellationException) {
            connection.disconnect()
            if (activeAttempts[hostId] == attempt) {
                activeAttempts.remove(hostId)
                setState(ConnectionState(hostId, ConnectionState.Status.DISCONNECTED))
            }
            throw e
        }
        // A disconnect or a newer connect may have happened while this attempt was
        // suspended. Never install a stale session in that case.
        if (activeAttempts[hostId] != attempt) {
            connection.disconnect()
            return stateFor(hostId)
        }
        return when (outcome) {
            is ConnectOutcome.Connected -> {
                connections[hostId] = connection
                if (host.hostKeySha256.isNullOrBlank() && outcome.hostKeySha256.isNotBlank()) {
                    vault.setHostKey(hostId, outcome.hostKeySha256)
                }
                vault.markConnected(hostId)
                val tunnelMessage = startTunnelIfNeeded(host, connection)
                val state = ConnectionState(
                    hostId = hostId,
                    status = ConnectionState.Status.CONNECTED,
                    message = tunnelMessage,
                    hostKeySha256 = outcome.hostKeySha256
                )
                setState(state)
                audit.add(
                    dev.pvps.data.model.AuditEntry(
                        hostName = host.name,
                        action = "SSH connect",
                        detail = "${host.sshUser}@${host.sshHost}:${host.sshPort}",
                        success = true
                    )
                )
                state
            }
            is ConnectOutcome.NeedsHostKey -> {
                connection.disconnect()
                val state = ConnectionState(
                    hostId = hostId,
                    status = ConnectionState.Status.NEEDS_HOST_KEY,
                    message = "Unknown host key. Verify the fingerprint before trusting.",
                    hostKeySha256 = outcome.hostKeySha256
                )
                setState(state)
                state
            }
            is ConnectOutcome.HostKeyChanged -> {
                connection.disconnect()
                fail(
                    hostId,
                    "HOST KEY CHANGED. Possible MITM. Expected ${host.hostKeySha256}, got ${outcome.hostKeySha256}",
                    hostKeySha256 = outcome.hostKeySha256
                )
            }
            is ConnectOutcome.Failed -> {
                connection.disconnect()
                fail(hostId, outcome.message)
            }
        }
    }

    suspend fun trustHostKey(hostId: String): ConnectionState {
        val fingerprint = stateFor(hostId).hostKeySha256
            ?: return fail(hostId, "No pending host key fingerprint")
        vault.setHostKey(hostId, fingerprint)
        return connect(hostId, autoTrust = true)
    }

    fun disconnect(hostId: String) {
        // Invalidate any connect attempt that is still suspended.
        activeAttempts.remove(hostId)
        forwards.remove(hostId)?.close()
        connections.remove(hostId)?.disconnect()
        setState(ConnectionState(hostId, ConnectionState.Status.DISCONNECTED))
        val host = vault.host(hostId)
        if (host != null) {
            audit.add(
                dev.pvps.data.model.AuditEntry(
                    hostName = host.name,
                    action = "SSH disconnect",
                    success = true
                )
            )
        }
    }

    fun disconnectAll() {
        activeAttempts.clear()
        forwards.keys.toList().forEach { forwards.remove(it)?.close() }
        connections.keys.toList().forEach { connections.remove(it)?.disconnect() }
        _states.value = _states.value.mapValues { (id, _) -> ConnectionState(id, ConnectionState.Status.DISCONNECTED) }
    }

    suspend fun exec(hostId: String, command: String, timeoutMs: Long = 30_000): CommandResult =
        withContext(Dispatchers.IO) {
            val connection = connections[hostId] ?: error("Host is not connected")
            connection.exec(command, timeoutMs)
        }

    fun openShell(hostId: String, cols: Int, rows: Int, cellWidth: Int = 8, cellHeight: Int = 16): ShellChannel {
        val connection = connections[hostId] ?: error("Host is not connected")
        return connection.openShell(cols, rows, cellWidth, cellHeight)
    }

    fun openSftp(hostId: String) = connections[hostId]?.openSftp() ?: error("Host is not connected")

    fun openCodeBaseUrl(host: Host): String {
        return when (host.openCodeMode) {
            OpenCodeMode.SSH_TUNNEL -> {
                val forward = forwards[host.id]
                    ?: error("OpenCode tunnel is not ready. Reconnect the host.")
                "http://127.0.0.1:${forward.port}"
            }
            OpenCodeMode.DIRECT -> "http://${host.effectiveOpenCodeHost}:${host.openCodePort}"
        }
    }

    private fun startTunnelIfNeeded(host: Host, connection: SshConnection): String {
        if (host.openCodeMode != OpenCodeMode.SSH_TUNNEL) return "SSH connected"
        return runCatching {
            forwards.remove(host.id)?.close()
            val forward = connection.openForward("127.0.0.1", host.openCodePort)
            forwards[host.id] = forward
            "SSH connected · OpenCode tunnel :${forward.port}"
        }.getOrElse { error ->
            "SSH connected · OpenCode tunnel failed: ${error.message}"
        }
    }

    private fun setState(state: ConnectionState) {
        _states.value = _states.value + (state.hostId to state)
    }

    private fun fail(
        hostId: String,
        message: String,
        hostKeySha256: String? = null
    ): ConnectionState {
        val state = ConnectionState(
            hostId = hostId,
            status = ConnectionState.Status.ERROR,
            message = message,
            hostKeySha256 = hostKeySha256
        )
        setState(state)
        val host = vault.host(hostId)
        if (host != null) {
            audit.add(
                dev.pvps.data.model.AuditEntry(
                    hostName = host.name,
                    action = "SSH error",
                    detail = message,
                    success = false
                )
            )
        }
        return state
    }
}
