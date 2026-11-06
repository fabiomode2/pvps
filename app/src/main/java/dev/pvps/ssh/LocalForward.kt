package dev.pvps.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
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
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

// Trust boundary: any co-resident app can reach a 127.0.0.1 listener. Admission
// is capped and each bridge has an idle timeout so a local app or stalled
// remote endpoint cannot create unbounded threads/channels.
private const val MAX_CONCURRENT_CLIENTS = 32
private const val CLIENT_IDLE_TIMEOUT_MS = 120_000

/**
 * Local TCP listener forwarded through SSH direct-tcpip. The OpenCode API is
 * reached through this listener so it can stay bound to 127.0.0.1 on the host.
 */
class LocalForward(
    private val session: Session,
    private val remoteHost: String,
    private val remotePort: Int
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val clientSlots = Semaphore(MAX_CONCURRENT_CLIENTS)
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    var port: Int = 0
        private set

    val isOpen: Boolean get() = !closed.get() && serverSocket?.isClosed == false

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
                        client.soTimeout = CLIENT_IDLE_TIMEOUT_MS
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
            t2.join(CLIENT_IDLE_TIMEOUT_MS.toLong())
        } catch (_: Exception) {
            // Client/channel closed or host unreachable; both sides are closed below.
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { client.close() }
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
                        CLIENT_IDLE_TIMEOUT_MS.toLong()
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
        runCatching { serverSocket?.close() }
        clients.forEach { runCatching { it.close() } }
        clients.clear()
        acceptThread?.interrupt()
    }
}
