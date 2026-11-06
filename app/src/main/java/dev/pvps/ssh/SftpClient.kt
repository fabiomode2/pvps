package dev.pvps.ssh

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.SftpATTRS
import dev.pvps.security.SecuritySupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream

private const val MAX_LIST_ENTRIES = 20_000

data class RemoteFile(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Long,
    val permissions: String
)

class SftpClient(private val connection: SshConnection) {

    suspend fun list(path: String): List<RemoteFile> = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            val normalized = normalize(path)
            val entries = sftp.ls(normalized)
            if (entries.size > MAX_LIST_ENTRIES) {
                throw IOException(
                    "Directory contains more than $MAX_LIST_ENTRIES entries; narrow the path or use the terminal"
                )
            }
            entries.asSequence()
                .map { it as ChannelSftp.LsEntry }
                .filter { it.filename != "." && it.filename != ".." }
                .mapNotNull { entry ->
                    // A server-controlled filename is a single path component.
                    // Drop names that can escape the browsed directory instead
                    // of letting them steer currentPath or a later operation.
                    val safeName = runCatching {
                        SecuritySupport.safeLeafName(entry.filename)
                    }.getOrNull() ?: return@mapNotNull null
                    val attrs = entry.attrs
                    RemoteFile(
                        name = safeName,
                        path = join(normalized, safeName),
                        isDirectory = attrs?.isDir == true,
                        size = attrs?.size ?: 0L,
                        modified = (attrs?.mTime ?: 0) * 1000L,
                        permissions = attrs?.permissionsString.orEmpty()
                    )
                }
                .sortedWith(compareByDescending<RemoteFile> { it.isDirectory }.thenBy { it.name.lowercase() })
                .toList()
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun stat(path: String): RemoteFile? = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            val attrs = sftp.lstat(normalize(path))
            RemoteFile(
                name = normalize(path).substringAfterLast('/').ifBlank { "/" },
                path = normalize(path),
                isDirectory = attrs.isDir,
                size = attrs.size,
                modified = attrs.mTime * 1000L,
                permissions = attrs.permissionsString
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun readText(path: String, maxBytes: Int = 256 * 1024): String = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            sftp.get(normalize(path)).use { input ->
                readLimited(input, maxBytes).toString(Charsets.UTF_8)
            }
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun download(
        path: String,
        destination: File,
        maxBytes: Long = SecuritySupport.MAX_DOWNLOAD_BYTES
    ): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val sftp = connection.openSftp()
        try {
            // Stream through a bounded copy instead of JSch's unbounded
            // get(remote, local) so a server cannot fill the app's storage.
            sftp.get(normalize(path)).use { input ->
                destination.outputStream().use { output ->
                    SecuritySupport.copyLimited(input, output, maxBytes)
                }
            }
            destination
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun upload(source: File, path: String) = withContext(Dispatchers.IO) {
        if (source.length() > SecuritySupport.MAX_UPLOAD_BYTES) {
            throw IOException(
                "File exceeds the ${SecuritySupport.MAX_UPLOAD_BYTES / (1024 * 1024)} MB upload limit"
            )
        }
        val sftp = connection.openSftp()
        try {
            sftp.put(source.absolutePath, normalize(path))
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun delete(path: String, directory: Boolean) = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            if (directory) sftp.rmdir(normalize(path)) else sftp.rm(normalize(path))
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun rename(from: String, to: String) = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            sftp.rename(normalize(from), normalize(to))
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun mkdir(path: String) = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            sftp.mkdir(normalize(path))
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun chmod(path: String, mode: Int) = withContext(Dispatchers.IO) {
        val sftp = connection.openSftp()
        try {
            sftp.chmod(mode, normalize(path))
        } finally {
            runCatching { sftp.disconnect() }
        }
    }

    suspend fun listTar(path: String): List<String> = withContext(Dispatchers.IO) {
        val result = connection.exec("tar -tzf ${ShellQuote.of(path)}", 30_000)
        if (!result.isSuccess) error(result.combined.ifBlank { "tar failed" })
        result.stdout.lineSequence().filter { it.isNotBlank() }.toList()
    }

    suspend fun createTar(directory: String, name: String, destination: String) = withContext(Dispatchers.IO) {
        // `--` ends tar option parsing, so a file name beginning with `-`
        // is always treated as an operand rather than an option.
        val result = connection.exec(
            "tar -czf ${ShellQuote.of(destination)} -C ${ShellQuote.of(directory)} -- ${ShellQuote.of(name)}",
            120_000
        )
        if (!result.isSuccess) error(result.combined.ifBlank { "tar failed" })
    }

    suspend fun extractTar(archive: String, destination: String) = withContext(Dispatchers.IO) {
        val entries = listTar(archive)
        val unsafe = entries.firstOrNull { entry ->
            entry.startsWith("/") || entry.split('/').any { it == ".." }
        }
        if (unsafe != null) error("Refusing to extract unsafe path: $unsafe")
        val result = connection.exec(
            "mkdir -p ${ShellQuote.of(destination)} && tar -xzf ${ShellQuote.of(archive)} -C ${ShellQuote.of(destination)} --no-same-owner --no-same-permissions",
            120_000
        )
        if (!result.isSuccess) error(result.combined.ifBlank { "tar failed" })
    }

    private fun normalize(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return "/"
        return if (trimmed.length > 1 && trimmed.endsWith('/')) trimmed.dropLast(1) else trimmed
    }

    private fun join(base: String, name: String): String =
        if (base == "/") "/$name" else "$base/$name"

    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(8 * 1024)
        val out = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < maxBytes) {
            val read = input.read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read < 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray()
    }
}

object ShellQuote {
    fun of(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
