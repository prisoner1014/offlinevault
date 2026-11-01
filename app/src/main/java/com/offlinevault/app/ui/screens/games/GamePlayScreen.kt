package com.offlinevault.app.ui.screens.games

import android.annotation.SuppressLint
import android.net.http.SslError
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GamePlayScreen(
    game: GameDefinition,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showInstructionsDialog by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }

    // Intercept hardware back button & system edge back gestures
    BackHandler(enabled = true) {
        if (showInstructionsDialog) {
            showInstructionsDialog = false
        } else {
            onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance?.destroy()
            webViewInstance = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = game.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = game.badge,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Exit Game to Library"
                        )
                    }
                },
                actions = {
                    // Mute / Unmute Toggle
                    IconButton(onClick = {
                        isMuted = !isMuted
                        webViewInstance?.evaluateJavascript("AudioFX.muted = $isMuted;", null)
                    }) {
                        Icon(
                            if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = if (isMuted) "Unmute Audio" else "Mute Audio",
                            tint = if (isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // Reload / Restart
                    IconButton(onClick = {
                        val gamesDir = File(context.cacheDir, "games").apply { mkdirs() }
                        val gameFile = File(gamesDir, "${game.id}.html")
                        val html = GameHtmlProvider.getHtmlForGame(game.id)
                        gameFile.writeText(html)
                        webViewInstance?.loadUrl("file://${gameFile.absolutePath}")
                    }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Restart Game"
                        )
                    }

                    // Help / Instructions
                    IconButton(onClick = { showInstructionsDialog = true }) {
                        Icon(
                            Icons.Default.HelpOutline,
                            contentDescription = "How to Play"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF090D16))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewInstance = this
                        WebView.setWebContentsDebuggingEnabled(true)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                            mediaPlaybackRequiresUserGesture = false
                            loadsImagesAutomatically = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                        }
                        setBackgroundColor(0xFF090D16.toInt())

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                Log.d("GamePlayScreen", "JS [${consoleMessage?.messageLevel()}]: ${consoleMessage?.message()} (${consoleMessage?.lineNumber()})")
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                Log.e("GamePlayScreen", "WebView Error: ${error?.description} for ${request?.url}")
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.proceed()
                            }
                        }

                        // Write self-contained offline HTML to cache and load as local file:// URL
                        val gamesDir = File(ctx.cacheDir, "games").apply { mkdirs() }
                        val gameFile = File(gamesDir, "${game.id}.html")
                        val html = GameHtmlProvider.getHtmlForGame(game.id)
                        gameFile.writeText(html)
                        loadUrl("file://${gameFile.absolutePath}")
                    }
                }
            )
        }
    }

    if (showInstructionsDialog) {
        AlertDialog(
            onDismissRequest = { showInstructionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        game.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(game.title)
                }
            },
            text = {
                Column {
                    Text(
                        text = "How to Play:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = game.instructions,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Features:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• 100% Offline with zero internet required\n• Built-in retro synth sound effects\n• Edge back swipe or top arrow exits to Games list\n• Scores persist in local storage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showInstructionsDialog = false }) {
                    Text("Got it")
                }
            }
        )
    }
}
