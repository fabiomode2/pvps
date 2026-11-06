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
import java.util.concurrent.atomic.AtomicLong

// Trust boundary: terminal responses and user input are queued for the remote
// PTY. Bound both item count and bytes so a host that withholds the SSH channel
// window cannot grow app memory without limit.
private const val MAX_QUEUED_WRITES = 64
private const val MAX_QUEUED_BYTES = 256L * 1024

class TerminalController(
    private val scope: CoroutineScope,
    private val shell: ShellChannel,
    palette: TerminalPalette,
    private val bellCallback: () -> Unit = {},
    private val onExit: () -> Unit = {}
) : TerminalOutput(), TerminalSessionClient {

    // These must be initialized before TerminalEmulator: its constructor
    // calls onColorsChanged() synchronously, and the output callback may be
    // invoked during construction.
    private val writeQueue = Channel<ByteArray>(capacity = MAX_QUEUED_WRITES)
    private val queuedBytes = AtomicLong(0)
    private val closed = AtomicBoolean(false)

    var revision by mutableIntStateOf(0)
        private set
    var title by mutableStateOf("")
        private set
    var exited by mutableStateOf(false)
        private set

    val emulator: TerminalEmulator = TerminalEmulator(this, 80, 24, 2000, this)

    // JSch's getInputStream() creates and installs a new pipe on every call.
    // Capture the stream once; re-reading shell.input would drop buffered bytes.
    private val shellInput = shell.input

    private var readerJob: Job? = null
    private var writerJob: Job? = null
    private var blinkJob: Job? = null
    private var resizeJob: Job? = null

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
                    val read = shellInput.read(buffer)
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
                try {
                    if (closed.get()) break
                    runCatching { shell.write(bytes) }
                } finally {
                    queuedBytes.addAndGet(-bytes.size.toLong())
                }
            }
        }
    }

    private fun tryEnqueue(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val total = queuedBytes.addAndGet(bytes.size.toLong())
        if (total > MAX_QUEUED_BYTES) {
            queuedBytes.addAndGet(-bytes.size.toLong())
            return false
        }
        val result = writeQueue.trySend(bytes)
        if (result.isFailure) {
            queuedBytes.addAndGet(-bytes.size.toLong())
            return false
        }
        return true
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
        if (count.toLong() > MAX_QUEUED_BYTES) return
        // Remote-controlled emulator replies are best-effort. Dropping on
        // overflow is safer than retaining an unbounded query-response backlog.
        tryEnqueue(data.copyOfRange(offset, offset + count))
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        title = newTitle.orEmpty()
        revision++
    }

    @Suppress("UNUSED_PARAMETER")
    override fun onCopyTextToClipboard(text: String?) {
        // Trust boundary: OSC 52 is remote SSH output, not a local user action.
        // Ignore it so a compromised host cannot replace the device clipboard.
        // Explicit local copy (for example Copy transcript) still works.
    }

    override fun onPasteTextFromClipboard() = Unit

    override fun onBell() = bellCallback()

    override fun onColorsChanged() {
        revision++
    }

    fun sendText(text: String) {
        if (text.isEmpty()) return
        tryEnqueue(text.toByteArray(Charsets.UTF_8))
    }

    fun sendBytes(bytes: ByteArray) {
        if (bytes.isNotEmpty()) tryEnqueue(bytes)
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

        // Emulator dimensions must change on the UI thread; the PTY packet can
        // wait. Debounce so IME animations and drag-resizes do not spam the wire.
        emulator.resize(cols, rows)
        revision++
        val widthPx = (cols * cellWidthPx).toInt().coerceAtLeast(1)
        val heightPx = (rows * cellHeightPx).toInt().coerceAtLeast(1)
        resizeJob?.cancel()
        resizeJob = scope.launch(Dispatchers.IO) {
            delay(80)
            if (!closed.get()) shell.resize(cols, rows, widthPx, heightPx)
        }
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        readerJob?.cancel()
        writerJob?.cancel()
        blinkJob?.cancel()
        resizeJob?.cancel()
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
