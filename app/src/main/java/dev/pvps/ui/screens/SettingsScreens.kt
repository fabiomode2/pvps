package dev.pvps.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.pvps.BuildConfig
import dev.pvps.data.model.AppSettings
import dev.pvps.diagnostics.Diagnostics
import dev.pvps.terminal.TerminalPalettes
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.SectionCard
import dev.pvps.ui.components.SectionLabel
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatDate
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning

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
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("Server")
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MoreTile("Docker", "Containers", Icons.Default.Storage, Violet, onDocker, Modifier.weight(1f))
                    MoreTile("Services", "systemd", Icons.Default.Dns, Warning, onServices, Modifier.weight(1f))
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    MoreTile("Processes", "CPU / RAM", Icons.Default.Memory, Danger, onProcesses, Modifier.weight(1f))
                    MoreTile("Hosts", "SSH targets", Icons.Default.Dns, Violet, onHosts, Modifier.weight(1f))
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("Access")
            }
            item {
                SectionCard(title = "Security") {
                    MoreRow("SSH keys", "Generate ed25519 or import existing keys", Icons.Default.Key, onKeys)
                    MoreRow("Audit log", "Connections and destructive actions", Icons.Default.Archive, onAudit)
                }
            }
            item {
                Spacer(Modifier.height(4.dp))
                SectionLabel("App")
            }
            item {
                SectionCard(title = "PVPS") {
                    MoreRow("Settings", "Security, terminal and chat", Icons.Default.Settings, onSettings)
                    MoreRow(
                        "Check for updates",
                        "Installed ${BuildConfig.VERSION_NAME}",
                        Icons.Default.Refresh,
                        onCheckUpdates
                    )
                }
            }
            item {
                Text(
                    "PVPS ${BuildConfig.VERSION_NAME}  ·  OpenCode v2  ·  SSH only",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
                )
            }
            item { Spacer(Modifier.height(96.dp)) }
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
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.42f),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.72f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(tint.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("↗", color = TextFaint, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                letterSpacing = 0.7.sp
            )
        }
    }
}

