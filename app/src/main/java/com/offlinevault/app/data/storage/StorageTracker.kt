package com.offlinevault.app.data.storage

import android.content.Context
import android.os.Environment
import android.os.StatFs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class StorageTracker(private val context: Context) {

    /**
     * Dedicated app-isolated root directory.
     * Uses private external storage so it doesn't clutter the user's public Downloads folder.
     */
    val vaultRootDir: File
        get() {
            val externalDir = context.getExternalFilesDir(null)
            return externalDir ?: context.filesDir
        }

    fun getCategoryDir(category: StorageCategory): File {
        val dir = File(vaultRootDir, category.subDirectory)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun calculateStorageBreakdown(): StorageBreakdown = withContext(Dispatchers.IO) {
        val videoBytes = calculateDirSize(getCategoryDir(StorageCategory.VIDEOS))
        val podcastBytes = calculateDirSize(getCategoryDir(StorageCategory.PODCASTS))
        val bookBytes = calculateDirSize(getCategoryDir(StorageCategory.BOOKS))
        val webArchiveBytes = calculateDirSize(getCategoryDir(StorageCategory.WEB_ARCHIVES))
        val cacheBytes = calculateDirSize(getCategoryDir(StorageCategory.TEMP_CACHE)) +
                calculateDirSize(context.cacheDir)

        val totalAppUsed = videoBytes + podcastBytes + bookBytes + webArchiveBytes + cacheBytes

        // Device hardware storage capacity
        val stat = StatFs(vaultRootDir.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong

        val totalDeviceBytes = totalBlocks * blockSize
        val freeDeviceBytes = availableBlocks * blockSize

        StorageBreakdown(
            videoBytes = videoBytes,
            podcastBytes = podcastBytes,
            bookBytes = bookBytes,
            webArchiveBytes = webArchiveBytes,
            cacheBytes = cacheBytes,
            totalAppUsedBytes = totalAppUsed,
            freeDeviceBytes = freeDeviceBytes,
            totalDeviceBytes = totalDeviceBytes
        )
    }

    suspend fun clearCache(): Long = withContext(Dispatchers.IO) {
        var freedBytes = 0L
        val tempCacheDir = getCategoryDir(StorageCategory.TEMP_CACHE)
        freedBytes += deleteDirContents(tempCacheDir)
        freedBytes += deleteDirContents(context.cacheDir)
        freedBytes
    }

    suspend fun deleteCategoryContent(category: StorageCategory): Long = withContext(Dispatchers.IO) {
        val dir = getCategoryDir(category)
        deleteDirContents(dir)
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.listFiles()?.forEach { file ->
            size += if (file.isDirectory) calculateDirSize(file) else file.length()
        }
        return size
    }

    private fun deleteDirContents(dir: File): Long {
        var freed = 0L
        dir.listFiles()?.forEach { file ->
            freed += if (file.isDirectory) {
                deleteDirContents(file).also { file.delete() }
            } else {
                val len = file.length()
                if (file.delete()) len else 0L
            }
        }
        return freed
    }
}
