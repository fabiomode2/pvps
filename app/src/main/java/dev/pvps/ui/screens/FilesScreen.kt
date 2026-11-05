package dev.pvps.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import dev.pvps.data.model.ConnectionState
import dev.pvps.data.model.Host
import dev.pvps.ssh.RemoteFile
import dev.pvps.ssh.SftpClient
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.EmptyState
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.ScreenHeader
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.components.formatDate
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private enum class FileAction { DOWNLOAD, RENAME, DELETE, TAR, EXTRACT }

@Composable
fun FilesScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val host = viewModel.selectedHost.collectAsStateValue()
    val states = viewModel.connectionStates.collectAsStateValue()
    val connected = host != null && states[host.id]?.status == ConnectionState.Status.CONNECTED
    var currentPath by remember(host?.id) { mutableStateOf("/home/${host?.sshUser ?: "ubuntu"}") }
    var entries by remember { mutableStateOf<List<RemoteFile>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<RemoteFile?>(null) }
    var previewText by remember { mutableStateOf<String?>(null) }
    var previewImage by remember { mutableStateOf<File?>(null) }
    var previewTar by remember { mutableStateOf<List<String>?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var tarTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var extractTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var pendingDownload by remember { mutableStateOf<RemoteFile?>(null) }
    val scope = rememberCoroutineScope()

    val connection = host?.let { viewModel.container.ssh.connection(it.id) }
    val sftp = remember(connection) { connection?.let { SftpClient(it) } }

    fun load() {
        val client = sftp ?: return
        scope.launch {
            loading = true
            error = null
            runCatching { client.list(currentPath) }
                .onSuccess { entries = it }
                .onFailure { error = it.message }
            loading = false
        }
    }

    LaunchedEffect(host?.id, connected, currentPath, refresh, connection) {
        if (connected && sftp != null) load()
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && sftp != null) {
            scope.launch {
                loading = true
                runCatching {
                    val name = queryName(context, uri) ?: "upload-${System.currentTimeMillis()}"
                    val temp = File(context.cacheDir, name)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            temp.outputStream().use { output -> input.copyTo(output) }
                        } ?: error("Could not open the selected file")
                    }
                    withContext(Dispatchers.IO) { sftp.upload(temp, join(currentPath, name)) }
                    temp.delete()
                }.onFailure { error = it.message }
                loading = false
                refresh++
            }
        }
    }

    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val target = pendingDownload
        pendingDownload = null
        if (uri != null && target != null && sftp != null) {
            scope.launch {
                loading = true
                runCatching {
                    val temp = File(context.cacheDir, target.name)
                    withContext(Dispatchers.IO) {
                        sftp.download(target.path, temp)
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            temp.inputStream().use { input -> input.copyTo(output) }
                        } ?: error("Could not write the selected destination")
                    }
                    temp.delete()
                }.onFailure { error = it.message }
                loading = false
            }
        }
    }

    fun openPreview(file: RemoteFile) {
        preview = file
        previewText = null
        previewImage = null
        previewTar = null
        previewLoading = true
        scope.launch {
            runCatching {
                val client = sftp ?: error("SSH not connected")
                when {
                    file.name.endsWith(".tar.gz") || file.name.endsWith(".tgz") -> {
                        previewTar = client.listTar(file.path)
                    }
                    file.name.substringAfterLast('.', "").lowercase(Locale.US) in setOf(
                        "png", "jpg", "jpeg", "webp", "gif", "bmp"
                    ) -> {
                        val temp = File(context.cacheDir, "preview-${file.name}")
                        withContext(Dispatchers.IO) { client.download(file.path, temp) }
                        previewImage = temp
                    }
                    else -> {
                        previewText = withContext(Dispatchers.IO) { client.readText(file.path) }
                    }
                }
            }.onFailure { error = it.message }
            previewLoading = false
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            ScreenHeader(
                title = "Files",
                subtitle = currentPath,
                onBack = onBack,
                actions = {
                    IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }, enabled = connected) {
                        Icon(Icons.Default.CloudUpload, contentDescription = "Upload", tint = Accent)
                    }
                    IconButton(onClick = { showNewFolder = true }, enabled = connected) {
                        Icon(Icons.Default.Add, contentDescription = "New folder", tint = Accent)
                    }
                    IconButton(onClick = { refresh++ }, enabled = connected && !loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Accent)
                    }
                }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            EmptyState(
                title = "SSH not connected",
                message = "Connect to the host from the dashboard first.",
                actionLabel = "Connect",
                onAction = { host?.let { viewModel.connect(it.id) } }
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Breadcrumbs(currentPath) { path -> currentPath = path }
            if (error != null) ErrorBanner(error!!) { error = null }
            if (loading) {
                CircularProgressIndicator(color = Accent, modifier = Modifier.padding(16.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                if (currentPath != "/") {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = parentOf(currentPath) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = null, tint = TextSecondary)
                            Spacer(Modifier.width(10.dp))
                            Text("..", color = TextSecondary, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
                items(entries, key = { it.path }) { file ->
                    FileRow(
                        file = file,
                        onOpen = {
                            if (file.isDirectory) {
                                currentPath = file.path
                            } else {
                                openPreview(file)
                            }
                        },
                        onAction = { action ->
                            when (action) {
                                FileAction.DOWNLOAD -> {
                                    pendingDownload = file
                                    downloadLauncher.launch(file.name)
                                }
                                FileAction.RENAME -> renameTarget = file
                                FileAction.DELETE -> deleteTarget = file
                                FileAction.TAR -> tarTarget = file
                                FileAction.EXTRACT -> extractTarget = file
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    if (showNewFolder) {
        NameDialog(
            title = "New folder",
            label = "Folder name",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { showNewFolder = false },
            onConfirm = { name ->
                val client = sftp ?: return@NameDialog
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.mkdir(join(currentPath, name)) } }
                        .onFailure { error = it.message }
                    showNewFolder = false
                    refresh++
                }
            }
        )
    }
    renameTarget?.let { file ->
        NameDialog(
            title = "Rename",
            label = "New name",
            initial = file.name,
            confirmLabel = "Rename",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                val client = sftp ?: return@NameDialog
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.rename(file.path, join(currentPath, name)) } }
                        .onFailure { error = it.message }
                    renameTarget = null
                    refresh++
                }
            }
        )
    }
    deleteTarget?.let { file ->
        ConfirmDialog(
            title = "Delete ${file.name}?",
            message = if (file.isDirectory) "Only empty directories can be removed over SFTP." else "This cannot be undone.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                val client = sftp ?: return@ConfirmDialog
                scope.launch {
                    runCatching { withContext(Dispatchers.IO) { client.delete(file.path, file.isDirectory) } }
                        .onFailure { error = it.message }
                    deleteTarget = null
                    refresh++
                }
            },
            onDismiss = { deleteTarget = null }
        )
    }
    tarTarget?.let { file ->
        NameDialog(
            title = "Create tar.gz",
            label = "Archive name",
            initial = "${file.name}-${System.currentTimeMillis()}.tar.gz",
            confirmLabel = "Create",
            onDismiss = { tarTarget = null },
            onConfirm = { archiveName ->
                val client = sftp ?: return@NameDialog
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            client.createTar(currentPath, file.name, join(currentPath, archiveName))
                        }
                    }.onFailure { error = it.message }
                    tarTarget = null
                    refresh++
                }
            }
        )
    }
    extractTarget?.let { file ->
        NameDialog(
            title = "Extract ${file.name}",
            label = "Destination directory",
            initial = join(currentPath, file.name.substringBefore(".tar")),
            confirmLabel = "Extract",
            onDismiss = { extractTarget = null },
            onConfirm = { destination ->
                val client = sftp ?: return@NameDialog
                scope.launch {
                    runCatching {
                        withContext(Dispatchers.IO) { client.extractTar(file.path, destination) }
                    }.onFailure { error = it.message }
                    extractTarget = null
                    refresh++
                }
            }
        )
    }
    preview?.let { file ->
        AlertDialog(
            onDismissRequest = { preview = null; previewImage?.delete() },
            title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                ) {
                    if (previewLoading) {
                        CircularProgressIndicator(color = Accent)
                    } else if (previewImage != null) {
                        AsyncImage(
                            model = previewImage,
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    } else if (previewTar != null) {
                        Text("Archive contents (${previewTar!!.size})", color = TextSecondary)
                        Spacer(Modifier.height(6.dp))
                        MonospaceBox(previewTar!!.joinToString("\n"))
                    } else {
                        MonospaceBox(previewText.orEmpty())
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (file.name.endsWith(".tar.gz") || file.name.endsWith(".tgz")) {
                        TextButton(onClick = { extractTarget = file; preview = null }) { Text("Extract") }
                    }
                    TextButton(onClick = { preview = null; previewImage?.delete() }) { Text("Close") }
                }
            }
        )
    }
}

