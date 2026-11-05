package dev.pvps.ssh

import android.util.Base64
import com.jcraft.jsch.ChannelExec
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import dev.pvps.data.model.CommandResult
import dev.pvps.data.model.Host
import dev.pvps.data.model.SshKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
        } catch (e: Exception) {
            val message = e.message.orEmpty()
            when {
                message.contains("UnknownHostKey", ignoreCase = true) ->
                    ConnectOutcome.NeedsHostKey(seenHostKey.get().orEmpty())
                message.contains("HostKey has been changed", ignoreCase = true) ->
                    ConnectOutcome.HostKeyChanged(seenHostKey.get().orEmpty())
                else -> ConnectOutcome.Failed(message.ifBlank { e.javaClass.simpleName })
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
            try {
                channel.setCommand(command)
                channel.setErrStream(stderr)
                channel.setInputStream(null)
                channel.setPty(false)
                channel.connect(timeoutMs.toInt().coerceIn(1_000, 120_000))
                channel.inputStream.use { input -> input.copyTo(stdout) }
                while (!channel.isClosed) delay(15)
                CommandResult(
                    exitCode = channel.exitStatus,
                    stdout = stdout.toString(Charsets.UTF_8.name()),
                    stderr = stderr.toString(Charsets.UTF_8.name()),
                    durationMs = System.currentTimeMillis() - started
                )
            } finally {
                runCatching { channel.disconnect() }
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
