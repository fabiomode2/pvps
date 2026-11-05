package dev.pvps.ssh

import com.jcraft.jsch.ChannelShell
import java.io.InputStream
import java.io.OutputStream

class ShellChannel(private val channel: ChannelShell) {

    val input: InputStream get() = channel.inputStream
    val output: OutputStream get() = channel.outputStream
    val isConnected: Boolean get() = channel.isConnected

    fun resize(cols: Int, rows: Int, cellWidthPx: Int, cellHeightPx: Int) {
        runCatching { channel.setPtySize(cols, rows, cellWidthPx, cellHeightPx) }
    }

    fun write(bytes: ByteArray) {
        runCatching {
            channel.outputStream.write(bytes)
            channel.outputStream.flush()
        }
    }

    fun write(text: String) = write(text.toByteArray(Charsets.UTF_8))

    fun close() {
        runCatching { channel.disconnect() }
    }
}
