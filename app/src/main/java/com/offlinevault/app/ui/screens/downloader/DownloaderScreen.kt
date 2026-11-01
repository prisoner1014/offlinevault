package com.offlinevault.app.ui.screens.downloader

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.DownloadEntity
import com.offlinevault.app.data.local.entity.DownloadStatus
import com.offlinevault.app.data.local.entity.MediaType
import com.offlinevault.app.data.storage.StorageBreakdown
import com.offlinevault.app.service.download.DownloaderService
import com.offlinevault.app.service.download.MediaQuality
import com.offlinevault.app.service.download.YouTubeAuthManager
import com.offlinevault.app.util.FileSharingUtils
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderScreen(
    prefilledUrl: String = "",
    onNavigateToVault: () -> Unit = {},
    onPlayVideo: (path: String, title: String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val downloadDao = remember { AppDatabase.getInstance(context).downloadDao() }
    val downloads by downloadDao.getAllDownloads().collectAsState(initial = emptyList())

    val authManager = remember { YouTubeAuthManager(context) }
    var isYtLoggedIn by remember { mutableStateOf(authManager.hasActiveCookies()) }
    var showLoginDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }

    var inputUrl by remember(prefilledUrl) { mutableStateOf(prefilledUrl) }
    var selectedQuality by remember { mutableStateOf(MediaQuality.BEST_VIDEO) }
    var downloadSubtitles by remember { mutableStateOf(false) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    // Intercept hardware back button & system edge back gestures
    BackHandler(
        enabled = showLoginDialog || showImportDialog || showAccountMenu || qualityMenuExpanded || (inputUrl.isNotBlank() && inputUrl != prefilledUrl)
    ) {
        when {
            showLoginDialog -> showLoginDialog = false
            showImportDialog -> showImportDialog = false
            showAccountMenu -> showAccountMenu = false
            qualityMenuExpanded -> qualityMenuExpanded = false
            else -> inputUrl = prefilledUrl
        }
    }

    if (showLoginDialog) {
        YouTubeLoginDialog(
            authManager = authManager,
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = {
                isYtLoggedIn = authManager.hasActiveCookies()
                showLoginDialog = false
            }
        )
    }

    if (showImportDialog) {
        ImportCookiesDialog(
            authManager = authManager,
            onDismiss = { showImportDialog = false },
            onSuccess = {
                isYtLoggedIn = authManager.hasActiveCookies()
                showImportDialog = false
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Video & Audio Downloader",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Powered by embedded yt-dlp & FFmpeg",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))

        // YouTube Account & Membership Auth Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isYtLoggedIn) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isYtLoggedIn) Icons.Default.CheckCircle else Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = if (isYtLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isYtLoggedIn) "YouTube Account Connected" else "YouTube Member Videos",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isYtLoggedIn) {
                                "Memberships active (${authManager.getCookieCount()} cookies)"
                            } else {
                                "Sign in to download member-only videos"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isYtLoggedIn) {
                    Box {
                        IconButton(onClick = { showAccountMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Manage Account")
                        }
                        DropdownMenu(
                            expanded = showAccountMenu,
                            onDismissRequest = { showAccountMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Refresh In-App Login") },
                                onClick = {
                                    showAccountMenu = false
                                    showLoginDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) }
                            )
                            DropdownMenuItem(
                                text = { Text("Import New cookies.txt") },
                                onClick = {
                                    showAccountMenu = false
                                    showImportDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Disconnect & Clear Cookies") },
                                onClick = {
                                    showAccountMenu = false
                                    authManager.clearCookies(clearWebViewCookies = true)
                                    isYtLoggedIn = false
                                },
                                leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                            )
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilledTonalButton(
                            onClick = { showLoginDialog = true },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Login, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sign In", style = MaterialTheme.typography.labelSmall)
                        }
                        IconButton(onClick = { showImportDialog = true }) {
                            Icon(
                                Icons.Default.FileOpen,
                                contentDescription = "Import cookies.txt",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // URL Input Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = { inputUrl = it },
                    label = { Text("Paste video or audio link") },
                    placeholder = { Text("https://youtube.com/... or any media link") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
                            if (clipText.isNotBlank()) inputUrl = clipText
                        }) {
                            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Options Row: Quality Picker & Download Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quality picker
                    Box {
                        OutlinedButton(onClick = { qualityMenuExpanded = true }) {
                            Icon(
                                if (selectedQuality.isAudioOnly) Icons.Default.Audiotrack else Icons.Default.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(selectedQuality.label)
                        }

                        DropdownMenu(
                            expanded = qualityMenuExpanded,
                            onDismissRequest = { qualityMenuExpanded = false }
                        ) {
                            Text(
                                "🎬 Video Resolutions",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            MediaQuality.entries.filter { !it.isAudioOnly }.forEach { quality ->
                                DropdownMenuItem(
                                    text = { Text(quality.label) },
                                    onClick = {
                                        selectedQuality = quality
                                        qualityMenuExpanded = false
                                    }
                                )
                            }
                            HorizontalDivider()
                            Text(
                                "🎙️ Audio Formats (Save Space)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                            )
                            MediaQuality.entries.filter { it.isAudioOnly }.forEach { quality ->
                                DropdownMenuItem(
                                    text = { Text(quality.label) },
                                    onClick = {
                                        selectedQuality = quality
                                        qualityMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Download trigger button
                    Button(
                        onClick = {
                            if (inputUrl.isNotBlank()) {
                                scope.launch {
                                    val newId = downloadDao.insertDownload(
                                        DownloadEntity(
                                            title = "Fetching info...",
                                            sourceUrl = inputUrl,
                                            mediaType = if (selectedQuality.isAudioOnly) MediaType.AUDIO else MediaType.VIDEO,
                                            formatDescription = selectedQuality.label,
                                            status = DownloadStatus.QUEUED
                                        )
                                    )
                                    DownloaderService.start(context, newId, inputUrl, selectedQuality, downloadSubtitles)
                                    inputUrl = ""
                                }
                            }
                        },
                        enabled = inputUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Subtitle checkbox toggle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = downloadSubtitles,
                        onCheckedChange = { downloadSubtitles = it }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Download & Embed Subtitles (.srt/vtt)",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Active & Recent Downloads (${downloads.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (downloads.any { it.status == DownloadStatus.COMPLETED }) {
                TextButton(
                    onClick = {
                        scope.launch {
                            downloads.filter { it.status == DownloadStatus.COMPLETED }.forEach {
                                downloadDao.deleteDownload(it)
                            }
                        }
                    }
                ) {
                    Text("Clear Completed", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No downloads yet.\nPaste a URL above to start saving offline!",
                    color = MaterialTheme.colorScheme.outline
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(downloads, key = { it.id }) { item ->
                    DownloadItemCard(
                        download = item,
                        onPlay = {
                            val path = item.localFilePath
                            if (!path.isNullOrEmpty() && java.io.File(path).exists()) {
                                onPlayVideo(path, item.title)
                            } else {
                                onNavigateToVault()
                            }
                        },
                        onOpenExternal = {
                            val path = item.localFilePath
                            if (!path.isNullOrEmpty()) {
                                FileSharingUtils.openInExternalPlayer(context, path, item.mediaType == MediaType.AUDIO)
                            }
                        },
                        onRetry = {
                            scope.launch {
                                downloadDao.updateDownload(item.copy(status = DownloadStatus.QUEUED, etaText = ""))
                                val quality = MediaQuality.entries.firstOrNull { it.label == item.formatDescription } ?: MediaQuality.BEST_VIDEO
                                DownloaderService.start(context, item.id, item.sourceUrl, quality, false)
                            }
                        },
                        onDelete = {
                            scope.launch {
                                item.localFilePath?.let { java.io.File(it).delete() }
                                downloadDao.deleteDownload(item)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    onPlay: () -> Unit = {},
    onOpenExternal: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            if (download.status == DownloadStatus.COMPLETED) {
                onPlay()
            }
        }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (download.status == DownloadStatus.COMPLETED) {
                    FilledIconButton(
                        onClick = onPlay,
                        modifier = Modifier.size(40.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play Video")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title.ifEmpty { download.sourceUrl },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    when (download.status) {
                        DownloadStatus.COMPLETED -> {
                            val finalSize = if (download.totalSizeBytes > 0) {
                                download.totalSizeBytes
                            } else {
                                download.localFilePath?.let { java.io.File(it).length() } ?: 0L
                            }
                            Text(
                                text = "✅ Downloaded • ${StorageBreakdown.formatBytes(finalSize)} • ${download.formatDescription ?: "Media"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!download.localFilePath.isNullOrEmpty()) {
                                Text(
                                    text = java.io.File(download.localFilePath).name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 1
                                )
                            }
                        }
                        DownloadStatus.DOWNLOADING -> {
                            val bytesText = if (download.totalSizeBytes > 0L) {
                                "${StorageBreakdown.formatBytes(download.downloadedBytes)} / ${StorageBreakdown.formatBytes(download.totalSizeBytes)}"
                            } else if (download.downloadedBytes > 0L) {
                                "${StorageBreakdown.formatBytes(download.downloadedBytes)} downloaded"
                            } else {
                                "Preparing stream..."
                            }
                            Text(
                                text = "⬇️ Downloading • $bytesText • ${download.formatDescription ?: "Media"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        DownloadStatus.QUEUED -> {
                            Text(
                                text = "⏳ Queued in background • Waiting to start",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DownloadStatus.FAILED -> {
                            Text(
                                text = "❌ Download failed • ${download.etaText.ifEmpty { "Network or extract error" }}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        DownloadStatus.PAUSED -> {
                            Text(
                                text = "⏸️ Paused",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (download.status == DownloadStatus.FAILED) {
                        IconButton(onClick = onRetry) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (download.status == DownloadStatus.COMPLETED && !download.localFilePath.isNullOrEmpty()) {
                        IconButton(onClick = onOpenExternal) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External App",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            if (download.status == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (download.progressPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val bytesText = if (download.totalSizeBytes > 0L) {
                        "${download.progressPercent.toInt()}% (${StorageBreakdown.formatBytes(download.downloadedBytes)} of ${StorageBreakdown.formatBytes(download.totalSizeBytes)})"
                    } else {
                        "${download.progressPercent.toInt()}% (${StorageBreakdown.formatBytes(download.downloadedBytes)})"
                    }
                    Text(
                        text = bytesText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    val metaList = mutableListOf<String>()
                    if (download.speedText.isNotBlank()) metaList.add("⚡ ${download.speedText}")
                    if (download.etaText.isNotBlank()) metaList.add("⏱️ ETA: ${download.etaText}")

                    Text(
                        text = metaList.joinToString("  ").ifEmpty { "Downloading..." },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
