package dev.pvps.data.store

import android.content.Context
import dev.pvps.data.model.AuditEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import java.io.File

class AuditStore(context: Context) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val file = File(context.filesDir, "audit.jsonl")
    private val _entries = MutableStateFlow(load())
    val entries: StateFlow<List<AuditEntry>> = _entries.asStateFlow()

    @Synchronized
    fun add(entry: AuditEntry) {
        val current = _entries.value
        val next = (current + entry).takeLast(MAX_ENTRIES)
        _entries.value = next
        runCatching {
            file.appendText(json.encodeToString(AuditEntry.serializer(), entry) + "\n")
            if (next.size == MAX_ENTRIES && file.length() > MAX_BYTES) {
                file.writeText(next.joinToString("\n") { json.encodeToString(AuditEntry.serializer(), it) } + "\n")
            }
        }
    }

    fun clear() {
        _entries.value = emptyList()
        runCatching { file.delete() }
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
        private const val MAX_BYTES = 512 * 1024L
    }
}
