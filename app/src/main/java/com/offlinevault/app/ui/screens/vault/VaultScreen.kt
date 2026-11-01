package com.offlinevault.app.ui.screens.vault

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.ArchiveEntity
import com.offlinevault.app.data.local.entity.DownloadEntity
import com.offlinevault.app.data.storage.StorageBreakdown
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.service.podcast.PodcastPlayerManager
import com.offlinevault.app.util.FileSharingUtils
import kotlinx.coroutines.launch
import java.io.File

enum class VaultSortOrder(val label: String) {
    NEWEST("Newest"),
    LARGEST("Largest Size"),
    NAME("A-Z")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onOpenWebArchive: (String) -> Unit = {},
    onPlayVideo: (path: String, title: String) -> Unit = { _, _ -> },
    onOpenBook: (Long) -> Unit = {},
    onNavigateToGames: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageTracker = remember { StorageTracker(context) }
    val db = remember { AppDatabase.getInstance(context) }

    var storageBreakdown by remember { mutableStateOf<StorageBreakdown?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Web Archives, 1: Videos, 2: Podcasts, 3: Books
    var searchQuery by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf(VaultSortOrder.NEWEST) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Intercept hardware back button & system edge back gestures
    BackHandler(
        enabled = showSortMenu || searchQuery.isNotBlank() || selectedTab != 0
    ) {
        when {
            showSortMenu -> showSortMenu = false
            searchQuery.isNotBlank() -> searchQuery = ""
            selectedTab != 0 -> selectedTab = 0
        }
    }

    val archives by db.archiveDao().getAllArchives().collectAsState(initial = emptyList())
    val completedDownloads by db.downloadDao().getCompletedDownloads().collectAsState(initial = emptyList())
    val downloadedEpisodes by db.feedDao().getDownloadedEpisodes().collectAsState(initial = emptyList())
    val books by db.bookDao().getAllBooks().collectAsState(initial = emptyList())

    // Refresh storage calculator
    fun refreshStorage() {
        scope.launch {
            storageBreakdown = storageTracker.calculateStorageBreakdown()
        }
    }

    LaunchedEffect(Unit) {
        refreshStorage()
    }

    // Filtered & Sorted Lists
    val filteredArchives = remember(archives, searchQuery, sortOrder) {
        val q = searchQuery.trim()
        val list = if (q.isBlank()) archives else archives.filter {
            it.title.contains(q, ignoreCase = true) || it.originalUrl.contains(q, ignoreCase = true)
        }
        when (sortOrder) {
            VaultSortOrder.NEWEST -> list.sortedByDescending { it.id }
            VaultSortOrder.LARGEST -> list.sortedByDescending { it.fileSizeBytes }
            VaultSortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        }
    }

    val filteredDownloads = remember(completedDownloads, searchQuery, sortOrder) {
        val q = searchQuery.trim()
        val list = if (q.isBlank()) completedDownloads else completedDownloads.filter {
            it.title.contains(q, ignoreCase = true) || it.sourceUrl.contains(q, ignoreCase = true)
        }
        when (sortOrder) {
            VaultSortOrder.NEWEST -> list.sortedByDescending { it.id }
            VaultSortOrder.LARGEST -> list.sortedByDescending {
                if (it.totalSizeBytes > 0) it.totalSizeBytes else (it.localFilePath?.let { p -> File(p).length() } ?: 0L)
            }
            VaultSortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        }
    }

    val filteredEpisodes = remember(downloadedEpisodes, searchQuery, sortOrder) {
        val q = searchQuery.trim()
        val list = if (q.isBlank()) downloadedEpisodes else downloadedEpisodes.filter {
            it.title.contains(q, ignoreCase = true)
        }
        when (sortOrder) {
            VaultSortOrder.NEWEST -> list.sortedByDescending { it.pubDate }
            VaultSortOrder.LARGEST -> list.sortedByDescending { it.fileSizeBytes }
            VaultSortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        }
    }

    val filteredBooks = remember(books, searchQuery, sortOrder) {
        val q = searchQuery.trim()
        val list = if (q.isBlank()) books else books.filter {
            it.title.contains(q, ignoreCase = true) || it.author?.contains(q, ignoreCase = true) == true
        }
        when (sortOrder) {
            VaultSortOrder.NEWEST -> list.sortedByDescending { it.id }
            VaultSortOrder.LARGEST -> list.sortedByDescending { it.fileSizeBytes }
            VaultSortOrder.NAME -> list.sortedBy { it.title.lowercase() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Offline Vault & Storage",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Private App Directory • No Public Folder Clutter",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { refreshStorage() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Storage")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Storage Analytics & Tools Card
        storageBreakdown?.let { breakdown ->
            StorageAnalyticsCard(
                breakdown = breakdown,
                onCleanCache = {
                    scope.launch {
                        val freed = storageTracker.clearCache()
                        refreshStorage()
                        Toast.makeText(
                            context,
                            "Cleaned ${StorageBreakdown.formatBytes(freed)} from cache!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Quick Access Banner to Offline Arcade (10 Games)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onNavigateToGames),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Offline Arcade (10 Games)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "2048, Retro Snake, Block Drop, Sudoku, Solitaire & more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Arcade",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Category Switcher
        TabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Web (${filteredArchives.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Media (${filteredDownloads.size})") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Podcasts (${filteredEpisodes.size})") }
            )
            Tab(
                selected = selectedTab == 3,
                onClick = { selectedTab = 3 },
                text = { Text("Books (${filteredBooks.size})") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search & Sort Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter items in vault...", style = MaterialTheme.typography.bodySmall) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier.weight(1f).height(50.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                OutlinedButton(
                    onClick = { showSortMenu = true },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.height(50.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(sortOrder.label, style = MaterialTheme.typography.labelSmall)
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    VaultSortOrder.entries.forEach { order ->
                        DropdownMenuItem(
                            text = { Text(order.label) },
                            leadingIcon = if (sortOrder == order) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            onClick = {
                                sortOrder = order
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Lists
        when (selectedTab) {
            0 -> WebArchivesList(
                archives = filteredArchives,
                onOpenArchive = { archive ->
                    archive.localMhtmlPath?.let { onOpenWebArchive(it) }
                },
                onDelete = {
                    scope.launch {
                        it.localMhtmlPath?.let { path ->
                            val f = File(path)
                            if (it.isSiteDirectory) {
                                f.parentFile?.deleteRecursively()
                            } else {
                                f.delete()
                            }
                        }
                        db.archiveDao().deleteArchive(it)
                        refreshStorage()
                    }
                }
            )
            1 -> CompletedDownloadsList(
                downloads = filteredDownloads,
                onPlay = { download ->
                    val path = download.localFilePath
                    if (!path.isNullOrEmpty() && File(path).exists()) {
                        onPlayVideo(path, download.title)
                    } else {
                        // Attempt to locate file in video directory
                        val videoDir = storageTracker.getCategoryDir(StorageCategory.VIDEOS)
                        val match = videoDir.listFiles()?.firstOrNull { f ->
                            download.sourceUrl.contains(f.nameWithoutExtension) || f.name.contains(download.title)
                        }
                        if (match != null && match.exists()) {
                            onPlayVideo(match.absolutePath, download.title)
                        } else {
                            Toast.makeText(context, "File path not found. Please re-download.", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onOpenExternal = { download ->
                    val path = download.localFilePath
                    val isAudio = download.mediaType == com.offlinevault.app.data.local.entity.MediaType.AUDIO
                    if (!path.isNullOrEmpty() && File(path).exists()) {
                        FileSharingUtils.openInExternalPlayer(context, path, isAudio)
                    } else {
                        val videoDir = storageTracker.getCategoryDir(StorageCategory.VIDEOS)
                        val match = videoDir.listFiles()?.firstOrNull { f ->
                            download.sourceUrl.contains(f.nameWithoutExtension) || f.name.contains(download.title)
                        }
                        if (match != null && match.exists()) {
                            FileSharingUtils.openInExternalPlayer(context, match.absolutePath, isAudio)
                        } else {
                            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onDelete = {
                    scope.launch {
                        it.localFilePath?.let { path -> File(path).delete() }
                        db.downloadDao().deleteDownload(it)
                        refreshStorage()
                    }
                }
            )
            2 -> DownloadedPodcastsList(
                episodes = filteredEpisodes,
                onPlay = { ep ->
                    PodcastPlayerManager.playEpisode(context, ep, null, ep.imageUrl, db)
                },
                onOpenExternal = { ep ->
                    ep.localAudioPath?.let { path ->
                        FileSharingUtils.openInExternalPlayer(context, path, true)
                    }
                },
                onDelete = {
                    scope.launch {
                        it.localAudioPath?.let { path -> File(path).delete() }
                        db.feedDao().updateEpisode(it.copy(isDownloaded = false, localAudioPath = null))
                        refreshStorage()
                    }
                }
            )
            3 -> DownloadedBooksList(
                books = filteredBooks,
                onOpenBook = { onOpenBook(it.id) },
                onDelete = { book ->
                    scope.launch {
                        book.localFilePath?.let { File(it).delete() }
                        db.bookDao().deleteBook(book)
                        refreshStorage()
                    }
                }
            )
        }
    }
}

@Composable
fun StorageAnalyticsCard(
    breakdown: StorageBreakdown,
    onCleanCache: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "App Storage Used",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = StorageBreakdown.formatBytes(breakdown.totalAppUsedBytes),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Device Free Space",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = String.format("%.1f GB free", breakdown.freeDeviceGb),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-segment storage visualization bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val totalUsed = breakdown.totalAppUsedBytes
                if (totalUsed > 0L) {
                    val totalFloat = totalUsed.toFloat()
                    if (breakdown.videoBytes > 0L) {
                        Box(modifier = Modifier.weight((breakdown.videoBytes.toFloat() / totalFloat).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFFE53935)))
                    }
                    if (breakdown.podcastBytes > 0L) {
                        Box(modifier = Modifier.weight((breakdown.podcastBytes.toFloat() / totalFloat).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF8E24AA)))
                    }
                    if (breakdown.bookBytes > 0L) {
                        Box(modifier = Modifier.weight((breakdown.bookBytes.toFloat() / totalFloat).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFFFFB300)))
                    }
                    if (breakdown.webArchiveBytes > 0L) {
                        Box(modifier = Modifier.weight((breakdown.webArchiveBytes.toFloat() / totalFloat).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF1E88E5)))
                    }
                    if (breakdown.cacheBytes > 0L) {
                        Box(modifier = Modifier.weight((breakdown.cacheBytes.toFloat() / totalFloat).coerceAtLeast(0.01f)).fillMaxHeight().background(Color(0xFF43A047)))
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category metrics breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StorageCategoryTag("🎬 Videos", breakdown.videoBytes, Color(0xFFE53935))
                StorageCategoryTag("🎙️ Podcasts", breakdown.podcastBytes, Color(0xFF8E24AA))
                StorageCategoryTag("📚 Books", breakdown.bookBytes, Color(0xFFFFB300))
                StorageCategoryTag("📰 Web", breakdown.webArchiveBytes, Color(0xFF1E88E5))
                StorageCategoryTag("🧹 Cache", breakdown.cacheBytes, Color(0xFF43A047))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Tool action: 1-Tap Cache Cleaner
            FilledTonalButton(
                onClick = onCleanCache,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Clean Temporary Cache & Chunks (${StorageBreakdown.formatBytes(breakdown.cacheBytes)})")
            }
        }
    }
}

@Composable
fun StorageCategoryTag(label: String, bytes: Long, indicatorColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(indicatorColor)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(
                StorageBreakdown.formatBytes(bytes),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun WebArchivesList(
    archives: List<ArchiveEntity>,
    onOpenArchive: (ArchiveEntity) -> Unit = {},
    onDelete: (ArchiveEntity) -> Unit
) {
    if (archives.isEmpty()) {
        EmptyListPlaceholder("No web pages archived yet.\nUse the Browser tab to save pages or deep-crawl sites offline with 1 tap.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(archives, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenArchive(item) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (item.isSiteDirectory) Icons.Default.TravelExplore else Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val typeLabel = if (item.isSiteDirectory) {
                                "🌐 Offline Site (${item.pageCount} pgs, depth ${item.crawlDepth})"
                            } else {
                                "📄 Single Page (.mht)"
                            }
                            Text(
                                "$typeLabel • ${StorageBreakdown.formatBytes(item.fileSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                item.originalUrl,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onDelete(item) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompletedDownloadsList(
    downloads: List<DownloadEntity>,
    onPlay: (DownloadEntity) -> Unit,
    onOpenExternal: (DownloadEntity) -> Unit,
    onDelete: (DownloadEntity) -> Unit
) {
    if (downloads.isEmpty()) {
        EmptyListPlaceholder("No completed media downloads yet.\nDownloaded videos will appear here ready for 1-tap playback.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(downloads, key = { it.id }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPlay(item) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            FilledIconButton(
                                onClick = { onPlay(item) },
                                modifier = Modifier.size(44.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play Video")
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 2
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val finalSize = if (item.totalSizeBytes > 0) {
                                    item.totalSizeBytes
                                } else {
                                    item.localFilePath?.let { File(it).length() } ?: 0L
                                }
                                Text(
                                    text = "✅ ${item.formatDescription ?: "Media"} • ${StorageBreakdown.formatBytes(finalSize)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (!item.localFilePath.isNullOrEmpty()) {
                                    Text(
                                        text = File(item.localFilePath).name,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onOpenExternal(item) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in External App",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedPodcastsList(
    episodes: List<com.offlinevault.app.data.local.entity.EpisodeEntity>,
    onPlay: (com.offlinevault.app.data.local.entity.EpisodeEntity) -> Unit = {},
    onOpenExternal: (com.offlinevault.app.data.local.entity.EpisodeEntity) -> Unit = {},
    onDelete: (com.offlinevault.app.data.local.entity.EpisodeEntity) -> Unit
) {
    if (episodes.isEmpty()) {
        EmptyListPlaceholder("No podcast episodes downloaded yet.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(episodes, key = { it.id }) { item ->
                val isPlaying = PodcastPlayerManager.currentEpisode?.id == item.id && PodcastPlayerManager.isPlaying
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onPlay(item) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            val epArtwork = item.imageUrl
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!epArtwork.isNullOrBlank()) {
                                    AsyncImage(
                                        model = epArtwork,
                                        contentDescription = item.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Podcasts,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onPlay(item) }) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    StorageBreakdown.formatBytes(item.fileSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { onOpenExternal(item) }) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in External App",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                            IconButton(onClick = { onDelete(item) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DownloadedBooksList(
    books: List<com.offlinevault.app.data.local.entity.BookEntity>,
    onOpenBook: (com.offlinevault.app.data.local.entity.BookEntity) -> Unit = {},
    onDelete: (com.offlinevault.app.data.local.entity.BookEntity) -> Unit
) {
    if (books.isEmpty()) {
        EmptyListPlaceholder("No books in your vault yet.\nUse the Books tab to download classics from Project Gutenberg or import local EPUB/PDFs.")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(books, key = { it.id }) { book ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { onOpenBook(book) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.tertiaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!book.coverUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = book.coverUrl,
                                        contentDescription = book.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = book.format.uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(book.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                                val authorLabel = book.author ?: "Unknown Author"
                                Text(
                                    "$authorLabel • ${book.format.uppercase()} • ${StorageBreakdown.formatBytes(book.fileSizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                val progress = (book.readingProgress * 100).toInt()
                                if (progress > 0) {
                                    Text(
                                        "Reading progress: $progress%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        IconButton(onClick = { onDelete(book) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyListPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = MaterialTheme.colorScheme.outline)
    }
}
