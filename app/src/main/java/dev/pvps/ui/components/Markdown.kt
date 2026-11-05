package dev.pvps.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.DisplayFont
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning

/**
 * Small, dependency-free markdown renderer for chat messages.
 *
 * It deliberately supports the subset that OpenCode models actually emit:
 * headings, paragraphs, lists, quotes, tables, fenced code and the usual
 * inline styles. It is not a full CommonMark implementation, but it keeps
 * code, commands and links readable without pulling a large renderer into the
 * APK.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    if (text.isBlank()) return
    val bodyStyle = if (compact) {
        MaterialTheme.typography.bodyMedium
    } else {
        MaterialTheme.typography.bodyLarge
    }
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
    ) {
        val lines = text.replace("\r\n", "\n").split('\n')
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> i++
                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    val fence = trimmed.take(3)
                    val language = trimmed.removePrefix(fence).trim()
                    val code = StringBuilder()
                    i++
                    while (i < lines.size && !lines[i].trim().startsWith(fence)) {
                        if (code.isNotEmpty()) code.append('\n')
                        code.append(lines[i])
                        i++
                    }
                    if (i < lines.size) i++
                    MarkdownCodeBlock(code.toString(), language)
                }
                i + 1 < lines.size && isTableSeparator(lines[i + 1]) && line.contains('|') -> {
                    val header = splitTableRow(line)
                    i += 2
                    val rows = mutableListOf<List<String>>()
                    while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                        rows += splitTableRow(lines[i])
                        i++
                    }
                    MarkdownTable(header, rows)
                }
                isHeading(trimmed) -> {
                    val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, 6)
                    val content = trimmed.drop(level).trim()
                    val size = when (level) {
                        1 -> 26.sp
                        2 -> 22.sp
                        3 -> 19.sp
                        else -> 16.sp
                    }
                    SelectionContainer {
                        Text(
                            inlineMarkdown(content),
                            style = bodyStyle.copy(
                                fontSize = size,
                                lineHeight = (size.value * 1.25f).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = DisplayFont
                            ),
                            color = if (level <= 2) TextPrimary else TextSecondary
                        )
                    }
                    i++
                }
                isHorizontalRule(trimmed) -> {
                    HorizontalDivider(
                        color = OutlineDark.copy(alpha = 0.75f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                    i++
                }
                trimmed.startsWith(">") -> {
                    val quote = mutableListOf<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote += lines[i].trimStart().removePrefix(">").trimStart()
                        i++
                    }
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Accent.copy(alpha = 0.55f))
                        )
                        Spacer(Modifier.width(10.dp))
                        SelectionContainer {
                            Text(
                                inlineMarkdown(quote.joinToString("\n")),
                                style = bodyStyle,
                                color = TextSecondary,
                                fontStyle = FontStyle.Italic
                            )
                        }
                    }
                }
                listMatch(trimmed) != null -> {
                    val items = mutableListOf<ListItem>()
                    while (i < lines.size) {
                        val match = listMatch(lines[i]) ?: break
                        items += match.second
                        i++
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = (item.indent * 12).dp)
                            ) {
                                Text(
                                    bulletFor(item),
                                    style = bodyStyle,
                                    color = Accent,
                                    modifier = Modifier.width(22.dp)
                                )
                                SelectionContainer {
                                    Text(
                                        inlineMarkdown(item.text),
                                        style = bodyStyle,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    val paragraph = mutableListOf<String>()
                    while (i < lines.size) {
                        val candidate = lines[i]
                        val candidateTrimmed = candidate.trim()
                        if (candidateTrimmed.isBlank() ||
                            candidateTrimmed.startsWith("```") ||
                            candidateTrimmed.startsWith("~~~") ||
                            isHeading(candidateTrimmed) ||
                            isHorizontalRule(candidateTrimmed) ||
                            candidateTrimmed.startsWith(">") ||
                            listMatch(candidateTrimmed) != null ||
                            (i + 1 < lines.size && isTableSeparator(lines[i + 1]) && candidate.contains('|'))
                        ) break
                        paragraph += candidate
                        i++
                    }
                    SelectionContainer {
                        Text(
                            inlineMarkdown(paragraph.joinToString("\n").trim()),
                            style = bodyStyle,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

private data class ListItem(val indent: Int, val marker: String, val text: String)

private fun listMatch(line: String): Pair<Int, ListItem>? {
    val trimmed = line.trimStart()
    val indent = (line.length - trimmed.length) / 2
    val unordered = Regex("^([-*+])\\s+(.*)$").find(trimmed)
    val ordered = Regex("^(\\d+)[.)]\\s+(.*)$").find(trimmed)
    val match = unordered ?: ordered ?: return null
    val marker = match.groupValues[1]
    return indent to ListItem(indent, marker, match.groupValues[2])
}

private fun bulletFor(item: ListItem): String =
    if (item.marker.firstOrNull()?.isDigit() == true) item.marker + "." else "•"

private fun isHeading(line: String): Boolean =
    Regex("^#{1,6}\\s+").containsMatchIn(line)

private fun isHorizontalRule(line: String): Boolean =
    line.matches(Regex("^(\\*\\s*){3,}$")) ||
        line.matches(Regex("^(-\\s*){3,}$")) ||
        line.matches(Regex("^(_\\s*){3,}$"))

private fun isTableSeparator(line: String): Boolean {
    val cells = splitTableRow(line)
    return cells.isNotEmpty() && cells.all { cell ->
        cell.trim().matches(Regex("^:?-{3,}:?$"))
    }
}

private fun splitTableRow(line: String): List<String> {
    var value = line.trim()
    if (value.startsWith("|")) value = value.drop(1)
    if (value.endsWith("|")) value = value.dropLast(1)
    return value.split('|').map { it.trim() }
}

@Composable
private fun MarkdownTable(header: List<String>, rows: List<List<String>>) {
    val width = (header.size.coerceAtLeast(1)) * 132
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.34f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.7f))
    ) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Column(modifier = Modifier.width(width.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Accent.copy(alpha = 0.07f))
                ) {
                    header.forEach { cell ->
                        Text(
                            inlineMarkdown(cell),
                            modifier = Modifier
                                .width(132.dp)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3
                        )
                    }
                }
                rows.forEach { row ->
                    HorizontalDivider(color = OutlineDark.copy(alpha = 0.55f))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        header.indices.forEach { index ->
                            Text(
                                inlineMarkdown(row.getOrElse(index) { "" }),
                                modifier = Modifier
                                    .width(132.dp)
                                    .padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                maxLines = 4
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(code: String, language: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DeepBackground.copy(alpha = 0.78f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.8f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Accent.copy(alpha = 0.035f))
                    .padding(start = 11.dp, end = 3.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    language.ifBlank { "code" }.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (language.isBlank()) TextFaint else Accent
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = TextFaint,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            HorizontalDivider(color = OutlineDark.copy(alpha = 0.6f))
            Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(
                        text = highlightCode(code.ifBlank { "(empty)" }, language),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = MonoFont,
                            lineHeight = 18.sp
                        ),
                        color = Color(0xFFC3D1CC),
                        softWrap = false
                    )
                }
            }
        }
    }
}

private val kotlinKeywords = setOf(
    "fun", "val", "var", "class", "object", "interface", "data", "sealed",
    "if", "else", "when", "for", "while", "do", "return", "break", "continue",
    "import", "package", "suspend", "try", "catch", "finally", "throw", "new",
    "public", "private", "internal", "protected", "override", "open", "abstract",
    "companion", "init", "constructor", "this", "super", "null", "true", "false",
    "is", "as", "in", "out", "by", "lateinit", "const", "inline", "reified"
)

private val otherKeywords = setOf(
    "def", "const", "let", "async", "await", "function", "export", "default",
    "from", "select", "where", "insert", "update", "delete", "create", "table",
    "and", "or", "not", "None", "True", "False", "echo", "fi", "then", "elif",
    "done", "esac", "case", "local", "readonly", "export", "source", "npm",
    "sudo", "git", "cd", "ls", "grep", "find", "cat", "curl", "ssh"
)

private fun highlightCode(code: String, language: String): AnnotatedString {
    val keywords = kotlinKeywords + otherKeywords
    val pattern = Regex(
        "(\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'|`(?:\\\\.|[^`\\\\])*`)|" +
            "(/\\*[\\s\\S]*?\\*/|//[^\\n]*|#[^\\n]*)|" +
            "(\\b\\d+(?:\\.\\d+)?\\b)|" +
            "(\\b[A-Za-z_][A-Za-z0-9_]*\\b)"
    )
    return buildAnnotatedString {
        var cursor = 0
        pattern.findAll(code).forEach { match ->
            if (match.range.first > cursor) append(code.substring(cursor, match.range.first))
            val token = match.value
            val style = when {
                match.groups[1] != null -> SpanStyle(color = Color(0xFFB8D98A))
                match.groups[2] != null -> SpanStyle(color = TextFaint, fontStyle = FontStyle.Italic)
                match.groups[3] != null -> SpanStyle(color = Warning)
                token.lowercase() in keywords || token in keywords -> SpanStyle(
                    color = Violet,
                    fontWeight = FontWeight.SemiBold
                )
                token.startsWith("$") -> SpanStyle(color = Accent)
                else -> SpanStyle(color = Color(0xFFC3D1CC))
            }
            withStyle(style) { append(token) }
            cursor = match.range.last + 1
        }
        if (cursor < code.length) append(code.substring(cursor))
    }
}

private fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) || text.startsWith("__", i) -> {
                val marker = text.substring(i, i + 2)
                val end = text.indexOf(marker, i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(inlineMarkdown(text.substring(i + 2, end)))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        append(inlineMarkdown(text.substring(i + 2, end)))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '`' -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = MonoFont,
                            background = DeepBackground,
                            color = Accent
                        )
                    ) { append(text.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '[' -> {
                val labelEnd = text.indexOf("](", i + 1)
                val urlEnd = if (labelEnd >= 0) text.indexOf(')', labelEnd + 2) else -1
                if (labelEnd > i && urlEnd > labelEnd) {
                    val label = text.substring(i + 1, labelEnd)
                    val url = text.substring(labelEnd + 2, urlEnd).trim()
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        withLink(LinkAnnotation.Url(url)) {
                            withStyle(
                                SpanStyle(
                                    color = Accent,
                                    textDecoration = TextDecoration.Underline
                                )
                            ) { append(label) }
                        }
                    } else {
                        append(text.substring(i, urlEnd + 1))
                    }
                    i = urlEnd + 1
                } else {
                    append(text[i]); i++
                }
            }
            text.startsWith("http://", i) || text.startsWith("https://", i) -> {
                var end = i
                while (end < text.length && !text[end].isWhitespace() &&
                    text[end] !in listOf(')', ']', ',', ';')
                ) end++
                val url = text.substring(i, end)
                withLink(LinkAnnotation.Url(url)) {
                    withStyle(
                        SpanStyle(color = Accent, textDecoration = TextDecoration.Underline)
                    ) { append(url) }
                }
                i = end
            }
            text[i] == '*' || text[i] == '_' -> {
                val marker = text[i]
                val end = text.indexOf(marker, i + 1)
                if (end > i + 1 && (end + 1 >= text.length || text[end + 1] != marker)) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(inlineMarkdown(text.substring(i + 1, end)))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}
