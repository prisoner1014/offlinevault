package com.offlinevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "book_bookmarks",
    indices = [Index(value = ["bookId"])]
)
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val bookId: Long,
    val chapterIndex: Int,
    val chapterTitle: String,
    val pageIndex: Int = 0,
    val paragraphIndex: Int = 0,
    val snippet: String,
    val note: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
