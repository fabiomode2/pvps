package dev.pvps.ssh

import dev.pvps.data.model.CommandResult
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.data.model.OpenCodeMode
import dev.pvps.data.store.AuditStore
import dev.pvps.data.store.SettingsStore
import dev.pvps.data.store.VaultRepository
import dev.pvps.diagnostics.Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.random.Random

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

    /**
     * Hosts the user asked to keep connected. They stay here while automatic
     * reconnects run, so the foreground service and the retry loop survive a
     * network handoff without the user doing anything.
     */
    private val desired = ConcurrentHashMap.newKeySet<String>()

    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val loopActive = ConcurrentHashMap.newKeySet<String>()
    private val probing = ConcurrentHashMap.newKeySet<String>()
    private val recoveryInFlight = ConcurrentHashMap.newKeySet<String>()
    private val probeFailures = ConcurrentHashMap<String, Int>()
    private val lastProbeAt = ConcurrentHashMap<String, Long>()
    private val reconnectAttempts = ConcurrentHashMap<String, Int>()
    private val lastRecoveryAt = ConcurrentHashMap<String, Long>()
    private val lastTunnelAttemptAt = ConcurrentHashMap<String, Long>()
    private val wakeups = ConcurrentHashMap<String, Channel<Unit>>()
    private val tunnelPorts = ConcurrentHashMap<String, Int>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        // Cheap check every 5s: catching a dead socket promptly is what makes a
        // Wi-Fi/LTE handoff feel like a hiccup instead of a dead app.
        private const val WATCHDOG_INTERVAL_MS = 5_000L
        private const val PROBE_TIMEOUT_MS = 4_000L
        private const val PROBE_INTERVAL_MS = 15_000L
        private const val MAX_PROBE_FAILURES = 2

        private const val RECONNECT_BASE_MS = 2_000L
        private const val RECONNECT_MAX_MS = 45_000L
        private const val RECOVERY_COOLDOWN_MS = 10_000L
        private const val TUNNEL_REBUILD_COOLDOWN_MS = 10_000L

        /** 2s, 4s, 8s, 16s, 32s, then 45s. Jitter avoids sync with the network. */
        internal fun reconnectDelayMs(attempt: Int, jitter: Long = 0L): Long {
            val base = (RECONNECT_BASE_MS shl attempt.coerceIn(0, 6))
                .coerceAtMost(RECONNECT_MAX_MS)
            return (base + jitter.coerceIn(0L, base / 4)).coerceAtMost(RECONNECT_MAX_MS)
        }
    }

    init {
        // Mobile networks drop idle sockets even with JSch keepalives. This
        // watchdog silently re-dials sessions the user asked to keep open.
        scope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                verifyConnections()
            }
        }
    }

    /** True while at least one host is supposed to stay connected. */
    val hasActiveSessions: Boolean
        get() = desired.isNotEmpty()

    fun verifyConnections(force: Boolean = false) {
        val now = System.currentTimeMillis()
        desired.forEach { hostId ->
            val state = stateFor(hostId)
            if (state.status == ConnectionState.Status.CONNECTING) return@forEach
            if (state.status == ConnectionState.Status.RECONNECTING) {
                // Normally the retry loop owns this host. If it exited without
                // a terminal state (unexpected error), restart it instead of
                // leaving the host stuck on "Reconnecting…" forever.
                if (!loopActive.contains(hostId)) {
                    wakeAndReconnect(hostId, "retry loop stopped")
                }
                return@forEach
            }
            val connection = connections[hostId]
            if (connection == null || !connection.isAlive) {
                // JSch is authoritative for a clean close/reset; do not wait for
                // a second strike in that case.
                wakeAndReconnect(hostId, "SSH session lost")
                return@forEach
            }

            val host = vault.host(hostId) ?: return@forEach
            if (host.openCodeMode == OpenCodeMode.SSH_TUNNEL && forwards[hostId]?.isOpen != true) {
                // The transport is up but the local listener is gone. Rebuild
                // it before OpenCode tries to reconnect through it. Throttle
                // the attempt so a port that is genuinely unavailable does not
                // retry on every watchdog tick.
                val lastAttempt = lastTunnelAttemptAt[hostId] ?: 0L
                if (now - lastAttempt >= TUNNEL_REBUILD_COOLDOWN_MS) {
                    lastTunnelAttemptAt[hostId] = now
                    Diagnostics.event("ssh", "tunnel missing host=${host.name}; rebuilding")
                    setState(stateFor(hostId).copy(message = startTunnelIfNeeded(host, connection)))
                }
            }

            val last = lastProbeAt[hostId] ?: 0L
            if (!force && now - last < PROBE_INTERVAL_MS) return@forEach
            if (!probing.add(hostId)) return@forEach
            lastProbeAt[hostId] = now
            scope.launch {
                try {
                    // JSch's session.isConnected can stay true after a dead
                    // mobile socket. A cheap exec is the only reliable probe.
                    val alive = runCatching {
                        connection.exec("true", PROBE_TIMEOUT_MS).isSuccess
                    }.getOrDefault(false)
                    if (alive) {
                        probeFailures.remove(hostId)
                        reconnectAttempts.remove(hostId)
                        val currentHost = vault.host(hostId) ?: return@launch
                        if (currentHost.openCodeMode == OpenCodeMode.SSH_TUNNEL &&
                            forwards[hostId]?.isOpen != true &&
                            now - (lastTunnelAttemptAt[hostId] ?: 0L) >= TUNNEL_REBUILD_COOLDOWN_MS
                        ) {
                            lastTunnelAttemptAt[hostId] = now
                            setState(
                                stateFor(hostId).copy(
                                    message = startTunnelIfNeeded(currentHost, connection)
                                )
                            )
                        }
                    } else {
                        val failures = (probeFailures[hostId] ?: 0) + 1
                        probeFailures[hostId] = failures
                        Diagnostics.event(
                            "ssh",
                            "probe failed host=$hostId ($failures/$MAX_PROBE_FAILURES)"
                        )
                        // A single failed exec can happen during a mobile
                        // handoff while the SSH socket is still usable.
                        if (failures >= MAX_PROBE_FAILURES) {
                            wakeAndReconnect(hostId, "keepalive probe failed")
                        }
                    }
                } finally {
                    probing.remove(hostId)
                }
            }
        }
    }

    /**
     * Called by the OpenCode client (or a dying listener) when the tunnel keeps
     * failing. It probes the transport first and only rebuilds the listener
     * when it is missing. If SSH answers and the listener is alive, the remote
     * OpenCode endpoint is the problem, so the session is left alone and the
     * OpenCode retry loop keeps trying.
     */
    fun requestRecovery(hostId: String, reason: String) {
        if (!desired.contains(hostId)) return
        val now = System.currentTimeMillis()
        if (now - (lastRecoveryAt[hostId] ?: 0L) < RECOVERY_COOLDOWN_MS) return
        if (!recoveryInFlight.add(hostId)) return
        lastRecoveryAt[hostId] = now
        Diagnostics.event("ssh", "recovery start host=$hostId reason=$reason")
        scope.launch {
            try {
                val connection = connections[hostId]
                if (connection == null || !connection.isAlive) {
                    wakeAndReconnect(hostId, "recovery: session down ($reason)")
                    return@launch
                }
                val probeOk = runCatching {
                    connection.exec("true", PROBE_TIMEOUT_MS).isSuccess
                }.getOrDefault(false)
                if (!probeOk) {
                    wakeAndReconnect(hostId, "recovery: probe failed ($reason)")
                    return@launch
                }
                val host = vault.host(hostId) ?: return@launch
                if (host.openCodeMode != OpenCodeMode.SSH_TUNNEL) return@launch

                if (forwards[hostId]?.isOpen == true) {
                    // The transport answered and the listener is alive: the
                    // failed channels are the remote OpenCode endpoint, not
                    // SSH. Its own retry loop owns recovery from here; redialing
                    // SSH would only disturb a working session.
                    Diagnostics.event(
                        "ssh",
                        "recovery probe ok host=$hostId; tunnel healthy ($reason)"
                    )
                    return@launch
                }
                forwards.remove(hostId)?.close()
                val message = startTunnelIfNeeded(host, connection)
                setState(stateFor(hostId).copy(message = message))
                Diagnostics.event("ssh", "recovery rebuilt tunnel host=$hostId")
            } finally {
                recoveryInFlight.remove(hostId)
            }
        }
    }

    /** A new default network appeared; retry immediately instead of waiting out the backoff. */
    fun onNetworkAvailable() {
        Diagnostics.event("ssh", "network available")
        probeFailures.clear()
        lastProbeAt.clear()
        if (desired.isEmpty()) return
        desired.forEach { hostId ->
            if (stateFor(hostId).status == ConnectionState.Status.CONNECTED) {
                verifyConnections(force = true)
            } else {
                wakeAndReconnect(hostId, "network available")
            }
        }
    }

    fun stateFor(hostId: String): ConnectionState =
        _states.value[hostId] ?: ConnectionState(hostId, ConnectionState.Status.DISCONNECTED)

    fun connection(hostId: String): SshConnection? = connections[hostId]

    fun forward(hostId: String): LocalForward? = forwards[hostId]

    suspend fun connect(hostId: String, autoTrust: Boolean = false): ConnectionState {
        // A watchdog redial or another tap already owns this host.
        if (!inFlight.add(hostId)) return stateFor(hostId)
        return try {
            connectInternal(hostId, autoTrust)
        } finally {
            inFlight.remove(hostId)
        }
    }

    private suspend fun connectInternal(hostId: String, autoTrust: Boolean): ConnectionState {
        val before = stateFor(hostId)
        if (before.status == ConnectionState.Status.CONNECTING) return before
        val host = vault.host(hostId)
            ?: return fail(hostId, "Host not found")
        val existing = connections[hostId]
        if (existing?.isAlive == true) {
            // Already connected: make sure the tunnel exists and report the
            // current state instead of dialing a second session.
            if (host.openCodeMode == OpenCodeMode.SSH_TUNNEL && forwards[hostId]?.isOpen != true) {
                setState(before.copy(message = startTunnelIfNeeded(host, existing)))
            }
            return stateFor(hostId)
        }
        val key = vault.key(host.keyId)
        if (host.authType == dev.pvps.data.model.AuthType.KEY && key == null) {
            return fail(hostId, "No SSH key selected for this host")
        }

        val wasDesired = desired.contains(hostId)
        val reconnecting = wasDesired || before.status == ConnectionState.Status.RECONNECTING
        setState(
            ConnectionState(
                hostId,
                if (reconnecting) ConnectionState.Status.RECONNECTING else ConnectionState.Status.CONNECTING,
                "Connecting to ${host.sshHost}:${host.sshPort}"
            )
        )
        val started = System.nanoTime()
        Diagnostics.event("ssh", "connect host=${host.name} ${host.sshHost}:${host.sshPort}")
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
                if (!wasDesired) {
                    setState(ConnectionState(hostId, ConnectionState.Status.DISCONNECTED))
                }
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
                desired += hostId
                probeFailures.remove(hostId)
                reconnectAttempts.remove(hostId)
                lastProbeAt.remove(hostId)
                if (host.hostKeySha256.isNullOrBlank() && outcome.hostKeySha256.isNotBlank()) {
                    vault.setHostKey(hostId, outcome.hostKeySha256)
                }
                vault.markConnected(hostId)
                val tunnelMessage = startTunnelIfNeeded(host, connection)
                val elapsed = (System.nanoTime() - started) / 1_000_000
                Diagnostics.event(
                    "ssh",
                    "connected host=${host.name} ${elapsed}ms $tunnelMessage"
                )
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
                if (reconnecting) desired.remove(hostId)
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
                desired.remove(hostId)
                fail(
                    hostId,
                    "HOST KEY CHANGED. Possible MITM. Expected ${host.hostKeySha256}, got ${outcome.hostKeySha256}",
                    hostKeySha256 = outcome.hostKeySha256
                )
            }
            is ConnectOutcome.Failed -> {
                connection.disconnect()
                val elapsed = (System.nanoTime() - started) / 1_000_000
                Diagnostics.event(
                    "ssh",
                    "connect failed host=${host.name} ${elapsed}ms ${outcome.message}"
                )
                if (reconnecting) {
                    // Keep the retry loop alive; report the reason without
                    // flipping the whole host to a dead ERROR state.
                    val state = ConnectionState(
                        hostId = hostId,
                        status = ConnectionState.Status.RECONNECTING,
                        message = outcome.message
                    )
                    setState(state)
                    state
                } else {
                    fail(hostId, outcome.message)
                }
            }
        }
    }

    suspend fun trustHostKey(hostId: String): ConnectionState {
        val fingerprint = stateFor(hostId).hostKeySha256
            ?: return fail(hostId, "No pending host key fingerprint")
        vault.setHostKey(hostId, fingerprint)
        return connect(hostId, autoTrust = true)
    }

    private fun wakeAndReconnect(hostId: String, reason: String) {
        if (!desired.contains(hostId)) return
        if (loopActive.contains(hostId)) {
            // The retry loop is sleeping; cut the backoff short.
            wakeups[hostId]?.trySend(Unit)
            return
        }
        startReconnectLoop(hostId, reason)
    }

    private fun startReconnectLoop(hostId: String, reason: String) {
        // inFlight only covers an actual dial. Keeping it out of the backoff
        // sleep lets a user tap dial immediately instead of being ignored.
        if (!loopActive.add(hostId)) return
        val wakeup = Channel<Unit>(Channel.CONFLATED)
        wakeups[hostId] = wakeup
        Diagnostics.event("ssh", "reconnect host=$hostId reason=$reason")
        scope.launch {
            try {
                var attempt = reconnectAttempts.getOrDefault(hostId, 0)
                while (isActive && desired.contains(hostId)) {
                    if (stateFor(hostId).status == ConnectionState.Status.CONNECTED) break
                    val baseWait = reconnectDelayMs(attempt)
                    val waitMs = reconnectDelayMs(
                        attempt,
                        Random.nextLong((baseWait / 4).coerceAtLeast(1L))
                    )
                    setState(
                        ConnectionState(
                            hostId,
                            ConnectionState.Status.RECONNECTING,
                            "Reconnecting… (${waitMs / 1000}s)"
                        )
                    )
                    // A network change must not wait out a 45s backoff.
                    withTimeoutOrNull(waitMs) { wakeup.receive() }
                    if (!desired.contains(hostId)) break
                    if (stateFor(hostId).status == ConnectionState.Status.CONNECTED) break
                    if (!inFlight.add(hostId)) {
                        // The user tapped Connect while we were waiting.
                        delay(500)
                        continue
                    }
                    val result = try {
                        connectInternal(hostId, autoTrust = false)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Diagnostics.event("ssh", "reconnect attempt failed", e)
                        ConnectionState(
                            hostId,
                            ConnectionState.Status.RECONNECTING,
                            e.message ?: "Reconnect failed"
                        )
                    } finally {
                        inFlight.remove(hostId)
                    }
                    when (result.status) {
                        ConnectionState.Status.CONNECTED -> break
                        ConnectionState.Status.NEEDS_HOST_KEY,
                        ConnectionState.Status.ERROR -> break
                        else -> attempt += 1
                    }
                    reconnectAttempts[hostId] = attempt
                }
            } finally {
                loopActive.remove(hostId)
                wakeups.remove(hostId, wakeup)
            }
        }
    }

    fun disconnect(hostId: String) {
        // Invalidate any connect attempt that is still suspended.
        desired.remove(hostId)
        probeFailures.remove(hostId)
        reconnectAttempts.remove(hostId)
        activeAttempts.remove(hostId)
        wakeups[hostId]?.trySend(Unit)
        // Keep the preferred tunnel port even across an explicit disconnect.
        // Forgetting it would let the next connect silently move to a new
        // port while an OpenCode client may still name the old one.
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
        desired.clear()
        probeFailures.clear()
        reconnectAttempts.clear()
        activeAttempts.clear()
        wakeups.values.forEach { it.trySend(Unit) }
        forwards.keys.toList().forEach { forwards.remove(it)?.close() }
        connections.keys.toList().forEach { connections.remove(it)?.disconnect() }
        _states.value = _states.value.mapValues { (id, _) ->
            ConnectionState(id, ConnectionState.Status.DISCONNECTED)
        }
    }

    // Trust boundary: command output comes from the remote SSH host. Keep the
    // byte caps explicit here so every caller inherits the same bounded reads.
    suspend fun exec(
        hostId: String,
        command: String,
        timeoutMs: Long = 30_000,
        maxOutputBytes: Long = SshConnection.DEFAULT_MAX_OUTPUT_BYTES,
        maxErrorBytes: Long = SshConnection.DEFAULT_MAX_ERROR_BYTES
    ): CommandResult =
        withContext(Dispatchers.IO) {
            val connection = connections[hostId] ?: error("Host is not connected")
            connection.exec(command, timeoutMs, maxOutputBytes, maxErrorBytes)
        }

    fun openShell(hostId: String, cols: Int, rows: Int, cellWidth: Int = 8, cellHeight: Int = 16): ShellChannel {
        val connection = connections[hostId] ?: error("Host is not connected")
        return connection.openShell(cols, rows, cellWidth, cellHeight)
    }

    fun openSftp(hostId: String) = connections[hostId]?.openSftp() ?: error("Host is not connected")

    fun openCodeBaseUrl(host: Host): String {
        return when (host.openCodeMode) {
            OpenCodeMode.SSH_TUNNEL -> {
                val forward = forwards[host.id]?.takeIf { it.isOpen }
                    ?: error("OpenCode tunnel is not ready. Reconnect the host.")
                "http://127.0.0.1:${forward.port}"
            }
            OpenCodeMode.DIRECT -> "http://${host.effectiveOpenCodeHost}:${host.openCodePort}"
        }
    }

    private fun startTunnelIfNeeded(host: Host, connection: SshConnection): String {
        if (host.openCodeMode != OpenCodeMode.SSH_TUNNEL) return "SSH connected"
        val existing = forwards[host.id]
        if (existing?.isOpen == true) {
            return "SSH connected · OpenCode tunnel :${existing.port}"
        }
        return runCatching {
            forwards.remove(host.id)?.close()
            // Reuse the previous local port after a tunnel rebuild. The
            // OpenCode client (and its SSE stream) can then survive a network
            // handoff without the repository wiping its transcript.
            //
            // Trust boundary: if that exact port is unavailable, LocalForward
            // fails instead of silently choosing a new port. A silent move
            // would leave the configured OpenCode client (with its Basic
            // credential) pointed at the old, now attacker-claimable loopback
            // port. The repository rebinds when the tunnel state changes.
            lastTunnelAttemptAt[host.id] = System.currentTimeMillis()
        val preferred = tunnelPorts[host.id] ?: 0
            var created: LocalForward? = null
            val forward = connection.openForward("127.0.0.1", host.openCodePort, preferred) { reason ->
                if (forwards[host.id] === created) forwards.remove(host.id)
                requestRecovery(host.id, reason)
            }
            created = forward
            tunnelPorts[host.id] = forward.port
            forwards[host.id] = forward
            lastProbeAt.remove(host.id)
            Diagnostics.event(
                "ssh",
                "tunnel host=${host.name} local=${forward.port} remote=${host.openCodePort}"
            )
            "SSH connected · OpenCode tunnel :${forward.port}"
        }.getOrElse { error ->
            Diagnostics.event("ssh", "tunnel failed host=${host.name}", error)
            "SSH connected · OpenCode tunnel failed: ${error.message}"
        }
    }

    private fun setState(state: ConnectionState) {
        _states.update { it + (state.hostId to state) }
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
