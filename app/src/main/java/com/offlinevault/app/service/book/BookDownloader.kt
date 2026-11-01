package com.offlinevault.app.service.book

import android.content.Context
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.local.entity.BookEntity
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class BookDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val storageTracker = StorageTracker(context)
    private val db = AppDatabase.getInstance(context)

    suspend fun downloadBook(
        downloadUrl: String,
        suggestedTitle: String? = null,
        suggestedAuthor: String? = null,
        suggestedCoverUrl: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            val booksDir = storageTracker.getCategoryDir(StorageCategory.BOOKS)
            val format = detectFormat(downloadUrl)

            val safeTitle = (suggestedTitle ?: "Book_${System.currentTimeMillis()}")
                .replace(Regex("[^a-zA-Z0-9.-]"), "_")
                .take(40)
            val targetFile = File(booksDir, "${safeTitle}_${System.currentTimeMillis()}.$format")

            val request = Request.Builder().url(downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(Exception("HTTP error ${response.code}: ${response.message}"))
                }

                val body = response.body ?: return@withContext Result.failure(Exception("Empty response body"))
                val contentLength = body.contentLength()
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(targetFile)

                var totalBytesRead = 0L
                val buffer = ByteArray(8192)

                inputStream.use { input ->
                    outputStream.use { output ->
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            totalBytesRead += read
                            if (contentLength > 0) {
                                val progress = (totalBytesRead.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f)
                                onProgress(progress)
                            }
                        }
                    }
                }
            }

            // Post-process metadata
            var finalTitle = suggestedTitle ?: targetFile.nameWithoutExtension
            var finalAuthor = suggestedAuthor
            var totalChapters = 1
            var coverUrl = suggestedCoverUrl

            if (format.equals("epub", ignoreCase = true)) {
                try {
                    val parsed = EpubParser.parseEpub(targetFile)
                    if (finalTitle.isBlank() || finalTitle.startsWith("Book_")) {
                        finalTitle = parsed.title
                    }
                    if (finalAuthor.isNullOrBlank()) {
                        finalAuthor = parsed.author
                    }
                    totalChapters = parsed.chapters.size.coerceAtLeast(1)

                    // If cover image bytes exist in EPUB and no remote cover, save locally
                    if (coverUrl.isNullOrBlank() && parsed.coverImageBytes != null) {
                        val coverFile = File(booksDir, "${targetFile.nameWithoutExtension}_cover.jpg")
                        coverFile.writeBytes(parsed.coverImageBytes)
                        coverUrl = coverFile.absolutePath
                    }
                } catch (_: Exception) {}
            } else if (format.equals("txt", ignoreCase = true)) {
                try {
                    val parsed = EpubParser.parsePlainText(targetFile)
                    totalChapters = parsed.chapters.size.coerceAtLeast(1)
                } catch (_: Exception) {}
            }

            val entity = BookEntity(
                title = finalTitle,
                author = finalAuthor,
                coverUrl = coverUrl,
                localFilePath = targetFile.absolutePath,
                format = format.uppercase(),
                fileSizeBytes = targetFile.length(),
                downloadDateEpochMs = System.currentTimeMillis(),
                lastReadEpochMs = 0L,
                readingProgress = 0f,
                currentChapterIndex = 0,
                currentScrollOffset = 0,
                totalChapters = totalChapters,
                synopsis = null,
                isDownloaded = true,
                downloadUrl = downloadUrl
            )

            val id = db.bookDao().insertBook(entity)
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importLocalFile(file: File): Result<BookEntity> = withContext(Dispatchers.IO) {
        try {
            val booksDir = storageTracker.getCategoryDir(StorageCategory.BOOKS)
            val format = detectFormat(file.name)
            val targetFile = File(booksDir, "${file.nameWithoutExtension}_${System.currentTimeMillis()}.$format")
            file.copyTo(targetFile, overwrite = true)

            var finalTitle = file.nameWithoutExtension
            var finalAuthor: String? = null
            var totalChapters = 1
            var coverUrl: String? = null

            if (format.equals("epub", ignoreCase = true)) {
                try {
                    val parsed = EpubParser.parseEpub(targetFile)
                    finalTitle = parsed.title
                    finalAuthor = parsed.author
                    totalChapters = parsed.chapters.size.coerceAtLeast(1)
                    if (parsed.coverImageBytes != null) {
                        val coverFile = File(booksDir, "${targetFile.nameWithoutExtension}_cover.jpg")
                        coverFile.writeBytes(parsed.coverImageBytes)
                        coverUrl = coverFile.absolutePath
                    }
                } catch (_: Exception) {}
            } else if (format.equals("txt", ignoreCase = true)) {
                try {
                    val parsed = EpubParser.parsePlainText(targetFile)
                    totalChapters = parsed.chapters.size.coerceAtLeast(1)
                } catch (_: Exception) {}
            }

            val entity = BookEntity(
                title = finalTitle,
                author = finalAuthor,
                coverUrl = coverUrl,
                localFilePath = targetFile.absolutePath,
                format = format.uppercase(),
                fileSizeBytes = targetFile.length(),
                downloadDateEpochMs = System.currentTimeMillis(),
                totalChapters = totalChapters,
                isDownloaded = true,
                downloadUrl = null
            )

            val id = db.bookDao().insertBook(entity)
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun detectFormat(pathOrUrl: String): String {
        val lower = pathOrUrl.lowercase()
        return when {
            lower.contains(".epub") -> "epub"
            lower.contains(".pdf") -> "pdf"
            lower.contains(".txt") -> "txt"
            else -> "epub"
        }
    }
}
