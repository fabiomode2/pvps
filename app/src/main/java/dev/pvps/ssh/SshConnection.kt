package dev.pvps.ssh

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.JSchUnknownHostKeyException
import com.jcraft.jsch.Session
import dev.pvps.data.model.CommandResult
import dev.pvps.data.model.Host
import dev.pvps.data.model.SshKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.atomic.AtomicReference

sealed interface ConnectOutcome {
    data class Connected(val hostKeySha256: String) : ConnectOutcome
    data class NeedsHostKey(val hostKeySha256: String) : ConnectOutcome
    data class HostKeyChanged(val hostKeySha256: String) : ConnectOutcome
    data class Failed(val message: String) : ConnectOutcome
}

class SshConnection(
    private val host: Host,
    private val key: SshKey?,
    private val pinnedHostKey: String?,
    private val autoTrustHostKey: Boolean
) {

    private val jsch = JSch()
    private val seenHostKey = AtomicReference<String?>(null)

    @Volatile
    private var session: Session? = null

    val isConnected: Boolean
        get() = session?.isConnected == true

    suspend fun connect(): ConnectOutcome = withContext(Dispatchers.IO) {
        try {
            val s = jsch.getSession(host.sshUser, host.sshHost, host.sshPort)
            if (key != null) {
                val passphrase = key.passphrase.takeIf { it.isNotBlank() }?.toByteArray()
                val pub = key.publicKey.takeIf { it.isNotBlank() }?.toByteArray()
                jsch.addIdentity("cc-${key.id}", key.privateKey.toByteArray(Charsets.UTF_8), pub, passphrase)
                s.setConfig("PreferredAuthentications", "publickey")
            } else {
                s.setPassword(host.password)
                s.setConfig("PreferredAuthentications", "password,keyboard-interactive")
            }
            s.setConfig("StrictHostKeyChecking", "yes")
            s.hostKeyRepository = PinnedHostKeyRepository(
                pinnedSha256 = pinnedHostKey,
                autoTrust = autoTrustHostKey,
                onSeen = { seenHostKey.set(it) }
            )
            s.serverAliveInterval = 30_000
            s.serverAliveCountMax = 3
            s.connect(15_000)
            session = s

            val fingerprint = seenHostKey.get()
                ?: s.hostKey?.let { sha256Fingerprint(Base64.decode(it.key, Base64.DEFAULT)) }
                ?: ""
            ConnectOutcome.Connected(fingerprint)
        } catch (e: CancellationException) {
            throw e
        } catch (e: JSchUnknownHostKeyException) {
            ConnectOutcome.NeedsHostKey(seenHostKey.get().orEmpty())
        } catch (e: JSchChangedHostKeyException) {
            ConnectOutcome.HostKeyChanged(seenHostKey.get().orEmpty())
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            when {
                // JSch 2.28.x throws JSchUnknownHostKeyException("reject HostKey: ...")
                // when StrictHostKeyChecking=yes and the key is not pinned yet.
                message.contains("UnknownHostKey", ignoreCase = true) ||
                    message.contains("reject HostKey", ignoreCase = true) ->
                    ConnectOutcome.NeedsHostKey(seenHostKey.get().orEmpty())
                message.contains("HostKey has been changed", ignoreCase = true) ->
                    ConnectOutcome.HostKeyChanged(seenHostKey.get().orEmpty())
                else -> ConnectOutcome.Failed(
                    connectionErrorMessage(host, message.ifBlank { e.javaClass.simpleName })
                )
            }
        }
    }

    suspend fun exec(command: String, timeoutMs: Long = 30_000): CommandResult =
        withContext(Dispatchers.IO) {
            val s = session ?: error("SSH session is not connected")
            val channel = s.openChannel("exec") as ChannelExec
            val stdout = ByteArrayOutputStream()
            val stderr = ByteArrayOutputStream()
            val started = System.currentTimeMillis()
            coroutineScope {
                // copyTo() below is blocking and ignores coroutine cancellation.
                // A watchdog closes the channel so a hung remote command cannot
                // hold the UI forever.
                val watchdog = launch {
                    delay(timeoutMs)
                    runCatching { channel.disconnect() }
                }
                try {
                    channel.setCommand(command)
                    channel.setErrStream(stderr)
                    channel.setInputStream(null)
                    channel.setPty(false)
                    channel.connect(timeoutMs.toInt().coerceIn(1_000, 120_000))
                    channel.inputStream.use { input -> input.copyTo(stdout) }
                    while (!channel.isClosed) delay(15)
                    if (!watchdog.isActive) {
                        throw SocketTimeoutException("Command timed out after ${timeoutMs}ms")
                    }
                    CommandResult(
                        exitCode = channel.exitStatus,
                        stdout = stdout.toString(Charsets.UTF_8.name()),
                        stderr = stderr.toString(Charsets.UTF_8.name()),
                        durationMs = System.currentTimeMillis() - started
                    )
                } catch (e: Exception) {
                    // copyTo() usually fails with an IOException when the watchdog
                    // disconnects the channel; report the real cause.
                    if (!watchdog.isActive) {
                        throw SocketTimeoutException("Command timed out after ${timeoutMs}ms")
                    }
                    throw e
                } finally {
                    watchdog.cancel()
                    runCatching { channel.disconnect() }
                }
            }
        }

    fun openShell(cols: Int, rows: Int, cellWidthPx: Int = 8, cellHeightPx: Int = 16): ShellChannel {
        val s = session ?: error("SSH session is not connected")
        val channel = s.openChannel("shell") as ChannelShell
        channel.setPtyType("xterm-256color", cols, rows, cellWidthPx, cellHeightPx)
        channel.setPtySize(cols, rows, cellWidthPx, cellHeightPx)
        channel.setEnv("TERM", "xterm-256color")
        channel.setEnv("LANG", "C.UTF-8")
        channel.connect(15_000)
        return ShellChannel(channel)
    }

    fun openSftp(): ChannelSftp {
        val s = session ?: error("SSH session is not connected")
        return (s.openChannel("sftp") as ChannelSftp).apply { connect(15_000) }
    }

    fun openForward(remoteHost: String, remotePort: Int): LocalForward {
        val s = session ?: error("SSH session is not connected")
        return LocalForward(s, remoteHost, remotePort).also { it.start() }
    }

    fun disconnect() {
        runCatching { session?.disconnect() }
        session = null
    }
}

