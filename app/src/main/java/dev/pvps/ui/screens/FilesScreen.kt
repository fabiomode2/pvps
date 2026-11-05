package dev.pvps.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import dev.pvps.data.model.ConnectionState
import dev.pvps.ssh.RemoteFile
import dev.pvps.ssh.SftpClient
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.MonospaceBox
import dev.pvps.ui.components.PvpsTextField
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OutlineDark
import dev.pvps.ui.theme.OutlineSoft
import dev.pvps.ui.theme.SurfaceDark
import dev.pvps.ui.theme.SurfaceHigh
import dev.pvps.ui.theme.Success
import dev.pvps.ui.theme.SurfaceRaised
import dev.pvps.ui.theme.TextFaint
import dev.pvps.ui.theme.TextPrimary
import dev.pvps.ui.theme.TextSecondary
import dev.pvps.ui.theme.Violet
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
    val settings = viewModel.settings.collectAsStateValue()
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

    val visibleEntries = remember(entries, settings.showHiddenFiles) {
        if (settings.showHiddenFiles) entries else entries.filterNot { it.name.startsWith(".") }
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
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FilesTopBar(
                currentPath = currentPath,
                connected = connected,
                busy = loading,
                onBack = onBack,
                onUpload = { uploadLauncher.launch(arrayOf("*/*")) },
                onNewFolder = { showNewFolder = true },
                onRefresh = { refresh++ }
            )
        }
    ) { padding ->
        if (host == null || !connected) {
            DisconnectedState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onConnect = { host?.let { viewModel.connect(it.id) } }
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PathStrip(currentPath) { path -> currentPath = path }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${visibleEntries.size} items",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextFaint,
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                )
                IconButton(onClick = {
                    viewModel.updateSettings { it.copy(fileGridView = !it.fileGridView) }
                }) {
                    Icon(
                        if (settings.fileGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle grid view",
                        tint = TextSecondary
                    )
                }
                IconButton(onClick = {
                    viewModel.updateSettings { it.copy(showHiddenFiles = !it.showHiddenFiles) }
                }) {
                    Icon(
                        if (settings.showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle hidden files",
                        tint = if (settings.showHiddenFiles) Accent else TextSecondary
                    )
                }
            }
            if (loading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Accent,
                    trackColor = OutlineSoft
                )
            }
            if (error != null) ErrorBanner(error!!) { error = null }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val onOpen: (RemoteFile) -> Unit = { file ->
                    if (file.isDirectory) currentPath = file.path else openPreview(file)
                }
                val onAction: (RemoteFile, FileAction) -> Unit = { file, action ->
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
                if (settings.fileGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(visibleEntries, key = { it.path }) { file ->
                            FileTile(
                                file = file,
                                onOpen = { onOpen(file) },
                                onAction = { action -> onAction(file, action) }
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 88.dp)
                    ) {
                        item(key = "file-list-header") { FileListHeader() }
                        items(visibleEntries, key = { it.path }) { file ->
                            FileRow(
                                file = file,
                                onOpen = { onOpen(file) },
                                onAction = { action -> onAction(file, action) }
                            )
                        }
                    }
                }
                if (visibleEntries.isEmpty() && !loading && error == null) {
                    DirectoryEmpty(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 28.dp)
                    )
                }
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
            containerColor = SurfaceHigh,
            shape = RoundedCornerShape(20.dp),
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
            title = {
                Text(
                    file.name,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
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
                        TextButton(onClick = { extractTarget = file; preview = null }) {
                            Text("Extract", color = Accent)
                        }
                    }
                    TextButton(onClick = { preview = null; previewImage?.delete() }) {
                        Text("Close", color = Accent)
                    }
                }
            }
        )
    }
}

@Composable
private fun FilesTopBar(
    currentPath: String,
    connected: Boolean,
    busy: Boolean,
    onBack: () -> Unit,
    onUpload: () -> Unit,
    onNewFolder: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(start = 6.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = TextPrimary,
                onClick = onBack
            )
            Spacer(Modifier.width(8.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    "FILES",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp
                    ),
                    color = Accent,
                    maxLines = 1
                )
                Text(
                    currentPath.ifBlank { "/" },
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.5.sp,
                        lineHeight = 16.sp,
                        letterSpacing = 0.1.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    fontFamily = MonoFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CompactIconButton(
                    icon = Icons.Default.CloudUpload,
                    contentDescription = "Upload",
                    enabled = connected,
                    onClick = onUpload
                )
                CompactIconButton(
                    icon = Icons.Default.CreateNewFolder,
                    contentDescription = "New folder",
                    enabled = connected,
                    onClick = onNewFolder
                )
                CompactIconButton(
                    icon = Icons.Default.Refresh,
                    contentDescription = "Refresh",
                    enabled = connected && !busy,
                    onClick = onRefresh
                )
            }
        }
        HorizontalDivider(
            thickness = 0.5.dp,
            color = OutlineDark.copy(alpha = 0.9f)
        )
    }
}

