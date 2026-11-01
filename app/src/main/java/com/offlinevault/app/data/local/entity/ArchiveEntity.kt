package com.offlinevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "web_archives")
data class ArchiveEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val originalUrl: String,
    val localMhtmlPath: String? = null,
    val readerTextContent: String? = null,
    val excerpt: String? = null,
    val fileSizeBytes: Long = 0,
    val isSiteDirectory: Boolean = false,
    val pageCount: Int = 1,
    val crawlDepth: Int = 0,
    val savedAt: Long = System.currentTimeMillis()
)
