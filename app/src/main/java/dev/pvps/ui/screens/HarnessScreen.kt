package dev.pvps.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.GlassIconButton
import dev.pvps.ui.components.PvpsCard
import dev.pvps.ui.components.PvpsTextField
import dev.pvps.ui.components.SignalButton
import dev.pvps.ui.components.TinyAction
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.PvpsType
import dev.pvps.ui.theme.Radius
import dev.pvps.ui.theme.Space
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Embedded web surface for the host's own web UI (DeepSeek Harness today).
 *
 * A loopback URL is not loaded directly: the page lives on the server, so the
 * app opens an on-demand SSH forward for the port in the URL and points the
 * WebView at that local port. Everything else (Direct mode, LAN, Tailscale) is
 * loaded as-is.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun HarnessScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val appSettings = viewModel.settings.collectAsStateValue()
    val state = host?.let { states[it.id] }
    val connected = state?.status == dev.pvps.data.model.ConnectionState.Status.CONNECTED

    var configuredUrl by remember(appSettings.harnessUrl) { mutableStateOf(appSettings.harnessUrl) }
    var loadedUrl by remember(host?.id) { mutableStateOf<String?>(null) }
    var resolving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableIntStateOf(0) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val context = LocalContext.current

    LaunchedEffect(configuredUrl, host?.id, connected, reloadTick) {
        val uri = runCatching { Uri.parse(configuredUrl) }.getOrNull()
        val loopback = uri?.host == "127.0.0.1" || uri?.host == "localhost"
        error = null
        if (!loopback) {
            loadedUrl = configuredUrl
            return@LaunchedEffect
        }
        if (!connected || host == null) {
            loadedUrl = null
            return@LaunchedEffect
        }
        resolving = true
        val remotePort = uri?.port?.takeIf { it in 1..65535 } ?: 80
        val localPort = withContext(Dispatchers.IO) {
            viewModel.container.ssh.webForwardPort(host.id, remotePort)
        }
        resolving = false
        loadedUrl = if (localPort == null) {
            error = "The SSH forward to port $remotePort could not be opened."
            null
        } else {
            "http://127.0.0.1:$localPort"
        }
    }

    BackHandler {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else onBack()
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = Space.md, end = Space.sm, top = Space.sm, bottom = Space.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(onClick = onBack, size = 38.dp) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.size(Space.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Harness",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 1
                    )
                    Text(
                        host?.name ?: "no host",
                        style = PvpsType.monoBody,
                        color = TextFaint,
                        maxLines = 1
                    )
                }
                TinyAction("URL") { showUrlDialog = true }
                Spacer(Modifier.size(Space.sm))
                GlassIconButton(
                    onClick = { reloadTick++; webView?.reload() },
                    size = 38.dp
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Reload",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.size(Space.sm))
                GlassIconButton(onClick = {
                    val intent = android.content.Intent(
                        android.content.Intent.ACTION_VIEW,
                        Uri.parse(loadedUrl ?: configuredUrl)
                    )
                    runCatching { context.startActivity(intent) }
                }, size = 38.dp) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = "Open in browser",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val url = loadedUrl
            when {
                !connected -> HarnessMessage(
                    title = "Host offline",
                    body = "Connect to ${host?.name ?: "a host"} to open its web UI through the SSH tunnel.",
                    actionLabel = "Connect",
                    onAction = { host?.let { viewModel.connect(it.id) } }
                )
                resolving -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                }
                url == null || error != null -> HarnessMessage(
                    title = "Could not load the page",
                    body = error ?: "The tunnel is not ready yet.",
                    actionLabel = "Retry",
                    onAction = { reloadTick++ }
                )
                else -> {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                setBackgroundColor(0xFF08090B.toInt())
                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        progress = 100
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: WebResourceRequest?,
                                        err: WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            error = err?.description?.toString() ?: "Network error"
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        progress = newProgress
                                    }
                                }
                                webView = this
                            }
                        },

                    )
                    if (progress in 1..99) {
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .align(Alignment.TopCenter),
                            color = Accent,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(loadedUrl, webView, reloadTick) {
        loadedUrl?.let { webView?.loadUrl(it) }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.destroy()
            webView = null
            host?.let { viewModel.container.ssh.releaseWebForwards(it.id) }
        }
    }

    if (showUrlDialog) {
        var draft by remember { mutableStateOf(configuredUrl) }
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(Radius.xl),
            title = {
                Text(
                    "Web UI address",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
            },
            text = {
                Column {
                    Text(
                        "A loopback address (127.0.0.1:port) is reached through the SSH tunnel. Any other address is loaded directly.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(Space.md))
                    PvpsTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        label = "URL",
                        placeholder = "http://127.0.0.1:3080"
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    configuredUrl = draft.trim()
                    viewModel.updateSettings { it.copy(harnessUrl = configuredUrl) }
                    showUrlDialog = false
                }) { Text("Save", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun HarnessMessage(
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Space.gutter),
        verticalArrangement = Arrangement.Center
    ) {
        PvpsCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.lg)) {
            Column(modifier = Modifier.padding(Space.xl)) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Danger.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Link,
                        contentDescription = null,
                        tint = Danger,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(Modifier.height(Space.lg))
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Spacer(Modifier.height(Space.sm))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                Spacer(Modifier.height(Space.xl))
                SignalButton(actionLabel, onAction, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