@Composable
private fun CompactIconButton(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean = true,
    tint: Color = Accent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) SurfaceRaised.copy(alpha = 0.55f) else Color.Transparent)
            .border(0.5.dp, OutlineDark, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) tint else TextFaint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun PathStrip(path: String, onNavigate: (String) -> Unit) {
    val scroll = rememberScrollState()
    val parts = path.split('/').filter { it.isNotBlank() }
    Surface(
        color = SurfaceDark.copy(alpha = 0.72f),
        contentColor = TextPrimary
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (path != "/") {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigate(parentOf(path)) }
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Parent folder",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "..",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.5.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = TextSecondary,
                            fontFamily = MonoFont
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 6.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(OutlineDark)
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(scroll),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CrumbSegment(
                        label = "/",
                        selected = path == "/",
                        onClick = { onNavigate("/") }
                    )
                    var accumulated = ""
                    parts.forEach { part ->
                        accumulated += "/$part"
                        val target = accumulated
                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextFaint,
                            modifier = Modifier.size(13.dp)
                        )
                        CrumbSegment(
                            label = part,
                            selected = target.trimEnd('/') == path.trimEnd('/'),
                            onClick = { onNavigate(target) }
                        )
                    }
                }
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = OutlineDark.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun CrumbSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(7.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.5.sp,
                letterSpacing = 0.2.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = if (selected) Accent else TextSecondary,
            fontFamily = MonoFont,
            maxLines = 1
        )
    }
}

@Composable
private fun FileTile(
    file: RemoteFile,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Surface(
        color = SurfaceRaised.copy(alpha = 0.44f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (file.isDirectory) Warning.copy(alpha = 0.16f) else OutlineDark.copy(alpha = 0.62f)),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onOpen)
    ) {
        Column(Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileGlyph(file)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(30.dp).clip(CircleShape).clickable { menu = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Actions for ${file.name}",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                    FileActionMenu(file = file, expanded = menu, onDismiss = { menu = false }, onAction = onAction)
                }
            }
            Spacer(Modifier.height(9.dp))
            Text(
                file.name,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                fileSizeLabel(file),
                style = MaterialTheme.typography.labelSmall,
                color = TextFaint,
                fontFamily = MonoFont,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun FileActionMenu(
    file: RemoteFile,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!file.isDirectory) {
            DropdownMenuItem(
                text = { Text("Download", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = TextSecondary) },
                onClick = { onDismiss(); onAction(FileAction.DOWNLOAD) }
            )
        }
        DropdownMenuItem(
            text = { Text("Rename", color = TextPrimary) },
            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = TextSecondary) },
            onClick = { onDismiss(); onAction(FileAction.RENAME) }
        )
        if (file.isDirectory) {
            DropdownMenuItem(
                text = { Text("Create tar.gz", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Archive, contentDescription = null, tint = TextSecondary) },
                onClick = { onDismiss(); onAction(FileAction.TAR) }
            )
        }
        if (file.name.endsWith(".tar.gz") || file.name.endsWith(".tgz")) {
            DropdownMenuItem(
                text = { Text("Extract", color = TextPrimary) },
                leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null, tint = TextSecondary) },
                onClick = { onDismiss(); onAction(FileAction.EXTRACT) }
            )
        }
        DropdownMenuItem(
            text = { Text("Delete", color = Danger) },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Danger) },
            onClick = { onDismiss(); onAction(FileAction.DELETE) }
        )
    }
}

