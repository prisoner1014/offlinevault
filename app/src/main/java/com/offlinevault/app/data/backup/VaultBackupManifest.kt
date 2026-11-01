package com.offlinevault.app.data.backup

import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class VaultBackupManifest(
    val formatVersion: Int = 1,
    val appVersionName: String,
    val appVersionCode: Int,
    val createdAtEpochMs: Long,
    val createdAtFormatted: String,
    val sourceVaultPath: String,
    val includesDatabase: Boolean,
    val includesSettings: Boolean,
    val mediaCategories: List<String>,
    val entityCounts: Map<String, Int> = emptyMap(),
    val totalSizeBytes: Long = 0L
) {
    fun toJson(): String {
        val root = JSONObject().apply {
            put("formatVersion", formatVersion)
            put("appVersionName", appVersionName)
            put("appVersionCode", appVersionCode)
            put("createdAtEpochMs", createdAtEpochMs)
            put("createdAtFormatted", createdAtFormatted)
            put("sourceVaultPath", sourceVaultPath)
            put("includesDatabase", includesDatabase)
            put("includesSettings", includesSettings)

            val cats = JSONArray()
            mediaCategories.forEach { cats.put(it) }
            put("mediaCategories", cats)

            val counts = JSONObject()
            entityCounts.forEach { (k, v) -> counts.put(k, v) }
            put("entityCounts", counts)

            put("totalSizeBytes", totalSizeBytes)
        }
        return root.toString(2)
    }

    companion object {
        fun fromJson(jsonStr: String): VaultBackupManifest {
            val root = JSONObject(jsonStr)
            val cats = mutableListOf<String>()
            val catsArr = root.optJSONArray("mediaCategories")
            if (catsArr != null) {
                for (i in 0 until catsArr.length()) {
                    cats.add(catsArr.getString(i))
                }
            }

            val counts = mutableMapOf<String, Int>()
            val countsObj = root.optJSONObject("entityCounts")
            if (countsObj != null) {
                val keys = countsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    counts[k] = countsObj.getInt(k)
                }
            }

            return VaultBackupManifest(
                formatVersion = root.optInt("formatVersion", 1),
                appVersionName = root.optString("appVersionName", "Unknown"),
                appVersionCode = root.optInt("appVersionCode", 1),
                createdAtEpochMs = root.optLong("createdAtEpochMs", System.currentTimeMillis()),
                createdAtFormatted = root.optString("createdAtFormatted", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())),
                sourceVaultPath = root.optString("sourceVaultPath", ""),
                includesDatabase = root.optBoolean("includesDatabase", true),
                includesSettings = root.optBoolean("includesSettings", true),
                mediaCategories = cats,
                entityCounts = counts,
                totalSizeBytes = root.optLong("totalSizeBytes", 0L)
            )
        }
    }
}

data class BackupOptions(
    val includeDatabase: Boolean = true,
    val includeSettings: Boolean = true,
    val includeBooks: Boolean = true,
    val includeWebArchives: Boolean = true,
    val includePodcasts: Boolean = false,
    val includeVideos: Boolean = false
)

data class BackupStats(
    val booksCount: Int = 0,
    val booksSizeBytes: Long = 0L,
    val archivesCount: Int = 0,
    val archivesSizeBytes: Long = 0L,
    val podcastsCount: Int = 0,
    val podcastsSizeBytes: Long = 0L,
    val videosCount: Int = 0,
    val videosSizeBytes: Long = 0L,
    val feedsCount: Int = 0,
    val databaseSizeBytes: Long = 0L
)
