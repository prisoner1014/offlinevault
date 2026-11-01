package com.offlinevault.app.ui.screens.browser

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.ArchiveEntity
import com.offlinevault.app.data.preferences.SettingsRepository
import com.offlinevault.app.data.storage.StorageBreakdown
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.service.crawler.CrawlConfig
import com.offlinevault.app.service.crawler.CrawlProgress
import com.offlinevault.app.service.crawler.ImageQualityOption
import com.offlinevault.app.service.crawler.OfflineArchiveSchemeHandler
import com.offlinevault.app.service.crawler.SiteCrawler
import kotlinx.coroutines.launch
import java.io.File

private const val DEFAULT_HOME_URL = "https://duckduckgo.com"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LowDataBrowserScreen(
    initialUrl: String = "https://duckduckgo.com",
    onSendToDownloader: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = remember { SettingsRepository(context) }
    val settings by settingsRepo.settingsFlow.collectAsState(initial = com.offlinevault.app.data.preferences.AppSettings())

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var urlInput by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var pageTitle by remember { mutableStateOf("New Tab") }
    var isLoading by remember { mutableStateOf(false) }
    var isImagesBlocked by remember { mutableStateOf(settings.blockImagesDataSaver) }
    var isAmoledDarkInjected by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    // Deep Crawl Dialog & Progress State
    var showDeepCrawlDialog by remember { mutableStateOf(false) }

    // Intercept hardware back button & system edge back gestures for WebView history & dialogs
    BackHandler(enabled = showDeepCrawlDialog || canGoBack) {
        if (showDeepCrawlDialog) {
            showDeepCrawlDialog = false
        } else if (canGoBack) {
            webViewInstance?.goBack()
        }
    }
    var crawlDepth by remember { mutableIntStateOf(1) } // 0, 1, 2
    var maxPagesCap by remember { mutableIntStateOf(25) } // 10, 25, 50, 100
    var imageQuality by remember { mutableStateOf(ImageQualityOption.LOW_QUALITY) }
    var sameDomainOnly by remember { mutableStateOf(true) }

    var isCrawling by remember { mutableStateOf(false) }
    var crawlProgress by remember { mutableStateOf<CrawlProgress?>(null) }

    val storageTracker = remember { StorageTracker(context) }
    val archiveDao = remember { AppDatabase.getInstance(context).archiveDao() }
    val siteCrawler = remember { SiteCrawler(context) }

    fun injectAmoledDark(view: WebView?) {
        val js = """
        (function() {
            var existing = document.getElementById('vault_amoled_style');
            if (!existing) {
                var style = document.createElement('style');
                style.id = 'vault_amoled_style';
                style.innerHTML = 'html, body { background-color: #090D16 !important; color: #E2E8F0 !important; } * { background-color: transparent !important; color: inherit !important; border-color: #334155 !important; } a { color: #60A5FA !important; } img, video, canvas, svg { filter: brightness(0.85) contrast(1.1) !important; }';
                document.head.appendChild(style);
            }
        })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    fun removeAmoledDark(view: WebView?) {
        val js = """
        (function() {
            var existing = document.getElementById('vault_amoled_style');
            if (existing) { existing.remove(); }
        })();
        """.trimIndent()
        view?.evaluateJavascript(js, null)
    }

    fun loadTargetUrl(rawUrl: String) {
        val archivesDir = storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES)
        val target = OfflineArchiveSchemeHandler.toVirtualUrl(archivesDir, rawUrl)
        currentUrl = target
        urlInput = if (target.startsWith(OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN)) {
            "offline://${target.removePrefix(OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN + "/")}"
        } else {
            target
        }

        if (rawUrl.endsWith(".mht", true) || rawUrl.endsWith(".mhtml", true) || target.endsWith(".mht", true) || target.endsWith(".mhtml", true)) {
            val cleanPath = rawUrl.removePrefix("file://").removePrefix(OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN + "/")
            val file = if (File(cleanPath).exists()) File(cleanPath) else File(archivesDir, cleanPath)
            if (file.exists()) {
                try {
                    val mhtmlContent = file.readText(Charsets.UTF_8)
                    webViewInstance?.loadDataWithBaseURL(
                        OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN + "/",
                        mhtmlContent,
                        "multipart/related",
                        "utf-8",
                        null
                    )
                    return
                } catch (e: Exception) {
                    // Fall back to standard load
                }
            }
        }

        webViewInstance?.loadUrl(target)
    }

    LaunchedEffect(initialUrl) {
        if (initialUrl.isNotBlank() && initialUrl != currentUrl) {
            loadTargetUrl(initialUrl)
        }
    }

    fun performSearchOrNavigate() {
        val trimmed = urlInput.trim()
        val target = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("file://") && !trimmed.startsWith("offline://")) {
            if (trimmed.contains(".")) "https://$trimmed" else "https://duckduckgo.com/?q=$trimmed"
        } else if (trimmed.startsWith("offline://")) {
            "${OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN}/${trimmed.removePrefix("offline://")}"
        } else trimmed
        loadTargetUrl(target)
    }

    // Quick Dial Bookmarks list
    val quickDials = remember {
        listOf(
            "Wikipedia" to "https://en.m.wikipedia.org",
            "Archive.org" to "https://archive.org",
            "Hacker News" to "https://news.ycombinator.com",
            "Project Gutenberg" to "https://www.gutenberg.org"
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation & Action Bar
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    // Home button
                    IconButton(
                        onClick = {
                            loadTargetUrl(DEFAULT_HOME_URL)
                        }
                    ) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Home Page",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = { webViewInstance?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }

                    // URL address field
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { performSearchOrNavigate() }),
                        trailingIcon = {
                            IconButton(onClick = { performSearchOrNavigate() }) {
                                Icon(Icons.Default.Search, contentDescription = "Go")
                            }
                        }
                    )

                    // AMOLED Dark Reader Mode toggle
                    IconButton(onClick = {
                        isAmoledDarkInjected = !isAmoledDarkInjected
                        if (isAmoledDarkInjected) {
                            injectAmoledDark(webViewInstance)
                        } else {
                            removeAmoledDark(webViewInstance)
                        }
                    }) {
                        Icon(
                            if (isAmoledDarkInjected) Icons.Default.DarkMode else Icons.Default.LightMode,
                            contentDescription = "AMOLED Dark Reader Mode",
                            tint = if (isAmoledDarkInjected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Data saver image toggle
                    IconButton(onClick = {
                        isImagesBlocked = !isImagesBlocked
                        webViewInstance?.settings?.loadsImagesAutomatically = !isImagesBlocked
                        webViewInstance?.reload()
                    }) {
                        Icon(
                            if (isImagesBlocked) Icons.Default.HideImage else Icons.Default.Image,
                            contentDescription = "Toggle Images",
                            tint = if (isImagesBlocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 1-Tap Save Single Page (MHTML)
                    IconButton(onClick = {
                        scope.launch {
                            val archiveDir = storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES)
                            val cleanTitle = pageTitle.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(40)
                            val archiveFile = File(archiveDir, "${cleanTitle}_${System.currentTimeMillis()}.mht")

                            webViewInstance?.saveWebArchive(archiveFile.absolutePath, false) { path ->
                                if (path != null) {
                                    scope.launch {
                                        archiveDao.insertArchive(
                                            ArchiveEntity(
                                                title = pageTitle,
                                                originalUrl = currentUrl,
                                                localMhtmlPath = path,
                                                fileSizeBytes = File(path).length()
                                            )
                                        )
                                        Toast.makeText(context, "Page Saved to Offline Vault! 📰", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Failed to save offline archive", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }) {
                        Icon(Icons.Default.Save, contentDescription = "Save Single Page Offline")
                    }

                    // Deep Site & Links Crawler Button
                    IconButton(onClick = { showDeepCrawlDialog = true }) {
                        Icon(
                            Icons.Default.TravelExplore,
                            contentDescription = "Deep Site & Links Downloader",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Send link to Video Downloader
                    IconButton(onClick = { onSendToDownloader(currentUrl) }) {
                        Icon(Icons.Default.Download, contentDescription = "Download with yt-dlp")
                    }
                }

                // Quick Dial Bookmarks Bar for Instant Low-Data Access
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickDials.forEach { (label, url) ->
                        AssistChip(
                            onClick = { loadTargetUrl(url) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Active Deep Crawl Notification Banner
        if (isCrawling && crawlProgress != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🌐 Deep Crawling: ${crawlProgress!!.pagesCompleted} / ${crawlProgress!!.totalDiscovered} Pages",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = StorageBreakdown.formatBytes(crawlProgress!!.bytesSaved),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Current: ${crawlProgress!!.currentUrl}",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // WebView Container
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    val webSettings = this.settings
                    webSettings.javaScriptEnabled = true
                    webSettings.domStorageEnabled = true
                    webSettings.allowFileAccess = true
                    webSettings.allowContentAccess = true
                    webSettings.mediaPlaybackRequiresUserGesture = false // Enables HTML5 audio & sound playback
                    webSettings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    webSettings.loadsImagesAutomatically = !isImagesBlocked

                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onPermissionRequest(request: android.webkit.PermissionRequest?) {
                            if (request?.origin?.toString()?.startsWith(OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN) == true) {
                                request.grant(request.resources)
                            } else {
                                request?.deny()
                            }
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            val reqUrl = request?.url?.toString() ?: ""
                            if (settings.adBlockEnabled && AdBlockEngine.isAdOrTracker(reqUrl)) {
                                return AdBlockEngine.createEmptyResponse()
                            }

                            val archivesDir = storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES)
                            val offlineResponse = OfflineArchiveSchemeHandler.handleRequest(archivesDir, reqUrl)
                            if (offlineResponse != null) {
                                return offlineResponse
                            }

                            return super.shouldInterceptRequest(view, request)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            url?.let {
                                currentUrl = it
                                urlInput = if (it.startsWith(OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN)) {
                                    "offline://${it.removePrefix(OfflineArchiveSchemeHandler.VIRTUAL_DOMAIN + "/")}"
                                } else {
                                    it
                                }
                            }
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            canGoBack = view?.canGoBack() == true
                            canGoForward = view?.canGoForward() == true
                            pageTitle = view?.title ?: "Web Page"
                            if (isAmoledDarkInjected) {
                                injectAmoledDark(view)
                            }
                        }
                    }
                    loadTargetUrl(initialUrl)
                }
            },
            update = { view ->
                view.settings.loadsImagesAutomatically = !isImagesBlocked
                if (initialUrl.isNotBlank() && view.url != initialUrl && currentUrl == initialUrl) {
                    loadTargetUrl(initialUrl)
                }
            }
        )
    }

    // Deep Site Crawler Configuration Modal
    if (showDeepCrawlDialog) {
        AlertDialog(
            onDismissRequest = { showDeepCrawlDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.TravelExplore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Deep Site & Links Downloader")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Download this entire page and linked sub-pages for 100% offline browsing.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Depth Selection
                    Text("Crawl Link Depth:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            0 to "Level 0 (Page Only)",
                            1 to "Level 1 (Direct Links)",
                            2 to "Level 2 (Deep Links)"
                        ).forEach { (d, label) ->
                            FilterChip(
                                selected = crawlDepth == d,
                                onClick = { crawlDepth = d },
                                label = { Text(if (d == 1) "1 (Links)" else if (d == 0) "0 (Single)" else "2 (Deep)") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Max Pages Budget
                    Text("Max Pages Cap:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(10, 25, 50, 100).forEach { cap ->
                            FilterChip(
                                selected = maxPagesCap == cap,
                                onClick = { maxPagesCap = cap },
                                label = { Text("$cap pgs") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Image & Data Saving Mode
                    Text("Image Quality & Data Saver:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Column {
                        ImageQualityOption.entries.forEach { opt ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                RadioButton(
                                    selected = imageQuality == opt,
                                    onClick = { imageQuality = opt }
                                )
                                Column(modifier = Modifier.padding(start = 4.dp)) {
                                    Text(opt.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                                    Text(opt.description, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Stay on same domain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Stay strictly on same domain", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = sameDomainOnly,
                            onCheckedChange = { sameDomainOnly = it }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeepCrawlDialog = false
                        isCrawling = true
                        scope.launch {
                            val config = CrawlConfig(
                                startUrl = currentUrl,
                                maxDepth = crawlDepth,
                                maxPages = maxPagesCap,
                                sameDomainOnly = sameDomainOnly,
                                imageOption = imageQuality
                            )
                            val result = siteCrawler.crawlAndSave(config) { progress ->
                                crawlProgress = progress
                            }
                            isCrawling = false
                            if (result.isSuccess) {
                                val archive = result.getOrNull()
                                Toast.makeText(
                                    context,
                                    "Successfully saved ${archive?.pageCount} pages (${StorageBreakdown.formatBytes(archive?.fileSizeBytes ?: 0L)}) to Vault! 🌐",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(
                                    context,
                                    "Deep download failed: ${result.exceptionOrNull()?.localizedMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Start Deep Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeepCrawlDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
