package dev.pvps.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.SetupCode
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.PvpsMark
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet

private const val INSTALL_COMMAND =
    "curl -fsSL https://github.com/fabiomode2/pvps/releases/latest/download/install.sh | bash"

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
            .padding(20.dp),
        verticalArrangement = Arrangement.Center
    ) {
        PvpsMark(58.dp)
        Spacer(Modifier.height(18.dp))
        Text("PVPS", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(
            "Portable VPS control. SSH-only, OpenCode v2 and a real terminal.",
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary
        )
        Spacer(Modifier.height(26.dp))

        StepCard("1", "Create a phone SSH key", "Ed25519, passphrase, stored in Android Keystore.") {
            OutlinedButton(onClick = onKeys, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.width(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open SSH keys")
            }
        }
        Spacer(Modifier.height(10.dp))
        StepCard("2", "Install the VPS agent", "One command sets up OpenCode v2 on loopback, tmux and a systemd unit.") {
            Text(
                INSTALL_COMMAND,
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = Accent,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF05090F), RoundedCornerShape(10.dp))
                    .padding(10.dp)
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(INSTALL_COMMAND)) }) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy")
                }
                Text("Then paste the pvps-setup code it prints.", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(10.dp))
        StepCard("3", "Add the host", "Paste the setup code or enter the SSH details manually.") {
            Button(onClick = { showSetup = true; setupError = null }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Paste setup code")
            }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onAddHost, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.width(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add host manually")
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "The app pins the SSH host key on first connect. OpenCode stays on 127.0.0.1 and is reached through an SSH tunnel; it is never exposed publicly.",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
    }

    if (showSetup) {
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Paste setup code") },
            text = {
                Column {
                    Text(
                        "Paste the pvps-setup:… line printed by install.sh. It contains the host, user and OpenCode password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it; setupError = null },
                        label = { Text("pvps-setup:…") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = {
                        clipboard.getText()?.text?.let { value = it; setupError = null }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.width(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Paste from clipboard")
                    }
                    if (setupError != null) {
                        Text(setupError!!, color = Danger, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val parsed = SetupCode.parse(value)
                    parsed.onSuccess { payload ->
                        val firstKey = viewModel.keys.value.firstOrNull()
                        val host = SetupCode.toHost(payload).copy(keyId = firstKey?.id)
                        viewModel.saveHost(host)
                        viewModel.selectHost(host.id)
                        showSetup = false
                        onHostCreated()
                    }.onFailure { setupError = it.message ?: "Invalid setup code" }
                }) { Text("Add host") }
            },
            dismissButton = { TextButton(onClick = { showSetup = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun StepCard(
    number: String,
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    number,
                    style = MaterialTheme.typography.titleLarge,
                    color = Accent,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, style = MaterialTheme.typography.titleMedium)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}
