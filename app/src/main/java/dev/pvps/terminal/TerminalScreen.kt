package dev.pvps.terminal

import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.components.StatusDot
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.termux.terminal.TextStyle
import com.termux.terminal.WcWidth
import dev.pvps.data.model.AppSettings
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.ssh.SshManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun TerminalScreen(
    host: Host,
    ssh: SshManager,
    settings: AppSettings,
    onBack: () -> Unit,
    onFontSizeChange: (Float) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val states by ssh.states.collectAsState()
    val connection = states[host.id] ?: ConnectionState(host.id, ConnectionState.Status.DISCONNECTED)
    val palette = remember(settings.terminalTheme) { TerminalPalettes.byId(settings.terminalTheme) }
    var controller by remember(host.id) { mutableStateOf<TerminalController?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var fontSize by remember { mutableFloatStateOf(settings.terminalFontSize) }
    var topRow by remember { mutableIntStateOf(0) }
    var cols by remember { mutableIntStateOf(80) }
    var rows by remember { mutableIntStateOf(24) }
    var input by remember { mutableStateOf("") }
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    val fontSizePx = with(density) { fontSize.sp.toPx() }
    val paint = remember(fontSizePx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            textSize = fontSizePx
        }
    }
    val cellWidth = remember(paint) { paint.measureText("M").coerceAtLeast(1f) }
    val cellHeight = remember(paint) { paint.fontSpacing.coerceAtLeast(1f) }

    LaunchedEffect(host.id, connection.status, retry) {
        if (connection.status == ConnectionState.Status.CONNECTED && controller == null) {
            error = null
            runCatching {
                val shell = withContext(Dispatchers.IO) { ssh.openShell(host.id, 80, 24) }
                controller = TerminalController(
                    scope = scope,
                    shell = shell,
                    palette = palette,
                    onCopy = { text -> clipboard.setText(AnnotatedString(text)) },
                    bellCallback = { haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                    onExit = {
                        error = "Shell exited"
                        controller = null
                    }
                )
            }.onFailure { error = it.message ?: "Could not open shell" }
        }
    }

    LaunchedEffect(connection.status) {
        if (connection.status != ConnectionState.Status.CONNECTED) {
            controller?.close()
            controller = null
        }
    }

    DisposableEffect(host.id) {
        onDispose {
            controller?.close()
            controller = null
        }
    }

    DisposableEffect(settings.keepScreenOnTerminal) {
        if (settings.keepScreenOnTerminal) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    fun sendSequence(text: String) {
        controller?.sendText(text)
    }

    fun sendWithModifiers(text: String) {
        if (text.isEmpty()) return
        if (ctrlActive) {
            val first = text.first().lowercaseChar()
            val code = if (first in 'a'..'z') first.code - 'a'.code + 1 else first.code
            controller?.sendBytes(byteArrayOf(code.toByte()))
            ctrlActive = false
            return
        }
        if (altActive) {
            controller?.sendBytes(byteArrayOf(0x1B) + text.toByteArray(Charsets.UTF_8))
            altActive = false
            return
        }
        controller?.sendText(text)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(palette.background))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = host.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = controller?.title?.takeIf { it.isNotBlank() } ?: connection.status.name.lowercase(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1
                )
            }
            StatusDot(
                when (connection.status) {
                    ConnectionState.Status.CONNECTED -> Success
                    ConnectionState.Status.ERROR -> Danger
                    else -> TextSecondary
                }
            )
            Spacer(Modifier.width(6.dp))
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Terminal menu", tint = TextSecondary)
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("A-  Smaller font") },
                        onClick = {
                            menuExpanded = false
                            fontSize = (fontSize - 1f).coerceIn(8f, 24f)
                            onFontSizeChange(fontSize)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("A+  Larger font") },
                        onClick = {
                            menuExpanded = false
                            fontSize = (fontSize + 1f).coerceIn(8f, 24f)
                            onFontSizeChange(fontSize)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy transcript") },
                        onClick = {
                            menuExpanded = false
                            controller?.copyTranscript()?.let { clipboard.setText(AnnotatedString(it)) }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Paste") },
                        onClick = {
                            menuExpanded = false
                            val text = clipboard.getText()?.text
                            if (!text.isNullOrEmpty()) controller?.paste(text)
                        }
                    )
                    if (topRow < 0) {
                        DropdownMenuItem(
                            text = { Text("Scroll to bottom") },
                            onClick = {
                                menuExpanded = false
                                topRow = 0
                            }
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (controller == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (connection.status == ConnectionState.Status.CONNECTING) {
                        CircularProgressIndicator(color = Accent)
                        Spacer(Modifier.height(12.dp))
                        Text("Connecting…", color = TextSecondary)
                    } else if (error != null) {
                        Text("Terminal error", color = Color(0xFFFF6B6B), style = MaterialTheme.typography.titleMedium)
                        Text(error.orEmpty(), color = TextSecondary, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = {
                            retry++
                            scope.launch { ssh.connect(host.id) }
                        }) { Text("Reconnect") }
                    } else {
                        Text("Terminal is not connected", color = TextSecondary)
                        TextButton(onClick = { scope.launch { ssh.connect(host.id) } }) {
                            Text("Connect")
                        }
                    }
                }
            } else {
                val terminal = controller!!
                androidx.compose.foundation.Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { size ->
                            val newCols = max(2, (size.width / cellWidth).roundToInt())
                            val newRows = max(2, (size.height / cellHeight).roundToInt())
                            if (newCols != cols || newRows != rows) {
                                cols = newCols
                                rows = newRows
                                terminal.resize(newCols, newRows, cellWidth, cellHeight)
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures {
                                focusRequester.requestFocus()
                                keyboard?.show()
                            }
                        }
                        .pointerInput(cellHeight) {
                            detectVerticalDragGestures { _, dragAmount ->
                                val delta = (dragAmount / cellHeight).roundToInt()
                                if (delta != 0) {
                                    val maxBack = terminal.emulator.screen.activeTranscriptRows
                                    topRow = (topRow + delta).coerceIn(-maxBack, 0)
                                }
                            }
                        }
                ) {
                    drawTerminal(
                        controller = terminal,
                        topRow = topRow,
                        cellWidth = cellWidth,
                        cellHeight = cellHeight,
                        paint = paint,
                        defaultBackground = palette.background,
                        revision = terminal.revision
                    )
                }

                BasicTextField(
                    value = input,
                    onValueChange = { value ->
                        if (value.isNotEmpty()) {
                            sendWithModifiers(value)
                            input = ""
                        }
                    },
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0.01f)
                        .focusRequester(focusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            val ctrl = event.isCtrlPressed || ctrlActive
                            val alt = event.isAltPressed || altActive
                            fun emit(value: String): Boolean {
                                sendWithModifiers(value)
                                return true
                            }
                            when (event.key) {
                                Key.Enter -> emit("\r")
                                Key.Backspace -> emit("\u007F")
                                Key.Tab -> emit("\t")
                                Key.Escape -> emit("\u001B")
                                Key.DirectionUp -> emit(if (ctrl) "\u001B[1;5A" else if (alt) "\u001B[1;3A" else "\u001B[A")
                                Key.DirectionDown -> emit(if (ctrl) "\u001B[1;5B" else if (alt) "\u001B[1;3B" else "\u001B[B")
                                Key.DirectionRight -> emit(if (ctrl) "\u001B[1;5C" else if (alt) "\u001B[1;3C" else "\u001B[C")
                                Key.DirectionLeft -> emit(if (ctrl) "\u001B[1;5D" else if (alt) "\u001B[1;3D" else "\u001B[D")
                                Key.MoveHome -> emit(if (ctrl) "\u001B[1;5H" else "\u001B[H")
                                Key.MoveEnd -> emit(if (ctrl) "\u001B[1;5F" else "\u001B[F")
                                Key.PageUp -> emit("\u001B[5~")
                                Key.PageDown -> emit("\u001B[6~")
                                Key.Delete -> emit("\u001B[3~")
                                else -> {
                                    if (ctrl) {
                                        val code = event.utf16CodePoint
                                        if (code in 'a'.code..'z'.code) {
                                            emit((code - 'a'.code + 1).toChar().toString())
                                        } else false
                                    } else if (alt) {
                                        val code = event.utf16CodePoint
                                        if (code > 0) {
                                            emit("\u001B" + code.toChar())
                                        } else false
                                    } else false
                                }
                            }
                        },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.None
                    ),
                    keyboardActions = KeyboardActions()
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceDark)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TerminalKey("CTRL", active = ctrlActive) { ctrlActive = !ctrlActive }
            TerminalKey("ALT", active = altActive) { altActive = !altActive }
            TerminalKey("ESC") { sendSequence("\u001B") }
            TerminalKey("TAB") { sendSequence("\t") }
            TerminalKey("↑") { sendSequence("\u001B[A") }
            TerminalKey("↓") { sendSequence("\u001B[B") }
            TerminalKey("←") { sendSequence("\u001B[D") }
            TerminalKey("→") { sendSequence("\u001B[C") }
            TerminalKey("C-c") { sendSequence("\u0003") }
            TerminalKey("C-d") { sendSequence("\u0004") }
            TerminalKey("C-z") { sendSequence("\u001A") }
            TerminalKey("C-l") { sendSequence("\u000C") }
            TerminalKey("/") { sendWithModifiers("/") }
            TerminalKey("-") { sendWithModifiers("-") }
            TerminalKey("|") { sendWithModifiers("|") }
            TerminalKey("~") { sendWithModifiers("~") }
            TerminalKey("`") { sendWithModifiers("`") }
            TerminalKey(":") { sendWithModifiers(":") }
            TerminalKey(";") { sendWithModifiers(";") }
            TerminalKey("=") { sendWithModifiers("=") }
            TerminalKey("+") { sendWithModifiers("+") }
            TerminalKey("_") { sendWithModifiers("_") }
            TerminalKey("*") { sendWithModifiers("*") }
            TerminalKey("\"") { sendWithModifiers("\"") }
            TerminalKey("'") { sendWithModifiers("'") }
            TerminalKey("(") { sendWithModifiers("(") }
            TerminalKey(")") { sendWithModifiers(")") }
            TerminalKey("[") { sendWithModifiers("[") }
            TerminalKey("]") { sendWithModifiers("]") }
            TerminalKey("{") { sendWithModifiers("{") }
            TerminalKey("}") { sendWithModifiers("}") }
            TerminalKey("<") { sendWithModifiers("<") }
            TerminalKey(">") { sendWithModifiers(">") }
            TerminalKey("?") { sendWithModifiers("?") }
            TerminalKey("&") { sendWithModifiers("&") }
            TerminalKey("%") { sendWithModifiers("%") }
            TerminalKey("$") { sendWithModifiers("$") }
            TerminalKey("#") { sendWithModifiers("#") }
            TerminalKey("@") { sendWithModifiers("@") }
            TerminalKey("!") { sendWithModifiers("!") }
            TerminalKey("^") { sendWithModifiers("^") }
            TerminalKey("\\") { sendWithModifiers("\\") }
        }
    }
}

