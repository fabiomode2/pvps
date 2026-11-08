package dev.pvps.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pvps.BuildConfig
import dev.pvps.diagnostics.Diagnostics
import dev.pvps.terminal.TerminalPalettes
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.PvpsCard
import dev.pvps.ui.components.PvpsSwitch
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.TinyAction
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatDate
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DisplayFont
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Themes
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
import dev.pvps.ui.theme.pressable

/* ------------------------------------------------------------------------- */
/* Control / More                                                             */
/* ------------------------------------------------------------------------- */

@Composable
fun MoreScreen(
    viewModel: AppViewModel,
    onHosts: () -> Unit,
    onKeys: () -> Unit,
    onDocker: () -> Unit,
    onServices: () -> Unit,
    onProcesses: () -> Unit,
    onAudit: () -> Unit,
    onSettings: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenFiles: () -> Unit,
    onCheckUpdates: () -> Unit
) {
    val host = viewModel.selectedHost.collectAsStateValue()
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Control",
                subtitle = host?.let { "${it.name} · ${it.sshHost}" } ?: "No host selected"
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.block)
        ) {
            item {
                Spacer(Modifier.height(Space.xs))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                    MoreTile("Docker", "Containers", Icons.Default.Storage, Violet, onDocker, Modifier.weight(1f))
                    MoreTile("Services", "systemd", Icons.Default.Dns, Warning, onServices, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.block)) {
                    MoreTile("Processes", "CPU · RAM", Icons.Default.Memory, Danger, onProcesses, Modifier.weight(1f))
                    MoreTile("Hosts", "SSH targets", Icons.Default.Dns, Success, onHosts, Modifier.weight(1f))
                }
            }

            item { SettingsGroup(title = "Access") {
                GroupRow("SSH keys", "Generate ed25519 or import existing", Icons.Default.Key, Accent, onKeys)
                RowDivider()
                GroupRow("Audit log", "Connections and destructive actions", Icons.Default.Archive, Violet, onAudit)
            } }

            item { SettingsGroup(title = "App") {
                GroupRow("Settings", "Security, terminal and chat", Icons.Default.Settings, TextSecondary, onSettings)
                RowDivider()
                GroupRow(
                    "Check for updates",
                    "Installed ${BuildConfig.VERSION_NAME}",
                    Icons.Default.Refresh,
                    TextSecondary,
                    onCheckUpdates
                )
            } }

            item {
                Spacer(Modifier.height(Space.sm))
                Text(
                    "PVPS ${BuildConfig.VERSION_NAME}  ·  OpenCode v2  ·  SSH only",
                    style = PvpsType.monoBody,
                    color = TextFaint,
                    modifier = Modifier.padding(horizontal = Space.xs)
                )
            }
            item { Spacer(Modifier.height(Space.dockClearance)) }
        }
    }
}

