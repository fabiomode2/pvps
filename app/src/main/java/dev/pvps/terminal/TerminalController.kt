package dev.pvps.terminal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import dev.pvps.ssh.ShellChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class TerminalController(
    private val scope: CoroutineScope,
    private val shell: ShellChannel,
    palette: TerminalPalette,
    private val onCopy: (String) -> Unit = {},
    private val bellCallback: () -> Unit = {},
    private val onExit: () -> Unit = {}
) : TerminalOutput(), TerminalSessionClient {

    // These must be initialized before TerminalEmulator: its constructor
    // calls onColorsChanged() synchronously, and the output callback may be
    // invoked during construction.
    private val writeQueue = Channel<ByteArray>(Channel.UNLIMITED)
    private val closed = AtomicBoolean(false)

    var revision by mutableIntStateOf(0)
        private set
    var title by mutableStateOf("")
        private set
    var exited by mutableStateOf(false)
        private set

    val emulator: TerminalEmulator = TerminalEmulator(this, 80, 24, 2000, this)

    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private var blinkJob: Job? = null

    init {
        applyPalette(emulator, palette)
        emulator.setCursorBlinkingEnabled(true)
        startReader()
        startWriter()
        startBlink()
    }

    private fun startReader() {
        readerJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(16 * 1024)
            try {
                while (isActive && !closed.get()) {
                    val read = shell.input.read(buffer)
                    if (read < 0) break
                    val copy = buffer.copyOf(read)
                    withContext(Dispatchers.Main) {
                        emulator.append(copy, copy.size)
                        revision++
                    }
                }
            } catch (_: Exception) {
                // Shell closed or network dropped.
            } finally {
                withContext(Dispatchers.Main) {
                    exited = true
                    revision++
                    onExit()
                }
            }
        }
    }

    private fun startWriter() {
        writerJob = scope.launch(Dispatchers.IO) {
            for (bytes in writeQueue) {
                if (closed.get()) break
                runCatching { shell.write(bytes) }
            }
        }
    }

    private fun startBlink() {
        blinkJob = scope.launch {
            var on = true
            while (isActive && !closed.get()) {
                delay(520)
                on = !on
                withContext(Dispatchers.Main) {
                    emulator.setCursorBlinkState(on)
                    revision++
                }
            }
        }
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (count <= 0) return
        writeQueue.trySend(data.copyOfRange(offset, offset + count))
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        title = newTitle.orEmpty()
        revision++
    }

    override fun onCopyTextToClipboard(text: String?) {
        if (!text.isNullOrEmpty()) onCopy(text)
    }

    override fun onPasteTextFromClipboard() = Unit

    override fun onBell() = bellCallback()

    override fun onColorsChanged() {
        revision++
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        writeQueue.trySend(text.toByteArray(Charsets.UTF_8))
    }

    fun sendBytes(bytes: ByteArray) {
        if (bytes.isNotEmpty()) writeQueue.trySend(bytes)
    }

    fun paste(text: String) {
        if (text.isEmpty()) return
        emulator.paste(text)
        revision++
    }

    fun copyTranscript(): String = emulator.screen.getTranscriptText()

    fun resize(cols: Int, rows: Int, cellWidthPx: Float, cellHeightPx: Float) {
        if (cols < 2 || rows < 2) return
        if (cols == emulator.mColumns && rows == emulator.mRows) return
        emulator.resize(cols, rows)
        shell.resize(
            cols,
            rows,
            (cols * cellWidthPx).toInt().coerceAtLeast(1),
            (rows * cellHeightPx).toInt().coerceAtLeast(1)
        )
        revision++
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        readerJob?.cancel()
        writerJob?.cancel()
        blinkJob?.cancel()
        writeQueue.close()
        shell.close()
    }

    override fun onTextChanged(session: TerminalSession?) = Unit
    override fun onTitleChanged(session: TerminalSession?) = Unit
    override fun onSessionFinished(session: TerminalSession?) = Unit
    override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) = Unit
    override fun onPasteTextFromClipboard(session: TerminalSession?) = Unit
    override fun onBell(session: TerminalSession?) = Unit
    override fun onColorsChanged(session: TerminalSession?) = Unit
    override fun onTerminalCursorStateChange(state: Boolean) {
        revision++
    }

    override fun getTerminalCursorStyle(): Int? = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    override fun logError(tag: String?, message: String?) = Unit
    override fun logWarn(tag: String?, message: String?) = Unit
    override fun logInfo(tag: String?, message: String?) = Unit
    override fun logDebug(tag: String?, message: String?) = Unit
    override fun logVerbose(tag: String?, message: String?) = Unit
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) = Unit
    override fun logStackTrace(tag: String?, e: Exception?) = Unit
}
