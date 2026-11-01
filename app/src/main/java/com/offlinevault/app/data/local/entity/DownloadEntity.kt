package com.offlinevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class DownloadStatus {
    QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED
}

enum class MediaType {
    VIDEO, AUDIO, ARCHIVE, DOCUMENT
}

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val sourceUrl: String,
    val localFilePath: String? = null,
    val mediaType: MediaType = MediaType.VIDEO,
    val formatDescription: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progressPercent: Float = 0f,
    val speedText: String = "",
    val etaText: String = "",
    val totalSizeBytes: Long = 0,
    val downloadedBytes: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)
