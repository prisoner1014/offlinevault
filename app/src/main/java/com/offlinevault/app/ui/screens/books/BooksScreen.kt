package com.offlinevault.app.ui.screens.books

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.BookEntity
import com.offlinevault.app.data.storage.StorageBreakdown
import com.offlinevault.app.service.book.BookDiscoveryService
import com.offlinevault.app.service.book.BookDownloader
import com.offlinevault.app.service.book.BookRepository
import com.offlinevault.app.service.book.DiscoveredBook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class BooksTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LIBRARY("Library", Icons.Default.CollectionsBookmark),
    DISCOVER("Discover Books", Icons.Default.Explore),
    DOWNLOAD_URL("Direct URL", Icons.Default.Download)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BooksScreen(
    onOpenBook: (Long) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val books by db.bookDao().getAllBooks().collectAsState(initial = emptyList())

    val discoveryService = remember { BookDiscoveryService() }
    val downloader = remember { BookDownloader(context) }

    var currentTab by remember { mutableStateOf(BooksTab.LIBRARY) }
    var searchQuery by remember { mutableStateOf("") }
    var directUrlInput by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadStatusText by remember { mutableStateOf("") }

    // Intercept hardware back button & system edge back gestures
    BackHandler(enabled = searchQuery.isNotBlank() || currentTab != BooksTab.LIBRARY) {
        if (searchQuery.isNotBlank()) {
            searchQuery = ""
        } else {
            currentTab = BooksTab.LIBRARY
        }
    }

    // Multi-repository & genre filtering state
    var selectedRepository by remember { mutableStateOf(BookRepository.ALL) }
    var selectedCategory by remember { mutableStateOf("All") }

    // Discovered books state
    var discoveredBooks by remember { mutableStateOf<List<DiscoveredBook>>(emptyList()) }
    var isSearchingDiscovered by remember { mutableStateOf(false) }

    // Load initial curated multi-repository catalog
    LaunchedEffect(Unit) {
        discoveredBooks = discoveryService.getMasterCatalog(selectedRepository, selectedCategory)
    }

    // System File Picker for importing local EPUB/PDF/TXT
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isDownloading = true
                downloadStatusText = "Importing book file..."
                withContext(Dispatchers.IO) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(uri)
                        if (inputStream != null) {
                            val tempFile = File(context.cacheDir, "import_${System.currentTimeMillis()}.epub")
                            FileOutputStream(tempFile).use { out ->
                                inputStream.copyTo(out)
                            }
                            val result = downloader.importLocalFile(tempFile)
                            tempFile.delete()
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    val b = result.getOrThrow()
                                    Toast.makeText(context, "Imported '${b.title}'", Toast.LENGTH_SHORT).show()
                                    currentTab = BooksTab.LIBRARY
                                } else {
                                    Toast.makeText(context, "Import failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Import failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    } finally {
                        isDownloading = false
                        downloadStatusText = ""
                    }
                }
            }
        }
    }

    fun refreshDiscoveredBooks(
        query: String = searchQuery,
        repo: BookRepository = selectedRepository,
        cat: String = selectedCategory
    ) {
        isSearchingDiscovered = true
        scope.launch {
            val results = discoveryService.searchBooks(
                query = query,
                repository = repo,
                category = cat
            )
            discoveredBooks = results
            isSearchingDiscovered = false
        }
    }

    fun startDownload(book: DiscoveredBook) {
        scope.launch {
            isDownloading = true
            downloadStatusText = "Downloading ${book.title.take(24)}..."
            downloadProgress = 0f

            val result = downloader.downloadBook(
                downloadUrl = book.downloadUrl,
                suggestedTitle = book.title,
                suggestedAuthor = book.author,
                suggestedCoverUrl = book.coverUrl,
                onProgress = { p -> downloadProgress = p }
            )

            isDownloading = false
            downloadStatusText = ""

            if (result.isSuccess) {
                val entity = result.getOrThrow()
                Toast.makeText(context, "Downloaded '${entity.title}'!", Toast.LENGTH_SHORT).show()
                currentTab = BooksTab.LIBRARY
            } else {
                Toast.makeText(context, "Download failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Books & Reader",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "EPUB & PDF reader, Fold two-page spread & classics",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedButton(
                onClick = { filePickerLauncher.launch("*/*") },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Import")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Navigation Tabs
        TabRow(
            selectedTabIndex = currentTab.ordinal,
            containerColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            BooksTab.entries.forEach { tab ->
                Tab(
                    selected = currentTab == tab,
                    onClick = { currentTab = tab },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                when (tab) {
                                    BooksTab.LIBRARY -> "Library (${books.size})"
                                    else -> tab.label
                                }
                            )
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Downloading indicator
        if (isDownloading) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (downloadProgress > 0f) {
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                Text(
                    text = "$downloadStatusText ${if (downloadProgress > 0f) "(${(downloadProgress * 100).toInt()}%)" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (currentTab) {
            BooksTab.LIBRARY -> {
                // TAB 0: Library
                if (books.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                            Icon(
                                Icons.AutoMirrored.Filled.MenuBook,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Your Library is Empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Discover thousands of free public domain classics or download direct EPUB & PDF books to read offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Button(onClick = { currentTab = BooksTab.DISCOVER }) {
                                    Icon(Icons.Default.Explore, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Explore Classics")
                                }
                                OutlinedButton(onClick = { filePickerLauncher.launch("*/*") }) {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Import File")
                                }
                            }
                        }
                    }
                } else {
                    // Continue reading banner for most recently opened book
                    val mostRecent = remember(books) {
                        books.maxByOrNull { it.lastReadEpochMs }?.takeIf { it.lastReadEpochMs > 0 }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (mostRecent != null) {
                            item {
                                ContinueReadingCard(
                                    book = mostRecent,
                                    onRead = { onOpenBook(mostRecent.id) }
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "All Books (${books.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        items(books, key = { it.id }) { book ->
                            BookLibraryCard(
                                book = book,
                                onRead = { onOpenBook(book.id) },
                                onDelete = {
                                    scope.launch {
                                        withContext(Dispatchers.IO) {
                                            book.localFilePath?.let { p -> File(p).delete() }
                                            db.bookDao().deleteBook(book)
                                        }
                                        Toast.makeText(context, "Deleted '${book.title}'", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            BooksTab.DISCOVER -> {
                // TAB 1: Discover & Multi-Repository Search
                Column(modifier = Modifier.fillMaxSize()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            refreshDiscoveredBooks(it, selectedRepository, selectedCategory)
                        },
                        placeholder = { Text("Search title, author, or keyword...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotBlank()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    refreshDiscoveredBooks("", selectedRepository, selectedCategory)
                                }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Open Repositories Selector Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "REPO:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        BookRepository.entries.forEach { repo ->
                            FilterChip(
                                selected = selectedRepository == repo,
                                onClick = {
                                    selectedRepository = repo
                                    refreshDiscoveredBooks(searchQuery, repo, selectedCategory)
                                },
                                label = { Text(repo.displayName) },
                                leadingIcon = if (selectedRepository == repo) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Genre / Topic Filter Bar
                    val categories = listOf("All", "Philosophy", "Sci-Fi", "Mystery", "Literature", "Adventure", "History", "Gothic")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GENRE:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )
                        categories.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = {
                                    selectedCategory = cat
                                    refreshDiscoveredBooks(searchQuery, selectedRepository, cat)
                                },
                                label = { Text(cat) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Found ${discoveredBooks.size} books in ${selectedRepository.badge}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "100% Free Public Domain",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    if (isSearchingDiscovered) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(discoveredBooks, key = { it.id + it.downloadUrl }) { item ->
                            val isAlreadyDownloaded = books.any { it.downloadUrl == item.downloadUrl || it.title.equals(item.title, ignoreCase = true) }
                            DiscoveredBookCard(
                                book = item,
                                isDownloaded = isAlreadyDownloaded,
                                onDownload = { startDownload(item) }
                            )
                        }
                    }
                }
            }

            BooksTab.DOWNLOAD_URL -> {
                // TAB 2: Direct URL & Import
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Direct Book Downloader",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Enter any direct link to an EPUB, PDF, or text book file to download into your offline library.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = directUrlInput,
                                onValueChange = { directUrlInput = it },
                                label = { Text("Book URL (.epub, .pdf, .txt)") },
                                placeholder = { Text("https://example.com/book.epub") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    val url = directUrlInput.trim()
                                    if (url.isNotBlank()) {
                                        scope.launch {
                                            isDownloading = true
                                            downloadStatusText = "Downloading book from URL..."
                                            downloadProgress = 0f

                                            val result = downloader.downloadBook(
                                                downloadUrl = url,
                                                onProgress = { p -> downloadProgress = p }
                                            )

                                            isDownloading = false
                                            downloadStatusText = ""

                                            if (result.isSuccess) {
                                                val entity = result.getOrThrow()
                                                Toast.makeText(context, "Downloaded '${entity.title}'!", Toast.LENGTH_SHORT).show()
                                                directUrlInput = ""
                                                currentTab = BooksTab.LIBRARY
                                            } else {
                                                Toast.makeText(context, "Download failed: ${result.exceptionOrNull()?.localizedMessage}", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }
                                },
                                enabled = directUrlInput.isNotBlank() && !isDownloading,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Download Book")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Import Local File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Import an EPUB, PDF, or TXT file stored on your phone or SD card into your personal vault.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose File from Storage")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ContinueReadingCard(
    book: BookEntity,
    onRead: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRead),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumbnail(
                coverUrl = book.coverUrl,
                title = book.title,
                modifier = Modifier.size(width = 54.dp, height = 76.dp)
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONTINUE READING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                val percent = (book.readingProgress * 100).toInt()
                LinearProgressIndicator(
                    progress = { book.readingProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Chapter ${book.currentChapterIndex + 1} of ${book.totalChapters}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "$percent%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            FilledTonalIconButton(
                onClick = onRead,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Resume Reading",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun BookLibraryCard(
    book: BookEntity,
    onRead: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRead),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumbnail(
                coverUrl = book.coverUrl,
                title = book.title,
                modifier = Modifier.size(width = 48.dp, height = 68.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                book.author?.let { author ->
                    Text(
                        text = author,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(
                        onClick = {},
                        label = { Text(book.format, style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.height(24.dp)
                    )

                    if (book.fileSizeBytes > 0) {
                        Text(
                            text = StorageBreakdown.formatBytes(book.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    if (book.readingProgress > 0f) {
                        val percent = (book.readingProgress * 100).toInt()
                        Text(
                            text = "• $percent% read",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Button(
                    onClick = onRead,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Read")
                }
            }
        }
    }
}

@Composable
fun DiscoveredBookCard(
    book: DiscoveredBook,
    isDownloaded: Boolean,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumbnail(
                coverUrl = book.coverUrl,
                title = book.title,
                modifier = Modifier.size(width = 54.dp, height = 76.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Repository badge & genre tag
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (book.repositoryBadge) {
                            "Standard Ebooks" -> MaterialTheme.colorScheme.tertiaryContainer
                            "Open Library" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        Text(
                            text = book.repositoryBadge,
                            style = MaterialTheme.typography.labelSmall,
                            color = when (book.repositoryBadge) {
                                "Standard Ebooks" -> MaterialTheme.colorScheme.onTertiaryContainer
                                "Open Library" -> MaterialTheme.colorScheme.onSecondaryContainer
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            },
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (book.subjects.isNotEmpty()) {
                        Text(
                            text = "• ${book.subjects.first()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                book.synopsis?.let { syn ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = syn,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (book.downloadCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "⭐ ${book.downloadCount} readers • EPUB",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isDownloaded) {
                AssistChip(
                    onClick = {},
                    label = { Text("✓ Saved", style = MaterialTheme.typography.labelSmall) }
                )
            } else {
                Button(
                    onClick = onDownload,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Get", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
fun BookCoverThumbnail(
    coverUrl: String?,
    title: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Book,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
