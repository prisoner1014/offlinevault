package com.offlinevault.app.service.download

import android.content.Context
import android.util.Log
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.DownloadEntity
import com.offlinevault.app.data.local.entity.DownloadStatus
import com.offlinevault.app.data.local.entity.MediaType
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

enum class MediaQuality(
    val label: String,
    val formatOption: String,
    val isAudioOnly: Boolean,
    val audioBitrate: String? = null
) {
    BEST_VIDEO("Best Available", "bestvideo+bestaudio/best", false),
    VIDEO_1080P("1080p Full HD", "bestvideo[height<=1080]+bestaudio/best[height<=1080]/best", false),
    VIDEO_720P("720p HD", "bestvideo[height<=720]+bestaudio/best[height<=720]/best", false),
    VIDEO_480P("480p SD", "bestvideo[height<=480]+bestaudio/best[height<=480]/best", false),
    VIDEO_360P("360p Low (Save Data)", "bestvideo[height<=360]+bestaudio/best[height<=360]/best", false),
    VIDEO_240P("240p Very Low (Tiny Size)", "bestvideo[height<=240]+bestaudio/best[height<=240]/worstvideo+worstaudio/worst", false),
    VIDEO_144P("144p Ultra Low (Minimal Data)", "bestvideo[height<=144]+bestaudio/best[height<=144]/worstvideo+worstaudio/worst", false),
    AUDIO_320K("Audio HQ (320 kbps MP3)", "bestaudio/best", true, "320K"),
    AUDIO_128K("Audio Standard (128 kbps MP3)", "bestaudio/best", true, "128K"),
    AUDIO_64K("Audio Voice / Podcast (64 kbps Small)", "bestaudio/best", true, "64K"),
    AUDIO_32K("Audio Ultra Low (32 kbps Tiny)", "bestaudio/best", true, "32K")
}

class YtDlpEngine(private val context: Context) {

    private val storageTracker = StorageTracker(context)
    private val downloadDao = AppDatabase.getInstance(context).downloadDao()
    private val youTubeAuthManager = YouTubeAuthManager(context)

    companion object {
        @Volatile
        private var hasCheckedUpdate = false

        private val speedPattern = Pattern.compile("at\\s+([0-9.]+[kKMGT]i?B/s)")
        private val etaPattern = Pattern.compile("ETA\\s+([0-9:]+)")
        private val totalSizePattern = Pattern.compile("of\\s+~?([0-9.]+)\\s*([kKMGT]i?B)")
        private val downloadedSizePattern = Pattern.compile("([0-9.]+)\\s*([kKMGT]i?B)\\s+of")

        private fun parseToBytes(numStr: String, unitStr: String): Long {
            val num = numStr.toDoubleOrNull() ?: return 0L
            val unit = unitStr.uppercase()
            return when {
                unit.startsWith("K") -> (num * 1024).toLong()
                unit.startsWith("M") -> (num * 1024 * 1024).toLong()
                unit.startsWith("G") -> (num * 1024 * 1024 * 1024).toLong()
                unit.startsWith("T") -> (num * 1024L * 1024L * 1024L * 1024L).toLong()
                else -> num.toLong()
            }
        }
    }

