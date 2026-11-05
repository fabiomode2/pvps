package dev.pvps.ssh

import com.jcraft.jsch.ChannelShell
import java.io.InputStream
import java.io.OutputStream

class ShellChannel(
    private val channel: ChannelShell,
    val input: InputStream,
    val output: OutputStream
) {

    val isConnected: Boolean get() = channel.isConnected

    @Synchronized
    fun resize(cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        runCatching { channel.setPtySize(cols, rows, cellWidthPx, cellHeightPx) }
    }

    @Synchronized
    fun write(bytes: ByteArray) {
        runCatching {
            // The streams captured by the constructor must be the only ones used.
            // JSch returns a new buffered OutputStream from every
            // channel.outputStream call, so flushing a fresh wrapper sends nothing.
            output.write(bytes)
            output.flush()
        }
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun close() {
        runCatching { output.flush() }
        runCatching { channel.disconnect() }
    }
}
