package dev.pvps.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Power
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
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
import dev.pvps.ui.theme.CardRadius
import dev.pvps.ui.theme.DisplayFont
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.DeepBackground
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.OutlineSoft
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
import dev.pvps.ui.theme.Warning
import dev.pvps.ui.theme.cardFill
import dev.pvps.ui.theme.pressable
import dev.pvps.ui.theme.topEdgeLight
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/* ------------------------------------------------------------------------- */
/* Canvas                                                                     */
/* ------------------------------------------------------------------------- */

/**
 * Ambient backdrop: two very soft light sources under a dark wash. Static on
 * purpose — a static gradient costs nothing per frame, and the life in the UI
 * comes from the elements themselves.
 */
@Composable
fun PvpsBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = DeepBackground)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.022f),
                0.45f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.28f)
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Accent.copy(alpha = 0.075f), Color.Transparent),
                center = Offset(size.width * 0.86f, -size.height * 0.02f),
                radius = size.width * 0.95f
            )
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Violet.copy(alpha = 0.05f), Color.Transparent),
                center = Offset(size.width * 0.08f, size.height * 1.02f),
                radius = size.width * 1.05f
            )
        )
    }
}

@Composable
fun PvpsMark(size: Dp = 36.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .shadow(12.dp, RoundedCornerShape(size * 0.32f), ambientColor = Accent.copy(alpha = 0.22f))
            .clip(RoundedCornerShape(size * 0.32f))
            .background(cardFill(SurfaceHigh, SurfaceDark))
            .border(1.dp, topEdgeLight(0.14f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_pvps_mark),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(size * 0.82f)
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Surfaces                                                                   */
/* ------------------------------------------------------------------------- */

/**
 * The one raised surface used across the app: soft vertical wash, hairline that
 * catches the light on the top edge, and no heavy outline.
 */
@Composable
fun PvpsCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(CardRadius),
    fill: Brush = cardFill(SurfaceRaised.copy(alpha = 0.86f), SurfaceDark.copy(alpha = 0.92f)),
    content: @Composable androidx.compose.foundation.layout.BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, topEdgeLight(0.075f), shape)
    ) {
        content()
    }
}

