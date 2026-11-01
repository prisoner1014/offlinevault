package com.offlinevault.app.data.local.dao

import androidx.room.*
import com.offlinevault.app.data.local.entity.EpisodeEntity
import com.offlinevault.app.data.local.entity.FeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedDao {
    @Query("SELECT * FROM feeds ORDER BY title ASC")
    fun getAllFeeds(): Flow<List<FeedEntity>>

    @Query("SELECT * FROM episodes WHERE feedId = :feedId ORDER BY pubDate DESC")
    fun getEpisodesForFeed(feedId: Long): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE isDownloaded = 1 ORDER BY pubDate DESC")
    fun getDownloadedEpisodes(): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE id = :episodeId")
    suspend fun getEpisodeById(episodeId: Long): EpisodeEntity?

    @Query("SELECT * FROM feeds WHERE feedUrl = :url LIMIT 1")
    suspend fun getFeedByUrl(url: String): FeedEntity?

    @Query("SELECT feedUrl FROM feeds")
    fun getAllFeedUrls(): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM episodes WHERE feedId = :feedId")
    suspend fun getEpisodeCount(feedId: Long): Int

    @Query("SELECT COUNT(*) FROM episodes WHERE feedId = :feedId AND isDownloaded = 1")
    suspend fun getDownloadedEpisodeCount(feedId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeed(feed: FeedEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisodes(episodes: List<EpisodeEntity>)

    @Update
    suspend fun updateEpisode(episode: EpisodeEntity)

    @Update
    suspend fun updateEpisodes(episodes: List<EpisodeEntity>)

    @Query("UPDATE episodes SET imageUrl = :imageUrl WHERE id = :episodeId")
    suspend fun updateEpisodeImageUrl(episodeId: Long, imageUrl: String?)

    @Query("UPDATE episodes SET imageUrl = :imageUrl WHERE feedId = :feedId AND audioUrl = :audioUrl")
    suspend fun updateEpisodeImageByAudioUrl(feedId: Long, audioUrl: String, imageUrl: String?)

    @Query("SELECT * FROM episodes WHERE feedId = :feedId")
    suspend fun getEpisodesListForFeed(feedId: Long): List<EpisodeEntity>

    @Delete
    suspend fun deleteFeed(feed: FeedEntity)

    @Query("DELETE FROM episodes WHERE feedId = :feedId")
    suspend fun deleteEpisodesForFeed(feedId: Long)

    @Query("UPDATE episodes SET lastPlaybackPositionMs = :positionMs WHERE id = :episodeId")
    suspend fun updatePlaybackPosition(episodeId: Long, positionMs: Long)

    @Query("UPDATE episodes SET isPlayed = 1 WHERE id = :episodeId")
    suspend fun markAsPlayed(episodeId: Long)
}