    private fun cleanUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        return try {
            if (trimmed.contains("youtu.be/")) {
                val videoId = trimmed.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else trimmed
            } else if (trimmed.contains("youtube.com/watch")) {
                val videoId = trimmed.substringAfter("v=").substringBefore("&")
                if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else trimmed
            } else if (trimmed.contains("youtube.com/shorts/")) {
                val videoId = trimmed.substringAfter("shorts/").substringBefore("?").substringBefore("&")
                if (videoId.isNotBlank()) "https://www.youtube.com/watch?v=$videoId" else trimmed
            } else {
                trimmed
            }
        } catch (e: Exception) {
            trimmed
        }
    }

    private fun extractVideoId(url: String): String? {
        return if (url.contains("v=")) {
            url.substringAfter("v=").substringBefore("&")
        } else if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        } else {
            null
        }
    }

    suspend fun startDownload(
        downloadId: Long,
        url: String,
        quality: MediaQuality,
        downloadSubtitles: Boolean = false,
        onProgress: (progress: Float, eta: String, speed: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val cleanedUrl = cleanUrl(url)
        val videoId = extractVideoId(cleanedUrl)
        val outputDir = storageTracker.getCategoryDir(StorageCategory.VIDEOS)
        val outputTemplate = File(outputDir, "%(title).100s-%(id)s.%(ext)s").absolutePath

        // Record initial files in output directory to find the resulting file later
        val filesBefore = outputDir.listFiles()?.toSet() ?: emptySet()

        // Update status to DOWNLOADING
        val entity = downloadDao.getDownloadById(downloadId)
        entity?.let {
            downloadDao.updateDownload(it.copy(sourceUrl = cleanedUrl, status = DownloadStatus.DOWNLOADING))
        }

        val hasActiveCookies = youTubeAuthManager.hasActiveCookies()
        val cookieFile = youTubeAuthManager.cookieFile

        // Try downloading with preferred options, fallback if 403 occurs.
        // When authenticated with membership cookies, web/mweb must be used instead of tv.
        val clientsToTry = if (hasActiveCookies) {
            listOf(
                "web,mweb",
                "mweb",
                "web",
                "tv,mweb,android"
            )
        } else {
            listOf(
                "tv,mweb,android",
                "mweb",
                "tv"
            )
        }

        var lastException: Exception? = null

        for (clientOption in clientsToTry) {
            try {
                Log.d("YtDlpEngine", "Attempting download with player_client=$clientOption for $cleanedUrl (hasCookies=$hasActiveCookies)")

                val request = YoutubeDLRequest(cleanedUrl).apply {
                    addOption("-o", outputTemplate)
                    addOption("-f", quality.formatOption)
                    addOption("--no-mtime")
                    addOption("--no-warnings")
                    addOption("--no-check-certificates")
                    addOption("--force-ipv4")
                    addOption("--rm-cache-dir")
                    addOption("--user-agent", "Mozilla/5.0 (Linux; Android 14; SM-F971B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    
                    if (hasActiveCookies && cookieFile.exists() && cookieFile.length() > 0) {
                        Log.d("YtDlpEngine", "Using YouTube session cookies from ${cookieFile.absolutePath}")
                        addOption("--cookies", cookieFile.absolutePath)
                    }

                    if (cleanedUrl.contains("youtube.com") || cleanedUrl.contains("youtu.be")) {
                        addOption("--extractor-args", "youtube:player_client=$clientOption")
                    }

                    if (quality.isAudioOnly) {
                        addOption("-x")
                        addOption("--audio-format", "mp3")
                        quality.audioBitrate?.let {
                            addOption("--audio-quality", it)
                        }
                    }

                    if (downloadSubtitles) {
                        addOption("--write-subs")
                        addOption("--sub-langs", "en.*,all")
                        addOption("--embed-subs")
                    }
                }

                var lastDbUpdateMs = 0L
                var parsedTotalBytes = 0L
                var parsedDownloadedBytes = 0L
                val updateScope = CoroutineScope(Dispatchers.IO)

                YoutubeDL.getInstance().execute(request) { progress, etaInSeconds, line ->
                    var speed = ""
                    var eta = if (etaInSeconds > 0) "${etaInSeconds}s" else ""

                    if (line != null) {
                        val speedMatcher = speedPattern.matcher(line)
                        if (speedMatcher.find()) {
                            speed = speedMatcher.group(1) ?: ""
                        }
                        val etaMatcher = etaPattern.matcher(line)
                        if (etaMatcher.find()) {
                            eta = etaMatcher.group(1) ?: eta
                        }
                        val totalMatcher = totalSizePattern.matcher(line)
                        if (totalMatcher.find()) {
                            val num = totalMatcher.group(1) ?: ""
                            val unit = totalMatcher.group(2) ?: ""
                            parsedTotalBytes = parseToBytes(num, unit)
                        }
                        val downMatcher = downloadedSizePattern.matcher(line)
                        if (downMatcher.find()) {
                            val num = downMatcher.group(1) ?: ""
                            val unit = downMatcher.group(2) ?: ""
                            parsedDownloadedBytes = parseToBytes(num, unit)
                        } else if (parsedTotalBytes > 0L && progress > 0f) {
                            parsedDownloadedBytes = (parsedTotalBytes * (progress / 100.0)).toLong()
                        }
                    }

                    onProgress(progress, eta, speed)

                    val now = System.currentTimeMillis()
                    if (now - lastDbUpdateMs > 600L || progress >= 99f) {
                        lastDbUpdateMs = now
                        updateScope.launch {
                            val existing = downloadDao.getDownloadById(downloadId)
                            if (existing != null && existing.status != DownloadStatus.COMPLETED) {
                                downloadDao.updateDownload(
                                    existing.copy(
                                        status = DownloadStatus.DOWNLOADING,
                                        progressPercent = progress,
                                        speedText = speed,
                                        etaText = eta,
                                        totalSizeBytes = if (parsedTotalBytes > 0L) parsedTotalBytes else existing.totalSizeBytes,
                                        downloadedBytes = if (parsedDownloadedBytes > 0L) parsedDownloadedBytes else existing.downloadedBytes
                                    )
                                )
                            }
                        }
                    }
                }

                // Download succeeded! Find the downloaded file
                val filesAfter = outputDir.listFiles()?.toList() ?: emptyList()
                val downloadedFile = filesAfter.firstOrNull { f ->
                    !filesBefore.contains(f) || (videoId != null && f.name.contains(videoId))
                } ?: filesAfter.maxByOrNull { it.lastModified() }

                val finalTitle = downloadedFile?.nameWithoutExtension ?: "Downloaded Video"
                val finalSize = downloadedFile?.length() ?: 0L
                val finalPath = downloadedFile?.absolutePath

                val updated = downloadDao.getDownloadById(downloadId)
                if (updated != null) {
                    downloadDao.updateDownload(
                        updated.copy(
                            title = finalTitle,
                            localFilePath = finalPath,
                            status = DownloadStatus.COMPLETED,
                            progressPercent = 100f,
                            speedText = "Finished",
                            etaText = "Done",
                            totalSizeBytes = finalSize,
                            downloadedBytes = finalSize
                        )
                    )
                }

                return@withContext Result.success(downloadedFile ?: outputDir)
            } catch (e: Exception) {
                Log.w("YtDlpEngine", "Attempt with player_client=$clientOption failed: ${e.message}")
                lastException = e
                // If yt-dlp update hasn't run yet, trigger an update now
                if (!hasCheckedUpdate) {
                    try {
                        Log.d("YtDlpEngine", "Updating yt-dlp binary after failure...")
                        val st = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                        Log.d("YtDlpEngine", "Updated yt-dlp binary: $st")
                        hasCheckedUpdate = true
                    } catch (ue: Exception) {
                        Log.w("YtDlpEngine", "Update attempt note: ${ue.message}")
                    }
                }
            }
        }

        // All attempts failed
        Log.e("YtDlpEngine", "All download attempts failed for url: $cleanedUrl", lastException)
        val updated = downloadDao.getDownloadById(downloadId)
        if (updated != null) {
            downloadDao.updateDownload(
                updated.copy(
                    status = DownloadStatus.FAILED,
                    speedText = "Error",
                    etaText = lastException?.localizedMessage ?: "Download failed"
                )
            )
        }
        Result.failure(lastException ?: Exception("Download failed"))
    }
}