/* ------------------------------------------------------------------------- */
/* Headers                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
fun ScreenHeader(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (onBack != null) Space.md else Space.gutter, end = Space.sm, top = Space.sm, bottom = Space.md),
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
            Spacer(Modifier.width(Space.md))
        } else {
            PvpsMark(36.dp)
            Spacer(Modifier.width(Space.md))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
                fontFamily = DisplayFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Spacer(Modifier.height(1.dp))
                Text(
                    text = subtitle.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
            verticalAlignment = Alignment.CenterVertically
        ) { actions() }
    }
}

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceRaised.copy(alpha = 0.78f))
            .border(1.dp, hairlineTint(0.09f), CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

private fun hairlineTint(strength: Float): Color = Color.White.copy(alpha = strength)

/* ------------------------------------------------------------------------- */
/* Status                                                                     */
/* ------------------------------------------------------------------------- */

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
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.22f), RoundedCornerShape(Radius.pill))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusDot(color, 5)
        Spacer(Modifier.width(6.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/* ------------------------------------------------------------------------- */
/* Host switcher                                                              */
/* ------------------------------------------------------------------------- */

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
        ConnectionState.Status.RECONNECTING -> Warning
        ConnectionState.Status.NEEDS_HOST_KEY -> Warning
        ConnectionState.Status.ERROR -> Danger
        else -> TextFaint
    }
    PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
        Row(
            modifier = Modifier.padding(start = Space.md, end = Space.sm, top = Space.md, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.10f))
                    .border(1.dp, statusColor.copy(alpha = 0.26f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                StatusDot(statusColor, 7)
            }
            Spacer(Modifier.width(Space.md))
            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .pressable { expanded = true }
                        .padding(vertical = Space.xs, horizontal = Space.xxs),
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
                            style = PvpsType.monoBody,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Switch host",
                        tint = TextFaint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    hosts.forEach { host ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(host.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${host.sshUser}@${host.sshHost}",
                                        style = PvpsType.monoBody,
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
            Spacer(Modifier.width(Space.sm))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(if (connected) Success.copy(alpha = 0.10f) else Accent.copy(alpha = 0.12f))
                    .border(
                        1.dp,
                        if (connected) Success.copy(alpha = 0.26f) else Accent.copy(alpha = 0.30f),
                        RoundedCornerShape(Radius.pill)
                    )
                    .pressable { if (connected) onDisconnect() else onConnect() }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (connected) Icons.Default.LinkOff else Icons.Default.Power,
                        contentDescription = if (connected) "Disconnect" else "Connect",
                        tint = if (connected) Success else Accent,
                        modifier = Modifier.size(15.dp)
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

/* ------------------------------------------------------------------------- */
/* Section rhythm                                                             */
/* ------------------------------------------------------------------------- */

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = PvpsType.eyebrow,
        color = TextFaint,
        modifier = modifier.fillMaxWidth()
    )
}

/** Eyebrow plus an optional trailing slot, with consistent block spacing. */
@Composable
fun SectionHead(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Space.section, bottom = Space.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = PvpsType.eyebrow,
            color = TextFaint,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                style = PvpsType.eyebrow,
                color = TextFaint,
                modifier = Modifier.weight(1f)
            )
            actions()
        }
        PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
            Column(modifier = Modifier.padding(Space.lg)) { content() }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Metrics                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
fun StatCard(
    label: String,
    value: String,
    detail: String? = null,
    accent: Color = Accent,
    modifier: Modifier = Modifier,
    progress: Float? = null
) {
    PvpsCard(modifier = modifier, shape = RoundedCornerShape(Radius.lg)) {
        Column(modifier = Modifier.padding(Space.lg)) {
            Text(
                label.uppercase(),
                style = PvpsType.eyebrow,
                color = TextFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(Space.sm))
            Text(
                value,
                style = PvpsType.numericHero,
                color = accent,
                maxLines = 1
            )
            if (detail != null) {
                Spacer(Modifier.height(Space.xs))
                Text(
                    detail,
                    style = PvpsType.monoBody,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (progress != null) {
                Spacer(Modifier.height(Space.md))
                MeterBar(progress = progress, accent = accent)
            }
        }
    }
}

/** Thin progress line with a soft head; used under telemetry values. */
@Composable
fun MeterBar(
    progress: Float,
    accent: Color = Accent,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp
) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(520),
        label = "meter"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Radius.pill))
            .background(Color.White.copy(alpha = 0.07f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated)
                .height(height)
                .clip(RoundedCornerShape(Radius.pill))
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.42f), accent)))
        )
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
    PvpsCard(modifier = modifier, shape = RoundedCornerShape(Radius.lg)) {
        Row(
            modifier = Modifier.padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 5.dp.toPx()
                    drawArc(
                        color = Color.White.copy(alpha = 0.08f),
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
                    style = PvpsType.numeric,
                    color = TextPrimary
                )
            }
            Spacer(Modifier.width(Space.lg))
            Column {
                Text(label.uppercase(), style = PvpsType.eyebrow, color = TextFaint)
                if (detail != null) {
                    Spacer(Modifier.height(Space.xxs))
                    Text(detail, style = PvpsType.monoBody, color = TextSecondary, maxLines = 2)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Actions                                                                    */
/* ------------------------------------------------------------------------- */

@Composable
fun SignalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null
) {
    val shape = RoundedCornerShape(Radius.pill)
    Box(
        modifier = modifier
            .shadow(
                if (enabled) 18.dp else 0.dp,
                shape,
                ambientColor = Accent.copy(alpha = 0.22f),
                spotColor = Accent.copy(alpha = 0.18f)
            )
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(listOf(Accent, AccentDim))
                else Brush.linearGradient(listOf(OutlineDark, OutlineDark))
            )
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.xl, vertical = 13.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) Color(0xFF10160A) else TextFaint,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(Space.sm))
            }
            Text(
                text = text.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) Color(0xFF10160A) else TextFaint,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                maxLines = 1
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
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(Radius.pill))
            .pressable(onClick = onClick)
            .padding(horizontal = Space.lg, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text.uppercase(), style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/** Square-ish icon action used in the quick action grid. */
@Composable
fun ActionTile(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    tint: Color = Accent
) {
    PvpsCard(modifier = modifier, shape = RoundedCornerShape(Radius.md)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .pressable(onClick = onClick)
                .padding(Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.xs))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (meta != null) {
                Text(
                    meta,
                    style = PvpsType.monoBody,
                    color = TextFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* States                                                                     */
/* ------------------------------------------------------------------------- */

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
            .padding(horizontal = Space.xxl, vertical = Space.huge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .background(
                    Brush.radialGradient(listOf(Accent.copy(alpha = 0.14f), Color.Transparent)),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            PvpsMark(62.dp)
        }
        Spacer(Modifier.height(Space.xl))
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(Space.sm))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.xl))
            SignalButton(actionLabel, onAction)
        }
    }
}

