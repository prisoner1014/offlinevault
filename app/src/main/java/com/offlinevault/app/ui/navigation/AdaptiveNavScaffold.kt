package com.offlinevault.app.ui.navigation

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.service.crawler.OfflineArchiveSchemeHandler
import com.offlinevault.app.service.podcast.PodcastPlayerManager
import com.offlinevault.app.ui.screens.books.BookReaderScreen
import com.offlinevault.app.ui.screens.books.BooksScreen
import com.offlinevault.app.ui.screens.browser.LowDataBrowserScreen
import com.offlinevault.app.ui.screens.downloader.DownloaderScreen
import com.offlinevault.app.ui.screens.player.VideoPlayerScreen
import com.offlinevault.app.ui.screens.podcasts.PodcastExpandedPlayerDialog
import com.offlinevault.app.ui.screens.podcasts.PodcastMiniPlayer
import com.offlinevault.app.ui.screens.podcasts.PodcastsScreen
import com.offlinevault.app.ui.screens.settings.SettingsScreen
import com.offlinevault.app.ui.screens.vault.VaultScreen
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.offlinevault.app.service.ai.AiModelManager
import com.offlinevault.app.service.ai.OfflineAiEngine
import com.offlinevault.app.ui.screens.about.AboutScreen
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.offlinevault.app.ui.screens.ai.AiAssistantScreen
import com.offlinevault.app.ui.screens.games.GameDefinition
import com.offlinevault.app.ui.screens.games.GamePlayScreen
import com.offlinevault.app.ui.screens.games.GamesScreen
import com.offlinevault.app.util.FileSharingUtils
import java.io.File

enum class NavDestination(val label: String, val icon: ImageVector) {
    VAULT("Vault", Icons.Default.FolderSpecial),
    DOWNLOADER("Downloader", Icons.Default.Download),
    PODCASTS("Podcasts", Icons.Default.Podcasts),
    BOOKS("Books", Icons.AutoMirrored.Filled.MenuBook),
    GAMES("Games", Icons.Default.SportsEsports),
    AI("AI", Icons.Default.Psychology),
    BROWSER("Browser", Icons.Default.Language),
    SETTINGS("Settings", Icons.Default.Settings),
    ABOUT("About", Icons.Default.Info)
}

