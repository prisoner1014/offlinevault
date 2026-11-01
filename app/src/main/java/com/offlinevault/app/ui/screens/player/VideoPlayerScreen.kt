package com.offlinevault.app.ui.screens.player

import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import java.io.File

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    videoPath: String,
    title: String,
    onBack: () -> Unit,
    onOpenExternal: (String) -> Unit
) {
    // Intercept hardware back button & system edge back gestures
    BackHandler(enabled = true) {
        onBack()
    }

    val context = LocalContext.current
    var playerError by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }

    val resizeModes = remember {
        listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT to "Fit",
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Fold Fill",
            AspectRatioFrameLayout.RESIZE_MODE_FILL to "Stretch"
        )
    }
    var resizeModeIndex by remember { mutableIntStateOf(0) }

    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    playerError = error.localizedMessage ?: "Playback error encountered"
                }
            })
            val file = File(videoPath)
            if (file.exists()) {
                val mediaItem = MediaItem.fromUri(Uri.fromFile(file))
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            } else {
                playerError = "Media file not found: ${file.name}"
            }
        }
    }

    DisposableEffect(videoPath) {
        onDispose {
            exoPlayer.release()
        }
    }

    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    resizeMode = resizeModes[resizeModeIndex].first
                    playerViewRef = this
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { view ->
                view.resizeMode = resizeModes[resizeModeIndex].first
            },
            modifier = Modifier.fillMaxSize()
        )

        // Overlay Top Bar with Back, Speed toggle, Fold Aspect Ratio, and Open in External Player buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Seek -10s
                IconButton(
                    onClick = {
                        val newPos = (exoPlayer.currentPosition - 10000L).coerceAtLeast(0L)
                        exoPlayer.seekTo(newPos)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FastRewind,
                        contentDescription = "Rewind 10s",
                        tint = Color.White
                    )
                }

                // Seek +10s
                IconButton(
                    onClick = {
                        val duration = exoPlayer.duration
                        val target = exoPlayer.currentPosition + 10000L
                        val newPos = if (duration > 0) target.coerceAtMost(duration) else target
                        exoPlayer.seekTo(newPos)
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.FastForward,
                        contentDescription = "Forward 10s",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Aspect ratio toggle button (crucial for Fold 7.6" inner screen vs Cover display)
                OutlinedButton(
                    onClick = {
                        resizeModeIndex = (resizeModeIndex + 1) % resizeModes.size
                        playerViewRef?.resizeMode = resizeModes[resizeModeIndex].first
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.padding(end = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = resizeModes[resizeModeIndex].second,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                // Playback speed toggle button
                OutlinedButton(
                    onClick = {
                        val nextIndex = (speeds.indexOf(playbackSpeed) + 1) % speeds.size
                        playbackSpeed = speeds[nextIndex]
                        exoPlayer.setPlaybackSpeed(playbackSpeed)
                    },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier.padding(end = 4.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "${playbackSpeed}x",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                IconButton(onClick = { onOpenExternal(videoPath) }) {
                    Icon(
                        Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in External Player",
                        tint = Color.White
                    )
                }
            }
        }

        // Error display overlay if player fails to render or file is missing
        playerError?.let { err ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "⚠️ Playback Error",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row {
                        Button(onClick = { onOpenExternal(videoPath) }) {
                            Text("Open in External Player (VLC)")
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        OutlinedButton(onClick = onBack) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
    }
}
