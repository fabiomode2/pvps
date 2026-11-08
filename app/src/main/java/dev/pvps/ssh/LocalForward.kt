package dev.pvps.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import dev.pvps.diagnostics.Diagnostics
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

// Trust boundary: any co-resident app can reach a 127.0.0.1 listener. Admission
// is capped and each bridge has an idle timeout so a local app or stalled
// remote endpoint cannot create unbounded threads/channels.
private const val MAX_CONCURRENT_CLIENTS = 32

// An SSE stream can be quiet for minutes while the server has nothing to say.
// The old 120s cutoff killed idle tunnels even though the SSH transport was
// healthy; 30 minutes still bounds leaked clients without breaking a chat the
// user left open. Data in either direction refreshes the deadline.
private const val CLIENT_IDLE_TIMEOUT_MS = 30L * 60_000L

// A channel-open failure can be a one-off (sshd's MaxSessions, a remote server
// restarting). Only a sustained burst marks the whole forward as broken.
private const val CHANNEL_FAILURE_WINDOW_MS = 15_000L
private const val CHANNEL_FAILURE_THRESHOLD = 3
private const val SESSION_MONITOR_INTERVAL_MS = 2_000L

/**
 * Local TCP listener forwarded through SSH direct-tcpip. The OpenCode API is
 * reached through this listener so it can stay bound to 127.0.0.1 on the host.
 *
 * The forward is tied to the lifetime of its SSH session: when the transport
 * dies (or its channels keep failing) the listener is closed and [onBroken] is
 * invoked once, so SshManager can redial immediately instead of waiting for the
 * next watchdog tick. Local clients then fail fast instead of being accepted
 * and reset for a minute, which is what made a dead tunnel look like a server
 * outage.
 */
