package com.offlinevault.app.service.podcast

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.EpisodeEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import com.offlinevault.app.service.playback.PodcastPlaybackService
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

object PodcastPlayerManager {
    private var exoPlayer: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var tickerJob: Job? = null
    private var appDb: AppDatabase? = null
    private var appContext: Context? = null

    var currentEpisode by mutableStateOf<EpisodeEntity?>(null)
    var currentFeedTitle by mutableStateOf<String?>(null)
    var currentImageUrl by mutableStateOf<String?>(null)
    var isPlaying by mutableStateOf(false)
    var isBuffering by mutableStateOf(false)
    var currentPositionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)
    var playbackSpeed by mutableFloatStateOf(1.0f)
    var errorMessage by mutableStateOf<String?>(null)
    var showExpandedPlayer by mutableStateOf(false)

    // Sleep Timer (null = Off, -1 = End of Episode, >0 = minutes)
    var sleepTimerMinutes by mutableStateOf<Int?>(null)
    var sleepTimerSecondsRemaining by mutableIntStateOf(0)

    fun setSleepTimer(minutes: Int?) {
        sleepTimerMinutes = minutes
        if (minutes == null || (minutes <= 0 && minutes != -1)) {
            sleepTimerMinutes = null
            sleepTimerSecondsRemaining = 0
        } else if (minutes == -1) {
            sleepTimerSecondsRemaining = 0
        } else {
            sleepTimerSecondsRemaining = minutes * 60
        }
    }

    fun getPlayer(context: Context): ExoPlayer = getOrCreatePlayer(context)

    private fun getOrCreatePlayer(context: Context): ExoPlayer {
        appContext = context.applicationContext
        if (exoPlayer == null) {
            val audioAttributes = androidx.media3.common.AudioAttributes.Builder()
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SPEECH)
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .build()

            exoPlayer = ExoPlayer.Builder(context.applicationContext)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .build().apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        PodcastPlayerManager.isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            val dur = duration
                            if (dur > 0) PodcastPlayerManager.durationMs = dur
                        } else if (state == Player.STATE_ENDED) {
                            PodcastPlayerManager.isPlaying = false
                            if (sleepTimerMinutes == -1) {
                                sleepTimerMinutes = null
                            }
                            currentEpisode?.let { ep ->
                                scope.launch(Dispatchers.IO) {
                                    appDb?.feedDao()?.markAsPlayed(ep.id)
                                }
                            }
                            appContext?.let { PodcastPlaybackService.startOrUpdate(it) }
                        }
                    }

                    override fun onIsPlayingChanged(playing: Boolean) {
                        PodcastPlayerManager.isPlaying = playing
                        appContext?.let { PodcastPlaybackService.startOrUpdate(it) }
                        if (playing) {
                            startTicker()
                        } else {
                            stopTicker()
                            persistCurrentPosition()
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        PodcastPlayerManager.errorMessage = error.localizedMessage ?: "Playback error encountered"
                        PodcastPlayerManager.isPlaying = false
                        stopTicker()
                    }
                })
            }
        }
        return exoPlayer!!
    }

    fun playEpisode(
        context: Context,
        episode: EpisodeEntity,
        feedTitle: String? = null,
        imageUrl: String? = null,
        db: AppDatabase? = null
    ) {
        db?.let { appDb = it }
        val player = getOrCreatePlayer(context)

        // If toggling the same episode
        if (currentEpisode?.id == episode.id) {
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
            return
        }

        // New episode: save prior progress
        persistCurrentPosition()

        currentEpisode = episode
        currentFeedTitle = feedTitle
        currentImageUrl = imageUrl ?: episode.imageUrl
        errorMessage = null
        currentPositionMs = episode.lastPlaybackPositionMs.coerceAtLeast(0L)
        durationMs = if (episode.durationSeconds > 0) episode.durationSeconds * 1000L else 0L

        val uri = if (episode.localAudioPath != null && File(episode.localAudioPath).exists()) {
            Uri.fromFile(File(episode.localAudioPath))
        } else {
            Uri.parse(episode.audioUrl)
        }

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri(uri))
        player.setPlaybackSpeed(playbackSpeed)
        player.prepare()

        if (episode.lastPlaybackPositionMs > 1000L) {
            player.seekTo(episode.lastPlaybackPositionMs)
        }
        player.playWhenReady = true
        player.play()
    }

    fun playMediaItem(
        context: Context,
        mediaItem: MediaItem,
        title: String,
        subtitle: String? = null,
        imageUrl: String? = null
    ) {
        val player = getOrCreatePlayer(context)
        persistCurrentPosition()

        currentEpisode = null
        currentFeedTitle = subtitle
        currentImageUrl = imageUrl
        errorMessage = null
        currentPositionMs = 0L
        durationMs = 0L

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(mediaItem)
        player.setPlaybackSpeed(playbackSpeed)
        player.prepare()
        player.playWhenReady = true
        player.play()
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
            } else {
                player.play()
            }
        }
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.let { player ->
            val target = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
            player.seekTo(target)
            currentPositionMs = target
            persistCurrentPosition()
        }
    }

    fun seekRelative(deltaMs: Long) {
        exoPlayer?.let { player ->
            val cur = player.currentPosition
            val maxDur = if (durationMs > 0L) durationMs else player.duration.coerceAtLeast(0L)
            val target = (cur + deltaMs).coerceIn(0L, if (maxDur > 0) maxDur else Long.MAX_VALUE)
            player.seekTo(target)
            currentPositionMs = target
            persistCurrentPosition()
        }
    }

    fun setSpeed(speed: Float) {
        playbackSpeed = speed
        exoPlayer?.setPlaybackSpeed(speed)
    }

    fun cycleSpeed(): Float {
        val speeds = listOf(1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        val currentIndex = speeds.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.05f }
        val nextIndex = if (currentIndex >= 0) (currentIndex + 1) % speeds.size else 0
        val nextSpeed = speeds[nextIndex]
        setSpeed(nextSpeed)
        return nextSpeed
    }

    fun stopAndDismiss() {
        persistCurrentPosition()
        stopTicker()
        appContext?.let { PodcastPlaybackService.stopService(it) }
        sleepTimerMinutes = null
        sleepTimerSecondsRemaining = 0
        exoPlayer?.stop()
        exoPlayer?.clearMediaItems()
        currentEpisode = null
        currentFeedTitle = null
        currentImageUrl = null
        isPlaying = false
        isBuffering = false
        currentPositionMs = 0L
        durationMs = 0L
        showExpandedPlayer = false
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            var counter = 0
            while (isActive) {
                exoPlayer?.let { player ->
                    currentPositionMs = player.currentPosition
                    val dur = player.duration
                    if (dur > 0) durationMs = dur

                    counter++
                    // Persist position every 5 seconds while playing
                    if (counter >= 10) {
                        counter = 0
                        persistCurrentPosition()
                    }

                    // Sleep timer countdown every second (2 x 500ms)
                    if (counter % 2 == 0 && sleepTimerSecondsRemaining > 0) {
                        sleepTimerSecondsRemaining--
                        if (sleepTimerSecondsRemaining <= 0) {
                            sleepTimerMinutes = null
                            player.pause()
                            appContext?.let { PodcastPlaybackService.startOrUpdate(it) }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun persistCurrentPosition() {
        val epId = currentEpisode?.id ?: return
        val pos = currentPositionMs
        if (pos > 0) {
            scope.launch(Dispatchers.IO) {
                appDb?.feedDao()?.updatePlaybackPosition(epId, pos)
            }
        }
    }

    fun formatTime(ms: Long): String {
        if (ms <= 0L) return "0:00"
        val totalSecs = ms / 1000
        val s = totalSecs % 60
        val m = (totalSecs / 60) % 60
        val h = totalSecs / 3600
        return if (h > 0) {
            String.format(java.util.Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(java.util.Locale.US, "%d:%02d", m, s)
        }
    }
}
