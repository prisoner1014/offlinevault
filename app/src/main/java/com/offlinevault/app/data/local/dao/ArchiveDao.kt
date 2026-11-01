package com.offlinevault.app.data.local.dao

import androidx.room.*
import com.offlinevault.app.data.local.entity.ArchiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {
    @Query("SELECT id, title, originalUrl, localMhtmlPath, NULL AS readerTextContent, excerpt, fileSizeBytes, isSiteDirectory, pageCount, crawlDepth, savedAt FROM web_archives ORDER BY savedAt DESC")
    fun getAllArchives(): Flow<List<ArchiveEntity>>

    @Query("SELECT * FROM web_archives WHERE id = :id")
    suspend fun getArchiveById(id: Long): ArchiveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArchive(archive: ArchiveEntity): Long

    @Delete
    suspend fun deleteArchive(archive: ArchiveEntity)

    @Query("DELETE FROM web_archives WHERE id = :id")
    suspend fun deleteArchiveById(id: Long)
}
