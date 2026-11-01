package com.offlinevault.app.service.crawler

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.ArchiveEntity
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.util.LinkedList
import java.util.concurrent.TimeUnit

enum class ImageQualityOption(val label: String, val description: String) {
    NO_IMAGES("No Images (Maximum Data & Space Saver)", "Strips images completely for near-zero bandwidth and tiny disk usage"),
    LOW_QUALITY("Low Quality (Compressed 30% WebP)", "Downscales images to 480px and compresses to 30% quality (saves ~85-90% data)"),
    FULL_QUALITY("Full Quality Images", "Downloads images as hosted online")
}

data class CrawlConfig(
    val startUrl: String,
    val maxDepth: Int = 1, // 0 = single page, 1 = page + direct links, 2 = 2 levels deep
    val maxPages: Int = 20, // Page cap to prevent runaway downloads
    val sameDomainOnly: Boolean = true,
    val imageOption: ImageQualityOption = ImageQualityOption.LOW_QUALITY
)

data class CrawlProgress(
    val pagesCompleted: Int,
    val totalDiscovered: Int,
    val currentUrl: String,
    val bytesSaved: Long
)

class SiteCrawler(private val context: Context) {

    private val storageTracker = StorageTracker(context)
    private val archiveDao = AppDatabase.getInstance(context).archiveDao()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun crawlAndSave(
        config: CrawlConfig,
        onProgress: (CrawlProgress) -> Unit = {}
    ): Result<ArchiveEntity> = withContext(Dispatchers.IO) {
        try {
            val seedUrl = config.startUrl.trim().substringBefore("#")
            val baseHost = getHost(seedUrl) ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid URL: $seedUrl")
            )

            val safeDomain = baseHost.replace(Regex("[^a-zA-Z0-9.-]"), "_")
            val sitesRootDir = File(storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES), "sites")
            val siteDir = File(sitesRootDir, "${safeDomain}_${System.currentTimeMillis()}")
            siteDir.mkdirs()

            val imagesDir = File(siteDir, "images").apply { mkdirs() }
            val cssDir = File(siteDir, "css").apply { mkdirs() }
            val mediaDir = File(siteDir, "media").apply { mkdirs() }
            val jsDir = File(siteDir, "js").apply { mkdirs() }

            val queue = LinkedList<Pair<String, Int>>() // URL to Depth
            queue.add(Pair(seedUrl, 0))

            val visitedUrls = mutableSetOf<String>()
            val urlToLocalFilename = mutableMapOf<String, String>()
            urlToLocalFilename[seedUrl] = "index.html"

            val downloadedAssets = mutableSetOf<String>()
            var pageCounter = 0
            var totalBytesSaved = 0L
            var mainSiteTitle = "Offline Site: $baseHost"

            while (queue.isNotEmpty() && visitedUrls.size < config.maxPages) {
                val (currentUrl, currentDepth) = queue.poll() ?: break
                if (visitedUrls.contains(currentUrl)) continue
                visitedUrls.add(currentUrl)

                val localFilename = urlToLocalFilename.getOrPut(currentUrl) {
                    if (currentUrl == seedUrl) "index.html" else "page_${pageCounter++}.html"
                }

                onProgress(
                    CrawlProgress(
                        pagesCompleted = visitedUrls.size,
                        totalDiscovered = visitedUrls.size + queue.size,
                        currentUrl = currentUrl,
                        bytesSaved = totalBytesSaved
                    )
                )

                val doc = fetchHtmlDocument(currentUrl) ?: continue
                if (currentUrl == seedUrl) {
                    val title = doc.title().trim()
                    if (title.isNotBlank()) mainSiteTitle = title
                }

                // 1. Process and optimize images
                processImages(doc, config.imageOption, imagesDir, downloadedAssets) { bytesAdded ->
                    totalBytesSaved += bytesAdded
                }

                // 2. Process stylesheets so the site renders properly styled offline
                processStylesheets(doc, cssDir, downloadedAssets) { bytesAdded ->
                    totalBytesSaved += bytesAdded
                }

                // 3. Process audio, podcasts, and sound media on the website
                processMedia(doc, mediaDir, downloadedAssets) { bytesAdded ->
                    totalBytesSaved += bytesAdded
                }

                // 4. Process JavaScript scripts for interactive audio/player functionality
                processScripts(doc, jsDir, downloadedAssets) { bytesAdded ->
                    totalBytesSaved += bytesAdded
                }

                // 5. Discover links and rewrite hrefs to local relative files
                val pageLinks = doc.select("a[href]")
                for (link in pageLinks) {
                    val rawHref = link.attr("href")
                    if (rawHref.startsWith("javascript:") || rawHref.startsWith("mailto:") || rawHref.startsWith("tel:")) {
                        continue
                    }

                    val absUrl = link.absUrl("href").substringBefore("#")
                    if (absUrl.isBlank() || !absUrl.startsWith("http")) continue

                    val linkHost = getHost(absUrl)
                    val isSameDomain = linkHost != null && (linkHost.equals(baseHost, ignoreCase = true) || linkHost.endsWith(".$baseHost"))

                    if (isSameDomain && (!config.sameDomainOnly || isSameDomain)) {
                        val targetFilename = urlToLocalFilename.getOrPut(absUrl) {
                            "page_${pageCounter++}.html"
                        }

                        // Rewrite link to open offline local file
                        link.attr("href", targetFilename)

                        // Enqueue if within depth and budget
                        if (!visitedUrls.contains(absUrl) && (currentDepth + 1) <= config.maxDepth && (visitedUrls.size + queue.size) < config.maxPages) {
                            if (queue.none { it.first == absUrl }) {
                                queue.add(Pair(absUrl, currentDepth + 1))
                            }
                        }
                    }
                }

                // 6. Save rewritten HTML to disk
                val htmlFile = File(siteDir, localFilename)
                val htmlBytes = doc.outerHtml().toByteArray(Charsets.UTF_8)
                htmlFile.writeBytes(htmlBytes)
                totalBytesSaved += htmlBytes.size
            }