@Composable
private fun MoreTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    PvpsCard(modifier = modifier, shape = RoundedCornerShape(Radius.md)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = onClick)
                .padding(Space.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radius.xs))
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = TextFaint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(Space.lg))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
            Spacer(Modifier.height(Space.xxs))
            Text(
                subtitle.uppercase(),
                style = PvpsType.eyebrow,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Settings                                                                   */
/* ------------------------------------------------------------------------- */

@Composable
fun SettingsScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onHosts: () -> Unit,
    onKeys: () -> Unit,
    onAudit: () -> Unit
) {
    val settings = viewModel.settings.collectAsStateValue()
    val context = LocalContext.current
    var showClearAudit by remember { mutableStateOf(false) }
    var showClearDiagnostics by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    var diagnosticsText by remember { mutableStateOf("") }
    var diagnosticsNotice by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { ScreenHeader(title = "Settings", onBack = onBack) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.lg)
        ) {
            item {
                Spacer(Modifier.height(Space.xs))
                SettingsGroup(title = "Security") {
                    SettingSwitch(
                        title = "Device lock",
                        subtitle = "Ask for the device PIN, pattern or biometrics when the app opens",
                        checked = settings.biometricLock,
                        onChecked = { value -> viewModel.updateSettings { it.copy(biometricLock = value) } }
                    )
                    RowDivider()
                    SettingSwitch(
                        title = "Block screenshots",
                        subtitle = "FLAG_SECURE while the app is visible",
                        checked = settings.blockScreenshots,
                        onChecked = { value -> viewModel.updateSettings { it.copy(blockScreenshots = value) } }
                    )
                    RowDivider()
                    SettingSwitch(
                        title = "Strict host keys",
                        subtitle = "Reject unknown or changed SSH host keys",
                        checked = settings.strictHostKeys,
                        onChecked = { value -> viewModel.updateSettings { it.copy(strictHostKeys = value) } }
                    )
                    RowDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(Space.md),
                        horizontalArrangement = Arrangement.spacedBy(Space.sm)
                    ) {
                        TinyAction("Hosts", onClick = onHosts)
                        TinyAction("SSH keys", onClick = onKeys)
                        TinyAction("Audit", onClick = onAudit)
                    }
                    RowDivider()
                    Text(
                        "Disconnect all and stop the SSH service",
                        style = MaterialTheme.typography.labelLarge,
                        color = Danger,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pressable { viewModel.disconnectAll() }
                            .padding(Space.lg)
                    )
                }
            }

            item {
                SettingsGroup(title = "Connection") {
                    SettingSwitch(
                        title = "Always-on tunnel",
                        subtitle = "Hold a wake lock while SSH is active so keepalives run with the screen off. Uses more battery.",
                        checked = settings.keepConnectionAlive,
                        onChecked = { value ->
                            viewModel.updateSettings { it.copy(keepConnectionAlive = value) }
                        }
                    )
                    RowDivider()
                    BatteryOptimizationRow()
                }
            }

            item {
                SettingsGroup(title = "Appearance") {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Text("Theme", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Spacer(Modifier.height(Space.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Themes.forEach { palette ->
                                ThemeChip(
                                    label = palette.label,
                                    selected = settings.uiTheme == palette.id,
                                    onClick = { viewModel.updateSettings { it.copy(uiTheme = palette.id) } }
                                )
                            }
                        }
                    }
                    RowDivider()
                    Column(modifier = Modifier.padding(Space.lg)) {
                        SliderRow(
                            label = "App font size",
                            value = "${(settings.fontScale * 100).toInt()}%",
                            hint = "Scales text across the whole app."
                        )
                        Slider(
                            value = settings.fontScale,
                            onValueChange = { value -> viewModel.updateSettings { it.copy(fontScale = value) } },
                            valueRange = 0.8f..1.35f,
                            steps = 10,
                            colors = sliderColors()
                        )
                        Spacer(Modifier.height(Space.sm))
                        SliderRow(
                            label = "Interface zoom",
                            value = "${(settings.uiScale * 100).toInt()}%",
                            hint = "Scales spacing and controls, not just text."
                        )
                        Slider(
                            value = settings.uiScale,
                            onValueChange = { value -> viewModel.updateSettings { it.copy(uiScale = value) } },
                            valueRange = 0.85f..1.2f,
                            steps = 6,
                            colors = sliderColors()
                        )
                    }
                }
            }

            item {
                SettingsGroup(title = "Terminal") {
                    GroupRow(
                        title = "Palette",
                        subtitle = TerminalPalettes.byId(settings.terminalTheme).name,
                        icon = Icons.Default.Palette,
                        tint = Accent,
                        onClick = { showThemeMenu = !showThemeMenu },
                        trailing = {
                            Text(
                                if (showThemeMenu) "Hide" else "Change",
                                style = MaterialTheme.typography.labelLarge,
                                color = Accent
                            )
                        }
                    )
                    if (showThemeMenu) {
                        Row(
                            modifier = Modifier.padding(start = Space.lg, end = Space.lg, bottom = Space.md),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm)
                        ) {
                            TerminalPalettes.all.forEach { palette ->
                                ThemeChip(
                                    label = palette.name,
                                    selected = settings.terminalTheme == palette.id,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(terminalTheme = palette.id) }
                                        showThemeMenu = false
                                    }
                                )
                            }
                        }
                    }
                    RowDivider()
                    Column(modifier = Modifier.padding(Space.lg)) {
                        SliderRow(
                            label = "Font size",
                            value = "${settings.terminalFontSize.toInt()} sp",
                            hint = null
                        )
                        Slider(
                            value = settings.terminalFontSize,
                            onValueChange = { value -> viewModel.updateSettings { it.copy(terminalFontSize = value) } },
                            valueRange = 9f..22f,
                            steps = 12,
                            colors = sliderColors()
                        )
                    }
                    RowDivider()
                    SettingSwitch(
                        title = "Keep screen on",
                        subtitle = "While the terminal is open",
                        checked = settings.keepScreenOnTerminal,
                        onChecked = { value -> viewModel.updateSettings { it.copy(keepScreenOnTerminal = value) } }
                    )
                }
            }

            item {
                SettingsGroup(title = "Chat") {
                    SettingSwitch(
                        title = "Show reasoning",
                        subtitle = "Render model reasoning blocks, collapsed by default",
                        checked = settings.showReasoning,
                        onChecked = { value -> viewModel.updateSettings { it.copy(showReasoning = value) } }
                    )
                    RowDivider()
                    SettingSwitch(
                        title = "Compact tool cards",
                        subtitle = "Collapse thinking and tool calls into one activity line",
                        checked = settings.compactToolCards,
                        onChecked = { value -> viewModel.updateSettings { it.copy(compactToolCards = value) } }
                    )
                    RowDivider()
                    SettingSwitch(
                        title = "Response notifications",
                        subtitle = "Notify when an agent finishes, asks a question or needs permission",
                        checked = settings.notifyResponses,
                        onChecked = { value -> viewModel.updateSettings { it.copy(notifyResponses = value) } }
                    )
                    RowDivider()
                    SettingSwitch(
                        title = "Show hidden files",
                        subtitle = "Dotfiles in the SFTP browser",
                        checked = settings.showHiddenFiles,
                        onChecked = { value -> viewModel.updateSettings { it.copy(showHiddenFiles = value) } }
                    )
                    RowDivider()
                    SettingSwitch(
                        title = "File grid view",
                        subtitle = "Adaptive two-column SFTP layout",
                        checked = settings.fileGridView,
                        onChecked = { value -> viewModel.updateSettings { it.copy(fileGridView = value) } }
                    )
                }
            }

            item {
                SettingsGroup(title = "Diagnostics") {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Text(
                            "Redacted app, SSH, tunnel, SSE and queue events. Stored in a small rotating on-device log; secrets are removed before writing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(Space.md))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            TinyAction(if (showDiagnostics) "Hide log" else "Show log") {
                                diagnosticsText = Diagnostics.snapshot()
                                showDiagnostics = !showDiagnostics
                            }
                            TinyAction("Copy") {
                                val text = Diagnostics.exportText()
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("PVPS diagnostics", text))
                                diagnosticsNotice = "Copied ${text.length} characters"
                            }
                            TinyAction("Export") {
                                val text = Diagnostics.exportText()
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "PVPS diagnostics")
                                    putExtra(Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(Intent.createChooser(send, "Export diagnostics"))
                            }
                            TinyAction("Clear") { showClearDiagnostics = true }
                        }
                        diagnosticsNotice?.let {
                            Spacer(Modifier.height(Space.sm))
                            Text(it, style = MaterialTheme.typography.labelSmall, color = Success)
                        }
                        if (showDiagnostics) {
                            Spacer(Modifier.height(Space.md))
                            MonospaceBox(diagnosticsText.takeLast(30_000))
                            Text(
                                "${diagnosticsText.lines().size} lines",
                                style = PvpsType.monoBody,
                                color = TextFaint,
                                modifier = Modifier.padding(top = Space.sm)
                            )
                        }
                    }
                }
            }

            item {
                SettingsGroup(title = "Data") {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Text(
                            "The vault (hosts, keys and OpenCode passwords) is encrypted with an Android Keystore AES-GCM key and excluded from backups.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(Space.md))
                        Text(
                            "Clear audit log",
                            style = MaterialTheme.typography.labelLarge,
                            color = Danger,
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .pressable { showClearAudit = true }
                                .padding(vertical = Space.sm)
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(horizontal = Space.xs)) {
                    Text(
                        "PVPS ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary,
                        fontFamily = DisplayFont
                    )
                    Spacer(Modifier.height(Space.xs))
                    Text(
                        "Native Android control plane for SSH hosts, OpenCode v2 and a Termux terminal. OpenCode must be v2.x; the server is never exposed publicly.",
                        style = PvpsType.monoBody,
                        color = TextFaint
                    )
                }
            }
            item { Spacer(Modifier.height(Space.dockClearance)) }
        }
    }

    if (showClearAudit) {
        ConfirmDialog(
            title = "Clear audit log?",
            message = "This removes the local record of connections and destructive actions.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { viewModel.clearAudit() },
            onDismiss = { showClearAudit = false }
        )
    }
    if (showClearDiagnostics) {
        ConfirmDialog(
            title = "Clear diagnostics?",
            message = "Removes the in-memory and rotating diagnostic logs from this device.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = {
                Diagnostics.clear()
                diagnosticsText = ""
                showDiagnostics = false
                diagnosticsNotice = "Diagnostics cleared"
                showClearDiagnostics = false
            },
            onDismiss = { showClearDiagnostics = false }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Rows                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
private fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.lg)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.05f))
    )
}

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = PvpsType.eyebrow,
            color = TextFaint,
            modifier = Modifier.padding(start = Space.xs, bottom = Space.sm)
        )
        PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
            Column { content() }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable { onChecked(!checked) }
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(Modifier.width(Space.md))
        PvpsSwitch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun GroupRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.xs))
                .background(tint.copy(alpha = 0.11f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(Space.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary, maxLines = 1)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailing != null) {
            trailing()
        } else {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun ThemeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Accent else TextSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(if (selected) Accent.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f))
            .pressable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm)
    )
}

