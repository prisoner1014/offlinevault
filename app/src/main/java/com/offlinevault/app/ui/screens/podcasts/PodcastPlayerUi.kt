package com.offlinevault.app.ui.screens.podcasts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.offlinevault.app.service.podcast.PodcastPlayerManager

@Composable
fun PodcastMiniPlayer(
    modifier: Modifier = Modifier
) {
    val episode = PodcastPlayerManager.currentEpisode ?: return
    val isPlaying = PodcastPlayerManager.isPlaying
    val isBuffering = PodcastPlayerManager.isBuffering
    val position = PodcastPlayerManager.currentPositionMs
    val duration = PodcastPlayerManager.durationMs
    val speed = PodcastPlayerManager.playbackSpeed
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { PodcastPlayerManager.showExpandedPlayer = true },
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Seek scrubber line
            val progress = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork / Icon badge
                val artworkUrl = PodcastPlayerManager.currentImageUrl ?: episode.imageUrl
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = episode.title,
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

                Spacer(modifier = Modifier.width(10.dp))

                // Title & Time info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PodcastPlayerManager.currentFeedTitle?.let { feedName ->
                            Text(
                                text = feedName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                        Text(
                            text = "${PodcastPlayerManager.formatTime(position)} / ${PodcastPlayerManager.formatTime(duration)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (PodcastPlayerManager.sleepTimerMinutes != null) {
                            val sleepBadge = if (PodcastPlayerManager.sleepTimerMinutes == -1) "🌙 End" else "🌙 ${(PodcastPlayerManager.sleepTimerSecondsRemaining / 60) + 1}m"
                            Text(
                                text = sleepBadge,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Controls: Rewind 10s
                IconButton(
                    onClick = { PodcastPlayerManager.seekRelative(-10000L) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Replay10,
                        contentDescription = "Rewind 10s",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Play / Pause / Buffering button
                IconButton(
                    onClick = { PodcastPlayerManager.togglePlayPause() },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                ) {
                    if (isBuffering) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // Forward 30s
                IconButton(
                    onClick = { PodcastPlayerManager.seekRelative(30000L) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Forward30,
                        contentDescription = "Forward 30s",
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Speed toggle
                TextButton(
                    onClick = {
                        val nextIdx = (speeds.indexOf(speed) + 1) % speeds.size
                        PodcastPlayerManager.setSpeed(speeds[nextIdx])
                    },
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "${speed}x",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Close/dismiss player
                IconButton(
                    onClick = { PodcastPlayerManager.stopAndDismiss() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close Player",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
fun PodcastExpandedPlayerDialog() {
    val episode = PodcastPlayerManager.currentEpisode ?: return
    val isPlaying = PodcastPlayerManager.isPlaying
    val isBuffering = PodcastPlayerManager.isBuffering
    val position = PodcastPlayerManager.currentPositionMs
    val duration = PodcastPlayerManager.durationMs
    val speed = PodcastPlayerManager.playbackSpeed
    val speeds = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isUserDragging by remember { mutableStateOf(false) }

    val currentFraction = if (duration > 0L) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
    LaunchedEffect(currentFraction) {
        if (!isUserDragging) {
            sliderPosition = currentFraction
        }
    }

    Dialog(
        onDismissRequest = { PodcastPlayerManager.showExpandedPlayer = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header: Down arrow & Close
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { PodcastPlayerManager.showExpandedPlayer = false }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Minimize")
                    }
                    Text(
                        text = "Now Playing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { PodcastPlayerManager.stopAndDismiss() }) {
                        Icon(Icons.Default.Close, contentDescription = "Stop & Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Artwork / Album Badge
                val artworkUrl = PodcastPlayerManager.currentImageUrl ?: episode.imageUrl
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    if (!artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artworkUrl,
                            contentDescription = episode.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Default.Podcasts,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Episode Title & Feed Name
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                PodcastPlayerManager.currentFeedTitle?.let { feedName ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = feedName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Scrubber Slider
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isUserDragging = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        val targetMs = (sliderPosition * duration).toLong()
                        PodcastPlayerManager.seekTo(targetMs)
                        isUserDragging = false
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // Time counters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val displayedPos = if (isUserDragging) (sliderPosition * duration).toLong() else position
                    Text(
                        text = PodcastPlayerManager.formatTime(displayedPos),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = PodcastPlayerManager.formatTime(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback Controls Row: -10s, Play/Pause, +30s
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { PodcastPlayerManager.seekRelative(-10000L) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Replay10,
                            contentDescription = "Rewind 10s",
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    FloatingActionButton(
                        onClick = { PodcastPlayerManager.togglePlayPause() },
                        modifier = Modifier.size(64.dp),
                        shape = CircleShape,
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        if (isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = { PodcastPlayerManager.seekRelative(30000L) },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.Forward30,
                            contentDescription = "Forward 30s",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Playback speed selector chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    speeds.forEach { s ->
                        FilterChip(
                            selected = speed == s,
                            onClick = { PodcastPlayerManager.setSpeed(s) },
                            label = { Text("${s}x") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Sleep Timer Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Sleep Timer",
                            tint = if (PodcastPlayerManager.sleepTimerMinutes != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Sleep Timer:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (PodcastPlayerManager.sleepTimerMinutes != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            onClick = { PodcastPlayerManager.setSleepTimer(null) }
                        ) {
                            val badgeText = if (PodcastPlayerManager.sleepTimerMinutes == -1) {
                                "End of Ep ✕"
                            } else {
                                val remainingMin = (PodcastPlayerManager.sleepTimerSecondsRemaining / 60) + 1
                                "${remainingMin}m left ✕"
                            }
                            Text(
                                text = badgeText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                val sleepOptions = listOf(null to "Off", 15 to "15m", 30 to "30m", 45 to "45m", 60 to "60m", -1 to "End")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sleepOptions.forEach { (mins, label) ->
                        FilterChip(
                            selected = PodcastPlayerManager.sleepTimerMinutes == mins,
                            onClick = { PodcastPlayerManager.setSleepTimer(mins) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Episode Description / Show Notes Section
                episode.description?.let { desc ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Episode Notes",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            val cleanDesc = desc.replace(Regex("<[^>]*>"), "")
                            Text(
                                text = cleanDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
