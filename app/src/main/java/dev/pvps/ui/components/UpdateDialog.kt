package dev.pvps.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.update.UpdateInfo
import dev.pvps.update.UpdateState

@Composable
fun UpdateDialog(
    state: UpdateState,
    needsUnknownSources: Boolean,
    onDismiss: () -> Unit,
    onUpdate: (UpdateInfo) -> Unit,
    onInstall: () -> Unit,
    onOpenSettings: () -> Unit
) {
    when (state) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("PVPS ${state.info.version} available") },
            text = {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "A newer release is available on GitHub.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.info.notes.ifBlank { "No release notes." },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = { TextButton(onClick = { onUpdate(state.info) }) { Text("Update now", color = Accent) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = { },
            title = { Text("Downloading update") },
            text = {
                Column {
                    LinearProgressIndicator(
                        progress = { state.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            },
            confirmButton = { }
        )

        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Ready to install") },
            text = {
                Text(
                    if (needsUnknownSources) {
                        "PVPS ${state.info.version} is downloaded. Android needs permission to install unknown apps for PVPS. Open the setting, allow it, then return."
                    } else {
                        "PVPS ${state.info.version} is downloaded and verified. The system installer will open next."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(onClick = if (needsUnknownSources) onOpenSettings else onInstall) {
                    Text(if (needsUnknownSources) "Open settings" else "Install", color = Accent)
                }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
        )

        // Automatic checks are silent: offline/rate-limit/no-release errors must not
        // interrupt the user on every launch. Manual checks can surface them later.
        is UpdateState.Error -> Unit

        UpdateState.Checking, UpdateState.Idle -> Unit
    }
}
