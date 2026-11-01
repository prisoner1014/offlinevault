package com.offlinevault.app.service.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.offlinevault.app.OfflineVaultApp
import com.offlinevault.app.R
import kotlinx.coroutines.*

class DownloaderService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var ytDlpEngine: YtDlpEngine

    override fun onCreate() {
        super.onCreate()
        ytDlpEngine = YtDlpEngine(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        val url = intent?.getStringExtra(EXTRA_URL)
        val qualityName = intent?.getStringExtra(EXTRA_QUALITY) ?: MediaQuality.BEST_VIDEO.name
        val downloadSubtitles = intent?.getBooleanExtra(EXTRA_SUBTITLES, false) ?: false
        val quality = try {
            MediaQuality.valueOf(qualityName)
        } catch (e: Exception) {
            MediaQuality.BEST_VIDEO
        }

        if (!url.isNullOrEmpty()) {
            startForeground(NOTIFICATION_ID, buildNotification("Preparing download...", 0))
            serviceScope.launch {
                val db = com.offlinevault.app.data.local.AppDatabase.getInstance(this@DownloaderService)
                val effectiveId = if (downloadId != -1L) {
                    downloadId
                } else {
                    db.downloadDao().insertDownload(
                        com.offlinevault.app.data.local.entity.DownloadEntity(
                            title = "Fetching info...",
                            sourceUrl = url,
                            mediaType = if (quality.isAudioOnly) com.offlinevault.app.data.local.entity.MediaType.AUDIO else com.offlinevault.app.data.local.entity.MediaType.VIDEO,
                            formatDescription = quality.label,
                            status = com.offlinevault.app.data.local.entity.DownloadStatus.QUEUED
                        )
                    )
                }

                ytDlpEngine.startDownload(effectiveId, url, quality, downloadSubtitles) { progress, eta, speed ->
                    val notification = buildNotification(
                        "Downloading: ${progress.toInt()}% ($speed, ETA: $eta)",
                        progress.toInt()
                    )
                    val manager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    manager.notify(NOTIFICATION_ID, notification)
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        } else {
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    private fun buildNotification(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, OfflineVaultApp.CHANNEL_DOWNLOADS)
            .setContentTitle("Offline Vault Downloader")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceJob.cancel()
        super.onDestroy()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_DOWNLOAD_ID = "extra_download_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_QUALITY = "extra_quality"
        const val EXTRA_SUBTITLES = "extra_subtitles"

        fun start(
            context: Context,
            downloadId: Long,
            url: String,
            quality: MediaQuality,
            downloadSubtitles: Boolean = false
        ) {
            val intent = Intent(context, DownloaderService::class.java).apply {
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_QUALITY, quality.name)
                putExtra(EXTRA_SUBTITLES, downloadSubtitles)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