@Composable
private fun TerminalKey(label: String, active: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .height(34.dp)
            .background(
                color = if (active) Accent.copy(alpha = 0.25f) else SurfaceRaised,
                shape = MaterialTheme.shapes.small
            )
            .padding(horizontal = 2.dp)
    ) {
        Text(
            text = label,
            color = if (active) Accent else TextPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTerminal(
    controller: TerminalController,
    topRow: Int,
    cellWidth: Float,
    cellHeight: Float,
    paint: Paint,
    defaultBackground: Int,
    revision: Int
) {
    @Suppress("UNUSED_EXPRESSION")
    revision
    val emulator = controller.emulator
    val screen = emulator.screen
    val colors = emulator.mColors.mCurrentColors
    val defaultFg = colors[TextStyle.COLOR_INDEX_FOREGROUND]
    val defaultBg = colors[TextStyle.COLOR_INDEX_BACKGROUND]
    val cursorColor = colors[TextStyle.COLOR_INDEX_CURSOR]
    val top = topRow.coerceIn(-screen.activeTranscriptRows, 0)
    val rows = emulator.mRows
    val cols = emulator.mColumns

    for (rowIndex in 0 until rows) {
        val externalRow = top + rowIndex
        val internalRow = screen.externalToInternalRow(externalRow)
        val line = screen.allocateFullLineIfNecessary(internalRow) ?: continue
        val yTop = rowIndex * cellHeight
        val baseline = yTop - paint.fontMetrics.ascent
        var col = 0
        while (col < cols) {
            val start = line.findStartOfColumn(col)
            if (start >= line.spaceUsed) break
            val first = line.mText[start]
            val codePoint = if (Character.isHighSurrogate(first) && start + 1 < line.spaceUsed) {
                Character.toCodePoint(first, line.mText[start + 1])
            } else {
                first.code
            }
            val width = WcWidth.width(codePoint).coerceAtLeast(1)
            val style = line.getStyle(col)
            val fgRaw = TextStyle.decodeForeColor(style)
            val bgRaw = TextStyle.decodeBackColor(style)
            val effects = TextStyle.decodeEffect(style)
            val bold = effects and TextStyle.CHARACTER_ATTRIBUTE_BOLD != 0
            val italic = effects and TextStyle.CHARACTER_ATTRIBUTE_ITALIC != 0
            val underline = effects and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE != 0
            val strike = effects and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH != 0
            val inverse = effects and TextStyle.CHARACTER_ATTRIBUTE_INVERSE != 0
            val invisible = effects and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE != 0
            val dim = effects and TextStyle.CHARACTER_ATTRIBUTE_DIM != 0

            fun resolve(raw: Int): Int =
                if (raw and 0xFF000000.toInt() == 0xFF000000.toInt()) raw else colors.getOrElse(raw) { defaultFg }

            var fg = resolve(fgRaw)
            var bg = resolve(bgRaw)
            if (bold && fgRaw in 0..7) fg = colors.getOrElse(fgRaw + 8) { fg }
            if (inverse) {
                val tmp = fg
                fg = bg
                bg = tmp
            }
            if (dim) {
                fg = Color(fg).copy(alpha = 0.65f).toArgb()
            }

            val x = col * cellWidth
            val cellW = width * cellWidth
            if (bg != defaultBg) {
                drawRect(
                    color = Color(bg),
                    topLeft = Offset(x, yTop),
                    size = Size(cellW, cellHeight)
                )
            }
            if (!invisible && codePoint != 0 && !Character.isWhitespace(first)) {
                paint.color = fg
                paint.isFakeBoldText = bold
                paint.isUnderlineText = underline
                paint.isStrikeThruText = strike
                paint.textSkewX = if (italic) -0.35f else 0f
                val charCount = Character.charCount(codePoint)
                drawIntoCanvas { canvas ->
                    canvas.nativeCanvas.drawText(
                        line.mText,
                        start,
                        charCount,
                        x,
                        baseline,
                        paint
                    )
                }
            }
            col += width
        }
    }

    if (top == 0 && emulator.shouldCursorBeVisible()) {
        val cursorRow = emulator.cursorRow
        val cursorCol = emulator.cursorCol
        if (cursorRow in 0 until rows && cursorCol in 0 until cols) {
            val x = cursorCol * cellWidth
            val y = cursorRow * cellHeight
            when (emulator.cursorStyle) {
                com.termux.terminal.TerminalEmulator.TERMINAL_CURSOR_STYLE_UNDERLINE ->
                    drawRect(Color(cursorColor), Offset(x, y + cellHeight - 3f), Size(cellWidth, 3f))
                com.termux.terminal.TerminalEmulator.TERMINAL_CURSOR_STYLE_BAR ->
                    drawRect(Color(cursorColor), Offset(x, y), Size(3f, cellHeight))
                else ->
                    drawRect(Color(cursorColor).copy(alpha = 0.55f), Offset(x, y), Size(cellWidth, cellHeight))
            }
        }
    }
}