@Composable
private fun FileRow(
    file: RemoteFile,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (file.isDirectory) Warning else TextSecondary,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                file.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    if (!file.isDirectory) append(formatBytes(file.size)).append(" · ")
                    append(formatDate(file.modified))
                    if (file.permissions.isNotBlank()) append(" · ").append(file.permissions)
                },
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary,
                maxLines = 1
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = TextSecondary)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                        onClick = { menu = false; onAction(FileAction.DOWNLOAD) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { menu = false; onAction(FileAction.RENAME) }
                )
                if (file.isDirectory) {
                    DropdownMenuItem(
                        text = { Text("Create tar.gz") },
                        leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null) },
                        onClick = { menu = false; onAction(FileAction.TAR) }
                    )
                }
                if (file.name.endsWith(".tar.gz") || file.name.endsWith(".tgz")) {
                    DropdownMenuItem(
                        text = { Text("Extract") },
                        leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                        onClick = { menu = false; onAction(FileAction.EXTRACT) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete", color = Danger) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Danger) },
                    onClick = { menu = false; onAction(FileAction.DELETE) }
                )
            }
        }
    }
}

@Composable
private fun Breadcrumbs(path: String, onNavigate: (String) -> Unit) {
    val parts = path.split('/').filter { it.isNotBlank() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = { onNavigate("/") }) { Text("/", fontFamily = FontFamily.Monospace) }
        var accumulated = ""
        parts.forEach { part ->
            accumulated += "/$part"
            val target = accumulated
            TextButton(onClick = { onNavigate(target) }) {
                Text(part, fontFamily = FontFamily.Monospace, maxLines = 1)
            }
        }
    }
}

@Composable
private fun NameDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (value.isNotBlank() && !value.contains('/') && value != "." && value != "..") {
                        onConfirm(value.trim())
                    }
                }
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun join(base: String, name: String): String =
    if (base == "/") "/$name" else "${base.trimEnd('/')}/$name"

private fun parentOf(path: String): String {
    val trimmed = path.trimEnd('/')
    if (trimmed.isEmpty() || trimmed == "/") return "/"
    val parent = trimmed.substringBeforeLast('/', "")
    return parent.ifBlank { "/" }
}

private fun queryName(context: Context, uri: Uri): String? {
    return runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        }
    }.getOrNull()
}
