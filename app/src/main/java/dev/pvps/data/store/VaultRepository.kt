package dev.pvps.data.store

import dev.pvps.data.model.Host
import dev.pvps.data.model.SecureData
import dev.pvps.data.model.SshKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VaultRepository(private val store: SecureStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<Unit>(Channel.CONFLATED)

    private val _hosts = MutableStateFlow<List<Host>>(emptyList())
    private val _keys = MutableStateFlow<List<SshKey>>(emptyList())
    private val _ready = MutableStateFlow(false)

    val hosts: StateFlow<List<Host>> = _hosts.asStateFlow()
    val keys: StateFlow<List<SshKey>> = _keys.asStateFlow()
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    init {
        // Keystore decrypt + JSON parse used to run inside Application.onCreate
        // on the main thread. The UI gates on `ready` for the few frames it takes.
        scope.launch {
            val initial = store.load()
            _hosts.value = initial.hosts
            _keys.value = initial.keys
            _ready.value = true
        }
        scope.launch {
            for (ignored in writes) {
                runCatching { store.save(SecureData(hosts = _hosts.value, keys = _keys.value)) }
            }
        }
    }

    fun host(id: String?): Host? = _hosts.value.firstOrNull { it.id == id }
    fun key(id: String?): SshKey? = _keys.value.firstOrNull { it.id == id }

    fun upsertHost(host: Host) {
        _hosts.value = _hosts.value.filterNot { it.id == host.id } + host
        persist()
    }

    fun deleteHost(id: String) {
        _hosts.value = _hosts.value.filterNot { it.id == id }
        persist()
    }

    fun upsertKey(key: SshKey) {
        _keys.value = _keys.value.filterNot { it.id == key.id } + key
        persist()
    }

    fun deleteKey(id: String) {
        _keys.value = _keys.value.filterNot { it.id == id }
        _hosts.value = _hosts.value.map { if (it.keyId == id) it.copy(keyId = null) else it }
        persist()
    }

    fun setHostKey(hostId: String, sha256: String) {
        val host = host(hostId) ?: return
        upsertHost(host.copy(hostKeySha256 = sha256))
    }

    fun markConnected(hostId: String) {
        val host = host(hostId) ?: return
        upsertHost(host.copy(lastConnectedAt = System.currentTimeMillis()))
    }

    private fun persist() {
        writes.trySend(Unit)
    }
}
