package com.offlinevault.app.ui.screens.podcasts

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.EpisodeEntity
import com.offlinevault.app.data.local.entity.FeedEntity
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.service.audio.AudioCompressor
import com.offlinevault.app.service.podcast.*
import com.prof18.rssparser.RssParserBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PodcastTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    EPISODES("Episodes", Icons.Default.Headphones),
    SUBSCRIPTIONS("Subscriptions", Icons.Default.Subscriptions),
    DISCOVER("Discover", Icons.Default.Explore)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val db = remember { AppDatabase.getInstance(context) }
    val feeds by db.feedDao().getAllFeeds().collectAsState(initial = emptyList())

    var currentTab by remember { mutableStateOf(PodcastTab.EPISODES) }
    var showAddFeedDialog by remember { mutableStateOf(false) }
    var showDiscoverDialog by remember { mutableStateOf(false) }
    var feedUrlInput by remember { mutableStateOf("") }
    var selectedFeedId by remember { mutableStateOf<Long?>(null) }
    var episodeSearchQuery by remember { mutableStateOf("") }
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }

    // Intercept hardware back button & system edge back gestures
    BackHandler(
        enabled = showAddFeedDialog ||
                showDiscoverDialog ||
                selectedFeedId != null ||
                episodeSearchQuery.isNotBlank() ||
                currentTab != PodcastTab.EPISODES
    ) {
        when {
            showAddFeedDialog -> showAddFeedDialog = false
            showDiscoverDialog -> showDiscoverDialog = false
            selectedFeedId != null -> selectedFeedId = null
            episodeSearchQuery.isNotBlank() -> episodeSearchQuery = ""
            currentTab != PodcastTab.EPISODES -> currentTab = PodcastTab.EPISODES
        }
    }

    val storageTracker = remember { StorageTracker(context) }
    val audioCompressor = remember { AudioCompressor(context) }
    val discoveryService = remember { PodcastDiscoveryService() }

    val activeFeedEpisodes by remember(selectedFeedId) {
        if (selectedFeedId != null) {
            db.feedDao().getEpisodesForFeed(selectedFeedId!!)
        } else {
            db.feedDao().getDownloadedEpisodes()
        }
    }.collectAsState(initial = emptyList())

    val displayedEpisodes = remember(activeFeedEpisodes, episodeSearchQuery) {
        if (episodeSearchQuery.isBlank()) {
            activeFeedEpisodes
        } else {
            val q = episodeSearchQuery.trim()
            activeFeedEpisodes.filter { ep ->
                ep.title.contains(q, ignoreCase = true) || (ep.description?.contains(q, ignoreCase = true) == true)
            }
        }
    }

    val selectedFeed = remember(feeds, selectedFeedId) {
        feeds.firstOrNull { it.id == selectedFeedId }
    }

    fun unsubscribeFeed(feed: FeedEntity) {
        scope.launch {
            // Delete local files of downloaded episodes for this feed
            withContext(Dispatchers.IO) {
                db.feedDao().deleteEpisodesForFeed(feed.id)
                db.feedDao().deleteFeed(feed)
            }
            if (selectedFeedId == feed.id) {
                selectedFeedId = null
            }
            Toast.makeText(context, "Unsubscribed from '${feed.title}'", Toast.LENGTH_SHORT).show()
        }
    }

    fun syncAllFeeds() {
        if (feeds.isEmpty()) return
        scope.launch {
            isSyncing = true
            syncMessage = "Syncing unique episode artwork..."
            try {
                val parser = RssParserBuilder().build()
                for (feed in feeds) {
                    syncMessage = "Refreshing ${feed.title.take(20)}..."
                    try {
                        val channel = withContext(Dispatchers.IO) { parser.getRssChannel(feed.feedUrl) }
                        val feedArtwork = channel.image?.url ?: feed.imageUrl
                        val existingEpisodes = withContext(Dispatchers.IO) { db.feedDao().getEpisodesListForFeed(feed.id) }
                        val existingMap = existingEpisodes.associateBy { it.audioUrl }

                        val updatedEpisodes = mutableListOf<EpisodeEntity>()
                        val newEpisodes = mutableListOf<EpisodeEntity>()

                        for (item in channel.items) {
                            val audioUrl = item.audio ?: item.link ?: continue
                            val epArtwork = extractEpisodeArtwork(item, feedArtwork)
                            val existing = existingMap[audioUrl]
                            if (existing != null) {
                                if (existing.imageUrl != epArtwork && !epArtwork.isNullOrBlank()) {
                                    updatedEpisodes.add(existing.copy(imageUrl = epArtwork))
                                }
                            } else {
                                val parsedPubDate = parseRssDateToEpoch(item.pubDate)
                                newEpisodes.add(
                                    EpisodeEntity(
                                        feedId = feed.id,
                                        title = item.title ?: "Episode",
                                        description = item.description,
                                        audioUrl = audioUrl,
                                        pubDate = parsedPubDate,
                                        imageUrl = epArtwork
                                    )
                                )
                            }
                        }
                        if (updatedEpisodes.isNotEmpty()) {
                            withContext(Dispatchers.IO) { db.feedDao().updateEpisodes(updatedEpisodes) }
                        }
                        if (newEpisodes.isNotEmpty()) {
                            withContext(Dispatchers.IO) { db.feedDao().insertEpisodes(newEpisodes) }
                        }
                    } catch (_: Exception) {}
                }
                Toast.makeText(context, "Episode artwork updated!", Toast.LENGTH_SHORT).show()
            } finally {
                isSyncing = false
                syncMessage = ""
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = if (PodcastPlayerManager.currentEpisode != null) 72.dp else 0.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Top Header: Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Podcasts & Audio",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Built-in player, offline shrink & subscriptions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (feeds.isNotEmpty()) {
                        IconButton(onClick = { syncAllFeeds() }, enabled = !isSyncing) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync Feeds & Artwork")
                        }
                    }
                    FilledTonalButton(onClick = { showDiscoverDialog = true }) {
                        Icon(Icons.Default.Explore, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Discover")
                    }
                    Button(onClick = { showAddFeedDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Subscribe")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Navigation Tabs: Episodes, Subscriptions, Discover
            TabRow(
                selectedTabIndex = currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                PodcastTab.entries.forEach { tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = {
                            if (tab == PodcastTab.DISCOVER) {
                                showDiscoverDialog = true
                            } else {
                                currentTab = tab
                            }
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    when (tab) {
                                        PodcastTab.SUBSCRIPTIONS -> "Subscriptions (${feeds.size})"
                                        else -> tab.label
                                    }
                                )
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Syncing / Progress banner
            if (isSyncing) {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    if (syncMessage.isNotBlank()) {
                        Text(
                            text = syncMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Tab 1: Subscriptions View (List of all subscribed podcasts with Unsubscribe button)
            if (currentTab == PodcastTab.SUBSCRIPTIONS) {
                SubscriptionsListContent(
                    feeds = feeds,
                    onBrowseFeed = { feed ->
                        selectedFeedId = feed.id
                        currentTab = PodcastTab.EPISODES
                    },
                    onUnsubscribe = { feed -> unsubscribeFeed(feed) },
                    onAddNewFeed = { showAddFeedDialog = true },
                    onDiscover = { showDiscoverDialog = true }
                )
            } else {
                // Tab 0: Episodes View (with feed chips, search, and audio player)
                EpisodesViewContent(
                    feeds = feeds,
                    selectedFeed = selectedFeed,
                    selectedFeedId = selectedFeedId,
                    displayedEpisodes = displayedEpisodes,
                    totalDownloadedCount = activeFeedEpisodes.count { it.isDownloaded },
                    episodeSearchQuery = episodeSearchQuery,
                    onSearchQueryChange = { episodeSearchQuery = it },
                    onSelectFeed = { feedId ->
                        selectedFeedId = feedId
                        episodeSearchQuery = ""
                    },
                    onUnsubscribeFeed = { feed -> unsubscribeFeed(feed) },
                    onPlayEpisode = { ep ->
                        val feed = feeds.firstOrNull { it.id == ep.feedId }
                        val feedTitle = feed?.title
                        val artwork = ep.imageUrl ?: feed?.imageUrl
                        PodcastPlayerManager.playEpisode(context, ep, feedTitle, artwork, db)
                    },
                    onDownloadEpisode = { ep, compressAfter ->
                        scope.launch {
                            isSyncing = true
                            syncMessage = "Downloading ${ep.title.take(25)}..."
                            downloadAndProcessEpisode(
                                context = context,
                                episode = ep,
                                compressAfter = compressAfter,
                                storageTracker = storageTracker,
                                audioCompressor = audioCompressor,
                                db = db
                            )
                            isSyncing = false
                            syncMessage = ""
                        }
                    },
                    onCompressExisting = { ep ->
                        scope.launch {
                            isSyncing = true
                            syncMessage = "Shrinking ${ep.title.take(25)} to 32kbps mono..."
                            compressExistingEpisode(
                                context = context,
                                episode = ep,
                                audioCompressor = audioCompressor,
                                db = db
                            )
                            isSyncing = false
                            syncMessage = ""
                        }
                    },
                    onDiscover = { showDiscoverDialog = true }
                )
            }
        }

        // Docked Mini-Player Bar at bottom
        AnimatedVisibility(
            visible = PodcastPlayerManager.currentEpisode != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            PodcastMiniPlayer()
        }

        // Expanded Full Player Dialog (if user taps mini player)
        if (PodcastPlayerManager.showExpandedPlayer) {
            PodcastExpandedPlayerDialog()
        }
    }

    // 1. Discover & Search Podcasts Dialog
    if (showDiscoverDialog) {
        DiscoverPodcastsDialog(
            discoveryService = discoveryService,
            subscribedUrls = feeds.map { it.feedUrl }.toSet(),
            onDismiss = { showDiscoverDialog = false },
            onSubscribe = { podcast ->
                isSyncing = true
                syncMessage = "Subscribing to ${podcast.title}..."
                scope.launch {
                    try {
                        val parser = RssParserBuilder().build()
                        val channel = withContext(Dispatchers.IO) { parser.getRssChannel(podcast.feedUrl) }
                        val feedArtwork = channel.image?.url ?: podcast.artworkUrl
                        val feedId = db.feedDao().insertFeed(
                            FeedEntity(
                                title = channel.title ?: podcast.title,
                                feedUrl = podcast.feedUrl,
                                description = channel.description,
                                imageUrl = feedArtwork,
                                isPodcast = true
                            )
                        )
                        val episodes = channel.items.mapNotNull { item ->
                            val audioUrl = item.audio ?: item.link ?: return@mapNotNull null
                            val parsedPubDate = parseRssDateToEpoch(item.pubDate)
                            EpisodeEntity(
                                feedId = feedId,
                                title = item.title ?: "Episode",
                                description = item.description,
                                audioUrl = audioUrl,
                                pubDate = parsedPubDate,
                                imageUrl = extractEpisodeArtwork(item, feedArtwork)
                            )
                        }
                        db.feedDao().insertEpisodes(episodes)
                        selectedFeedId = feedId
                        currentTab = PodcastTab.EPISODES
                        Toast.makeText(context, "Subscribed to '${podcast.title}' (${episodes.size} episodes)", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Subscription failed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                    } finally {
                        isSyncing = false
                        syncMessage = ""
                    }
                }
            },
            onUnsubscribeUrl = { feedUrl ->
                scope.launch {
                    val feed = feeds.firstOrNull { it.feedUrl == feedUrl }
                    if (feed != null) {
                        unsubscribeFeed(feed)
                    }
                }
            }
        )
    }

    // 2. Direct RSS Feed URL Dialog
    if (showAddFeedDialog) {
        AlertDialog(
            onDismissRequest = { showAddFeedDialog = false },
            title = { Text("Subscribe to RSS or Podcast Feed URL") },
            text = {
                Column {
                    Text(
                        text = "Enter any direct RSS or podcast XML feed URL to subscribe and sync all episodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = feedUrlInput,
                        onValueChange = { feedUrlInput = it },
                        label = { Text("Feed URL") },
                        placeholder = { Text("https://example.com/feed.xml") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = feedUrlInput.trim()
                        if (url.isNotBlank()) {
                            showAddFeedDialog = false
                            isSyncing = true
                            syncMessage = "Parsing feed..."
                            scope.launch {
                                try {
                                    val parser = RssParserBuilder().build()
                                    val channel = withContext(Dispatchers.IO) { parser.getRssChannel(url) }
                                    val feedArtwork = channel.image?.url
                                    val feedId = db.feedDao().insertFeed(
                                        FeedEntity(
                                            title = channel.title ?: "New Feed",
                                            feedUrl = url,
                                            description = channel.description,
                                            imageUrl = feedArtwork,
                                            isPodcast = channel.items.any { it.audio != null }
                                        )
                                    )
                                    val episodes = channel.items.mapNotNull { item ->
                                        val audioUrl = item.audio ?: item.link ?: return@mapNotNull null
                                        val parsedPubDate = parseRssDateToEpoch(item.pubDate)
                                        EpisodeEntity(
                                            feedId = feedId,
                                            title = item.title ?: "Episode",
                                            description = item.description,
                                            audioUrl = audioUrl,
                                            pubDate = parsedPubDate,
                                            imageUrl = extractEpisodeArtwork(item, feedArtwork)
                                        )
                                    }
                                    db.feedDao().insertEpisodes(episodes)
                                    selectedFeedId = feedId
                                    currentTab = PodcastTab.EPISODES
                                    Toast.makeText(context, "Subscribed to '${channel.title}' with ${episodes.size} episodes!", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Failed to parse feed: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                } finally {
                                    isSyncing = false
                                    syncMessage = ""
                                    feedUrlInput = ""
                                }
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Subscribe")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFeedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SubscriptionsListContent(
    feeds: List<FeedEntity>,
    onBrowseFeed: (FeedEntity) -> Unit,
    onUnsubscribe: (FeedEntity) -> Unit,
    onAddNewFeed: () -> Unit,
    onDiscover: () -> Unit
) {
    if (feeds.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    Icons.Default.Subscriptions,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Podcast Subscriptions Yet",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Subscribe to podcasts to automatically sync episodes, shrink audio files, and play offline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onDiscover) {
                        Icon(Icons.Default.Explore, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Discover Podcasts")
                    }
                    OutlinedButton(onClick = onAddNewFeed) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add URL")
                    }
                }
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Subscribed Podcasts (${feeds.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAddNewFeed) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add URL")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(feeds, key = { it.id }) { feed ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val channelArtwork = feed.imageUrl
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!channelArtwork.isNullOrBlank()) {
                                        AsyncImage(
                                            model = channelArtwork,
                                            contentDescription = feed.title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        Icon(
                                            Icons.Default.Podcasts,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = feed.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    feed.description?.let { desc ->
                                        Text(
                                            text = desc.replace(Regex("<[^>]*>"), ""),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Text(
                                        text = feed.feedUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // Action buttons: Browse Episodes & Unsubscribe Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AssistChip(
                                    onClick = {},
                                    label = { Text("✓ Subscribed", style = MaterialTheme.typography.labelSmall) },
                                    leadingIcon = {
                                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = { onUnsubscribe(feed) },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Unsubscribe")
                                    }

                                    Button(
                                        onClick = { onBrowseFeed(feed) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                    ) {
                                        Text("Episodes")
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodesViewContent(
    feeds: List<FeedEntity>,
    selectedFeed: FeedEntity?,
    selectedFeedId: Long?,
    displayedEpisodes: List<EpisodeEntity>,
    totalDownloadedCount: Int,
    episodeSearchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectFeed: (Long?) -> Unit,
    onUnsubscribeFeed: (FeedEntity) -> Unit,
    onPlayEpisode: (EpisodeEntity) -> Unit,
    onDownloadEpisode: (EpisodeEntity, compressAfter: Boolean) -> Unit,
    onCompressExisting: (EpisodeEntity) -> Unit,
    onDiscover: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Feeds horizontally scrollable filter chips
        if (feeds.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFeedId == null,
                    onClick = { onSelectFeed(null) },
                    label = { Text("Downloaded ($totalDownloadedCount)") }
                )
                feeds.forEach { feed ->
                    FilterChip(
                        selected = selectedFeedId == feed.id,
                        onClick = { onSelectFeed(feed.id) },
                        label = { Text(feed.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Active Feed Banner with Search Bar & Explicit Unsubscribe Button
        if (selectedFeed != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                OutlinedTextField(
                    value = episodeSearchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Search in ${selectedFeed.title.take(18)}...", style = MaterialTheme.typography.bodySmall) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (episodeSearchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(50.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Prominent Unsubscribe button for active feed
                OutlinedButton(
                    onClick = { onUnsubscribeFeed(selectedFeed) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Unsubscribe", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Unsubscribe", style = MaterialTheme.typography.labelSmall)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Episodes List
        if (displayedEpisodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Podcasts,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (episodeSearchQuery.isNotBlank()) {
                            "No episodes match '$episodeSearchQuery'"
                        } else if (selectedFeedId == null) {
                            "No downloaded episodes yet.\nDiscover podcasts or select a feed to play & download!"
                        } else {
                            "No episodes found in this feed."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (selectedFeedId == null) {
                        Button(onClick = onDiscover) {
                            Icon(Icons.Default.Explore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Discover New Podcasts")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(displayedEpisodes, key = { it.id }) { ep ->
                    EpisodeCard(
                        episode = ep,
                        onPlay = { onPlayEpisode(ep) },
                        onDownload = { shouldCompress -> onDownloadEpisode(ep, shouldCompress) },
                        onCompressExisting = { onCompressExisting(ep) }
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(
    episode: EpisodeEntity,
    onPlay: () -> Unit,
    onDownload: (compressAfter: Boolean) -> Unit,
    onCompressExisting: () -> Unit
) {
    val isCurrentlyActive = PodcastPlayerManager.currentEpisode?.id == episode.id
    val isPlaying = isCurrentlyActive && PodcastPlayerManager.isPlaying

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentlyActive)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            else MaterialTheme.colorScheme.surface
        ),
        border = if (isCurrentlyActive)
            androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Episode artwork thumbnail
                val epArtwork = episode.imageUrl
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!epArtwork.isNullOrBlank()) {
                        AsyncImage(
                            model = epArtwork,
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play / Pause Built-in Player Button
                FilledTonalIconButton(
                    onClick = onPlay,
                    modifier = Modifier.size(44.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = if (isCurrentlyActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = if (isCurrentlyActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause Episode" else "Play Episode",
                        modifier = Modifier.size(26.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val dateStr = remember(episode.pubDate) {
                        if (episode.pubDate > 0) {
                            SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(episode.pubDate))
                        } else ""
                    }
                    val formattedSize = if (episode.fileSizeBytes > 0) {
                        formatBytes(episode.fileSizeBytes)
                    } else ""

                    val statusText = if (episode.isDownloaded) {
                        val isM4a = episode.localAudioPath?.endsWith(".m4a") == true
                        if (isM4a) "✓ Offline (Shrunk ~80% Space): $formattedSize"
                        else "✓ Offline (Original): $formattedSize"
                    } else {
                        "Stream or Download"
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (episode.isDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (dateStr.isNotBlank()) {
                            Text(
                                text = dateStr,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Download & Shrink Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!episode.isDownloaded) {
                    OutlinedButton(
                        onClick = { onDownload(false) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onDownload(true) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download & Shrink")
                    }
                } else {
                    val isAlreadyCompressed = episode.localAudioPath?.endsWith(".m4a") == true
                    if (!isAlreadyCompressed) {
                        FilledTonalButton(
                            onClick = onCompressExisting,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shrink (Save ~80%)")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Saved Offline",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverPodcastsDialog(
    discoveryService: PodcastDiscoveryService,
    subscribedUrls: Set<String>,
    onDismiss: () -> Unit,
    onSubscribe: (DiscoveredPodcast) -> Unit,
    onUnsubscribeUrl: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchQuery by remember { mutableStateOf("") }
    var rawPodcasts by remember { mutableStateOf<List<DiscoveredPodcast>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    var selectedSortOrder by remember { mutableStateOf(PodcastSortOrder.RECENTLY_UPDATED) }
    var selectedDateFilter by remember { mutableStateOf(PodcastDateFilter.ALL) }
    var showSortMenu by remember { mutableStateOf(false) }

    val presetCategories = listOf(
        "🔥 Top Trending",
        "Technology",
        "Science",
        "News",
        "History",
        "True Crime",
        "Comedy",
        "Health & Fitness"
    )

    fun executeSearch(term: String) {
        isSearching = true
        scope.launch {
            val results = if (term == "🔥 Top Trending" || term.isBlank()) {
                discoveryService.getTopPodcasts(limit = 40)
            } else {
                discoveryService.searchPodcasts(term, limit = 50)
            }
            rawPodcasts = results
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        executeSearch("🔥 Top Trending")
    }

    val filteredAndSortedPodcasts by remember(rawPodcasts, selectedSortOrder, selectedDateFilter) {
        derivedStateOf {
            val nowMs = System.currentTimeMillis()
            var list = rawPodcasts

            selectedDateFilter.minDaysAgo?.let { days ->
                val thresholdMs = nowMs - (days.toLong() * 24L * 60L * 60L * 1000L)
                list = list.filter { it.releaseDateEpochMs >= thresholdMs }
            }

            when (selectedSortOrder) {
                PodcastSortOrder.RECENTLY_UPDATED -> list.sortedByDescending { it.releaseDateEpochMs }
                PodcastSortOrder.EPISODE_COUNT -> list.sortedByDescending { it.episodeCount }
                PodcastSortOrder.POPULARITY -> list
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.90f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
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
                            text = "Find & Discover Podcasts",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Search directory & trending charts with 1-tap subscribe",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search podcasts, creators, or topics") },
                    placeholder = { Text("e.g. Huberman, Joe Rogan, Tech...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { executeSearch(searchQuery) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Search")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetCategories.forEach { category ->
                        SuggestionChip(
                            onClick = {
                                searchQuery = if (category.startsWith("🔥")) "" else category
                                executeSearch(category)
                            },
                            label = { Text(category) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PodcastDateFilter.entries.forEach { filter ->
                            FilterChip(
                                selected = selectedDateFilter == filter,
                                onClick = { selectedDateFilter = filter },
                                label = { Text(filter.label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            PodcastSortOrder.entries.forEach { order ->
                                DropdownMenuItem(
                                    text = { Text(order.label) },
                                    leadingIcon = if (selectedSortOrder == order) {
                                        { Icon(Icons.Default.Check, contentDescription = null) }
                                    } else null,
                                    onClick = {
                                        selectedSortOrder = order
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                Text(
                    text = "Showing ${filteredAndSortedPodcasts.size} podcasts • Sorted by ${selectedSortOrder.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                if (isSearching) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (filteredAndSortedPodcasts.isEmpty() && !isSearching) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = if (rawPodcasts.isNotEmpty()) {
                                    "No podcasts matched the date filter '${selectedDateFilter.label}'.\nTry switching to 'All Time'."
                                } else {
                                    "No podcasts found for '$searchQuery'.\nTry another search keyword!"
                                },
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            if (selectedDateFilter != PodcastDateFilter.ALL) {
                                Spacer(modifier = Modifier.height(8.dp))
                                TextButton(onClick = { selectedDateFilter = PodcastDateFilter.ALL }) {
                                    Text("Reset Date Filter")
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredAndSortedPodcasts) { podcast ->
                            val isSubscribed = subscribedUrls.contains(podcast.feedUrl)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Podcast Artwork thumbnail
                                    val podcastArtwork = podcast.artworkUrl
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!podcastArtwork.isNullOrBlank()) {
                                            AsyncImage(
                                                model = podcastArtwork,
                                                contentDescription = podcast.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.Podcasts,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = podcast.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = podcast.author,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            podcast.genre?.let {
                                                Text(
                                                    text = it,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            if (podcast.episodeCount > 0) {
                                                Text(
                                                    text = "• ${podcast.episodeCount} eps",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline
                                                )
                                            }
                                            if (podcast.releaseDateFormatted.isNotBlank()) {
                                                Text(
                                                    text = "• ${podcast.releaseDateFormatted}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.tertiary
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(8.dp))

                                    // Subscribe / Unsubscribe Button
                                    if (isSubscribed) {
                                        OutlinedButton(
                                            onClick = { onUnsubscribeUrl(podcast.feedUrl) },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Unsubscribe", style = MaterialTheme.typography.labelSmall)
                                        }
                                    } else {
                                        Button(
                                            onClick = { onSubscribe(podcast) },
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Subscribe")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun parseRssDateToEpoch(pubDateStr: String?): Long {
    if (pubDateStr.isNullOrBlank()) return System.currentTimeMillis()
    val formats = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    )
    for (f in formats) {
        try {
            val d = f.parse(pubDateStr)
            if (d != null) return d.time
        } catch (_: Exception) {}
    }
    return System.currentTimeMillis()
}

private suspend fun downloadAndProcessEpisode(
    context: android.content.Context,
    episode: EpisodeEntity,
    compressAfter: Boolean,
    storageTracker: StorageTracker,
    audioCompressor: AudioCompressor,
    db: AppDatabase
) = withContext(Dispatchers.IO) {
    try {
        val podcastDir = storageTracker.getCategoryDir(StorageCategory.PODCASTS)
        val cleanTitle = episode.title.replace(Regex("[^a-zA-Z0-9.-]"), "_").take(40)
        val targetFile = File(podcastDir, "${cleanTitle}_${episode.id}.mp3")

        val connection = URL(episode.audioUrl).openConnection()
        val inputStream = connection.getInputStream()
        val outputStream = FileOutputStream(targetFile)

        inputStream.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }

        var finalPath = targetFile.absolutePath
        var finalSize = targetFile.length()

        if (compressAfter) {
            val compressionResult = audioCompressor.compressAudio(targetFile)
            if (compressionResult.success) {
                targetFile.delete()
                finalPath = compressionResult.outputFilePath
                finalSize = compressionResult.compressedSizeBytes
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Shrunk from ${formatBytes(compressionResult.originalSizeBytes)} to ${formatBytes(finalSize)} (${compressionResult.savedPercentage}% saved!)",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        db.feedDao().updateEpisode(
            episode.copy(
                isDownloaded = true,
                localAudioPath = finalPath,
                fileSizeBytes = finalSize
            )
        )

        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Episode Saved Offline! 🎙️", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Download failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

private suspend fun compressExistingEpisode(
    context: android.content.Context,
    episode: EpisodeEntity,
    audioCompressor: AudioCompressor,
    db: AppDatabase
) = withContext(Dispatchers.IO) {
    val localPath = episode.localAudioPath ?: return@withContext
    val file = File(localPath)
    if (!file.exists()) return@withContext

    val result = audioCompressor.compressAudio(file)
    if (result.success) {
        file.delete()
        db.feedDao().updateEpisode(
            episode.copy(
                localAudioPath = result.outputFilePath,
                fileSizeBytes = result.compressedSizeBytes
            )
        )
        withContext(Dispatchers.Main) {
            Toast.makeText(
                context,
                "Shrunk from ${formatBytes(result.originalSizeBytes)} to ${formatBytes(result.compressedSizeBytes)} (${result.savedPercentage}% space saved!)",
                Toast.LENGTH_LONG
            ).show()
        }
    } else {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Shrink failed: ${result.errorMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun extractEpisodeArtwork(
    item: com.prof18.rssparser.model.RssItem,
    fallbackArtwork: String?
): String? {
    // 1. iTunes episode image tag <itunes:image href="..." /> (unique per episode)
    val itunesImg = item.itunesItemData?.image?.trim()
    if (!itunesImg.isNullOrBlank()) return itunesImg

    // 2. Standard RSS enclosure or media:thumbnail
    val standardImg = item.image?.trim()
    if (!standardImg.isNullOrBlank()) return standardImg

    // 3. Search for <img src="..." in description or content
    val html = item.content ?: item.description
    if (!html.isNullOrBlank()) {
        val match = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(html)
        val src = match?.groupValues?.getOrNull(1)?.trim()
        if (!src.isNullOrBlank()) return src
    }

    // 4. Fallback to channel artwork
    return fallbackArtwork
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
        else -> "$bytes B"
    }
}