@Composable
fun ErrorBanner(message: String, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(Danger.copy(alpha = 0.09f))
            .border(1.dp, Danger.copy(alpha = 0.22f), RoundedCornerShape(Radius.md))
            .padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Danger,
            modifier = Modifier.weight(1f).padding(vertical = Space.sm)
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Danger, modifier = Modifier.size(16.dp))
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
        shape = RoundedCornerShape(Radius.xl),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) {
                Text(
                    confirmLabel.uppercase(),
                    color = if (destructive) Danger else Accent,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    )
}

/* ------------------------------------------------------------------------- */
/* Data bits                                                                  */
/* ------------------------------------------------------------------------- */

@Composable
fun MonospaceBox(text: String, modifier: Modifier = Modifier) {
    val clipboard = LocalClipboardManager.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(Radius.sm))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Space.md, end = Space.xs, top = Space.xxs, bottom = Space.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("OUTPUT", style = PvpsType.eyebrow, color = TextFaint)
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = { clipboard.setText(AnnotatedString(text)) },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = TextFaint,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Text(
            text = text.ifBlank { "(no output)" },
            modifier = Modifier.padding(horizontal = Space.md, vertical = Space.sm),
            style = PvpsType.monoBody,
            color = Color(0xFFC6D2CB)
        )
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            style = PvpsType.eyebrow,
            color = TextFaint,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = PvpsType.monoStrong,
            color = TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TinyAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        label.uppercase(),
        modifier = modifier
            .clip(RoundedCornerShape(Radius.pill))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(Radius.pill))
            .pressable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = 7.dp),
        style = MaterialTheme.typography.labelSmall,
        color = TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
fun TerminalWindow(
    title: String,
    lines: List<String>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.lg))
            .background(Color(0xFF040705))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(Radius.lg))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.03f))
                .padding(horizontal = Space.md, vertical = Space.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusDot(Danger.copy(alpha = 0.8f), 6)
            Spacer(Modifier.width(6.dp))
            StatusDot(Warning.copy(alpha = 0.8f), 6)
            Spacer(Modifier.width(6.dp))
            StatusDot(Success.copy(alpha = 0.8f), 6)
            Spacer(Modifier.weight(1f))
            Text(title.uppercase(), style = PvpsType.eyebrow, color = TextFaint)
        }
        Column(modifier = Modifier.padding(Space.lg)) {
            lines.forEach { line ->
                Text(
                    text = line,
                    style = PvpsType.monoBody,
                    color = when {
                        line.startsWith("$") -> Accent
                        line.startsWith("#") -> TextFaint
                        else -> Color(0xFFB8C9BE)
                    },
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------- */
/* Inputs                                                                     */
/* ------------------------------------------------------------------------- */

@Composable
fun PvpsSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 22.dp else 3.dp,
        animationSpec = tween(220),
        label = "switchKnob"
    )
    val trackAlpha by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(220),
        label = "switchTrack"
    )
    Box(
        modifier = modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(RoundedCornerShape(Radius.pill))
            .background(
                if (checked) Accent.copy(alpha = 0.22f)
                else Color.White.copy(alpha = 0.06f + 0.02f * (1f - trackAlpha))
            )
            .border(
                1.dp,
                if (checked) Accent.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.10f),
                RoundedCornerShape(Radius.pill)
            )
            .pressable { onCheckedChange(!checked) }
    ) {
        Box(
            modifier = Modifier
                .offset(x = knobOffset, y = 3.dp)
                .size(20.dp)
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
        placeholder = placeholder?.let { { Text(it, color = TextFaint, style = MaterialTheme.typography.bodyMedium) } },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        maxLines = maxLines,
        shape = RoundedCornerShape(Radius.md),
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = keyboardOptions,
        visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent.copy(alpha = 0.70f),
            unfocusedBorderColor = Color.White.copy(alpha = 0.10f),
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextFaint,
            cursorColor = Accent,
            focusedContainerColor = SurfaceRaised.copy(alpha = 0.55f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.035f),
            focusedTextColor = TextPrimary,
            unfocusedTextColor = TextPrimary
        )
    )
}

/* ------------------------------------------------------------------------- */
/* Helpers                                                                    */
/* ------------------------------------------------------------------------- */

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

/** OutlineSoft kept for legacy call sites that tint dividers by hand. */
val Divider: Color get() = OutlineSoft.copy(alpha = 0.9f)
