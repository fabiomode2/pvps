package dev.pvps.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.pvps.R
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.AccentDim
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.DisplayFont
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.OutlineSoft
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun PvpsBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = DeepBackground)
        // Soft signal glow instead of a hard engineering grid: the backdrop
        // should recede and let content carry the hierarchy.
        // One quiet light source. No dot grid: the old texture made every screen
        // feel like a dashboard and competed with the type.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.018f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.12f)
                )
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Accent.copy(alpha = 0.055f), Color.Transparent),
                center = Offset(size.width * 0.9f, -size.height * 0.05f),
                radius = size.width * 0.85f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Violet.copy(alpha = 0.035f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.95f),
                radius = size.width * 0.9f
            )
        )
    }
}

@Composable
fun PvpsMark(size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(10.dp, RoundedCornerShape(size * 0.3f), ambientColor = Accent.copy(alpha = 0.18f))
            .clip(RoundedCornerShape(size * 0.3f))
            .background(Brush.linearGradient(listOf(SurfaceHigh, SurfaceDark)))
            .border(BorderStroke(1.dp, Accent.copy(alpha = 0.28f)), RoundedCornerShape(size * 0.3f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pvps_mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 0.84f)
        )
    }
}

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                GlassIconButton(onClick = onBack, size = 40.dp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                PvpsMark(36.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary,
                    fontFamily = DisplayFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
        }
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.72f),
        shape = CircleShape,
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

@Composable
fun HostSwitcher(
    hosts: List<Host>,
    selected: Host?,
    state: ConnectionState?,
    onSelect: (Host) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val connected = state?.status == ConnectionState.Status.CONNECTED
    val statusColor = when (state?.status) {
        ConnectionState.Status.CONNECTED -> Success
        ConnectionState.Status.CONNECTING -> Warning
        ConnectionState.Status.NEEDS_HOST_KEY -> Warning
        ConnectionState.Status.ERROR -> Danger
        else -> TextFaint
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.66f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.10f))
                    .border(1.dp, statusColor.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                StatusDot(statusColor, 8)
            }
            Spacer(Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { expanded = true }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = selected?.name ?: "No host",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selected?.let { "${it.sshUser}@${it.sshHost}" } ?: "Add a host to begin",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(Icons.Default.ExpandMore, contentDescription = "Switch host", tint = TextSecondary)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    hosts.forEach { host ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${host.sshUser}@${host.sshHost}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            },
                            onClick = {
                                expanded = false
                                onSelect(host)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { if (connected) onDisconnect() else onConnect() },
                color = if (connected) Success.copy(alpha = 0.10f) else Accent.copy(alpha = 0.10f),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (connected) Success.copy(alpha = 0.28f) else Accent.copy(alpha = 0.30f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (connected) Icons.Default.LinkOff else Icons.Default.Power,
                        contentDescription = if (connected) "Disconnect" else "Connect",
                        tint = if (connected) Success else Accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (connected) "LIVE" else "CONNECT",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (connected) Success else Accent
                    )
                }
            }
        }
    }
}

@Composable
fun StatusDot(color: Color, size: Int = 9) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun StatusPill(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.09f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, color.copy(alpha = 0.28f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(color, 6)
            Spacer(Modifier.width(5.dp))
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

@Composable
fun StatCard(
    label: String,
    value: String,
    detail: String? = null,
    accent: Color = Accent,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    Surface(
        modifier = modifier,
        color = SurfaceRaised.copy(alpha = 0.62f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(modifier = Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Spacer(Modifier.width(7.dp))
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            }
            Spacer(Modifier.height(9.dp))
            Text(
                value,
                style = MaterialTheme.typography.displaySmall,
                color = accent,
                maxLines = 1
            )
            if (detail != null) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(50))
                        .background(OutlineSoft)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(3.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.45f), accent)))
                    )
                }
            }
        }
    }
}

@Composable
fun MetricRing(
    progress: Float,
    label: String,
    value: String,
    detail: String?,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "ring"
    )
    Surface(
        modifier = modifier,
        color = SurfaceRaised.copy(alpha = 0.62f),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(66.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    drawArc(
                        color = OutlineSoft,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * animated,
                        useCenter = false,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontFamily = MonoFont
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                if (detail != null) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = TextSecondary, maxLines = 2)
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(14.dp)
                .height(1.dp)
                .background(Accent.copy(alpha = 0.55f))
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = TextSecondary,
            letterSpacing = 1.2.sp
        )
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = SurfaceRaised.copy(alpha = 0.58f),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(15.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Accent)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                actions()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun SignalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .shadow(
                14.dp,
                shape,
                ambientColor = Accent.copy(alpha = 0.18f),
                spotColor = Accent.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.linearGradient(listOf(Accent, AccentDim))
                } else {
                    Brush.linearGradient(listOf(OutlineDark, OutlineDark))
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF03100B) else TextFaint,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(7.dp))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color(0xFF03100B) else TextFaint,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
        }
    }
}