/** Tailscale uses 100.64.0.0/10 (100.64.x.x through 100.127.x.x). */
internal fun isTailscaleAddress(address: String): Boolean {
    val parts = address.trim().split('.')
    if (parts.size != 4) return false
    val first = parts[0].toIntOrNull() ?: return false
    val second = parts[1].toIntOrNull() ?: return false
    return first == 100 && second in 64..127
}

private fun connectionErrorMessage(host: Host, raw: String): String {
    val lower = raw.lowercase()
    val hint = when {
        lower.contains("ehostunreach") || lower.contains("no route to host") -> {
            if (isTailscaleAddress(host.sshHost)) {
                "Hint: ${host.sshHost} is a Tailscale address. This phone must be signed into the same tailnet, or the host must use the server's public IP."
            } else {
                "Hint: the network cannot reach ${host.sshHost}:${host.sshPort}. Check the IP/hostname, that sshd is listening, and that the cloud firewall allows this port."
            }
        }
        lower.contains("connection refused") ->
            "Hint: the host is reachable, but nothing is listening on ${host.sshHost}:${host.sshPort}."
        lower.contains("timed out") || lower.contains("timeout") ->
            "Hint: no response from ${host.sshHost}:${host.sshPort}. A firewall or NAT may be dropping the connection."
        lower.contains("auth fail") || lower.contains("auth cancel") ->
            "Hint: the server rejected the SSH key. Check that the phone's public key is in ~/.ssh/authorized_keys."
        else -> null
    }
    return if (hint == null) raw else "$raw\n\n$hint"
}
