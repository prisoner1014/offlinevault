package com.offlinevault.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "feeds")
data class FeedEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val feedUrl: String,
    val link: String? = null,
    val description: String? = null,
    val imageUrl: String? = null,
    val isPodcast: Boolean = false,
    val autoDownloadEpisodes: Boolean = true,
    val lastSyncTime: Long = 0
)

@Entity(
    tableName = "episodes",
    foreignKeys = [
        ForeignKey(
            entity = FeedEntity::class,
            parentColumns = ["id"],
            childColumns = ["feedId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("feedId")]
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val feedId: Long,
    val title: String,
    val description: String? = null,
    val audioUrl: String,
    val imageUrl: String? = null,
    val localAudioPath: String? = null,
    val pubDate: Long = 0,
    val durationSeconds: Long = 0,
    val lastPlaybackPositionMs: Long = 0,
    val isDownloaded: Boolean = false,
    val isPlayed: Boolean = false,
    val fileSizeBytes: Long = 0
)
