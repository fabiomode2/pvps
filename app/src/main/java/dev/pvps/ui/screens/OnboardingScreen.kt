package dev.pvps.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.SetupCode
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.GhostButton
import dev.pvps.ui.components.PvpsMark
import dev.pvps.ui.components.PvpsTextField
import dev.pvps.ui.components.SectionLabel
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.TerminalWindow
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet

private const val INSTALL_COMMAND =
    "base=\"https://github.com/fabiomode2/pvps/releases/latest/download\"\n" +
        "curl -fsSLO \"\${base}/install.sh\"\n" +
        "curl -fsSLO \"\${base}/SHA256SUMS\"\n" +
        "grep -E '  install\\.sh$' SHA256SUMS | sha256sum -c -\n" +
        "bash install.sh"

@Composable
fun OnboardingScreen(
    viewModel: AppViewModel,
    onAddHost: () -> Unit,
    onKeys: () -> Unit,
    onHostCreated: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var showSetup by remember { mutableStateOf(false) }
    var setupError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PvpsMark(46.dp)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("PVPS", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
                Text(
                    "PORTABLE VPS CONTROL",
                    style = MaterialTheme.typography.labelSmall,
                    color = Accent
                )
            }
        }
        Spacer(Modifier.height(34.dp))
        Text(
            "Your server,\nin your pocket.",
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "A native SSH control plane for OpenCode, terminals, files, Docker and systemd. No cloud middleman. No public OpenCode port.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(Modifier.height(24.dp))
        TerminalWindow(
            title = "pvps / setup",
            lines = listOf(
                "$ curl -fsSLO …/install.sh",
                "$ curl -fsSLO …/SHA256SUMS",
                "$ sha256sum -c --ignore-missing SHA256SUMS",
                "$ bash install.sh",
                "# installing OpenCode v2 on 127.0.0.1:49374",
                "# done. paste the pvps-setup code into the app."
            )
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GhostButton(
                text = "Copy command",
                onClick = { clipboard.setText(AnnotatedString(INSTALL_COMMAND)) },
                icon = Icons.Default.ContentCopy
            )
            GhostButton(
                text = "Add manually",
                onClick = onAddHost,
                icon = Icons.Default.Add
            )
        }
        Spacer(Modifier.height(34.dp))
        SectionLabel("Three steps")
        Spacer(Modifier.height(8.dp))
        StepRow(
            number = "01",
            title = "Create a phone key",
            body = "Ed25519, stored in the Android Keystore. The private key never leaves the device.",
            action = "Open SSH keys",
            icon = Icons.Default.Key,
            onClick = onKeys
        )
        StepDivider()
        StepRow(
            number = "02",
            title = "Install on the server",
            body = "One command sets up OpenCode v2, tmux and a loopback-only systemd unit.",
            action = "Copy install command",
            icon = Icons.Default.ContentCopy,
            onClick = { clipboard.setText(AnnotatedString(INSTALL_COMMAND)) }
        )
        StepDivider()
        StepRow(
            number = "03",
            title = "Paste the setup code",
            body = "The installer prints a pvps-setup line with the host, user and OpenCode password.",
            action = "Paste setup code",
            icon = Icons.Default.ContentPaste,
            onClick = { showSetup = true; setupError = null },
            primary = true
        )
        Spacer(Modifier.height(30.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Accent.copy(alpha = 0.05f),
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Accent.copy(alpha = 0.16f))
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Accent.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Private by default", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Text(
                        "The app pins the SSH host key on first connect. OpenCode listens only on 127.0.0.1 and is reached through an SSH tunnel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showSetup) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetup = false },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(28.dp),
            title = {
                Text("Paste setup code", style = MaterialTheme.typography.headlineSmall, color = TextPrimary)
            },
            text = {
                Column {
                    Text(
                        "Paste the pvps-setup:… line printed by install.sh. It contains the host, user and OpenCode password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))
                    PvpsTextField(
                        value = value,
                        onValueChange = { value = it; setupError = null },
                        label = "pvps-setup:…",
                        singleLine = false,
                        minLines = 3,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let { value = it; setupError = null }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("PASTE FROM CLIPBOARD", style = MaterialTheme.typography.labelSmall)
                    }
                    if (setupError != null) {
                        Text(
                            setupError!!,
                            color = Danger,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = MonoFont
                        )
                    }
                }
            },
            confirmButton = {
                SignalButton(
                    text = "Add host",
                    onClick = {
                        val parsed = SetupCode.parse(value)
                        parsed.onSuccess { payload ->
                            val firstKey = viewModel.keys.value.firstOrNull()
                            if (firstKey == null) {
                                setupError = "Create an SSH key first, then import the setup code."
                                return@onSuccess
                            }
                            val host = SetupCode.toHost(payload).copy(keyId = firstKey.id)
                            viewModel.saveHost(host)
                            viewModel.selectHost(host.id)
                            showSetup = false
                            onHostCreated()
                        }.onFailure { setupError = it.message ?: "Invalid setup code" }
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showSetup = false }) {
                    Text("CANCEL", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        )
    }
}

@Composable
private fun StepRow(
    number: String,
    title: String,
    body: String,
    action: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    primary: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            number,
            style = MaterialTheme.typography.labelSmall,
            color = Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            Spacer(Modifier.height(9.dp))
            if (primary) {
                SignalButton(text = action, onClick = onClick, icon = icon)
            } else {
                TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Icon(icon, contentDescription = null, tint = Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(action.uppercase(), color = Accent, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Accent,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StepDivider() {
    HorizontalDivider(color = OutlineDark.copy(alpha = 0.7f))
}