            val entryFile = File(siteDir, "index.html")
            val entity = ArchiveEntity(
                title = mainSiteTitle,
                originalUrl = seedUrl,
                localMhtmlPath = entryFile.absolutePath,
                fileSizeBytes = totalBytesSaved,
                isSiteDirectory = true,
                pageCount = visitedUrls.size,
                crawlDepth = config.maxDepth,
                savedAt = System.currentTimeMillis()
            )

            val insertedId = archiveDao.insertArchive(entity)
            Result.success(entity.copy(id = insertedId))
        } catch (e: Exception) {
            Log.e("SiteCrawler", "Crawling failed for ${config.startUrl}", e)
            Result.failure(e)
        }
    }

    private fun fetchHtmlDocument(url: String): Document? {
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; SM-F971B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val html = response.body?.string() ?: return null
                Jsoup.parse(html, url)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun processImages(
        doc: Document,
        imageOption: ImageQualityOption,
        imagesDir: File,
        downloadedAssets: MutableSet<String>,
        onBytesAdded: (Long) -> Unit
    ) {
        val imgElements = doc.select("img[src]")
        for (img in imgElements) {
            val absUrl = img.absUrl("src")

            when (imageOption) {
                ImageQualityOption.NO_IMAGES -> {
                    val altText = img.attr("alt").ifBlank { "Image" }
                    val badge = Element("span")
                        .text("[🖼 $altText]")
                        .attr("style", "color:#888;font-size:0.85em;padding:2px 6px;background:#eee;border-radius:4px;display:inline-block;")
                    img.replaceWith(badge)
                }

                ImageQualityOption.LOW_QUALITY -> {
                    if (absUrl.isNotBlank() && absUrl.startsWith("http")) {
                        val localFilename = "img_${absUrl.hashCode().coerceAtLeast(0)}.webp"
                        val localFile = File(imagesDir, localFilename)

                        if (!localFile.exists() && !downloadedAssets.contains(absUrl)) {
                            downloadedAssets.add(absUrl)
                            val compressedSize = downloadAndCompressImage(absUrl, localFile, maxDim = 480, quality = 30)
                            onBytesAdded(compressedSize)
                        }

                        if (localFile.exists()) {
                            img.attr("src", "images/$localFilename")
                            img.removeAttr("srcset")
                        }
                    }
                }

                ImageQualityOption.FULL_QUALITY -> {
                    if (absUrl.isNotBlank() && absUrl.startsWith("http")) {
                        val ext = absUrl.substringAfterLast(".").take(4).substringBefore("?").ifBlank { "jpg" }
                        val localFilename = "img_${absUrl.hashCode().coerceAtLeast(0)}.$ext"
                        val localFile = File(imagesDir, localFilename)

                        if (!localFile.exists() && !downloadedAssets.contains(absUrl)) {
                            downloadedAssets.add(absUrl)
                            val bytes = downloadRawAsset(absUrl, localFile)
                            onBytesAdded(bytes)
                        }

                        if (localFile.exists()) {
                            img.attr("src", "images/$localFilename")
                            img.removeAttr("srcset")
                        }
                    }
                }
            }
        }
    }

    private fun processStylesheets(
        doc: Document,
        cssDir: File,
        downloadedAssets: MutableSet<String>,
        onBytesAdded: (Long) -> Unit
    ) {
        val cssElements = doc.select("link[rel=stylesheet]")
        for (link in cssElements) {
            val absUrl = link.absUrl("href")
            if (absUrl.isNotBlank() && absUrl.startsWith("http")) {
                val localFilename = "style_${absUrl.hashCode().coerceAtLeast(0)}.css"
                val localFile = File(cssDir, localFilename)

                if (!localFile.exists() && !downloadedAssets.contains(absUrl)) {
                    downloadedAssets.add(absUrl)
                    val bytes = downloadRawAsset(absUrl, localFile)
                    onBytesAdded(bytes)
                }

                if (localFile.exists()) {
                    link.attr("href", "css/$localFilename")
                }
            }
        }
    }

    private fun processMedia(
        doc: Document,
        mediaDir: File,
        downloadedAssets: MutableSet<String>,
        onBytesAdded: (Long) -> Unit
    ) {
        // Collect all audio, video, and source elements
        val mediaElements = doc.select("audio[src], video[src], audio source[src], video source[src], source[type^=audio], source[type^=video]")
        for (media in mediaElements) {
            val absUrl = media.absUrl("src")
            if (absUrl.isNotBlank() && absUrl.startsWith("http")) {
                val ext = absUrl.substringAfterLast(".").take(4).substringBefore("?").ifBlank { "mp3" }
                val localFilename = "media_${absUrl.hashCode().coerceAtLeast(0)}.$ext"
                val localFile = File(mediaDir, localFilename)

                if (!localFile.exists() && !downloadedAssets.contains(absUrl)) {
                    downloadedAssets.add(absUrl)
                    val bytes = downloadRawAsset(absUrl, localFile)
                    onBytesAdded(bytes)
                }

                if (localFile.exists()) {
                    media.attr("src", "media/$localFilename")
                    // If parent or element is audio, ensure controls are visible for offline playback
                    if (media.tagName() == "audio") {
                        media.attr("controls", "controls")
                    } else if (media.parent()?.tagName() == "audio") {
                        media.parent()?.attr("controls", "controls")
                    }
                }
            }
        }
    }

    private fun processScripts(
        doc: Document,
        jsDir: File,
        downloadedAssets: MutableSet<String>,
        onBytesAdded: (Long) -> Unit
    ) {
        val scripts = doc.select("script[src]")
        for (script in scripts) {
            val absUrl = script.absUrl("src")
            // Skip tracking scripts
            if (absUrl.contains("google-analytics") || absUrl.contains("doubleclick") || absUrl.contains("facebook")) {
                continue
            }
            if (absUrl.isNotBlank() && absUrl.startsWith("http")) {
                val localFilename = "script_${absUrl.hashCode().coerceAtLeast(0)}.js"
                val localFile = File(jsDir, localFilename)

                if (!localFile.exists() && !downloadedAssets.contains(absUrl)) {
                    downloadedAssets.add(absUrl)
                    val bytes = downloadRawAsset(absUrl, localFile)
                    onBytesAdded(bytes)
                }

                if (localFile.exists()) {
                    script.attr("src", "js/$localFilename")
                }
            }
        }
    }

    private fun downloadAndCompressImage(url: String, destFile: File, maxDim: Int, quality: Int): Long {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return 0L
                val bytes = response.body?.bytes() ?: return 0L

                val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOptions)

                var sampleSize = 1
                while ((boundsOptions.outWidth / (sampleSize * 2)) >= maxDim ||
                    (boundsOptions.outHeight / (sampleSize * 2)) >= maxDim
                ) {
                    sampleSize *= 2
                }

                val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return 0L

                FileOutputStream(destFile).use { fos ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, fos)
                    } else {
                        bmp.compress(Bitmap.CompressFormat.JPEG, quality, fos)
                    }
                }
                bmp.recycle()
                destFile.length()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun downloadRawAsset(url: String, destFile: File): Long {
        return try {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return 0L
                destFile.parentFile?.mkdirs()
                response.body?.byteStream()?.use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destFile.length()
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun getHost(url: String): String? {
        return try {
            URI(url).host
        } catch (e: Exception) {
            null
        }
    }
}
