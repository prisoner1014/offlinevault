package com.offlinevault.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.offlinevault.app.data.local.dao.ArchiveDao
import com.offlinevault.app.data.local.dao.BookDao
import com.offlinevault.app.data.local.dao.ChatDao
import com.offlinevault.app.data.local.dao.DownloadDao
import com.offlinevault.app.data.local.dao.FeedDao
import com.offlinevault.app.data.local.entity.ArchiveEntity
import com.offlinevault.app.data.local.entity.BookEntity
import com.offlinevault.app.data.local.entity.BookmarkEntity
import com.offlinevault.app.data.local.entity.ChatMessageEntity
import com.offlinevault.app.data.local.entity.ChatSessionEntity
import com.offlinevault.app.data.local.entity.DownloadEntity
import com.offlinevault.app.data.local.entity.EpisodeEntity
import com.offlinevault.app.data.local.entity.FeedEntity

@Database(
    entities = [
        DownloadEntity::class,
        FeedEntity::class,
        EpisodeEntity::class,
        ArchiveEntity::class,
        BookEntity::class,
        BookmarkEntity::class,
        ChatSessionEntity::class,
        ChatMessageEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun feedDao(): FeedDao
    abstract fun archiveDao(): ArchiveDao
    abstract fun bookDao(): BookDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offline_vault.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeAndResetInstance() {
            synchronized(this) {
                try {
                    INSTANCE?.close()
                } catch (e: Exception) {
                    // Ignore error on closing
                }
                INSTANCE = null
            }
        }
    }
}
