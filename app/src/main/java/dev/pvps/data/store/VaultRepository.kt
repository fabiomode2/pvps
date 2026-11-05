package dev.pvps.data.store

import dev.pvps.data.model.Host
import dev.pvps.data.model.SecureData
import dev.pvps.data.model.SshKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class VaultRepository(private val store: SecureStore) {

    private val initial = store.load()
    private val _hosts = MutableStateFlow(initial.hosts)
    private val _keys = MutableStateFlow(initial.keys)

    val hosts: StateFlow<List<Host>> = _hosts.asStateFlow()
    val keys: StateFlow<List<SshKey>> = _keys.asStateFlow()

    fun host(id: String?): Host? = _hosts.value.firstOrNull { it.id == id }
    fun key(id: String?): SshKey? = _keys.value.firstOrNull { it.id == id }

    fun upsertHost(host: Host) {
        val next = _hosts.value.filterNot { it.id == host.id } + host
        _hosts.value = next
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
        store.save(SecureData(hosts = _hosts.value, keys = _keys.value))
    }
}
