package com.offlinevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val author: String? = null,
    val coverUrl: String? = null,
    val localFilePath: String? = null,
    val format: String = "EPUB", // "EPUB", "PDF", "TXT"
    val fileSizeBytes: Long = 0L,
    val downloadDateEpochMs: Long = System.currentTimeMillis(),
    val lastReadEpochMs: Long = 0L,
    val readingProgress: Float = 0f, // 0.0 to 1.0 (0% to 100%)
    val currentChapterIndex: Int = 0,
    val currentScrollOffset: Int = 0,
    val totalChapters: Int = 1,
    val synopsis: String? = null,
    val isDownloaded: Boolean = false,
    val downloadUrl: String? = null
)
