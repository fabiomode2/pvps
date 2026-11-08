package dev.pvps.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.pvps.AppContainer
import dev.pvps.data.model.AppSettings
import dev.pvps.data.model.Host
import dev.pvps.data.model.SshKey
import dev.pvps.service.ConnectionService
import dev.pvps.ssh.KeyTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AppViewModel(val container: AppContainer) : ViewModel() {

    val hosts = container.vault.hosts
    val keys = container.vault.keys
    val settings = container.settings.settings
    val audit = container.audit.entries
    val connectionStates = container.ssh.states
    val openCodeState = container.openCode.state
    val system = container.system
    val updateState = container.updates.state
    val needsUnknownSources = container.updates.needsUnknownSources

    private val _selectedHostId = MutableStateFlow<String?>(null)
    val selectedHostId: StateFlow<String?> = _selectedHostId

    private val _pendingOpenSession = MutableStateFlow<String?>(null)
    /** Session requested by a notification tap; consumed by ChatScreen. */
    val pendingOpenSession: StateFlow<String?> = _pendingOpenSession.asStateFlow()

    val selectedHost: StateFlow<Host?> = combine(hosts, _selectedHostId) { list, id ->
        list.firstOrNull { it.id == id } ?: list.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            hosts.collect { list ->
                val current = _selectedHostId.value
                if (current == null || list.none { it.id == current }) {
                    _selectedHostId.value = list.firstOrNull()?.id
                }
            }
        }
    }

    fun selectHost(id: String) {
        _selectedHostId.value = id
    }

    fun requestOpenSession(sessionId: String?) {
        if (!sessionId.isNullOrBlank()) _pendingOpenSession.value = sessionId
    }

    fun consumePendingOpenSession() {
        _pendingOpenSession.value = null
    }

    fun connect(hostId: String) {
        ConnectionService.start(container.appContext)
        viewModelScope.launch {
            val state = container.ssh.connect(hostId)
            if (state.status == dev.pvps.data.model.ConnectionState.Status.CONNECTED) {
                val connected = container.ssh.states.value.values.count {
                    it.status == dev.pvps.data.model.ConnectionState.Status.CONNECTED
                }
                val name = container.vault.host(hostId)?.name ?: "host"
                ConnectionService.update(
                    container.appContext,
                    "$name · tunnel ready · $connected connected"
                )
                // Rebind OpenCode even when the user is on another tab.
                container.openCode.onResume()
            } else {
                stopServiceIfIdle()
            }
        }
    }

    fun connectSelected() {
        selectedHost.value?.let { connect(it.id) }
    }

    fun disconnect(hostId: String) {
        container.ssh.disconnect(hostId)
        if (container.openCode.isBoundTo(hostId)) {
            // The OpenCode client points at a tunnel that no longer exists.
            container.openCode.disconnect()
        }
        stopServiceIfIdle()
    }

    fun disconnectAll() {
        container.ssh.disconnectAll()
        container.openCode.disconnect()
        ConnectionService.stop(container.appContext)
    }

    fun trustHostKey(hostId: String) {
        ConnectionService.start(container.appContext)
        viewModelScope.launch {
            val state = container.ssh.trustHostKey(hostId)
            if (state.status != dev.pvps.data.model.ConnectionState.Status.CONNECTED) {
                stopServiceIfIdle()
            }
        }
    }

    fun saveHost(host: Host) {
        container.vault.upsertHost(host)
        _selectedHostId.value = host.id
    }

    fun deleteHost(hostId: String) {
        // Full teardown also stops the tunnel, OpenCode client, and foreground service.
        disconnect(hostId)
        container.vault.deleteHost(hostId)
    }

    fun clearHostKey(hostId: String) {
        val host = container.vault.host(hostId) ?: return
        container.vault.upsertHost(host.copy(hostKeySha256 = null))
    }

    fun saveKey(key: SshKey) {
        container.vault.upsertKey(key)
    }

    fun deleteKey(keyId: String) {
        container.vault.deleteKey(keyId)
    }

    suspend fun generateKey(name: String, passphrase: String, rsa: Boolean = false): SshKey =
        withContext(Dispatchers.IO) {
            val key = if (rsa) KeyTools.generateRsa(name, passphrase) else KeyTools.generateEd25519(name, passphrase)
            container.vault.upsertKey(key)
            key
        }

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        container.settings.update(transform)
    }

    fun connectOpenCode(host: Host, force: Boolean = false) {
        viewModelScope.launch { container.openCode.connect(host, force) }
    }

    fun disconnectOpenCode() {
        container.openCode.disconnect()
    }

    fun clearAudit() {
        container.audit.clear()
    }

    fun checkForUpdates(force: Boolean = false) {
        viewModelScope.launch { container.updates.check(force) }
    }

    fun downloadUpdate(info: dev.pvps.update.UpdateInfo) {
        viewModelScope.launch { container.updates.downloadAndInstall(info) }
    }

    fun installReadyUpdate() {
        container.updates.installReady()
    }

    fun dismissUpdate() {
        container.updates.dismiss()
    }

    fun openUpdateSettings() {
        container.updates.openUnknownSourcesSettings()
    }

    fun onResume() {
        // Reconnect any session that died while the app was backgrounded, and
        // force an immediate probe after a network handoff.
        container.ssh.verifyConnections(force = true)
        // App-scoped supervisor: rebinds OpenCode as soon as the tunnel is back,
        // even when the Chat tab is not composed.
        container.openCode.onResume()
        container.updates.refreshUnknownSources()
        if (container.updates.state.value is dev.pvps.update.UpdateState.ReadyToInstall &&
            !container.updates.needsUnknownSources.value
        ) {
            container.updates.installReady()
        }
    }

    private fun stopServiceIfIdle() {
        // Connected hosts keep the foreground service (and its wake lock)
        // alive. An initial connect that failed has no desired host yet, so the
        // notification is not left running for nothing.
        if (!container.ssh.hasActiveSessions) ConnectionService.stop(container.appContext)
    }

    override fun onCleared() {
        super.onCleared()
        // SSH and OpenCode are application-scoped on purpose: an Activity
        // recreation, rotation, or backgrounding must not kill long-lived
        // tunnels or the SSE event stream.
    }
}

class AppViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
}