@Composable
private fun FileListHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 50.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "NAME",
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            modifier = Modifier.weight(1f)
        )
        Text("SIZE", style = MaterialTheme.typography.labelSmall, color = TextFaint)
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
            .heightIn(min = 54.dp)
            .clickable(onClick = onOpen)
            .padding(start = 14.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileGlyph(file, size = 30.dp)
        Spacer(Modifier.width(11.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = fileSizeLabel(file),
            style = MaterialTheme.typography.labelSmall,
            color = TextFaint,
            fontFamily = MonoFont,
            maxLines = 1
        )
        if (file.isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(14.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable { menu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Actions for ${file.name}",
                tint = TextSecondary,
                modifier = Modifier.size(18.dp)
            )
            FileActionMenu(file = file, expanded = menu, onDismiss = { menu = false }, onAction = onAction)
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 55.dp, end = 14.dp),
        thickness = 0.5.dp,
        color = OutlineDark.copy(alpha = 0.45f)
    )
}

@Composable
private fun FileGlyph(file: RemoteFile, size: androidx.compose.ui.unit.Dp = 34.dp) {
    val isDirectory = file.isDirectory
    val tint = when {
        isDirectory -> Warning
        isImageFile(file.name) -> Success
        isArchiveFile(file.name) -> Violet
        isCodeFile(file.name) -> Accent
        else -> TextSecondary
    }
    val container = if (isDirectory) Warning.copy(alpha = 0.10f) else tint.copy(alpha = 0.08f)
    val border = if (isDirectory) Warning.copy(alpha = 0.20f) else tint.copy(alpha = 0.18f)
    val icon = when {
        isDirectory -> Icons.Outlined.Folder
        isImageFile(file.name) -> Icons.Outlined.Image
        isArchiveFile(file.name) -> Icons.Outlined.FolderZip
        isCodeFile(file.name) -> Icons.Outlined.Code
        isVideoFile(file.name) -> Icons.Outlined.VideoFile
        isAudioFile(file.name) -> Icons.Outlined.AudioFile
        file.name.endsWith(".pdf", true) -> Icons.Outlined.PictureAsPdf
        else -> Icons.Outlined.Description
    }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 32.dp) 10.dp else 9.dp))
            .background(container)
            .border(0.5.dp, border, RoundedCornerShape(if (size > 32.dp) 10.dp else 9.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

private fun isImageFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "svg", "heic")

private fun isArchiveFile(name: String): Boolean =
    name.endsWith(".tar.gz", true) || name.endsWith(".tgz", true) ||
        name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("zip", "gz", "bz2", "xz", "7z", "rar", "tar")

private fun isCodeFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in setOf(
        "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "go", "rs", "c", "h",
        "cpp", "hpp", "cs", "sh", "bash", "zsh", "fish", "json", "yaml", "yml",
        "toml", "xml", "html", "css", "scss", "sql", "md", "gradle", "properties"
    )

private fun isVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("mp4", "mkv", "mov", "webm", "avi", "m4v")

private fun isAudioFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus")

@Composable
private fun DirectoryEmpty(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceRaised.copy(alpha = 0.55f))
                .border(0.5.dp, OutlineDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(21.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            "Empty directory",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Upload a file or create a folder to get started.",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DisconnectedState(modifier: Modifier = Modifier, onConnect: () -> Unit) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceRaised.copy(alpha = 0.55f))
                .border(0.5.dp, OutlineDark, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Outlined.CloudOff,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "SSH not connected",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Connect to the host from the dashboard to browse its filesystem.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))
        val shape = RoundedCornerShape(10.dp)
        Surface(
            modifier = Modifier
                .clip(shape)
                .heightIn(min = 48.dp)
                .clickable(onClick = onConnect),
            color = Accent.copy(alpha = 0.10f),
            shape = shape,
            border = BorderStroke(1.dp, Accent.copy(alpha = 0.34f))
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "CONNECT",
                    style = MaterialTheme.typography.labelLarge,
                    color = Accent
                )
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
    val trimmed = value.trim()
    val valid = trimmed.isNotEmpty() &&
        !trimmed.contains('/') &&
        trimmed != "." &&
        trimmed != ".."
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceHigh,
        shape = RoundedCornerShape(20.dp),
        titleContentColor = TextPrimary,
        textContentColor = TextSecondary,
        title = { Text(title, color = TextPrimary) },
        text = {
            PvpsTextField(
                value = value,
                onValueChange = { value = it },
                label = label
            )
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { if (valid) onConfirm(value.trim()) }
            ) {
                Text(
                    confirmLabel,
                    color = if (valid) Accent else TextFaint
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}

private fun fileSizeLabel(file: RemoteFile): String =
    if (file.isDirectory) "Folder" else formatBytes(file.size)

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
