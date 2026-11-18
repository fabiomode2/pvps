package dev.pvps.data.store

import android.content.Context
import dev.pvps.data.model.AuditEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Append-only audit trail.
 *
 * Loading and writing happen off the main thread: the old constructor parsed up
 * to 512 KB of JSON lines before the first frame, and every connect/disconnect
 * paid an fsync on whatever thread called it.
 */
class AuditStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "audit.jsonl")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fileMutex = Mutex()
    private val writes = Channel<Unit>(Channel.CONFLATED)
    private val _entries = MutableStateFlow<List<AuditEntry>>(emptyList())
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            // Load first, then drain writes, so an early connect cannot write
            // a one-line file before the existing history has been read.
            val loaded = load()
            _entries.update { current ->
                if (current.isEmpty()) loaded else (loaded + current).takeLast(MAX_ENTRIES)
            }
            for (ignored in writes) {
                fileMutex.withLock {
                    runCatching { rewrite(_entries.value) }
                }
            }
        }
    }

    fun add(entry: AuditEntry) {
        _entries.update { (it + entry).takeLast(MAX_ENTRIES) }
        writes.trySend(Unit)
    }

    fun clear() {
        _entries.value = emptyList()
        writes.trySend(Unit)
    }

    private fun rewrite(entries: List<AuditEntry>) {
        if (entries.isEmpty()) {
            file.delete()
            return
        }
        val text = entries.joinToString("\n") {
            json.encodeToString(AuditEntry.serializer(), it)
        } + "\n"
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.writeText(text)
            tmp.delete()
        }
    }

    private fun load(): List<AuditEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            file.readLines()
                .mapNotNull { line ->
                    runCatching { json.decodeFromString(AuditEntry.serializer(), line) }.getOrNull()
                }
                .takeLast(MAX_ENTRIES)
        }.getOrDefault(emptyList())
    }

    companion object {
        private const val MAX_ENTRIES = 500
    }
}
