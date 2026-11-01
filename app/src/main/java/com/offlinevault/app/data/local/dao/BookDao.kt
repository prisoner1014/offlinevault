package com.offlinevault.app.data.local.dao

import androidx.room.*
import com.offlinevault.app.data.local.entity.BookEntity
import com.offlinevault.app.data.local.entity.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {
    @Query("SELECT * FROM books ORDER BY lastReadEpochMs DESC, downloadDateEpochMs DESC")
    fun getAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE isDownloaded = 1 ORDER BY lastReadEpochMs DESC, downloadDateEpochMs DESC")
    fun getDownloadedBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getBookById(id: Long): BookEntity?

    @Query("SELECT * FROM books WHERE downloadUrl = :url LIMIT 1")
    suspend fun getBookByDownloadUrl(url: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: BookEntity): Long

    @Update
    suspend fun updateBook(book: BookEntity)

    @Delete
    suspend fun deleteBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteBookById(id: Long)

    @Query("UPDATE books SET readingProgress = :progress, currentChapterIndex = :chapterIndex, currentScrollOffset = :scrollOffset, lastReadEpochMs = :lastRead WHERE id = :id")
    suspend fun updateReadingProgress(id: Long, progress: Float, chapterIndex: Int, scrollOffset: Int, lastRead: Long)

    // Bookmarks
    @Query("SELECT * FROM book_bookmarks WHERE bookId = :bookId ORDER BY chapterIndex ASC, pageIndex ASC, createdAtEpochMs DESC")
    fun getBookmarksForBook(bookId: Long): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM book_bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    @Query("DELETE FROM book_bookmarks WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND pageIndex = :pageIndex")
    suspend fun deleteBookmarkAt(bookId: Long, chapterIndex: Int, pageIndex: Int)

    @Query("SELECT COUNT(*) FROM book_bookmarks WHERE bookId = :bookId AND chapterIndex = :chapterIndex AND pageIndex = :pageIndex")
    suspend fun countBookmarkAt(bookId: Long, chapterIndex: Int, pageIndex: Int): Int
}