@Composable
private fun MoreRow(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Accent.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        Text("›", color = TextFaint, style = MaterialTheme.typography.titleLarge)
    }
}

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
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            item {
                SectionCard(title = "Security") {
                    SettingSwitch(
                        title = "Device lock",
                        subtitle = "Ask for the device PIN/pattern/biometric when the app opens",
                        checked = settings.biometricLock,
                        onChecked = { value -> viewModel.updateSettings { it.copy(biometricLock = value) } }
                    )
                    SettingSwitch(
                        title = "Block screenshots",
                        subtitle = "FLAG_SECURE while the app is visible",
                        checked = settings.blockScreenshots,
                        onChecked = { value -> viewModel.updateSettings { it.copy(blockScreenshots = value) } }
                    )
                    SettingSwitch(
                        title = "Strict host keys",
                        subtitle = "Reject unknown or changed SSH host keys",
                        checked = settings.strictHostKeys,
                        onChecked = { value -> viewModel.updateSettings { it.copy(strictHostKeys = value) } }
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onHosts) { Text("Hosts") }
                        TextButton(onClick = onKeys) { Text("SSH keys") }
                        TextButton(onClick = onAudit) { Text("Audit") }
                    }
                    TextButton(onClick = { viewModel.disconnectAll() }) {
                        Text("Disconnect all and stop the SSH service", color = Danger)
                    }
                }
            }
            item {
                SectionCard(title = "Connection") {
                    SettingSwitch(
                        title = "Always-on tunnel",
                        subtitle = "Hold a wake lock while SSH is active so keepalives run with the screen off. Uses more battery.",
                        checked = settings.keepConnectionAlive,
                        onChecked = { value ->
                            viewModel.updateSettings { it.copy(keepConnectionAlive = value) }
                        }
                    )
                    BatteryOptimizationRow()
                }
            }
            item {
                SectionCard(title = "Appearance") {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        dev.pvps.ui.theme.Themes.forEach { palette ->
                            FilterChip(
                                selected = settings.uiTheme == palette.id,
                                onClick = { viewModel.updateSettings { it.copy(uiTheme = palette.id) } },
                                label = { Text(palette.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "App font size: ${(settings.fontScale * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Slider(
                        value = settings.fontScale,
                        onValueChange = { value -> viewModel.updateSettings { it.copy(fontScale = value) } },
                        valueRange = 0.8f..1.35f,
                        steps = 10
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Interface zoom: ${(settings.uiScale * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "Scales spacing and controls, not just text. Useful for tuning density without rebuilding.",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Slider(
                        value = settings.uiScale,
                        onValueChange = { value -> viewModel.updateSettings { it.copy(uiScale = value) } },
                        valueRange = 0.85f..1.2f,
                        steps = 6
                    )
                }
            }
            item {
                SectionCard(title = "Terminal") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = Accent)
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Theme", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                TerminalPalettes.byId(settings.terminalTheme).name,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                        TextButton(onClick = { showThemeMenu = !showThemeMenu }) { Text("Change") }
                    }
                    if (showThemeMenu) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TerminalPalettes.all.forEach { palette ->
                                FilterChip(
                                    selected = settings.terminalTheme == palette.id,
                                    onClick = {
                                        viewModel.updateSettings { it.copy(terminalTheme = palette.id) }
                                        showThemeMenu = false
                                    },
                                    label = { Text(palette.name, style = MaterialTheme.typography.labelSmall) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Font size: ${settings.terminalFontSize.toInt()} sp", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.terminalFontSize,
                        onValueChange = { value -> viewModel.updateSettings { it.copy(terminalFontSize = value) } },
                        valueRange = 9f..22f,
                        steps = 12
                    )
                    SettingSwitch(
                        title = "Keep screen on",
                        subtitle = "While the terminal is open",
                        checked = settings.keepScreenOnTerminal,
                        onChecked = { value -> viewModel.updateSettings { it.copy(keepScreenOnTerminal = value) } }
                    )
                }
            }
            item {
                SectionCard(title = "Chat") {
                    SettingSwitch(
                        title = "Show reasoning",
                        subtitle = "Render model reasoning blocks (collapsed by default)",
                        checked = settings.showReasoning,
                        onChecked = { value -> viewModel.updateSettings { it.copy(showReasoning = value) } }
                    )
                    SettingSwitch(
                        title = "Compact tool cards",
                        subtitle = "Collapse thinking and tool calls into one activity line",
                        checked = settings.compactToolCards,
                        onChecked = { value -> viewModel.updateSettings { it.copy(compactToolCards = value) } }
                    )
                    SettingSwitch(
                        title = "Response notifications",
                        subtitle = "Notify when an agent finishes, asks a question or needs permission",
                        checked = settings.notifyResponses,
                        onChecked = { value -> viewModel.updateSettings { it.copy(notifyResponses = value) } }
                    )
                    SettingSwitch(
                        title = "Show hidden files",
                        subtitle = "Dotfiles in the SFTP browser",
                        checked = settings.showHiddenFiles,
                        onChecked = { value -> viewModel.updateSettings { it.copy(showHiddenFiles = value) } }
                    )
                    SettingSwitch(
                        title = "File grid view",
                        subtitle = "Adaptive two-column SFTP layout",
                        checked = settings.fileGridView,
                        onChecked = { value -> viewModel.updateSettings { it.copy(fileGridView = value) } }
                    )
                }
            }
            item {
                SectionCard(title = "Diagnostics") {
                    Text(
                        "Redacted app, SSH, tunnel, SSE and queue events. Stored in a small rotating on-device log; secrets are removed before writing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = {
                            diagnosticsText = Diagnostics.snapshot()
                            showDiagnostics = !showDiagnostics
                        }) {
                            Text(if (showDiagnostics) "Hide log" else "Show log")
                        }
                        TextButton(onClick = {
                            val text = Diagnostics.exportText()
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("PVPS diagnostics", text))
                            diagnosticsNotice = "Copied ${text.length} characters"
                        }) {
                            Text("Copy")
                        }
                        TextButton(onClick = {
                            val text = Diagnostics.exportText()
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "PVPS diagnostics")
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            context.startActivity(Intent.createChooser(send, "Export diagnostics"))
                        }) {
                            Text("Export")
                        }
                        TextButton(onClick = { showClearDiagnostics = true }) {
                            Text("Clear", color = Danger)
                        }
                    }
                    diagnosticsNotice?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = Success)
                    }
                    if (showDiagnostics) {
                        Spacer(Modifier.height(6.dp))
                        MonospaceBox(diagnosticsText.takeLast(30_000))
                        Text(
                            "${diagnosticsText.lines().size} lines",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextFaint,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            item {
                SectionCard(title = "Data") {
                    Text(
                        "The vault (hosts, keys and OpenCode passwords) is encrypted with an Android Keystore AES-GCM key. It is excluded from backups.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showClearAudit = true }) {
                        Text("Clear audit log", color = Danger)
                    }
                }
            }
            item {
                SectionCard(title = "About") {
                    Text("PVPS ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Native Android control plane for SSH hosts, OpenCode v2 and Termux terminal. OpenCode must be v2.x; the server is never exposed publicly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
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
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        }
        dev.pvps.ui.components.PvpsSwitch(checked = checked, onCheckedChange = onChecked)
    }
}

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
                    TextButton(onClick = { showClear = true }, enabled = entries.isNotEmpty()) {
                        Text("Clear", color = Danger)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            item { Spacer(Modifier.height(4.dp)) }
            items(entries.asReversed(), key = { "${it.timestamp}-${it.action}-${it.detail}" }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (entry.success) "OK" else "FAIL",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (entry.success) Success else Danger
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(entry.action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(formatDate(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        }
                        Text(
                            "${entry.hostName} · ${entry.detail}",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Battery optimization", style = MaterialTheme.typography.bodyLarge)
            Text(
                if (ignoring) {
                    "Unrestricted: keepalives keep running in the background"
                } else {
                    "Optimized: Android may freeze the tunnel while the screen is off"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (ignoring) Success else TextSecondary
            )
        }
        if (!ignoring) {
            TextButton(onClick = { requestIgnoreBatteryOptimizations(context) }) {
                Text("Allow")
            }
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
