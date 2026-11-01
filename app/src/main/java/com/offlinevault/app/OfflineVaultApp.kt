package com.offlinevault.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.ffmpeg.FFmpeg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OfflineVaultApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannels()
        initDownloadEngines()
    }

    private fun initDownloadEngines() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                YoutubeDL.getInstance().init(this@OfflineVaultApp)
                FFmpeg.getInstance().init(this@OfflineVaultApp)
                Log.d("OfflineVault", "YoutubeDL and FFmpeg initialized successfully.")

                // Check & update yt-dlp to latest stable release
                try {
                    Log.d("OfflineVault", "Checking yt-dlp updates...")
                    val status = YoutubeDL.getInstance().updateYoutubeDL(this@OfflineVaultApp, YoutubeDL.UpdateChannel.STABLE)
                    Log.d("OfflineVault", "YoutubeDL update status: $status")
                } catch (ue: Exception) {
                    Log.w("OfflineVault", "yt-dlp auto-update note: ${ue.message}")
                }
            } catch (e: Exception) {
                Log.e("OfflineVault", "Failed to initialize YoutubeDL / FFmpeg", e)
            }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Offline Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time download progress and speed"
            }

            val playbackChannel = NotificationChannel(
                CHANNEL_PLAYBACK,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls for offline audio and podcast playback"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(downloadChannel)
            manager?.createNotificationChannel(playbackChannel)
        }
    }

    companion object {
        lateinit var instance: OfflineVaultApp
            private set

        const val CHANNEL_DOWNLOADS = "offline_downloads_channel"
        const val CHANNEL_PLAYBACK = "offline_playback_channel"
    }
}
