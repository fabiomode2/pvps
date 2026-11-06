package dev.pvps.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import dev.pvps.data.model.ConnectionState
import dev.pvps.security.SecuritySupport
import dev.pvps.ssh.RemoteFile
import dev.pvps.ssh.SftpClient
import dev.pvps.ui.AppViewModel
import dev.pvps.ui.components.ConfirmDialog
import dev.pvps.ui.components.ErrorBanner
import dev.pvps.ui.components.PvpsTextField
import dev.pvps.ui.components.collectAsStateValue
import dev.pvps.ui.components.formatBytes
import dev.pvps.ui.theme.Accent
import dev.pvps.ui.theme.Danger
import dev.pvps.ui.theme.MonoFont
import dev.pvps.ui.theme.OnAccent
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

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

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { cleanupStalePreviewFiles(context.cacheDir) }
    }

    val visibleEntries = remember(entries, settings.showHiddenFiles) {
        if (settings.showHiddenFiles) entries else entries.filterNot { it.name.startsWith(".") }
    }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && sftp != null) {
            scope.launch {
                loading = true
                runCatching {
                    SecuritySupport.requirePickerReadUri(context, uri)
                    val name = SecuritySupport.safeLeafName(
                        queryName(context, uri) ?: "upload-${System.currentTimeMillis()}"
                    )
                    val temp = SecuritySupport.containedFile(context.cacheDir, name)
                    try {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(uri)?.use { input ->
                                temp.outputStream().use { output ->
                                    SecuritySupport.copyLimited(
                                        input,
                                        output,
                                        SecuritySupport.MAX_UPLOAD_BYTES
                                    )
                                }
                            } ?: error("Could not open the selected file")
                        }
                        withContext(Dispatchers.IO) {
                            sftp.upload(temp, SecuritySupport.safeRemoteChild(currentPath, name))
                        }
                    } finally {
                        runCatching { temp.delete() }
                    }
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
                    SecuritySupport.requirePickerWriteUri(context, uri)
                    val temp = SecuritySupport.containedFile(context.cacheDir, target.name)
                    try {
                        withContext(Dispatchers.IO) {
                            sftp.download(
                                target.path,
                                temp,
                                maxBytes = SecuritySupport.MAX_DOWNLOAD_BYTES
                            )
                            context.contentResolver.openOutputStream(uri)?.use { output ->
                                temp.inputStream().use { input ->
                                    SecuritySupport.copyLimited(
                                        input,
                                        output,
                                        SecuritySupport.MAX_DOWNLOAD_BYTES
                                    )
                                }
                            } ?: error("Could not write the selected destination")
                        }
                    } finally {
                        runCatching { temp.delete() }
                    }
                }.onFailure { error = it.message }
                loading = false
            }
        }
    }

    fun openPreview(file: RemoteFile) {
        preview = file
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${visibleEntries.size} items",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = TextFaint,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp)
                )
                IconButton(
                    onClick = {
                        viewModel.updateSettings { it.copy(fileGridView = !it.fileGridView) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (settings.fileGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle grid view",
                        tint = TextSecondary,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(
                    onClick = {
                        viewModel.updateSettings { it.copy(showHiddenFiles = !it.showHiddenFiles) }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (settings.showHiddenFiles) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle hidden files",
                        tint = if (settings.showHiddenFiles) Accent else TextSecondary,
                        modifier = Modifier.size(17.dp)
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
                        columns = GridCells.Adaptive(100.dp),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 88.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
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
        FilePreviewDialog(
            file = file,
            sftp = sftp,
            cacheDir = context.cacheDir,
            onDismiss = { preview = null },
            onDownload = {
                preview = null
                pendingDownload = file
                downloadLauncher.launch(file.name)
            },
            onExtract = if (isTarArchive(file.name)) {
                {
                    preview = null
                    extractTarget = file
                }
            } else {
                null
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
                .height(52.dp)
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
    var showPathMenu by remember(path) { mutableStateOf(false) }
    val ancestors = remember(path) {
        buildList {
            add("/")
            var accumulated = ""
            path.split('/').filter { it.isNotBlank() }.forEach { part ->
                accumulated += "/$part"
                add(accumulated)
            }
        }
    }
    Surface(
        color = SurfaceDark.copy(alpha = 0.72f),
        contentColor = TextPrimary
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (path != "/") {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigate(parentOf(path)) }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward,
                            contentDescription = "Parent folder",
                            tint = TextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "..",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontSize = 11.sp,
                                letterSpacing = 0.2.sp
                            ),
                            color = TextSecondary,
                            fontFamily = MonoFont
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .width(1.dp)
                            .height(16.dp)
                            .background(OutlineDark)
                    )
                }
                Icon(
                    Icons.Outlined.Folder,
                    contentDescription = null,
                    tint = Accent.copy(alpha = 0.85f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = path.ifBlank { "/" },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showPathMenu = true }
                            .padding(vertical = 6.dp, horizontal = 2.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 11.5.sp,
                            letterSpacing = 0.1.sp
                        ),
                        color = TextSecondary,
                        fontFamily = MonoFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    DropdownMenu(
                        expanded = showPathMenu,
                        onDismissRequest = { showPathMenu = false }
                    ) {
                        ancestors.forEach { target ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = target,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = if (target == path) Accent else TextPrimary,
                                        fontFamily = MonoFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                onClick = {
                                    showPathMenu = false
                                    onNavigate(target)
                                }
                            )
                        }
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
private fun FileTile(
    file: RemoteFile,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = SurfaceRaised.copy(alpha = 0.44f),
            shape = shape,
            border = BorderStroke(
                0.5.dp,
                if (file.isDirectory) Warning.copy(alpha = 0.16f) else OutlineDark.copy(alpha = 0.62f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable(onClick = onOpen)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 82.dp)
                    .padding(start = 5.dp, end = 5.dp, top = 10.dp, bottom = 7.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                FileGlyph(file, size = 28.dp)
                Spacer(Modifier.height(6.dp))
                Text(
                    file.name,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!file.isDirectory) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        formatBytes(file.size),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                        color = TextFaint,
                        fontFamily = MonoFont,
                        maxLines = 1
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
                .size(24.dp)
                .clip(CircleShape)
                .clickable { menu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Actions for ${file.name}",
                tint = TextSecondary,
                modifier = Modifier.size(14.dp)
            )
        }
        FileActionMenu(file = file, expanded = menu, onDismiss = { menu = false }, onAction = onAction)
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
        if (isTarArchive(file.name)) {
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
private fun FileRow(
    file: RemoteFile,
    onOpen: () -> Unit,
    onAction: (FileAction) -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FileGlyph(file, size = 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.titleSmall.copy(
                fontSize = 13.5.sp,
                lineHeight = 18.sp,
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
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
            color = TextFaint,
            fontFamily = MonoFont,
            maxLines = 1
        )
        if (file.isDirectory) {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(13.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { menu = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "Actions for ${file.name}",
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
            FileActionMenu(file = file, expanded = menu, onDismiss = { menu = false }, onAction = onAction)
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(start = 50.dp, end = 12.dp),
        thickness = 0.5.dp,
        color = OutlineDark.copy(alpha = 0.35f)
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

private fun isTarArchive(name: String): Boolean =
    name.endsWith(".tar.gz", true) || name.endsWith(".tgz", true)

private fun isArchiveFile(name: String): Boolean =
    isTarArchive(name) ||
        name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("zip", "gz", "bz2", "xz", "7z", "rar", "tar")

private fun isCodeFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in setOf(
        "kt", "kts", "java", "js", "ts", "tsx", "jsx", "py", "go", "rs", "c", "h",
        "cpp", "hpp", "cs", "sh", "bash", "zsh", "fish", "json", "yaml", "yml",
        "toml", "xml", "html", "css", "scss", "sql", "md", "gradle", "properties"
    )

private fun isTextFile(name: String): Boolean =
    isCodeFile(name) ||
        name.substringAfterLast('.', "").lowercase(Locale.US) in setOf(
            "txt", "log", "csv", "tsv", "ini", "conf", "cfg", "env", "service",
            "rst", "tex", "diff", "patch"
        ) ||
        name.lowercase(Locale.US) in setOf(
            "dockerfile", "makefile", "readme", "license", "procfile",
            ".gitignore", ".gitattributes", ".env"
        )

private fun isVideoFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("mp4", "mkv", "mov", "webm", "avi", "m4v")

private fun isAudioFile(name: String): Boolean =
    name.substringAfterLast('.', "").lowercase(Locale.US) in
        setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus")

private const val MAX_TEXT_PREVIEW_BYTES = 256 * 1024
private const val MAX_MEDIA_PREVIEW_BYTES = 64L * 1024 * 1024
private const val PREVIEW_CACHE_PREFIX = "pvps-preview-"
private const val PREVIEW_CACHE_MAX_AGE_MS = 24L * 60L * 60L * 1000L

private enum class PreviewKind { IMAGE, PDF, VIDEO, AUDIO, TEXT, ARCHIVE, UNSUPPORTED }

private sealed interface PreviewContent {
    data object Loading : PreviewContent
    data class Error(val message: String) : PreviewContent
    data class Image(val file: File) : PreviewContent
    data class Pdf(val file: File, val pageCount: Int) : PreviewContent
    data class Video(val file: File) : PreviewContent
    data class Audio(val file: File) : PreviewContent
    data class Text(val body: String, val truncated: Boolean) : PreviewContent
    data class Archive(val entries: List<String>) : PreviewContent
    data class Unsupported(val message: String) : PreviewContent
}

private sealed interface PdfPageRender {
    data object Loading : PdfPageRender
    data class Ready(val bitmap: Bitmap) : PdfPageRender
    data class Failed(val message: String) : PdfPageRender
}

private fun previewKind(file: RemoteFile): PreviewKind = when {
    file.isDirectory -> PreviewKind.UNSUPPORTED
    isImageFile(file.name) -> PreviewKind.IMAGE
    file.name.endsWith(".pdf", true) -> PreviewKind.PDF
    isVideoFile(file.name) -> PreviewKind.VIDEO
    isAudioFile(file.name) -> PreviewKind.AUDIO
    isTarArchive(file.name) -> PreviewKind.ARCHIVE
    isTextFile(file.name) -> PreviewKind.TEXT
    else -> PreviewKind.UNSUPPORTED
}

private fun previewKindLabel(kind: PreviewKind): String = when (kind) {
    PreviewKind.IMAGE -> "IMAGE"
    PreviewKind.PDF -> "PDF"
    PreviewKind.VIDEO -> "VIDEO"
    PreviewKind.AUDIO -> "AUDIO"
    PreviewKind.TEXT -> "TEXT"
    PreviewKind.ARCHIVE -> "ARCHIVE"
    PreviewKind.UNSUPPORTED -> "FILE"
}

private fun previewUnsupportedMessage(file: RemoteFile): String =
    if (isArchiveFile(file.name)) {
        "Inline preview is not available for this archive format. Download it to inspect it on this device."
    } else {
        "This file type has no inline preview. Download it to open it with another app."
    }

private fun previewCacheKey(file: RemoteFile): String {
    val raw = "${file.path}|${file.size}|${file.modified}"
    return Integer.toHexString(raw.hashCode()) + "-" + file.size + "-" + file.modified
}

private fun previewTempFile(cacheDir: File, file: RemoteFile): File {
    val extension = file.name.substringAfterLast('.', "").lowercase(Locale.US)
        .takeIf { it.length in 1..6 && it.all { ch -> ch.isLetterOrDigit() } }
        ?: "bin"
    // The cache key is derived from hashed/metadata fields and the extension
    // is restricted to alphanumerics, so no remote path component reaches the
    // local file name.
    val prefix = "$PREVIEW_CACHE_PREFIX${previewCacheKey(file)}-"
    val generated = runCatching { File.createTempFile(prefix, ".$extension", cacheDir) }
        .getOrElse { File(cacheDir, "$prefix${System.nanoTime()}.$extension") }
    return SecuritySupport.containedFile(cacheDir, generated.name)
}

private fun cleanupStalePreviewFiles(cacheDir: File) {
    val cutoff = System.currentTimeMillis() - PREVIEW_CACHE_MAX_AGE_MS
    cacheDir.listFiles()?.forEach { file ->
        if (file.isFile && file.name.startsWith(PREVIEW_CACHE_PREFIX) && file.lastModified() < cutoff) {
            runCatching { file.delete() }
        }
    }
}

private suspend fun loadPdfPageCount(file: File): Int = withContext(Dispatchers.IO) {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
    }
}

@Composable
private fun FilePreviewDialog(
    file: RemoteFile,
    sftp: SftpClient?,
    cacheDir: File,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onExtract: (() -> Unit)?
) {
    val kind = remember(file.path, file.size, file.modified) { previewKind(file) }
    val cacheKey = remember(file.path, file.size, file.modified) { previewCacheKey(file) }
    var attempt by remember(file.path, file.size, file.modified) { mutableIntStateOf(0) }
    var content by remember(file.path, file.size, file.modified) {
        mutableStateOf<PreviewContent>(PreviewContent.Loading)
    }
    val scope = rememberCoroutineScope()

    DisposableEffect(file.path, file.size, file.modified, attempt) {
        content = PreviewContent.Loading
        val temp = previewTempFile(cacheDir, file)
        val job = scope.launch {
            val client = sftp
            if (client == null) {
                content = PreviewContent.Error("SSH is not connected.")
                return@launch
            }
            content = runCatching {
                when (kind) {
                    PreviewKind.UNSUPPORTED -> PreviewContent.Unsupported(previewUnsupportedMessage(file))
                    PreviewKind.TEXT -> {
                        val body = client.readText(file.path, MAX_TEXT_PREVIEW_BYTES)
                        PreviewContent.Text(body, file.size > MAX_TEXT_PREVIEW_BYTES)
                    }
                    PreviewKind.ARCHIVE -> PreviewContent.Archive(client.listTar(file.path))
                    PreviewKind.IMAGE -> {
                        client.download(file.path, temp, maxBytes = MAX_MEDIA_PREVIEW_BYTES)
                        PreviewContent.Image(temp)
                    }
                    PreviewKind.PDF -> {
                        client.download(file.path, temp, maxBytes = MAX_MEDIA_PREVIEW_BYTES)
                        PreviewContent.Pdf(temp, loadPdfPageCount(temp))
                    }
                    PreviewKind.VIDEO -> {
                        client.download(file.path, temp, maxBytes = MAX_MEDIA_PREVIEW_BYTES)
                        PreviewContent.Video(temp)
                    }
                    PreviewKind.AUDIO -> {
                        client.download(file.path, temp, maxBytes = MAX_MEDIA_PREVIEW_BYTES)
                        PreviewContent.Audio(temp)
                    }
                }
            }.getOrElse { throwable ->
                if (throwable is CancellationException) throw throwable
                // Do not keep a partial capped download around while the error
                // is displayed; onDispose will also attempt this delete.
                runCatching { temp.delete() }
                PreviewContent.Error(throwable.message ?: "Preview failed.")
            }
        }
        onDispose {
            job.cancel()
            runCatching { temp.delete() }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SurfaceDark)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
            ) {
                PreviewTopBar(
                    file = file,
                    kind = kind,
                    onDismiss = onDismiss,
                    onDownload = onDownload,
                    onExtract = onExtract
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = OutlineDark.copy(alpha = 0.9f)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (val state = content) {
                        PreviewContent.Loading -> PreviewLoading()
                        is PreviewContent.Error -> PreviewStateMessage(
                            icon = Icons.Outlined.ErrorOutline,
                            title = "Preview failed",
                            message = state.message,
                            primaryLabel = "RETRY",
                            onPrimary = {
                                content = PreviewContent.Loading
                                attempt++
                            },
                            secondaryLabel = "DOWNLOAD",
                            onSecondary = onDownload
                        )
                        is PreviewContent.Image -> PreviewImage(
                            file = state.file,
                            cacheKey = cacheKey,
                            name = file.name
                        )
                        is PreviewContent.Pdf -> PdfPreview(
                            file = state.file,
                            pageCount = state.pageCount
                        )
                        is PreviewContent.Video -> VideoPreview(state.file)
                        is PreviewContent.Audio -> AudioPreview(state.file)
                        is PreviewContent.Text -> TextPreview(
                            body = state.body,
                            truncated = state.truncated,
                            fileSize = file.size
                        )
                        is PreviewContent.Archive -> ArchivePreview(state.entries)
                        is PreviewContent.Unsupported -> PreviewStateMessage(
                            icon = Icons.Outlined.Description,
                            title = "No preview available",
                            message = state.message,
                            primaryLabel = "DOWNLOAD",
                            onPrimary = onDownload
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewTopBar(
    file: RemoteFile,
    kind: PreviewKind,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onExtract: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .padding(start = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactIconButton(
            icon = Icons.Default.Close,
            contentDescription = "Close preview",
            tint = TextPrimary,
            onClick = onDismiss
        )
        Spacer(Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${previewKindLabel(kind)} · ${fileSizeLabel(file)}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                color = TextFaint,
                fontFamily = MonoFont,
                maxLines = 1
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onExtract != null) {
                CompactIconButton(
                    icon = Icons.Default.Unarchive,
                    contentDescription = "Extract archive",
                    onClick = onExtract
                )
            }
            CompactIconButton(
                icon = Icons.Default.Download,
                contentDescription = "Download file",
                onClick = onDownload
            )
        }
    }
}

@Composable
private fun PreviewLoading() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text(
                "Preparing preview...",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun PreviewStateMessage(
    icon: ImageVector,
    title: String,
    message: String,
    primaryLabel: String? = null,
    onPrimary: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceRaised.copy(alpha = 0.55f))
                .border(0.5.dp, OutlineDark, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextFaint,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
        if (primaryLabel != null && onPrimary != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onPrimary) {
                Text(primaryLabel, color = Accent, style = MaterialTheme.typography.labelLarge)
            }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary) {
                Text(secondaryLabel, color = TextSecondary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PreviewImage(file: File, cacheKey: String, name: String) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(file)
                .memoryCacheKey("preview-$cacheKey")
                .diskCacheKey("preview-$cacheKey")
                .memoryCachePolicy(CachePolicy.DISABLED)
                .diskCachePolicy(CachePolicy.DISABLED)
                .crossfade(true)
                .build(),
            contentDescription = name,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp),
            contentScale = ContentScale.Fit,
            loading = {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                }
            },
            error = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Could not decode this image.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.78f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        )
    }
}

@Composable
private fun PdfPreview(file: File, pageCount: Int) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0B)),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(pageCount) { index ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    PdfPageImage(
                        file = file,
                        pageIndex = index,
                        targetWidthPx = constraints.maxWidth.coerceAtLeast(320)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${index + 1} / $pageCount",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TextFaint,
                    fontFamily = MonoFont
                )
            }
        }
    }
}

@Composable
private fun PdfPageImage(file: File, pageIndex: Int, targetWidthPx: Int) {
    val render by produceState<PdfPageRender>(
        PdfPageRender.Loading,
        file.absolutePath,
        pageIndex,
        targetWidthPx
    ) {
        value = withContext(Dispatchers.IO) { renderPdfPage(file, pageIndex, targetWidthPx) }
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        when (val state = render) {
            PdfPageRender.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp)
            }
            is PdfPageRender.Ready -> ComposeImage(
                bitmap = state.bitmap.asImageBitmap(),
                contentDescription = "PDF page ${pageIndex + 1}",
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.FillWidth
            )
            is PdfPageRender.Failed -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8A8D80),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun renderPdfPage(file: File, pageIndex: Int, targetWidthPx: Int): PdfPageRender = try {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (pageIndex !in 0 until renderer.pageCount) {
                PdfPageRender.Failed("Page ${pageIndex + 1} is not available.")
            } else {
                renderer.openPage(pageIndex).use { page ->
                    val sourceWidth = page.width.coerceAtLeast(1)
                    val sourceHeight = page.height.coerceAtLeast(1)
                    var scale = targetWidthPx.toFloat() / sourceWidth
                    val maxHeight = 4096f
                    if (sourceHeight * scale > maxHeight) scale = maxHeight / sourceHeight
                    val bitmapWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
                    val bitmapHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(AndroidColor.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    PdfPageRender.Ready(bitmap)
                }
            }
        }
    }
} catch (error: Exception) {
    PdfPageRender.Failed(error.message ?: "Could not render this page.")
} catch (error: OutOfMemoryError) {
    PdfPageRender.Failed("This page is too large to render.")
}

@Composable
private fun VideoPreview(file: File) {
    val context = LocalContext.current
    var playbackError by remember(file.absolutePath) { mutableStateOf<String?>(null) }
    val videoView = remember(file.absolutePath) { VideoView(context) }

    DisposableEffect(videoView, file.absolutePath) {
        val controller = MediaController(context)
        controller.setAnchorView(videoView)
        videoView.setMediaController(controller)
        videoView.setOnPreparedListener { player ->
            player.setVolume(1f, 1f)
            videoView.start()
        }
        videoView.setOnErrorListener { _, what, extra ->
            playbackError = "Video playback failed ($what/$extra)."
            true
        }
        runCatching { videoView.setVideoPath(file.absolutePath) }
            .onFailure { playbackError = it.message ?: "Could not open this video." }
        onDispose {
            runCatching { videoView.setMediaController(null) }
            runCatching { videoView.stopPlayback() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { videoView },
            modifier = Modifier.fillMaxSize()
        )
        playbackError?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.78f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun AudioPreview(file: File) {
    val player = remember(file.absolutePath) { MediaPlayer() }
    var prepared by remember(file.absolutePath) { mutableStateOf(false) }
    var playing by remember(file.absolutePath) { mutableStateOf(false) }
    var durationMs by remember(file.absolutePath) { mutableLongStateOf(0L) }
    var positionMs by remember(file.absolutePath) { mutableLongStateOf(0L) }
    var playbackError by remember(file.absolutePath) { mutableStateOf<String?>(null) }

    DisposableEffect(player, file.absolutePath) {
        player.setOnPreparedListener { mediaPlayer ->
            prepared = true
            durationMs = mediaPlayer.duration.coerceAtLeast(0).toLong()
            mediaPlayer.start()
            playing = true
        }
        player.setOnCompletionListener {
            playing = false
            positionMs = durationMs
        }
        player.setOnErrorListener { _, what, extra ->
            playbackError = "Audio playback failed ($what/$extra)."
            true
        }
        runCatching {
            player.setDataSource(file.absolutePath)
            player.prepareAsync()
        }.onFailure { playbackError = it.message ?: "Could not open this audio file." }
        onDispose {
            player.setOnPreparedListener(null)
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            runCatching { if (player.isPlaying) player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
    }

    LaunchedEffect(playing, prepared) {
        while (playing) {
            positionMs = runCatching { player.currentPosition.toLong() }.getOrDefault(positionMs)
            delay(350)
        }
    }

    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Violet.copy(alpha = 0.10f))
                    .border(0.5.dp, Violet.copy(alpha = 0.24f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Outlined.AudioFile,
                    contentDescription = null,
                    tint = Violet,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = when {
                    playbackError != null -> playbackError!!
                    !prepared -> "Preparing audio..."
                    else -> "${formatDuration(positionMs)} / ${formatDuration(durationMs)}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (playbackError != null) Danger else TextFaint,
                fontFamily = MonoFont,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(OutlineDark)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(Accent)
                )
            }
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(if (prepared && playbackError == null) Accent else Accent.copy(alpha = 0.35f))
                    .clickable(enabled = prepared && playbackError == null) {
                        runCatching {
                            if (playing) {
                                player.pause()
                                playing = false
                            } else {
                                if (durationMs > 0L && positionMs >= durationMs) {
                                    player.seekTo(0)
                                    positionMs = 0L
                                }
                                player.start()
                                playing = true
                            }
                        }.onFailure {
                            playbackError = it.message ?: "Playback failed."
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playing) "Pause" else "Play",
                    tint = OnAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun TextPreview(body: String, truncated: Boolean, fileSize: Long) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (truncated) {
            Surface(
                color = Warning.copy(alpha = 0.10f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Showing first ${formatBytes(MAX_TEXT_PREVIEW_BYTES.toLong())} of ${formatBytes(fileSize)}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = Warning,
                    fontFamily = MonoFont
                )
            }
            HorizontalDivider(
                thickness = 0.5.dp,
                color = OutlineDark.copy(alpha = 0.6f)
            )
        }
        SelectionContainer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = body.ifEmpty { "(empty file)" },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = MonoFont,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    ),
                    color = TextPrimary
                )
            }
        }
    }
}

@Composable
private fun ArchivePreview(entries: List<String>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        item {
            Text(
                text = if (entries.isEmpty()) "Empty archive" else "${entries.size} entries",
                modifier = Modifier.padding(bottom = 6.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextFaint,
                fontFamily = MonoFont
            )
        }
        items(entries) { entry ->
            Text(
                text = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                fontFamily = MonoFont,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            HorizontalDivider(
                thickness = 0.5.dp,
                color = OutlineSoft.copy(alpha = 0.6f)
            )
        }
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

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