class LocalForward(
    private val session: Session,
    private val remoteHost: String,
    private val remotePort: Int,
    private val onBroken: (reason: String) -> Unit = {}
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val clientSlots = Semaphore(MAX_CONCURRENT_CLIENTS)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null
    private var monitorThread: Thread? = null

    private val channelFailures = AtomicInteger(0)
    private val lastChannelFailureAt = AtomicLong(0L)
    private val lastFailureLogAt = AtomicLong(0L)
    private val lastBrokenReportAt = AtomicLong(0L)

    @Volatile
    var port: Int = 0
        private set

    /** True only while the listener and the underlying SSH transport are up. */
    val isOpen: Boolean
        get() = !closed.get() && session.isConnected && serverSocket?.isClosed == false

    fun start(preferredPort: Int = 0) {
        val address = InetAddress.getByName("127.0.0.1")
        val bindPort = if (preferredPort > 0) preferredPort else 0
        val server = ServerSocket()
        try {
            server.reuseAddress = true
            server.bind(InetSocketAddress(address, bindPort), 50)
        } catch (e: Exception) {
            runCatching { server.close() }
            if (preferredPort > 0) {
                // Trust boundary: never silently move a reused forward to a new
                // port. The existing OpenCode client still names the old port,
                // so a silent move could send its Basic credential to whatever
                // process wins that released loopback port.
                throw IOException(
                    "Preferred local forward port $preferredPort is unavailable",
                    e
                )
            }
            throw e
        }
        serverSocket = server
        port = server.localPort
        acceptThread = thread(name = "ssh-forward-$port", isDaemon = true) {
            while (!closed.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                if (!clientSlots.tryAcquire()) {
                    // Excess local clients are rejected immediately; the
                    // listener never allocates a bridge thread or SSH channel
                    // beyond the cap.
                    runCatching { client.close() }
                    continue
                }
                if (closed.get()) {
                    runCatching { client.close() }
                    clientSlots.release()
                    break
                }
                clients.add(client)
                thread(name = "ssh-forward-client-$port", isDaemon = true) {
                    try {
                        client.soTimeout = CLIENT_IDLE_TIMEOUT_MS.toInt()
                        bridge(client)
                    } catch (_: Exception) {
                        // Client/channel closed or idle timeout; both sides are
                        // closed below.
                    } finally {
                        runCatching { client.close() }
                        clients.remove(client)
                        clientSlots.release()
                    }
                }
            }
        }
        monitorThread = thread(name = "ssh-forward-watch-$port", isDaemon = true) {
            while (!closed.get()) {
                try {
                    Thread.sleep(SESSION_MONITOR_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    break
                }
                // JSch normally flips this after its keepalive count is
                // exhausted. Checking it here also covers a listener that was
                // left behind by a transport that died between probes.
                if (!session.isConnected) {
                    reportBroken("SSH transport closed")
                    break
                }
            }
        }
    }

    private fun bridge(client: Socket) {
        var channel: ChannelDirectTCPIP? = null
        val lastActivity = AtomicLong(System.currentTimeMillis())
        try {
            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            // The remote destination is fixed by construction; local clients
            // cannot redirect the tunnel to another host or port.
            channel.setHost(remoteHost)
            channel.setPort(remotePort)
            channel.connect(15_000)
            channelFailures.set(0)
            val remoteIn = channel.inputStream
            val remoteOut = channel.outputStream
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            val t1 = thread(name = "ssh-forward-up-$port", isDaemon = true) {
                pump(clientIn, remoteOut, lastActivity)
            }
            val t2 = thread(name = "ssh-forward-down-$port", isDaemon = true) {
                pump(remoteIn, clientOut, lastActivity)
                // The remote side ended; close the local socket so the upstream
                // pump does not wait for its idle timeout.
                runCatching { client.close() }
            }
            t1.join()
            // Give the opposite direction a bounded drain window, then tear
            // down both sides so a stalled peer cannot pin this client slot
            // indefinitely.
            t2.join(CLIENT_IDLE_TIMEOUT_MS)
        } catch (e: Exception) {
            noteChannelFailure(e)
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { client.close() }
        }
    }

    /**
     * A channel open failure means the SSH session is alive but cannot carry
     * this connection (sshd MaxSessions, the forwarded server refusing the
     * port, or a wedged transport). "session is down" is definitive; anything
     * else needs a short burst before we ask SshManager to verify/rebuild.
     */
    private fun noteChannelFailure(error: Throwable) {
        val message = error.message.orEmpty().ifBlank { error.javaClass.simpleName }
        if (!session.isConnected || message.contains("session is down", ignoreCase = true)) {
            reportBroken("SSH session is down")
            return
        }
        val now = System.currentTimeMillis()
        val count = channelFailures.incrementAndGet()
        val lastFailure = lastChannelFailureAt.getAndSet(now)
        if (now - lastFailure > CHANNEL_FAILURE_WINDOW_MS) channelFailures.set(1)
        if (now - lastFailureLogAt.get() > 5_000) {
            lastFailureLogAt.set(now)
            Diagnostics.event("ssh", "forward :$port channel failed: $message")
        }
        if (count >= CHANNEL_FAILURE_THRESHOLD && now - lastFailure <= CHANNEL_FAILURE_WINDOW_MS) {
            channelFailures.set(0)
            // Do NOT close the listener here. If the SSH transport is healthy,
            // the failed channel means the remote endpoint is unavailable (for
            // example OpenCode restarting); keeping the listener lets the
            // client reconnect as soon as it is back. Only a dead transport
            // closes the forward.
            if (now - lastBrokenReportAt.get() >= 10_000L) {
                lastBrokenReportAt.set(now)
                runCatching { onBroken("tunnel channel open failed: $message") }
            }
        }
    }

    private fun pump(input: InputStream, output: OutputStream, lastActivity: AtomicLong) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (!closed.get()) {
                val read = try {
                    input.read(buffer)
                } catch (e: SocketTimeoutException) {
                    // A read timeout is only an idle timeout when neither
                    // direction has moved data. A busy downstream stream keeps
                    // a client with no upstream traffic (for example SSE) alive.
                    if (System.currentTimeMillis() - lastActivity.get() <
                        CLIENT_IDLE_TIMEOUT_MS
                    ) {
                        continue
                    }
                    throw e
                }
                if (read < 0) break
                if (read > 0) {
                    lastActivity.set(System.currentTimeMillis())
                    output.write(buffer, 0, read)
                    output.flush()
                }
            }
        } catch (_: Exception) {
            // Normal when either side closes or the idle timeout fires.
        } finally {
            runCatching { output.close() }
            runCatching { input.close() }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        teardown()
    }

    /**
     * Unexpected failure path: close the listener first (so new local clients
     * fail fast), then tell SshManager once. A concurrent intentional close
     * wins the CAS and suppresses the callback.
     */
    private fun reportBroken(reason: String) {
        if (!closed.compareAndSet(false, true)) return
        teardown()
        Diagnostics.event("ssh", "forward :$port lost: $reason")
        runCatching { onBroken(reason) }
    }

    private fun teardown() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        acceptThread?.interrupt()
        monitorThread?.interrupt()
    }
}
