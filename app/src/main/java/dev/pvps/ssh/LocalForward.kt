package dev.pvps.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

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
    private var serverSocket: ServerSocket? = null
    private var acceptThread: Thread? = null

    @Volatile
    var port: Int = 0
        private set

    val isOpen: Boolean get() = !closed.get() && serverSocket?.isClosed == false

    fun start(preferredPort: Int = 0) {
        val address = InetAddress.getByName("127.0.0.1")
        val server = run {
            if (preferredPort > 0) {
                runCatching {
                    ServerSocket().apply {
                        reuseAddress = true
                        bind(java.net.InetSocketAddress(address, preferredPort), 50)
                    }
                }.getOrNull()
            } else {
                null
            }
        } ?: ServerSocket().apply {
            reuseAddress = true
            bind(java.net.InetSocketAddress(address, 0), 50)
        }
        serverSocket = server
        port = server.localPort
        acceptThread = thread(name = "ssh-forward-$port", isDaemon = true) {
            while (!closed.get()) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                clients.add(client)
                thread(name = "ssh-forward-client-$port", isDaemon = true) {
                    bridge(client)
                }
            }
        }
    }

    private fun bridge(client: Socket) {
        var channel: ChannelDirectTCPIP? = null
        try {
            channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(remoteHost)
            channel.setPort(remotePort)
            channel.connect(15_000)
            val remoteIn = channel.inputStream
            val remoteOut = channel.outputStream
            val clientIn = client.getInputStream()
            val clientOut = client.getOutputStream()

            val t1 = thread(name = "ssh-forward-up-$port", isDaemon = true) {
                pump(clientIn, remoteOut)
            }
            val t2 = thread(name = "ssh-forward-down-$port", isDaemon = true) {
                pump(remoteIn, clientOut)
            }
            t1.join()
            t2.join()
        } catch (_: Exception) {
            // Client/channel closed or host unreachable; both sides are closed below.
        } finally {
            runCatching { channel?.disconnect() }
            runCatching { client.close() }
            clients.remove(client)
        }
    }

    private fun pump(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (!closed.get()) {
                val read = input.read(buffer)
                if (read < 0) break
                output.write(buffer, 0, read)
                output.flush()
            }
        } catch (_: Exception) {
            // Normal when either side closes.
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