@Composable
fun AdaptiveNavScaffold(
    sharedUrl: String? = null
) {
    val context = LocalContext.current
    val storageTracker = remember { StorageTracker(context) }
    val aiModelManager = remember { AiModelManager(context) }
    val aiEngine = remember { OfflineAiEngine(context, aiModelManager) }
    var currentDestination by remember {
        mutableStateOf(if (!sharedUrl.isNullOrBlank()) NavDestination.DOWNLOADER else NavDestination.VAULT)
    }
    // Navigation history backstack so users can always back out of tabs/screens
    val navBackStack = remember { mutableStateListOf<NavDestination>() }

    fun navigateTo(destination: NavDestination) {
        if (currentDestination != destination) {
            navBackStack.add(currentDestination)
            currentDestination = destination
        }
    }

    var prefilledDownloaderUrl by remember { mutableStateOf(sharedUrl ?: "") }
    var currentBrowserUrl by remember { mutableStateOf("https://duckduckgo.com") }

    // State for built-in video player
    var activeVideoPlaying by remember { mutableStateOf<Pair<String, String>?>(null) } // (filePath, title)

    // State for built-in book reader
    var activeBookReadingId by remember { mutableStateOf<Long?>(null) }

    // State for built-in offline games
    var activeGamePlaying by remember { mutableStateOf<GameDefinition?>(null) }

    // Central Scaffold Back Gesture Handler: guarantees system edge back gestures always work
    BackHandler(
        enabled = activeGamePlaying != null ||
                activeBookReadingId != null ||
                activeVideoPlaying != null ||
                navBackStack.isNotEmpty() ||
                currentDestination != NavDestination.VAULT
    ) {
        when {
            activeGamePlaying != null -> {
                activeGamePlaying = null
            }
            activeBookReadingId != null -> {
                activeBookReadingId = null
            }
            activeVideoPlaying != null -> {
                activeVideoPlaying = null
            }
            navBackStack.isNotEmpty() -> {
                currentDestination = navBackStack.removeAt(navBackStack.lastIndex)
            }
            currentDestination != NavDestination.VAULT -> {
                currentDestination = NavDestination.VAULT
            }
        }
    }

    // If an offline game is active, show the full-screen game runner
    if (activeGamePlaying != null) {
        GamePlayScreen(
            game = activeGamePlaying!!,
            onBack = { activeGamePlaying = null }
        )
        return
    }

    // If reading a book, show full-screen built-in reader (with Fold dual-page spread)
    if (activeBookReadingId != null) {
        BookReaderScreen(
            bookId = activeBookReadingId!!,
            onBack = { activeBookReadingId = null }
        )
        return
    }

    // If video is active, show the full-screen built-in player
    if (activeVideoPlaying != null) {
        val (path, title) = activeVideoPlaying!!
        VideoPlayerScreen(
            videoPath = path,
            title = title,
            onBack = { activeVideoPlaying = null },
            onOpenExternal = { p -> FileSharingUtils.openInExternalPlayer(context, p, false) }
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isFoldUnfolded = maxWidth >= 600.dp

        if (isFoldUnfolded) {
            // Samsung Fold Unfolded Inner Screen (7.6"+): Navigation Rail + Content Area
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    modifier = Modifier.fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    NavDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = currentDestination == destination,
                            onClick = { navigateTo(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ScreenContent(
                            destination = currentDestination,
                            prefilledUrl = prefilledDownloaderUrl,
                            browserUrl = currentBrowserUrl,
                            onOpenWebArchive = { path ->
                                val archivesDir = storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES)
                                currentBrowserUrl = OfflineArchiveSchemeHandler.toVirtualUrl(archivesDir, path)
                                navigateTo(NavDestination.BROWSER)
                            },
                            onSendToDownloader = { url ->
                                prefilledDownloaderUrl = url
                                navigateTo(NavDestination.DOWNLOADER)
                            },
                            onNavigateToVault = {
                                navigateTo(NavDestination.VAULT)
                            },
                            onNavigateToGames = {
                                navigateTo(NavDestination.GAMES)
                            },
                            onSelectGame = { game ->
                                activeGamePlaying = game
                            },
                            onPlayVideo = { path, title ->
                                activeVideoPlaying = Pair(path, title)
                            },
                            onOpenBook = { bookId ->
                                activeBookReadingId = bookId
                            },
                            onNavigateToAbout = {
                                navigateTo(NavDestination.ABOUT)
                            },
                            aiModelManager = aiModelManager,
                            aiEngine = aiEngine
                        )
                    }

                    if (currentDestination != NavDestination.PODCASTS && PodcastPlayerManager.currentEpisode != null) {
                        PodcastMiniPlayer()
                    }
                }
            }
        } else {
            // Samsung Fold Cover Screen (Narrow single-hand use): Bottom Navigation Bar
            Scaffold(
                bottomBar = {
                    Column {
                        if (currentDestination != NavDestination.PODCASTS && PodcastPlayerManager.currentEpisode != null) {
                            PodcastMiniPlayer()
                        }
                        val listState = rememberLazyListState()
                        val selectedIndex = remember(currentDestination) {
                            NavDestination.entries.indexOf(currentDestination)
                        }

                        LaunchedEffect(selectedIndex) {
                            if (selectedIndex >= 0) {
                                listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            tonalElevation = 3.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            LazyRow(
                                state = listState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .navigationBarsPadding()
                                    .height(68.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                items(NavDestination.entries) { destination ->
                                    val isSelected = currentDestination == destination

                                    Surface(
                                        onClick = { navigateTo(destination) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (isSelected) {
                                            MaterialTheme.colorScheme.secondaryContainer
                                        } else {
                                            Color.Transparent
                                        },
                                        modifier = Modifier
                                            .wrapContentWidth()
                                            .fillMaxHeight()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .padding(horizontal = 14.dp, vertical = 4.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = destination.icon,
                                                contentDescription = destination.label,
                                                modifier = Modifier.size(24.dp),
                                                tint = if (isSelected) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = destination.label,
                                                maxLines = 1,
                                                softWrap = false,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.onSecondaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            ) { padding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    ScreenContent(
                        destination = currentDestination,
                        prefilledUrl = prefilledDownloaderUrl,
                        browserUrl = currentBrowserUrl,
                        onOpenWebArchive = { path ->
                            val archivesDir = storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES)
                            currentBrowserUrl = OfflineArchiveSchemeHandler.toVirtualUrl(archivesDir, path)
                            navigateTo(NavDestination.BROWSER)
                        },
                        onSendToDownloader = { url ->
                            prefilledDownloaderUrl = url
                            navigateTo(NavDestination.DOWNLOADER)
                        },
                        onNavigateToVault = {
                            navigateTo(NavDestination.VAULT)
                        },
                        onNavigateToGames = {
                            navigateTo(NavDestination.GAMES)
                        },
                        onSelectGame = { game ->
                            activeGamePlaying = game
                        },
                        onPlayVideo = { path, title ->
                            activeVideoPlaying = Pair(path, title)
                        },
                        onOpenBook = { bookId ->
                            activeBookReadingId = bookId
                        },
                        onNavigateToAbout = {
                            navigateTo(NavDestination.ABOUT)
                        },
                        aiModelManager = aiModelManager,
                        aiEngine = aiEngine
                    )
                }
            }
        }

        // Global Expanded Podcast Player Dialog
        if (PodcastPlayerManager.showExpandedPlayer) {
            PodcastExpandedPlayerDialog()
        }
    }
}

@Composable
private fun ScreenContent(
    destination: NavDestination,
    prefilledUrl: String,
    browserUrl: String,
    onOpenWebArchive: (String) -> Unit,
    onSendToDownloader: (String) -> Unit,
    onNavigateToVault: () -> Unit,
    onNavigateToGames: () -> Unit,
    onSelectGame: (GameDefinition) -> Unit,
    onPlayVideo: (path: String, title: String) -> Unit,
    onOpenBook: (bookId: Long) -> Unit,
    onNavigateToAbout: () -> Unit,
    aiModelManager: AiModelManager,
    aiEngine: OfflineAiEngine
) {
    when (destination) {
        NavDestination.VAULT -> VaultScreen(
            onOpenWebArchive = onOpenWebArchive,
            onPlayVideo = onPlayVideo,
            onOpenBook = onOpenBook,
            onNavigateToGames = onNavigateToGames
        )
        NavDestination.DOWNLOADER -> DownloaderScreen(
            prefilledUrl = prefilledUrl,
            onNavigateToVault = onNavigateToVault,
            onPlayVideo = onPlayVideo
        )
        NavDestination.PODCASTS -> PodcastsScreen()
        NavDestination.BOOKS -> BooksScreen(
            onOpenBook = onOpenBook
        )
        NavDestination.GAMES -> GamesScreen(
            onSelectGame = onSelectGame
        )
        NavDestination.AI -> AiAssistantScreen(
            modelManager = aiModelManager,
            aiEngine = aiEngine
        )
        NavDestination.BROWSER -> LowDataBrowserScreen(
            initialUrl = browserUrl,
            onSendToDownloader = onSendToDownloader
        )
        NavDestination.SETTINGS -> SettingsScreen(
            onNavigateToAbout = onNavigateToAbout
        )
        NavDestination.ABOUT -> AboutScreen()
    }
}
