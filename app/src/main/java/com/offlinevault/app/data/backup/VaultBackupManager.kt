package com.offlinevault.app.data.backup

import android.content.Context
import com.offlinevault.app.data.local.AppDatabase
import com.offlinevault.app.data.preferences.SettingsRepository
import com.offlinevault.app.data.storage.StorageCategory
import com.offlinevault.app.data.storage.StorageTracker
import com.offlinevault.app.util.AppVersionInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class VaultBackupManager(private val context: Context) {

    private val storageTracker = StorageTracker(context)
    private val settingsRepository = SettingsRepository(context)
    private val db = AppDatabase.getInstance(context)

    suspend fun calculateStats(): BackupStats = withContext(Dispatchers.IO) {
        val books = try { db.bookDao().getAllBooks().first() } catch (e: Exception) { emptyList() }
        val archives = try { db.archiveDao().getAllArchives().first() } catch (e: Exception) { emptyList() }
        val feeds = try { db.feedDao().getAllFeeds().first() } catch (e: Exception) { emptyList() }
        val downloads = try { db.downloadDao().getAllDownloads().first() } catch (e: Exception) { emptyList() }

        val booksDir = storageTracker.getCategoryDir(StorageCategory.BOOKS)
        val archivesDir = storageTracker.getCategoryDir(StorageCategory.WEB_ARCHIVES)
        val podcastsDir = storageTracker.getCategoryDir(StorageCategory.PODCASTS)
        val videosDir = storageTracker.getCategoryDir(StorageCategory.VIDEOS)
        val dbFile = context.getDatabasePath("offline_vault.db")

        BackupStats(
            booksCount = books.size,
            booksSizeBytes = calculateDirSize(booksDir),
            archivesCount = archives.size,
            archivesSizeBytes = calculateDirSize(archivesDir),
            podcastsCount = feeds.size,
            podcastsSizeBytes = calculateDirSize(podcastsDir),
            videosCount = downloads.count { it.mediaType.name == "VIDEO" },
            videosSizeBytes = calculateDirSize(videosDir),
            feedsCount = feeds.size,
            databaseSizeBytes = if (dbFile.exists()) dbFile.length() else 0L
        )
    }

    suspend fun exportBackup(
        outputStream: OutputStream,
        options: BackupOptions,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<VaultBackupManifest> = withContext(Dispatchers.IO) {
        try {
            onProgress(0.05f, "Preparing database snapshot...")

            // 1. Force WAL checkpoint so offline_vault.db is fully up to date
            try {
                db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                    cursor.moveToFirst()
                }
            } catch (e: Exception) {
                // Ignore if checkpoint fails, continue with current file
            }

            // Gather stats for manifest
            val books = try { db.bookDao().getAllBooks().first() } catch (e: Exception) { emptyList() }
            val archives = try { db.archiveDao().getAllArchives().first() } catch (e: Exception) { emptyList() }
            val feeds = try { db.feedDao().getAllFeeds().first() } catch (e: Exception) { emptyList() }
            val downloads = try { db.downloadDao().getAllDownloads().first() } catch (e: Exception) { emptyList() }

            val categoriesIncluded = mutableListOf<String>()
            if (options.includeBooks) categoriesIncluded.add("BOOKS")
            if (options.includeWebArchives) categoriesIncluded.add("WEB_ARCHIVES")
            if (options.includePodcasts) categoriesIncluded.add("PODCASTS")
            if (options.includeVideos) categoriesIncluded.add("VIDEOS")

            val counts = mapOf(
                "books" to books.size,
                "archives" to archives.size,
                "feeds" to feeds.size,
                "downloads" to downloads.size
            )

            val manifest = VaultBackupManifest(
                formatVersion = 1,
                appVersionName = AppVersionInfo.CURRENT_VERSION_NAME,
                appVersionCode = AppVersionInfo.CURRENT_VERSION_CODE,
                createdAtEpochMs = System.currentTimeMillis(),
                createdAtFormatted = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date()),
                sourceVaultPath = storageTracker.vaultRootDir.absolutePath,
                includesDatabase = options.includeDatabase,
                includesSettings = options.includeSettings,
                mediaCategories = categoriesIncluded,
                entityCounts = counts
            )

            val zipOut = ZipOutputStream(BufferedOutputStream(outputStream))
            zipOut.use { zos ->
                // Write manifest
                onProgress(0.10f, "Writing backup manifest...")
                val manifestBytes = manifest.toJson().toByteArray(Charsets.UTF_8)
                val manifestEntry = ZipEntry("manifest.json")
                zos.putNextEntry(manifestEntry)
                zos.write(manifestBytes)
                zos.closeEntry()

                // Write Database
                if (options.includeDatabase) {
                    onProgress(0.20f, "Archiving database...")
                    val dbFile = context.getDatabasePath("offline_vault.db")
                    if (dbFile.exists()) {
                        addFileToZip(dbFile, "offline_vault.db", zos)
                    }
                }

                // Write Settings
                if (options.includeSettings) {
                    onProgress(0.30f, "Archiving settings...")
                    val settingsJson = settingsRepository.exportSettingsJson()
                    val settingsEntry = ZipEntry("settings.json")
                    zos.putNextEntry(settingsEntry)
                    zos.write(settingsJson.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }

                // Add Media Files
                val mediaTasks = mutableListOf<Pair<StorageCategory, String>>()
                if (options.includeBooks) mediaTasks.add(StorageCategory.BOOKS to "Books & Documents")
                if (options.includeWebArchives) mediaTasks.add(StorageCategory.WEB_ARCHIVES to "Web Archives")
                if (options.includePodcasts) mediaTasks.add(StorageCategory.PODCASTS to "Podcast Audio")
                if (options.includeVideos) mediaTasks.add(StorageCategory.VIDEOS to "Videos")

                var progressFraction = 0.35f
                val progressStep = if (mediaTasks.isNotEmpty()) (0.60f / mediaTasks.size) else 0f

                for ((category, label) in mediaTasks) {
                    onProgress(progressFraction, "Archiving $label...")
                    val dir = storageTracker.getCategoryDir(category)
                    addDirectoryToZip(dir, category.subDirectory, zos)
                    progressFraction += progressStep
                }

                onProgress(0.95f, "Finalizing backup archive...")
            }

            onProgress(1.0f, "Backup completed successfully!")
            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun inspectBackup(inputStream: InputStream): VaultBackupManifest? = withContext(Dispatchers.IO) {
        try {
            val zipIn = ZipInputStream(BufferedInputStream(inputStream))
            zipIn.use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    if (entry.name == "manifest.json") {
                        val content = zis.bufferedReader(Charsets.UTF_8).readText()
                        return@withContext VaultBackupManifest.fromJson(content)
                    }
                    entry = zis.nextEntry
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun restoreBackup(
        inputStream: InputStream,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<VaultBackupManifest> = withContext(Dispatchers.IO) {
        val stageDir = File(context.cacheDir, "vault_restore_stage_${System.currentTimeMillis()}")
        try {
            onProgress(0.05f, "Reading backup archive...")
            stageDir.mkdirs()

            var manifest: VaultBackupManifest? = null
            val zipIn = ZipInputStream(BufferedInputStream(inputStream))
            zipIn.use { zis ->
                var entry = zis.nextEntry
                var extractedCount = 0
                while (entry != null) {
                    val targetFile = File(stageDir, entry.name)
                    if (entry.isDirectory) {
                        targetFile.mkdirs()
                    } else {
                        targetFile.parentFile?.mkdirs()
                        FileOutputStream(targetFile).use { out ->
                            zis.copyTo(out)
                        }
                        if (entry.name == "manifest.json") {
                            manifest = VaultBackupManifest.fromJson(targetFile.readText(Charsets.UTF_8))
                        }
                    }
                    extractedCount++
                    if (extractedCount % 10 == 0) {
                        onProgress(0.20f, "Extracted $extractedCount files...")
                    }
                    entry = zis.nextEntry
                }
            }

            if (manifest == null) {
                return@withContext Result.failure(IllegalStateException("Invalid backup: manifest.json missing"))
            }

            onProgress(0.40f, "Restoring settings...")
            val stagedSettings = File(stageDir, "settings.json")
            if (stagedSettings.exists()) {
                settingsRepository.restoreSettingsFromJson(stagedSettings.readText(Charsets.UTF_8))
            }

            // Restore Database
            val stagedDb = File(stageDir, "offline_vault.db")
            if (stagedDb.exists()) {
                onProgress(0.55f, "Restoring database tables...")
                // Safely close active Room instance
                AppDatabase.closeAndResetInstance()

                val appDbFile = context.getDatabasePath("offline_vault.db")
                appDbFile.parentFile?.mkdirs()

                // Remove old DB and WAL files
                val walFile = File(appDbFile.parentFile, "offline_vault.db-wal")
                val shmFile = File(appDbFile.parentFile, "offline_vault.db-shm")
                if (walFile.exists()) walFile.delete()
                if (shmFile.exists()) shmFile.delete()

                stagedDb.copyTo(appDbFile, overwrite = true)

                // Fix up directory paths if app directory changed (e.g. restoring to a different device or user profile)
                val newVaultRoot = storageTracker.vaultRootDir.absolutePath
                val oldVaultRoot = manifest?.sourceVaultPath ?: ""
                if (oldVaultRoot.isNotBlank() && oldVaultRoot != newVaultRoot) {
                    onProgress(0.70f, "Remapping media paths to new device location...")
                    try {
                        val restoredDb = AppDatabase.getInstance(context)
                        val writable = restoredDb.openHelper.writableDatabase
                        writable.execSQL(
                            "UPDATE books SET localFilePath = REPLACE(localFilePath, ?, ?) WHERE localFilePath IS NOT NULL",
                            arrayOf(oldVaultRoot, newVaultRoot)
                        )
                        writable.execSQL(
                            "UPDATE web_archives SET localMhtmlPath = REPLACE(localMhtmlPath, ?, ?) WHERE localMhtmlPath IS NOT NULL",
                            arrayOf(oldVaultRoot, newVaultRoot)
                        )
                        writable.execSQL(
                            "UPDATE episodes SET localAudioPath = REPLACE(localAudioPath, ?, ?) WHERE localAudioPath IS NOT NULL",
                            arrayOf(oldVaultRoot, newVaultRoot)
                        )
                        writable.execSQL(
                            "UPDATE downloads SET localFilePath = REPLACE(localFilePath, ?, ?) WHERE localFilePath IS NOT NULL",
                            arrayOf(oldVaultRoot, newVaultRoot)
                        )
                    } catch (e: Exception) {
                        // Ignore path fixup error
                    }
                }
            }

            // Move media folders to vaultRootDir
            onProgress(0.80f, "Restoring saved media & books...")
            val stagedVaultDir = File(stageDir, "vault")
            if (stagedVaultDir.exists() && stagedVaultDir.isDirectory) {
                copyDirectoryRecursively(stagedVaultDir, File(storageTracker.vaultRootDir, "vault"))
            }

            onProgress(1.0f, "Vault restored successfully!")
            Result.success(manifest!!)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            // Clean up staging directory
            deleteDirectoryRecursively(stageDir)
        }
    }

    private fun addFileToZip(file: File, entryPath: String, zos: ZipOutputStream) {
        val entry = ZipEntry(entryPath.replace("\\", "/"))
        zos.putNextEntry(entry)
        FileInputStream(file).use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun addDirectoryToZip(dir: File, baseRelativePath: String, zos: ZipOutputStream) {
        if (!dir.exists()) return
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                val rel = file.relativeTo(dir).path
                val entryPath = "$baseRelativePath/$rel".replace("\\", "/")
                val entry = ZipEntry(entryPath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }

    private fun calculateDirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) size += file.length()
        }
        return size
    }

    private fun copyDirectoryRecursively(source: File, dest: File) {
        if (!source.exists()) return
        dest.mkdirs()
        source.listFiles()?.forEach { file ->
            val target = File(dest, file.name)
            if (file.isDirectory) {
                copyDirectoryRecursively(file, target)
            } else {
                file.copyTo(target, overwrite = true)
            }
        }
    }

    private fun deleteDirectoryRecursively(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) deleteDirectoryRecursively(file) else file.delete()
        }
        dir.delete()
    }
}
