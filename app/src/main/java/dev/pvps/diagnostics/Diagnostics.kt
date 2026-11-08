package dev.pvps.diagnostics

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Bounded, redacted on-device log used by Settings -> Diagnostics.
 *
 * The log is deliberately small: a 900-line memory ring plus two rotating
 * 1 MiB files. That is enough to diagnose tunnel/SSE/queue failures without
 * letting a long-running foreground service fill the phone.
 */
object Diagnostics {
    private const val MAX_MEMORY_LINES = 900
    private const val MAX_FILE_BYTES = 1_048_576L
    private const val TAG = "PVPS"

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private val stamp = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            if (logFile != null) return
            val dir = File(context.filesDir, "diagnostics").apply { mkdirs() }
            logFile = File(dir, "pvps.log")
        }
        event("app", "diagnostics started")
    }

    fun event(tag: String, message: String, error: Throwable? = null) {
        val text = redact(
            buildString {
                append(tag)
                append(": ")
                append(message)
                if (error != null) {
                    append(" | ")
                    append(error.javaClass.simpleName)
                    append(": ")
                    append(error.message.orEmpty())
                }
            }
        )
        val line = "${stamp.format(Date())}  $text"
        Log.i(TAG, text.take(900))
        synchronized(lock) {
            if (lines.size >= MAX_MEMORY_LINES) lines.removeFirst()
            lines.addLast(line)
            appendToFileLocked(line)
        }
    }

    /**
     * Most recent lines, optionally only those emitted by [tags]. Used by the
     * link detail screen: the last few ssh/opencode events usually explain a
     * drop faster than the full log.
     */
    fun tail(limit: Int = 40, tags: Set<String>? = null): List<String> = synchronized(lock) {
        val filtered = if (tags == null) {
            lines.toList()
        } else {
            lines.filter { line -> tags.any { line.contains("  $it:") } }
        }
        filtered.takeLast(limit.coerceIn(1, MAX_MEMORY_LINES))
    }

    fun snapshot(): String = synchronized(lock) {
        if (lines.isEmpty()) "No diagnostics recorded yet." else lines.joinToString("\n")
    }

    fun exportText(): String = buildString {
        appendLine("PVPS diagnostics")
        appendLine("generated ${Date()}")
        appendLine()
        appendLine(snapshot())
        appendLine()
        appendLine("----- rotated log -----")
        val file = logFile
        if (file != null && file.exists()) {
            appendLine(file.readText().takeLast(400_000))
        }
    }

    fun clear() {
        synchronized(lock) {
            lines.clear()
            logFile?.delete()
            File(logFile?.parentFile, "pvps.log.1").delete()
        }
    }

    private fun appendToFileLocked(line: String) {
        val file = logFile ?: return
        runCatching {
            if (file.length() > MAX_FILE_BYTES) {
                val old = File(file.parentFile, "${file.name}.1")
                old.delete()
                file.renameTo(old)
            }
            file.appendText(line + "\n")
        }
    }

    private val secretPatterns = listOf(
        Regex("(?i)(password|passwd|token|secret|authorization|api[_-]?key)\\s*[=:]\\s*\\S+"),
        Regex("(?i)Basic\\s+[A-Za-z0-9+/=]+"),
        Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    )

    private fun redact(raw: String): String {
        var text = raw
        secretPatterns.forEach { pattern ->
            text = pattern.replace(text) { match ->
                val key = match.value.substringBefore('=').substringBefore(':').trim()
                "$key=[redacted]"
            }
        }
        return text.take(2_000)
    }
}
