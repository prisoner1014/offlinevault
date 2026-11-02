package com.offlinevault.app.data.local.dao

import androidx.room.*
import com.offlinevault.app.data.local.entity.DownloadEntity
import com.offlinevault.app.data.local.entity.DownloadStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY createdAt DESC")
    fun getCompletedDownloads(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND mediaType = 'VIDEO' ORDER BY createdAt DESC")
    suspend fun getCompletedVideosList(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadById(id: Long): DownloadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: DownloadEntity): Long

    @Update
    suspend fun updateDownload(download: DownloadEntity)

    @Delete
    suspend fun deleteDownload(download: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteDownloadById(id: Long)
}
