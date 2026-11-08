package dev.pvps.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pvps.data.model.ConnectionState
import dev.pvps.diagnostics.Diagnostics
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.GhostButton
import dev.pvps.ui.components.PvpsCard
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Warning

/**
 * Everything the app knows about the active link in one place: transport state,
 * tunnel port, OpenCode binding and the last ssh/opencode events. This exists so
 * a dropped tunnel can be explained without a laptop.
 */
@Composable
fun LinkScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val openCode = viewModel.openCodeState.collectAsStateValue()
    val state: ConnectionState? = host?.let { states[it.id] }
    var tick by remember { mutableIntStateOf(0) }
    val clipboard = LocalClipboardManager.current

    val status = state?.status
    val color = when (status) {
        ConnectionState.Status.CONNECTED -> Success
        ConnectionState.Status.CONNECTING, ConnectionState.Status.RECONNECTING -> Warning
        ConnectionState.Status.ERROR -> Danger
        ConnectionState.Status.NEEDS_HOST_KEY -> Warning
        else -> TextFaint
    }
    val tunnelPort = remember(tick, state?.status, host?.id) {
        host?.let { viewModel.container.ssh.tunnelPort(it.id) }
    }
    val events = remember(tick, state?.message, openCode.error) {
        Diagnostics.tail(limit = 24, tags = setOf("ssh", "opencode", "service"))
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Link",
                subtitle = host?.name ?: "no host",
                onBack = onBack,
                actions = {
                    GhostButton(
                        text = "Probe",
                        onClick = {
                            host?.let { viewModel.container.ssh.requestRecovery(it.id, "manual probe") }
                            tick++
                        }
                    )
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 760.dp)
                    .fillMaxWidth()
                    .padding(horizontal = Space.gutter)
            ) {
                PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.hero)) {
                    Column(modifier = Modifier.padding(Space.xl)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                )
                            }
                            Spacer(Modifier.width(Space.md))
                            Text(
                                when (status) {
                                    ConnectionState.Status.CONNECTED -> "Transport healthy"
                                    ConnectionState.Status.CONNECTING -> "Opening session"
                                    ConnectionState.Status.RECONNECTING -> "Reconnecting"
                                    ConnectionState.Status.NEEDS_HOST_KEY -> "Waiting for host key"
                                    ConnectionState.Status.ERROR -> "Transport failed"
                                    else -> "Offline"
                                },
                                style = MaterialTheme.typography.headlineSmall,
                                color = TextPrimary
                            )
                        }
                        Spacer(Modifier.height(Space.sm))
                        Text(
                            state?.message?.takeIf { it.isNotBlank() } ?: "No detail reported.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                        Spacer(Modifier.height(Space.lg))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            if (status == ConnectionState.Status.CONNECTED) {
                                GhostButton(
                                    text = "Disconnect",
                                    icon = Icons.Default.LinkOff,
                                    color = Danger,
                                    onClick = {
                                        host?.let { viewModel.disconnect(it.id) }
                                        tick++
                                    }
                                )
                            } else {
                                SignalButton(
                                    text = "Connect",
                                    icon = Icons.Default.Refresh,
                                    onClick = { host?.let { viewModel.connect(it.id) } }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(Space.block))
                PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
                    Column {
                        DetailRow("Host", host?.let { "${it.sshUser}@${it.sshHost}:${it.sshPort}" } ?: "—")
                        Divider()
                        DetailRow("Tunnel port", tunnelPort?.let { "127.0.0.1:$it" } ?: "not open")
                        Divider()
                        DetailRow(
                            "OpenCode",
                            when {
                                openCode.connected -> "connected" + if (openCode.serverVersion.isNotBlank()) " · v${openCode.serverVersion}" else ""
                                openCode.connecting -> "connecting"
                                else -> "offline"
                            }
                        )
                        Divider()
                        DetailRow("Sessions", "${openCode.sessions.size}")
                        Divider()
                        DetailRow("Last error", openCode.error?.takeIf { it.isNotBlank() } ?: "none")
                    }
                }

                Spacer(Modifier.height(Space.section))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "RECENT EVENTS",
                        style = PvpsType.eyebrow,
                        color = TextFaint,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        "${events.size} lines",
                        style = PvpsType.monoBody,
                        color = TextFaint
                    )
                }
                Spacer(Modifier.height(Space.sm))
                PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md)) {
                    Column(modifier = Modifier.padding(Space.md)) {
                        if (events.isEmpty()) {
                            Text(
                                "No SSH or OpenCode events recorded yet.",
                                style = PvpsType.monoBody,
                                color = TextSecondary
                            )
                        } else {
                            events.forEach { line ->
                                Text(
                                    text = line,
                                    style = PvpsType.monoBody,
                                    color = when {
                                        line.contains("error") || line.contains("fail") -> Danger
                                        line.contains("ok") || line.contains("up") -> Success
                                        else -> TextSecondary
                                    },
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(Space.sm))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                            GhostButton(
                                text = "Copy",
                                icon = Icons.Default.ContentCopy,
                                onClick = {
                                    clipboard.setText(AnnotatedString(events.joinToString("\n")))
                                }
                            )
                            GhostButton(text = "Refresh", onClick = { tick++ })
                        }
                    }
                }
                Spacer(Modifier.height(Space.dockClearance))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.lg, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            style = PvpsType.eyebrow,
            color = TextFaint,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            style = PvpsType.monoStrong,
            color = TextPrimary,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun Divider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Space.lg)
            .height(1.dp)
            .background(Color.White.copy(alpha = 0.05f))
    )
}
