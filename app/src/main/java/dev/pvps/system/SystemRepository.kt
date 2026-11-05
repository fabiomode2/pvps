package dev.pvps.system

import dev.pvps.data.model.AuditEntry
import dev.pvps.data.store.AuditStore
import dev.pvps.data.store.VaultRepository
import dev.pvps.ssh.ShellQuote
import dev.pvps.ssh.SshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

class SystemRepository(
    private val ssh: SshManager,
    private val audit: AuditStore,
    private val vault: VaultRepository
) {
    data class Snapshot(
        val hostname: String = "",
        val os: String = "",
        val kernel: String = "",
        val uptime: String = "",
        val load1: Double = 0.0,
        val load5: Double = 0.0,
        val load15: Double = 0.0,
        val cpuCount: Int = 1,
        val memTotal: Long = 0,
        val memUsed: Long = 0,
        val memAvailable: Long = 0,
        val diskTotal: Long = 0,
        val diskUsed: Long = 0,
        val diskAvailable: Long = 0,
        val diskPercent: Int = 0,
        val containersRunning: Int = 0,
        val containersTotal: Int = 0,
        val failedServices: Int = 0,
        val tailscaleIp: String = ""
    ) {
        val cpuPercent: Int get() = ((load1 / cpuCount.coerceAtLeast(1)) * 100).toInt().coerceIn(0, 100)
        val memPercent: Int get() = if (memTotal > 0) ((memUsed * 100) / memTotal).toInt() else 0
    }

    data class Container(
        val id: String,
        val name: String,
        val image: String,
        val state: String,
        val status: String,
        val ports: String,
        val running: Boolean
    )

    data class Service(
        val unit: String,
        val load: String,
        val active: String,
        val sub: String,
        val description: String
    ) {
        val isRunning: Boolean get() = active == "active"
        val isFailed: Boolean get() = active == "failed" || sub == "failed"
    }

    data class Process(
        val pid: Int,
        val ppid: Int,
        val user: String,
        val cpu: Double,
        val mem: Double,
        val rssKb: Long,
        val state: String,
        val elapsed: String,
        val command: String,
        val args: String
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun snapshot(hostId: String): Snapshot = withContext(Dispatchers.IO) {
        val script = """
            export LC_ALL=C
            echo '@@HOST'; hostname 2>/dev/null
            echo '@@OS'; (. /etc/os-release 2>/dev/null && echo "${'$'}PRETTY_NAME")
            echo '@@KERNEL'; uname -r 2>/dev/null
            echo '@@UPTIME'; uptime -p 2>/dev/null || uptime 2>/dev/null
            echo '@@LOAD'; cat /proc/loadavg 2>/dev/null
            echo '@@CPU'; nproc 2>/dev/null
            echo '@@MEM'; free -b 2>/dev/null | awk '/^Mem:/{print ${'$'}2, ${'$'}3, ${'$'}4, ${'$'}7}'
            echo '@@DISK'; df -P / 2>/dev/null | awk 'NR==2{print ${'$'}2, ${'$'}3, ${'$'}4, ${'$'}5}'
            echo '@@CONTAINERS'; docker ps -q 2>/dev/null | wc -l; docker ps -aq 2>/dev/null | wc -l
            echo '@@FAILED'; systemctl --failed --no-legend 2>/dev/null | wc -l
            echo '@@TAILSCALE'; tailscale ip -4 2>/dev/null | head -1
            echo '@@END'
        """.trimIndent()
        val result = ssh.exec(hostId, script, 30_000)
        if (!result.isSuccess) error(result.combined.ifBlank { "Could not read system status" })
        val sections = parseSections(result.stdout)
        val load = sections["LOAD"].orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
        val mem = sections["MEM"].orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
        val disk = sections["DISK"].orEmpty().split(Regex("\\s+")).filter { it.isNotBlank() }
        val containers = sections["CONTAINERS"].orEmpty().lines().filter { it.isNotBlank() }
        Snapshot(
            hostname = sections["HOST"].orEmpty().trim(),
            os = sections["OS"].orEmpty().trim(),
            kernel = sections["KERNEL"].orEmpty().trim(),
            uptime = sections["UPTIME"].orEmpty().trim(),
            load1 = load.getOrNull(0)?.toDoubleOrNull() ?: 0.0,
            load5 = load.getOrNull(1)?.toDoubleOrNull() ?: 0.0,
            load15 = load.getOrNull(2)?.toDoubleOrNull() ?: 0.0,
            cpuCount = sections["CPU"].orEmpty().trim().toIntOrNull() ?: 1,
            memTotal = mem.getOrNull(0)?.toLongOrNull() ?: 0,
            memUsed = mem.getOrNull(1)?.toLongOrNull() ?: 0,
            memAvailable = mem.getOrNull(3)?.toLongOrNull() ?: 0,
            diskTotal = disk.getOrNull(0)?.toLongOrNull() ?: 0,
            diskUsed = disk.getOrNull(1)?.toLongOrNull() ?: 0,
            diskAvailable = disk.getOrNull(2)?.toLongOrNull() ?: 0,
            diskPercent = disk.getOrNull(3)?.removeSuffix("%")?.toIntOrNull() ?: 0,
            containersRunning = containers.getOrNull(0)?.trim()?.toIntOrNull() ?: 0,
            containersTotal = containers.getOrNull(1)?.trim()?.toIntOrNull() ?: 0,
            failedServices = sections["FAILED"].orEmpty().trim().toIntOrNull() ?: 0,
            tailscaleIp = sections["TAILSCALE"].orEmpty().trim()
        )
    }

    suspend fun containers(hostId: String): List<Container> = withContext(Dispatchers.IO) {
        val result = ssh.exec(hostId, "docker ps -a --format '{{json .}}' 2>/dev/null", 30_000)
        if (!result.isSuccess) return@withContext emptyList()
        result.stdout.lineSequence().mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            runCatching {
                val obj = json.parseToJsonElement(line) as JsonObject
                Container(
                    id = obj.string("ID"),
                    name = obj.string("Names"),
                    image = obj.string("Image"),
                    state = obj.string("State"),
                    status = obj.string("Status"),
                    ports = obj.string("Ports"),
                    running = obj.string("State").equals("running", ignoreCase = true)
                )
            }.getOrNull()
        }.sortedWith(compareByDescending<Container> { it.running }.thenBy { it.name }).toList()
    }

    suspend fun services(hostId: String): List<Service> = withContext(Dispatchers.IO) {
        val result = ssh.exec(
            hostId,
            "systemctl list-units --type=service --all --no-legend --no-pager 2>/dev/null",
            30_000
        )
        if (!result.isSuccess) return@withContext emptyList()
        result.stdout.lineSequence().mapNotNull { line ->
            val trimmed = line.trim().trimStart('●', '*', '○', '×', '!').trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            val parts = trimmed.split(Regex("\\s+"), limit = 5)
            if (parts.size < 4) return@mapNotNull null
            Service(
                unit = parts[0],
                load = parts[1],
                active = parts[2],
                sub = parts[3],
                description = parts.getOrElse(4) { "" }
            )
        }.sortedWith(
            compareByDescending<Service> { it.isFailed }
                .thenByDescending { it.isRunning }
                .thenBy { it.unit }
        ).toList()
    }

    suspend fun processes(hostId: String): List<Process> = withContext(Dispatchers.IO) {
        val result = ssh.exec(
            hostId,
            "ps -eo pid=,ppid=,user=,pcpu=,pmem=,rss=,stat=,etime=,comm=,args= --sort=-pcpu 2>/dev/null",
            30_000
        )
        if (!result.isSuccess) return@withContext emptyList()
        result.stdout.lineSequence().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 10)
            if (parts.size < 10) return@mapNotNull null
            Process(
                pid = parts[0].toIntOrNull() ?: return@mapNotNull null,
                ppid = parts[1].toIntOrNull() ?: 0,
                user = parts[2],
                cpu = parts[3].toDoubleOrNull() ?: 0.0,
                mem = parts[4].toDoubleOrNull() ?: 0.0,
                rssKb = parts[5].toLongOrNull() ?: 0,
                state = parts[6],
                elapsed = parts[7],
                command = parts[8],
                args = parts[9]
            )
        }.toList()
    }

    suspend fun serviceLogs(hostId: String, unit: String, lines: Int = 200): String = withContext(Dispatchers.IO) {
        require(unit.matches(Regex("[A-Za-z0-9_.@:-]+"))) { "Invalid unit name" }
        val result = ssh.exec(
            hostId,
            "journalctl -u ${ShellQuote.of(unit)} -n ${lines.coerceIn(10, 2000)} --no-pager 2>&1",
            45_000
        )
        result.combined
    }

    suspend fun containerLogs(hostId: String, name: String, lines: Int = 200): String = withContext(Dispatchers.IO) {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*"))) { "Invalid container name" }
        val result = ssh.exec(
            hostId,
            "docker logs --tail ${lines.coerceIn(10, 2000)} ${ShellQuote.of(name)} 2>&1",
            45_000
        )
        result.combined
    }

    suspend fun serviceAction(hostId: String, unit: String, action: String): String = withContext(Dispatchers.IO) {
        require(unit.matches(Regex("[A-Za-z0-9_.@:-]+"))) { "Invalid unit name" }
        require(action in setOf("start", "stop", "restart", "reload", "enable", "disable")) { "Invalid action" }
        val result = ssh.exec(
            hostId,
            "sudo -n systemctl $action ${ShellQuote.of(unit)} 2>&1",
            45_000
        )
        record(hostId, "systemctl $action", unit, result.isSuccess)
        if (!result.isSuccess) error(result.combined.ifBlank { "systemctl failed" })
        result.combined.ifBlank { "OK" }
    }

    suspend fun containerAction(hostId: String, name: String, action: String): String = withContext(Dispatchers.IO) {
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]*"))) { "Invalid container name" }
        require(action in setOf("start", "stop", "restart", "pause", "unpause")) { "Invalid action" }
        val result = ssh.exec(
            hostId,
            "docker $action ${ShellQuote.of(name)} 2>&1",
            60_000
        )
        record(hostId, "docker $action", name, result.isSuccess)
        if (!result.isSuccess) error(result.combined.ifBlank { "docker failed" })
        result.combined.ifBlank { "OK" }
    }

    suspend fun killProcess(hostId: String, pid: Int, force: Boolean): String = withContext(Dispatchers.IO) {
        require(pid > 1) { "Refusing to kill PID $pid" }
        val signal = if (force) "KILL" else "TERM"
        val result = ssh.exec(hostId, "kill -$signal $pid 2>&1", 15_000)
        record(hostId, "kill -$signal", pid.toString(), result.isSuccess)
        if (!result.isSuccess) error(result.combined.ifBlank { "kill failed" })
        "Signal $signal sent to $pid"
    }

    private fun record(hostId: String, action: String, target: String, success: Boolean) {
        audit.add(
            AuditEntry(
                hostName = vault.host(hostId)?.name ?: hostId,
                action = action,
                detail = target,
                success = success
            )
        )
    }

    private fun parseSections(output: String): Map<String, String> {
        val result = linkedMapOf<String, String>()
        var current: String? = null
        val buffer = StringBuilder()
        fun flush() {
            current?.let { result[it] = buffer.toString().trim() }
            buffer.clear()
        }
        output.lineSequence().forEach { line ->
            if (line.startsWith("@@")) {
                flush()
                current = line.removePrefix("@@").trim()
            } else {
                if (buffer.isNotEmpty()) buffer.append('\n')
                buffer.append(line)
            }
        }
        flush()
        return result
    }

    private fun JsonObject.string(key: String): String =
        (this[key]?.jsonPrimitive?.contentOrNull).orEmpty()
}
