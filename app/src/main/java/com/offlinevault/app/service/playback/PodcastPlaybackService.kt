package com.offlinevault.app.service.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offlinevault.app.MainActivity
import com.offlinevault.app.R
import com.offlinevault.app.service.podcast.PodcastPlayerManager

class PodcastPlaybackService : Service() {

    companion object {
        const val CHANNEL_ID = "offline_vault_audio_playback"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_OR_UPDATE = "com.offlinevault.app.action.START_OR_UPDATE"
        const val ACTION_TOGGLE_PLAY = "com.offlinevault.app.action.TOGGLE_PLAY"
        const val ACTION_REWIND = "com.offlinevault.app.action.REWIND"
        const val ACTION_FORWARD = "com.offlinevault.app.action.FORWARD"
        const val ACTION_STOP = "com.offlinevault.app.action.STOP"

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, PodcastPlaybackService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PodcastPlaybackService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_PLAY -> {
                PodcastPlayerManager.togglePlayPause()
                updateNotification()
            }
            ACTION_REWIND -> {
                PodcastPlayerManager.seekRelative(-10000L)
                updateNotification()
            }
            ACTION_FORWARD -> {
                PodcastPlayerManager.seekRelative(30000L)
                updateNotification()
            }
            ACTION_STOP -> {
                PodcastPlayerManager.stopAndDismiss()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START_OR_UPDATE -> {
                updateNotification()
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Podcast Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls and status for background audio and podcast playback"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateNotification() {
        val episode = PodcastPlayerManager.currentEpisode
        if (episode == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        val isPlaying = PodcastPlayerManager.isPlaying
        val title = episode.title
        val feedTitle = PodcastPlayerManager.currentFeedTitle ?: "Offline Vault Podcast"

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Action intents
        fun createActionPendingIntent(action: String, requestCode: Int): PendingIntent {
            val intent = Intent(this, PodcastPlaybackService::class.java).apply {
                this.action = action
            }
            return PendingIntent.getService(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val rewindIntent = createActionPendingIntent(ACTION_REWIND, 1)
        val togglePlayIntent = createActionPendingIntent(ACTION_TOGGLE_PLAY, 2)
        val forwardIntent = createActionPendingIntent(ACTION_FORWARD, 3)
        val stopIntent = createActionPendingIntent(ACTION_STOP, 4)

        val playPauseIcon = if (isPlaying) {
            android.R.drawable.ic_media_pause
        } else {
            android.R.drawable.ic_media_play
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(feedTitle)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_rew, "-10s", rewindIntent)
            .addAction(playPauseIcon, playPauseTitle, togglePlayIntent)
            .addAction(android.R.drawable.ic_media_ff, "+30s", forwardIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
