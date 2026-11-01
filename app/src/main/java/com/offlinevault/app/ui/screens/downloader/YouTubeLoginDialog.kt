package com.offlinevault.app.ui.screens.downloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.view.ViewGroup
import android.webkit.*
import android.widget.Toast
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.offlinevault.app.service.download.YouTubeAuthManager

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubeLoginDialog(
    authManager: YouTubeAuthManager,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var currentUrl by remember { mutableStateOf("https://accounts.google.com/ServiceLogin?service=youtube&continue=https%3A%2F%2Fm.youtube.com%2F") }
    var isLoading by remember { mutableStateOf(true) }
    var detectedSession by remember { mutableStateOf(false) }

    // Intercept hardware back button & system edge back gestures
    BackHandler(enabled = true) {
        if (webViewInstance?.canGoBack() == true) {
            webViewInstance?.goBack()
        } else {
            onDismiss()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewInstance?.stopLoading()
            webViewInstance = null
        }
    }

    fun checkAndSaveCookies(): Boolean {
        val cookieManager = CookieManager.getInstance()
        val ytCookies = cookieManager.getCookie("https://www.youtube.com") ?: ""
        val googleCookies = cookieManager.getCookie("https://accounts.google.com") ?: ""
        val combined = "$ytCookies; $googleCookies"

        val hasSession = YouTubeAuthManager.ESSENTIAL_SESSION_COOKIES.any { combined.contains(it) }
        if (hasSession) {
            detectedSession = true
            val saved = authManager.saveFromRawCookieString(combined, "Google/YouTube Account")
            if (saved) {
                Toast.makeText(context, "YouTube Account Connected! Memberships Active \uD83C\uDF1F", Toast.LENGTH_LONG).show()
                onLoginSuccess()
                return true
            }
        }
        return false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Sign In with YouTube",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (detectedSession) "Session Detected \u2705" else "Log in to enable channel memberships",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (detectedSession) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                        }
                    },
                    actions = {
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reload")
                        }
                        Button(
                            onClick = {
                                val success = checkAndSaveCookies()
                                if (!success) {
                                    Toast.makeText(
                                        context,
                                        "Please finish entering your credentials, then tap Save.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (detectedSession) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            ),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (detectedSession) "Save & Finish" else "Save Cookies")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            },
            bottomBar = {
                if (detectedSession) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        "YouTube Session Active!",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "Cookies ready for members-only downloads",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Button(onClick = { checkAndSaveCookies() }) {
                                Text("Complete")
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
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewInstance = this
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                // Clean modern Chrome Android user-agent to prevent Google's "unsecure browser" screen
                                userAgentString = "Mozilla/5.0 (Linux; Android 14; SM-F971B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    url?.let { currentUrl = it }
                                    checkAndSaveCookies()
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    url?.let { currentUrl = it }
                                    checkAndSaveCookies()
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    handler?.proceed()
                                }
                            }

                            loadUrl(currentUrl)
                        }
                    }
                )

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }
    }
}