@Composable
private fun SliderRow(label: String, value: String, hint: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        }
        Text(value, style = PvpsType.monoStrong, color = Accent)
    }
}

@Composable
private fun sliderColors() = SliderDefaults.colors(
    thumbColor = Accent,
    activeTrackColor = Accent,
    inactiveTrackColor = Color.White.copy(alpha = 0.10f),
    activeTickColor = Color.Transparent,
    inactiveTickColor = Color.Transparent
)

/* ------------------------------------------------------------------------- */
/* Audit                                                                      */
/* ------------------------------------------------------------------------- */

@Composable
fun AuditScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val entries = viewModel.audit.collectAsStateValue()
    var showClear by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Audit log",
                subtitle = "${entries.size} entries",
                onBack = onBack,
                actions = {
                    if (entries.isNotEmpty()) {
                        TinyAction("Clear") { showClear = true }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Space.gutter),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            item { Spacer(Modifier.height(Space.xs)) }
            items(entries.asReversed(), key = { "${it.timestamp}-${it.action}-${it.detail}" }) { entry ->
                PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md)) {
                    Column(modifier = Modifier.padding(Space.lg)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (entry.success) Success else Danger)
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                entry.action,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextPrimary,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                formatDate(entry.timestamp),
                                style = PvpsType.monoBody,
                                color = TextFaint
                            )
                        }
                        Spacer(Modifier.height(Space.xs))
                        Text(
                            "${entry.hostName} · ${entry.detail}",
                            style = PvpsType.monoBody,
                            color = TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(Space.dockClearance)) }
        }
    }
    if (showClear) {
        ConfirmDialog(
            title = "Clear audit log?",
            message = "This cannot be undone.",
            confirmLabel = "Clear",
            destructive = true,
            onConfirm = { viewModel.clearAudit() },
            onDismiss = { showClear = false }
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Battery                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
private fun BatteryOptimizationRow() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var ignoring by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    // Refresh when the user comes back from the system dialog.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                ignoring = isIgnoringBatteryOptimizations(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Battery optimization", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Spacer(Modifier.height(Space.xxs))
            Text(
                if (ignoring) {
                    "Unrestricted · keepalives keep running in the background"
                } else {
                    "Optimized · Android may freeze the tunnel while the screen is off"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (ignoring) Success else TextSecondary
            )
        }
        if (!ignoring) {
            TinyAction("Allow") { requestIgnoreBatteryOptimizations(context) }
        }
    }
}

private fun isIgnoringBatteryOptimizations(context: Context): Boolean = runCatching {
    context.getSystemService(PowerManager::class.java)
        ?.isIgnoringBatteryOptimizations(context.packageName) == true
}.getOrDefault(false)

private fun requestIgnoreBatteryOptimizations(context: Context) {
    val direct = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}")
    )
    if (runCatching { context.startActivity(direct) }.isSuccess) return
    // Fall back to the list of apps when the OEM blocks the direct request.
    runCatching {
        context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
    }
}
