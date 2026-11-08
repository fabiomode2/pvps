package dev.pvps.ssh

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.JSchChangedHostKeyException
import com.jcraft.jsch.JSchUnknownHostKeyException
import com.jcraft.jsch.Session
import com.jcraft.jsch.SocketFactory
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
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
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

    companion object {
        const val DEFAULT_MAX_OUTPUT_BYTES: Long = 8L * 1024 * 1024
        const val DEFAULT_MAX_ERROR_BYTES: Long = 1L * 1024 * 1024

        // Mobile NATs forget idle flows after ~30-60s. Send our own keepalive
        // every 15s and give up after two missed replies (30s) instead of the
        // old 30s x 3 = 90s, which is long enough for the UI to look dead.
        const val KEEPALIVE_INTERVAL_MS = 15_000
        const val KEEPALIVE_COUNT_MAX = 2
        private const val CONNECT_TIMEOUT_MS = 15_000
    }

    /**
     * JSch reports a dead transport through this flag. It can lag behind
     * reality on a half-open mobile socket, which is why SshManager also runs a
     * cheap exec probe; it is still the fastest signal for a clean RST/EOF.
     */
    val isAlive: Boolean
        get() = session?.isConnected == true

    suspend fun connect(): ConnectOutcome = withContext(Dispatchers.IO) {
        try {
            val s = jsch.getSession(host.sshUser, host.sshHost, host.sshPort)
            // SO_KEEPALIVE is not enough on its own (the kernel default is two
            // hours) but it is a cheap extra safety net under the SSH-level
            // keepalive below. TCP_NODELAY keeps interactive keystrokes snappy.
            s.setSocketFactory(KeepAliveSocketFactory(CONNECT_TIMEOUT_MS))
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
            s.serverAliveInterval = KEEPALIVE_INTERVAL_MS
            s.serverAliveCountMax = KEEPALIVE_COUNT_MAX
            s.connect(CONNECT_TIMEOUT_MS)
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

    // Trust boundary: the authenticated SSH host controls command output and a
    // compromised host can ignore the command and stream arbitrary bytes. Cap
    // both streams by byte count, not only by the wall-clock watchdog.
    suspend fun exec(
        command: String,
        timeoutMs: Long = 30_000,
        maxOutputBytes: Long = DEFAULT_MAX_OUTPUT_BYTES,
        maxErrorBytes: Long = DEFAULT_MAX_ERROR_BYTES
    ): CommandResult =
        withContext(Dispatchers.IO) {
            val s = session ?: error("SSH session is not connected")
            val channel = s.openChannel("exec") as ChannelExec
            val stdout = ByteArrayOutputStream()
            val stderr = BoundedByteArrayOutputStream(maxErrorBytes, "stderr")
            val started = System.currentTimeMillis()
            coroutineScope {
                // The read loop below is blocking and ignores coroutine
                // cancellation. A watchdog closes the channel so a hung remote
                // command cannot hold the UI forever.
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
                    val buffer = ByteArray(16 * 1024)
                    var totalBytes = 0L
                    channel.inputStream.use { input ->
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            totalBytes += read
                            if (totalBytes > maxOutputBytes) {
                                throw IOException("SSH stdout exceeded ${maxOutputBytes} bytes")
                            }
                            stdout.write(buffer, 0, read)
                        }
                    }
                    if (stderr.overflowed) {
                        throw IOException("SSH stderr exceeded ${maxErrorBytes} bytes")
                    }
                    while (!channel.isClosed) {
                        if (stderr.overflowed) {
                            throw IOException("SSH stderr exceeded ${maxErrorBytes} bytes")
                        }
                        delay(15)
                    }
                    if (stderr.overflowed) {
                        throw IOException("SSH stderr exceeded ${maxErrorBytes} bytes")
                    }
                    if (!watchdog.isActive) {
                        throw SocketTimeoutException("Command timed out after ${timeoutMs}ms")
                    }
                    CommandResult(
                        exitCode = channel.exitStatus,
                        stdout = stdout.toString(Charsets.UTF_8.name()),
                        stderr = String(stderr.toByteArray(), Charsets.UTF_8),
                        durationMs = System.currentTimeMillis() - started
                    )
                } catch (e: Exception) {
                    // The blocking read usually fails with an IOException when
                    // the watchdog disconnects the channel; report the real cause.
                    if (stderr.overflowed) {
                        throw IOException("SSH stderr exceeded ${maxErrorBytes} bytes")
                    }
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
        // JSch creates a fresh pipe every time getInputStream() is called and
        // installs it as the channel's output sink. Calling it after connect (or
        // more than once) can silently discard buffered bytes, so acquire the
        // streams exactly once, before the channel starts receiving data.
        val input = channel.inputStream
        val output = channel.outputStream
        try {
            channel.connect(15_000)
        } catch (e: Exception) {
            runCatching { channel.disconnect() }
            throw e
        }
        return ShellChannel(channel, input, output)
    }

    fun openSftp(): ChannelSftp {
        val s = session ?: error("SSH session is not connected")
        return (s.openChannel("sftp") as ChannelSftp).apply { connect(15_000) }
    }

    fun openForward(
        remoteHost: String,
        remotePort: Int,
        localPort: Int = 0,
        onBroken: (reason: String) -> Unit = {}
    ): LocalForward {
        val s = session ?: error("SSH session is not connected")
        return LocalForward(s, remoteHost, remotePort, onBroken).also { it.start(localPort) }
    }

    fun disconnect() {
        runCatching { session?.disconnect() }
        session = null
    }
}

/**
 * Socket factory used by the SSH transport. JSch's default socket has no
 * SO_KEEPALIVE and relies on the JVM default connect timeout, both of which are
 * poor fits for a phone that moves between Wi-Fi and LTE.
 */
private class KeepAliveSocketFactory(
    private val connectTimeoutMs: Int
) : SocketFactory {

    override fun createSocket(host: String, port: Int): Socket {
        val socket = Socket()
        runCatching { socket.keepAlive = true }
        runCatching { socket.tcpNoDelay = true }
        socket.connect(InetSocketAddress(host, port), connectTimeoutMs)
        return socket
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()

    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()
}

/**
 * Bounded sink used for remote stderr. JSch writes that stream from its own
 * reader thread, so overflow is both recorded and raised; exec() checks the
 * flag after draining stdout to report a clear failure regardless of how JSch
 * handles the writer-thread exception.
 */
private class BoundedByteArrayOutputStream(
    private val maxBytes: Long,
    private val streamName: String
) : OutputStream() {

    private val buffer = ByteArrayOutputStream()

    @Volatile
    var overflowed = false
        private set

    @Synchronized
    override fun write(b: Int) {
        if (buffer.size().toLong() + 1 > maxBytes) {
            overflowed = true
            throw IOException("SSH $streamName exceeded $maxBytes bytes")
        }
        buffer.write(b)
    }

    @Synchronized
    override fun write(b: ByteArray, off: Int, len: Int) {
        if (len <= 0) return
        if (buffer.size().toLong() + len > maxBytes) {
            overflowed = true
            throw IOException("SSH $streamName exceeded $maxBytes bytes")
        }
        buffer.write(b, off, len)
    }

    @Synchronized
    fun toByteArray(): ByteArray = buffer.toByteArray()
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