@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = TextPrimary
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
            }
            Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = color)
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Accent.copy(alpha = 0.16f), Color.Transparent)
                    ),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            PvpsMark(58.dp)
        }
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(22.dp))
            SignalButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: (() -> Unit)? = null) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        color = Danger.copy(alpha = 0.08f),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Danger.copy(alpha = 0.24f))
    ) {
        Row(
            modifier = Modifier.padding(start = 0.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Danger)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = Danger,
                modifier = Modifier.weight(1f)
            )
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Danger, modifier = Modifier.size(17.dp))
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = "Confirm",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        shape = RoundedCornerShape(28.dp),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(confirmLabel.uppercase(), color = if (destructive) Danger else Accent, style = MaterialTheme.typography.labelLarge)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

@Composable
fun MonospaceBox(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = DeepBackground.copy(alpha = 0.72f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, OutlineDark.copy(alpha = 0.65f))
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CODE", style = MaterialTheme.typography.labelSmall, color = TextFaint)
                Spacer(Modifier.weight(1f))
                Text("MONO", style = MaterialTheme.typography.labelSmall, color = TextFaint)
            }
            HorizontalDivider(color = OutlineSoft.copy(alpha = 0.7f))
            Text(
                text = text.ifBlank { "(no output)" },
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = MonoFont,
                color = Color(0xFFC3D1CC)
            )
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = TextPrimary,
            fontFamily = MonoFont,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TinyAction(label: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        color = SurfaceRaised.copy(alpha = 0.7f),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Text(
            label.uppercase(),
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = TextPrimary
        )
    }
}

@Composable
fun TerminalWindow(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFF040705),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, OutlineDark)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceRaised.copy(alpha = 0.45f))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusDot(Danger, 7)
                Spacer(Modifier.width(6.dp))
                StatusDot(Warning, 7)
                Spacer(Modifier.width(6.dp))
                StatusDot(Success, 7)
                Spacer(Modifier.weight(1f))
                Text(title.uppercase(), style = MaterialTheme.typography.labelSmall, color = TextFaint)
            }
            Column(modifier = Modifier.padding(14.dp)) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = MonoFont,
                        color = when {
                            line.startsWith("$") -> Accent
                            line.startsWith("#") -> TextFaint
                            else -> Color(0xFFB8C9BE)
                        },
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
                BlinkingCursor()
            }
        }
    }
}

@Composable
private fun BlinkingCursor() {
    val transition = rememberInfiniteTransition(label = "cursor")
    val alpha by transition.animateFloat(
        initialValue = 0.15f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "cursorAlpha"
    )
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(width = 8.dp, height = 15.dp)
            .background(Accent.copy(alpha = alpha), RoundedCornerShape(2.dp))
    )
}

@Composable
fun PvpsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 21.dp else 3.dp,
        animationSpec = tween(180),
        label = "switchKnob"
    )
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(50))
            .background(if (checked) Accent.copy(alpha = 0.22f) else SurfaceHigh)
            .border(
                1.dp,
                if (checked) Accent.copy(alpha = 0.75f) else OutlineDark,
                RoundedCornerShape(50)
            )
            .clickable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(if (checked) Accent else TextSecondary)
        )
    }
}

@Composable
fun PvpsTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 5,
    placeholder: String? = null,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = MaterialTheme.typography.labelSmall) },
        placeholder = placeholder?.let { { Text(it, color = TextFaint) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(16.dp),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent.copy(alpha = 0.8f),
            unfocusedBorderColor = OutlineDark,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextSecondary,
            cursorColor = Accent,
            focusedContainerColor = SurfaceRaised.copy(alpha = 0.45f),
            unfocusedContainerColor = SurfaceRaised.copy(alpha = 0.25f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
        value /= 1024
        index++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[index])
}

fun relativeTime(timestamp: Long): String {
    if (timestamp <= 0) return "never"
    val delta = System.currentTimeMillis() - timestamp
    if (delta < 60_000) return "just now"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    if (minutes < 60) return "${minutes}m ago"
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    if (hours < 24) return "${hours}h ago"
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return "${days}d ago"
}

fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "-"
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(timestamp))
}
